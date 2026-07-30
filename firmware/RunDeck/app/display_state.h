#pragma once

#include <stdint.h>

namespace rundeck {

enum class SourceState : uint8_t { Connected, Stale, Unavailable, Error };
enum class Screen : uint8_t { Ready, Dashboard, Music, Stats };

struct FreshValue {
  SourceState state;
  uint32_t updatedAtMs;
};

struct DisplayState {
  Screen screen;
  FreshValue phone;
  FreshValue gps;
  FreshValue heartRate;
  FreshValue media;
  float paceMinutesPerMile;
  float distanceMiles;
  uint16_t heartRateBpm;
  uint32_t elapsedSeconds;
  int8_t temperatureF;
  bool mediaPlaying;
  bool notificationVisible;
  const char* statusText;
  const char* presetName;
  const char* targetLabel;
  const char* mediaTitle;
  const char* mediaArtist;
};

}  // namespace rundeck
