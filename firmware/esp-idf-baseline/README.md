# ESP-IDF display baseline

This is a deliberately display-only cold-boot gate for the Waveshare
ESP32-S3-Touch-AMOLED-2.41. It pins ESP-IDF 5.5.2 and the SH8601 panel driver
used by the factory program, while excluding touch, LVGL, BLE, Wi-Fi, and the
factory peripheral diagnostics.

Build only:

```sh
source /home/tjhurt/.local/esp-idf-v5.5.2/export.sh
idf.py build
```

Do not flash this project until the hardware gate in `docs/status.md` is
explicitly updated. The acceptance test is a solid green display after three
USB cold boots, with a serial `DISPLAY_GATE_PASS` log each time.
