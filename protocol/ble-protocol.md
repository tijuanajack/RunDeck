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

`LiveMetrics` payload is `flags:u16, pace_centiseconds_per_mile:u16,
distance_centimeters:u32, elapsed_seconds:u32, moving_seconds:u32,
speed_centimeters_per_second:u16, temperature_deci_f:i16,
forwarded_hr_bpm:u8`. Flags declare which values are valid and whether GPS,
phone, HR, and weather are connected/stale.

Low-frequency CBOR payloads have a 768-byte total limit. Notification text is
sanitized by Android, truncated to 240 UTF-8 bytes, and fragmented with
`messageId`, `fragmentIndex`, and `fragmentCount`; firmware permits at most
four outstanding fragments and expires incomplete messages after 10 seconds.

Important commands (run state, media command, dismissal, settings write) carry
a command ID and produce one `0005` acknowledgement containing that ID and a
success/error code. No notification content is persisted on the device.
