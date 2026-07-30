# Test plan

## Automated

- Protocol codec: valid packet, unsupported version, bad length, replayed
  sequence, stale timestamp, and fragmented notification reassembly.
- Run domain: GPS accuracy/outlier rejection, 10-second weighted pace, moving
  time, stationary behavior, and pace/HR combined statuses.
- Android: permission state reducers, persisted active-run recovery, and BLE
  reconnect state transitions.

## Bench

Verify cold boot, screen/touch, USB reconnect, phone/app restart, malformed
BLE writes, HR strap loss/recovery, and data-stale presentation. Run a BLE
central+peripheral soak while rendering at the intended cadence.

## Outdoor

Test readability in direct sun, mount angle, sweat/accidental touch behavior,
touch lock, GPS weak areas, headphones/media coexistence, HR continuity, and
measured power before publishing battery-life claims.
