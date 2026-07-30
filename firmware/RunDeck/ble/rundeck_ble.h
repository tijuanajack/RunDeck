#pragma once

#include <stdint.h>

#include "../app/display_state.h"

namespace rundeck {

/** RunDeck's v1 BLE peripheral service. Android owns the metric sources. */
class RunDeckBle {
 public:
  void begin();
  void applyLiveMetrics(DisplayState* state, uint32_t nowMs);
};

}  // namespace rundeck
