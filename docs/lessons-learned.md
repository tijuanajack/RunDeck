# RunDeck integration lessons learned

**Status:** V1 tabled after a successful 10-mile field run.

**Purpose:** A reusable integration guide for future projects using the
Waveshare RunDeck device, its firmware, or the Android companion. This is a
record of what was verified, what failed, and what should be treated as a
future gate.

## The short version

RunDeck is most reliable when the Android phone is the authority and the
ESP32-S3 is a display and control surface. Android owns run timing, GPS,
pace, distance, targets, presets, weather, media, notifications, and
configuration. The device accepts fresh, validated state over BLE and renders
it; it does not maintain a second run history.

The two highest-risk areas are hardware bring-up and Android/BLE lifecycle
behavior. A successful compile or upload is not proof that the panel will
cold-boot, and a successful BLE connection is not proof that a queued GATT
operation is safe to overlap with another one. Start every successor project
with the recovery image, the exact board configuration, serialized GATT
operations, and a physical cold-boot test.

## 1. Hardware identity and configuration

The tested board is the Waveshare **ESP32-S3-Touch-AMOLED-2.41-B**, with a
600 × 450 landscape AMOLED panel. The working display path is the Waveshare
SH8601 QSPI implementation. Older planning notes that name RM690B0 or a
direct LCD reset GPIO do not describe the working configuration.

Use these as the starting configuration:

| Item | Known-good value or rule |
| --- | --- |
| Arduino FQBN | `esp32:esp32:waveshare_esp32_s3_touch_amoled_241` |
| ESP32 Arduino core | 3.0.7 |
| LVGL | 8.4.0 |
| NimBLE-Arduino | 2.5.1 |
| Panel | SH8601 QSPI, native 600 × 450 landscape |
| Shared I2C bus | GPIO47/GPIO48, initialized once |
| OLED reset | TCA9554 EXIO0 pulse before panel init |
| Touch reset | TCA9554 EXIO1 pulse before FT5x06 touch init |
| Direct LCD reset | Disabled (`GPIO_NUM_NC` / `-1`) |
| Battery measurement | GPIO17 with GPIO16 battery-enable; provisional |

The reset detail is critical. The V2 Waveshare example resets the OLED through
the TCA9554 expander. Earlier RunDeck code and stale examples used a direct
GPIO21 LCD reset path; that image could upload and then remain black after a
real USB unplug/replug. Do not change panel pins, reset ownership, power
sequencing, or the shared I2C initialization without repeating the hardware
gate.

The board's USB port is session-dependent. When the phone is also connected,
there may be multiple `/dev/ttyACM*` devices. Identify the Espressif USB JTAG
device with `udevadm` and verify its vendor/model before compiling or flashing.
Never assume that `/dev/ttyACM0` is the board. A known-good USB data cable is
part of the test setup: one failing cable repeatedly interrupted full-image
writes around 14%, while a replacement cable restored reliable flashing.

The battery percentage is not calibrated. Record USB-only and installed-LiPo
ADC readings before making battery-life or percentage claims.

## 2. Recovery and flash discipline

Keep a local, ignored copy of the pre-RunDeck factory image at
`firmware/backups/factory-before-rundeck.bin`. It is recovery material, not a
Git artifact. Do not delete, overwrite, or commit it.

Use the recovery gate in [recovery.md](recovery.md) whenever a panel is black
or a display/reset/power change is being considered:

1. Disconnect USB power for about five seconds.
2. Identify the Espressif port explicitly.
3. Restore the factory image with the ROM-loader procedure if needed.
4. Confirm display and touch, including one physical unplug/replug cold boot.
5. Flash only the known-good RunDeck image.
6. After hardware changes, require three true unplug/replug boots before
   expanding features.

An upload hash only proves that bytes reached flash. It does not prove that
the panel reset, power rail, bootloader, partition table, or touch controller
will work after USB power is removed. BOOT is reserved for recovery. Do not
make PWR the only pause/stop or other safety-critical control.

The approved ESP-IDF baseline in `firmware/esp-idf-baseline/` is a useful
display-only cold-boot gate (ESP-IDF 5.5.2, `esp_lcd_sh8601` 2.0.1~1, LVGL
8.3.11). It is not a drop-in replacement for full RunDeck firmware and should
not be flashed casually.

## 3. Reproducible toolchain and repository practice

Pin the board package and libraries before adding application features. Build
with Arduino CLI rather than relying on an IDE's implicit library versions.
The normal helper intentionally refuses to guess when more than one serial
device is present; preserve that behavior.

Keep the repository organized by responsibility:

- `firmware/RunDeck/` is the composition root plus board, BLE, HR, state, and
  UI modules.
- `android/` contains the Kotlin/Compose companion.
- `protocol/` is the wire contract and test vectors.
- `docs/` contains recovery, validation, architecture, and operational notes.

Do not commit APKs, firmware binaries, vendor archives, device identifiers,
notification contents, or API keys. Make small commits, run the applicable
build/tests, inspect the diff, and push only the requested changes to `main`.

The repeatable local build entry points are:

```sh
# Firmware compile (from the repository root)
/home/tjhurt/.local/bin/arduino-cli compile \
  --fqbn esp32:esp32:waveshare_esp32_s3_touch_amoled_241 firmware/RunDeck

# Android unit tests and debug APK (from the repository root)
cd android
env PATH=/home/tjhurt/.local/rundeck-toolchain/jdk-17/bin:$PATH \
  ./gradlew --no-daemon testDebugUnitTest assembleDebug --console=plain
```

Flash only after the board port has been identified and the recovery gate has
been satisfied. The repository helper requires
`RUNDECK_HARDWARE_VALIDATED=1` and exactly one serial device by design; an
explicit upload should still include the verified port rather than copying a
port number from an earlier session. Install the Android debug APK with the
local platform-tools `adb`, then force-stop and relaunch `com.rundeck.app`
before checking logcat for a fatal exception.

## 4. Firmware and display-state design

The immutable `DisplayState` model was more valuable than view-specific
variables. Every source has an explicit `connected`, `stale`, `unavailable`,
or `error` state and a freshness timestamp. Old values are never presented as
live. This prevented the device from implying that a disconnected HR strap,
weather source, or media session was still current.

The final device flow is:

1. Branded splash with a clear “CONNECT TO RUNDECK APP” instruction.
2. Fresh phone packet arrives; transition to `READY TO RUN`.
3. Ready can swipe through Music, Stats, and device-local brightness.
4. Start from the phone or RunDeck; the active dashboard shows pace,
   distance, elapsed time, HR/status, weather, media, and connection state.
5. Stop returns the device to Ready.

The black/lime/cyan AMOLED treatment, large numerals, text status labels,
touch-lock gesture, and idle display timeout are now part of the working UI
contract. Burn-in mitigation, brightness calibration, and long-duration wake
behavior still require operational testing; do not describe them as measured
hardware guarantees.

Brand artwork should be treated as two deliverables: a full-resolution Android
header/launcher asset and a deliberately reduced, flash-safe RGB565 firmware
splash. Waiting for a fresh phone packet before leaving the splash avoids a
misleading “ready” screen when the BLE link is absent. Keep the connection
instruction on the splash itself; it is the only screen a user may see after a
fresh boot.

## 5. Android ownership and lifecycle

The phone-side `RunTrackingService` and `RunSession` own the run clock and
GPS-derived values. The service publishes elapsed and moving-time state every
second, even when GPS callbacks pause or the screen is locked. A partial wake
lock is held only for an active/resumed run. Checkpoints are local and small;
there is no cloud sync or duplicate full history on the device.

Use the phone's active MediaSession rather than a Spotify-specific API. Use a
replaceable weather provider (Open-Meteo was sufficient for V1), cache the
result, refresh no more than every ten minutes while running, and expose
stale/unavailable state.

Ask permissions at the action that needs them: Nearby Devices during device
setup, then precise location, notifications, and location foreground-service
access when the user starts tracking. Do not grant permissions through ADB as
a substitute for the real flow.

Android 16 exposed an important lifecycle rule: a BLE callback cannot safely
start the location foreground service while the Activity is backgrounded. A
device-origin START must first bring the Activity visibly forward; only then
should the Activity start the service. Catch a rejected start and report it in
the UI instead of allowing a fatal exception to crash the app.

Samsung battery optimization is user-controlled. Offer an explicit opt-in
exemption flow and re-check it when the user returns from Settings; never
silently modify that setting.

## 6. BLE and protocol lessons

The primary service is versioned as
`7b2e0000-6d1f-4a91-8a5f-6c796a25a000`. Characteristics `0001` through `0007`
carry live metrics, run state, media, notifications, device events, context/
settings, and heartbeat/time sync respectively. Preserve the established
wire contract in [ble-protocol.md](../protocol/ble-protocol.md):

- 12-byte little-endian header: version, type, sequence, monotonic source
  timestamp, payload length, and reserved field.
- Validate version, size, sequence/timestamp monotonicity, freshness, and
  replay before applying state.
- Live metrics are 33 bytes total. Pace is **seconds per mile**, not
  centiseconds; the old field overflowed at walking paces and made the phone
  and device disagree.
- CBOR maps are bounded integer-key maps. Reject duplicate keys, unknown or
  incompatible fields, malformed data, oversized payloads, and stale packets.
- Notification payloads are sanitized, bounded, and fragmented with a timeout;
  only one assembly is active at a time.

The most important Android BLE rule is operational: serialize every GATT
operation, including service discovery, descriptor writes, reads, and
notifications, through one queue. A Samsung device produced `prior command is
not finished` when CCCD subscription overlapped a protocol write. Read-after-
write ACKs were a dependable fallback while notification setup was being
hardened. Reconnect by closing the stale GATT session and using bounded,
backed-off retries; do not require a fresh manual scan after every short link
loss.

The ESP32-S3 can support central and peripheral roles in principle, but the
first direct Garmin HRM-Dual soak disrupted RunDeck discovery. The V1 choice
was to keep direct-device HR disabled and use phone-forwarded HR as the
fallback. Treat multi-role support as a measured concurrency gate, not a
capability claim.

## 7. Media, notifications, HR, and context

Media metadata must be copied into persistent firmware buffers before LVGL
uses it. Temporary decoded CBOR/stack buffers caused random characters under
song titles. Long titles use LVGL circular scrolling; stale timestamps should
keep the last known title/artist rather than replacing useful text with
`MEDIA STALE`. Device Music controls send compact events back to Android,
which dispatches them to the active MediaSession.

Notification forwarding is deliberately narrow: a user-enabled
`NotificationListenerService`, clearable message-style notifications,
sanitized/truncated app/title/body, duplicate suppression, an app/contact
allowlist, bounded fragments, and in-memory dismissal routing. Never persist
notification content or keys, and never dismiss a source that was not
forwarded as clearable.

HR ownership is explicit: `PhoneForwardedHr`, `PhoneOnly`, or the reserved
`DirectDeviceHr`. The standard Heart Rate Service (`0x180D`) and Measurement
characteristic (`0x2A37`) are parsed with bounds checks for both 8-bit and
16-bit values. A source that is absent or stale must clear BPM and render
`--`/`GARMIN STRAP OFF`; never synthesize a plausible reading.

## 8. Failure modes worth carrying forward

| Symptom | Root cause | Durable fix or lesson |
| --- | --- | --- |
| Black panel after unplug/replug | Direct GPIO21 LCD reset / stale board example | Use V2 TCA9554 EXIO0 reset and require physical cold-boot gates |
| Flash stops around 14% | Bad USB data cable | Replace cable; verify the board port before retrying |
| Wrong target or song characters | LVGL pointed at temporary decoded storage | Copy wire data into persistent buffers |
| Walking pace disagrees with phone | Pace sent as centiseconds in a 16-bit field | Send seconds per mile and preserve units in the protocol |
| `prior command is not finished` | Overlapping Samsung GATT operations | One serialized queue for all GATT work |
| App crash on device START | Android 16 foreground-service launch restriction | Bring Activity to foreground, then start service; catch rejection |
| Device stops discovering during HR test | Unproven ESP32 central/peripheral scheduling | Keep direct HR gated; use phone-forwarded HR |
| Screen-lock stream appears frozen | Updates depended on GPS/screen callbacks | Active-run wake lock plus one-second service ticker |
| Phone and board appear connected but state is old | No explicit freshness/source state | Carry source status and timestamps through every layer |

## 9. Suggested next-project startup checklist

Before writing feature code:

- Confirm the exact Waveshare board revision and obtain the V2 example that
  uses SH8601 plus TCA9554 EXIO reset.
- Preserve a factory image and write down the recovery sequence.
- Install the pinned Arduino/ESP32/LVGL/NimBLE versions and verify a clean
  compile on a clean machine.
- Identify the Espressif USB port with the phone disconnected, then repeat
  with both devices attached.
- Run a display-only firmware and perform three real USB cold boots.
- Copy the existing protocol UUIDs, header rules, field units, limits, and
  test vectors before adding a new characteristic.
- Implement the Android GATT operation queue and bounded reconnect before
  adding media, notifications, or HR.
- Keep Android authoritative; define freshness and unavailable states before
  drawing a value on the device.
- Exercise permission/setup flows on the target Android version, including a
  device-origin START while the app is backgrounded.
- Test phone-forwarded HR first. Do not enable direct HR until a concurrent
  central/peripheral soak proves discovery, reconnection, throughput, and UI
  responsiveness together.
- Run a short walk, a screen-lock run, a BLE disconnect/reconnect, a phone
  restart, a device restart, and a clean stop/return-to-Ready cycle before
  calling the integration usable.

## 10. Deliberately deferred from V1

These are not missing fixes in the delivered prototype: direct Garmin HRM-Dual
concurrency, Hilt/Room/feature-module reorganization, full preset editing,
production-grade checkpoint recovery, cloud sync, maps, ski mode, Bowline or
Garmin cloud integrations, iOS, HRV, structured workouts, voice reply, and
production battery-life claims. Re-evaluate them only after repeating the
hardware cold-boot and BLE reliability gates above.

For the current verified implementation, see [status.md](status.md),
[architecture.md](architecture.md), [hardware-validation.md](hardware-validation.md),
and [testing.md](testing.md).
