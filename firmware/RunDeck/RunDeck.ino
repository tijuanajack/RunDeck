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

// Board initialization deliberately remains a narrow adapter. Keep it aligned
// with the current Waveshare V2 LVGL reset sequence: no direct LCD reset GPIO,
// OLED reset through TCA9554 EXIO0, and touch reset through EXIO1.
// See AGENTS.md and docs/status.md before changing hardware startup.
namespace {
rundeck::SimulatedData simulator;
rundeck::Dashboard dashboard;
rundeck::RunDeckBle ble;
uint32_t lastRenderMs = 0;
}

void setup() {
  Serial.begin(115200);
  delay(250);  // cold USB power needs a stable panel supply before QSPI setup
  Serial.println("RunDeck UI booting");
  if (!rundeck::beginWaveshareBoard()) {
    // Do not leave a black, apparently bricked display after a transient cold
    // start failure. Report it on serial and retry a full initialization.
    Serial.println("RunDeck board initialization failed; retrying");
    delay(1500);
    ESP.restart();
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
    if (!ble.applyLiveMetrics(&state, now)) {
      // A lost phone must never look like a credible live run. Keep the
      // dashboard legible while making every live metric visibly unavailable.
      state.phone = {rundeck::SourceState::Unavailable, now};
      state.gps = {rundeck::SourceState::Unavailable, now};
      state.heartRate = {rundeck::SourceState::Unavailable, now};
      state.paceMinutesPerMile = 0.0f;
      state.distanceMiles = 0.0f;
      state.elapsedSeconds = 0;
      state.heartRateBpm = 0;
      state.statusText = "PHONE OFFLINE";
    }
    dashboard.render(state);
    Serial.printf("pace=%.2f hr=%u status=%s\n", state.paceMinutesPerMile,
                  state.heartRateBpm, state.statusText);
  }
  delay(5);
}
