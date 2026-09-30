from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
COMBAT = ROOT / "app/src/main/java/com/rabpit/backroom/core/CombatRuntime.kt"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/CombatRuntimeTest.kt"
REGISTRY = ROOT / "app/src/main/assets/knowledge/entity_encounters.json"

registry = json.loads(REGISTRY.read_text(encoding="utf-8"))
if registry.get("rollMode") != "independent_per_entity":
    raise RuntimeError("V2 Entity registry must use independent_per_entity")

entities = registry.get("entities") or []
standard = []
treasure = []
for record in entities:
    key = str(record.get("key", "")).strip()
    name = str(record.get("name", key)).strip()
    rate = float(record.get("ratePercent", 0))
    spawn_class = str(record.get("spawnClass", "standard")).strip().lower()
    if not key:
        raise RuntimeError("V2 Entity registry contains an empty key")
    if spawn_class == "treasure":
        if not (0 < rate <= 4.0):
            raise RuntimeError(f"Invalid V2 treasure Entity rate for {key}: {rate}")
        treasure.append((key, name, rate))
    else:
        if not (3.0 <= rate <= 3.5):
            raise RuntimeError(f"Invalid V2 Entity rate for {key}: {rate}")
        standard.append((key, name, rate))

if [key for key, _, _ in treasure] != ["tam_ma_cao_minh"]:
    raise RuntimeError("Expected Tâm Ma Cao Minh as the V2 treasure Entity")

required_v2 = {
    "hound", "clump", "duller", "deathmoth", "hostile_faceling", "false_puddle",
    "paintings", "smiler", "skin-stealer", "predatory_window", "biological_pipeline",
    "wretch", "cable_mimic", "the_beast_of_level_5", "hotel_corpse_lure",
    "jeff_the_killer", "async_rifleman", "copx", "tam_ma_cao_minh",
}
registered_v2 = {str(record.get("key", "")).strip() for record in entities}
if registered_v2 != required_v2:
    raise RuntimeError(f"Unexpected V2 Entity registry keys: {sorted(registered_v2)}")

def java_var(key: str) -> str:
    return "entityRoll_" + re.sub(r"[^A-Za-z0-9_]", "_", key)

def threshold(rate: float) -> int:
    return int(round(rate * 100.0))

main = MAIN.read_text(encoding="utf-8")

old_thresholds = '    int[] entityThresholds = {805, 1000, 1150, 1150, 810, 1200, 805};\n'
new_thresholds = '    int[] legacyEntityPoolThresholds = {805, 1000, 1150, 1150, 810, 1200, 805};\n'
if new_thresholds not in main:
    if old_thresholds not in main:
        raise RuntimeError("Final legacy Entity threshold anchor missing")
    main = main.replace(old_thresholds, new_thresholds, 1)

entity_suffix = '    String entitySuffix = level == 0 || level == 4 || level == 6 ? " incursion/roaming only" : "";\n'
main = main.replace(entity_suffix, "", 1)

normal_start_marker = '    JSONObject normalEntityRoll = thresholdRoll("entityEncounter", 10000, entityThresholds[level], entityEncounterAction && entityAllowed, entitySuffix);\n'
normal_start = main.find(normal_start_marker)
normal_end = main.find('    rolls.put("loot"', normal_start)
if normal_start < 0 or normal_end < 0:
    raise RuntimeError("Final shared Entity roll block not found")

lines = [
    '    // V2 Entity authority: fixed independent rates from knowledge/entity_encounters.json.',
    '    JSONObject perEntityRolls = new JSONObject();',
    '    java.util.ArrayList<String> successfulEntities = new java.util.ArrayList<>();',
    '    String selectedEntityKey = "";',
]
for key, name, rate in treasure:
    var = java_var(key)
    lines.extend([
        f'    JSONObject {var} = thresholdRoll("entity:{key}", 10000, {threshold(rate)}, entityEncounterAction && entityAllowed, " V2 treasure priority");',
        f'    perEntityRolls.put("{key}", {var});',
        f'    if ({var}.optBoolean("success", false)) selectedEntityKey = "{key}";',
    ])
lines.append('    if (selectedEntityKey.isEmpty()) {')
for key, name, rate in standard:
    var = java_var(key)
    lines.extend([
        f'      JSONObject {var} = thresholdRoll("entity:{key}", 10000, {threshold(rate)}, entityEncounterAction && entityAllowed, " V2 independent");',
        f'      perEntityRolls.put("{key}", {var});',
        f'      if ({var}.optBoolean("success", false)) successfulEntities.add("{key}");',
    ])
for key in ("jane_the_killer", "slenderman"):
    var = java_var(key)
    lines.extend([
        f'      JSONObject {var} = thresholdRoll("entity:{key}", 180000, legacyEntityPoolThresholds[level], entityEncounterAction && entityAllowed, " V1 effective-rate fallback");',
        f'      perEntityRolls.put("{key}", {var});',
        f'      if ({var}.optBoolean("success", false)) successfulEntities.add("{key}");',
    ])
lines.extend([
    '      if (!successfulEntities.isEmpty()) selectedEntityKey = successfulEntities.get(GAME_RNG.nextInt(successfulEntities.size()));',
    '    }',
    '    JSONObject normalEntityRoll = new JSONObject()',
    '      .put("label", "entityEncounter")',
    '      .put("eligible", entityEncounterAction && entityAllowed)',
    '      .put("success", !selectedEntityKey.isEmpty())',
    '      .put("roll", JSONObject.NULL)',
    '      .put("rollMode", "independent_per_entity")',
    '      .put("chance", "V2 fixed per-Entity rates; legacy per-key fallback where V2 has no rate");',
    '    rolls.put("entityEncounter", normalEntityRoll);',
    '    rolls.put("entityRolls", perEntityRolls);',
    '    if (!selectedEntityKey.isEmpty()) rolls.put("roamingEntityKey", selectedEntityKey);',
    '',
])
new_roll_block = "\n".join(lines)
main = main[:normal_start] + new_roll_block + main[normal_end:]

normalized_old = '      case "jeff_the_killer": case "jane_the_killer": case "slenderman": case "diep_minh":\n        return key;\n'
normalized_new = '      case "jeff_the_killer": case "async_rifleman": case "copx": case "tam_ma_cao_minh":\n      case "jane_the_killer": case "slenderman": case "diep_minh":\n        return key;\n'
if normalized_new not in main:
    if normalized_old not in main:
        raise RuntimeError("Final Entity key switch anchor missing")
    main = main.replace(normalized_old, normalized_new, 1)

name_anchor = '      case "jeff_the_killer": name = "Jeff the Killer"; break;\n'
name_extra = (
    name_anchor
    + '      case "async_rifleman": name = "ASYNC Rifleman"; break;\n'
    + '      case "copx": name = "CopX"; break;\n'
    + '      case "tam_ma_cao_minh": name = "Tâm Ma Cao Minh"; break;\n'
)
if 'case "async_rifleman": name = "ASYNC Rifleman"; break;' not in main:
    if name_anchor not in main:
        raise RuntimeError("Entity overlay display-name anchor missing")
    main = main.replace(name_anchor, name_extra, 1)

keys_old = "'hotel_corpse_lure','jeff_the_killer','jane_the_killer','slenderman','diep_minh'];"
keys_new = "'hotel_corpse_lure','jeff_the_killer','async_rifleman','copx','tam_ma_cao_minh','jane_the_killer','slenderman','diep_minh'];"
if keys_new not in main:
    if keys_old not in main:
        raise RuntimeError("Entity overlay JS key-list anchor missing")
    main = main.replace(keys_old, keys_new, 1)

resolver_start = main.find('  private JSONObject resolveEntityOverlay(String rawEntityKey) throws Exception {')
resolver_end = main.find('\n  private boolean retryable(int code) {', resolver_start)
if resolver_start < 0 or resolver_end < 0:
    raise RuntimeError("Entity overlay resolver boundary missing")
resolver = main[resolver_start:resolver_end]
if 'private boolean entityAssetExists(String path)' not in main:
    helper = '''  private boolean entityAssetExists(String path) {
    try (InputStream input = getAssets().open(path)) {
      return true;
    } catch (Exception ignored) {
      return false;
    }
  }

'''
    main = main[:resolver_start] + helper + main[resolver_start:]
    resolver_start += len(helper)
    resolver_end += len(helper)
    resolver = main[resolver_start:resolver_end]

return_anchor = '    return new JSONObject()\n'
if 'String overlayExtension =' not in resolver:
    pos = resolver.find(return_anchor)
    if pos < 0:
        raise RuntimeError("Entity overlay return anchor missing")
    asset_logic = '''    String webpAsset = "entity/" + entityKey + ".webp";
    String overlayExtension = entityAssetExists(webpAsset) ? ".webp" : ".png";
    if (!entityAssetExists("entity/" + entityKey + overlayExtension)) {
      throw new Exception("Khong co local asset cho " + entityKey);
    }
'''
    resolver = resolver[:pos] + asset_logic + resolver[pos:]
resolver = resolver.replace('.put("revision", 1)', '.put("revision", 2)', 1)
resolver = resolver.replace(
    '.put("url", "file:///android_asset/entity/" + entityKey + ".png");',
    '.put("url", "file:///android_asset/entity/" + entityKey + overlayExtension);',
    1,
)
main = main[:resolver_start] + resolver + main[resolver_end:]

prompt_lines = []
rates_text = ", ".join(f"{key}={rate:g}%" for key, _, rate in treasure + standard)
for line in main.splitlines(keepends=True):
    if "ENTITY ROAMING HARD LOCK:" in line:
        prompt_lines.append(
            '      "ENTITY V2 SPAWN HARD LOCK: V2 dùng independent_per_entity và roaming mọi Level. '
            + rates_text
            + '. Tâm Ma Cao Minh là treasure priority. Jane the Killer và Slenderman không có rate V2 nên giữ đúng effective per-key rate từ V1 shared pool theo từng Level. Diệp Minh giữ roll boss độc lập 3%. Khi nhiều Entity thường cùng success chỉ một canonical key được chọn; Gemini không được thay thế lựa chọn runtime. " +\n'
        )
    elif "ROAMING KILLER HARD LOCK:" in line:
        continue
    else:
        prompt_lines.append(line)
main = "".join(prompt_lines)
main = main.replace(
    'hình Entity chỉ lấy từ APK assets/entity qua file:///android_asset/entity/<canonical-key>.png; cấm mã Entity legacy, alias theo Level, manifest từ xa hoặc ảnh Entity từ mạng.',
    'hình Entity chỉ lấy từ APK assets/entity; ưu tiên file:///android_asset/entity/<canonical-key>.webp của V2 khi tồn tại, nếu không có thì fallback <canonical-key>.png cũ. Cấm manifest từ xa hoặc ảnh Entity từ mạng.',
)

combat = COMBAT.read_text(encoding="utf-8")
profile_anchor = '    Profile("slenderman", "Slenderman", 160, 23, 8, 10),\n'
profile_block = '''    // V2 HP is stored minus the target's legacy +30 durability layer so final combat HP stays canonical.
    Profile("async_rifleman", "ASYNC Rifleman", 150, 20, 5, 7),
    Profile("copx", "CopX", 230, 22, 8, 7),
    Profile("tam_ma_cao_minh", "Tâm Ma Cao Minh", 270, 30, 8, 10),
''' + profile_anchor
if 'Profile("async_rifleman", "ASYNC Rifleman", 150, 20, 5, 7)' not in combat:
    if profile_anchor not in combat:
        raise RuntimeError("Final CombatRuntime Entity profile anchor missing")
    combat = combat.replace(profile_anchor, profile_block, 1)
COMBAT.write_text(combat, encoding="utf-8")

test = TEST.read_text(encoding="utf-8")
test_marker = "v2PortedEntitiesStartWithCanonicalFinalHp"
if test_marker not in test:
    extra = r'''
  @Test fun v2PortedEntitiesStartWithCanonicalFinalHp() {
    val expected = mapOf(
      "async_rifleman" to 180,
      "copx" to 260,
      "tam_ma_cao_minh" to 300
    )
    expected.forEach { (key, hp) ->
      val active = CombatRuntime.active(CombatRuntime.start(GameState.initial(), key))
      assertNotNull("V2 Entity must start CombatRuntime: $key", active)
      assertEquals("V2 final HP must remain canonical: $key", hp, active!!.entityMaxHp)
    }
  }

'''
    close = test.rfind("}\n")
    if close < 0:
        raise RuntimeError("CombatRuntimeTest closing brace missing")
    test = test[:close] + extra + test[close:]
TEST.write_text(test, encoding="utf-8")

for marker in (
    'legacyEntityPoolThresholds = {805, 1000, 1150, 1150, 810, 1200, 805}',
    'rolls.put("entityRolls", perEntityRolls)',
    'thresholdRoll("entity:jeff_the_killer", 10000, 300',
    'thresholdRoll("entity:tam_ma_cao_minh", 10000, 400',
    'thresholdRoll("entity:jane_the_killer", 180000, legacyEntityPoolThresholds[level]',
    'thresholdRoll("entity:slenderman", 180000, legacyEntityPoolThresholds[level]',
    'case "async_rifleman":',
    'case "copx":',
    'case "tam_ma_cao_minh":',
    'entityAssetExists(webpAsset) ? ".webp" : ".png"',
    'file:///android_asset/entity/" + entityKey + overlayExtension',
    'ENTITY V2 SPAWN HARD LOCK:',
):
    if marker not in main:
        raise RuntimeError("V2 Entity port contract missing: " + marker)

for forbidden in (
    'String[] roamingPool =',
    'thresholdRoll("entityEncounter", 10000, entityThresholds[level]',
    'ROAMING KILLER HARD LOCK:',
):
    if forbidden in main:
        raise RuntimeError("V1 Entity spawn conflict survived V2 authority: " + forbidden)

MAIN.write_text(main, encoding="utf-8")
print("V2 Entity system installed: independent fixed rates, legacy rate fallback, WebP-first overlays, and V2-only combat profiles.")
