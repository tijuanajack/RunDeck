#pragma once

namespace rundeck {

/** Initializes the verified 600×450 landscape QSPI panel and FT5x06 touch. */
bool beginWaveshareBoard();
/** Turns the panel off/on without touching the verified reset or power path. */
bool setDisplayAwake(bool awake);
/** Returns true once when the touch controller reports user activity. */
bool consumeTouchActivity();
/** Sets the SH8601 AMOLED brightness without changing the reset/power path. */
bool setDisplayBrightness(uint8_t level);
/** Applies a queued brightness change from the LVGL/main loop. */
void applyPendingDisplayBrightness();

}  // namespace rundeck
