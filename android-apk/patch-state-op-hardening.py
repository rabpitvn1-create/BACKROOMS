from pathlib import Path


MAIN = Path(__file__).resolve().parent / "app/src/main/java/com/rabpit/backroom/MainActivity.java"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


text = MAIN.read_text(encoding="utf-8")

old = r'''        boolean allowedNew = acquisitionIntent(action);
        if (madGod && !before.optJSONObject("flags").optJSONObject("madGod").optBoolean("spawned", false)) allowedNew = false;
'''
new = r'''        boolean allowedNew = acquisitionIntent(action);
        JSONObject beforeFlagsForItem = before.optJSONObject("flags");
        JSONObject beforeMadGodForItem = beforeFlagsForItem != null ? beforeFlagsForItem.optJSONObject("madGod") : null;
        boolean madGodAlreadySpawned = beforeMadGodForItem != null && beforeMadGodForItem.optBoolean("spawned", false);
        if (madGod && !madGodAlreadySpawned) allowedNew = false;
'''
text = replace_once(text, old, new, "MadGod inventory null safety")

old_remove = r'''        boolean consequence = "world_consequence".equals(lower(op.optString("basis", ""))) &&
          (rollSuccess(rolls, "hazard") || rollSuccess(rolls, "entityEncounter"));
'''
new_remove = r'''        boolean consequence = "world_consequence".equals(lower(op.optString("basis", ""))) &&
          (rollSuccess(rolls, "hazard") || rollSuccess(rolls, "entityEncounter"));
        // A semantic inference alone never authorizes deletion of owned inventory.
'''
text = replace_once(text, old_remove, new_remove, "inventory removal authority comment")

MAIN.write_text(text, encoding="utf-8")
print("APK state-op hardening applied: old-save null safety and conservative inventory deletion authority.")
