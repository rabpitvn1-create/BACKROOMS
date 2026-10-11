#!/usr/bin/env bash
set -euo pipefail
mkdir -p emulator-evidence
APK="android-apk/app/build/outputs/apk/debug/app-debug.apk"
test -s "$APK"
adb install -r "$APK"
adb shell am start -W -n com.rabpit.backroom/.MainActivity
PID=""
for i in $(seq 1 45); do
  PID="$(adb shell pidof com.rabpit.backroom | tr -d '\r' | awk '{print $1}')"
  if [[ -n "$PID" ]]; then break; fi
  sleep 1
done
test -n "$PID"
adb forward tcp:9222 "localabstract:webview_devtools_remote_$PID"
node .github/scripts/combat-emulator-level0.cjs
