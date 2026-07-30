# Hardware validation log

This document is a required gate before RunDeck firmware is flashed. Do not
erase factory/test firmware until its recovery image has been obtained from the
Waveshare archive and its behavior recorded.

## Confirmed from the current vendor archive

- Board: ESP32-S3, 16 MB flash, 8 MB PSRAM; 600 × 450 AMOLED; 5-point I2C touch.
- Vendor Arduino board package: ESP32 core 3.0.7; LVGL 8.4.0; RGB565.
- Landscape test uses 600 × 450, QSPI CS/CLK/D0..D3 on GPIO9..14, reset GPIO21,
  and touch I2C SDA/SCL on GPIO47/48.
- The 2025 vendor LVGL source uses `esp_lcd_sh8601`, not RM690B0. Treat the
  delivered board and unmodified vendor test as authoritative.

## Per-board log

| Item | Result |
| --- | --- |
| Board marking / case revision | |
| USB serial device | |
| Arduino CLI version | |
| ESP32 core/FQBN/options | |
| Vendor archive hash | |
| Vendor display test flashed | |
| Landscape orientation/touch mapping | |
| BLE scan/advertise test | |
| Wi-Fi test | |
| ADC result, USB only | |
| ADC result, installed LiPo | |
| Factory restore command/image | |

## 2026-07-30 cold-boot finding

The full factory backup boots after a five-second USB unplug/replug. Both the
RunDeck Arduino build and Waveshare's untouched Arduino `09_LVGL_Test` build
uploaded successfully and rendered after the upload reset, but remained black
after the same true cold boot. The Arduino path is therefore not a valid
hardware bring-up baseline for this board as currently configured. The factory
backup has a different bootloader and partition table. Preserve the board on
the factory image while the vendor ESP-IDF/factory boot configuration is
investigated.

## Required procedure

1. Fetch the vendor archive using `firmware/tools/fetch-waveshare-bsp.sh`.
2. Build and flash its ADC, I2C/RTC, IMU, Wi-Fi, battery, and LVGL examples
   without modification. Capture serial output and exact commands.
3. Verify display rotation, touch coordinates, and repeated reset behavior.
4. Only then port the minimal display/touch adapter into `firmware/RunDeck`.
5. Record battery ADC behavior both on USB and an installed LiPo. No battery
   percentage may be shown in RunDeck before this evidence exists.
6. Once all required checks pass, use `RUNDECK_HARDWARE_VALIDATED=1
   firmware/tools/flash-rundeck.sh` to build and flash RunDeck. The script
   requires exactly one detected serial board.

BOOT remains a boot/recovery control. PWR is not assigned any RunDeck safety
action until it has been electrically and behaviorally tested.
