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

lv_obj_t* makeLabel(lv_obj_t* parent, const lv_font_t* font, lv_align_t align, int x, int y,
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

void metricCaption(lv_obj_t* root, const char* text, int x) {
  lv_obj_t* value = makeLabel(root, &lv_font_montserrat_14, LV_ALIGN_TOP_LEFT, x, 327, kMuted);
  lv_label_set_text(value, text);
  rule(root, x + 20, 356, 70, kCyan);
}
}  // namespace

void Dashboard::begin() {
  lv_obj_t* root = lv_scr_act();
  // Remove LVGL's default theme first: it is the source of the unwanted pink
  // surfaces on boards whose vendor demo config enables a colored theme.
  lv_obj_remove_style_all(root);
  lv_obj_set_style_bg_color(root, lv_color_black(), 0);
  lv_obj_set_style_bg_opa(root, LV_OPA_COVER, 0);
  lv_obj_clear_flag(root, LV_OBJ_FLAG_SCROLLABLE);

  topLine_ = makeLabel(root, &lv_font_montserrat_20, LV_ALIGN_TOP_LEFT, 18, 14);
  lv_label_set_text(topLine_, "10:42 AM");
  lv_obj_t* title = makeLabel(root, &lv_font_montserrat_16, LV_ALIGN_TOP_MID, 0, 16, kLime);
  lv_label_set_text(title, "RUNDECK");
  lv_obj_t* weather = makeLabel(root, &lv_font_montserrat_20, LV_ALIGN_TOP_RIGHT, -18, 14, kAmber);
  lv_label_set_text(weather, "☼  78°F");

  rule(root, 132, 50, 100, kLime);
  rule(root, 368, 50, 100, kLime);
  lv_obj_t* paceCaption = makeLabel(root, &lv_font_montserrat_28, LV_ALIGN_TOP_MID, 0, 40);
  lv_label_set_text(paceCaption, "PACE");
  pace_ = makeLabel(root, &lv_font_montserrat_48, LV_ALIGN_TOP_MID, 0, 75);
  lv_obj_set_style_text_letter_space(pace_, -1, 0);
  lv_obj_t* unit = makeLabel(root, &lv_font_montserrat_28, LV_ALIGN_TOP_MID, 0, 137, kMuted);
  lv_label_set_text(unit, "/MI");
  paceTarget_ = makeLabel(root, &lv_font_montserrat_20, LV_ALIGN_TOP_MID, 0, 171, kLime);

  rule(root, 0, 211, 600);
  rule(root, 200, 228, 1);
  rule(root, 399, 228, 1);
  distance_ = makeLabel(root, &lv_font_montserrat_48, LV_ALIGN_TOP_MID, -200, 236);
  elapsed_ = makeLabel(root, &lv_font_montserrat_48, LV_ALIGN_TOP_MID, 200, 236);
  metricCaption(root, "DISTANCE", 42);
  metricCaption(root, "ELAPSED", 432);

  // Heart-rate gauge: a restrained lime arc avoids animation while retaining
  // the glanceable center focus of the approved concept.
  lv_obj_t* gauge = lv_arc_create(root);
  lv_obj_remove_style_all(gauge);
  lv_obj_set_size(gauge, 130, 130);
  lv_obj_align(gauge, LV_ALIGN_TOP_MID, 0, 218);
  lv_arc_set_rotation(gauge, 135);
  lv_arc_set_bg_angles(gauge, 0, 270);
  lv_arc_set_value(gauge, 55);
  lv_obj_remove_style(gauge, nullptr, LV_PART_KNOB);
  lv_obj_set_style_arc_width(gauge, 6, LV_PART_MAIN);
  lv_obj_set_style_arc_color(gauge, kLine, LV_PART_MAIN);
  lv_obj_set_style_arc_width(gauge, 7, LV_PART_INDICATOR);
  lv_obj_set_style_arc_color(gauge, kLime, LV_PART_INDICATOR);
  hr_ = makeLabel(root, &lv_font_montserrat_36, LV_ALIGN_TOP_MID, 0, 259);
  hrTarget_ = makeLabel(root, &lv_font_montserrat_14, LV_ALIGN_TOP_MID, 0, 366, kLime);
  lv_obj_t* hrCaption = makeLabel(root, &lv_font_montserrat_14, LV_ALIGN_TOP_MID, 0, 335, kMuted);
  lv_label_set_text(hrCaption, "HEART RATE");

  rule(root, 0, 380, 600);
  media_ = makeLabel(root, &lv_font_montserrat_16, LV_ALIGN_BOTTOM_LEFT, 26, -19);
  lv_obj_t* mediaIcon = makeLabel(root, &lv_font_montserrat_28, LV_ALIGN_BOTTOM_LEFT, 18, -46, kCyan);
  lv_label_set_text(mediaIcon, "♫");
  lv_obj_t* bluetooth = makeLabel(root, &lv_font_montserrat_28, LV_ALIGN_BOTTOM_RIGHT, -110, -25, kCyan);
  lv_label_set_text(bluetooth, "ᛒ");
  lv_obj_t* battery = makeLabel(root, &lv_font_montserrat_20, LV_ALIGN_BOTTOM_RIGHT, -18, -25, kWhite);
  lv_label_set_text(battery, "▰ 82%");

  notification_ = lv_obj_create(root);
  lv_obj_remove_style_all(notification_);
  lv_obj_set_size(notification_, 500, 230);
  lv_obj_center(notification_);
  lv_obj_set_style_bg_color(notification_, lv_color_hex(0x080A0D), 0);
  lv_obj_set_style_bg_opa(notification_, LV_OPA_COVER, 0);
  lv_obj_set_style_border_color(notification_, kCyan, 0);
  lv_obj_set_style_border_width(notification_, 2, 0);
  lv_obj_set_style_radius(notification_, 20, 0);
  lv_obj_t* note = makeLabel(notification_, &lv_font_montserrat_20, LV_ALIGN_CENTER, 0, 0);
  lv_label_set_text(note, "TEXT  •  ALEX\n\nNice pace — keep it smooth\n\nDISMISS                 REPLY LATER");
  lv_obj_add_flag(notification_, LV_OBJ_FLAG_HIDDEN);
}

void Dashboard::render(const DisplayState& state) {
  char value[96];
  const int minutes = static_cast<int>(state.paceMinutesPerMile);
  const int seconds = static_cast<int>((state.paceMinutesPerMile - minutes) * 60.0f);
  snprintf(value, sizeof(value), "%d:%02d", minutes, seconds);
  lv_label_set_text(pace_, value);
  snprintf(value, sizeof(value), "TARGET 8:30  •  %s", state.statusText);
  lv_label_set_text(paceTarget_, value);
  lv_obj_set_style_text_color(paceTarget_, state.statusText[0] == 'O' ? kLime : kAmber, 0);

  snprintf(value, sizeof(value), "%.2f", state.distanceMiles);
  lv_label_set_text(distance_, value);
  snprintf(value, sizeof(value), "%02lu:%02lu", state.elapsedSeconds / 60, state.elapsedSeconds % 60);
  lv_label_set_text(elapsed_, value);
  snprintf(value, sizeof(value), "%u", state.heartRateBpm);
  lv_label_set_text(hr_, value);
  lv_label_set_text(hrTarget_, "TARGET 135–150  •  IN ZONE");
  snprintf(value, sizeof(value), "%s\n%s", state.mediaTitle, state.mediaArtist);
  lv_label_set_text(media_, value);

  if (state.notificationVisible) lv_obj_clear_flag(notification_, LV_OBJ_FLAG_HIDDEN);
  else lv_obj_add_flag(notification_, LV_OBJ_FLAG_HIDDEN);
}

}  // namespace rundeck
