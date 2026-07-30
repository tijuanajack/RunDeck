#include "simulated_data.h"

#include <math.h>

namespace rundeck {

DisplayState SimulatedData::next(uint32_t nowMs) {
  const float seconds = nowMs / 1000.0f;
  const bool weakGps = static_cast<uint32_t>(seconds) % 47 >= 42;
  const bool strapLost = static_cast<uint32_t>(seconds) % 89 >= 85;
  const float pace = 8.50f + sinf(seconds / 8.0f) * 0.18f;
  const uint16_t hr = static_cast<uint16_t>(143 + sinf(seconds / 5.0f) * 5);
  return {
      Screen::Dashboard,
      {SourceState::Connected, nowMs},
      {weakGps ? SourceState::Stale : SourceState::Connected, nowMs},
      {strapLost ? SourceState::Stale : SourceState::Connected, nowMs},
      {SourceState::Connected, nowMs},
      pace,
      seconds / 8.7f / 5280.0f,
      hr,
      static_cast<uint32_t>(seconds),
      72,
      static_cast<uint32_t>(seconds) % 16 < 8,
      static_cast<uint32_t>(seconds) % 30 >= 24 && static_cast<uint32_t>(seconds) % 30 < 29,
      weakGps ? "GPS WEAK" : (strapLost ? "STRAP LOST" : "ON TARGET"),
      "Midnight City",
      "M83",
  };
}

}  // namespace rundeck
