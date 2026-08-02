# RunDeck

RunDeck is a 600 × 450 landscape running display for the Waveshare
ESP32-S3-Touch-AMOLED-2.41-B, paired with an Android phone.

## Repository layout

| Directory | Purpose |
| --- | --- |
| `firmware/` | Arduino-core firmware and board bring-up tooling |
| `android/` | Kotlin/Compose Android companion application |
| `protocol/` | Versioned BLE wire contract and test vectors |
| `docs/` | Architecture, hardware validation, and test plans |
| `assets/` | Project-owned fonts, icons, and mockups |

## Current V1

1. Read [hardware validation](docs/hardware-validation.md) and
   [recovery](docs/recovery.md) before flashing. The delivered board uses the
   verified Waveshare V2 TCA9554 reset sequence and SH8601 QSPI panel path.
2. Install the pinned Arduino toolchain from [firmware/README.md](firmware/README.md),
   then build or flash only after identifying the Espressif USB JTAG port.
3. Build the Android companion with `./gradlew testDebugUnitTest assembleDebug`
   from `android/`.

V1 is an end-to-end Android GPS run: the phone owns timing, location, pace,
distance, targets, presets, weather, media, notifications, and configuration;
RunDeck renders the fresh state over BLE. The device boots through the branded
waiting screen, lands on Ready, starts from either phone or RunDeck, shows the
live Dashboard, and returns to Ready after stop. See
[implementation status](docs/status.md) for verified behavior and future work.
For a reusable integration handoff covering hardware, Android lifecycle, BLE,
recovery, and the known failure modes, see
[lessons learned](docs/lessons-learned.md).

## Git workflow

`origin` is `https://github.com/tijuanajack/RunDeck.git` and development is on
`main`. Before pushing: run the applicable checks, review `git diff`, and make
small intentional commits. Do not commit device IDs, notification contents,
API keys, firmware binaries, or downloaded vendor archives.
