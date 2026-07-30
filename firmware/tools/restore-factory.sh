#!/usr/bin/env bash
set -euo pipefail

# Deliberately restores only the known-good full flash image. It never guesses
# a serial port: a phone connected for ADB often appears as another ttyACM port.
if [[ $# -ne 1 ]]; then
  echo "Usage: $0 /dev/ttyACM<n>" >&2
  exit 2
fi

port="$1"
root="$(cd "$(dirname "$0")/.." && pwd)"
backup="$root/backups/factory-before-rundeck.bin"
esptool="${ESPTOOL_PY:-$HOME/.arduino15/packages/esp32/tools/esptool_py/4.6/esptool.py}"

[[ -c "$port" ]] || { echo "Not a serial device: $port" >&2; exit 3; }
[[ -f "$backup" ]] || { echo "Missing factory backup: $backup" >&2; exit 4; }
[[ -f "$esptool" ]] || { echo "Missing esptool: $esptool" >&2; exit 5; }

echo "Restoring the complete 16 MB factory image to $port at conservative USB speed."
# This board disconnects once esptool's RAM stub has taken over.  Use the
# ESP32-S3 ROM loader directly for recovery; it is slower but independent of
# the installed application and avoids the failing stub path.
python3 "$esptool" --chip esp32s3 --port "$port" --baud 115200 --no-stub \
  write_flash 0x0 "$backup"
echo "Factory restore complete. Physically verify the factory display before flashing RunDeck."
