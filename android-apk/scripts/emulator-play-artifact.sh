#!/usr/bin/env bash
# Install and operate ONLY artifacts produced in this workflow run, not a local rebuild.
set -euo pipefail

mkdir -p emulator-evidence
trap '
  adb logcat -d -v threadtime -s AndroidRuntime:E > emulator-evidence/android-runtime-errors.log 2>/dev/null || true
  adb shell screencap -p /sdcard/backroom-final.png >/dev/null 2>&1 || true
  adb pull /sdcard/backroom-final.png emulator-evidence/final-screen.png >/dev/null 2>&1 || true
  adb pull /sdcard/Android/data/com.rabpit.backroom/files/emulator-smoke emulator-evidence/playthrough >/dev/null 2>&1 || true
' EXIT

app_path="emulator-artifacts/game/Backroom-1.2.0.0.39.apk"
test_path="emulator-artifacts/instrumentation/app-debug-androidTest.apk"
test -s "$app_path"
test -s "$test_path"
echo "Actual artifact SHA256:"
sha256sum "$app_path"
adb wait-for-device
adb shell getprop sys.boot_completed | grep -q 1
# Remove Android's one-time immersive-fullscreen instruction before launching
# the game; the test itself also rejects any remaining system-level overlays.
adb shell settings put secure immersive_mode_confirmations confirmed
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0

# Install the exact previously uploaded app artifact and its same-signer test APK.
adb install -r "$app_path"
adb install -r "$test_path"
adb shell pm path com.rabpit.backroom | grep -q package:
adb shell pm path com.rabpit.backroom.test | grep -q package:

set +e
adb shell am instrument -w -r -e class com.rabpit.backroom.EmulatorGameplayTest,com.rabpit.backroom.Level0RiddleGameplayTest \
  com.rabpit.backroom.test/androidx.test.runner.AndroidJUnitRunner \
  2>&1 | tee emulator-evidence/instrumentation.log
test_result=${PIPESTATUS[0]}
set -e

# am instrument may exit 0 even after assertion failures: inspect JUnit result.
test "$test_result" -eq 0
grep -q 'OK (2 tests)' emulator-evidence/instrumentation.log
! grep -q 'FAILURES!!!' emulator-evidence/instrumentation.log
adb pull /sdcard/Android/data/com.rabpit.backroom/files/emulator-smoke emulator-evidence/playthrough
test -s emulator-evidence/playthrough/gameplay-report.json
test -s emulator-evidence/playthrough/02-fullscreen-map.png
test -s emulator-evidence/playthrough/07-map-after-move.png || test -s emulator-evidence/playthrough/07-encounter-after-move.png
test -s emulator-evidence/playthrough/09-real-gm-narration.png
echo 'EMULATOR ARTIFACT PLAYTHROUGH: PASS'
