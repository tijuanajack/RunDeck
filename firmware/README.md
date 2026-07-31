# Firmware

The firmware is an Arduino-core `3.0.7` project because that is the Waveshare
supported bring-up path. It uses the ESP-IDF display APIs bundled in that core.

## Prerequisites

```sh
arduino-cli core update-index
arduino-cli core install esp32:esp32@3.0.7
arduino-cli lib install lvgl@8.4.0
arduino-cli lib install NimBLE-Arduino@2.5.1
./tools/fetch-waveshare-bsp.sh
```

The downloaded legacy `09_LVGL_Test` is retained as a historical diagnostic;
on this board it uploaded but left the panel black. The working RunDeck build
uses the newer Waveshare V2 reset sequence: TCA9554 EXIO0 resets the SH8601
OLED, EXIO1 resets touch, and the direct GPIO21 LCD reset is disabled.

The implementation is intentionally split by responsibility: `app/` holds the
display state and simulated source, `ui/` owns view construction, and `board/`
owns the verified board adapter. `RunDeck.ino` is only the composition root.

## Flashing

Use the vendor-supported FQBN `esp32:esp32:waveshare_esp32_s3_touch_amoled_241`.
After the vendor smoke-test results are recorded, connect exactly one board and
run:

```sh
RUNDECK_HARDWARE_VALIDATED=1 ./tools/flash-rundeck.sh
```

The script refuses to flash before validation and refuses ambiguous multi-board
selection. It compiles the same source immediately before upload.

## BLE pairing prototype

The display advertises as `RunDeck` with the versioned service specified in
`protocol/ble-protocol.md`. The Android companion filters for that service and
writes validated live-metrics frames at 1 Hz. Until a valid frame arrives the
display shows connection/unavailable states; incoming values expire after five
seconds and never appear live once stale.
