from pathlib import Path

ROOT = Path(__file__).resolve().parent
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"
INDEX = ROOT / "app/src/main/assets/index.html"
MARKER = "EVIDENCE_HIGHLIGHT_V1"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


facade = FACADE.read_text(encoding="utf-8")
if 'outputFlags.put("evidenceHighlights", highlightArray)' not in facade:
    anchor = '''    val output = syncLegacy(legacy, result.state, incrementTurn = true)
    val reply = result.reply ?: if (result.progressed) "Môi trường đã thay đổi." else "Không có tiến triển mới."
    appendLog(output, action, reply)
'''
    replacement = '''    val output = syncLegacy(legacy, result.state, incrementTurn = true)
    val reply = result.reply ?: if (result.progressed) "Môi trường đã thay đổi." else "Không có tiến triển mới."

    // Only evidence that GenericLevelRuntime actually surfaced may reach this player-facing ledger.
    // Hidden/undiscovered evidence never enters result.evidenceIds and therefore cannot leak here.
    val evidenceHighlights = linkedSetOf<String>()
    legacy.optJSONObject("flags")?.optJSONArray("evidenceHighlights")?.let { existing ->
      for (index in 0 until existing.length()) {
        existing.optString(index, "").trim().takeIf(String::isNotEmpty)?.let(evidenceHighlights::add)
      }
    }
    if (result.evidenceIds.isNotEmpty()) {
      val instanceReplies = result.state.levelInstance?.replies.orEmpty()
      val definitionReplies = levelRegistry.require(levelId).replies
      result.evidenceIds.sorted().forEach { evidenceId ->
        val text = instanceReplies["evidence:$evidenceId"] ?: definitionReplies["evidence:$evidenceId"]
        text?.trim()?.takeIf(String::isNotEmpty)?.let(evidenceHighlights::add)
      }
    }
    val highlightArray = JSONArray()
    evidenceHighlights.toList().takeLast(256).forEach(highlightArray::put)
    val outputFlags = output.optJSONObject("flags") ?: JSONObject().also { output.put("flags", it) }
    outputFlags.put("evidenceHighlights", highlightArray)
    appendLog(output, action, reply)
'''
    facade = replace_once(facade, anchor, replacement, "registered evidence highlight ledger")
    FACADE.write_text(facade, encoding="utf-8")

# Evidence remains in Core's discovery ledger; ordinary prose must not be labelled as a clue.
# This patch no longer installs a badge, highlight stylesheet or transcript observer.
print("Evidence discovery ledger applied without player-facing clue highlighting.")
