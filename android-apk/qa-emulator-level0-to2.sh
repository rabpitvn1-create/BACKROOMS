#!/usr/bin/env bash
set -uo pipefail

APK_PATH="${1:-Backroom-1.1.49.4.apk}"
PACKAGE="com.rabpit.backroom"
ACTIVITY="com.rabpit.backroom/.MainActivity"
OUT="${QA_OUT:-qa-emulator-output}"
mkdir -p "$OUT/ui" "$OUT/screens" "$OUT/state"

exec > >(tee "$OUT/runner.log") 2>&1

echo "QA started: $(date -u +%FT%TZ)"
echo "APK: $APK_PATH"
adb devices
adb shell wm size || true

record_problem() {
  printf '%s\n' "$*" | tee -a "$OUT/problems.txt"
}

dump_ui() {
  local tag="$1"
  local remote="/sdcard/window.xml"
  local target="$OUT/ui/${tag}.xml"
  local ok=0
  for _ in 1 2 3 4 5; do
    if adb shell uiautomator dump "$remote" >/dev/null 2>&1 && adb pull "$remote" "$target" >/dev/null 2>&1; then
      ok=1
      break
    fi
    sleep 1
  done
  [[ "$ok" -eq 1 ]]
}

screenshot() {
  local tag="$1"
  adb exec-out screencap -p > "$OUT/screens/${tag}.png" || true
}

dump_core_state() {
  local tag="$1"
  adb shell "run-as $PACKAGE cat shared_prefs/backroom_game_state_core.xml" > "$OUT/state/${tag}.xml" 2>/dev/null || true
}

ui_has_text() {
  local xml="$1"
  local needle="$2"
  python3 - "$xml" "$needle" <<'PY'
import sys, xml.etree.ElementTree as ET
path, needle = sys.argv[1], sys.argv[2].casefold()
try:
    root = ET.parse(path).getroot()
except Exception:
    raise SystemExit(1)
for node in root.iter('node'):
    value = (node.attrib.get('text','') + ' ' + node.attrib.get('content-desc','')).casefold()
    if needle in value:
        raise SystemExit(0)
raise SystemExit(1)
PY
}

node_center_by_text() {
  local xml="$1"
  local needle="$2"
  python3 - "$xml" "$needle" <<'PY'
import re, sys, xml.etree.ElementTree as ET
path, needle = sys.argv[1], sys.argv[2].casefold()
root = ET.parse(path).getroot()
choices=[]
for node in root.iter('node'):
    text=node.attrib.get('text','')
    desc=node.attrib.get('content-desc','')
    value=(text+' '+desc).casefold()
    if needle not in value:
        continue
    m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds',''))
    if not m:
        continue
    x1,y1,x2,y2=map(int,m.groups())
    if x2<=x1 or y2<=y1:
        continue
    enabled=node.attrib.get('enabled','true')!='false'
    clickable=node.attrib.get('clickable','false')=='true'
    exact=(text.strip().casefold()==needle or desc.strip().casefold()==needle)
    choices.append((exact, clickable, enabled, y2-y1, x1,y1,x2,y2,text,desc))
if not choices:
    raise SystemExit(1)
choices.sort(reverse=True)
_,_,enabled,_,x1,y1,x2,y2,text,desc=choices[0]
if not enabled:
    raise SystemExit(2)
print((x1+x2)//2, (y1+y2)//2)
PY
}

tap_text() {
  local tag="$1"
  local needle="$2"
  dump_ui "$tag" || return 1
  local xy
  xy="$(node_center_by_text "$OUT/ui/${tag}.xml" "$needle")" || return 1
  echo "Tap [$needle] at $xy"
  adb shell input tap $xy
}

edit_center() {
  local xml="$1"
  python3 - "$xml" <<'PY'
import re, sys, xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
choices=[]
for node in root.iter('node'):
    cls=node.attrib.get('class','')
    if 'EditText' not in cls:
        continue
    m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds',''))
    if not m:
        continue
    x1,y1,x2,y2=map(int,m.groups())
    if x2>x1 and y2>y1 and node.attrib.get('enabled','true')!='false':
        choices.append((y2-y1, x1,y1,x2,y2))
if not choices:
    raise SystemExit(1)
choices.sort(reverse=True)
_,x1,y1,x2,y2=choices[0]
print((x1+x2)//2, (y1+y2)//2)
PY
}

read_level() {
  local xml="$1"
  python3 - "$xml" <<'PY'
import re, sys, xml.etree.ElementTree as ET
names={0:'the lobby',1:'parking zone',2:'pipe dreams',3:'the electrical station',4:'the abandoned office',5:'terror hotel',6:'lights out'}
try:
    root=ET.parse(sys.argv[1]).getroot()
except Exception:
    print(-1); raise SystemExit
candidates=[]
for node in root.iter('node'):
    for v in (node.attrib.get('text',''), node.attrib.get('content-desc','')):
        s=v.strip().casefold()
        m=re.search(r'level\s*([0-6])',s)
        if not m:
            continue
        n=int(m.group(1))
        score=0
        if s.startswith('level'): score+=2
        if names[n] in s: score+=5
        if len(s)<80: score+=1
        candidates.append((score,n,v))
if not candidates:
    print(-1)
else:
    candidates.sort(reverse=True)
    print(candidates[0][1])
PY
}

read_all_text() {
  local xml="$1"
  python3 - "$xml" <<'PY'
import sys, xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
seen=[]
for node in root.iter('node'):
    for k in ('text','content-desc'):
        v=node.attrib.get(k,'').strip()
        if v and v not in seen:
            seen.append(v)
print('\n'.join(seen))
PY
}

wait_for_text() {
  local needle="$1"
  local timeout="${2:-90}"
  local elapsed=0
  while (( elapsed < timeout )); do
    if dump_ui "poll" && ui_has_text "$OUT/ui/poll.xml" "$needle"; then return 0; fi
    sleep 2
    elapsed=$((elapsed+2))
  done
  return 1
}

wait_turn_idle() {
  local timeout="${1:-180}"
  local elapsed=0
  sleep 2
  while (( elapsed < timeout )); do
    if ! adb shell pidof "$PACKAGE" >/dev/null 2>&1; then
      record_problem "CRASH: app process disappeared while waiting for turn completion."
      return 2
    fi
    if dump_ui "poll"; then
      if ui_has_text "$OUT/ui/poll.xml" "Lỗi Gemini:"; then
        record_problem "GAME_MASTER_ERROR: UI displayed 'Lỗi Gemini:' during traversal."
        return 3
      fi
      if ! ui_has_text "$OUT/ui/poll.xml" "ĐANG THỰC HIỆN" && ! ui_has_text "$OUT/ui/poll.xml" "ĐANG KHÁM PHÁ"; then
        if ui_has_text "$OUT/ui/poll.xml" "THỰC HIỆN" || ui_has_text "$OUT/ui/poll.xml" "BẮT ĐẦU COMBAT"; then
          return 0
        fi
      fi
    fi
    sleep 4
    elapsed=$((elapsed+4))
  done
  record_problem "TIMEOUT: gameplay turn did not return to an actionable state within ${timeout}s."
  return 4
}

handle_combat_if_present() {
  local rounds=0
  while (( rounds < 4 )); do
    dump_ui "combat-check" || return 0
    if ! ui_has_text "$OUT/ui/combat-check.xml" "BẮT ĐẦU COMBAT"; then return 0; fi
    echo "Pending combat detected. Starting combat."
    screenshot "combat-pending-${STEP:-0}-${rounds}"
    if ! tap_text "combat-tap" "BẮT ĐẦU COMBAT"; then
      record_problem "UI: pending combat was visible but BẮT ĐẦU COMBAT could not be tapped."
      return 1
    fi
    sleep 3
    local elapsed=0
    while (( elapsed < 180 )); do
      if ! adb shell pidof "$PACKAGE" >/dev/null 2>&1; then
        record_problem "CRASH: app process disappeared during combat playback."
        return 2
      fi
      dump_ui "combat-poll" || true
      if [[ -f "$OUT/ui/combat-poll.xml" ]] && ! ui_has_text "$OUT/ui/combat-poll.xml" "BẮT ĐẦU COMBAT" && (ui_has_text "$OUT/ui/combat-poll.xml" "THỰC HIỆN" || ui_has_text "$OUT/ui/combat-poll.xml" "KHÁM PHÁ & TÌM KIẾM"); then
        break
      fi
      sleep 4
      elapsed=$((elapsed+4))
    done
    if (( elapsed >= 180 )); then
      record_problem "TIMEOUT: combat playback did not return control within 180s."
      return 3
    fi
    rounds=$((rounds+1))
  done
  return 0
}

submit_explore() {
  tap_text "explore-ready" "KHÁM PHÁ & TÌM KIẾM"
}

submit_exit_action() {
  dump_ui "exit-ready" || return 1
  local xy
  xy="$(edit_center "$OUT/ui/exit-ready.xml")" || return 1
  adb shell input tap $xy
  sleep 1
  adb shell input text "scan%sfor%sexit%sand%sproceed%sto%sthe%snext%slevel%sif%san%sexit%sis%sfound"
  adb shell input keyevent 4
  sleep 1
  tap_text "exit-submit" "THỰC HIỆN"
}

# Install and force a true first-run state.
adb install -r "$APK_PATH"
adb shell pm clear "$PACKAGE" >/dev/null || true
adb logcat -c
adb shell am start -W -n "$ACTIVITY" | tee "$OUT/launch-result.txt"
sleep 8

if ! adb shell pidof "$PACKAGE" >/dev/null 2>&1; then
  record_problem "CRASH: application failed to remain alive after launch."
  adb logcat -d -v threadtime > "$OUT/logcat.txt" || true
  exit 20
fi

if ! wait_for_text "KHÁM PHÁ & TÌM KIẾM" 90; then
  record_problem "UI: GAME action controls did not become visible after fresh launch."
  screenshot "launch-no-controls"
  adb logcat -d -v threadtime > "$OUT/logcat.txt" || true
  exit 21
fi

dump_ui "start"
screenshot "start"
dump_core_state "start"
read_all_text "$OUT/ui/start.xml" > "$OUT/start-visible-text.txt" || true
LEVEL="$(read_level "$OUT/ui/start.xml")"
echo "Detected start Level: $LEVEL"
if [[ "$LEVEL" != "0" ]]; then
  record_problem "STATE: fresh launch did not present Level 0; detected Level=$LEVEL."
fi

INITIAL_LEVEL="$LEVEL"
TRANSITIONS=0
SUCCESS=0

for STEP in $(seq 1 26); do
  export STEP
  echo "===== GAMEPLAY STEP $STEP / current Level $LEVEL ====="
  handle_combat_if_present || true

  if (( STEP <= 6 )); then
    if ! submit_explore; then
      record_problem "UI: could not trigger KHÁM PHÁ & TÌM KIẾM on gameplay step $STEP."
      break
    fi
  else
    if ! submit_exit_action; then
      record_problem "UI: could not enter/submit exit-seeking action on gameplay step $STEP."
      break
    fi
  fi

  if ! wait_turn_idle 210; then
    screenshot "turn-${STEP}-timeout"
    dump_core_state "turn-${STEP}-timeout"
    break
  fi

  handle_combat_if_present || true
  dump_ui "turn-${STEP}" || true
  screenshot "turn-${STEP}"
  dump_core_state "turn-${STEP}"
  if [[ -f "$OUT/ui/turn-${STEP}.xml" ]]; then
    read_all_text "$OUT/ui/turn-${STEP}.xml" > "$OUT/ui/turn-${STEP}.txt" || true
    NEW_LEVEL="$(read_level "$OUT/ui/turn-${STEP}.xml")"
  else
    NEW_LEVEL=-1
  fi

  echo "Level after step $STEP: $NEW_LEVEL"
  if [[ "$NEW_LEVEL" =~ ^[0-6]$ ]]; then
    if (( NEW_LEVEL < LEVEL )); then
      record_problem "STATE: Level regressed from $LEVEL to $NEW_LEVEL on step $STEP."
    elif (( NEW_LEVEL > LEVEL )); then
      TRANSITIONS=$((TRANSITIONS+1))
      echo "Transition #$TRANSITIONS: Level $LEVEL -> Level $NEW_LEVEL"
      printf 'step=%s from=%s to=%s\n' "$STEP" "$LEVEL" "$NEW_LEVEL" >> "$OUT/transitions.txt"
      LEVEL="$NEW_LEVEL"
    fi
  fi

  # Mandatory Lucia contact should have happened by the action on displayed Turn 6.
  if (( STEP == 6 )); then
    if ! grep -Eqi 'Lucia|Hứa Thu[yý] Mai|Thu[yý] Mai' "$OUT/ui/turn-${STEP}.txt" 2>/dev/null; then
      record_problem "STORY/STATE: no visible Lucia/Hứa Thuý Mai evidence after the sixth completed gameplay action."
    fi
  fi

  if (( LEVEL >= 2 && TRANSITIONS >= 2 )); then
    SUCCESS=1
    break
  fi

done

dump_ui "final" || true
screenshot "final"
dump_core_state "final"
adb logcat -d -v threadtime > "$OUT/logcat.txt" || true
adb logcat -d -b crash -v threadtime > "$OUT/crash-buffer.txt" || true

if grep -Eq 'FATAL EXCEPTION|Fatal signal|Process: com\.rabpit\.backroom' "$OUT/crash-buffer.txt" "$OUT/logcat.txt"; then
  record_problem "CRASH: fatal process event found in Android logs."
fi
if grep -Eqi 'chromium.*(uncaught|error)|AndroidRuntime.*Exception' "$OUT/logcat.txt"; then
  grep -Ei 'chromium.*(uncaught|error)|AndroidRuntime.*Exception' "$OUT/logcat.txt" | tail -80 > "$OUT/runtime-errors.txt" || true
fi

{
  echo "# Emulator traversal report"
  echo
  echo "- APK: $APK_PATH"
  echo "- Started at Level: $INITIAL_LEVEL"
  echo "- Final detected Level: $LEVEL"
  echo "- Level transitions observed: $TRANSITIONS"
  echo "- Traversal target Level 0 -> Level 1 -> Level 2: $([[ $SUCCESS -eq 1 ]] && echo PASS || echo NOT REACHED)"
  echo "- Gameplay steps executed: ${STEP:-0}"
  echo
  echo "## Problems recorded"
  if [[ -s "$OUT/problems.txt" ]]; then sed 's/^/- /' "$OUT/problems.txt"; else echo "- No harness-detected problem."; fi
} > "$OUT/report.md"

cat "$OUT/report.md"

if [[ "$SUCCESS" -ne 1 ]]; then
  record_problem "TRAVERSAL: target of two Level transitions was not reached within ${STEP:-0} gameplay steps."
  exit 30
fi

exit 0
