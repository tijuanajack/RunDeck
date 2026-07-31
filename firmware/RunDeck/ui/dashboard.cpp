#include "dashboard.h"

#include <math.h>
#include <stdio.h>
#include <string.h>

namespace rundeck {
namespace {
const lv_color_t kWhite = lv_color_hex(0xF6F7F8);
const lv_color_t kMuted = lv_color_hex(0xA5A8AD);
const lv_color_t kLine = lv_color_hex(0x282C31);
const lv_color_t kLime = lv_color_hex(0x9AF000);
const lv_color_t kCyan = lv_color_hex(0x38D7E4);
const lv_color_t kGreen = lv_color_hex(0x58D66D);
const lv_color_t kAmber = lv_color_hex(0xF8C433);
const lv_color_t kRed = lv_color_hex(0xF04444);

lv_obj_t* label(lv_obj_t* parent, const lv_font_t* font, lv_align_t align, int x, int y,
                lv_color_t color = kWhite) {
  lv_obj_t* value = lv_label_create(parent);
  lv_obj_remove_style_all(value);
  lv_obj_set_style_text_font(value, font, 0);
  lv_obj_set_style_text_color(value, color, 0);
  lv_obj_set_style_text_letter_space(value, 1, 0);
  lv_obj_align(value, align, x, y);
  return value;
}

lv_obj_t* line(lv_obj_t* parent, int x, int y, int width, lv_color_t color = kLine) {
  lv_obj_t* value = lv_obj_create(parent);
  lv_obj_remove_style_all(value);
  lv_obj_set_pos(value, x, y);
  lv_obj_set_size(value, width, 1);
  lv_obj_set_style_bg_color(value, color, 0);
  lv_obj_set_style_bg_opa(value, LV_OPA_COVER, 0);
  return value;
}

lv_obj_t* panel(lv_obj_t* parent, int x, int y, int width, int height, lv_color_t border) {
  lv_obj_t* value = lv_obj_create(parent);
  lv_obj_remove_style_all(value);
  lv_obj_set_pos(value, x, y);
  lv_obj_set_size(value, width, height);
  lv_obj_set_style_bg_color(value, lv_color_hex(0x050607), 0);
  lv_obj_set_style_bg_opa(value, LV_OPA_COVER, 0);
  lv_obj_set_style_border_color(value, border, 0);
  lv_obj_set_style_border_width(value, 2, 0);
  lv_obj_set_style_radius(value, 14, 0);
  lv_obj_clear_flag(value, LV_OBJ_FLAG_SCROLLABLE);
  return value;
}

int pageIndex(Screen page) {
  switch (page) {
    case Screen::Ready: return 0;
    case Screen::Dashboard: return 1;
    case Screen::Music: return 2;
    case Screen::Stats: return 3;
  }
  return 1;
}

Screen pageForIndex(int index) {
  static constexpr Screen kPages[] = {Screen::Ready, Screen::Dashboard, Screen::Music, Screen::Stats};
  return kPages[(index + 4) % 4];
}
}  // namespace

void Dashboard::begin() {
  root_ = lv_scr_act();
  lv_obj_remove_style_all(root_);
  lv_obj_set_style_bg_color(root_, lv_color_black(), 0);
  lv_obj_set_style_bg_opa(root_, LV_OPA_COVER, 0);
  lv_obj_clear_flag(root_, LV_OBJ_FLAG_SCROLLABLE);
  lv_obj_add_event_cb(root_, onGesture, LV_EVENT_GESTURE, this);

  content_ = lv_obj_create(root_);
  lv_obj_remove_style_all(content_);
  lv_obj_set_size(content_, 600, 450);
  lv_obj_clear_flag(content_, LV_OBJ_FLAG_SCROLLABLE);
  lv_obj_add_flag(content_, LV_OBJ_FLAG_GESTURE_BUBBLE);
  show(Screen::Dashboard);
}

void Dashboard::onGesture(lv_event_t* event) {
  auto* self = static_cast<Dashboard*>(lv_event_get_user_data(event));
  lv_indev_t* input = lv_indev_get_act();
  if (!self || !input) return;
  const lv_dir_t direction = lv_indev_get_gesture_dir(input);
  if (self->notification_ && !lv_obj_has_flag(self->notification_, LV_OBJ_FLAG_HIDDEN)) {
    if (direction == LV_DIR_BOTTOM) {
      self->notificationDismissed_ = true;
      lv_obj_add_flag(self->notification_, LV_OBJ_FLAG_HIDDEN);
    }
    return;
  }
  if (direction == LV_DIR_LEFT) self->show(pageForIndex(pageIndex(self->page_) + 1));
  if (direction == LV_DIR_RIGHT) self->show(pageForIndex(pageIndex(self->page_) - 1));
}

void Dashboard::onStart(lv_event_t* event) {
  auto* self = static_cast<Dashboard*>(lv_event_get_user_data(event));
  if (self) self->show(Screen::Dashboard);
}

void Dashboard::onMediaPrevious(lv_event_t* event) {
  auto* self = static_cast<Dashboard*>(lv_event_get_user_data(event));
  if (self) self->emitMediaControl(MediaControlAction::Previous);
}

void Dashboard::onMediaPlayPause(lv_event_t* event) {
  auto* self = static_cast<Dashboard*>(lv_event_get_user_data(event));
  if (self) self->emitMediaControl(MediaControlAction::PlayPause);
}

void Dashboard::onMediaNext(lv_event_t* event) {
  auto* self = static_cast<Dashboard*>(lv_event_get_user_data(event));
  if (self) self->emitMediaControl(MediaControlAction::Next);
}

void Dashboard::setMediaControlHandler(void (*handler)(MediaControlAction, void*), void* context) {
  mediaControlHandler_ = handler;
  mediaControlContext_ = context;
}

void Dashboard::emitMediaControl(MediaControlAction action) {
  if (mediaControlHandler_) mediaControlHandler_(action, mediaControlContext_);
}

void Dashboard::show(Screen page) {
  page_ = page;
  pace_ = paceTarget_ = distance_ = elapsed_ = hr_ = hrTarget_ = media_ = nullptr;
  mediaSource_ = mediaTrack_ = mediaArtist_ = mediaPlayButton_ = musicFooter_ = nullptr;
  notification_ = nullptr;
  lv_obj_clean(content_);
  switch (page_) {
    case Screen::Ready: buildReady(); break;
    case Screen::Music: buildMusic(); break;
    case Screen::Stats: buildStats(); break;
    case Screen::Dashboard: buildDashboard(); break;
  }
  updateDashboard();
}

void Dashboard::buildDashboard() {
  lv_obj_t* time = label(content_, &lv_font_montserrat_20, LV_ALIGN_TOP_LEFT, 18, 14);
  lv_label_set_text(time, "10:42 AM");
  lv_obj_t* title = label(content_, &lv_font_montserrat_16, LV_ALIGN_TOP_MID, 0, 16, kLime);
  lv_label_set_text(title, "RUNDECK");
  lv_obj_t* weather = label(content_, &lv_font_montserrat_20, LV_ALIGN_TOP_RIGHT, -18, 14, kAmber);
  lv_label_set_text(weather, "78F");
  line(content_, 132, 50, 100, kLime); line(content_, 368, 50, 100, kLime);
  lv_obj_t* caption = label(content_, &lv_font_montserrat_28, LV_ALIGN_TOP_MID, 0, 40);
  lv_label_set_text(caption, "PACE");
  pace_ = label(content_, &lv_font_montserrat_48, LV_ALIGN_TOP_MID, 0, 75);
  lv_obj_set_style_text_letter_space(pace_, -1, 0);
  lv_obj_t* unit = label(content_, &lv_font_montserrat_28, LV_ALIGN_TOP_MID, 0, 137, kMuted);
  lv_label_set_text(unit, "/MI");
  paceTarget_ = label(content_, &lv_font_montserrat_20, LV_ALIGN_TOP_MID, 0, 171, kLime);
  line(content_, 0, 211, 600); line(content_, 200, 228, 1); line(content_, 399, 228, 1);
  distance_ = label(content_, &lv_font_montserrat_48, LV_ALIGN_TOP_MID, -200, 236);
  elapsed_ = label(content_, &lv_font_montserrat_48, LV_ALIGN_TOP_MID, 200, 236);
  lv_obj_t* distanceCaption = label(content_, &lv_font_montserrat_14, LV_ALIGN_TOP_LEFT, 42, 327, kMuted);
  lv_label_set_text(distanceCaption, "DISTANCE"); line(content_, 62, 356, 70, kCyan);
  lv_obj_t* elapsedCaption = label(content_, &lv_font_montserrat_14, LV_ALIGN_TOP_RIGHT, -42, 327, kMuted);
  lv_label_set_text(elapsedCaption, "ELAPSED"); line(content_, 468, 356, 70, kCyan);
  lv_obj_t* gauge = lv_arc_create(content_);
  lv_obj_remove_style_all(gauge); lv_obj_set_size(gauge, 130, 130); lv_obj_align(gauge, LV_ALIGN_TOP_MID, 0, 218);
  lv_arc_set_rotation(gauge, 135); lv_arc_set_bg_angles(gauge, 0, 270); lv_arc_set_value(gauge, 55);
  lv_obj_set_style_arc_width(gauge, 6, LV_PART_MAIN); lv_obj_set_style_arc_color(gauge, kLine, LV_PART_MAIN);
  lv_obj_set_style_arc_width(gauge, 7, LV_PART_INDICATOR); lv_obj_set_style_arc_color(gauge, kLime, LV_PART_INDICATOR);
  hr_ = label(content_, &lv_font_montserrat_36, LV_ALIGN_TOP_MID, 0, 259);
  lv_obj_t* hrCaption = label(content_, &lv_font_montserrat_14, LV_ALIGN_TOP_MID, 0, 335, kMuted);
  lv_label_set_text(hrCaption, "HEART RATE");
  hrTarget_ = label(content_, &lv_font_montserrat_14, LV_ALIGN_TOP_MID, 0, 366, kLime);
  line(content_, 0, 380, 600);
  lv_obj_t* playing = label(content_, &lv_font_montserrat_14, LV_ALIGN_BOTTOM_LEFT, 26, -55, kCyan);
  lv_label_set_text(playing, "NOW PLAYING");
  media_ = label(content_, &lv_font_montserrat_16, LV_ALIGN_BOTTOM_LEFT, 26, -19);
  lv_obj_t* bluetooth = label(content_, &lv_font_montserrat_16, LV_ALIGN_BOTTOM_RIGHT, -116, -26, kCyan);
  lv_label_set_text(bluetooth, "BT");
  lv_obj_t* battery = label(content_, &lv_font_montserrat_16, LV_ALIGN_BOTTOM_RIGHT, -18, -26);
  lv_label_set_text(battery, "BAT 82%");
  buildNotification();
}

void Dashboard::buildMusic() {
  lv_obj_t* heading = label(content_, &lv_font_montserrat_36, LV_ALIGN_TOP_LEFT, 24, 18);
  lv_label_set_text(heading, "MUSIC");
  line(content_, 0, 65, 600, kCyan); line(content_, 24, 63, 92, kCyan);
  mediaSource_ = label(content_, &lv_font_montserrat_20, LV_ALIGN_TOP_LEFT, 40, 110, kCyan);
  mediaTrack_ = label(content_, &lv_font_montserrat_48, LV_ALIGN_TOP_LEFT, 36, 144);
  lv_obj_set_width(mediaTrack_, 520);
  lv_label_set_long_mode(mediaTrack_, LV_LABEL_LONG_SCROLL_CIRCULAR);
  lv_obj_set_style_anim_speed(mediaTrack_, 35, 0);
  mediaArtist_ = label(content_, &lv_font_montserrat_20, LV_ALIGN_TOP_LEFT, 40, 206, kMuted);
  lv_obj_set_width(mediaArtist_, 520);
  lv_label_set_long_mode(mediaArtist_, LV_LABEL_LONG_DOT);
  const char* controls[] = {"PREV", "PAUSE", "NEXT"};
  for (int i = 0; i < 3; ++i) {
    lv_obj_t* control = panel(content_, 24 + i * 194, 258, 170, 78, kCyan);
    lv_obj_add_flag(control, LV_OBJ_FLAG_CLICKABLE);
    lv_obj_add_event_cb(control, i == 0 ? onMediaPrevious : (i == 1 ? onMediaPlayPause : onMediaNext),
                        LV_EVENT_CLICKED, this);
    lv_obj_t* controlText = label(control, &lv_font_montserrat_20, LV_ALIGN_CENTER, 0, 0);
    lv_label_set_text(controlText, controls[i]);
    if (i == 1) mediaPlayButton_ = controlText;
  }
  line(content_, 0, 362, 600, kCyan);
  musicFooter_ = label(content_, &lv_font_montserrat_20, LV_ALIGN_BOTTOM_MID, 0, -28);
}

void Dashboard::buildStats() {
  lv_obj_t* heading = label(content_, &lv_font_montserrat_28, LV_ALIGN_TOP_LEFT, 24, 16);
  lv_label_set_text(heading, "RUN STATS");
  lv_obj_t* clock = label(content_, &lv_font_montserrat_20, LV_ALIGN_TOP_MID, 0, 18);
  lv_label_set_text(clock, "10:42 AM");
  const char* names[] = {"PACE", "AVG PACE", "SPEED", "HEART RATE", "HR ZONE", "DISTANCE", "ELAPSED", "TEMP"};
  const char* values[] = {"8:25", "8:32", "7.0", "143", "135-150", "0.00", "00:47", "78F"};
  const char* units[] = {"/MI", "/MI", "MPH", "BPM", "IN ZONE", "MI", "", ""};
  for (int i = 0; i < 8; ++i) {
    const int col = i % 4, row = i / 4;
    lv_obj_t* cell = panel(content_, 18 + col * 146, 64 + row * 150, 134, 136, kLine);
    lv_obj_t* name = label(cell, &lv_font_montserrat_14, LV_ALIGN_TOP_LEFT, 10, 12, kMuted);
    lv_label_set_text(name, names[i]);
    lv_obj_t* value = label(cell, &lv_font_montserrat_28, LV_ALIGN_CENTER, 0, 6);
    lv_label_set_text(value, values[i]);
    lv_obj_t* unit = label(cell, &lv_font_montserrat_14, LV_ALIGN_BOTTOM_MID, 0, -12, i == 4 ? kGreen : kMuted);
    lv_label_set_text(unit, units[i]);
  }
  lv_obj_t* status = panel(content_, 18, 372, 564, 60, kGreen);
  lv_obj_t* labelLeft = label(status, &lv_font_montserrat_20, LV_ALIGN_LEFT_MID, 20, 0, kGreen);
  lv_label_set_text(labelLeft, "ON TARGET");
  lv_obj_t* labelRight = label(status, &lv_font_montserrat_14, LV_ALIGN_RIGHT_MID, -20, 0);
  lv_label_set_text(labelRight, "PACE AND HR IN RANGE");
}

void Dashboard::buildReady() {
  lv_obj_t* brand = label(content_, &lv_font_montserrat_28, LV_ALIGN_TOP_MID, 0, 16);
  lv_label_set_text(brand, "RUNDECK");
  lv_obj_t* subtitle = label(content_, &lv_font_montserrat_16, LV_ALIGN_TOP_MID, 0, 52, kMuted);
  lv_label_set_text(subtitle, "READY TO RUN");
  const char* presets[] = {"EASY", "STEADY", "LONG RUN", "CUSTOM"};
  const char* details[] = {"HR 135-145", "PACE 8:45-9:00", "10.0 MI / HR < 150 / PACE 8:50-9:20", "SET YOUR TARGETS"};
  for (int i = 0; i < 4; ++i) {
    lv_obj_t* row = panel(content_, 42, 84 + i * 62, 516, 54, i == 2 ? kLime : kLine);
    lv_obj_t* name = label(row, &lv_font_montserrat_20, LV_ALIGN_LEFT_MID, 20, -8);
    lv_label_set_text(name, presets[i]);
    lv_obj_t* detail = label(row, &lv_font_montserrat_14, LV_ALIGN_LEFT_MID, 20, 14, i == 2 ? kLime : kMuted);
    lv_label_set_text(detail, details[i]);
  }
  lv_obj_t* start = panel(content_, 42, 350, 516, 64, kLime);
  lv_obj_set_style_bg_color(start, kLime, 0); lv_obj_set_style_bg_opa(start, LV_OPA_COVER, 0);
  lv_obj_add_flag(start, LV_OBJ_FLAG_CLICKABLE);
  lv_obj_add_event_cb(start, onStart, LV_EVENT_CLICKED, this);
  lv_obj_t* startText = label(start, &lv_font_montserrat_36, LV_ALIGN_CENTER, 0, 0, lv_color_black());
  lv_label_set_text(startText, "START RUN");
}

void Dashboard::buildNotification() {
  notification_ = panel(content_, 64, 90, 472, 258, kCyan);
  lv_obj_add_flag(notification_, LV_OBJ_FLAG_GESTURE_BUBBLE);
  lv_obj_t* app = label(notification_, &lv_font_montserrat_20, LV_ALIGN_TOP_LEFT, 28, 24, kCyan);
  lv_label_set_text(app, "TEXT");
  lv_obj_t* sender = label(notification_, &lv_font_montserrat_36, LV_ALIGN_TOP_LEFT, 28, 56);
  lv_label_set_text(sender, "ELLEN");
  line(notification_, 28, 112, 416);
  lv_obj_t* body = label(notification_, &lv_font_montserrat_20, LV_ALIGN_TOP_LEFT, 28, 130);
  lv_label_set_text(body, "I'M LEAVING WORK NOW.\nNEED ANYTHING?");
  line(notification_, 28, 206, 416);
  lv_obj_t* dismiss = label(notification_, &lv_font_montserrat_20, LV_ALIGN_BOTTOM_MID, 0, -18, kCyan);
  lv_label_set_text(dismiss, "SWIPE DOWN TO DISMISS");
  lv_obj_add_flag(notification_, LV_OBJ_FLAG_HIDDEN);
}

void Dashboard::updateDashboard() {
  const char* source = state_.mediaSource ? state_.mediaSource : "PHONE";
  const char* title = state_.mediaTitle ? state_.mediaTitle : "NO MEDIA";
  const char* artist = state_.mediaArtist ? state_.mediaArtist : "";
  char value[96];
  if (page_ == Screen::Music && mediaTrack_) {
    if (strcmp(lv_label_get_text(mediaSource_), source) != 0) lv_label_set_text(mediaSource_, source);
    if (strcmp(lv_label_get_text(mediaTrack_), title) != 0) lv_label_set_text(mediaTrack_, title);
    if (strcmp(lv_label_get_text(mediaArtist_), artist) != 0) lv_label_set_text(mediaArtist_, artist);
    lv_label_set_text(mediaPlayButton_, state_.mediaPlaying ? "PAUSE" : "PLAY");
    char hrValue[12];
    if (state_.heartRateBpm) snprintf(hrValue, sizeof(hrValue), "%u BPM", state_.heartRateBpm);
    else snprintf(hrValue, sizeof(hrValue), "-- BPM");
    if (state_.paceMinutesPerMile <= 0.0f) {
      snprintf(value, sizeof(value), "--:-- /MI   %.2f MI   %02lu:%02lu   %s",
               state_.distanceMiles, state_.elapsedSeconds / 60, state_.elapsedSeconds % 60,
               hrValue);
    } else {
      const int totalSeconds = static_cast<int>(lroundf(state_.paceMinutesPerMile * 60.0f));
      snprintf(value, sizeof(value), "%d:%02d /MI   %.2f MI   %02lu:%02lu   %s",
               totalSeconds / 60, totalSeconds % 60, state_.distanceMiles,
               state_.elapsedSeconds / 60, state_.elapsedSeconds % 60,
               hrValue);
    }
    lv_label_set_text(musicFooter_, value);
    return;
  }
  if (page_ != Screen::Dashboard || !pace_) return;
  const char* status = state_.statusText ? state_.statusText : "READY";
  const char* target = state_.targetLabel ? state_.targetLabel : "8:50-9:20";
  if (state_.paceMinutesPerMile <= 0.0f) {
    lv_label_set_text(pace_, "--:--");
  } else {
    // Android rounds total seconds before formatting; do the same so the
    // display and phone agree rather than differing because of truncation.
    const int totalSeconds = static_cast<int>(lroundf(state_.paceMinutesPerMile * 60.0f));
    const int minutes = totalSeconds / 60;
    const int seconds = totalSeconds % 60;
    snprintf(value, sizeof(value), "%d:%02d", minutes, seconds); lv_label_set_text(pace_, value);
  }
  snprintf(value, sizeof(value), "TARGET %s / %s", target, status); lv_label_set_text(paceTarget_, value);
  lv_obj_set_style_text_color(paceTarget_, status[0] == 'O' ? kLime : (status[0] == 'G' ? kMuted : kRed), 0);
  snprintf(value, sizeof(value), "%.2f", state_.distanceMiles); lv_label_set_text(distance_, value);
  snprintf(value, sizeof(value), "%02lu:%02lu", state_.elapsedSeconds / 60, state_.elapsedSeconds % 60); lv_label_set_text(elapsed_, value);
  const bool heartRateLive = state_.heartRate.state == SourceState::Connected && state_.heartRateBpm > 0;
  if (heartRateLive) {
    snprintf(value, sizeof(value), "%u", state_.heartRateBpm); lv_label_set_text(hr_, value);
    lv_label_set_text(hrTarget_, "TARGET 135-150 / IN ZONE");
  } else {
    lv_label_set_text(hr_, "--");
    lv_label_set_text(hrTarget_, "GARMIN STRAP OFF");
  }
  lv_obj_set_style_text_color(hr_, heartRateLive ? kWhite : kMuted, 0);
  lv_obj_set_style_text_color(hrTarget_, heartRateLive ? kLime : kMuted, 0);
  snprintf(value, sizeof(value), "%s\n%s", title, artist); lv_label_set_text(media_, value);
  if (notification_ && state_.notificationVisible && !notificationDismissed_) lv_obj_clear_flag(notification_, LV_OBJ_FLAG_HIDDEN);
  if (notification_ && !state_.notificationVisible) {
    notificationDismissed_ = false;
    lv_obj_add_flag(notification_, LV_OBJ_FLAG_HIDDEN);
  }
}

void Dashboard::render(const DisplayState& state) {
  state_ = state;
  updateDashboard();
}

}  // namespace rundeck
