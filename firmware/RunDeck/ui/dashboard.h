#pragma once

#include <lvgl.h>

#include "../app/display_state.h"

namespace rundeck {

class Dashboard {
 public:
  void begin();
  void render(const DisplayState& state);

 private:
  lv_obj_t* pace_ = nullptr;
  lv_obj_t* status_ = nullptr;
  lv_obj_t* metrics_ = nullptr;
  lv_obj_t* media_ = nullptr;
  lv_obj_t* notification_ = nullptr;
};

}  // namespace rundeck
