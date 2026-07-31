#pragma once

#include <stdint.h>
#include <stddef.h>

namespace rundeck {

/**
 * Optional Garmin/standard HR central role. Disabled until the
 * central/peripheral soak gate is explicitly passed.
 */
class DirectHrClient {
 public:
  void begin();
  void setEnabled(bool enabled);
  void tick(uint32_t nowMs);
  bool fresh(uint32_t nowMs) const;
  uint16_t bpm() const { return bpm_; }

  // Callback entry points are public so NimBLE's callback adapters can remain
  // local to the implementation without exposing them as part of the UI API.
  void onAdvertised(const void* device);
  void onConnected(void* client);
  void onDisconnected();
  void onMeasurement(const uint8_t* data, size_t length);

 private:

  bool enabled_ = false;
  bool scanning_ = false;
  uint32_t lastAttemptMs_ = 0;
  uint32_t lastMeasurementMs_ = 0;
  uint16_t bpm_ = 0;
  void* client_ = nullptr;
};

}  // namespace rundeck
