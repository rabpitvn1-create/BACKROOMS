from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return source.replace(old, new, 1)


# entityEncounterKey is intentionally allowed to become an empty string when an encounter ends.
# The old renderer returned immediately whenever the key existed, even when empty, which prevented
# the Jeff/Jane/entityRegistry fallbacks from ever being considered after the first cleared encounter.
old_direct = "if(Object.prototype.hasOwnProperty.call(f,'entityEncounterKey'))return normalizeEntityId(f.entityEncounterKey);"
new_direct = "var direct=Object.prototype.hasOwnProperty.call(f,'entityEncounterKey')?normalizeEntityId(f.entityEncounterKey):'';if(direct)return direct;"
if new_direct not in text:
    text = replace_once(text, old_direct, new_direct, "Entity active-id empty-key fallback")

# The Entity PNG is absolutely positioned. The Snapshot container must establish the positioning
# context or the PNG can render relative to the page instead of inside the Snapshot frame.
old_box = "function appendEntityOverlay(){var box=document.getElementById('snapshot');if(!box)return;var old=box.querySelector('.snapshot-entity');"
new_box = "function appendEntityOverlay(){var box=document.getElementById('snapshot');if(!box)return;box.style.position='relative';box.style.overflow='hidden';var old=box.querySelector('.snapshot-entity');"
if new_box not in text:
    text = replace_once(text, old_box, new_box, "Entity Snapshot positioning context")

# Do not depend on the language model remembering to write entityEncounterKey. The encounter roll is
# authoritative. Persist the selected local Entity ID into the candidate before Game State Core saves
# it, so the WebView always receives a deterministic visual-presence key for the current encounter.
helper = r'''  private void forceEntityEncounterFlag(JSONObject candidateState, JSONObject rolls) throws Exception {
    if (candidateState == null || rolls == null) return;
    String entityId = "";
    JSONObject normal = rolls.optJSONObject("entityEncounter");
    if (normal != null && normal.optBoolean("success", false)) {
      entityId = rolls.optString("roamingEntityId", "").trim();
    }
    JSONObject jeff = rolls.optJSONObject("jeffEncounter");
    if (jeff != null && jeff.optBoolean("success", false)) entityId = "ENT-R01";
    JSONObject jane = rolls.optJSONObject("janeEncounter");
    if (jane != null && jane.optBoolean("success", false)) entityId = "ENT-R02";
    if (entityId.isEmpty()) return;
    JSONObject flags = candidateState.optJSONObject("flags");
    if (flags == null) {
      flags = new JSONObject();
      candidateState.put("flags", flags);
    }
    flags.put("entityEncounterKey", normalizedEntityId(entityId));
  }

'''
if "private void forceEntityEncounterFlag(JSONObject candidateState, JSONObject rolls)" not in text:
    anchor = "  private JSONObject resolveEntityOverlay(String rawEntityId) throws Exception {\n"
    if anchor not in text:
        raise RuntimeError("Entity overlay resolver anchor missing")
    text = text.replace(anchor, helper + anchor, 1)

call = "          forceEntityEncounterFlag(candidateState, rolls);\n"
if call not in text:
    anchors = [
        "          JSONObject coreCommit = new JSONObject(requireGameCore().processValidatedCandidate(before.toString(), candidateState.toString(), action));\n",
        "          JSONObject coreCommit = new JSONObject(gameCore.processValidatedCandidate(before.toString(), candidateState.toString(), action));\n",
    ]
    for anchor in anchors:
        if anchor in text:
            text = text.replace(anchor, call + anchor, 1)
            break
    else:
        raise RuntimeError("Validated candidate commit anchor missing for deterministic Entity presence")

for marker in (
    "file:///android_asset/entity/",
    "private void forceEntityEncounterFlag(JSONObject candidateState, JSONObject rolls)",
    "forceEntityEncounterFlag(candidateState, rolls);",
    "flags.put(\"entityEncounterKey\", normalizedEntityId(entityId));",
    "box.style.position='relative';box.style.overflow='hidden'",
    "var direct=Object.prototype.hasOwnProperty.call(f,'entityEncounterKey')?normalizeEntityId(f.entityEncounterKey):'';if(direct)return direct;",
    "window.backroomEntityOverlay=function(payload)",
    "@JavascriptInterface public void requestEntityOverlay(String entityId)",
):
    if marker not in text:
        raise RuntimeError("Entity runtime hotfix contract missing: " + marker)

MAIN.write_text(text, encoding="utf-8")
print("Entity overlay hotfix applied: deterministic encounter ID, Snapshot-relative PNG positioning, and empty-key fallback repair.")
