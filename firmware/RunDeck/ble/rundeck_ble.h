#pragma once

#include <stdint.h>

#include "../app/display_state.h"
#include "../ui/dashboard.h"

namespace rundeck {

/** RunDeck's v1 BLE peripheral service. Android owns the metric sources. */
class RunDeckBle {
 public:
  void begin();
  /** Applies the last accepted Android-owned preset/target config, if present. */
  void applyRunState(DisplayState* state, uint32_t nowMs);
  /** Applies the last accepted Android MediaSession metadata, if present. */
  void applyMediaState(DisplayState* state, uint32_t nowMs);
  /** Applies the last short-lived Android notification overlay, if present. */
  void applyNotificationState(DisplayState* state, uint32_t nowMs);
  /** Applies phone-owned time/weather context with explicit freshness. */
  void applyDisplayContext(DisplayState* state, uint32_t nowMs);
  /** Reads the board's provisional battery ADC value. */
  void applyBatteryState(DisplayState* state, uint32_t nowMs);
  /** Sends a tapped Music-screen control to Android over device events. */
  void notifyMediaControl(MediaControlAction action);
  /** Sends a tapped run-control action to Android over device events. */
  void notifyRunControl(RunControlAction action);
  /** Acknowledges a local notification dismissal using its Android sequence. */
  void notifyNotificationDismissed(uint16_t notificationSequence);
  /** Returns true only when a fresh Android metrics frame was applied. */
  bool applyLiveMetrics(DisplayState* state, uint32_t nowMs);
};

}  // namespace rundeck
