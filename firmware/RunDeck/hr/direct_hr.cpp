#include "direct_hr.h"

#include <NimBLEDevice.h>

namespace rundeck {
namespace {
constexpr char kHeartRateService[] = "180D";
constexpr char kHeartRateMeasurement[] = "2A37";
constexpr uint32_t kScanWindowMs = 5000;
constexpr uint32_t kReconnectBackoffMs = 3000;
constexpr uint32_t kStaleMs = 10000;
DirectHrClient* activeClient = nullptr;

class ScanCallbacks final : public NimBLEScanCallbacks {
 public:
  void onResult(const NimBLEAdvertisedDevice* device) override {
    if (!activeClient || !device->isAdvertisingService(NimBLEUUID(kHeartRateService))) return;
    activeClient->onAdvertised(device);
  }
};

class ClientCallbacks final : public NimBLEClientCallbacks {
 public:
  void onConnect(NimBLEClient* client) override {
    if (activeClient) activeClient->onConnected(client);
  }
  void onConnectFail(NimBLEClient*, int) override {
    if (activeClient) activeClient->onDisconnected();
  }
  void onDisconnect(NimBLEClient*, int) override {
    if (activeClient) activeClient->onDisconnected();
  }
};

ScanCallbacks scanCallbacks;
ClientCallbacks clientCallbacks;
NimBLEScan* scan = nullptr;
}  // namespace

void DirectHrClient::begin() {
  activeClient = this;
  scan = NimBLEDevice::getScan();
  scan->setActiveScan(true);
  scan->setScanCallbacks(&scanCallbacks, false);
}

void DirectHrClient::setEnabled(bool enabled) {
  enabled_ = enabled;
  if (!enabled_ && scan && scan->isScanning()) scan->stop();
  if (!enabled_) {
    scanning_ = false;
    bpm_ = 0;
    client_ = nullptr;
  }
}

void DirectHrClient::tick(uint32_t nowMs) {
  if (!enabled_ || !scan) return;
  if (client_ == nullptr && !scanning_ && nowMs - lastAttemptMs_ >= kReconnectBackoffMs) {
    lastAttemptMs_ = nowMs;
    scanning_ = scan->start(kScanWindowMs, false, true);
  }
  if (scanning_ && !scan->isScanning()) scanning_ = false;
}

bool DirectHrClient::fresh(uint32_t nowMs) const {
  return bpm_ > 0 && nowMs - lastMeasurementMs_ <= kStaleMs;
}

void DirectHrClient::onAdvertised(const void* rawDevice) {
  if (!enabled_ || client_ != nullptr) return;
  auto* device = static_cast<const NimBLEAdvertisedDevice*>(rawDevice);
  NimBLEClient* client = NimBLEDevice::createClient();
  if (!client) return;
  client->setClientCallbacks(&clientCallbacks, false);
  if (!client->connect(device)) {
    NimBLEDevice::deleteClient(client);
    return;
  }
  client_ = client;
  scanning_ = false;
}

void DirectHrClient::onConnected(void* rawClient) {
  auto* client = static_cast<NimBLEClient*>(rawClient);
  NimBLERemoteService* service = client->getService(NimBLEUUID(kHeartRateService));
  if (!service) return onDisconnected();
  NimBLERemoteCharacteristic* measurement = service->getCharacteristic(NimBLEUUID(kHeartRateMeasurement));
  if (!measurement || !measurement->canNotify()) return onDisconnected();
  const bool subscribed = measurement->subscribe(true, [this](NimBLERemoteCharacteristic*, uint8_t* data, size_t length, bool) {
    onMeasurement(data, length);
  });
  if (!subscribed) onDisconnected();
}

void DirectHrClient::onDisconnected() {
  bpm_ = 0;
  lastMeasurementMs_ = 0;
  auto* client = static_cast<NimBLEClient*>(client_);
  client_ = nullptr;
  if (client) NimBLEDevice::deleteClient(client);
}

void DirectHrClient::onMeasurement(const uint8_t* data, size_t length) {
  if (length < 2) return;
  const uint8_t flags = data[0];
  const uint16_t value = (flags & 0x01) == 0 ? data[1]
      : (length >= 3 ? static_cast<uint16_t>(data[1] | (data[2] << 8)) : 0);
  if (value < 30 || value > 240) return;
  bpm_ = value;
  lastMeasurementMs_ = millis();
}

}  // namespace rundeck
