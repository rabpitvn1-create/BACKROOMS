from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
CSS = ROOT / "app/src/main/assets/combat-overlay-feedback.css"

main = MAIN.read_text(encoding="utf-8")
css = CSS.read_text(encoding="utf-8")

for marker in (
    "DEBUG_LOG_EXPORT_REQUEST = 311",
    "DEBUG_EVENT_LIMIT = 80",
    "Intent.ACTION_CREATE_DOCUMENT",
    "Intent.CATEGORY_OPENABLE",
    'intent.setType("text/plain")',
    'intent.putExtra(Intent.EXTRA_TITLE, fileName)',
    "BuildConfig.VERSION_NAME",
    "BuildConfig.VERSION_CODE",
    "BuildConfig.HAKU_API_KEY",
    "BuildConfig.LUNA_API_KEY",
    "BuildConfig.GEMINI_API_KEY_1",
    "BuildConfig.GEMINI_API_KEY_5",
    "sanitizeDebugText(contextJson",
    'safe.replace(secret, "[REDACTED]")',
    'Bearer [REDACTED]',
    "function debugLastAction()",
    "function debugPartySummary()",
    "function debugLogContext()",
    "recentLog:logs",
    "provider:{selected:String(window.__backroomProvider||'UNKNOWN')",
    "combat:c?{active:true",
    "@JavascriptInterface public void requestDebugLog(String contextJson)",
    "@JavascriptInterface public void recordDebugEvent(String kind, String detail)",
    '"backroomProvider".equals(function) || function.endsWith("Error")',
    "b.id='debugLogButton'",
    "b.textContent='Xuất log TXT'",
):
    assert marker in main, marker

assert "b.id='snapshotButton'" not in main
assert "b.textContent='Snapshot chưa cấu hình'" not in main

shadow_rule = '.snapshot[data-combat-active="true"]>.snapshot-character-shadow{bottom:calc(28px + 1.5%)!important}'
assert shadow_rule in css, shadow_rule

# Exploration geometry remains owned by the existing base rule. The combat override
# must only add the same 28px floor offset used by the combat actor/nameplate layout.
assert ".snapshot .snapshot-character-shadow{" in main
assert "bottom:1.5%" in main
assert '.snapshot .combat-fx-layer .snapshot-party-actor{position:absolute;right:0;bottom:28px' in css

print("Issue #311 contracts verified: combat shadow follows the 28px combat floor and TXT debug export is bounded/redacted.")
