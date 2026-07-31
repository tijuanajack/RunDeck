# RunDeck BLE protocol v1

Primary service UUID: `7b2e0000-6d1f-4a91-8a5f-6c796a25a000`. Characteristics
replace the first group with `7b2e0001` through `7b2e0007` respectively.

| Suffix | Direction | Encoding | Cadence |
| --- | --- | --- | --- |
| `0001` live metrics | Android → ESP32 | binary | 1 Hz |
| `0002` run state/preset | Android → ESP32 | CBOR | change |
| `0003` media | Android → ESP32 | CBOR | event |
| `0004` notification | Android → ESP32 | CBOR fragments | event |
| `0005` device event | ESP32 → Android | binary + ack | event |
| `0006` configuration | bidirectional | CBOR | change |
| `0007` heartbeat/clock | bidirectional | binary | 10 s |

All binary frames use little-endian integers and begin with `version:u8, type:u8, sequence:u16,
source_monotonic_ms:u32, payload_length:u16, reserved:u16`. The reserved field
is zero in V1, making the fixed header 12 bytes. Receivers reject versions other
than `1`, lengths beyond the characteristic limit, non-monotonic sequences,
and values older than the message-specific freshness window.

`LiveMetrics` payload on `0001` is `flags:u16, pace_seconds_per_mile:u16,
distance_centimeters:u32, elapsed_seconds:u32, moving_seconds:u32,
speed_centimeters_per_second:u16, temperature_deci_f:i16,
forwarded_hr_bpm:u8`. Flags declare which values are valid and whether GPS,
phone, HR, and weather are connected/stale.

`RunState` payload on `0002` is a bounded CBOR map with unsigned-integer keys.
Firmware currently accepts only this fixed v1 map and rejects unknown keys,
oversized payloads, replayed sequences, incompatible versions, out-of-range
pace/HR bounds, and non-ASCII-overlong labels. Text that can appear on the
ESP32 display should stay ASCII until the loaded LVGL fonts are expanded.

| Key | Field | Type | Notes |
| --- | --- | --- | --- |
| `0` | `version` | uint | Must be `1`. |
| `1` | `sequence` | uint | `0..65535`, monotonic per run-state stream. |
| `2` | `active` | bool | Android-owned run active/inactive state. |
| `3` | `presetName` | text | Max 20 UTF-8 bytes; V1 sends `LONG RUN`. |
| `4` | `targetLabel` | text | Max 28 UTF-8 bytes; V1 sends `8:50-9:20`. |
| `5` | `paceLowSecondsPerMile` | uint | Inclusive lower pace target. |
| `6` | `paceHighSecondsPerMile` | uint | Inclusive upper pace target. |
| `7` | `hrLowBpm` | uint | Optional HR lower bound. |
| `8` | `hrHighBpm` | uint | Optional HR upper/ceiling bound. |

`MediaState` payload on `0003` is a bounded CBOR map with unsigned-integer
keys. Android sources it from active `MediaSession` controllers after the user
enables RunDeck's notification-listener/media access. Text is sanitized to
printable ASCII before encoding and firmware rejects oversized, incompatible,
unknown-key, or replayed packets.

| Key | Field | Type | Notes |
| --- | --- | --- | --- |
| `0` | `version` | uint | Must be `1`. |
| `1` | `sequence` | uint | `0..65535`, monotonic per media stream. |
| `2` | `available` | bool | True when Android found active media metadata. |
| `3` | `playing` | bool | True when the selected MediaSession is playing. |
| `4` | `source` | text | Max 16 bytes; package-derived display source. |
| `5` | `title` | text | Max 40 bytes. |
| `6` | `artist` | text | Max 32 bytes. |

The first `0006` context packet is a six-entry CBOR map carrying the
phone-owned clock and Open-Meteo temperature. It is sent on connect and every
30 seconds; weather refreshes are limited to once per 10 minutes. Weather state
uses `0=connected`, `1=stale`, `2=unavailable`, `3=error`. The ESP32 rejects
old or malformed context packets and shows `TEMP STALE`/`TEMP --` instead of
presenting an old reading as live.

| Key | Field | Type | Notes |
| --- | --- | --- | --- |
| `0` | `version` | uint | Must be `1`. |
| `1` | `sequence` | uint | `0..65535`, monotonic context stream. |
| `2` | `clock` | text | Printable `h:mm AM/PM`, max 8 bytes. |
| `3` | `weatherState` | uint | `0..3`, as defined above. |
| `4` | `temperatureAvailable` | bool | False when weather is unavailable. |
| `5` | `temperatureOffset` | uint | Fahrenheit plus 100, bounded by firmware. |

Low-frequency CBOR payloads are bounded per characteristic. Notification
payloads are limited to 192 bytes total and are sent as ordered 20-byte
fragment frames (9-byte fragment header plus an 11-byte chunk); firmware
reassembles one message at a time and expires incomplete messages after five
seconds.

Current `Notification` payloads on `0004` are the first unfragmented V1 slice:
a bounded CBOR map with `version`, `sequence`, `app`, `title`, and `body`.
Android only forwards clearable, message-style notifications from likely
messaging apps, sanitizes to printable ASCII, truncates app/title/body to
16/32/96 bytes, suppresses exact duplicates for 90 seconds, and allows
distinct overlays roughly every 8 seconds.
Firmware displays accepted notifications as a short-lived modal overlay and
does not persist the content.

Important Android-to-device commands (run state, dismissal, settings write)
carry a command ID and produce one `0005` acknowledgement containing that ID
and a success/error code. No notification content is persisted on the device.

`DeviceEvent` payloads on `0005` are compact fixed-width 8-byte events.

ACK event:

`version:u8, event_type:u8, acknowledged_sequence:u16, command_type:u8,
status:u8, reserved:u16`

Device-origin media-control event:

`version:u8, event_type:u8, sequence:u16, action:u8, reserved:u8, reserved:u16`

Current values:

| Field | Value | Meaning |
| --- | --- | --- |
| `version` | `1` | Protocol version. |
| `event_type` | `0x51` | ACK event. |
| `event_type` | `0x52` | Device-origin media-control event. |
| `command_type` | `2` | Run-state/preset packet on `0002`. |
| `status` | `0` | Accepted. |
| `action` | `1` | Previous track. |
| `action` | `2` | Play/pause toggle. |
| `action` | `3` | Next track. |

Run-control actions on `event_type=0x53` are `4=Pause`, `5=Resume`, `6=Stop`,
and `7=Start`. A start event asks Android to bring the app visibly forward and
start its location foreground service; the device enters Dashboard only after
Android publishes active run state.

Device-origin run-control event (`event_type=0x53`) uses the same 8-byte event
shape as media controls. Android dispatches accepted actions to the foreground
run service; the device changes pages only after the corresponding
Android-owned run-state packet is received.

Device-origin notification-dismiss event (`event_type=0x54`) uses the same
8-byte shape, with `sequence` set to the Android notification sequence and all
remaining fields reserved zero. Android maps that bounded sequence to the
in-memory clearable notification key and calls `cancelNotification` only while
the user-enabled notification listener is connected; no notification content
or key is persisted.

In the current Android prototype, Android reads `0005` after a run-state write
to retrieve the run-state ACK, and also subscribes to `0005` notifications for
RunDeck Music-screen previous/play-pause/next taps. CCCD subscription and all
reads/writes are serialized through the Android GATT operation queue.
