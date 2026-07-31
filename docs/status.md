# Implementation status

Last verified: 2026-07-30.

## Current hardware gate

- The board is restored to its factory image, which cold-boots correctly with
  a replacement known-good data cable.
- Earlier downloaded Waveshare examples were stale for this board revision:
  the old Arduino and ESP-IDF LVGL tests uploaded and hash-verified but left
  the panel black. The current Waveshare V2 Arduino example shows the missing
  startup detail: OLED reset is TCA9554 EXIO0, not a direct GPIO21 LCD reset
  pin. Treat the GPIO21 reset path as rejected.
- `firmware/esp-idf-baseline/` uses ESP-IDF 5.5.2, `esp_lcd_sh8601 2.0.1~1`,
  and LVGL 8.3.11 with the factory 16 MB/QIO/80 MHz + octal PSRAM and 9 MB
  factory partition configuration. It excludes touch, BLE, Wi-Fi, and factory
  GPIO diagnostics. It now sets LCD reset to `GPIO_NUM_NC`, initializes the
  TCA9554 on GPIO47/GPIO48, and pulses EXIO0 high/low/high before SH8601 panel
  init. After a full factory restore and app-only flash on 2026-07-30, the
  user verified three true unplug/replug cold boots returned to a solid green
  display.
- The factory image bootloader and partition table differ from the Arduino
  output. The Arduino RunDeck BSP now contains the V2 TCA9554 reset sequence;
  continue to verify physical cold boots before adding more hardware
  complexity.

## Working and verified

- The repository is connected to authenticated HTTPS `origin` and development
  is on `main`.
- The Waveshare ESP32-S3-Touch-AMOLED-2.41 runs RunDeck at 600 × 450
  landscape. Its black AMOLED UI, touch navigation, dashboard, Music, Stats,
  and notification-overlay prototype are present in `firmware/RunDeck/`.
- The display-only ESP-IDF baseline passes the cold-boot gate with the V2
  TCA9554 OLED reset sequence: build successful, app-only flash verified, and
  three physical USB unplug/replug boots returned to the green test screen.
- The Arduino RunDeck BSP now follows that V2 reset sequence: direct LCD reset
  is disabled, GPIO47/GPIO48 I2C is initialized once, TCA9554 EXIO0 is pulsed
  before SH8601 panel init, and EXIO1 is pulsed before FT5x06 touch init.
  The Arduino build passed, upload hashes verified, and the user confirmed the
  full RunDeck UI appeared after flashing on 2026-07-30.
- Firmware uses Arduino ESP32 core 3.0.7, LVGL 8.4.0, and NimBLE-Arduino 2.5.1.
  The working board configuration uses the vendor SH8601 display path.
- The device advertises the RunDeck BLE GATT service and Android can discover,
  connect, disconnect, and reconnect to it.
- The Android companion runs on API 36. It has BLE setup, a Run Setup screen,
  a user-started foreground location service, and a persistent active-run
  notification.
- On 2026-07-30, a real walk verified Android GPS pace, distance, and elapsed
  time agree with the values delivered to the RunDeck display.
- Live BLE polish checkpoint: Android now exposes an explicit phone-to-RunDeck
  bridge status, unit tests cover the status labels, and firmware renders
  absent HR as `--` / `GARMIN STRAP OFF` instead of `0 BPM` or a simulated
  in-zone value. The firmware build and flash were hash-verified on
  2026-07-30; the Android APK built successfully but was not installed because
  no phone was visible to ADB at that moment.
- Run-state/preset protocol checkpoint: Android encodes a bounded CBOR
  run-state packet for characteristic `7b2e0002-...` and sends the Long Run
  preset, ASCII target label, active flag, pace bounds, and optional HR bounds
  on connect/start/stop changes. Firmware validates and stores that packet,
  rejects replayed/malformed/out-of-range payloads, and renders the
  Android-owned `targetLabel` instead of hardcoding `8:50-9:20`. Android unit
  tests and `assembleDebug` passed; firmware compile and flash were
  hash-verified on 2026-07-30. The updated Android APK still needs installation
  when the phone is visible to ADB.
- Post-install bugfix checkpoint: user reported the Android-owned target label
  rendered as random symbols and pace did not match the phone while distance
  and elapsed time did. Firmware now uses the persistent stored run-state
  buffer for target/preset labels instead of a temporary stack copy. Live
  metrics pace changed from `uint16` centiseconds-per-mile, which overflowed
  above about 10:55 /mi, to `uint16` seconds-per-mile so walking paces match
  the phone. Android tests/build passed; firmware compile, flash, APK install,
  app launch, and BLE service discovery were verified on 2026-07-30.
- Run-state ACK checkpoint: firmware now stores an 8-byte ACK event on
  device-event characteristic `7b2e0005-...` after accepting a non-replayed
  run-state packet. Android reads that characteristic after the run-state write
  completes and shows `PRESET ACCEPTED` briefly in the bridge status. A first
  notification-subscription attempt exposed a Samsung GATT `prior command is
  not finished` failure, so this slice intentionally uses read-after-write for
  ACK reliability while leaving `0005` notify-capable for later event work.
  Android tests/build passed; firmware compile/flash and APK install/launch
  were verified on 2026-07-30, with clean BLE connect/service-discovery logs.
- Android run-control checkpoint: the phone active-run screen now supports
  pause, resume, and stop actions. Pause freezes moving time while elapsed time
  can continue, the foreground notification marks paused runs, and the live
  metrics packet now sends the separate moving-time field. Active checkpoints
  can be reloaded after app restart into a paused recovery screen with
  `RESUME CHECKPOINT` and `DISCARD CHECKPOINT`. Android tests/build passed, the
  APK installed, and the app launched without an immediate fatal crash on the
  attached Samsung on 2026-07-30.
- Music bridge checkpoint: Android now declares a notification-listener service
  for user-enabled MediaSession access, exposes a Music card with
  enable/refresh plus previous/play-pause/next controls, serializes GATT
  operations to avoid Samsung overlapping-command failures, and sends bounded
  ASCII CBOR media state on characteristic `0003`. Firmware validates/stores
  that media packet, applies a 30-second freshness window, and renders source,
  title, artist, and play/pause state on the RunDeck Music screen instead of
  the previous hardcoded mock. Android tests/build passed; APK install/launch
  passed; firmware compile and flash to the verified Espressif port were
  hash-verified on 2026-07-30.
- Music metadata bugfix: the firmware media renderer now points at the
  persistent stored media buffers instead of a temporary decoded copy. This
  fixes random characters under the song title. Firmware compile and flash to
  `/dev/ttyACM0` were hash-verified on 2026-07-30.
- Device-origin music controls checkpoint: Android now subscribes to
  characteristic `0005` notifications through the serialized GATT operation
  queue and decodes 8-byte media-control events. RunDeck Music-screen PREV,
  PLAY/PAUSE, and NEXT panels are clickable and notify Android, which dispatches
  them to the active MediaSession. Android tests/build passed, APK
  install/launch passed, and firmware compile/flash to `/dev/ttyACM0` were
  hash-verified on 2026-07-30. User still needs to confirm taps on the RunDeck
  screen control the phone.
- Music display polish: long song titles now use LVGL circular horizontal
  scrolling instead of dot truncation, and the firmware keeps the last known
  title/artist visible when MediaSession metadata ages instead of replacing it
  with `MEDIA STALE`. Firmware compile and flash to `/dev/ttyACM0` were
  hash-verified on 2026-07-30.
- Messaging overlay checkpoint: the existing Android notification-listener
  service now forwards only clearable, likely message-style notifications,
  parses recent individual `MessagingStyle` messages from Samsung / Google
  bundled conversation notifications, sanitizes app/title/body to printable
  ASCII, truncates them to bounded V1 lengths, suppresses exact duplicates for
  90 seconds, and queues bundled messages to the device with a short visible
  gap. Android sends an unfragmented CBOR notification payload on characteristic
  `0004`. Firmware validates/stores that packet, shows a 12-second modal
  overlay on any RunDeck screen, and supports local swipe-down dismissal.
  Android tests/build passed and APK install/launch passed on 2026-07-30.
- Notification allowlist checkpoint: Android now has a first app-level message
  allowlist card on Device Setup with forwarding on/off, all-message-apps mode,
  selected-sources mode, source discovery for common installed message apps and
  observed notification packages, and local persistence. Android tests/build
  passed on 2026-07-31; the updated APK was installed and launched on the
  attached Samsung after reconnecting ADB. Fragmented long notifications
  remain future work.
- Device Setup usability checkpoint: the setup screen now scrolls on the
  phone, so the Messages card's ALL APPS / SELECTED controls and source rows
  are reachable on a full-height device. Android tests/build and install/launch
  passed on 2026-07-31.
- Notification end-to-end verification: the user confirmed a new test message
  appeared on RunDeck after selecting the message source. The phone listener,
  allowlist, BLE notification write, and device overlay are working together.
- Notification dismissal checkpoint: RunDeck swipe-down now emits a bounded
  `0005` `0x54` event containing the Android notification sequence. Android
  keeps only a short in-memory sequence-to-clearable-key map and asks the
  user-enabled notification listener to dismiss that source notification;
  nothing is persisted and non-clearable notifications are never forwarded.
- Contact filter checkpoint: Device Setup now offers ALL CONTACTS (the
  compatibility default) or SELECTED sender mode. Observed sender names are
  sanitized and stored locally with their source package; selected mode must
  be explicitly enabled and does not forward undiscovered senders.
- Android architecture checkpoint: the device-facing coordinator is now
  isolated in `android/app/src/main/java/com/rundeck/app/device/DeviceViewModel.kt`.
  `MainActivity` remains the Compose host while BLE, media, notifications,
  weather context, and device actions stay behind the device ViewModel
  boundary. Android tests/build and APK install/launch passed on 2026-07-31.
- Device UI boundary checkpoint: the setup screen, media controls,
  notification filters, and background-run card now live in the `device`
  package, while shared RunDeck colors and connection primitives live in
  `ui/RunDeckComponents.kt`. The Compose activity remains the navigation host.
- HR ownership checkpoint: Device Setup now persists an explicit
  `PhoneForwardedHr`, `PhoneOnly`, or `DirectDeviceHr` mode. Phone-forwarded
  HR remains the compatibility default; PhoneOnly and DirectDeviceHr send no
  HR value until a matching source is actually available. The display still
  renders unavailable HR as `--` / `GARMIN STRAP OFF`.
- Combined target-status checkpoint: Android now marks forwarded HR as
  `HR LOW`, `HR HIGH`, or `IN ZONE`; high HR dominates pace with `BACK OFF`,
  while high HR plus fast pace yields `EASE OFF`. The corresponding live-metric
  flags and Stats/dashboard labels are implemented and the firmware flashed
  with a verified hash on 2026-07-31.
- Phone-forwarded HR checkpoint: Android now scans the standard Heart Rate
  Service (`0x180D`), subscribes to Heart Rate Measurement (`0x2A37`), parses
  8/16-bit measurements, stores only the selected strap address, and retries
  reconnects with a bounded backoff. The source remains opt-in through the HR
  ownership selector and unavailable readings clear to `GARMIN STRAP OFF`.
- Environment context checkpoint: Android now formats local time and fetches
  current Fahrenheit temperature from Open-Meteo using the active GPS fix,
  caching it with a ten-minute refresh limit and explicit stale/unavailable
  states. A bounded `0006` CBOR context packet carries clock/weather to the
  display every 30 seconds. Firmware renders live time/temperature only while
  context is fresh and reads the Waveshare GPIO17 battery ADC with GPIO16
  battery enable; invalid ADC values render `BAT --`. Firmware compiled and
  flashed successfully on 2026-07-31; Android build passed and the updated APK
  installed/launched on the attached Samsung with no fatal startup crash.
- Clock rendering fix: firmware now keeps the decoded Android clock label in
  its persistent BLE context buffer instead of pointing the LVGL state at a
  temporary stack copy. The fix compiled and flashed successfully on
  2026-07-31.
- Screen-lock resilience checkpoint: the Android run service now holds a
  partial wake lock only while an active run is running, and emits elapsed/
  moving-time state every second between GPS callbacks so the BLE stream does
  not depend on a screen refresh. Android tests/build passed on 2026-07-31;
  the updated APK installed/launched successfully on the attached Samsung.
  Device Setup now reports whether Android battery optimization exempts
  RunDeck and provides an explicit user-approved request flow for that
  exemption; the app never changes this setting silently.
- Run-control/Stats checkpoint: RunDeck now exposes a swipe-up RUN CONTROLS
  panel with PAUSE/RESUME and STOP RUN buttons. Actions travel as `0005`
  device events to Android's foreground run service, and paused state is
  carried in live-metrics flags. The Stats screen now renders live pace,
  average pace, speed, HR, distance, elapsed, temperature, and status instead
  of its original mock values. Firmware compile/flash and Android tests/build
  passed on 2026-07-31; phone APK installation remains pending until ADB sees
  the Samsung.
- The selected V1 Long Run target is 8:50–9:20 /mi. Android derives
  `ON TARGET`, `EASE OFF`, `PICK IT UP`, or `GPS WEAK` and sends that state to
  the display. Active-run distance, elapsed time, and pace are checkpointed
  locally with DataStore; stopping the run clears the checkpoint.
- A full 16 MB pre-RunDeck recovery image exists locally at
  `firmware/backups/factory-before-rundeck.bin` and is intentionally ignored by
  Git.

## Current limitations (do not misrepresent as complete)

- The Android app is still a compact single-module prototype. Hilt, Room,
  Gradle feature modules, full preset editing, and production-grade checkpoint
  recovery are not implemented. The first device feature boundary is now in
  place without changing the working BLE contract.
- The device currently receives live metrics and the first bounded CBOR
  run-state/preset packet, and Android can read back the first run-state ACK.
  Phone-side pause/resume/stop exists, Android-to-device media metadata is
  wired, device-origin Music-screen controls are wired, and device-origin
  run-control commands now have their first end-to-end slice. Notification
  settings and heartbeat contracts are not implemented end-to-end.
- Heart rate remains optional. Live phone-GPS runs display the Garmin strap as
  off/unavailable until an actual HR source is connected. Garmin HRM-Dual
  direct mode, concurrent central/peripheral soak testing, and a real
  phone-forwarded HR source remain future work; the ownership selector and
  safe packet gating are now in place. Physical strap pairing and a real HR
  run are the next verification gate.
- Fragmented long notifications,
  weather location before a run, touch lock, brightness/power work, and
  real-run outdoor validation remain future work. App-level message-source
  selection and 90-second duplicate suppression are implemented.
- The scripted flash helper deliberately refuses to choose between multiple
  serial ports. When the phone is also attached, identify the Espressif USB
  JTAG port via `udevadm` and use that explicit port.

## Next implementation order

1. Continue BLE/live phone metrics polish and reconnect visibility; re-run the
   full-UI cold-boot gate three times when the user can physically test it.
   Restore factory immediately on any failed cold boot.
2. Physically verify the new swipe-up Run Controls panel and device-origin
   pause/resume/stop, then polish tap-size/debounce issues found.
3. On the Samsung, review the new BACKGROUND RUNS card and approve the
   battery-optimization exemption before a screen-lock run test. Then add
   permitted contact-level filtering, followed by touch lock/brightness and
   outdoor/power validation.
4. Add optional HR: phone-forwarded HR and target/combined pace-HR status
   rules first, then evaluate direct Garmin HRM-Dual only behind the
   BLE-concurrency soak gate. The strap must be treated as absent when not
   worn/connected.
