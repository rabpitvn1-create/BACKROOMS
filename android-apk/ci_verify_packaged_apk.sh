#!/usr/bin/env bash
set -euo pipefail

APK=${1:-Backroom-1.3.2-debug.apk}
BUILD_TOOLS=$(find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)

"$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$APK"
"$BUILD_TOOLS/aapt" dump badging "$APK" | grep -q "package: name='com.rabpit.backroom'"
"$BUILD_TOOLS/aapt" dump badging "$APK" | grep -q "versionCode='87'"
"$BUILD_TOOLS/aapt" dump badging "$APK" | grep -q "versionName='1.3.2'"
"$BUILD_TOOLS/aapt" dump badging "$APK" | grep -q "launchable-activity: name='com.rabpit.backroom.MainActivity'"
"$BUILD_TOOLS/zipalign" -c -P 16 -v 4 "$APK"

rm -rf apk-check
mkdir apk-check
unzip -q "$APK" 'assets/index.html' 'assets/entity/*' 'assets/Kai_new_overlay.png' 'assets/BESTKAIV2.png' -d apk-check

grep -q 'searchActionButton' apk-check/assets/index.html
grep -q 'exploreActionButton' apk-check/assets/index.html
! grep -qi 'madGodSetEquipped' apk-check/assets/index.html
grep -q 'equipmentDetailModal' apk-check/assets/index.html
grep -q 'renderCharacterStatusEquipment' apk-check/assets/index.html
grep -q 'characterSkillsModal' apk-check/assets/index.html
grep -q 'characterSkillsButton' apk-check/assets/index.html

test -s apk-check/assets/entity/hound.png
test -s apk-check/assets/entity/slenderman.png
test -s apk-check/assets/entity/diep_minh.png
test -s apk-check/assets/entity/173.png
test -s apk-check/assets/entity/SCP173.png
test -s apk-check/assets/entity/John.png
test -s apk-check/assets/entity/Newviolet.png
test -s apk-check/assets/entity/Kai-TheDevilWithin.png
test -s apk-check/assets/Kai_new_overlay.png
test -s apk-check/assets/BESTKAIV2.png

! unzip -l "$APK" | grep -q 'assets/Kai_MadGod_snapshot_overlay.png'
! unzip -l "$APK" | grep -q 'assets/avatars/MadGod.jpg'
! unzip -l "$APK" | grep -q 'assets/kai_snapshot_overlay.png'
! unzip -l "$APK" | grep -q 'assets/kai_snapshot_overlay.webp'
! unzip -l "$APK" | grep -q 'assets/BestKai.png'

printf 'Packaged APK contracts verified: %s\n' "$APK"
