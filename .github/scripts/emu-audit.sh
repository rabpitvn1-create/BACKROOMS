#!/usr/bin/env bash
set -u

OUT="${GITHUB_WORKSPACE}/emu-audit"
mkdir -p "$OUT"
SUMMARY="$OUT/narrative-summary.txt"
: > "$SUMMARY"

log() {
  printf '%s\n' "$*" | tee -a "$SUMMARY"
}

APK="${GITHUB_WORKSPACE}/android-apk/app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK" ]; then
  log "FATAL: APK not found at $APK"
  exit 0
fi

log "NARRATIVE_AUDIT_START $(date -u +%FT%TZ)"
log "TARGET_ROUNDS=50"

timeout 60s adb install -r "$APK" >>"$SUMMARY" 2>&1 || {
  log "FATAL: install failed or timed out"
  exit 0
}
timeout 10s adb shell pm clear com.rabpit.backroom >/dev/null 2>&1 || true
timeout 20s adb shell am start -W -n com.rabpit.backroom/.MainActivity >>"$SUMMARY" 2>&1 || {
  log "FATAL: launch failed or timed out"
  exit 0
}
sleep 8

dump_ui() {
  local label="$1"
  timeout 6s adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  timeout 6s adb pull /sdcard/window.xml "$OUT/${label}.xml" >/dev/null 2>&1 || true
  if [ ! -s "$OUT/${label}.xml" ]; then
    printf '<hierarchy/>\n' > "$OUT/${label}.xml"
  fi
}

scroll_log_down() {
  for _ in 1 2 3; do
    timeout 4s adb shell input swipe 540 1450 540 700 160 >/dev/null 2>&1 || true
    sleep 0.15
  done
}

point_for_text() {
  local xml="$1"
  local target="$2"
  python3 - "$xml" "$target" <<'PY'
import re, sys, xml.etree.ElementTree as ET
path,target=sys.argv[1],sys.argv[2]
try:
    root=ET.parse(path).getroot()
except Exception:
    raise SystemExit
for n in root.iter("node"):
    if n.attrib.get("enabled") != "true":
        continue
    text=(n.attrib.get("text") or n.attrib.get("content-desc") or "").strip()
    if text != target:
        continue
    m=re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.attrib.get("bounds",""))
    if not m:
        continue
    x1,y1,x2,y2=map(int,m.groups())
    if x2>x1 and y2>y1:
        print(f"{(x1+x2)//2}\t{(y1+y2)//2}")
        raise SystemExit
PY
}

editable_point() {
  local xml="$1"
  python3 - "$xml" <<'PY'
import re, sys, xml.etree.ElementTree as ET
try:
    root=ET.parse(sys.argv[1]).getroot()
except Exception:
    raise SystemExit
candidates=[]
for n in root.iter("node"):
    if n.attrib.get("enabled") != "true":
        continue
    cls=n.attrib.get("class","")
    text=(n.attrib.get("text") or n.attrib.get("content-desc") or "").strip()
    is_edit=("EditText" in cls or n.attrib.get("focusable")=="true" and
             ("Nhập hành động" in text or n.attrib.get("long-clickable")=="true"))
    if not is_edit:
        continue
    m=re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.attrib.get("bounds",""))
    if not m:
        continue
    x1,y1,x2,y2=map(int,m.groups())
    if x2>x1 and y2>y1:
        candidates.append(((x1+x2)//2,(y1+y2)//2))
if candidates:
    print(f"{candidates[0][0]}\t{candidates[0][1]}")
PY
}

pick_candidate() {
  local xml="$1"
  local round="$2"
  python3 - "$xml" "$round" <<'PY'
import re, sys, xml.etree.ElementTree as ET
path=sys.argv[1]
round_no=int(sys.argv[2])
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
    m=re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.attrib.get("bounds",""))
    if not m:
        continue
    x1,y1,x2,y2=map(int,m.groups())
    if x2<=x1 or y2<=y1:
        continue
    nodes.append((text,(x1+x2)//2,(y1+y2)//2))

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
    "ROLL",
    "FINISH",
]
for p in preferred:
    for item in usable:
        if item[0] == p:
            print(f"PICK\t{item[1]}\t{item[2]}\t{item[0]}")
            raise SystemExit

choices=[x for x in usable if len(x[0]) >= 3]
if not choices:
    print("NONE")
    raise SystemExit
choices.sort(key=lambda x:(x[2],x[1]))
item=choices[(round_no-1) % len(choices)]
print(f"PICK\t{item[1]}\t{item[2]}\t{item[0]}")
PY
}

report_narration() {
  local xml="$1"
  local label="$2"
  python3 - "$xml" "$label" >>"$SUMMARY" <<'PY'
import json, sys, xml.etree.ElementTree as ET
path,label=sys.argv[1],sys.argv[2]
try:
    root=ET.parse(path).getroot()
except Exception as e:
    print(f"{label} NARRATIVE_XML_ERROR={e}")
    raise SystemExit

chrome={
  "PLAYER ACTION","GAME MENU","Lưu","Tải","Bắt đầu lại từ đầu","Xóa save trên máy",
  "HỦY","THỰC HIỆN","×","☰","Cao Minh sẽ làm gì?"
}
texts=[]
seen=set()
for n in root.iter("node"):
    text=(n.attrib.get("text") or n.attrib.get("content-desc") or "").strip()
    if not text or text in chrome or text in seen:
        continue
    seen.add(text)
    texts.append(text)
print(f"{label} VISIBLE_TEXT=" + json.dumps(texts, ensure_ascii=False))
PY
}

submit_player_action() {
  local base_xml="$1"
  local round="$2"
  local action="$3"

  local point
  point="$(point_for_text "$base_xml" "PLAYER ACTION" 2>/dev/null || true)"
  [ -n "$point" ] || return 1

  local x y
  IFS=$'\t' read -r x y <<<"$point"
  timeout 4s adb shell input tap "$x" "$y" >/dev/null 2>&1 || return 1
  sleep 0.5

  dump_ui "round-$(printf '%02d' "$round")-action-open"
  local form_xml="$OUT/round-$(printf '%02d' "$round")-action-open.xml"
  local edit
  edit="$(editable_point "$form_xml" 2>/dev/null || true)"
  [ -n "$edit" ] || return 1

  IFS=$'\t' read -r x y <<<"$edit"
  timeout 4s adb shell input tap "$x" "$y" >/dev/null 2>&1 || return 1
  sleep 0.2
  timeout 6s adb shell input keyevent KEYCODE_MOVE_END >/dev/null 2>&1 || true
  local encoded
  encoded="$(printf '%s' "$action" | sed 's/ /%s/g')"
  timeout 8s adb shell input text "$encoded" >/dev/null 2>&1 || return 1
  sleep 0.4

  dump_ui "round-$(printf '%02d' "$round")-action-filled"
  local filled_xml="$OUT/round-$(printf '%02d' "$round")-action-filled.xml"
  point="$(point_for_text "$filled_xml" "THỰC HIỆN" 2>/dev/null || true)"
  [ -n "$point" ] || return 1
  IFS=$'\t' read -r x y <<<"$point"
  timeout 4s adb shell input tap "$x" "$y" >/dev/null 2>&1 || return 1
  log "ROUND $round PLAYER_ACTION=$action"
  return 0
}

ACTIONS=(
  "Cao Minh tha than thuc do xet khong gian xung quanh"
  "Cao Minh dung lai dieu tuc trong choc lat"
  "Cao Minh quan sat ky cac loi di phia truoc"
  "Cao Minh thu quay lai doan hanh lang vua di qua"
  "Cao Minh de Huyet Ma Kiem lo lung ben canh va tham do xung quanh"
  "Cao Minh dung than thuc tim diem bat thuong trong khong gian"
  "Cao Minh dung ma khi tham do buc tuong gan nhat"
  "Cao Minh dung yen lang nghe dong tinh phia truoc"
)

dump_ui "launch"
report_narration "$OUT/launch.xml" "LAUNCH"

for round in $(seq 1 50); do
  log ""
  log "===== ROUND $round ====="

  candidate=""
  ready_xml=""
  for poll in $(seq 1 16); do
    scroll_log_down
    dump_ui "round-$(printf '%02d' "$round")-ready-$(printf '%02d' "$poll")"
    xml="$OUT/round-$(printf '%02d' "$round")-ready-$(printf '%02d' "$poll").xml"
    ready_xml="$xml"
    candidate="$(pick_candidate "$xml" "$round" 2>/dev/null || true)"
    player_point="$(point_for_text "$xml" "PLAYER ACTION" 2>/dev/null || true)"
    if [ -n "$player_point" ] || [[ "$candidate" == PICK$'\t'* ]]; then
      break
    fi
    sleep 1
  done

  acted=0
  if [ $((round % 2)) -eq 1 ] && [ -n "${ready_xml:-}" ]; then
    action="${ACTIONS[$(((round-1)/2 % ${#ACTIONS[@]}))]}"
    if submit_player_action "$ready_xml" "$round" "$action"; then
      acted=1
    fi
  fi

  if [ "$acted" -eq 0 ]; then
    if [[ "$candidate" != PICK$'\t'* ]]; then
      log "ROUND $round STALL=no gameplay action available"
      report_narration "$ready_xml" "ROUND$(printf '%02d' "$round")-STALL"
      continue
    fi
    IFS=$'\t' read -r _ x y text <<<"$candidate"
    log "ROUND $round CHOICE=$text"
    timeout 4s adb shell input tap "$x" "$y" >/dev/null 2>&1 || {
      log "ROUND $round TAP_FAILED"
      continue
    }
  fi

  sleep 2
  final_xml=""
  for wait_no in $(seq 1 20); do
    dump_ui "round-$(printf '%02d' "$round")-wait-$(printf '%02d' "$wait_no")"
    final_xml="$OUT/round-$(printf '%02d' "$round")-wait-$(printf '%02d' "$wait_no").xml"
    if ! grep -q "Đang xử lý lượt" "$final_xml"; then
      next_choice="$(pick_candidate "$final_xml" "$((round+1))" 2>/dev/null || true)"
      next_player="$(point_for_text "$final_xml" "PLAYER ACTION" 2>/dev/null || true)"
      if [ -n "$next_player" ] || [[ "$next_choice" == PICK$'\t'* ]]; then
        break
      fi
    fi
    sleep 1
  done

  report_narration "$final_xml" "ROUND$(printf '%02d' "$round")"
done

dump_ui "round-50-final"
report_narration "$OUT/round-50-final.xml" "FINAL"

log ""
log "NARRATIVE_AUDIT_END $(date -u +%FT%TZ)"
log "Completed 50 emulator interaction rounds; output is limited to gameplay actions and visible GM/player narrative text."
exit 0
