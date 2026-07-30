#include "rundeck_ble.h"

#include <Arduino.h>
#include <NimBLEDevice.h>

namespace rundeck {
namespace {
constexpr char kServiceUuid[] = "7b2e0000-6d1f-4a91-8a5f-6c796a25a000";
constexpr char kLiveMetricsUuid[] = "7b2e0001-6d1f-4a91-8a5f-6c796a25a000";
constexpr char kRunStateUuid[] = "7b2e0002-6d1f-4a91-8a5f-6c796a25a000";
constexpr char kMediaUuid[] = "7b2e0003-6d1f-4a91-8a5f-6c796a25a000";
constexpr char kNotificationUuid[] = "7b2e0004-6d1f-4a91-8a5f-6c796a25a000";
constexpr char kDeviceEventUuid[] = "7b2e0005-6d1f-4a91-8a5f-6c796a25a000";
constexpr char kSettingsUuid[] = "7b2e0006-6d1f-4a91-8a5f-6c796a25a000";
constexpr char kHeartbeatUuid[] = "7b2e0007-6d1f-4a91-8a5f-6c796a25a000";
constexpr uint8_t kProtocolVersion = 1;
constexpr uint8_t kLiveMetricsType = 1;
constexpr size_t kHeaderBytes = 12;
constexpr size_t kLiveMetricsBytes = 21;
constexpr size_t kFrameBytes = kHeaderBytes + kLiveMetricsBytes;
constexpr uint32_t kMetricsFreshForMs = 5000;

struct LiveMetrics {
  uint16_t flags;
  uint16_t paceCentisecondsPerMile;
  uint32_t distanceCentimeters;
  uint32_t elapsedSeconds;
  uint32_t movingSeconds;
  uint16_t speedCentimetersPerSecond;
  int16_t temperatureDeciF;
  uint8_t heartRateBpm;
};

portMUX_TYPE metricsMux = portMUX_INITIALIZER_UNLOCKED;
LiveMetrics latest{};
uint32_t receivedAtMs = 0;
uint16_t lastSequence = 0;
uint32_t lastSourceMs = 0;
bool haveMetrics = false;
bool haveSequence = false;

uint16_t u16(const uint8_t* input) { return static_cast<uint16_t>(input[0] | (input[1] << 8)); }
uint32_t u32(const uint8_t* input) {
  return static_cast<uint32_t>(input[0]) | (static_cast<uint32_t>(input[1]) << 8) |
         (static_cast<uint32_t>(input[2]) << 16) | (static_cast<uint32_t>(input[3]) << 24);
}

class LiveMetricsCallbacks : public NimBLECharacteristicCallbacks {
  void onWrite(NimBLECharacteristic* characteristic, NimBLEConnInfo&) override {
    const std::string value = characteristic->getValue();
    if (value.size() != kFrameBytes) return;
    const auto* input = reinterpret_cast<const uint8_t*>(value.data());
    if (input[0] != kProtocolVersion || input[1] != kLiveMetricsType ||
        u16(input + 8) != kLiveMetricsBytes || u16(input + 10) != 0) return;
    const uint16_t sequence = u16(input + 2);
    const uint32_t sourceMs = u32(input + 4);
    LiveMetrics decoded{
        u16(input + 12), u16(input + 14), u32(input + 16), u32(input + 20), u32(input + 24),
        u16(input + 28), static_cast<int16_t>(u16(input + 30)), input[32]};
    if (decoded.paceCentisecondsPerMile > 300000 ||
        decoded.heartRateBpm > 240) return;

    portENTER_CRITICAL(&metricsMux);
    const bool replayed = haveSequence && static_cast<int16_t>(sequence - lastSequence) <= 0;
    const bool olderSource = haveSequence && sourceMs < lastSourceMs;
    if (!replayed && !olderSource) {
      latest = decoded;
      receivedAtMs = millis();
      lastSequence = sequence;
      lastSourceMs = sourceMs;
      haveMetrics = true;
      haveSequence = true;
    }
    portEXIT_CRITICAL(&metricsMux);
  }
};

LiveMetricsCallbacks liveMetricsCallbacks;

class ServerCallbacks : public NimBLEServerCallbacks {
  void onDisconnect(NimBLEServer*, NimBLEConnInfo&, int) override {
    NimBLEDevice::startAdvertising();
  }
};

ServerCallbacks serverCallbacks;

void addWritable(NimBLEService* service, const char* uuid) {
  service->createCharacteristic(uuid, NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::WRITE_NR);
}
}  // namespace

void RunDeckBle::begin() {
  NimBLEDevice::init("RunDeck");
  NimBLEServer* server = NimBLEDevice::createServer();
  server->setCallbacks(&serverCallbacks);
  NimBLEService* service = server->createService(kServiceUuid);
  NimBLECharacteristic* live = service->createCharacteristic(
      kLiveMetricsUuid, NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::WRITE_NR | NIMBLE_PROPERTY::NOTIFY);
  live->setCallbacks(&liveMetricsCallbacks);
  addWritable(service, kRunStateUuid);
  addWritable(service, kMediaUuid);
  addWritable(service, kNotificationUuid);
  service->createCharacteristic(kDeviceEventUuid, NIMBLE_PROPERTY::NOTIFY | NIMBLE_PROPERTY::WRITE);
  service->createCharacteristic(kSettingsUuid, NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::WRITE);
  service->createCharacteristic(kHeartbeatUuid, NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::NOTIFY);
  service->start();
  NimBLEAdvertising* advertising = NimBLEDevice::getAdvertising();
  advertising->setName("RunDeck");
  advertising->addServiceUUID(kServiceUuid);
  NimBLEDevice::startAdvertising();
  Serial.println("RunDeck BLE advertising");
}

bool RunDeckBle::applyLiveMetrics(DisplayState* state, uint32_t nowMs) {
  LiveMetrics metrics{};
  uint32_t receivedAt = 0;
  bool valid = false;
  portENTER_CRITICAL(&metricsMux);
  valid = haveMetrics;
  metrics = latest;
  receivedAt = receivedAtMs;
  portEXIT_CRITICAL(&metricsMux);
  if (!valid || nowMs - receivedAt > kMetricsFreshForMs) return false;

  state->phone = {SourceState::Connected, receivedAt};
  state->gps = {SourceState::Connected, receivedAt};
  state->heartRate = {metrics.heartRateBpm ? SourceState::Connected : SourceState::Unavailable, receivedAt};
  state->paceMinutesPerMile = metrics.paceCentisecondsPerMile / 6000.0f;
  state->distanceMiles = metrics.distanceCentimeters / 160934.4f;
  state->elapsedSeconds = metrics.elapsedSeconds;
  state->heartRateBpm = metrics.heartRateBpm;
  state->temperatureF = static_cast<int8_t>(metrics.temperatureDeciF / 10);
  if (metrics.flags & 0x0004) state->statusText = "ON TARGET";
  else if (metrics.flags & 0x0008) state->statusText = "EASE OFF";
  else if (metrics.flags & 0x0010) state->statusText = "PICK IT UP";
  else state->statusText = "GPS WEAK";
  return true;
}

}  // namespace rundeck
