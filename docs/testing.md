# V1 verification

## Automated

- Android unit coverage passes for protocol codecs, duplicate-key rejection,
  notification fragmentation, pace smoothing/sample rejection, target and
  combined HR status, heart-rate measurement parsing, and reconnect state.
- The debug APK builds with `testDebugUnitTest assembleDebug`.
- Firmware compiles for the pinned Waveshare FQBN and the shipped image was
  hash-verified after upload.

## Bench

The verified bench path covers cold boot/recovery, display/touch, USB
reconnect, phone/app reconnect, malformed BLE writes, stale-state rendering,
media controls, messaging overlays, brightness, and device-origin run start.
Direct-device HR central/peripheral soak and a calibrated battery ADC test are
deferred; the direct HR firmware gate remains disabled.

## Outdoor

Operational validation remains: direct-sun readability, mount angle,
sweat/accidental touch behavior, touch lock, GPS weak areas,
headphones/media coexistence, screen-lock longevity, optional HR continuity,
and measured power before publishing battery-life claims.
