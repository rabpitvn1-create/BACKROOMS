#!/usr/bin/env bash
set -euo pipefail
mkdir -p emulator-evidence
stage() {
  printf '%s | %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a emulator-evidence/stages.log
}
on_exit() {
  code=$?
  stage "Exit status=$code stage=$STAGE"
  exit "$code"
}
trap on_exit EXIT
APK="android-apk/app/build/outputs/apk/debug/app-debug.apk"
STAGE="validate-apk"; stage "$STAGE"; test -s "$APK"
STAGE="adb-devices"; stage "$STAGE"
timeout -k 3s 20s adb devices -l | tee emulator-evidence/adb-device-before.txt
STAGE="install-apk"; stage "$STAGE"
timeout -k 5s 120s adb install -r "$APK"
STAGE="start-game"; stage "$STAGE"
timeout -k 5s 40s adb shell am start -W -n com.rabpit.backroom/.MainActivity
STAGE="resolve-app-pid"; stage "$STAGE"
PID=""
for i in $(seq 1 40); do
  PID="$(timeout -k 1s 5s adb shell pidof com.rabpit.backroom | tr -d '\r' | awk '{print $1}')" || true
  if [[ -n "$PID" ]]; then break; fi
  sleep 1
done
if [[ -z "$PID" ]]; then stage "ERROR: app process did not start"; exit 1; fi
stage "App PID=$PID"
STAGE="connect-webview-devtools"; stage "$STAGE"
timeout -k 3s 15s adb forward tcp:9222 "localabstract:webview_devtools_remote_$PID"
STAGE="play-game-level0"; stage "$STAGE"
timeout -k 10s 14m node .github/scripts/combat-emulator-level0.cjs 2>&1 | tee emulator-evidence/node-output.log
stage "Completed game runner"
