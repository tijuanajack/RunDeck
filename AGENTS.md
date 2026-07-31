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
- Android phone-side run controls now include pause, resume, stop, moving time,
  and a local resume/discard checkpoint screen. The display still needs a
  production-grade paused-state rendering path, but first-slice device-origin
  commands over characteristic `0005` now exist; keep them clearly labeled as
  pending physical verification.
- Device Setup exposes a user-controlled BACKGROUND RUNS battery-optimization
  exemption flow. It must remain opt-in and must be rechecked after returning
  from Android Settings; never attempt to change the exemption silently.
- Notification dismissal uses device-event type `0x54`: firmware sends the
  Android notification sequence after a swipe-down, and Android maps it only
  to an in-memory clearable notification key while the listener is connected.
  Never persist notification content or keys, and never dismiss a source that
  was not forwarded as clearable.
- Contact filtering is opt-in: ALL CONTACTS remains the default, while
  SELECTED mode matches sanitized sender labels within each source package.
  Do not broaden it to infer contacts from unsanitized notification content.
- `DeviceViewModel` belongs under the `device` package; keep BLE/media/
  notification/weather coordination out of `MainActivity` as future Gradle
  feature modules are introduced.
- Device setup composables belong under `device/DeviceSetupScreen.kt`; shared
  visual primitives belong under `ui/RunDeckComponents.kt`. Preserve the
  black/lime/cyan visual contract when extracting additional screens.
- HR ownership is selected by `HrOwnershipPreferences`: default to
  `PhoneForwardedHr`, but transmit zero/unavailable unless that source has a
  real reading. `DirectDeviceHr` is a reserved mode behind the Garmin
  concurrent-role soak gate; never synthesize strap data.
- Combined status uses live metric flags `0x0040=HR LOW` and `0x0080=HR HIGH`;
  high HR dominates pace (`BACK OFF`, or `EASE OFF` when pace is also fast).
  Keep the display labels non-medical and render unavailable HR as absent.
- Phone-forwarded HR uses the standard `0x180D` service and `0x2A37`
  measurement characteristic in `hr/HeartRateClient.kt`. Keep reconnects
  bounded, retain only the selected device address, and clear BPM on source
  loss; do not make direct-device HR appear available before the concurrent
  ESP32 central/peripheral soak gate.
- The firmware direct client is in `hr/direct_hr.*` and is deliberately
  disabled in `RunDeck.ino` until soak testing proves Android peripheral
  throughput, HR reconnection, and UI responsiveness together.
- Media metadata is now Android-to-device over characteristic `0003` as a
  bounded ASCII CBOR packet sourced from active Android MediaSession
  controllers. The Android app has phone-side previous/play-pause/next buttons,
  and RunDeck Music-screen PREV/PLAY-PAUSE/NEXT now send device-origin media
  control events over characteristic `0005`; user confirmation is still needed.
  Keep display-bound media strings backed by persistent storage, not local
  decoded packet copies; the temporary-copy bug caused random characters under
  the song title. Long Music-screen titles use LVGL circular scrolling, and
  stale MediaSession timestamps should not replace known title/artist text with
  `MEDIA STALE`.
- Device-event `0005` currently exposes run-state ACK as an 8-byte compact
  event and device-origin media controls as 8-byte notify events. Android reads
  `0005` after the run-state write completes and shows `PRESET ACCEPTED`;
  Android also subscribes to `0005` notifications through the explicit GATT
  operation queue. A Samsung test exposed `prior command is not finished` when
  notification subscription overlapped the protocol start, so keep future GATT
  work on that queue.
- Device-event `0005` also carries first-slice run controls (`0x53`, actions
  `4=Pause`, `5=Resume`, `6=Stop`) from a swipe-up RunDeck controls panel.
  Android dispatches them to `RunTrackingService`; paused state is reflected
  in live-metrics flags. Keep the event queue serialized.
- Messaging overlays are first-slice V1: Android forwards only clearable,
  likely message-style notifications through the existing notification-listener
  service, parses recent individual `MessagingStyle` messages from Samsung /
  Google bundled conversation notifications, sanitizes/truncates app/title/body,
  suppresses exact duplicates for 90 seconds, queues bundled messages to the
  device with a short visible gap, and writes an unfragmented CBOR packet on
  `0004`. Firmware shows a 12-second modal overlay on any page and supports
  local swipe-down dismiss. Android has a first app-level allowlist UI with
  forwarding on/off, all-message-apps mode, selected-sources mode, and local
  persistence for observed/common message apps. Contact allowlisting, Android
  dismissal ack, and fragmentation are not complete.
- Environment context uses characteristic `0006`: Android sends local
  `h:mm AM/PM` and Open-Meteo Fahrenheit state every 30 seconds, with weather
  refreshes capped at 10 minutes and stale/unavailable labels preserved on the
  device. Firmware reads the Waveshare battery divider on GPIO17 with GPIO16
  battery enable; treat the resulting percentage as provisional until USB-only
  and installed-LiPo ADC readings are recorded.
- During an active run, `RunTrackingService` holds a partial wake lock and
  publishes elapsed/moving-time state every second even when GPS callbacks are
  delayed by a screen lock. Keep the lock scoped to active/resumed runs; a
  dedicated foreground BLE service and Samsung battery-optimization guidance
  are still available if real lock tests show GATT dropouts.
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
