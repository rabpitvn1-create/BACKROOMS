from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"

if not MAIN.is_file():
    raise SystemExit("generated MainActivity.java missing; run ci_apply_runtime_patches.py first")

text = MAIN.read_text(encoding="utf-8")

required = (
    'boolean exploreAction = "EXPLORE".equals(actionKindNormalized);',
    'boolean entityEncounterAction = exploreAction;',
    'thresholdRoll("entityEncounter", 10000, entityThresholds[level], entityEncounterAction && entityAllowed',
    'thresholdRoll("luciaEncounter", 10000, 5000, exploreAction && level == 0',
    'SEARCH không được khởi tạo encounter Entity mới',
    'đây là action duy nhất được phép kích hoạt roll encounter Entity mới',
    'không tự đổi mục tiêu và không khởi tạo encounter Entity mới.',
)
for marker in required:
    if marker not in text:
        raise SystemExit("encounter action authority missing: " + marker)

forbidden = (
    'boolean entityEncounterAction = exploreAction || "SEARCH".equals(actionKindNormalized) || "EXECUTE".equals(actionKindNormalized);',
    'SEARCH vẫn roll entityEncounter theo tỷ lệ Level và có thể khởi tạo roaming Entity mới;',
    'EXECUTE vẫn roll Entity và có thể khởi tạo roaming encounter mới.',
)
for marker in forbidden:
    if marker in text:
        raise SystemExit("unsafe all-action encounter authority survived: " + marker)

print("Encounter action authority verified: dialogue/EXECUTE and SEARCH cannot spawn new roaming Entities; EXPLORE remains authoritative, including Lucia first contact.")
