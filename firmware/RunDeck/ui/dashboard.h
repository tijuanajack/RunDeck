#pragma once

#include <lvgl.h>

#include "../app/display_state.h"

namespace rundeck {

enum class MediaControlAction : uint8_t { Previous = 1, PlayPause = 2, Next = 3 };
enum class RunControlAction : uint8_t { Pause = 4, Resume = 5, Stop = 6 };

class Dashboard {
 public:
  void begin();
  void onPhoneConnected();
  void render(const DisplayState& state);
  void setMediaControlHandler(void (*handler)(MediaControlAction, void*), void* context);
  void setRunControlHandler(void (*handler)(RunControlAction, void*), void* context);
  void setNotificationDismissHandler(void (*handler)(uint16_t, void*), void* context);

 private:
  static void onGesture(lv_event_t* event);
  static void onStart(lv_event_t* event);
  static void onMediaPrevious(lv_event_t* event);
  static void onMediaPlayPause(lv_event_t* event);
  static void onMediaNext(lv_event_t* event);
  static void onRunPause(lv_event_t* event);
  static void onRunStop(lv_event_t* event);
  static void onTouchLock(lv_event_t* event);

  void emitMediaControl(MediaControlAction action);
  void emitRunControl(RunControlAction action);
  void emitNotificationDismiss(uint16_t sequence);
  void setTouchLocked(bool locked);

  void show(Screen page);
  void buildSplash();
  void buildDashboard();
  void buildMusic();
  void buildStats();
  void buildReady();
  void buildNotification();
  void buildRunControls();
  void updateNotificationOverlay();
  void updateDashboard();

  lv_obj_t* root_ = nullptr;
  lv_obj_t* content_ = nullptr;
  lv_obj_t* notification_ = nullptr;
  lv_obj_t* notificationApp_ = nullptr;
  lv_obj_t* notificationTitle_ = nullptr;
  lv_obj_t* notificationBody_ = nullptr;
  Screen page_ = Screen::Dashboard;
  DisplayState state_{};
  bool splashVisible_ = true;
  bool notificationDismissed_ = false;
  bool runControlsVisible_ = false;
  void (*mediaControlHandler_)(MediaControlAction, void*) = nullptr;
  void* mediaControlContext_ = nullptr;

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
  lv_obj_t* clock_ = nullptr;
  lv_obj_t* weather_ = nullptr;
  lv_obj_t* battery_ = nullptr;
  lv_obj_t* statsClock_ = nullptr;
  lv_obj_t* statsTemperature_ = nullptr;
  lv_obj_t* statsStatus_ = nullptr;
  lv_obj_t* statsStatusDetail_ = nullptr;
  lv_obj_t* statsValues_[8] = {};
  lv_obj_t* runControls_ = nullptr;
  lv_obj_t* runPauseButton_ = nullptr;
  lv_obj_t* touchLockButton_ = nullptr;
  lv_obj_t* touchLockHint_ = nullptr;
  bool touchLocked_ = false;
  bool unlockArmed_ = false;
  void (*runControlHandler_)(RunControlAction, void*) = nullptr;
  void* runControlContext_ = nullptr;
  void (*notificationDismissHandler_)(uint16_t, void*) = nullptr;
  void* notificationDismissContext_ = nullptr;
};

}  // namespace rundeck
