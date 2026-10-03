#!/usr/bin/env bash
set -u

OUT="${GITHUB_WORKSPACE}/emu-audit"
mkdir -p "$OUT"
SUMMARY="$OUT/narrative-summary.txt"
AUDIT="$OUT/narrative-audit.jsonl"
: > "$SUMMARY"
: > "$AUDIT"

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
log "DRIVER=debug WebView bridge on Android Emulator API 29"

timeout 60s adb install -r "$APK" >>"$SUMMARY" 2>&1 || {
  log "FATAL: install failed or timed out"
  exit 0
}
timeout 10s adb shell pm clear com.rabpit.backroom >/dev/null 2>&1 || true
timeout 10s adb logcat -c >/dev/null 2>&1 || true
timeout 20s adb shell am start -W -n com.rabpit.backroom/.MainActivity --ez narrative_audit true >>"$SUMMARY" 2>&1 || true

done_flag=0
last_round=0
for tick in $(seq 1 300); do
  timeout 6s adb exec-out run-as com.rabpit.backroom cat files/narrative-audit.jsonl >"$AUDIT" 2>/dev/null || true
  if [ -s "$AUDIT" ]; then
    current_round=$(python3 - "$AUDIT" <<'PY'
import json,sys
m=0
for line in open(sys.argv[1],encoding='utf-8',errors='replace'):
    try:
        row=json.loads(line)
    except Exception:
        continue
    m=max(m,int(row.get('round',0) or 0))
print(m)
PY
)
    if [ "$current_round" != "$last_round" ]; then
      last_round="$current_round"
      log "PROGRESS_ROUND=$last_round"
    fi
    if grep -q '"done":true' "$AUDIT"; then
      done_flag=1
      break
    fi
  fi
  sleep 5
done

timeout 6s adb exec-out run-as com.rabpit.backroom cat files/narrative-audit.jsonl >"$AUDIT" 2>/dev/null || true

if [ "$done_flag" -eq 1 ]; then
  log "NARRATIVE_AUDIT_COMPLETE rounds=$last_round"
else
  log "NARRATIVE_AUDIT_TIMEOUT rounds=$last_round"
fi

python3 - "$AUDIT" >>"$SUMMARY" <<'PY'
import json,sys
rows=[]
for line in open(sys.argv[1],encoding='utf-8',errors='replace'):
    try:
        rows.append(json.loads(line))
    except Exception:
        pass
rounds=[r for r in rows if isinstance(r.get('round'),int) and 'gm' in r]
print("RECORDED_ROUNDS="+str(len(rounds)))
print("PLAYER_ACTION_ROUNDS="+str(sum(r.get('mode')=='PLAYER_ACTION' for r in rounds)))
print("CHOICE_ROUNDS="+str(sum(r.get('mode')=='CHOICE' for r in rounds)))
errors=[r for r in rows if r.get('type') in {'runtime_error','submit_error','combat_error'}]
print("RECORDED_ERRORS="+str(len(errors)))
for r in errors[:20]:
    print("ERROR="+json.dumps(r,ensure_ascii=False))
PY

log "NARRATIVE_AUDIT_END $(date -u +%FT%TZ)"
exit 0
