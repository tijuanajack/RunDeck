#include <stdint.h>

#include "driver/spi_master.h"
#include "esp_err.h"
#include "esp_heap_caps.h"
#include "esp_lcd_panel_io.h"
#include "esp_lcd_panel_ops.h"
#include "esp_lcd_sh8601.h"
#include "esp_log.h"
#include "esp_timer.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "lvgl.h"

/* Display-only cold-boot gate for Waveshare ESP32-S3-Touch-AMOLED-2.41. */
static const char *const TAG = "rundeck_baseline";

#define LCD_HOST SPI2_HOST
#define LCD_H_RES 600
#define LCD_V_RES 450
#define LCD_BITS_PER_PIXEL 16
#define LCD_CS GPIO_NUM_9
#define LCD_PCLK GPIO_NUM_10
#define LCD_DATA0 GPIO_NUM_11
#define LCD_DATA1 GPIO_NUM_12
#define LCD_DATA2 GPIO_NUM_13
#define LCD_DATA3 GPIO_NUM_14
#define LCD_RST GPIO_NUM_21
#define LCD_Y_OFFSET 16
#define LVGL_BUF_ROWS (LCD_V_RES / 10)

static lv_disp_draw_buf_t sDrawBuffers;
static lv_disp_drv_t sDisplayDriver;

static const sh8601_lcd_init_cmd_t kPanelInit[] = {
    {0xFE, (uint8_t[]){0x20}, 1, 0}, {0x26, (uint8_t[]){0x0A}, 1, 0},
    {0x24, (uint8_t[]){0x80}, 1, 0}, {0xFE, (uint8_t[]){0x00}, 1, 0},
    {0x3A, (uint8_t[]){0x55}, 1, 0}, {0xC2, (uint8_t[]){0x00}, 1, 10},
    {0x35, (uint8_t[]){0x00}, 0, 0}, {0x51, (uint8_t[]){0x00}, 1, 10},
    {0x11, (uint8_t[]){0x00}, 0, 80},
    {0x2A, (uint8_t[]){0x00, 0x10, 0x00, 0xD1}, 4, 0},
    {0x2B, (uint8_t[]){0x00, 0x00, 0x00, 0x57}, 4, 0},
    {0x29, (uint8_t[]){0x00}, 0, 10}, {0x36, (uint8_t[]){0x30}, 1, 0},
    {0x51, (uint8_t[]){0xFF}, 1, 0},
};

static bool flush_ready(esp_lcd_panel_io_handle_t io,
                        esp_lcd_panel_io_event_data_t *event, void *context)
{
    lv_disp_flush_ready(context);
    return false;
}

static void lvgl_flush(lv_disp_drv_t *driver, const lv_area_t *area,
                       lv_color_t *pixels)
{
    esp_lcd_panel_handle_t panel = driver->user_data;
    ESP_ERROR_CHECK(esp_lcd_panel_draw_bitmap(panel, area->x1, area->y1 + LCD_Y_OFFSET,
                                               area->x2 + 1, area->y2 + LCD_Y_OFFSET + 1,
                                               pixels));
}

static void lvgl_rounder(lv_disp_drv_t *driver, lv_area_t *area)
{
    area->x1 &= ~1;
    area->x2 |= 1;
    area->y1 &= ~1;
    area->y2 |= 1;
}

static void lvgl_tick(void *arg)
{
    lv_tick_inc(2);
}

void app_main(void)
{
    ESP_LOGI(TAG, "cold-boot display gate starting");
    const spi_bus_config_t bus = SH8601_PANEL_BUS_QSPI_CONFIG(
        LCD_PCLK, LCD_DATA0, LCD_DATA1, LCD_DATA2, LCD_DATA3,
        LCD_H_RES * LCD_V_RES * LCD_BITS_PER_PIXEL / 8);
    ESP_ERROR_CHECK(spi_bus_initialize(LCD_HOST, &bus, SPI_DMA_CH_AUTO));

    esp_lcd_panel_io_handle_t io = NULL;
    const esp_lcd_panel_io_spi_config_t io_config =
        SH8601_PANEL_IO_QSPI_CONFIG(LCD_CS, flush_ready, &sDisplayDriver);
    ESP_ERROR_CHECK(esp_lcd_new_panel_io_spi((esp_lcd_spi_bus_handle_t)LCD_HOST,
                                              &io_config, &io));
    const sh8601_vendor_config_t vendor = {
        .init_cmds = kPanelInit, .init_cmds_size = sizeof(kPanelInit) / sizeof(kPanelInit[0]),
        .flags.use_qspi_interface = 1,
    };
    const esp_lcd_panel_dev_config_t panel_config = {
        .reset_gpio_num = LCD_RST, .rgb_ele_order = LCD_RGB_ELEMENT_ORDER_RGB,
        .bits_per_pixel = LCD_BITS_PER_PIXEL, .vendor_config = &vendor,
    };
    esp_lcd_panel_handle_t panel = NULL;
    ESP_ERROR_CHECK(esp_lcd_new_panel_sh8601(io, &panel_config, &panel));
    ESP_ERROR_CHECK(esp_lcd_panel_reset(panel));
    ESP_ERROR_CHECK(esp_lcd_panel_init(panel));
    ESP_ERROR_CHECK(esp_lcd_panel_disp_on_off(panel, true));

    lv_init();
    const size_t pixels = LCD_H_RES * LVGL_BUF_ROWS;
    lv_color_t *buf1 = heap_caps_malloc(pixels * sizeof(lv_color_t), MALLOC_CAP_DMA);
    lv_color_t *buf2 = heap_caps_malloc(pixels * sizeof(lv_color_t), MALLOC_CAP_DMA);
    assert(buf1 != NULL && buf2 != NULL);
    lv_disp_draw_buf_init(&sDrawBuffers, buf1, buf2, pixels);
    lv_disp_drv_init(&sDisplayDriver);
    sDisplayDriver.hor_res = LCD_H_RES;
    sDisplayDriver.ver_res = LCD_V_RES;
    sDisplayDriver.flush_cb = lvgl_flush;
    sDisplayDriver.rounder_cb = lvgl_rounder;
    sDisplayDriver.draw_buf = &sDrawBuffers;
    sDisplayDriver.user_data = panel;
    lv_disp_drv_register(&sDisplayDriver);

    const esp_timer_create_args_t tick_args = {.callback = lvgl_tick, .name = "lvgl_tick"};
    esp_timer_handle_t tick_timer = NULL;
    ESP_ERROR_CHECK(esp_timer_create(&tick_args, &tick_timer));
    ESP_ERROR_CHECK(esp_timer_start_periodic(tick_timer, 2000));

    lv_obj_set_style_bg_color(lv_scr_act(), lv_color_hex(0x00FF00), LV_PART_MAIN);
    lv_obj_set_style_bg_opa(lv_scr_act(), LV_OPA_COVER, LV_PART_MAIN);
    ESP_LOGI(TAG, "DISPLAY_GATE_PASS: LVGL display initialized");
    while (true) {
        lv_timer_handler();
        vTaskDelay(pdMS_TO_TICKS(5));
    }
}
