#!/usr/bin/env bash
set -euo pipefail

APK=${1:-Backroom-1.3.4-debug.apk}
BUILD_TOOLS=$(find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)

"$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$APK"
"$BUILD_TOOLS/aapt" dump badging "$APK" | grep -q "package: name='com.rabpit.backroom'"
"$BUILD_TOOLS/aapt" dump badging "$APK" | grep -q "versionCode='89'"
"$BUILD_TOOLS/aapt" dump badging "$APK" | grep -q "versionName='1.3.4'"
"$BUILD_TOOLS/aapt" dump badging "$APK" | grep -q "launchable-activity: name='com.rabpit.backroom.MainActivity'"
"$BUILD_TOOLS/zipalign" -c -P 16 -v 4 "$APK"

rm -rf apk-check
mkdir apk-check
unzip -q "$APK" 'assets/index.html' 'assets/levels/*' 'assets/level_catalog/*' 'assets/models/*' 'assets/entity/*' 'assets/Kai_new_overlay.png' 'assets/BESTKAIV2.png' -d apk-check

grep -q 'searchActionButton' apk-check/assets/index.html
grep -q 'exploreActionButton' apk-check/assets/index.html
! grep -qi 'madGodSetEquipped' apk-check/assets/index.html
grep -q 'equipmentDetailModal' apk-check/assets/index.html
grep -q 'renderCharacterStatusEquipment' apk-check/assets/index.html
grep -q 'characterSkillsModal' apk-check/assets/index.html
grep -q 'characterSkillsButton' apk-check/assets/index.html

# TURN remains an internal state key but must not be visible in the player HUD. Escape is now
# resolved from locked Level blueprints, never from a player-facing percentage meter.
grep -q '.turn{display:none!important}' apk-check/assets/index.html
! grep -q 'id="escapeChance"' apk-check/assets/index.html
! grep -q 'ESCAPE_CHANCE_HUD_R02' apk-check/assets/index.html
! grep -q 'getEscapeChancePercent' apk-check/assets/index.html
grep -q 'const CORE_SAVE_KEY="backroom-apk-core-state"' apk-check/assets/index.html
grep -q 'Android.exportCoreState()' apk-check/assets/index.html
grep -q 'Android.restoreCoreState(raw)' apk-check/assets/index.html

test -s apk-check/assets/levels/0.json
test -s apk-check/assets/levels/1.json
grep -q '"schemaVersion"' apk-check/assets/levels/1.json
grep -q '"escapeBlueprint"' apk-check/assets/levels/1.json

test -s apk-check/assets/level_catalog/backrooms-0-6.json
grep -q '"campaignId": "BACKROOMS_FANDOM_LEVELS_0_6_R01"' apk-check/assets/level_catalog/backrooms-0-6.json
grep -q '"id":"1.618033988749894..."' apk-check/assets/level_catalog/backrooms-0-6.json
grep -q '"id":"Red Rooms"' apk-check/assets/level_catalog/backrooms-0-6.json

# Both local models are build-time generated/cache-restored assets. The Director model is separate
# from player-intent classification and can only rank evidence already declared legal by Core.
test -s apk-check/assets/models/backroom_intent.tflite
test -s apk-check/assets/models/backroom_intent_labels.txt
test -s apk-check/assets/models/backrooms_director.tflite
test -s apk-check/assets/models/backrooms_director_labels.txt
grep -q '^SEARCH$' apk-check/assets/models/backrooms_director_labels.txt
grep -q '^ENVIRONMENT$' apk-check/assets/models/backrooms_director_labels.txt
grep -q '^ANOMALY$' apk-check/assets/models/backrooms_director_labels.txt
grep -q '^SURVIVOR$' apk-check/assets/models/backrooms_director_labels.txt

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
