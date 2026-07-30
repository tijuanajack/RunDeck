#include "dashboard.h"

#include <stdio.h>

namespace rundeck {
namespace {
const lv_color_t kWhite = lv_color_white();
const lv_color_t kGreen = lv_color_hex(0x37D67A);
const lv_color_t kAmber = lv_color_hex(0xFFC857);

lv_obj_t* label(lv_obj_t* parent, const lv_font_t* font, lv_align_t align, int x, int y) {
  lv_obj_t* value = lv_label_create(parent);
  lv_obj_set_style_text_font(value, font, 0);
  lv_obj_set_style_text_color(value, kWhite, 0);
  lv_obj_align(value, align, x, y);
  return value;
}
}  // namespace

void Dashboard::begin() {
  lv_obj_t* root = lv_scr_act();
  lv_obj_set_style_bg_color(root, lv_color_black(), 0);
  lv_obj_set_style_bg_opa(root, LV_OPA_COVER, 0);

  lv_obj_t* title = label(root, &lv_font_montserrat_16, LV_ALIGN_TOP_LEFT, 24, 20);
  lv_label_set_text(title, "RUNDECK   •   LIVE RUN");
  pace_ = label(root, &lv_font_montserrat_48, LV_ALIGN_TOP_LEFT, 24, 64);
  status_ = label(root, &lv_font_montserrat_20, LV_ALIGN_TOP_LEFT, 27, 128);
  metrics_ = label(root, &lv_font_montserrat_28, LV_ALIGN_TOP_LEFT, 24, 183);
  media_ = label(root, &lv_font_montserrat_16, LV_ALIGN_BOTTOM_LEFT, 24, -28);
  notification_ = label(root, &lv_font_montserrat_20, LV_ALIGN_CENTER, 0, 0);
  lv_obj_set_size(notification_, 480, 130);
  lv_obj_set_style_bg_color(notification_, lv_color_hex(0x151A22), 0);
  lv_obj_set_style_bg_opa(notification_, LV_OPA_COVER, 0);
  lv_obj_set_style_pad_all(notification_, 18, 0);
  lv_obj_set_style_radius(notification_, 16, 0);
  lv_obj_add_flag(notification_, LV_OBJ_FLAG_HIDDEN);
}

void Dashboard::render(const DisplayState& state) {
  char value[96];
  const int paceMinutes = static_cast<int>(state.paceMinutesPerMile);
  const int paceSeconds = static_cast<int>((state.paceMinutesPerMile - paceMinutes) * 60.0f);
  snprintf(value, sizeof(value), "%d:%02d /MI", paceMinutes, paceSeconds);
  lv_label_set_text(pace_, value);
  lv_obj_set_style_text_color(status_, state.statusText[0] == 'O' ? kGreen : kAmber, 0);
  lv_label_set_text(status_, state.statusText);
  snprintf(value, sizeof(value), "%.2f MI     %02lu:%02lu\n%u BPM      %d°F",
           state.distanceMiles, state.elapsedSeconds / 60, state.elapsedSeconds % 60,
           state.heartRateBpm, state.temperatureF);
  lv_label_set_text(metrics_, value);
  snprintf(value, sizeof(value), "%s  —  %s%s", state.mediaTitle, state.mediaArtist,
           state.mediaPlaying ? "   ▮▮" : "   ▶");
  lv_label_set_text(media_, value);
  if (state.notificationVisible) {
    lv_label_set_text(notification_, "MESSAGE\nAlex: Nice pace — keep it smooth\nSwipe down to dismiss");
    lv_obj_clear_flag(notification_, LV_OBJ_FLAG_HIDDEN);
  } else {
    lv_obj_add_flag(notification_, LV_OBJ_FLAG_HIDDEN);
  }
}

}  // namespace rundeck
