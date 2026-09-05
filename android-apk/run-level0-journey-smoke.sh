#!/usr/bin/env bash
set -uo pipefail

REPORT_DIR="app/build/reports/gameplay-smoke"
mkdir -p "$REPORT_DIR"

set +e
gradle :app:connectedDebugAndroidTest \
  --no-daemon \
  --build-cache \
  -Pandroid.testInstrumentationRunnerArguments.class=com.rabpit.backroom.Level0JourneySmokeTest
status=$?
set -e

if [[ $status -ne 0 ]]; then
  adb logcat -d > "$REPORT_DIR/logcat.txt" 2>&1 || true
  adb exec-out screencap -p > "$REPORT_DIR/failure-screen.png" 2>/dev/null || true
  adb shell dumpsys activity activities > "$REPORT_DIR/activity.txt" 2>&1 || true
fi

exit "$status"
