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

lv_obj_t* card(lv_obj_t* parent, int x, int y, int width, int height) {
  lv_obj_t* value = lv_obj_create(parent);
  lv_obj_clear_flag(value, LV_OBJ_FLAG_SCROLLABLE);
  lv_obj_set_pos(value, x, y);
  lv_obj_set_size(value, width, height);
  lv_obj_set_style_bg_color(value, lv_color_hex(0x10151E), 0);
  lv_obj_set_style_bg_opa(value, LV_OPA_COVER, 0);
  lv_obj_set_style_border_color(value, lv_color_hex(0x253142), 0);
  lv_obj_set_style_border_width(value, 1, 0);
  lv_obj_set_style_radius(value, 16, 0);
  lv_obj_set_style_pad_all(value, 0, 0);
  return value;
}
}  // namespace

void Dashboard::begin() {
  lv_obj_t* root = lv_scr_act();
  lv_obj_set_style_bg_color(root, lv_color_black(), 0);
  lv_obj_set_style_bg_opa(root, LV_OPA_COVER, 0);

  topLine_ = label(root, &lv_font_montserrat_14, LV_ALIGN_TOP_LEFT, 24, 16);
  lv_label_set_text(topLine_, "10:24 AM     72°F     PHONE • GPS • HR");
  lv_obj_t* paceCard = card(root, 20, 48, 360, 186);
  lv_obj_t* paceLabel = label(paceCard, &lv_font_montserrat_14, LV_ALIGN_TOP_LEFT, 20, 16);
  lv_label_set_text(paceLabel, "CURRENT PACE");
  pace_ = label(paceCard, &lv_font_montserrat_48, LV_ALIGN_TOP_LEFT, 18, 42);
  paceTarget_ = label(paceCard, &lv_font_montserrat_16, LV_ALIGN_BOTTOM_LEFT, 20, -18);
  lv_obj_t* hrCard = card(root, 396, 48, 184, 186);
  lv_obj_t* hrLabel = label(hrCard, &lv_font_montserrat_14, LV_ALIGN_TOP_LEFT, 18, 16);
  lv_label_set_text(hrLabel, "HEART RATE");
  hr_ = label(hrCard, &lv_font_montserrat_36, LV_ALIGN_TOP_LEFT, 18, 54);
  hrTarget_ = label(hrCard, &lv_font_montserrat_14, LV_ALIGN_BOTTOM_LEFT, 18, -20);
  status_ = label(root, &lv_font_montserrat_20, LV_ALIGN_TOP_LEFT, 26, 250);
  lv_obj_t* distanceCard = card(root, 20, 286, 174, 88);
  lv_obj_t* distanceLabel = label(distanceCard, &lv_font_montserrat_14, LV_ALIGN_TOP_LEFT, 16, 12);
  lv_label_set_text(distanceLabel, "DISTANCE");
  distance_ = label(distanceCard, &lv_font_montserrat_28, LV_ALIGN_BOTTOM_LEFT, 16, -12);
  lv_obj_t* elapsedCard = card(root, 206, 286, 174, 88);
  lv_obj_t* elapsedLabel = label(elapsedCard, &lv_font_montserrat_14, LV_ALIGN_TOP_LEFT, 16, 12);
  lv_label_set_text(elapsedLabel, "ELAPSED");
  elapsed_ = label(elapsedCard, &lv_font_montserrat_28, LV_ALIGN_BOTTOM_LEFT, 16, -12);
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
  snprintf(value, sizeof(value), "%d:%02d", paceMinutes, paceSeconds);
  lv_label_set_text(pace_, value);
  lv_label_set_text(paceTarget_, "TARGET  8:20–8:45 /MI");
  lv_obj_set_style_text_color(status_, state.statusText[0] == 'O' ? kGreen : kAmber, 0);
  lv_label_set_text(status_, state.statusText);
  snprintf(value, sizeof(value), "%.2f MI", state.distanceMiles);
  lv_label_set_text(distance_, value);
  snprintf(value, sizeof(value), "%02lu:%02lu", state.elapsedSeconds / 60, state.elapsedSeconds % 60);
  lv_label_set_text(elapsed_, value);
  snprintf(value, sizeof(value), "%u", state.heartRateBpm);
  lv_label_set_text(hr_, value);
  lv_label_set_text(hrTarget_, "TARGET 135–150\nIN ZONE");
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
