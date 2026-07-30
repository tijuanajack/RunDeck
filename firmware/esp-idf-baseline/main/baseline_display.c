#include <stdint.h>

#include "driver/spi_master.h"
#include "esp_err.h"
#include "esp_heap_caps.h"
#include "esp_lcd_panel_io.h"
#include "esp_lcd_panel_ops.h"
#include "esp_lcd_sh8601.h"
#include "esp_log.h"

/*
 * Deliberately small cold-boot gate for ESP32-S3-Touch-AMOLED-2.41.
 *
 * This uses the pinout and panel command sequence in Waveshare's factory
 * program, but excludes touch, LVGL, Wi-Fi, BLE, and the factory GPIO test.
 * A solid green panel after three USB cold boots is the prerequisite for
 * bringing any RunDeck code onto ESP-IDF.
 */
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

static const sh8601_lcd_init_cmd_t kPanelInit[] = {
    {0xFE, (uint8_t[]){0x20}, 1, 0},
    {0x26, (uint8_t[]){0x0A}, 1, 0},
    {0x24, (uint8_t[]){0x80}, 1, 0},
    {0xFE, (uint8_t[]){0x00}, 1, 0},
    {0x3A, (uint8_t[]){0x55}, 1, 0},
    {0xC2, (uint8_t[]){0x00}, 1, 10},
    {0x35, (uint8_t[]){0x00}, 0, 0},
    {0x51, (uint8_t[]){0x00}, 1, 10},
    {0x11, (uint8_t[]){0x00}, 0, 80},
    {0x2A, (uint8_t[]){0x00, 0x10, 0x00, 0xD1}, 4, 0},
    {0x2B, (uint8_t[]){0x00, 0x00, 0x00, 0x57}, 4, 0},
    {0x29, (uint8_t[]){0x00}, 0, 10},
    {0x36, (uint8_t[]){0x30}, 1, 0},
    {0x51, (uint8_t[]){0xFF}, 1, 0},
};

void app_main(void)
{
    ESP_LOGI(TAG, "cold-boot display gate starting");

    const spi_bus_config_t bus = SH8601_PANEL_BUS_QSPI_CONFIG(
        LCD_PCLK, LCD_DATA0, LCD_DATA1, LCD_DATA2, LCD_DATA3,
        LCD_H_RES * LCD_V_RES * LCD_BITS_PER_PIXEL / 8);
    ESP_ERROR_CHECK(spi_bus_initialize(LCD_HOST, &bus, SPI_DMA_CH_AUTO));

    esp_lcd_panel_io_handle_t io = NULL;
    const esp_lcd_panel_io_spi_config_t io_config =
        SH8601_PANEL_IO_QSPI_CONFIG(LCD_CS, NULL, NULL);
    ESP_ERROR_CHECK(esp_lcd_new_panel_io_spi((esp_lcd_spi_bus_handle_t)LCD_HOST,
                                              &io_config, &io));

    const sh8601_vendor_config_t vendor = {
        .init_cmds = kPanelInit,
        .init_cmds_size = sizeof(kPanelInit) / sizeof(kPanelInit[0]),
        .flags.use_qspi_interface = 1,
    };
    const esp_lcd_panel_dev_config_t panel_config = {
        .reset_gpio_num = LCD_RST,
        .rgb_ele_order = LCD_RGB_ELEMENT_ORDER_RGB,
        .bits_per_pixel = LCD_BITS_PER_PIXEL,
        .vendor_config = &vendor,
    };

    esp_lcd_panel_handle_t panel = NULL;
    ESP_ERROR_CHECK(esp_lcd_new_panel_sh8601(io, &panel_config, &panel));
    ESP_ERROR_CHECK(esp_lcd_panel_reset(panel));
    ESP_ERROR_CHECK(esp_lcd_panel_init(panel));
    ESP_ERROR_CHECK(esp_lcd_panel_disp_on_off(panel, true));

    uint16_t *const green = heap_caps_malloc(
        LCD_H_RES * LCD_V_RES * sizeof(*green), MALLOC_CAP_DMA);
    assert(green != NULL);
    for (int pixel = 0; pixel < LCD_H_RES * LCD_V_RES; ++pixel) {
        green[pixel] = 0x07E0;  // RGB565 green: unmistakable baseline output.
    }
    ESP_ERROR_CHECK(esp_lcd_panel_draw_bitmap(panel, 0, 16, LCD_H_RES,
                                               LCD_V_RES + 16, green));
    ESP_LOGI(TAG, "DISPLAY_GATE_PASS: panel initialized and painted green");
}
