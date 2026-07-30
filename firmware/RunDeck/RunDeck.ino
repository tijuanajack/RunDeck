#define LV_CONF_INCLUDE_SIMPLE
#include <Arduino.h>
#include <lvgl.h>

#include "app/simulated_data.h"
#include "ble/rundeck_ble.h"
#include "board/waveshare_board.h"
#include "ui/dashboard.h"

// Arduino CLI does not compile sketch subdirectories. Keep the implementation
// modular on disk, then include it once at the composition root.
#include "app/simulated_data.cpp"
#include "ble/rundeck_ble.cpp"
#include "ui/dashboard.cpp"
#include "board/waveshare_board.cpp"

// Board initialization deliberately remains a narrow adapter until the exact
// delivered display is validated with the unmodified Waveshare LVGL test.
// See firmware/README.md and docs/hardware-validation.md.
namespace {
rundeck::SimulatedData simulator;
rundeck::Dashboard dashboard;
rundeck::RunDeckBle ble;
uint32_t lastRenderMs = 0;
}

void setup() {
  Serial.begin(115200);
  Serial.println("RunDeck simulated UI booting");
  if (!rundeck::beginWaveshareBoard()) {
    Serial.println("RunDeck board initialization failed");
    while (true) delay(1000);
  }
  dashboard.begin();
  ble.begin();
}

void loop() {
  const uint32_t now = millis();
  lv_timer_handler();
  if (now - lastRenderMs >= 1000) {
    lastRenderMs = now;
    auto state = simulator.next(now);
    ble.applyLiveMetrics(&state, now);
    dashboard.render(state);
    Serial.printf("pace=%.2f hr=%u status=%s\n", state.paceMinutesPerMile,
                  state.heartRateBpm, state.statusText);
  }
  delay(5);
}
