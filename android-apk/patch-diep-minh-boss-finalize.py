from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"

text = MAIN.read_text(encoding="utf-8")

# The deferred unified Entity-pool pass intentionally rewrites forceEntityEncounterFlag after
# the main Diệp Minh combat patch has already run. Restore only the unique-boss encounter priority
# here instead of re-running the whole combat patch a second time.
helper_start = text.find('  private void forceEntityEncounterFlag(JSONObject candidateState, JSONObject rolls) throws Exception {')
helper_end = text.find('\n  private JSONObject resolveEntityOverlay(String rawEntityKey) throws Exception {', helper_start)
if helper_start < 0 or helper_end < 0:
    raise RuntimeError("Final forceEntityEncounterFlag boundary missing")

boss_helper = r'''  private void forceEntityEncounterFlag(JSONObject candidateState, JSONObject rolls) throws Exception {
    if (candidateState == null || rolls == null) return;
    String entityKey;
    JSONObject boss = rolls.optJSONObject("diepMinhEncounter");
    if (boss != null && boss.optBoolean("success", false)) {
      entityKey = "diep_minh";
    } else {
      JSONObject normal = rolls.optJSONObject("entityEncounter");
      if (normal == null || !normal.optBoolean("success", false)) return;
      entityKey = rolls.optString("roamingEntityKey", "").trim();
      if (entityKey.isEmpty()) return;
    }
    JSONObject flags = candidateState.optJSONObject("flags");
    if (flags == null) {
      flags = new JSONObject();
      candidateState.put("flags", flags);
    }
    String canonicalKey = normalizedEntityKey(entityKey);
    flags.put("entityEncounterKey", canonicalKey);
    requireGameCore().startCombatState(candidateState.toString(), canonicalKey);
  }
'''

current_helper = text[helper_start:helper_end]
if current_helper != boss_helper:
    text = text[:helper_start] + boss_helper + text[helper_end:]

# The earlier boss patch temporarily adds a GM prose line using an unstable prompt anchor. In the
# fully generated runtime that line can land outside the Java String expression. It is not gameplay
# authority, so remove only that prose line here rather than touching the validated dice/combat code.
prompt_lines = [line for line in text.splitlines(keepends=True) if 'DIỆP MINH BOSS HARD LOCK:' in line]
if len(prompt_lines) > 1:
    raise RuntimeError(f"Expected at most one Diệp Minh prompt line, found {len(prompt_lines)}")
if prompt_lines:
    text = "".join(line for line in text.splitlines(keepends=True) if 'DIỆP MINH BOSS HARD LOCK:' not in line)

# This finalizer must not invent the boss contract. The earlier boss patch remains responsible for
# the independent 3% roll, canonical local asset key, display name, local asset, and combat rules.
for marker in (
    'thresholdRoll("diepMinhEncounter", 10000, 300, entityEncounterAction && entityAllowed',
    'rolls.put("diepMinhEncounter", diepMinhRoll)',
    'case "diep_minh":',
    'case "diep_minh": name = "Diệp Minh"; break;',
    "'slenderman','diep_minh']",
    'JSONObject boss = rolls.optJSONObject("diepMinhEncounter")',
    'entityKey = "diep_minh";',
    'file:///android_asset/entity/',
):
    if marker not in text:
        raise RuntimeError("Diệp Minh final encounter contract missing: " + marker)

pool_lines = [line for line in text.splitlines() if 'String[] roamingPool =' in line]
if len(pool_lines) != 1:
    raise RuntimeError(f"Expected exactly one final roaming pool, found {len(pool_lines)}")
if 'diep_minh' in pool_lines[0]:
    raise RuntimeError("Diệp Minh must remain outside the shared roaming pool")

MAIN.write_text(text, encoding="utf-8")
print("Diệp Minh final encounter priority restored after unified Entity pool; unstable prose-only Java insertion removed.")
