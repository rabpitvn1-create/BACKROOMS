#!/usr/bin/env bash
set -uo pipefail

APK="${APK_PATH:-Backroom-1.1.49.5.apk}"
OUT="${QA_OUT:-qa-artifacts}"
PKG="com.rabpit.backroom"
ACTIVITY="${PKG}/.MainActivity"
mkdir -p "$OUT"

log(){ printf '[qa] %s\n' "$*" | tee -a "$OUT/session.log"; }
screen_size(){ adb shell wm size | tr -d '\r' | tail -1 | sed 's/.*: //'; }
foreground(){ adb shell dumpsys activity activities 2>/dev/null | grep -Eq "mResumedActivity:.*${PKG}|topResumedActivity=.*${PKG}"; }
ensure_app(){ foreground && return 0; log "Backroom lost foreground; relaunching"; adb shell am start -W -n "$ACTIVITY" >>"$OUT/relaunch.txt" 2>&1 || true; sleep 3; foreground; }

tap_pct(){
  local xp="$1" yp="$2" size w h
  size="$(screen_size)"; w="${size%x*}"; h="${size#*x}"
  adb shell input tap $((w*xp/100)) $((h*yp/100)) >/dev/null 2>&1 || true
  sleep 1
}

refresh_xml(){
  ensure_app || true
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/window.xml "$OUT/current.xml" >/dev/null 2>&1 || true
}

node_center(){
  local needle="$1"
  python3 - "$OUT/current.xml" "$needle" <<'PY'
import re,sys,xml.etree.ElementTree as ET
path,needle=sys.argv[1:]
try: root=ET.parse(path).getroot()
except Exception: raise SystemExit(1)
needle=needle.casefold()
for n in root.iter('node'):
    value=((n.attrib.get('text') or '')+' '+(n.attrib.get('content-desc') or '')).casefold()
    if needle not in value: continue
    m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',n.attrib.get('bounds',''))
    if not m: continue
    x1,y1,x2,y2=map(int,m.groups())
    if x2>x1 and y2>y1:
        print((x1+x2)//2,(y1+y2)//2); raise SystemExit(0)
raise SystemExit(1)
PY
}

tap_text(){
  local needle="$1" c
  refresh_xml
  c="$(node_center "$needle" 2>/dev/null || true)"
  [ -n "$c" ] || return 1
  log "tap text: $needle @ $c"
  adb shell input tap $c >/dev/null 2>&1 || true
  sleep 1
}

capture(){
  local name="$1"
  ensure_app || true
  adb exec-out screencap -p >"$OUT/${name}.png" 2>/dev/null || true
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/window.xml "$OUT/${name}.xml" >/dev/null 2>&1 || true
}

current_turn(){
  refresh_xml
  python3 - "$OUT/current.xml" <<'PY'
import re,sys,xml.etree.ElementTree as ET
try: root=ET.parse(sys.argv[1]).getroot()
except Exception: print(0); raise SystemExit
texts=[(n.attrib.get('text') or '').strip() for n in root.iter('node')]
for i,t in enumerate(texts):
    m=re.search(r'\bTURN\s*(\d+)\b',t,re.I)
    if m: print(int(m.group(1))); raise SystemExit
    if t.upper()=='TURN':
        for u in texts[i+1:i+5]:
            if u.isdigit(): print(int(u)); raise SystemExit
print(0)
PY
}

dismiss_immersive_hint(){
  refresh_xml
  if node_center "Got it" >/dev/null 2>&1; then
    tap_text "Got it" || true
    sleep 2
    log "dismissed Android immersive-mode education overlay"
  fi
}

maybe_start_combat(){
  if tap_text "BẮT ĐẦU COMBAT" || tap_text "START BATTLE"; then
    log "pending combat popup detected and started"
    return 0
  fi
  return 1
}

wait_for_turn_change(){
  local before="$1" deadline now
  deadline=$(( $(date +%s) + 55 ))
  while [ "$(date +%s)" -lt "$deadline" ]; do
    sleep 4
    ensure_app || true
    if maybe_start_combat; then
      sleep 26
      continue
    fi
    now="$(current_turn)"
    if [ "$now" -gt "$before" ] 2>/dev/null; then
      log "turn advanced: $before -> $now"
      return 0
    fi
  done
  now="$(current_turn)"
  log "WARNING: turn did not advance within 55s (before=$before now=$now)"
  return 1
}

submit_typed_action(){
  local text="$1" before
  before="$(current_turn)"
  log "submit typed action at turn $before: $text"
  # Focus the composer. Fixed geometry is intentional because Android WebView children are not
  # consistently exposed through UIAutomator until after IME interaction.
  tap_pct 50 80
  adb shell input text "${text// /%s}" >/dev/null 2>&1 || true
  sleep 1
  # Hide IME before tapping the in-page action button. Otherwise UIAutomator reports stale WebView
  # bounds underneath the keyboard and the tap is consumed by Gboard rather than the game.
  adb shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
  sleep 2
  tap_pct 25 90
  wait_for_turn_change "$before"
}

submit_explore(){
  local before
  before="$(current_turn)"
  log "submit Explore at turn $before"
  adb shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
  tap_pct 75 90
  wait_for_turn_change "$before"
}

log "install verified release APK: $APK"
adb install -r "$APK" | tee "$OUT/install.txt"
adb logcat -c || true
adb shell am force-stop "$PKG" || true
adb shell am start -W -n "$ACTIVITY" >"$OUT/launch.txt" 2>&1 || true
sleep 8
foreground || { log "FATAL: Backroom did not launch"; exit 12; }
dismiss_immersive_hint
capture "00-game-before-first-turn"

# Inspect information before the first gameplay action.
tap_pct 75 97
sleep 2
capture "01-info-before-first-turn"
tap_pct 25 97
sleep 2
capture "02-game-before-first-turn"
initial_turn="$(current_turn)"
log "first actionable render reports turn=$initial_turn"

start_epoch=$(date +%s)
end_epoch=$((start_epoch+600))
actions=(
  "Look around carefully"
  "Move forward slowly"
  "Inspect the corridor"
  "Listen for movement"
  "Check the walls for exits"
  "Continue forward"
  "Search for useful supplies"
  "Inspect the next passage"
)
idx=0
shot=3

while [ "$(date +%s)" -lt "$end_epoch" ]; do
  ensure_app || { log "FATAL: app could not be restored"; break; }
  if maybe_start_combat; then
    sleep 28
    capture "$(printf '%02d' "$shot")-combat"
    shot=$((shot+1))
    continue
  fi
  if [ $((idx%3)) -eq 2 ]; then
    submit_explore || true
  else
    submit_typed_action "${actions[$((idx%${#actions[@]}))]}" || true
  fi
  idx=$((idx+1))
  capture "$(printf '%02d' "$shot")-settled"
  shot=$((shot+1))
  sleep 3
done

log "10-minute gameplay window complete"
adb shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
capture "99-final"
adb shell dumpsys activity top >"$OUT/dumpsys-activity-top.txt" 2>&1 || true
adb shell dumpsys meminfo "$PKG" >"$OUT/dumpsys-meminfo.txt" 2>&1 || true
adb logcat -d -v threadtime >"$OUT/logcat-full.txt" 2>&1 || true
pid="$(adb shell pidof "$PKG" | tr -d '\r' | awk '{print $1}')"
if [ -n "$pid" ]; then adb logcat --pid="$pid" -d -v threadtime >"$OUT/logcat-app.txt" 2>&1 || true; else : >"$OUT/logcat-app.txt"; fi
grep -Ei "FATAL EXCEPTION|ANR in|AndroidRuntime|chromium.*(ERROR|CONSOLE)|Uncaught (TypeError|ReferenceError|RangeError)|net::ERR_|SIGSEGV|OutOfMemoryError" "$OUT/logcat-app.txt" >"$OUT/logcat-suspects.txt" || true

python3 - "$OUT" <<'PY'
from pathlib import Path
import re,sys,xml.etree.ElementTree as ET
out=Path(sys.argv[1]); rows=[]; turns=[]
for xml in sorted(out.glob('*.xml')):
    try: root=ET.parse(xml).getroot()
    except Exception: continue
    texts=[]
    for n in root.iter('node'):
        t=(n.attrib.get('text') or '').strip()
        if t and t not in texts: texts.append(t)
    joined='\n'.join(texts)
    rows.append(f"## {xml.name}\n{joined}")
    for m in re.finditer(r'\bTURN\s*(\d+)\b',joined,re.I): turns.append(int(m.group(1)))
(out/'ui-text-timeline.txt').write_text('\n\n'.join(rows),encoding='utf-8')
sus=(out/'logcat-suspects.txt').read_text(encoding='utf-8',errors='ignore') if (out/'logcat-suspects.txt').exists() else ''
summary=[
 f"screenshots={len(list(out.glob('*.png')))}",
 f"ui_dumps={len(list(out.glob('*.xml')))}",
 f"suspect_app_log_lines={len([x for x in sus.splitlines() if x.strip()])}",
 f"max_turn_seen={max(turns) if turns else 0}",
]
(out/'qa-summary.txt').write_text('\n'.join(summary)+'\n',encoding='utf-8')
PY
cat "$OUT/qa-summary.txt"
