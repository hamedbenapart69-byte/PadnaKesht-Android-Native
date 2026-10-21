#!/usr/bin/env bash
# Runs instrumented tests and exports screenshots from the debuggable app sandbox.
set +e
mkdir -p screenshots
gradle connectedDebugAndroidTest --stacktrace 2>&1 | tee gradle-connected.log
test_result=${PIPESTATUS[0]}
adb logcat -d > screenshots/emulator-logcat.txt 2>&1

screenshot_result=0
for name in dashboard business reports; do
  target="screenshots/padnakasht-${name}.png"
  if adb exec-out run-as com.padnakasht.nativeapp cat "files/padnakasht-${name}.png" > "$target" 2>/dev/null && test -s "$target"; then
    echo "Captured UI evidence: $target ($(wc -c < "$target") bytes)"
  else
    echo "::error::Expected UI screenshot was not available: $target"
    rm -f "$target"
    screenshot_result=1
  fi
done
cp gradle-connected.log screenshots/gradle-connected.log 2>/dev/null

if [ "$test_result" -ne 0 ]; then
  exit "$test_result"
fi
exit "$screenshot_result"
