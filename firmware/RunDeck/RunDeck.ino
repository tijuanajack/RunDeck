#define LV_CONF_INCLUDE_SIMPLE
#include <Arduino.h>
#include <lvgl.h>

#include "app/simulated_data.h"
#include "ble/rundeck_ble.h"
#include "board/waveshare_board.h"
#include "ui/dashboard.h"
#include "hr/direct_hr.h"

// Arduino CLI does not compile sketch subdirectories. Keep the implementation
// modular on disk, then include it once at the composition root.
#include "app/simulated_data.cpp"
#include "ble/rundeck_ble.cpp"
#include "ui/brand_splash.cpp"
#include "ui/dashboard.cpp"
#include "hr/direct_hr.cpp"
#include "board/waveshare_board.cpp"

// Board initialization deliberately remains a narrow adapter. Keep it aligned
// with the current Waveshare V2 LVGL reset sequence: no direct LCD reset GPIO,
// OLED reset through TCA9554 EXIO0, and touch reset through EXIO1.
// See AGENTS.md and docs/status.md before changing hardware startup.
namespace {
constexpr bool kDirectHrSoakEnabled = false;
rundeck::SimulatedData simulator;
rundeck::Dashboard dashboard;
rundeck::RunDeckBle ble;
rundeck::DirectHrClient directHr;
uint32_t lastRenderMs = 0;
uint32_t lastActivityMs = 0;
constexpr uint32_t kIdleDisplayTimeoutMs = 60'000;
bool displaySleeping = false;

void onMediaControl(rundeck::MediaControlAction action, void*) {
  ble.notifyMediaControl(action);
}

void onRunControl(rundeck::RunControlAction action, void*) {
  ble.notifyRunControl(action);
}

void onNotificationDismiss(uint16_t sequence, void*) {
  ble.notifyNotificationDismissed(sequence);
}
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
  dashboard.setMediaControlHandler(onMediaControl, nullptr);
  dashboard.setRunControlHandler(onRunControl, nullptr);
  dashboard.setNotificationDismissHandler(onNotificationDismiss, nullptr);
  ble.begin();
  directHr.begin();
  directHr.setEnabled(kDirectHrSoakEnabled);
  lastActivityMs = millis();
}

void loop() {
  const uint32_t now = millis();
  directHr.tick(now);
  if (rundeck::consumeTouchActivity()) {
    lastActivityMs = now;
    if (displaySleeping) {
      rundeck::setDisplayAwake(true);
      displaySleeping = false;
    }
  }
  rundeck::applyPendingDisplayBrightness();
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
    if (kDirectHrSoakEnabled && directHr.fresh(now)) {
      state.heartRate = {rundeck::SourceState::Connected, now};
      state.heartRateBpm = directHr.bpm();
      state.metricFlags &= static_cast<uint16_t>(~0x00C0);
      if (state.heartRateBpm > 150) state.metricFlags |= 0x0080;
      else if (state.heartRateBpm < 135) state.metricFlags |= 0x0040;
    }
    ble.applyRunState(&state, now);
    ble.applyMediaState(&state, now);
    ble.applyDisplayContext(&state, now);
    ble.applyBatteryState(&state, now);
    ble.applyNotificationState(&state, now);
    if (state.runActive) {
      lastActivityMs = now;
      if (displaySleeping) {
        rundeck::setDisplayAwake(true);
        displaySleeping = false;
      }
    } else if (!displaySleeping && now - lastActivityMs >= kIdleDisplayTimeoutMs) {
      displaySleeping = rundeck::setDisplayAwake(false);
    }
    // The phone sends run-state/media/context during protocol setup even
    // before a run starts. That is enough to leave the branded boot screen;
    // live metrics are still freshness-gated independently below.
    if (ble.phoneSessionSeen()) dashboard.onPhoneConnected();
    dashboard.render(state);
    Serial.printf("pace=%.2f hr=%u status=%s\n", state.paceMinutesPerMile,
                  state.heartRateBpm, state.statusText);
  }
  delay(5);
}
