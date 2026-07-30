# Implementation status

## Implemented now

- Git workspace connected to authenticated HTTPS `origin/main`.
- Monorepo baseline, hardware-validation procedure, BLE wire contract, and test
  plan.
- Standalone UI domain model and simulated data source; native-resolution LVGL
  dashboard composition is ready to bind to the validated board adapter.
- Android Compose project foundation and unit-tested pure pace calculator.

## Hardware-gated next work

- Copy/adapt the vendor panel and touch driver only after the untouched vendor
  demo is flashed on the actual device. This is required because the vendor
  archive currently calls the panel SH8601 while the original specification
  calls it RM690B0.
- Implement and test the real LVGL QSPI/touch adapter, swipe events, BLE GATT
  server/client, HR client, Android BLE client, foreground service, and system
  integrations on physical devices.
