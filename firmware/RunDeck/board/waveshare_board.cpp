#include "waveshare_board.h"

#include <assert.h>

#include "driver/i2c.h"
#include "driver/spi_master.h"
#include "esp_heap_caps.h"
#include "esp_lcd_panel_io.h"
#include "esp_lcd_panel_ops.h"
#include "esp_timer.h"
#include "lvgl.h"

#include "../esp_lcd_sh8601.h"
#include "../esp_lcd_touch_ft5x06.h"

namespace rundeck {
namespace {
constexpr int kWidth = 600;
constexpr int kHeight = 450;
constexpr int kBufferHeight = kHeight / 10;
constexpr gpio_num_t kCs = GPIO_NUM_9;
constexpr gpio_num_t kClock = GPIO_NUM_10;
constexpr gpio_num_t kData0 = GPIO_NUM_11;
constexpr gpio_num_t kData1 = GPIO_NUM_12;
constexpr gpio_num_t kData2 = GPIO_NUM_13;
constexpr gpio_num_t kData3 = GPIO_NUM_14;
constexpr gpio_num_t kReset = GPIO_NUM_21;
constexpr gpio_num_t kTouchSda = GPIO_NUM_47;
constexpr gpio_num_t kTouchScl = GPIO_NUM_48;

esp_lcd_touch_handle_t touch = nullptr;
lv_disp_draw_buf_t drawBuffer;
lv_disp_drv_t displayDriver;

const sh8601_lcd_init_cmd_t kInit[] = {
    {0xFE, (uint8_t[]){0x20}, 1, 0}, {0x26, (uint8_t[]){0x0A}, 1, 0},
    {0x24, (uint8_t[]){0x80}, 1, 0}, {0xFE, (uint8_t[]){0x00}, 1, 0},
    {0x3A, (uint8_t[]){0x55}, 1, 0}, {0xC2, (uint8_t[]){0x00}, 1, 10},
    {0x35, (uint8_t[]){0x00}, 0, 0},  {0x51, (uint8_t[]){0x00}, 1, 10},
    {0x11, (uint8_t[]){0x00}, 0, 80}, {0x2A, (uint8_t[]){0x00, 0x10, 0x01, 0xD1}, 4, 0},
    {0x2B, (uint8_t[]){0x00, 0x00, 0x02, 0x57}, 4, 0}, {0x29, (uint8_t[]){0x00}, 0, 10},
    {0x36, (uint8_t[]){0x30}, 1, 0}, {0x51, (uint8_t[]){0xD0}, 1, 0},
};

bool flushReady(esp_lcd_panel_io_handle_t, esp_lcd_panel_io_event_data_t*, void* context) {
  lv_disp_flush_ready(static_cast<lv_disp_drv_t*>(context));
  return false;
}

void flush(lv_disp_drv_t* driver, const lv_area_t* area, lv_color_t* colors) {
  auto panel = static_cast<esp_lcd_panel_handle_t>(driver->user_data);
  esp_lcd_panel_draw_bitmap(panel, area->x1, area->y1 + 16, area->x2 + 1, area->y2 + 17, colors);
}

void rounder(lv_disp_drv_t*, lv_area_t* area) {
  area->x1 &= ~1; area->y1 &= ~1; area->x2 |= 1; area->y2 |= 1;
}

void readTouch(lv_indev_drv_t*, lv_indev_data_t* data) {
  if (!touch) {
    data->state = LV_INDEV_STATE_RELEASED;
    return;
  }
  uint16_t x = 0, y = 0;
  uint8_t count = 0;
  esp_lcd_touch_read_data(touch);
  if (esp_lcd_touch_get_coordinates(touch, &x, &y, nullptr, &count, 1) && count) {
    data->point.x = x;
    data->point.y = y;
    data->state = LV_INDEV_STATE_PRESSED;
  } else {
    data->state = LV_INDEV_STATE_RELEASED;
  }
}

void tick(void*) { lv_tick_inc(2); }
}  // namespace

bool beginWaveshareBoard() {
  const spi_bus_config_t bus = SH8601_PANEL_BUS_QSPI_CONFIG(kClock, kData0, kData1, kData2, kData3,
      kWidth * kHeight * 2);
  if (spi_bus_initialize(SPI2_HOST, &bus, SPI_DMA_CH_AUTO) != ESP_OK) return false;

  const esp_lcd_panel_io_spi_config_t ioConfig = SH8601_PANEL_IO_QSPI_CONFIG(kCs, flushReady, &displayDriver);
  esp_lcd_panel_io_handle_t io = nullptr;
  if (esp_lcd_new_panel_io_spi((esp_lcd_spi_bus_handle_t)SPI2_HOST, &ioConfig, &io) != ESP_OK) return false;
  const sh8601_vendor_config_t vendor = {kInit, sizeof(kInit) / sizeof(kInit[0]), {.use_qspi_interface = 1}};
  const esp_lcd_panel_dev_config_t panelConfig = {
      .reset_gpio_num = kReset,
      .rgb_ele_order = LCD_RGB_ELEMENT_ORDER_RGB,
      .bits_per_pixel = 16,
      .vendor_config = const_cast<sh8601_vendor_config_t*>(&vendor),
  };
  esp_lcd_panel_handle_t panel = nullptr;
  if (esp_lcd_new_panel_sh8601(io, &panelConfig, &panel) != ESP_OK ||
      esp_lcd_panel_reset(panel) != ESP_OK || esp_lcd_panel_init(panel) != ESP_OK) return false;
  esp_lcd_panel_disp_on_off(panel, true);
  // The AMOLED controller and shared I2C peripherals need time to settle after
  // a real USB power loss. Upload reset is warmer and masked this race.
  delay(120);

  const i2c_config_t i2c = {.mode = I2C_MODE_MASTER, .sda_io_num = kTouchSda, .scl_io_num = kTouchScl,
      .sda_pullup_en = GPIO_PULLUP_ENABLE, .scl_pullup_en = GPIO_PULLUP_ENABLE,
      .master = {.clk_speed = 300000}};
  const bool i2cReady = i2c_param_config(I2C_NUM_0, &i2c) == ESP_OK &&
      i2c_driver_install(I2C_NUM_0, i2c.mode, 0, 0, 0) == ESP_OK;
  esp_lcd_panel_io_handle_t touchIo = nullptr;
  const esp_lcd_panel_io_i2c_config_t touchIoConfig = ESP_LCD_TOUCH_IO_I2C_FT5x06_CONFIG();
  const bool touchIoReady = i2cReady &&
      esp_lcd_new_panel_io_i2c((esp_lcd_i2c_bus_handle_t)I2C_NUM_0, &touchIoConfig, &touchIo) == ESP_OK;
  const esp_lcd_touch_config_t touchConfig = {.x_max = kHeight - 1, .y_max = kWidth - 1,
      .rst_gpio_num = GPIO_NUM_NC, .int_gpio_num = GPIO_NUM_NC,
      .levels = {.reset = 0, .interrupt = 0}, .flags = {.swap_xy = 1, .mirror_x = 0, .mirror_y = 1}};
  if (!touchIoReady || esp_lcd_touch_new_i2c_ft5x06(touchIo, &touchConfig, &touch) != ESP_OK) {
    touch = nullptr;
    Serial.println("RunDeck touch unavailable; continuing without touch");
  }

  lv_init();
  auto* first = static_cast<lv_color_t*>(heap_caps_malloc(kWidth * kBufferHeight * sizeof(lv_color_t), MALLOC_CAP_DMA));
  auto* second = static_cast<lv_color_t*>(heap_caps_malloc(kWidth * kBufferHeight * sizeof(lv_color_t), MALLOC_CAP_DMA));
  if (!first || !second) return false;
  lv_disp_draw_buf_init(&drawBuffer, first, second, kWidth * kBufferHeight);
  lv_disp_drv_init(&displayDriver);
  displayDriver.hor_res = kWidth; displayDriver.ver_res = kHeight; displayDriver.draw_buf = &drawBuffer;
  displayDriver.flush_cb = flush; displayDriver.rounder_cb = rounder; displayDriver.user_data = panel;
  lv_disp_t* display = lv_disp_drv_register(&displayDriver);
  static lv_indev_drv_t input;
  lv_indev_drv_init(&input); input.type = LV_INDEV_TYPE_POINTER; input.disp = display; input.read_cb = readTouch;
  lv_indev_drv_register(&input);
  const esp_timer_create_args_t timerArgs = {.callback = tick, .name = "rundeck_lvgl"};
  esp_timer_handle_t timer = nullptr;
  return esp_timer_create(&timerArgs, &timer) == ESP_OK && esp_timer_start_periodic(timer, 2000) == ESP_OK;
}

}  // namespace rundeck
