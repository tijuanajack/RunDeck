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

  void show(Screen page);
  void buildDashboard();
  void buildMusic();
  void buildStats();
  void buildReady();
  void buildNotification();
  void updateDashboard();

  lv_obj_t* root_ = nullptr;
  lv_obj_t* content_ = nullptr;
  lv_obj_t* notification_ = nullptr;
  Screen page_ = Screen::Dashboard;
  DisplayState state_{};
  bool notificationDismissed_ = false;

  lv_obj_t* pace_ = nullptr;
  lv_obj_t* paceTarget_ = nullptr;
  lv_obj_t* distance_ = nullptr;
  lv_obj_t* elapsed_ = nullptr;
  lv_obj_t* hr_ = nullptr;
  lv_obj_t* hrTarget_ = nullptr;
  lv_obj_t* media_ = nullptr;
  lv_obj_t* mediaSource_ = nullptr;
  lv_obj_t* mediaTrack_ = nullptr;
  lv_obj_t* mediaArtist_ = nullptr;
  lv_obj_t* mediaPlayButton_ = nullptr;
  lv_obj_t* musicFooter_ = nullptr;
};

}  // namespace rundeck
