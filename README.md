# RunDeck

RunDeck is a 600 × 450 landscape running display for the Waveshare
ESP32-S3-Touch-AMOLED-2.41-B, paired with an Android phone.

## Repository layout

| Directory | Purpose |
| --- | --- |
| `firmware/` | Arduino-core firmware and board bring-up tooling |
| `android/` | Kotlin/Compose companion application scaffold |
| `protocol/` | Versioned BLE wire contract and test vectors |
| `docs/` | Architecture, hardware validation, and test plans |
| `assets/` | Project-owned fonts, icons, and mockups |

## Start here

1. Read [hardware validation](docs/hardware-validation.md) before flashing a
   board. The current Waveshare demo archive identifies its panel driver as
   **SH8601**, not RM690B0; validation on the delivered device is the source of
   truth.
2. Install Arduino CLI and ESP32 Arduino core `3.0.7`, then run
   `firmware/tools/fetch-waveshare-bsp.sh`. It obtains the unmodified vendor
   display/touch source locally; it is intentionally not committed.
3. Build `firmware/RunDeck/RunDeck.ino` for the Waveshare ESP32-S3 board after
   the vendor LVGL test has flashed successfully.

The first implemented milestone is a standalone, simulated-data firmware UI.
Android, live GPS, heart-rate, media, and notification integrations are
scaffolded/documented but require physical-device validation before release.

## Git workflow

`origin` is `https://github.com/tijuanajack/RunDeck.git` and development is on
`main`. Before pushing: run the applicable checks, review `git diff`, and make
small intentional commits. Do not commit device IDs, notification contents,
API keys, firmware binaries, or downloaded vendor archives.
