# RunDeck working guide

This file is the durable handoff for future Codex sessions. Read
`docs/status.md` before changing code; it distinguishes verified behavior from
planned work.

## Repository and workflow

- Repository: `https://github.com/tijuanajack/RunDeck.git`; authenticated HTTPS
  remote is `origin`.
- Work directly on `main` unless the user explicitly requests another branch.
- Preserve unrelated changes in a dirty tree. Make small, intentional commits,
  run checks, then push only changes made for the requested task.
- Do not claim a next step has been performed until code/build/deployment has
  actually happened. Give short progress updates during long builds/flashes.
- Do not commit APKs, firmware binaries, downloaded vendor archives, device
  identifiers, API keys, notification content, or the recovery image.

## Hardware safety

- Target board: Waveshare ESP32-S3-Touch-AMOLED-2.41, 600 × 450 landscape.
- The verified display implementation is SH8601-based, despite older planning
  references to RM690B0. Do not swap drivers or alter display/panel pin setup
  casually; a prior bad update caused a black-screen recovery event.
- Critical 2026-07-30 finding: the current Waveshare V2 Arduino LVGL example
  resets the OLED through TCA9554 EXIO0 and sets the direct LCD reset pin to
  `-1`. Older/stale examples and prior RunDeck code used a direct GPIO21 LCD
  reset path and black-screened after a true USB cold boot. Do not restore the
  GPIO21 reset path.
- `firmware/esp-idf-baseline/` is the only approved next firmware candidate.
  It is deliberately display-only and pins ESP-IDF 5.5.2 with
  `esp_lcd_sh8601 2.0.1~1` and LVGL 8.3.11. It follows the V2 TCA9554 OLED
  reset sequence and passed three true USB unplug/replug cold boots with a
  solid green panel after a full factory restore and app-only flash. It is
  approved as the display-only cold-boot gate, not as full RunDeck firmware.
- The same reset sequence has been ported into the Arduino RunDeck BSP:
  RunDeck uses no direct LCD reset GPIO, initializes the GPIO47/GPIO48 I2C bus
  once, configures TCA9554 outputs, pulses EXIO0 before SH8601 panel init, and
  pulses EXIO1 before FT5x06 touch init. Build and flash succeeded, and the
  user confirmed the RunDeck UI appeared. Before expanding hardware features,
  ask the user to confirm three physical USB unplug/replug boots still return
  to the RunDeck UI.
- Use a known-good direct USB data cable. A failing cable previously dropped
  full-image writes at about 14%; the ROM-loader factory restore succeeded
  after the cable was replaced.
- FQBN: `esp32:esp32:waveshare_esp32_s3_touch_amoled_241`.
- Tool versions are pinned for bring-up: ESP32 Arduino core **3.0.7**, LVGL
  **8.4.0**, NimBLE-Arduino **2.5.1**.
- The factory recovery image is a local, ignored file:
  `firmware/backups/factory-before-rundeck.bin`. Never delete or overwrite it.
- If the display is black after an update or USB power cycle, follow
  [the recovery gate](docs/recovery.md). Restore factory, obtain a physical
  cold-boot confirmation, then and only then flash RunDeck. A successful
  upload hash is not proof of a successful panel cold boot.
- BOOT is a recovery control. Do not assign safety-critical behavior to PWR.
- The normal helper requires `RUNDECK_HARDWARE_VALIDATED=1` and exactly one
  serial device:

  ```sh
  cd firmware
  RUNDECK_HARDWARE_VALIDATED=1 ./tools/flash-rundeck.sh
  ```

  When an Android phone is also connected, this intentionally refuses to
  guess. Identify the ESP32 first (its USB identity is Espressif USB JTAG),
  then compile/upload explicitly after confirming the port:

  ```sh
  udevadm info --query=property --name=/dev/ttyACM0
  sg dialout -c '/home/tjhurt/.local/bin/arduino-cli compile --fqbn esp32:esp32:waveshare_esp32_s3_touch_amoled_241 RunDeck --output-dir build && /home/tjhurt/.local/bin/arduino-cli upload --fqbn esp32:esp32:waveshare_esp32_s3_touch_amoled_241 --port /dev/ttyACM0 RunDeck'
  ```

  Do not blindly copy `/dev/ttyACM0`: confirm it is the ESP32 each session.
  Use `sg dialout -c` for serial access when needed.

## Android build and device deployment

- Android application ID: `com.rundeck.app`; minimum SDK 26, target SDK 36.
- The connected test phone is a Samsung running API 36. Check it with:

  ```sh
  /home/tjhurt/.local/android-sdk/platform-tools/adb devices
  ```

- Local JDK and SDK used by this workspace:

  ```sh
  cd android
  env PATH=/home/tjhurt/.local/rundeck-toolchain/jdk-17/bin:$PATH ./gradlew --no-daemon testDebugUnitTest assembleDebug --console=plain
  /home/tjhurt/.local/android-sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
  /home/tjhurt/.local/android-sdk/platform-tools/adb shell am force-stop com.rundeck.app
  /home/tjhurt/.local/android-sdk/platform-tools/adb shell am start -n com.rundeck.app/.MainActivity
  ```

- Never grant location or notification permissions through ADB as a substitute
  for the user flow. The app must ask when the user starts a run.

## Current architecture contract

- Android is authoritative for timing, GPS, pace, distance, targets, presets,
  weather, media, notifications, and configuration. Firmware displays only
  fresh state and does not keep full run history.
- Current verified path: Android foreground `LocationManager` service →
  `RunSession` flow → `RunDeckBleClient` live-metrics write at 1 Hz → ESP32
  NimBLE server → immutable firmware `DisplayState` → LVGL dashboard.
- BLE/live-metrics polish is proceeding before protocol/media/notification/HR
  work. Android exposes a live bridge status; absent Garmin HR must render as
  unavailable (`--` / `GARMIN STRAP OFF`), never as a fake or zero BPM value.
- Live metrics are v1, little-endian, 33 bytes total. Preserve validation,
  sequence and freshness protections in `firmware/RunDeck/ble/rundeck_ble.cpp`
  and `android/app/src/main/java/com/rundeck/app/ble/RunDeckProtocol.kt`.
  The pace field is `uint16 pace_seconds_per_mile`; do not revert it to
  centiseconds because that overflowed at walking paces and made the device
  disagree with the phone.
- Run-state `0002` is a bounded fixed-map CBOR packet. Android currently sends
  Long Run active/inactive, ASCII `targetLabel`, pace bounds, and optional HR
  bounds on connect/start/stop changes; firmware validates and stores it, then
  renders the Android-owned target label. Keep display-bound strings ASCII
  unless LVGL font coverage is intentionally expanded.
- Device-event `0005` currently exposes run-state ACK as an 8-byte compact
  event. Android reads `0005` after the run-state write completes and shows
  `PRESET ACCEPTED`; do not reintroduce automatic CCCD notification
  subscription until Android GATT writes are serialized through an explicit
  command queue. A Samsung test exposed `prior command is not finished` when
  notification subscription overlapped the protocol start.
- For the Long Run target, Android owns the 8:50–9:20 /mi rule. Do not hardcode
  a competing target on the display. Firmware currently renders the target and
  status flags sent by Android.
- A zero pace means GPS has not produced a reliable pace; it must render as
  `--:--`, never a plausible live pace.

## Validation before handoff

- Android changes: run unit tests and `assembleDebug`; install/launch on the
  attached phone when the change affects user-visible behavior. Check logcat
  for `FATAL EXCEPTION` after launch.
- Firmware changes: compile before upload. Confirm the serial port belongs to
  the ESP32 and report successful flash output. Do not flash merely to test an
  unrelated Android change.
- BLE changes: test scan, connect, reconnect, and a live metric update. For
  GPS changes, perform a short real walk whenever feasible and compare phone
  and display pace/distance/elapsed values.

## Roadmap boundaries

Implement in this user-selected order: BLE/live phone metrics polish; full
run-state/preset CBOR and acknowledgements plus resume/discard checkpoint UI;
MediaSession metadata/actions and Music bridge; notification/weather/settings/
touch-lock/brightness/resilience work; optional HR last. For HR, add
phone-forwarded support before direct Garmin HRM-Dual, and treat the strap as
absent whenever it is not worn/connected. Direct-HR still requires the
concurrent BLE role soak gate.
Do not add maps, ski mode, Bowline, cloud/Garmin integrations, iOS, HRV,
structured workouts, or voice reply to V1.
