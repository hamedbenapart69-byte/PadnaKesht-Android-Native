#!/usr/bin/env bash
# Runs instrumented tests on the emulator, keeps the Gradle output and device
# diagnostics, and preserves the real Gradle exit status for the CI step.
set +e
mkdir -p screenshots
gradle connectedDebugAndroidTest --stacktrace 2>&1 | tee gradle-connected.log
test_result=${PIPESTATUS[0]}
adb logcat -d > screenshots/emulator-logcat.txt 2>&1
adb pull /sdcard/Android/data/com.padnakasht.nativeapp/files/. screenshots/ >/dev/null 2>&1
cp gradle-connected.log screenshots/gradle-connected.log 2>/dev/null
exit "$test_result"
