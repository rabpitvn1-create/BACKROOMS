#!/bin/sh
set -eu

PACKAGE="${BACKROOM_PACKAGE:-com.rabpit.backroom}"
ACTIVITY="${BACKROOM_ACTIVITY:-com.rabpit.backroom.MainActivity}"
COMPONENT="$PACKAGE/$ACTIVITY"
APK="${BACKROOM_APK:-Backroom-1.1.50.apk}"

wait_for_package_service() {
  label="$1"
  attempt=1
  while [ "$attempt" -le 60 ]; do
    boot="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
    if [ "$boot" = "1" ] \
        && adb shell service check package 2>/dev/null | grep -q "found" \
        && adb shell cmd package list packages >/dev/null 2>&1; then
      echo "Android package service ready: $label"
      return 0
    fi
    echo "Waiting for Android package service: $label ($attempt/60)"
    adb wait-for-device >/dev/null 2>&1 || true
    sleep 5
    attempt=$((attempt + 1))
  done
  echo "Android package service did not recover: $label"
  adb shell getprop sys.boot_completed || true
  adb shell service check package || true
  return 1
}

adb wait-for-device
wait_for_package_service "before install"

install_attempt=1
installed=0
while [ "$install_attempt" -le 3 ]; do
  echo "APK install attempt $install_attempt for $PACKAGE"
  # Software-emulated API 36 can take several minutes to dex/scan a ~38 MB APK.
  # Do not kill adb at 180s while PackageManager may still be processing it.
  if timeout 600 adb install --no-streaming "$APK"; then
    installed=1
    break
  fi

  echo "APK install attempt $install_attempt failed; waiting for PackageManager to fully recover"
  adb wait-for-device >/dev/null 2>&1 || true
  wait_for_package_service "after failed install $install_attempt"
  sleep 15
  install_attempt=$((install_attempt + 1))
done

if [ "$installed" -ne 1 ]; then
  echo "APK installation failed after three attempts"
  exit 1
fi

wait_for_package_service "after successful install"

attempt=1
while [ "$attempt" -le 3 ]; do
  echo "Android 16 cold launch attempt $attempt for $PACKAGE using $ACTIVITY"
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

  if ! adb shell dumpsys activity activities | grep -E -q "(mResumedActivity|topResumedActivity).*${PACKAGE}/.*MainActivity"; then
    echo "MainActivity is not resumed after cold launch attempt $attempt"
    adb shell dumpsys activity activities | grep -E "ResumedActivity|topResumedActivity|${PACKAGE}|MainActivity" || true
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

echo "Android 16 cold-launch smoke test passed three consecutive launches for $PACKAGE."
