#!/bin/sh
set -eu

PACKAGE="com.rabpit.backroom"
COMPONENT="$PACKAGE/.MainActivity"
APK="Backroom-1.1.50.apk"

# API 36 software-emulated runners can report sys.boot_completed before PackageManager is
# actually ready to accept a streamed install. Wait for the package service explicitly so a
# transient emulator IPC failure is not mistaken for an application startup failure.
adb wait-for-device

pm_attempt=1
pm_ready=0
while [ "$pm_attempt" -le 30 ]; do
  if adb shell service check package 2>/dev/null | grep -q "found" \
      && adb shell cmd package list packages >/dev/null 2>&1; then
    pm_ready=1
    break
  fi
  echo "Waiting for Android package service ($pm_attempt/30)"
  sleep 5
  pm_attempt=$((pm_attempt + 1))
done

if [ "$pm_ready" -ne 1 ]; then
  echo "Android package service never became ready"
  adb shell service check package || true
  exit 1
fi

install_attempt=1
installed=0
while [ "$install_attempt" -le 3 ]; do
  echo "APK install attempt $install_attempt"
  if timeout 180 adb install --no-streaming "$APK"; then
    installed=1
    break
  fi
  echo "APK install attempt $install_attempt failed; waiting for emulator package service before retry"
  adb wait-for-device || true
  adb shell service check package || true
  adb shell cmd package list packages >/dev/null 2>&1 || true
  sleep 10
  install_attempt=$((install_attempt + 1))
done

if [ "$installed" -ne 1 ]; then
  echo "APK installation failed after three attempts"
  exit 1
fi

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
