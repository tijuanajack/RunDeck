#pragma once

#include "display_state.h"

namespace rundeck {

class SimulatedData {
 public:
  DisplayState next(uint32_t nowMs);
};

}  // namespace rundeck
