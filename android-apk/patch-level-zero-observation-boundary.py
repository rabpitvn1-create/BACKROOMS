from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"


def replace_once(text, old, new):
    assert text.count(old) == 1, old
    return text.replace(old, new, 1)


main = MAIN.read_text(encoding="utf-8")
# Check the final writer/repair reply against the accepted area, after route resolution.
# A writer may repeat the correct JSON location while describing an entirely different place.
anchor = '          state.put("canonVersion", DRIVE_CANON_VERSION);\n'
main = replace_once(main, anchor, '''          if (com.rabpit.backroom.core.LevelNarrativePolicy.contradictsArea(currentStoryAreaId(state), reply)) {
            reply = "Quanh bạn vẫn là giấy tường vàng, thảm ẩm và tiếng đèn huỳnh quang. Lối đi chưa có thay đổi nào khác.";
          }
''' + anchor)
MAIN.write_text(main, encoding="utf-8")

facade = FACADE.read_text(encoding="utf-8")
start = facade.index("    val evidenceHighlights = linkedSetOf<String>()")
end = facade.index("    if (result.evidenceIds.isNotEmpty())", start)
facade = facade[:start] + "    val surfacedEvidence = JSONArray()\n" + facade[end:]
facade = replace_once(facade, "          evidenceHighlights.add(visible)\n", "")
start = facade.index("    val highlightArray = JSONArray()")
end = facade.index("    logger.log(PipelineLogEvent(", start)
facade = facade[:start] + '''    // Remove the old UI ledger on continued saves; discoveries remain in LevelInstance.
    output.optJSONObject("flags")?.remove("evidenceHighlights")
''' + facade[end:]
FACADE.write_text(facade, encoding="utf-8")

html = (ROOT / "app/src/main/assets/index.html").read_text(encoding="utf-8")
assert "rpg-evidence-badge" not in html
assert "evidenceHighlightStyle" not in html
assert "registered_narrative_area_scenery_mismatch" in main
print("Level 0 observation boundary applied: plain observations, persisted discoveries, checked final scenery.")
