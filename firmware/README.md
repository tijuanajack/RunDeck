# Firmware

The firmware is an Arduino-core `3.0.7` project because that is the Waveshare
supported bring-up path. It uses the ESP-IDF display APIs bundled in that core.

## Prerequisites

```sh
arduino-cli core update-index
arduino-cli core install esp32:esp32@3.0.7
arduino-cli lib install lvgl@8.4.0
./tools/fetch-waveshare-bsp.sh
```

Run the fetched `09_LVGL_Test` unchanged first. Its result establishes panel,
touch, rotation, and USB serial behavior. Only then copy/adapt the vendor
`esp_lcd_sh8601.*` and `esp_lcd_touch*` files into `RunDeck/vendor/` as
described by the script output.

The implementation is intentionally split by responsibility: `app/` holds the
display state and simulated source, `ui/` owns view construction, and `board/`
will own the board adapter after validation. `RunDeck.ino` is only the
composition root.

## Flashing

Use the board/port reported by `arduino-cli board list`, enabling USB CDC on
boot where the installed board package exposes that option. Record the exact
FQBN and upload command in `docs/hardware-validation.md` after the first
successful flash; this repository does not invent board flags that have not
been validated on the delivered revision.
