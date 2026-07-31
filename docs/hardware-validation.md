# Hardware validation log

This is the historical bring-up record and the safety gate for future firmware
flashes. The current board has a local factory recovery image and a verified
RunDeck image; preserve both before changing the display or power path.

## Confirmed from the current vendor archive

- Board: ESP32-S3, 16 MB flash, 8 MB PSRAM; 600 × 450 AMOLED; 5-point I2C touch.
- Vendor Arduino board package: ESP32 core 3.0.7; LVGL 8.4.0; RGB565.
- The working V2 path uses 600 × 450 SH8601 QSPI, GPIO47/48 for the shared I2C
  bus, TCA9554 EXIO0 for OLED reset, and EXIO1 for touch reset. Direct GPIO21
  LCD reset is disabled.
- The 2025 vendor LVGL source uses `esp_lcd_sh8601`, not RM690B0. Treat the
  delivered board and unmodified vendor test as authoritative.

## Per-board log

| Item | Result |
| --- | --- |
| Board marking / case revision | Waveshare ESP32-S3 Touch AMOLED 2.41-B; exact case revision not recorded |
| USB serial device | Espressif USB JTAG serial debug unit; port number is session-dependent |
| Arduino CLI version | Verify with `arduino-cli version` before a release flash |
| ESP32 core/FQBN/options | Core 3.0.7; `esp32:esp32:waveshare_esp32_s3_touch_amoled_241`; upload 115200 |
| Vendor archive hash | Not recorded; vendor archive is not committed |
| Vendor display test flashed | Legacy examples attempted and failed cold boot; V2 RunDeck BSP is the working path |
| Landscape orientation/touch mapping | 600 × 450 landscape UI and touch navigation confirmed |
| BLE scan/advertise test | RunDeck advertises and Android discovers/connects/reconnects |
| Wi-Fi test | Not required by V1; not recorded |
| ADC result, USB only | Not calibrated; battery percentage remains provisional |
| ADC result, installed LiPo | Not calibrated; battery percentage remains provisional |
| Factory restore command/image | `firmware/backups/factory-before-rundeck.bin`; use `tools/restore-factory.sh` |

## 2026-07-30 cold-boot finding

The full factory backup boots after a five-second USB unplug/replug. With a
replacement known-good data cable, Waveshare's untouched Arduino
`09_LVGL_Test` uploaded and hash-verified but left the panel black immediately.
The untouched vendor ESP-IDF LVGL test also left the panel black. Therefore
the issue is not RunDeck UI, BLE, touch, or cable reliability; neither generic
vendor build currently matches this board's factory boot/flash configuration.
The factory backup has a different bootloader and partition table. Preserve
the factory image as the recovery path.

The original cable was also defective: full-image recovery repeatedly dropped
at about 14%. With the replacement cable and the ESP32-S3 ROM loader
(`--no-stub`, 115200 baud), the complete factory image was restored and hash
verified.

The factory image identifies as ESP-IDF 5.5.2 and contains the vendor
`10_FactoryProgram` modules. Rebuilding that project under ESP-IDF 5.5.2
produced an exact matching partition table plus matching bootloader and app
image header/segment layouts. It displayed after upload reset but still failed
after a true USB cold boot; this is retained as historical failure analysis,
not as the current RunDeck path.

Serial diagnostics identified the historical incompatibility: the downloaded
FactoryProgram source uses the obsolete legacy I2C panel wrapper, while the
factory binary uses `esp_lcd_touch_new_i2c_ft5x06`. With the current resolved
`esp_lcd_touch_ft5x06` component, the former aborts because it supplies
`scl_speed_hz` to a legacy I2C driver. Waveshare documents that this Factory
Program is IDF-version-sensitive and advises its supplied test binary for
validation. Do not rely on the archive's floating component dependencies;
obtain or reconstruct the newer factory BSP revision and lock every component
before any future display-driver experiment.

## Required procedure

1. Keep the factory image and identify the Espressif USB JTAG port with
   `udevadm`; never guess a `ttyACM` number when the phone is attached.
2. Build the pinned Arduino image and confirm the V2 TCA9554 reset path is
   unchanged.
3. Flash RunDeck only to the identified board, then verify display, touch, BLE,
   and the branded boot-to-Ready flow.
4. For any display or power change, restore factory first if the panel is
   black, then require three true USB unplug/replug boots before continuing.
5. Record USB-only and installed-LiPo ADC readings before making battery-life
   or battery-percentage claims; current battery display remains provisional.

BOOT remains a boot/recovery control. PWR is not assigned any RunDeck safety
action until it has been electrically and behaviorally tested.
