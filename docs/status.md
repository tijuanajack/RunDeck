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
  attached Samsung after reconnecting ADB. Contact-level allowlisting,
  Android-side dismissal acknowledgements, and fragmented long notifications
  remain future work.
- Device Setup usability checkpoint: the setup screen now scrolls on the
  phone, so the Messages card's ALL APPS / SELECTED controls and source rows
  are reachable on a full-height device. Android tests/build and install/launch
  passed on 2026-07-31.
- Notification end-to-end verification: the user confirmed a new test message
  appeared on RunDeck after selecting the message source. The phone listener,
  allowlist, BLE notification write, and device overlay are working together.
- The selected V1 Long Run target is 8:50–9:20 /mi. Android derives
  `ON TARGET`, `EASE OFF`, `PICK IT UP`, or `GPS WEAK` and sends that state to
  the display. Active-run distance, elapsed time, and pace are checkpointed
  locally with DataStore; stopping the run clears the checkpoint.
- A full 16 MB pre-RunDeck recovery image exists locally at
  `firmware/backups/factory-before-rundeck.bin` and is intentionally ignored by
  Git.

## Current limitations (do not misrepresent as complete)

- The Android app is still a compact single-module prototype. Hilt, Room,
  feature modules, full preset editing, and production-grade checkpoint
  recovery are not implemented.
- The device currently receives live metrics and the first bounded CBOR
  run-state/preset packet, and Android can read back the first run-state ACK.
  Phone-side pause/resume/stop exists, Android-to-device media metadata is
  wired, and device-origin Music-screen controls are implemented pending user
  confirmation. First messaging overlays are wired. Device-origin
  pause/resume/stop commands, notification dismissal acknowledgements,
  settings, and heartbeat contracts are not implemented end-to-end.
- Heart rate remains optional. Live phone-GPS runs display the Garmin strap as
  off/unavailable until an actual HR source is connected. Garmin HRM-Dual
  direct mode, concurrent central/peripheral soak testing, and phone-forwarded
  HR remain future work.
- Full notification allowlisting/dismissal, Open-Meteo, touch lock,
  brightness/power work, and real-run outdoor validation remain future work.
- The scripted flash helper deliberately refuses to choose between multiple
  serial ports. When the phone is also attached, identify the Espressif USB
  JTAG port via `udevadm` and use that explicit port.

## Next implementation order

1. Continue BLE/live phone metrics polish and reconnect visibility; re-run the
   full-UI cold-boot gate three times when the user can physically test it.
   Restore factory immediately on any failed cold boot.
2. Confirm device-origin previous/play-pause/next from the RunDeck Music screen
   works on the phone, then polish any tap-size/debounce issues found.
3. Continue V1 run-state protocol: add device-origin pause/resume/stop command
   flow and show paused state on the display.
4. Polish notifications: add explicit allowlist UI and permitted dismissal
   acknowledgements; then add weather freshness, settings, touch
   lock/brightness, resilience tests, and outdoor/power validation.
5. Add optional HR: phone-forwarded HR and target/combined pace-HR status
   rules first, then evaluate direct Garmin HRM-Dual only behind the
   BLE-concurrency soak gate. The strap must be treated as absent when not
   worn/connected.
