#!/usr/bin/env bash
set -uo pipefail

APK="${APK_PATH:-Backroom-1.1.49.5.apk}"
OUT="${QA_OUT:-qa-artifacts}"
PKG="com.rabpit.backroom"
ACTIVITY="com.rabpit.backroom.MainActivity"
mkdir -p "$OUT"

log(){ printf '[qa] %s\n' "$*" | tee -a "$OUT/session.log"; }

capture(){
  local name="$1"
  adb exec-out screencap -p > "$OUT/${name}.png" 2>/dev/null || true
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/window.xml "$OUT/${name}.xml" >/dev/null 2>&1 || true
}

node_center(){
  local needle="$1" mode="${2:-text}"
  python3 - "$OUT/current.xml" "$needle" "$mode" <<'PY'
import re,sys,xml.etree.ElementTree as ET
path,needle,mode=sys.argv[1:]
try: root=ET.parse(path).getroot()
except Exception: raise SystemExit(1)
needle=needle.casefold()
for node in root.iter('node'):
    value=(node.attrib.get('text','')+' '+node.attrib.get('content-desc','')).casefold()
    cls=node.attrib.get('class','')
    ok=(needle in value) if mode=='text' else (mode=='class' and needle in cls.casefold())
    if not ok: continue
    m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',node.attrib.get('bounds',''))
    if not m: continue
    x1,y1,x2,y2=map(int,m.groups())
    if x2<=x1 or y2<=y1: continue
    print((x1+x2)//2,(y1+y2)//2)
    raise SystemExit(0)
raise SystemExit(1)
PY
}

refresh_xml(){
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/window.xml "$OUT/current.xml" >/dev/null 2>&1 || true
}

click_text(){
  local needle="$1" coords
  refresh_xml
  coords="$(node_center "$needle" text 2>/dev/null || true)"
  if [ -z "$coords" ]; then return 1; fi
  log "tap text: $needle @ $coords"
  adb shell input tap $coords >/dev/null 2>&1 || true
  sleep 1
  return 0
}

click_class(){
  local cls="$1" coords
  refresh_xml
  coords="$(node_center "$cls" class 2>/dev/null || true)"
  if [ -z "$coords" ]; then return 1; fi
  log "tap class: $cls @ $coords"
  adb shell input tap $coords >/dev/null 2>&1 || true
  sleep 1
  return 0
}

enter_action(){
  local text="$1"
  if ! click_class "EditText"; then
    local size w h
    size="$(adb shell wm size | tr -d '\r' | tail -1 | sed 's/.*: //')"
    w="${size%x*}"; h="${size#*x}"
    log "EditText not exposed; fallback tap composer"
    adb shell input tap $((w/2)) $((h*78/100)) >/dev/null 2>&1 || true
  fi
  adb shell input keyevent KEYCODE_CTRL_A >/dev/null 2>&1 || true
  adb shell input text "${text// /%s}" >/dev/null 2>&1 || true
  sleep 1
  if ! click_text "THỰC HIỆN"; then
    local size w h
    size="$(adb shell wm size | tr -d '\r' | tail -1 | sed 's/.*: //')"
    w="${size%x*}"; h="${size#*x}"
    log "THỰC HIỆN not exposed; fallback tap left action button"
    adb shell input tap $((w*25/100)) $((h*88/100)) >/dev/null 2>&1 || true
  fi
}

maybe_start_combat(){
  if click_text "BẮT ĐẦU COMBAT"; then
    log "pending combat detected and started"
    return 0
  fi
  if click_text "START BATTLE"; then
    log "pending combat detected and started"
    return 0
  fi
  return 1
}

log "install released APK: $APK"
adb install -r "$APK" | tee "$OUT/install.txt"
adb logcat -c || true
adb shell am force-stop "$PKG" || true
adb shell am start -n "$ACTIVITY" | tee "$OUT/launch.txt"
sleep 8
capture "00-launch"

# Verify the very first render, before any gameplay action, including the Information page.
if click_text "THÔNG TIN"; then
  sleep 2
  capture "01-info-before-first-turn"
  click_text "GAME" || true
  sleep 1
fi
capture "02-game-before-first-turn"

start_epoch=$(date +%s)
end_epoch=$((start_epoch + 600))
actions=(
  "Look around carefully"
  "Move forward slowly"
  "Search the nearby room"
  "Inspect the corridor"
  "Listen for movement"
  "Explore the area"
  "Check the walls for exits"
  "Continue forward"
  "Search for useful supplies"
  "Inspect the next passage"
)
idx=0
shot=3

while [ "$(date +%s)" -lt "$end_epoch" ]; do
  if maybe_start_combat; then
    sleep 18
    capture "$(printf '%02d' "$shot")-combat"
    shot=$((shot+1))
    # Combat playback may still be active. Give it time and keep observing.
    sleep 20
    continue
  fi

  # Prefer the dedicated exploration button every third action to exercise both input paths.
  if [ $((idx % 3)) -eq 2 ] && click_text "KHÁM PHÁ"; then
    log "submitted exploration action"
  else
    action="${actions[$((idx % ${#actions[@]}))]}"
    log "submit action $idx: $action"
    enter_action "$action"
  fi
  idx=$((idx+1))

  # Model/provider path is asynchronous. Observe intermediate and completed UI states.
  sleep 12
  capture "$(printf '%02d' "$shot")-after-submit"
  shot=$((shot+1))
  maybe_start_combat || true
  sleep 22
  capture "$(printf '%02d' "$shot")-settled"
  shot=$((shot+1))

done

log "10-minute play window complete"
capture "99-final"
adb shell dumpsys activity top > "$OUT/dumpsys-activity-top.txt" 2>&1 || true
adb shell dumpsys meminfo "$PKG" > "$OUT/dumpsys-meminfo.txt" 2>&1 || true
adb logcat -d -v threadtime > "$OUT/logcat-full.txt" 2>&1 || true

grep -Ei "FATAL EXCEPTION|ANR in|AndroidRuntime|chromium.*(ERROR|CONSOLE)|Uncaught (TypeError|ReferenceError|RangeError)|net::ERR_|SIGSEGV|OutOfMemoryError|StrictMode" "$OUT/logcat-full.txt" > "$OUT/logcat-suspects.txt" || true

python3 - "$OUT" <<'PY'
from pathlib import Path
import re,sys,xml.etree.ElementTree as ET
out=Path(sys.argv[1])
rows=[]
for xml in sorted(out.glob('*.xml')):
    try: root=ET.parse(xml).getroot()
    except Exception: continue
    texts=[]
    for n in root.iter('node'):
        t=(n.attrib.get('text') or '').strip()
        if t and t not in texts: texts.append(t)
    rows.append(f"## {xml.name}\n"+'\n'.join(texts[:120]))
(out/'ui-text-timeline.txt').write_text('\n\n'.join(rows),encoding='utf-8')

sus=(out/'logcat-suspects.txt').read_text(encoding='utf-8',errors='ignore') if (out/'logcat-suspects.txt').exists() else ''
summary=[]
summary.append(f"screenshots={len(list(out.glob('*.png')))}")
summary.append(f"ui_dumps={len(list(out.glob('*.xml')))}")
summary.append(f"suspect_log_lines={len([x for x in sus.splitlines() if x.strip()])}")
(out/'qa-summary.txt').write_text('\n'.join(summary)+'\n',encoding='utf-8')
PY

cat "$OUT/qa-summary.txt"
exit 0
