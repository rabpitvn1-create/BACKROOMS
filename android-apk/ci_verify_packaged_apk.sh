#!/usr/bin/env bash
set -euo pipefail

APK=${1:-Backroom-1.3.8-debug.apk}
BUILD_TOOLS=$(find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)

"$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$APK"
"$BUILD_TOOLS/aapt" dump badging "$APK" | grep -q "package: name='com.rabpit.backroom'"
"$BUILD_TOOLS/aapt" dump badging "$APK" | grep -q "versionCode='93'"
"$BUILD_TOOLS/aapt" dump badging "$APK" | grep -q "versionName='1.3.8'"
"$BUILD_TOOLS/aapt" dump badging "$APK" | grep -q "launchable-activity: name='com.rabpit.backroom.MainActivity'"
"$BUILD_TOOLS/zipalign" -c -P 16 -v 4 "$APK"

rm -rf apk-check
mkdir apk-check
unzip -q "$APK" \
  'assets/index.html' \
  'assets/levels/*' \
  'assets/level_profiles/*' \
  'assets/level_catalog/*' \
  'assets/level_snapshots/*' \
  'assets/models/*' \
  'assets/entity/*' \
  'assets/Kai_new_overlay.png' \
  'assets/BESTKAIV2.png' \
  -d apk-check

grep -q 'searchActionButton' apk-check/assets/index.html
grep -q 'exploreActionButton' apk-check/assets/index.html
! grep -qi 'madGodSetEquipped' apk-check/assets/index.html
grep -q 'equipmentDetailModal' apk-check/assets/index.html
grep -q 'renderCharacterStatusEquipment' apk-check/assets/index.html
grep -q 'characterSkillsModal' apk-check/assets/index.html
grep -q 'characterSkillsButton' apk-check/assets/index.html
# Observations must blend into ordinary prose, including when loading a save with an old ledger.
if grep -Eq 'EVIDENCE_HIGHLIGHT_V1|rpg-evidence-badge|evidenceHighlightStyle' apk-check/assets/index.html; then
  echo 'Packaged APK still exposes evidence highlighting' >&2
  exit 1
fi

# TURN remains an internal state key but must not be visible in the player HUD. Escape is resolved
# from locked Level instances, never from a player-facing percentage meter.
grep -q '.turn{display:none!important}' apk-check/assets/index.html
! grep -q 'id="escapeChance"' apk-check/assets/index.html
! grep -q 'ESCAPE_CHANCE_HUD_R02' apk-check/assets/index.html
! grep -q 'getEscapeChancePercent' apk-check/assets/index.html
grep -q 'const CORE_SAVE_KEY="backroom-apk-core-state"' apk-check/assets/index.html
grep -q 'Android.exportCoreState()' apk-check/assets/index.html
grep -q 'Android.restoreCoreState(raw)' apk-check/assets/index.html

# Level packaging is catalog-driven. The verifier has no knowledge of the last Level ID and does
# not maintain a hand-written list of definitions/profiles. Validate the source and extracted APK
# with the same fail-closed inventory, then require byte-independent semantic equivalence.
SOURCE_LEVEL_REPORT=$(mktemp)
PACKAGED_LEVEL_REPORT=$(mktemp)
trap 'rm -f "$SOURCE_LEVEL_REPORT" "$PACKAGED_LEVEL_REPORT"' EXIT
python3 android-apk/validate_level_content.py \
  --assets-root android-apk/app/src/main/assets \
  --strict --json > "$SOURCE_LEVEL_REPORT"
python3 android-apk/validate_level_content.py \
  --assets-root apk-check/assets \
  --strict --json > "$PACKAGED_LEVEL_REPORT"
python3 - "$SOURCE_LEVEL_REPORT" "$PACKAGED_LEVEL_REPORT" <<'PY'
import json
import sys

source_path, packaged_path = sys.argv[1:]
with open(source_path, encoding="utf-8") as handle:
    source = json.load(handle)
with open(packaged_path, encoding="utf-8") as handle:
    packaged = json.load(handle)

for key in ("summary", "levels", "errors"):
    if source.get(key) != packaged.get(key):
        raise SystemExit(f"Packaged Level content differs from source report: {key}")
if packaged.get("summary", {}).get("validationErrors") != 0:
    raise SystemExit("Packaged Level content report contains validation errors")
print(
    "Catalog-driven packaged Level verification passed: "
    f"{packaged['summary']['totalCatalogLevels']} Levels, "
    f"{packaged['summary']['transitionEdges']} transitions"
)
PY

# Both local models are build-time generated/cache-restored assets. The Director model now ranks
# broad world-pressure proposals only; Core owns legality/liveness and the proposal cannot mutate
# gameplay state by itself. Evidence-source labels are retired from the packaged model contract.
test -s apk-check/assets/models/backroom_intent.tflite
test -s apk-check/assets/models/backroom_intent_labels.txt
test -s apk-check/assets/models/backrooms_director.tflite
test -s apk-check/assets/models/backrooms_director_labels.txt
grep -q '^NONE$' apk-check/assets/models/backrooms_director_labels.txt
grep -q '^MAZE_PRESSURE$' apk-check/assets/models/backrooms_director_labels.txt
grep -q '^ENTITY_PRESSURE$' apk-check/assets/models/backrooms_director_labels.txt
grep -q '^ITEM_OPPORTUNITY$' apk-check/assets/models/backrooms_director_labels.txt
! grep -q '^SEARCH$' apk-check/assets/models/backrooms_director_labels.txt
! grep -q '^ENVIRONMENT$' apk-check/assets/models/backrooms_director_labels.txt
! grep -q '^ANOMALY$' apk-check/assets/models/backrooms_director_labels.txt
! grep -q '^SURVIVOR$' apk-check/assets/models/backrooms_director_labels.txt

test -s apk-check/assets/entity/hound.png
test -s apk-check/assets/entity/slenderman.png
test -s apk-check/assets/entity/diep_minh.png
test -s apk-check/assets/entity/173.png
test -s apk-check/assets/entity/SCP173.png
test -s apk-check/assets/entity/Jane.png
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
