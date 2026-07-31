#pragma once

namespace rundeck {

/** Initializes the verified 600×450 landscape QSPI panel and FT5x06 touch. */
bool beginWaveshareBoard();
/** Sets the SH8601 AMOLED brightness without changing the reset/power path. */
bool setDisplayBrightness(uint8_t level);

}  // namespace rundeck
