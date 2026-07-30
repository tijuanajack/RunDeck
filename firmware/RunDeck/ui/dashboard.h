#pragma once

#include <lvgl.h>

#include "../app/display_state.h"

namespace rundeck {

class Dashboard {
 public:
  void begin();
  void render(const DisplayState& state);

 private:
  static void onGesture(lv_event_t* event);
  static void onStart(lv_event_t* event);
  void setPage(Screen page);
  void buildDashboard();
  void buildMusic();
  void buildStats();
  void buildReady();
  void updatePage();

  lv_obj_t* root_ = nullptr;
  Screen page_ = Screen::Dashboard;
  DisplayState state_{};
  lv_obj_t* pace_ = nullptr;
  lv_obj_t* paceTarget_ = nullptr;
  lv_obj_t* status_ = nullptr;
  lv_obj_t* distance_ = nullptr;
  lv_obj_t* elapsed_ = nullptr;
  lv_obj_t* hr_ = nullptr;
  lv_obj_t* hrTarget_ = nullptr;
  lv_obj_t* topLine_ = nullptr;
  lv_obj_t* media_ = nullptr;
  lv_obj_t* notification_ = nullptr;
};

}  // namespace rundeck
