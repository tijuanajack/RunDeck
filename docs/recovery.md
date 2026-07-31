# Display recovery and cold-boot gate

Use this procedure when the RunDeck display stays black after an update or a
USB power cycle. A black display is a physical validation failure, not a cue to
blindly flash another firmware build.

## Recovery sequence

1. Disconnect the board for five seconds, reconnect it directly to the
   development computer, and identify the Espressif USB JTAG serial port. Do
   not assume a `ttyACM` number—an attached Android phone also exposes one.

   ```sh
   udevadm info --query=property --name=/dev/ttyACM0
   ```

   The correct port identifies itself as `Espressif_USB_JTAG_serial_debug_unit`.

2. Restore the known-good full factory image. This intentionally overwrites
   all 16 MB of flash; the image is local and ignored by Git.

   ```sh
   cd firmware
   sg dialout -c './tools/restore-factory.sh /dev/ttyACM0'
   ```

3. Use a known-good direct USB **data** cable. If a full-image write drops,
   put the board in BOOT mode (hold BOOT while connecting USB for three
   seconds), reconnect with a different cable, and rerun the restore. The
   recovery script intentionally uses the ROM loader at 115200 baud for this
   board.

4. **Stop and inspect the board.** Confirm the factory display appears, touch
   responds where applicable, and it still boots after one USB unplug/replug.
   If this fails, record the behavior, cable/power source, and serial output
   before attempting another image.

5. The working RunDeck image uses the Waveshare V2 TCA9554 reset sequence:
   EXIO0 resets the SH8601 OLED, EXIO1 resets touch, and direct GPIO21 LCD
   reset remains disabled. Compile and flash only this known-good path unless
   intentionally testing a new display candidate.

   ```sh
   cd firmware
   sg dialout -c '/home/tjhurt/.local/bin/arduino-cli compile --fqbn esp32:esp32:waveshare_esp32_s3_touch_amoled_241 RunDeck --output-dir build && /home/tjhurt/.local/bin/arduino-cli upload --fqbn esp32:esp32:waveshare_esp32_s3_touch_amoled_241 --port /dev/ttyACM0 RunDeck'
   ```

6. Verify RunDeck while USB-powered, then cold-boot it three times (unplug for
   five seconds, reconnect, wait up to 15 seconds). Test BLE only after all
   three cold boots show the display. A release is not considered stable until
   it passes this gate.

## Rules that prevent repeat incidents

- Never flash when more than one candidate serial device exists without
  explicitly identifying the Espressif port.
- Never replace the complete factory backup. Do not commit it or generated
  binaries.
- Keep display-driver, display-pin, boot, and power changes isolated from UI
  work. Compile first; flash only changes requiring device validation.
- If a build fails a cold boot, restore factory first and keep the failed
  revision documented. Do not stack additional changes onto it.
- A serial upload hash only proves flash contents. It does not prove the panel
  initialized after cold power-on; the physical cold-boot gate does.
