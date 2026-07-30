#include "rundeck_ble.h"

#include <Arduino.h>
#include <NimBLEDevice.h>
#include <string.h>

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
constexpr size_t kRunStateMaxBytes = 128;
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

struct RunStateConfig {
  bool active;
  uint16_t sequence;
  uint16_t paceLowSecondsPerMile;
  uint16_t paceHighSecondsPerMile;
  uint8_t hrLowBpm;
  uint8_t hrHighBpm;
  char presetName[21];
  char targetLabel[29];
};

portMUX_TYPE metricsMux = portMUX_INITIALIZER_UNLOCKED;
LiveMetrics latest{};
uint32_t receivedAtMs = 0;
uint16_t lastSequence = 0;
uint32_t lastSourceMs = 0;
bool haveMetrics = false;
bool haveSequence = false;
RunStateConfig latestRunState{false, 0, 530, 560, 135, 150, "LONG RUN", "8:50-9:20"};
uint16_t lastRunStateSequence = 0;
uint32_t runStateReceivedAtMs = 0;
bool haveRunState = false;
bool haveRunStateSequence = false;

uint16_t u16(const uint8_t* input) { return static_cast<uint16_t>(input[0] | (input[1] << 8)); }
uint32_t u32(const uint8_t* input) {
  return static_cast<uint32_t>(input[0]) | (static_cast<uint32_t>(input[1]) << 8) |
         (static_cast<uint32_t>(input[2]) << 16) | (static_cast<uint32_t>(input[3]) << 24);
}

class CborReader {
 public:
  CborReader(const uint8_t* input, size_t size) : input_(input), size_(size) {}

  bool done() const { return offset_ == size_; }

  bool readMap(uint8_t* entries) {
    uint8_t first = 0;
    if (!readByte(&first) || (first >> 5) != 5 || (first & 0x1F) > 23) return false;
    *entries = first & 0x1F;
    return true;
  }

  bool readUInt(uint32_t* value) {
    uint8_t first = 0;
    if (!readByte(&first) || (first >> 5) != 0) return false;
    return readArgument(first, value);
  }

  bool readBool(bool* value) {
    uint8_t first = 0;
    if (!readByte(&first)) return false;
    if (first == 0xF4) {
      *value = false;
      return true;
    }
    if (first == 0xF5) {
      *value = true;
      return true;
    }
    return false;
  }

  bool readText(char* output, size_t capacity) {
    uint8_t first = 0;
    if (!readByte(&first) || (first >> 5) != 3) return false;
    uint32_t length = 0;
    if (!readArgument(first, &length) || length >= capacity || offset_ + length > size_) return false;
    memcpy(output, input_ + offset_, length);
    output[length] = '\0';
    offset_ += length;
    return true;
  }

 private:
  bool readArgument(uint8_t first, uint32_t* value) {
    const uint8_t additional = first & 0x1F;
    if (additional <= 23) {
      *value = additional;
      return true;
    }
    if (additional == 24) {
      uint8_t byte = 0;
      if (!readByte(&byte)) return false;
      *value = byte;
      return true;
    }
    if (additional == 25) {
      uint8_t high = 0, low = 0;
      if (!readByte(&high) || !readByte(&low)) return false;
      *value = (static_cast<uint32_t>(high) << 8) | low;
      return true;
    }
    return false;
  }

  bool readByte(uint8_t* value) {
    if (offset_ >= size_) return false;
    *value = input_[offset_++];
    return true;
  }

  const uint8_t* input_;
  size_t size_;
  size_t offset_ = 0;
};

bool decodeRunState(const uint8_t* input, size_t size, RunStateConfig* decoded) {
  if (size == 0 || size > kRunStateMaxBytes) return false;
  CborReader reader(input, size);
  uint8_t entries = 0;
  if (!reader.readMap(&entries) || entries != 9) return false;

  uint32_t version = 0;
  uint32_t sequence = 0;
  uint32_t paceLow = 0;
  uint32_t paceHigh = 0;
  uint32_t hrLow = 0;
  uint32_t hrHigh = 0;
  RunStateConfig candidate{};
  bool seenVersion = false, seenSequence = false, seenActive = false, seenPreset = false;
  bool seenTarget = false, seenPaceLow = false, seenPaceHigh = false, seenHrLow = false, seenHrHigh = false;

  for (uint8_t i = 0; i < entries; ++i) {
    uint32_t key = 0;
    if (!reader.readUInt(&key)) return false;
    switch (key) {
      case 0: seenVersion = reader.readUInt(&version); break;
      case 1: seenSequence = reader.readUInt(&sequence); break;
      case 2: seenActive = reader.readBool(&candidate.active); break;
      case 3: seenPreset = reader.readText(candidate.presetName, sizeof(candidate.presetName)); break;
      case 4: seenTarget = reader.readText(candidate.targetLabel, sizeof(candidate.targetLabel)); break;
      case 5: seenPaceLow = reader.readUInt(&paceLow); break;
      case 6: seenPaceHigh = reader.readUInt(&paceHigh); break;
      case 7: seenHrLow = reader.readUInt(&hrLow); break;
      case 8: seenHrHigh = reader.readUInt(&hrHigh); break;
      default: return false;
    }
  }
  if (!reader.done() || !seenVersion || !seenSequence || !seenActive || !seenPreset || !seenTarget ||
      !seenPaceLow || !seenPaceHigh || !seenHrLow || !seenHrHigh) return false;
  if (version != kProtocolVersion || sequence > 0xFFFF || paceLow < 180 || paceHigh > 1800 ||
      paceLow > paceHigh || hrLow > 240 || hrHigh > 240 || hrLow > hrHigh) return false;
  candidate.sequence = static_cast<uint16_t>(sequence);
  candidate.paceLowSecondsPerMile = static_cast<uint16_t>(paceLow);
  candidate.paceHighSecondsPerMile = static_cast<uint16_t>(paceHigh);
  candidate.hrLowBpm = static_cast<uint8_t>(hrLow);
  candidate.hrHighBpm = static_cast<uint8_t>(hrHigh);
  *decoded = candidate;
  return true;
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

class RunStateCallbacks : public NimBLECharacteristicCallbacks {
  void onWrite(NimBLECharacteristic* characteristic, NimBLEConnInfo&) override {
    const std::string value = characteristic->getValue();
    RunStateConfig decoded{};
    if (!decodeRunState(reinterpret_cast<const uint8_t*>(value.data()), value.size(), &decoded)) return;

    portENTER_CRITICAL(&metricsMux);
    const bool replayed = haveRunStateSequence &&
        static_cast<int16_t>(decoded.sequence - lastRunStateSequence) <= 0;
    if (!replayed) {
      latestRunState = decoded;
      lastRunStateSequence = decoded.sequence;
      runStateReceivedAtMs = millis();
      haveRunState = true;
      haveRunStateSequence = true;
    }
    portEXIT_CRITICAL(&metricsMux);
  }
};

RunStateCallbacks runStateCallbacks;

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
  NimBLECharacteristic* runState = service->createCharacteristic(
      kRunStateUuid, NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::WRITE_NR);
  runState->setCallbacks(&runStateCallbacks);
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

void RunDeckBle::applyRunState(DisplayState* state, uint32_t nowMs) {
  (void)nowMs;
  RunStateConfig config{};
  bool valid = false;
  portENTER_CRITICAL(&metricsMux);
  valid = haveRunState;
  config = latestRunState;
  portEXIT_CRITICAL(&metricsMux);
  if (!valid) return;
  state->presetName = config.presetName;
  state->targetLabel = config.targetLabel;
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
  applyRunState(state, nowMs);
  // Notifications are not yet supplied by Android. Never leak the simulator's
  // periodic mock text overlay into a real live run.
  state->notificationVisible = false;
  if (metrics.flags & 0x0004) state->statusText = "ON TARGET";
  else if (metrics.flags & 0x0008) state->statusText = "EASE OFF";
  else if (metrics.flags & 0x0010) state->statusText = "PICK IT UP";
  else state->statusText = "GPS WEAK";
  return true;
}

}  // namespace rundeck
