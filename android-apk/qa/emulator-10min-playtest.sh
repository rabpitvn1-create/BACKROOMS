#!/usr/bin/env bash
set -uo pipefail

APK="${APK_PATH:-Backroom-1.1.49.5.apk}"
OUT="${QA_OUT:-qa-artifacts}"
PKG="com.rabpit.backroom"
ACTIVITY="${PKG}/.MainActivity"
mkdir -p "$OUT"

log(){ printf '[qa] %s\n' "$*" | tee -a "$OUT/session.log"; }

screen_size(){ adb shell wm size | tr -d '\r' | tail -1 | sed 's/.*: //'; }

is_app_foreground(){
  adb shell dumpsys activity activities 2>/dev/null | grep -Eq "mResumedActivity:.*${PKG}|topResumedActivity=.*${PKG}"
}

ensure_app(){
  if is_app_foreground; then return 0; fi
  log "Backroom not foreground; relaunching"
  adb shell am start -W -n "$ACTIVITY" >> "$OUT/relaunch.txt" 2>&1 || true
  sleep 3
  is_app_foreground
}

capture(){
  local name="$1"
  ensure_app || true
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
  ensure_app || true
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

fallback_tap(){
  local xpc="$1" ypc="$2" size w h
  size="$(screen_size)"; w="${size%x*}"; h="${size#*x}"
  adb shell input tap $((w*xpc/100)) $((h*ypc/100)) >/dev/null 2>&1 || true
  sleep 1
}

enter_action(){
  local text="$1"
  ensure_app || return 1
  if ! click_class "EditText"; then
    log "EditText not exposed; fallback tap composer"
    fallback_tap 50 80
  fi
  adb shell input keyevent KEYCODE_MOVE_END >/dev/null 2>&1 || true
  adb shell input keyevent --longpress KEYCODE_DEL >/dev/null 2>&1 || true
  adb shell input text "${text// /%s}" >/dev/null 2>&1 || true
  sleep 1
  if ! click_text "THỰC HIỆN"; then
    log "THỰC HIỆN not exposed; fallback tap left action button"
    fallback_tap 25 90
  fi
}

submit_explore(){
  if click_text "KHÁM PHÁ"; then return 0; fi
  log "KHÁM PHÁ not exposed; fallback tap right action button"
  fallback_tap 75 90
}

maybe_start_combat(){
  if click_text "BẮT ĐẦU COMBAT" || click_text "START BATTLE"; then
    log "pending combat detected and started via accessibility"
    return 0
  fi
  return 1
}

probe_modal_fallback(){
  # WebView DOM is often absent from uiautomator. A pending-combat modal is centered and owns the
  # screen; tapping its primary-button zone is harmless to the story when no modal is present.
  log "probe centered Start Combat button zone"
  fallback_tap 50 61
}

log "install released APK: $APK"
adb install -r "$APK" | tee "$OUT/install.txt"
adb logcat -c || true
adb shell am force-stop "$PKG" || true
adb shell am start -W -n "$ACTIVITY" > "$OUT/launch.txt" 2>&1 || true
sleep 8
if ! is_app_foreground; then
  log "FATAL QA HARNESS: Backroom failed to become foreground after explicit component launch"
  adb shell dumpsys activity activities > "$OUT/launch-failure-dumpsys.txt" 2>&1 || true
  capture "00-launch-failure"
  exit 12
fi
log "Backroom launched and is foreground"
capture "00-launch"

# Verify the first-render information before any gameplay action. Accessibility is optional; use
# fixed bottom-nav geometry as fallback because the app itself owns a deterministic two-tab layout.
if click_text "THÔNG TIN"; then
  log "opened Information via accessibility"
else
  log "THÔNG TIN not exposed; fallback tap right bottom nav"
  fallback_tap 75 97
fi
sleep 2
capture "01-info-before-first-turn"
if ! click_text "GAME"; then fallback_tap 25 97; fi
sleep 1
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
  ensure_app || { log "app lost foreground and could not relaunch"; break; }

  if maybe_start_combat; then
    sleep 18
    capture "$(printf '%02d' "$shot")-combat"
    shot=$((shot+1))
    sleep 20
    continue
  fi

  if [ $((idx % 3)) -eq 2 ]; then
    log "submit exploration action $idx"
    submit_explore
  else
    action="${actions[$((idx % ${#actions[@]}))]}"
    log "submit action $idx: $action"
    enter_action "$action"
  fi
  idx=$((idx+1))

  sleep 12
  capture "$(printf '%02d' "$shot")-after-submit"
  shot=$((shot+1))

  if ! maybe_start_combat; then probe_modal_fallback; fi
  sleep 22
  capture "$(printf '%02d' "$shot")-settled"
  shot=$((shot+1))
done

log "10-minute play window complete"
capture "99-final"
adb shell dumpsys activity top > "$OUT/dumpsys-activity-top.txt" 2>&1 || true
adb shell dumpsys meminfo "$PKG" > "$OUT/dumpsys-meminfo.txt" 2>&1 || true
adb logcat -d -v threadtime > "$OUT/logcat-full.txt" 2>&1 || true
pid="$(adb shell pidof "$PKG" | tr -d '\r' | awk '{print $1}')"
if [ -n "$pid" ]; then adb logcat --pid="$pid" -d -v threadtime > "$OUT/logcat-app.txt" 2>&1 || true; else : > "$OUT/logcat-app.txt"; fi

grep -Ei "FATAL EXCEPTION|ANR in|AndroidRuntime|chromium.*(ERROR|CONSOLE)|Uncaught (TypeError|ReferenceError|RangeError)|net::ERR_|SIGSEGV|OutOfMemoryError|WebView" "$OUT/logcat-app.txt" > "$OUT/logcat-suspects.txt" || true

python3 - "$OUT" <<'PY'
from pathlib import Path
import sys,xml.etree.ElementTree as ET
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
summary=[
    f"screenshots={len(list(out.glob('*.png')))}",
    f"ui_dumps={len(list(out.glob('*.xml')))}",
    f"suspect_app_log_lines={len([x for x in sus.splitlines() if x.strip()])}",
]
(out/'qa-summary.txt').write_text('\n'.join(summary)+'\n',encoding='utf-8')
PY
cat "$OUT/qa-summary.txt"
exit 0
