#if 1
#ifndef LV_CONF_H
#define LV_CONF_H

/* RunDeck is optimized for a 600×450 RGB565 AMOLED panel. */
#define LV_COLOR_DEPTH 16
// Waveshare's QSPI AMOLED transfer expects RGB565 bytes in wire order.
// Without this, lime/cyan accents are rendered as magenta/purple.
#define LV_COLOR_16_SWAP 1
#define LV_MEM_SIZE (64U * 1024U)
#define LV_FONT_MONTSERRAT_14 1
#define LV_FONT_MONTSERRAT_16 1
#define LV_FONT_MONTSERRAT_20 1
#define LV_FONT_MONTSERRAT_28 1
#define LV_FONT_MONTSERRAT_36 1
#define LV_FONT_MONTSERRAT_48 1
#define LV_USE_LOG 1
#define LV_USE_PERF_MONITOR 0
#define LV_USE_MEM_MONITOR 0

#endif
#endif
