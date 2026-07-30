#!/usr/bin/env bash
set -euo pipefail

archive_url='https://files.waveshare.com/wiki/ESP32-S3-Touch-AMOLED-2.41/ESP32-S3-Touch-AMOLED-2.41-Demo.zip'
destination="$(cd "$(dirname "$0")/.." && pwd)/vendor/waveshare-demo"

mkdir -p "$destination"
curl --fail --location --output "$destination/demo.zip" "$archive_url"
unzip -oq "$destination/demo.zip" -d "$destination/source"

adapter_vendor="$(cd "$(dirname "$0")/../RunDeck" && pwd)"
demo_dir="$destination/source/CN/Arduino/examples/09_LVGL_Test"
for file in esp_lcd_sh8601.c esp_lcd_sh8601.h esp_lcd_touch.c esp_lcd_touch.h esp_lcd_touch_ft5x06.c esp_lcd_touch_ft5x06.h; do
  cp "$demo_dir/$file" "$adapter_vendor/$file"
done

cat <<'EOF'
Fetched the Waveshare archive to firmware/vendor/waveshare-demo (gitignored)
and copied the required vendor display/touch sources to the ignored RunDeck
sketch root (Arduino CLI compiles only root-level sketch sources).

Validate source/EN/Arduino/examples/09_LVGL_Test or its CN equivalent unchanged
before adapting the following vendor files into the RunDeck build:
  esp_lcd_sh8601.[ch]
  esp_lcd_touch.[ch]
  esp_lcd_touch_ft5x06.[ch]

The current vendor LVGL demo names the panel SH8601. Confirm against the
delivered board before treating this as final hardware configuration.
EOF
