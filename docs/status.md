# Implementation status

Last verified: 2026-07-30.

## Current hardware gate

- The board is restored to its factory image, which cold-boots correctly with
  a replacement known-good data cable.
- Waveshare's untouched Arduino `09_LVGL_Test` was built, flashed, and
  hash-verified but left the panel black immediately. The untouched vendor
  ESP-IDF LVGL test did the same. This rejects RunDeck source, touch, BLE,
  UI code, and the original cable as the root cause.
- The vendor `10_FactoryProgram`, built with ESP-IDF 5.5.2, matches the
  factory image's 16 MB partition table and boot/app headers. It lights the
  panel after upload reset but still black-screens after a true USB cold boot.
  The next firmware test must capture its serial cold-boot log; the board has
  been restored to factory afterward.
- The factory image bootloader and partition table differ from the Arduino
  output. Do **not** flash another Arduino build as a normal development step.
  The next firmware task is to reproduce the factory/vendor boot configuration
  and display/power configuration from the factory image, then pass three cold
  boots before restoring RunDeck functionality.

## Working and verified

- The repository is connected to authenticated HTTPS `origin` and development
  is on `main`.
- The Waveshare ESP32-S3-Touch-AMOLED-2.41 runs RunDeck at 600 × 450
  landscape. Its black AMOLED UI, touch navigation, dashboard, Music, Stats,
  and notification-overlay prototype are present in `firmware/RunDeck/`.
- Firmware uses Arduino ESP32 core 3.0.7, LVGL 8.4.0, and NimBLE-Arduino 2.5.1.
  The working board configuration uses the vendor SH8601 display path.
- The device advertises the RunDeck BLE GATT service and Android can discover,
  connect, disconnect, and reconnect to it.
- The Android companion runs on API 36. It has BLE setup, a Run Setup screen,
  a user-started foreground location service, and a persistent active-run
  notification.
- On 2026-07-30, a real walk verified Android GPS pace, distance, and elapsed
  time agree with the values delivered to the RunDeck display.
- The selected V1 Long Run target is 8:50–9:20 /mi. Android derives
  `ON TARGET`, `EASE OFF`, `PICK IT UP`, or `GPS WEAK` and sends that state to
  the display. Active-run distance, elapsed time, and pace are checkpointed
  locally with DataStore; stopping the run clears the checkpoint.
- A full 16 MB pre-RunDeck recovery image exists locally at
  `firmware/backups/factory-before-rundeck.bin` and is intentionally ignored by
  Git.

## Current limitations (do not misrepresent as complete)

- The Android app is still a compact single-module prototype. Hilt, Room,
  feature modules, full preset editing, and active-run recovery/resume UI are
  not implemented.
- The device currently receives live metrics only. The versioned run-state,
  media, notification, settings, and heartbeat characteristics exist but their
  full CBOR/acknowledgement contracts are not implemented end-to-end.
- The heart-rate display is simulated/unavailable in live phone-GPS runs.
  Garmin HRM-Dual direct mode, concurrent central/peripheral soak testing, and
  phone-forwarded HR remain future work.
- Media control, notification allowlisting/dismissal, Open-Meteo, touch lock,
  brightness/power work, and real-run outdoor validation remain future work.
- The scripted flash helper deliberately refuses to choose between multiple
  serial ports. When the phone is also attached, identify the Espressif USB
  JTAG port via `udevadm` and use that explicit port.

## Next implementation order

1. Finish V1 run-state protocol: CBOR preset/run-state characteristic,
   acknowledgement/event handling, and resume/discard checkpoint UI.
2. Add phone-forwarded HR and target/combined pace-HR status rules, then
   evaluate direct Garmin HRM-Dual only behind the BLE-concurrency soak gate.
3. Add MediaSession metadata/actions and the Music display screen bridge.
4. Add notification allowlist, sanitization, modal payloads, and permitted
   dismissal acknowledgements.
5. Add weather freshness, settings, touch lock/brightness, resilience tests,
   and outdoor/power validation.
