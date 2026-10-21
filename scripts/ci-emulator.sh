#!/usr/bin/env bash
# Runs instrumented tests, keeps diagnostics, and exports genuine emulator screenshots.
set +e
mkdir -p screenshots
gradle connectedDebugAndroidTest --stacktrace 2>&1 | tee gradle-connected.log
test_result=${PIPESTATUS[0]}
adb logcat -d > screenshots/emulator-logcat.txt 2>&1

screenshot_result=0
for name in dashboard customers invoice reports; do
  target="screenshots/padnakasht-${name}.png"
  device_path="/sdcard/Pictures/PadnaKeshtCI/padnakasht-${name}.png"
  if adb pull "$device_path" "$target" >/dev/null 2>&1 && test -s "$target" && file "$target" | grep -q 'PNG image data'; then
    echo "Captured UI evidence: $target ($(wc -c < "$target") bytes)"
  else
    echo "::error::Expected valid UI screenshot was not available: $target"
    rm -f "$target"
    screenshot_result=1
  fi
done
cp gradle-connected.log screenshots/gradle-connected.log 2>/dev/null

if [ "$test_result" -ne 0 ]; then
  exit "$test_result"
fi
exit "$screenshot_result"
