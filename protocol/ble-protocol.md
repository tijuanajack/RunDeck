# RunDeck BLE protocol v1

Primary service UUID: `7b2e0000-6d1f-4a91-8a5f-6c796a25a000`. Characteristics
replace the first group with `7b2e0001` through `7b2e0007` respectively.

| Suffix | Direction | Encoding | Cadence |
| --- | --- | --- |
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

Low-frequency CBOR payloads have a 768-byte total limit. Notification text is
sanitized by Android, truncated to 240 UTF-8 bytes, and fragmented with
`messageId`, `fragmentIndex`, and `fragmentCount`; firmware permits at most
four outstanding fragments and expires incomplete messages after 10 seconds.

Important commands (run state, media command, dismissal, settings write) carry
a command ID and produce one `0005` acknowledgement containing that ID and a
success/error code. No notification content is persisted on the device.

`DeviceEvent` payload on `0005` starts with a compact fixed-width ACK event for
the run-state/preset slice:

`version:u8, event_type:u8, acknowledged_sequence:u16, command_type:u8,
status:u8, reserved:u16`

Current values:

| Field | Value | Meaning |
| --- | --- | --- |
| `version` | `1` | Protocol version. |
| `event_type` | `0x51` | ACK event. |
| `command_type` | `2` | Run-state/preset packet on `0002`. |
| `status` | `0` | Accepted. |

In the current Android prototype, Android reads `0005` after the run-state
write completes. Firmware stores this ACK after accepting and storing a
non-replayed run-state packet. The characteristic remains notify-capable for a
later queued-GATT event stream, but V1 does not rely on CCCD subscription yet.
