#include "rundeck_ble.h"
#include "../board/waveshare_board.h"

#include <Arduino.h>
#include <NimBLEDevice.h>
#include <math.h>
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
constexpr uint8_t kDeviceEventAckType = 0x51;
constexpr uint8_t kDeviceEventMediaControlType = 0x52;
constexpr uint8_t kDeviceEventRunControlType = 0x53;
constexpr uint8_t kDeviceEventNotificationDismissedType = 0x54;
constexpr uint8_t kCommandRunState = 2;
constexpr uint8_t kCommandSettings = 7;
constexpr uint8_t kAckOk = 0;
constexpr size_t kHeaderBytes = 12;
constexpr size_t kLiveMetricsBytes = 21;
constexpr size_t kFrameBytes = kHeaderBytes + kLiveMetricsBytes;
constexpr size_t kRunStateMaxBytes = 128;
constexpr size_t kMediaMaxBytes = 160;
constexpr size_t kNotificationMaxBytes = 192;
constexpr uint8_t kNotificationFragmentType = 2;
constexpr size_t kNotificationFragmentHeaderBytes = 9;
constexpr size_t kNotificationFragmentChunkBytes = 11;
constexpr size_t kNotificationFragmentMaxBytes = kNotificationFragmentHeaderBytes + kNotificationFragmentChunkBytes;
constexpr uint32_t kNotificationAssemblyTimeoutMs = 5000;
constexpr size_t kDisplayContextMaxBytes = 96;
constexpr size_t kHeartbeatBytes = 9;
constexpr uint32_t kMetricsFreshForMs = 5000;
constexpr uint32_t kMediaFreshForMs = 30000;
constexpr uint32_t kNotificationVisibleForMs = 12000;
constexpr uint32_t kDisplayContextFreshForMs = 90000;
constexpr uint8_t kBatteryAdcPin = 17;
constexpr uint8_t kBatteryEnablePin = 16;

struct LiveMetrics {
  uint16_t flags;
  uint16_t paceSecondsPerMile;
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

struct MediaConfig {
  bool available;
  bool playing;
  uint16_t sequence;
  char source[17];
  char title[41];
  char artist[33];
};

struct NotificationConfig {
  uint16_t sequence;
  char app[17];
  char title[33];
  char body[97];
};

struct DisplayContextConfig {
  uint16_t sequence;
  uint8_t weatherState;
  bool temperatureAvailable;
  int8_t temperatureF;
  char clockLabel[9];
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
MediaConfig latestMedia{false, false, 0, "PHONE", "NO MEDIA", ""};
uint16_t lastMediaSequence = 0;
uint32_t mediaReceivedAtMs = 0;
bool haveMedia = false;
bool haveMediaSequence = false;
NotificationConfig latestNotification{0, "TEXT", "", ""};
uint16_t lastNotificationSequence = 0;
uint32_t notificationReceivedAtMs = 0;
bool haveNotification = false;
bool haveNotificationSequence = false;
uint8_t notificationAssembly[kNotificationMaxBytes] = {};
uint16_t notificationAssemblySequence = 0;
uint8_t notificationAssemblyIndex = 0;
uint8_t notificationAssemblyCount = 0;
uint16_t notificationAssemblyTotal = 0;
uint32_t notificationAssemblyStartedAtMs = 0;
bool notificationAssemblyActive = false;
DisplayContextConfig latestDisplayContext{0, 2, false, 0, "--:--"};
uint16_t lastDisplayContextSequence = 0;
uint32_t displayContextReceivedAtMs = 0;
bool haveDisplayContext = false;
bool haveDisplayContextSequence = false;
uint16_t lastSettingsSequence = 0;
uint32_t settingsReceivedAtMs = 0;
bool haveSettings = false;
bool haveSettingsSequence = false;
uint32_t heartbeatReceivedAtMs = 0;
uint32_t lastHeartbeatSourceMs = 0;
uint16_t lastHeartbeatSequence = 0;
bool haveHeartbeat = false;
bool haveHeartbeatSequence = false;
uint32_t batterySampledAtMs = 0;
uint8_t batteryPercent = 0;
bool batteryAvailable = false;
NimBLECharacteristic* deviceEventCharacteristic = nullptr;
uint16_t deviceEventSequence = 0;

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

bool decodeMediaState(const uint8_t* input, size_t size, MediaConfig* decoded) {
  if (size == 0 || size > kMediaMaxBytes) return false;
  CborReader reader(input, size);
  uint8_t entries = 0;
  if (!reader.readMap(&entries) || entries != 7) return false;

  uint32_t version = 0;
  uint32_t sequence = 0;
  MediaConfig candidate{};
  bool seenVersion = false, seenSequence = false, seenAvailable = false, seenPlaying = false;
  bool seenSource = false, seenTitle = false, seenArtist = false;

  for (uint8_t i = 0; i < entries; ++i) {
    uint32_t key = 0;
    if (!reader.readUInt(&key)) return false;
    switch (key) {
      case 0: seenVersion = reader.readUInt(&version); break;
      case 1: seenSequence = reader.readUInt(&sequence); break;
      case 2: seenAvailable = reader.readBool(&candidate.available); break;
      case 3: seenPlaying = reader.readBool(&candidate.playing); break;
      case 4: seenSource = reader.readText(candidate.source, sizeof(candidate.source)); break;
      case 5: seenTitle = reader.readText(candidate.title, sizeof(candidate.title)); break;
      case 6: seenArtist = reader.readText(candidate.artist, sizeof(candidate.artist)); break;
      default: return false;
    }
  }
  if (!reader.done() || !seenVersion || !seenSequence || !seenAvailable || !seenPlaying ||
      !seenSource || !seenTitle || !seenArtist) return false;
  if (version != kProtocolVersion || sequence > 0xFFFF) return false;
  candidate.sequence = static_cast<uint16_t>(sequence);
  *decoded = candidate;
  return true;
}

bool decodeNotification(const uint8_t* input, size_t size, NotificationConfig* decoded) {
  if (size == 0 || size > kNotificationMaxBytes) return false;
  CborReader reader(input, size);
  uint8_t entries = 0;
  if (!reader.readMap(&entries) || entries != 5) return false;

  uint32_t version = 0;
  uint32_t sequence = 0;
  NotificationConfig candidate{};
  bool seenVersion = false, seenSequence = false, seenApp = false, seenTitle = false, seenBody = false;

  for (uint8_t i = 0; i < entries; ++i) {
    uint32_t key = 0;
    if (!reader.readUInt(&key)) return false;
    switch (key) {
      case 0: seenVersion = reader.readUInt(&version); break;
      case 1: seenSequence = reader.readUInt(&sequence); break;
      case 2: seenApp = reader.readText(candidate.app, sizeof(candidate.app)); break;
      case 3: seenTitle = reader.readText(candidate.title, sizeof(candidate.title)); break;
      case 4: seenBody = reader.readText(candidate.body, sizeof(candidate.body)); break;
      default: return false;
    }
  }
  if (!reader.done() || !seenVersion || !seenSequence || !seenApp || !seenTitle || !seenBody) return false;
  if (version != kProtocolVersion || sequence > 0xFFFF) return false;
  candidate.sequence = static_cast<uint16_t>(sequence);
  *decoded = candidate;
  return true;
}

bool decodeDisplayContext(const uint8_t* input, size_t size, DisplayContextConfig* decoded) {
  if (size == 0 || size > kDisplayContextMaxBytes) return false;
  CborReader reader(input, size);
  uint8_t entries = 0;
  if (!reader.readMap(&entries) || entries != 6) return false;

  uint32_t version = 0, sequence = 0, weatherState = 0, temperatureOffset = 0;
  DisplayContextConfig candidate{};
  bool seenVersion = false, seenSequence = false, seenClock = false, seenWeather = false;
  bool seenTempAvailable = false, seenTempOffset = false;
  for (uint8_t i = 0; i < entries; ++i) {
    uint32_t key = 0;
    if (!reader.readUInt(&key)) return false;
    switch (key) {
      case 0: seenVersion = reader.readUInt(&version); break;
      case 1: seenSequence = reader.readUInt(&sequence); break;
      case 2: seenClock = reader.readText(candidate.clockLabel, sizeof(candidate.clockLabel)); break;
      case 3: seenWeather = reader.readUInt(&weatherState); break;
      case 4: seenTempAvailable = reader.readBool(&candidate.temperatureAvailable); break;
      case 5: seenTempOffset = reader.readUInt(&temperatureOffset); break;
      default: return false;
    }
  }
  if (!reader.done() || !seenVersion || !seenSequence || !seenClock || !seenWeather ||
      !seenTempAvailable || !seenTempOffset) return false;
  if (version != kProtocolVersion || sequence > 0xFFFF || weatherState > 3 || temperatureOffset > 300) return false;
  candidate.sequence = static_cast<uint16_t>(sequence);
  candidate.weatherState = static_cast<uint8_t>(weatherState);
  candidate.temperatureF = static_cast<int8_t>(static_cast<int>(temperatureOffset) - 100);
  *decoded = candidate;
  return true;
}

bool decodeProtocolSettings(const uint8_t* input, size_t size, uint16_t* sequence, uint8_t* brightnessOut) {
  if (size == 0 || size > kDisplayContextMaxBytes) return false;
  CborReader reader(input, size);
  uint8_t entries = 0;
  if (!reader.readMap(&entries) || (entries != 4 && entries != 5)) return false;
  uint32_t version = 0, decodedSequence = 0, maxFragment = 0, maxNotification = 0;
  bool seenVersion = false, seenSequence = false, seenFragment = false, seenNotification = false, seenBrightness = false;
  uint32_t brightness = 208;
  for (uint8_t i = 0; i < entries; ++i) {
    uint32_t key = 0;
    if (!reader.readUInt(&key)) return false;
    switch (key) {
      case 0: seenVersion = reader.readUInt(&version); break;
      case 1: seenSequence = reader.readUInt(&decodedSequence); break;
      case 2: seenFragment = reader.readUInt(&maxFragment); break;
      case 3: seenNotification = reader.readUInt(&maxNotification); break;
      case 4: seenBrightness = reader.readUInt(&brightness); break;
      default: return false;
    }
  }
  if (!reader.done() || !seenVersion || !seenSequence || !seenFragment || !seenNotification || (entries == 5 && !seenBrightness)) return false;
  if (version != kProtocolVersion || decodedSequence > 0xFFFF || maxFragment != kNotificationFragmentMaxBytes ||
      maxNotification != kNotificationMaxBytes || brightness < 16 || brightness > 255) return false;
  *sequence = static_cast<uint16_t>(decodedSequence);
  *brightnessOut = static_cast<uint8_t>(brightness);
  return true;
}

void notifyAck(uint16_t sequence, uint8_t commandType, uint8_t status) {
  if (!deviceEventCharacteristic) return;
  const uint8_t payload[] = {
      kProtocolVersion,
      kDeviceEventAckType,
      static_cast<uint8_t>(sequence & 0xFF),
      static_cast<uint8_t>(sequence >> 8),
      commandType,
      status,
      0,
      0,
  };
  deviceEventCharacteristic->setValue(payload, sizeof(payload));
}

void notifyDeviceMediaControl(MediaControlAction action) {
  if (!deviceEventCharacteristic) return;
  const uint16_t sequence = ++deviceEventSequence;
  const uint8_t payload[] = {
      kProtocolVersion,
      kDeviceEventMediaControlType,
      static_cast<uint8_t>(sequence & 0xFF),
      static_cast<uint8_t>(sequence >> 8),
      static_cast<uint8_t>(action),
      0,
      0,
      0,
  };
  deviceEventCharacteristic->setValue(payload, sizeof(payload));
  deviceEventCharacteristic->notify();
}

void notifyDeviceRunControl(RunControlAction action) {
  if (!deviceEventCharacteristic) return;
  const uint16_t sequence = ++deviceEventSequence;
  const uint8_t payload[] = {
      kProtocolVersion,
      kDeviceEventRunControlType,
      static_cast<uint8_t>(sequence & 0xFF),
      static_cast<uint8_t>(sequence >> 8),
      static_cast<uint8_t>(action),
      0,
      0,
      0,
  };
  deviceEventCharacteristic->setValue(payload, sizeof(payload));
  deviceEventCharacteristic->notify();
}

void notifyDeviceNotificationDismissed(uint16_t notificationSequence) {
  if (!deviceEventCharacteristic || notificationSequence == 0) return;
  const uint8_t payload[] = {
      kProtocolVersion,
      kDeviceEventNotificationDismissedType,
      static_cast<uint8_t>(notificationSequence & 0xFF),
      static_cast<uint8_t>(notificationSequence >> 8),
      0,
      0,
      0,
      0,
  };
  deviceEventCharacteristic->setValue(payload, sizeof(payload));
  deviceEventCharacteristic->notify();
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
    if (decoded.paceSecondsPerMile > 65535 ||
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
    bool accepted = false;
    if (!replayed) {
      latestRunState = decoded;
      lastRunStateSequence = decoded.sequence;
      runStateReceivedAtMs = millis();
      haveRunState = true;
      haveRunStateSequence = true;
      accepted = true;
    }
    portEXIT_CRITICAL(&metricsMux);
    if (accepted) notifyAck(decoded.sequence, kCommandRunState, kAckOk);
  }
};

RunStateCallbacks runStateCallbacks;

class MediaCallbacks : public NimBLECharacteristicCallbacks {
  void onWrite(NimBLECharacteristic* characteristic, NimBLEConnInfo&) override {
    const std::string value = characteristic->getValue();
    MediaConfig decoded{};
    if (!decodeMediaState(reinterpret_cast<const uint8_t*>(value.data()), value.size(), &decoded)) return;

    portENTER_CRITICAL(&metricsMux);
    const bool replayed = haveMediaSequence &&
        static_cast<int16_t>(decoded.sequence - lastMediaSequence) <= 0;
    if (!replayed) {
      latestMedia = decoded;
      lastMediaSequence = decoded.sequence;
      mediaReceivedAtMs = millis();
      haveMedia = true;
      haveMediaSequence = true;
    }
    portEXIT_CRITICAL(&metricsMux);
  }
};

MediaCallbacks mediaCallbacks;

class NotificationCallbacks : public NimBLECharacteristicCallbacks {
  void onWrite(NimBLECharacteristic* characteristic, NimBLEConnInfo&) override {
    const std::string value = characteristic->getValue();
    if (value.size() < kNotificationFragmentHeaderBytes || value.size() > kNotificationFragmentMaxBytes) return;
    const auto* input = reinterpret_cast<const uint8_t*>(value.data());
    if (input[0] != kProtocolVersion || input[1] != kNotificationFragmentType || input[8] != 0) return;
    const uint16_t sequence = u16(input + 2);
    const uint8_t index = input[4];
    const uint8_t count = input[5];
    const uint16_t total = u16(input + 6);
    const size_t chunk = value.size() - kNotificationFragmentHeaderBytes;
    const uint8_t expectedCount = static_cast<uint8_t>((total + kNotificationFragmentChunkBytes - 1) / kNotificationFragmentChunkBytes);
    if (total == 0 || total > kNotificationMaxBytes || count == 0 || count != expectedCount || index >= count ||
        (index + 1 < count && chunk != kNotificationFragmentChunkBytes) ||
        index * kNotificationFragmentChunkBytes + chunk > total) return;

    const uint32_t now = millis();
    if (!notificationAssemblyActive || now - notificationAssemblyStartedAtMs > kNotificationAssemblyTimeoutMs || index == 0) {
      if (index != 0) return;
      notificationAssemblyActive = true;
      notificationAssemblySequence = sequence;
      notificationAssemblyIndex = 0;
      notificationAssemblyCount = count;
      notificationAssemblyTotal = total;
      notificationAssemblyStartedAtMs = now;
    }
    if (!notificationAssemblyActive || sequence != notificationAssemblySequence || index != notificationAssemblyIndex ||
        count != notificationAssemblyCount || total != notificationAssemblyTotal) return;
    memcpy(notificationAssembly + index * kNotificationFragmentChunkBytes,
           input + kNotificationFragmentHeaderBytes, chunk);
    notificationAssemblyIndex++;
    if (notificationAssemblyIndex != notificationAssemblyCount) return;

    NotificationConfig decoded{};
    if (!decodeNotification(notificationAssembly, notificationAssemblyTotal, &decoded)) {
      notificationAssemblyActive = false;
      return;
    }

    portENTER_CRITICAL(&metricsMux);
    const bool replayed = haveNotificationSequence &&
        static_cast<int16_t>(decoded.sequence - lastNotificationSequence) <= 0;
    if (!replayed && decoded.sequence == sequence) {
      latestNotification = decoded;
      lastNotificationSequence = decoded.sequence;
      notificationReceivedAtMs = millis();
      haveNotification = true;
      haveNotificationSequence = true;
    }
    portEXIT_CRITICAL(&metricsMux);
    notificationAssemblyActive = false;
  }
};

NotificationCallbacks notificationCallbacks;

class DisplayContextCallbacks : public NimBLECharacteristicCallbacks {
  void onWrite(NimBLECharacteristic* characteristic, NimBLEConnInfo&) override {
    const std::string value = characteristic->getValue();
    DisplayContextConfig decoded{};
    if (!decodeDisplayContext(reinterpret_cast<const uint8_t*>(value.data()), value.size(), &decoded)) return;

    portENTER_CRITICAL(&metricsMux);
    const bool replayed = haveDisplayContextSequence &&
        static_cast<int16_t>(decoded.sequence - lastDisplayContextSequence) <= 0;
    if (!replayed) {
      latestDisplayContext = decoded;
      lastDisplayContextSequence = decoded.sequence;
      displayContextReceivedAtMs = millis();
      haveDisplayContext = true;
      haveDisplayContextSequence = true;
    }
    portEXIT_CRITICAL(&metricsMux);
  }
};

class SettingsCallbacks : public NimBLECharacteristicCallbacks {
  void onWrite(NimBLECharacteristic* characteristic, NimBLEConnInfo&) override {
    const std::string value = characteristic->getValue();
    const auto* input = reinterpret_cast<const uint8_t*>(value.data());
    if (!value.empty() && input[0] == 0xA5) {
      Serial.printf("RunDeck settings packet bytes=%u\n", static_cast<unsigned>(value.size()));
      uint16_t sequence = 0;
      uint8_t brightness = 208;
      if (!decodeProtocolSettings(input, value.size(), &sequence, &brightness)) {
        Serial.println("RunDeck settings rejected");
        return;
      }
      portENTER_CRITICAL(&metricsMux);
      const bool replayed = haveSettingsSequence && static_cast<int16_t>(sequence - lastSettingsSequence) <= 0;
      bool accepted = false;
      if (!replayed) {
        lastSettingsSequence = sequence;
        settingsReceivedAtMs = millis();
        haveSettings = true;
        haveSettingsSequence = true;
        accepted = true;
      }
      portEXIT_CRITICAL(&metricsMux);
      if (accepted) {
        Serial.printf("RunDeck settings brightness=%u\n", brightness);
        setDisplayBrightness(brightness);
      }
      if (accepted) notifyAck(sequence, kCommandSettings, kAckOk);
      return;
    }
    DisplayContextConfig decoded{};
    if (!decodeDisplayContext(input, value.size(), &decoded)) return;
    portENTER_CRITICAL(&metricsMux);
    const bool replayed = haveDisplayContextSequence &&
        static_cast<int16_t>(decoded.sequence - lastDisplayContextSequence) <= 0;
    if (!replayed) {
      latestDisplayContext = decoded;
      lastDisplayContextSequence = decoded.sequence;
      displayContextReceivedAtMs = millis();
      haveDisplayContext = true;
      haveDisplayContextSequence = true;
    }
    portEXIT_CRITICAL(&metricsMux);
  }
};

SettingsCallbacks settingsCallbacks;

class HeartbeatCallbacks : public NimBLECharacteristicCallbacks {
  void onWrite(NimBLECharacteristic* characteristic, NimBLEConnInfo&) override {
    const std::string value = characteristic->getValue();
    if (value.size() != kHeartbeatBytes) return;
    const auto* input = reinterpret_cast<const uint8_t*>(value.data());
    if (input[0] != kProtocolVersion || u16(input + 7) != 0) return;
    const uint16_t sequence = u16(input + 1);
    const uint32_t sourceMs = u32(input + 3);
    portENTER_CRITICAL(&metricsMux);
    const bool replayed = haveHeartbeatSequence && static_cast<int16_t>(sequence - lastHeartbeatSequence) <= 0;
    const bool olderSource = haveHeartbeat && static_cast<int32_t>(sourceMs - lastHeartbeatSourceMs) <= 0;
    if (!replayed && !olderSource) {
      lastHeartbeatSequence = sequence;
      lastHeartbeatSourceMs = sourceMs;
      heartbeatReceivedAtMs = millis();
      haveHeartbeat = true;
      haveHeartbeatSequence = true;
    }
    portEXIT_CRITICAL(&metricsMux);
  }
};

HeartbeatCallbacks heartbeatCallbacks;

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
  NimBLECharacteristic* media = service->createCharacteristic(
      kMediaUuid, NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::WRITE_NR);
  media->setCallbacks(&mediaCallbacks);
  NimBLECharacteristic* notification = service->createCharacteristic(
      kNotificationUuid, NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::WRITE_NR);
  notification->setCallbacks(&notificationCallbacks);
  deviceEventCharacteristic = service->createCharacteristic(
      kDeviceEventUuid, NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY | NIMBLE_PROPERTY::WRITE);
  NimBLECharacteristic* settings = service->createCharacteristic(
      kSettingsUuid, NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::WRITE_NR);
  settings->setCallbacks(&settingsCallbacks);
  NimBLECharacteristic* heartbeat = service->createCharacteristic(
      kHeartbeatUuid, NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::NOTIFY);
  heartbeat->setCallbacks(&heartbeatCallbacks);
  pinMode(kBatteryEnablePin, OUTPUT);
  digitalWrite(kBatteryEnablePin, HIGH);
  analogReadResolution(12);
  analogSetPinAttenuation(kBatteryAdcPin, ADC_11db);
  service->start();
  NimBLEAdvertising* advertising = NimBLEDevice::getAdvertising();
  advertising->setName("RunDeck");
  advertising->addServiceUUID(kServiceUuid);
  NimBLEDevice::startAdvertising();
  Serial.println("RunDeck BLE advertising");
}

void RunDeckBle::applyRunState(DisplayState* state, uint32_t nowMs) {
  (void)nowMs;
  bool valid = false;
  portENTER_CRITICAL(&metricsMux);
  valid = haveRunState;
  state->runActive = valid && latestRunState.active;
  if (!state->runActive) state->runPaused = false;
  if (valid) {
    state->presetName = latestRunState.presetName;
    state->targetLabel = latestRunState.targetLabel;
    state->heartRateLowBpm = latestRunState.hrLowBpm;
    state->heartRateHighBpm = latestRunState.hrHighBpm;
  }
  portEXIT_CRITICAL(&metricsMux);
}

void RunDeckBle::applyMediaState(DisplayState* state, uint32_t nowMs) {
  uint32_t receivedAt = 0;
  bool valid = false;
  bool available = false;
  bool playing = false;
  const char* source = "PHONE";
  const char* title = "NO MEDIA";
  const char* artist = "";
  portENTER_CRITICAL(&metricsMux);
  valid = haveMedia;
  receivedAt = mediaReceivedAtMs;
  if (valid) {
    available = latestMedia.available;
    playing = latestMedia.playing;
    source = latestMedia.source;
    title = latestMedia.title;
    artist = latestMedia.artist;
  }
  portEXIT_CRITICAL(&metricsMux);

  if (!valid) {
    state->media = {SourceState::Unavailable, nowMs};
    state->mediaPlaying = false;
    state->mediaSource = "PHONE";
    state->mediaTitle = "NO MEDIA";
    state->mediaArtist = "";
    return;
  }

  const bool fresh = nowMs - receivedAt <= kMediaFreshForMs;
  state->media = {fresh ? (available ? SourceState::Connected : SourceState::Unavailable)
                        : SourceState::Stale,
                  receivedAt};
  // MediaSession metadata is event-driven: many players do not re-send title
  // and artist while a song continues. Keep the last known metadata visible
  // even if the source timestamp is stale; only the source state ages.
  state->mediaPlaying = available && playing;
  state->mediaSource = source;
  state->mediaTitle = available ? title : "NO MEDIA";
  state->mediaArtist = artist;
}

void RunDeckBle::applyNotificationState(DisplayState* state, uint32_t nowMs) {
  uint32_t receivedAt = 0;
  uint16_t sequence = 0;
  bool valid = false;
  const char* app = "";
  const char* title = "";
  const char* body = "";
  portENTER_CRITICAL(&metricsMux);
  valid = haveNotification;
  receivedAt = notificationReceivedAtMs;
  sequence = lastNotificationSequence;
  if (valid) {
    app = latestNotification.app;
    title = latestNotification.title;
    body = latestNotification.body;
  }
  portEXIT_CRITICAL(&metricsMux);

  const bool visible = valid && nowMs - receivedAt <= kNotificationVisibleForMs;
  state->notificationVisible = visible;
  state->notificationSequence = valid ? sequence : 0;
  if (visible) {
    state->notificationApp = app;
    state->notificationTitle = title;
    state->notificationBody = body;
  }
}

void RunDeckBle::applyDisplayContext(DisplayState* state, uint32_t nowMs) {
  uint32_t receivedAt = 0;
  bool valid = false;
  bool temperatureAvailable = false;
  uint8_t weatherState = 2;
  int8_t temperatureF = -128;
  portENTER_CRITICAL(&metricsMux);
  valid = haveDisplayContext;
  receivedAt = displayContextReceivedAtMs;
  weatherState = latestDisplayContext.weatherState;
  temperatureAvailable = latestDisplayContext.temperatureAvailable;
  temperatureF = latestDisplayContext.temperatureF;
  if (valid) state->clockLabel = latestDisplayContext.clockLabel;
  portEXIT_CRITICAL(&metricsMux);

  const bool fresh = valid && nowMs - receivedAt <= kDisplayContextFreshForMs;
  state->clock = {fresh ? SourceState::Connected : SourceState::Stale, receivedAt};
  state->weather = {fresh ? static_cast<SourceState>(weatherState) : SourceState::Stale, receivedAt};
  if (!fresh) state->clockLabel = "TIME OFFLINE";
  state->temperatureF = (fresh && temperatureAvailable) ? temperatureF : -128;
}

void RunDeckBle::applyBatteryState(DisplayState* state, uint32_t nowMs) {
  if (nowMs - batterySampledAtMs >= 30000 || batterySampledAtMs == 0) {
    batterySampledAtMs = nowMs;
    const uint32_t adcMillivolts = analogReadMilliVolts(kBatteryAdcPin);
    const float batteryVolts = (adcMillivolts * 3.0f) / 1000.0f;
    if (batteryVolts >= 3.0f && batteryVolts <= 5.5f) {
      const float fraction = (batteryVolts - 3.30f) / (4.20f - 3.30f);
      batteryPercent = static_cast<uint8_t>(constrain(lroundf(fraction * 100.0f), 0L, 100L));
      batteryAvailable = true;
    } else {
      batteryAvailable = false;
    }
  }
  state->batteryAvailable = batteryAvailable;
  state->batteryPercent = batteryPercent;
}

void RunDeckBle::notifyMediaControl(MediaControlAction action) {
  notifyDeviceMediaControl(action);
}

void RunDeckBle::notifyRunControl(RunControlAction action) {
  notifyDeviceRunControl(action);
}

void RunDeckBle::notifyNotificationDismissed(uint16_t notificationSequence) {
  notifyDeviceNotificationDismissed(notificationSequence);
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
  state->metricFlags = metrics.flags;
  state->gps = {SourceState::Connected, receivedAt};
  state->heartRate = {metrics.heartRateBpm ? SourceState::Connected : SourceState::Unavailable, receivedAt};
  state->paceMinutesPerMile = metrics.paceSecondsPerMile / 60.0f;
  state->distanceMiles = metrics.distanceCentimeters / 160934.4f;
  state->elapsedSeconds = metrics.elapsedSeconds;
  state->heartRateBpm = metrics.heartRateBpm;
  state->runPaused = (metrics.flags & 0x0020) != 0;
  state->temperatureF = static_cast<int8_t>(metrics.temperatureDeciF / 10);
  applyRunState(state, nowMs);
  if (metrics.flags & 0x0080) state->statusText = (metrics.flags & 0x0008) ? "EASE OFF" : "BACK OFF";
  else if (metrics.flags & 0x0004) state->statusText = "ON TARGET";
  else if (metrics.flags & 0x0008) state->statusText = "EASE OFF";
  else if (metrics.flags & 0x0010) state->statusText = "PICK IT UP";
  else state->statusText = "GPS WEAK";
  return true;
}

bool RunDeckBle::phoneSessionSeen() const {
  bool seen = false;
  portENTER_CRITICAL(&metricsMux);
  seen = haveMetrics || haveRunState || haveMedia || haveNotification || haveDisplayContext;
  portEXIT_CRITICAL(&metricsMux);
  return seen;
}

}  // namespace rundeck
