#!/usr/bin/env bash
set -u

OUT="${GITHUB_WORKSPACE}/emu-audit"
mkdir -p "$OUT"
SUMMARY="$OUT/summary.txt"
: > "$SUMMARY"

log() {
  printf '%s\n' "$*" | tee -a "$SUMMARY"
}

APK="${GITHUB_WORKSPACE}/android-apk/app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK" ]; then
  log "FATAL: APK not found at $APK"
  exit 0
fi

log "EMU_AUDIT_START $(date -u +%FT%TZ)"
log "APK=$APK"
timeout 60s adb install -r "$APK" >>"$SUMMARY" 2>&1 || log "ANOMALY install_failed_or_timed_out"
timeout 10s adb shell pm clear com.rabpit.backroom >>"$SUMMARY" 2>&1 || true
timeout 10s adb logcat -c || true
adb logcat -v threadtime > "$OUT/logcat-full.txt" 2>&1 &
LOGCAT_PID=$!

timeout 20s adb shell am start -W -n com.rabpit.backroom/.MainActivity >>"$SUMMARY" 2>&1 || log "ANOMALY launch_failed_or_timed_out"
sleep 8

screen_size="$(timeout 5s adb shell wm size 2>/dev/null | tr -d '\r' | tail -1 || true)"
log "SCREEN=$screen_size"

dump_ui() {
  local label="$1"
  timeout 8s adb exec-out screencap -p > "$OUT/${label}.png" 2>/dev/null || true
  timeout 6s adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  timeout 6s adb pull /sdcard/window.xml "$OUT/${label}.xml" >/dev/null 2>&1 || true
  if [ ! -s "$OUT/${label}.xml" ]; then
    printf '<hierarchy/>\n' > "$OUT/${label}.xml"
    log "${label} ANOMALY ui_dump_missing_or_timed_out"
  fi
}

scroll_log_down() {
  # Repeated upward swipes inside the central log area expose the latest GM choice buttons.
  for _ in 1 2 3 4; do
    timeout 4s adb shell input swipe 540 1450 540 650 180 >/dev/null 2>&1 || true
    sleep 0.2
  done
}

pick_candidate() {
  local xml="$1"
  local turn="$2"
  python3 - "$xml" "$turn" <<'PY'
import re, sys, xml.etree.ElementTree as ET
path=sys.argv[1]
turn=int(sys.argv[2])
try:
    root=ET.parse(path).getroot()
except Exception:
    print("NONE")
    raise SystemExit

nodes=[]
for n in root.iter("node"):
    if n.attrib.get("clickable") != "true" or n.attrib.get("enabled") != "true":
        continue
    text=(n.attrib.get("text") or n.attrib.get("content-desc") or "").strip()
    bounds=n.attrib.get("bounds","")
    m=re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
    if not m:
        continue
    x1,y1,x2,y2=map(int,m.groups())
    if x2<=x1 or y2<=y1:
        continue
    nodes.append((text,(x1+x2)//2,(y1+y2)//2,n.attrib.get("class",""),bounds))

exclude={
    "PLAYER ACTION","GAME MENU","Lưu","Tải","Bắt đầu lại từ đầu",
    "Xóa save trên máy","HỦY","THỰC HIỆN","×","☰"
}
usable=[x for x in nodes if x[0] and x[0] not in exclude]
preferred=[
    "Nhấn vào để bắt đầu khám phá thế giới Backrooms",
    "Tiếp tục lựa chọn đã lưu",
    "Thử lại phản hồi",
    "Tiếp tục qua ranh giới",
    "Tiếp tục cốt truyện",
    "Tấn công",
    "Mở Rương",
]
for p in preferred:
    for item in usable:
        if item[0] == p:
            print(f"PICK\t{item[1]}\t{item[2]}\t{item[0]}")
            raise SystemExit

# Normal GM choices are the remaining enabled WebView buttons. Prefer long player-facing text.
choices=[x for x in usable if len(x[0]) >= 4]
if not choices:
    print("NONE")
    raise SystemExit

# Sort top-to-bottom. Cycle across choices so the 20-turn audit does not always take button 1.
choices.sort(key=lambda x:(x[2],x[1]))
idx=(turn-1) % len(choices)
item=choices[idx]
print(f"PICK\t{item[1]}\t{item[2]}\t{item[0]}")
PY
}

report_xml() {
  local xml="$1"
  local label="$2"
  python3 - "$xml" "$label" >>"$SUMMARY" <<'PY'
import re, sys, xml.etree.ElementTree as ET
path,label=sys.argv[1],sys.argv[2]
try:
    root=ET.parse(path).getroot()
except Exception as e:
    print(f"{label} XML_PARSE_ERROR {e}")
    raise SystemExit
texts=[]
clicks=[]
for n in root.iter("node"):
    text=(n.attrib.get("text") or n.attrib.get("content-desc") or "").strip()
    if text:
        texts.append(text)
    if n.attrib.get("clickable")=="true" and n.attrib.get("enabled")=="true" and text:
        clicks.append(text)
errors=[t for t in texts if re.search(r"(?i)\b(lỗi|error|exception|failed|không tìm thấy|không thể|thử lại)\b",t)]
print(f"{label} ENABLED_CLICKABLES={clicks}")
if errors:
    print(f"{label} VISIBLE_ANOMALIES={errors}")
PY
}

dump_ui "turn-00-launch"
report_xml "$OUT/turn-00-launch.xml" "TURN00"

for turn in $(seq 1 20); do
  log ""
  log "===== INTERACTION $turn ====="
  candidate=""
  for poll in $(seq 1 8); do
    scroll_log_down
    dump_ui "turn-$(printf '%02d' "$turn")-poll-$(printf '%02d' "$poll")"
    xml="$OUT/turn-$(printf '%02d' "$turn")-poll-$(printf '%02d' "$poll").xml"
    report_xml "$xml" "TURN$(printf '%02d' "$turn")P$(printf '%02d' "$poll")"
    candidate="$(pick_candidate "$xml" "$turn" 2>/dev/null || true)"
    if [[ "$candidate" == PICK$'\t'* ]]; then
      break
    fi
    sleep 1
  done

  if [[ "$candidate" != PICK$'\t'* ]]; then
    log "INTERACTION $turn STALL no enabled gameplay button after polling"
    continue
  fi

  IFS=$'\t' read -r _ x y text <<<"$candidate"
  log "INTERACTION $turn TAP x=$x y=$y text=$text"
  timeout 4s adb shell input tap "$x" "$y" >/dev/null 2>&1 || log "INTERACTION $turn ANOMALY tap_failed_or_timed_out"
  sleep 2

  dump_ui "turn-$(printf '%02d' "$turn")-after"
  report_xml "$OUT/turn-$(printf '%02d' "$turn")-after.xml" "TURN$(printf '%02d' "$turn")-AFTER"

  # Record crash/error signatures without aborting the run.
  timeout 8s adb logcat -d -v brief 2>/dev/null | grep -E 'FATAL EXCEPTION|AndroidRuntime|BackroomMain|chromium.*(ERROR|crash)|IllegalStateException|IllegalArgumentException' | tail -n 30 >> "$SUMMARY" || true
done

dump_ui "turn-20-final"
report_xml "$OUT/turn-20-final.xml" "FINAL"
timeout 10s adb shell dumpsys activity activities > "$OUT/dumpsys-activity.txt" 2>&1 || true
timeout 10s adb shell dumpsys meminfo com.rabpit.backroom > "$OUT/dumpsys-meminfo.txt" 2>&1 || true
kill "$LOGCAT_PID" >/dev/null 2>&1 || true
wait "$LOGCAT_PID" 2>/dev/null || true

log ""
log "EMU_AUDIT_END $(date -u +%FT%TZ)"
log "Completed all 20 interaction slots; anomalies were recorded but never used to abort the loop."
exit 0
