#!/bin/sh
set -eu

PACKAGE="com.rabpit.backroom"
COMPONENT="$PACKAGE/.MainActivity"
APK="Backroom-1.1.50.apk"

adb install "$APK"

attempt=1
while [ "$attempt" -le 3 ]; do
  echo "Android 16 cold launch attempt $attempt"
  adb shell am force-stop "$PACKAGE"
  adb logcat -c

  adb shell am start -W -n "$COMPONENT"
  sleep 12

  if ! adb shell pidof "$PACKAGE" >/dev/null 2>&1; then
    echo "App process is not alive after cold launch attempt $attempt"
    adb logcat -d -b crash || true
    adb logcat -d | tail -n 400 || true
    exit 1
  fi

  if ! adb shell dumpsys activity activities | grep -E -q "(mResumedActivity|topResumedActivity).*${PACKAGE}/.MainActivity"; then
    echo "MainActivity is not resumed after cold launch attempt $attempt"
    adb shell dumpsys activity activities | grep -E "ResumedActivity|topResumedActivity|${PACKAGE}" || true
    adb logcat -d -b crash || true
    exit 1
  fi

  if adb logcat -d -b crash | grep -q "$PACKAGE"; then
    echo "Android crash buffer contains $PACKAGE after attempt $attempt"
    adb logcat -d -b crash
    exit 1
  fi

  if adb logcat -d | grep -E -q "FATAL EXCEPTION.*${PACKAGE}|Process: ${PACKAGE}.*FATAL"; then
    echo "Fatal exception found for $PACKAGE after attempt $attempt"
    adb logcat -d | grep -E -C 25 "FATAL EXCEPTION|Process: ${PACKAGE}"
    exit 1
  fi

  attempt=$((attempt + 1))
done

echo "Android 16 cold-launch smoke test passed three consecutive launches."
