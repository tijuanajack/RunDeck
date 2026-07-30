#!/usr/bin/env bash
set -euo pipefail

cli="${ARDUINO_CLI:-$HOME/.local/bin/arduino-cli}"
fqbn="esp32:esp32:waveshare_esp32_s3_touch_amoled_241"
root="$(cd "$(dirname "$0")/.." && pwd)"

if [[ "${RUNDECK_HARDWARE_VALIDATED:-}" != "1" ]]; then
  echo "Refusing to flash: first complete docs/hardware-validation.md."
  echo "After recording the vendor smoke test, run with RUNDECK_HARDWARE_VALIDATED=1."
  exit 2
fi

mapfile -t ports < <("$cli" board list | awk 'NR > 1 && $1 ~ /^\/dev\// { print $1 }')
if [[ ${#ports[@]} -ne 1 ]]; then
  echo "Expected exactly one connected serial board; found ${#ports[@]}."
  "$cli" board list
  exit 3
fi

"$root/tools/fetch-waveshare-bsp.sh"
"$cli" compile --fqbn "$fqbn" "$root/RunDeck" --output-dir "$root/build"
"$cli" upload --fqbn "$fqbn" --port "${ports[0]}" "$root/RunDeck"
echo "Flashed RunDeck to ${ports[0]}. Open the serial monitor at 115200 baud."
