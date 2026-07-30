# Architecture

Android owns run timing, GPS, pace, distance, weather, presets, media, and
notifications. The ESP32 owns display/touch state and, in direct mode, the HR
strap connection. The device keeps only reconnection and display preferences.

`RunRepository` feeds a foreground-service state flow. `BleDeviceRepository`
maps that state to the versioned RunDeck protocol. Firmware decodes it into an
immutable `DisplayState`; each producer includes a freshness timestamp and
state. Views render that state and never infer that stale data is live.

## BLE roles

The ESP32 advertises the RunDeck peripheral service for Android and, in
`DirectDeviceHr` mode, operates as a BLE central against the standard HR
service. This is behind an explicit reliability gate: a multi-hour concurrent
reconnect/UI soak test must pass before direct mode becomes the default. The
same display contract supports `PhoneForwardedHr` and `PhoneOnly` fallback
modes.

The HR client scans for `0x180D`, subscribes to `0x2A37`, records freshness per
measurement, and transitions to `STRAP LOST` after the configured timeout.
