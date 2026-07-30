#include "dashboard.h"

#include <stdio.h>

namespace rundeck {
namespace {
const lv_color_t kWhite = lv_color_hex(0xF6F7F8);
const lv_color_t kMuted = lv_color_hex(0xA5A8AD);
const lv_color_t kLine = lv_color_hex(0x282C31);
const lv_color_t kLime = lv_color_hex(0x9AF000);
const lv_color_t kCyan = lv_color_hex(0x38D7E4);
const lv_color_t kGreen = lv_color_hex(0x58D66D);
const lv_color_t kAmber = lv_color_hex(0xF8C433);

lv_obj_t* text(lv_obj_t* parent, const lv_font_t* font, lv_align_t align, int x, int y,
               lv_color_t color = kWhite) {
  lv_obj_t* value = lv_label_create(parent);
  lv_obj_remove_style_all(value);
  lv_obj_set_style_text_font(value, font, 0);
  lv_obj_set_style_text_color(value, color, 0);
  lv_obj_set_style_text_letter_space(value, 1, 0);
  lv_obj_align(value, align, x, y);
  return value;
}

lv_obj_t* rule(lv_obj_t* parent, int x, int y, int width, lv_color_t color = kLine) {
  lv_obj_t* value = lv_obj_create(parent);
  lv_obj_remove_style_all(value);
  lv_obj_set_pos(value, x, y);
  lv_obj_set_size(value, width, 1);
  lv_obj_set_style_bg_color(value, color, 0);
  lv_obj_set_style_bg_opa(value, LV_OPA_COVER, 0);
  return value;
}

lv_obj_t* outlined(lv_obj_t* parent, int x, int y, int width, int height, lv_color_t accent) {
  lv_obj_t* value = lv_obj_create(parent);
  lv_obj_remove_style_all(value);
  lv_obj_set_pos(value, x, y);
  lv_obj_set_size(value, width, height);
  lv_obj_set_style_bg_color(value, lv_color_hex(0x050607), 0);
  lv_obj_set_style_bg_opa(value, LV_OPA_COVER, 0);
  lv_obj_set_style_border_color(value, accent, 0);
  lv_obj_set_style_border_width(value, 2, 0);
  lv_obj_set_style_radius(value, 16, 0);
  lv_obj_clear_flag(value, LV_OBJ_FLAG_SCROLLABLE);
  return value;
}

void metricCaption(lv_obj_t* root, const char* label, int x) {
  lv_obj_t* value = text(root, &lv_font_montserrat_14, LV_ALIGN_TOP_LEFT, x, 327, kMuted);
  lv_label_set_text(value, label);
  rule(root, x + 20, 356, 70, kCyan);
}
}  // namespace

void Dashboard::begin() {
  root_ = lv_scr_act();
  lv_obj_remove_style_all(root_);
  lv_obj_set_style_bg_color(root_, lv_color_black(), 0);
  lv_obj_set_style_bg_opa(root_, LV_OPA_COVER, 0);
  lv_obj_clear_flag(root_, LV_OBJ_FLAG_SCROLLABLE);
  lv_obj_add_event_cb(root_, onGesture, LV_EVENT_GESTURE, this);
  setPage(Screen::Dashboard);
}

void Dashboard::onGesture(lv_event_t* event) {
  auto* self = static_cast<Dashboard*>(lv_event_get_user_data(event));
  lv_indev_t* input = lv_indev_get_act();
  if (!input) return;
  switch (lv_indev_get_gesture_dir(input)) {
    case LV_DIR_LEFT:
      self->setPage(self->page_ == Screen::Stats ? Screen::Ready : static_cast<Screen>(static_cast<int>(self->page_) + 1));
      break;
    case LV_DIR_RIGHT:
      self->setPage(self->page_ == Screen::Ready ? Screen::Stats : static_cast<Screen>(static_cast<int>(self->page_) - 1));
      break;
    default: break;
  }
}

void Dashboard::onStart(lv_event_t* event) {
  static_cast<Dashboard*>(lv_event_get_user_data(event))->setPage(Screen::Dashboard);
}

void Dashboard::setPage(Screen page) {
  page_ = page;
  pace_ = paceTarget_ = status_ = distance_ = elapsed_ = hr_ = hrTarget_ = topLine_ = media_ = notification_ = nullptr;
  lv_obj_clean(root_);
  switch (page_) {
    case Screen::Ready: buildReady(); break;
    case Screen::Music: buildMusic(); break;
    case Screen::Stats: buildStats(); break;
    default: buildDashboard(); break;
  }
  updatePage();
}

void Dashboard::buildDashboard() {
  topLine_ = text(root_, &lv_font_montserrat_20, LV_ALIGN_TOP_LEFT, 18, 14);
  lv_obj_t* title = text(root_, &lv_font_montserrat_16, LV_ALIGN_TOP_MID, 0, 16, kLime);
  lv_label_set_text(title, "RUNDECK");
  lv_obj_t* weather = text(root_, &lv_font_montserrat_20, LV_ALIGN_TOP_RIGHT, -18, 14, kAmber);
  lv_label_set_text(weather, "78°F");
  rule(root_, 132, 50, 100, kLime); rule(root_, 368, 50, 100, kLime);
  lv_obj_t* caption = text(root_, &lv_font_montserrat_28, LV_ALIGN_TOP_MID, 0, 40); lv_label_set_text(caption, "PACE");
  pace_ = text(root_, &lv_font_montserrat_48, LV_ALIGN_TOP_MID, 0, 75);
  lv_obj_t* unit = text(root_, &lv_font_montserrat_28, LV_ALIGN_TOP_MID, 0, 137, kMuted); lv_label_set_text(unit, "/MI");
  paceTarget_ = text(root_, &lv_font_montserrat_20, LV_ALIGN_TOP_MID, 0, 171, kLime);
  rule(root_, 0, 211, 600); rule(root_, 200, 228, 1); rule(root_, 399, 228, 1);
  distance_ = text(root_, &lv_font_montserrat_48, LV_ALIGN_TOP_MID, -200, 236);
  elapsed_ = text(root_, &lv_font_montserrat_48, LV_ALIGN_TOP_MID, 200, 236);
  metricCaption(root_, "DISTANCE", 42); metricCaption(root_, "ELAPSED", 432);
  lv_obj_t* gauge = lv_arc_create(root_);
  lv_obj_remove_style_all(gauge); lv_obj_set_size(gauge, 130, 130); lv_obj_align(gauge, LV_ALIGN_TOP_MID, 0, 218);
  lv_arc_set_rotation(gauge, 135); lv_arc_set_bg_angles(gauge, 0, 270); lv_arc_set_value(gauge, 55);
  lv_obj_set_style_arc_width(gauge, 6, LV_PART_MAIN); lv_obj_set_style_arc_color(gauge, kLine, LV_PART_MAIN);
  lv_obj_set_style_arc_width(gauge, 7, LV_PART_INDICATOR); lv_obj_set_style_arc_color(gauge, kLime, LV_PART_INDICATOR);
  hr_ = text(root_, &lv_font_montserrat_36, LV_ALIGN_TOP_MID, 0, 259);
  lv_obj_t* hrCaption = text(root_, &lv_font_montserrat_14, LV_ALIGN_TOP_MID, 0, 335, kMuted); lv_label_set_text(hrCaption, "HEART RATE");
  hrTarget_ = text(root_, &lv_font_montserrat_14, LV_ALIGN_TOP_MID, 0, 366, kLime);
  rule(root_, 0, 380, 600);
  lv_obj_t* playing = text(root_, &lv_font_montserrat_14, LV_ALIGN_BOTTOM_LEFT, 26, -55, kCyan); lv_label_set_text(playing, "NOW PLAYING");
  media_ = text(root_, &lv_font_montserrat_16, LV_ALIGN_BOTTOM_LEFT, 26, -19);
  lv_obj_t* bluetooth = text(root_, &lv_font_montserrat_16, LV_ALIGN_BOTTOM_RIGHT, -116, -26, kCyan); lv_label_set_text(bluetooth, "BT");
  lv_obj_t* battery = text(root_, &lv_font_montserrat_16, LV_ALIGN_BOTTOM_RIGHT, -18, -26); lv_label_set_text(battery, "BAT 82%");
}

void Dashboard::buildMusic() {
  lv_obj_t* heading = text(root_, &lv_font_montserrat_36, LV_ALIGN_TOP_LEFT, 24, 18); lv_label_set_text(heading, "MUSIC");
  rule(root_, 0, 65, 600, kCyan); rule(root_, 24, 63, 92, kCyan);
  lv_obj_t* source = text(root_, &lv_font_montserrat_16, LV_ALIGN_TOP_LEFT, 36, 110, kCyan); lv_label_set_text(source, "GOOSE");
  media_ = text(root_, &lv_font_montserrat_48, LV_ALIGN_TOP_LEFT, 34, 140); lv_label_set_text(media_, "Hungersite");
  lv_obj_t* artist = text(root_, &lv_font_montserrat_20, LV_ALIGN_TOP_LEFT, 38, 202, kMuted); lv_label_set_text(artist, "Midnight City  •  M83");
  const char* controls[] = {"<<", "PAUSE", ">>"};
  for (int i = 0; i < 3; ++i) {
    lv_obj_t* button = outlined(root_, 24 + i * 194, 258, i == 1 ? 170 : 170, 78, kCyan);
    lv_obj_t* value = text(button, i == 1 ? &lv_font_montserrat_20 : &lv_font_montserrat_28, LV_ALIGN_CENTER, 0, 0);
    lv_label_set_text(value, controls[i]);
  }
  rule(root_, 0, 362, 600, kCyan);
  lv_obj_t* footer = text(root_, &lv_font_montserrat_20, LV_ALIGN_BOTTOM_MID, 0, -28);
  lv_label_set_text(footer, "8:25 /MI     0.00 MI     00:47     143 BPM");
}

void Dashboard::buildStats() {
  lv_obj_t* heading = text(root_, &lv_font_montserrat_28, LV_ALIGN_TOP_LEFT, 24, 16); lv_label_set_text(heading, "RUN STATS");
  lv_obj_t* clock = text(root_, &lv_font_montserrat_20, LV_ALIGN_TOP_MID, 0, 18); lv_label_set_text(clock, "10:42 AM");
  const char* labels[] = {"CURRENT PACE", "AVG PACE", "SPEED", "HEART RATE", "HR ZONE", "DISTANCE", "ELAPSED", "TEMP"};
  const char* values[] = {"8:25", "8:32", "7.0", "143", "135–150", "0.00", "00:47", "78°F"};
  const char* units[] = {"/MI", "/MI", "MPH", "BPM", "IN ZONE", "MI", "", ""};
  for (int i = 0; i < 8; ++i) {
    int col = i % 4, row = i / 4;
    lv_obj_t* cell = outlined(root_, 18 + col * 146, 64 + row * 150, 134, 136, kLine);
    lv_obj_t* label = text(cell, &lv_font_montserrat_14, LV_ALIGN_TOP_LEFT, 12, 14); lv_label_set_text(label, labels[i]);
    lv_obj_t* value = text(cell, &lv_font_montserrat_28, LV_ALIGN_CENTER, 0, 8); lv_label_set_text(value, values[i]);
    lv_obj_t* unit = text(cell, &lv_font_montserrat_14, LV_ALIGN_BOTTOM_MID, 0, -14, i == 4 ? kGreen : kMuted); lv_label_set_text(unit, units[i]);
  }
  lv_obj_t* status = outlined(root_, 18, 372, 564, 60, kGreen);
  lv_obj_t* label = text(status, &lv_font_montserrat_20, LV_ALIGN_LEFT_MID, 22, 0, kGreen); lv_label_set_text(label, "ON TARGET");
  lv_obj_t* detail = text(status, &lv_font_montserrat_14, LV_ALIGN_RIGHT_MID, -22, 0); lv_label_set_text(detail, "PACE AND HR IN RANGE");
}

void Dashboard::buildReady() {
  lv_obj_t* brand = text(root_, &lv_font_montserrat_28, LV_ALIGN_TOP_MID, 0, 16); lv_label_set_text(brand, "RUNDECK");
  lv_obj_t* sub = text(root_, &lv_font_montserrat_16, LV_ALIGN_TOP_MID, 0, 52, kMuted); lv_label_set_text(sub, "READY TO RUN");
  const char* presets[] = {"EASY", "STEADY", "LONG RUN", "CUSTOM"};
  const char* details[] = {"HR 135–145", "PACE 8:45–9:00", "10.0 MI   •   HR < 150   •   PACE 8:50–9:20", "SET YOUR TARGETS"};
  for (int i = 0; i < 4; ++i) {
    lv_obj_t* row = outlined(root_, 42, 84 + i * 62, 516, 54, i == 2 ? kLime : kLine);
    lv_obj_t* name = text(row, &lv_font_montserrat_20, LV_ALIGN_LEFT_MID, 20, -8); lv_label_set_text(name, presets[i]);
    lv_obj_t* detail = text(row, &lv_font_montserrat_14, LV_ALIGN_LEFT_MID, 20, 14, i == 2 ? kLime : kMuted); lv_label_set_text(detail, details[i]);
  }
  lv_obj_t* start = outlined(root_, 42, 350, 516, 64, kLime);
  lv_obj_set_style_bg_color(start, kLime, 0); lv_obj_set_style_bg_opa(start, LV_OPA_COVER, 0);
  lv_obj_add_flag(start, LV_OBJ_FLAG_CLICKABLE); lv_obj_add_event_cb(start, onStart, LV_EVENT_CLICKED, this);
  lv_obj_t* startText = text(start, &lv_font_montserrat_36, LV_ALIGN_CENTER, 0, 0, lv_color_black()); lv_label_set_text(startText, "START RUN");
}

void Dashboard::render(const DisplayState& state) {
  state_ = state;
  updatePage();
}

void Dashboard::updatePage() {
  if (page_ == Screen::Music || page_ == Screen::Stats || page_ == Screen::Ready) return;
  if (!pace_) return;
  char value[96];
  const int minutes = static_cast<int>(state_.paceMinutesPerMile);
  const int seconds = static_cast<int>((state_.paceMinutesPerMile - minutes) * 60.0f);
  snprintf(value, sizeof(value), "%d:%02d", minutes, seconds); lv_label_set_text(pace_, value);
  snprintf(value, sizeof(value), "TARGET 8:30  •  %s", state_.statusText); lv_label_set_text(paceTarget_, value);
  lv_obj_set_style_text_color(paceTarget_, state_.statusText[0] == 'O' ? kLime : kAmber, 0);
  snprintf(value, sizeof(value), "%.2f", state_.distanceMiles); lv_label_set_text(distance_, value);
  snprintf(value, sizeof(value), "%02lu:%02lu", state_.elapsedSeconds / 60, state_.elapsedSeconds % 60); lv_label_set_text(elapsed_, value);
  snprintf(value, sizeof(value), "%u", state_.heartRateBpm); lv_label_set_text(hr_, value);
  lv_label_set_text(hrTarget_, "TARGET 135–150  •  IN ZONE");
  snprintf(value, sizeof(value), "%s\n%s", state_.mediaTitle, state_.mediaArtist); lv_label_set_text(media_, value);
  lv_label_set_text(topLine_, "10:42 AM");
}

}  // namespace rundeck
