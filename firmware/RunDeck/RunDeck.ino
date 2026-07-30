#include <Arduino.h>
#include <lvgl.h>

#include "app/simulated_data.h"
#include "ui/dashboard.h"

// Board initialization deliberately remains a narrow adapter until the exact
// delivered display is validated with the unmodified Waveshare LVGL test.
// See firmware/README.md and docs/hardware-validation.md.
namespace {
rundeck::SimulatedData simulator;
rundeck::Dashboard dashboard;
uint32_t lastRenderMs = 0;
}

void setup() {
  Serial.begin(115200);
  Serial.println("RunDeck simulated UI booting");
  // TODO(board): initialize SH8601 QSPI panel, FT5x06 touch, LVGL draw buffers,
  // tick timer, and LVGL task from the validated Waveshare demo adapter.
  lv_init();
  dashboard.begin();
}

void loop() {
  const uint32_t now = millis();
  lv_timer_handler();
  if (now - lastRenderMs >= 1000) {
    lastRenderMs = now;
    const auto state = simulator.next(now);
    dashboard.render(state);
    Serial.printf("pace=%.2f hr=%u status=%s\n", state.paceMinutesPerMile,
                  state.heartRateBpm, state.statusText);
  }
  delay(5);
}
