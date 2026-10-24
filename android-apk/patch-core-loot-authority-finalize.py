"""Enforce Core-only item-drop authority after every generated runtime patch."""
from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
FACADE = CORE / "GameCoreFacade.kt"
REDUCER = CORE / "StateReducer.kt"

main = MAIN.read_text(encoding="utf-8")
facade = FACADE.read_text(encoding="utf-8")
reducer = REDUCER.read_text(encoding="utf-8")

if "fun processExplorationLoot(" not in facade or "CoreLootEngine.roll" not in facade:
    raise RuntimeError("Core Loot gameplay hook missing from facade")
if 'command.source != CommandSource.SYSTEM' not in reducer or 'proposed.remove("worldItems")' not in reducer:
    raise RuntimeError("GM inventory/world-loot authority guard was overwritten")

# Patch-generated bridges can replace the entire turn handler. Restore the
# Core roll before GM processing and use typed SEARCH/EXPLORE when available.
typed = 'private void submitTurnInternal(String stateJson, String actionKind, String action)' in main
call = ('processExplorationLoot(before.toString(), action, actionKind)'
        if typed else 'processExplorationLoot(before.toString(), action)')
if typed and 'processExplorationLoot(before.toString(), action)' in main:
    main = main.replace('processExplorationLoot(before.toString(), action)', call, 1)
if 'processExplorationLoot(before.toString(), action' not in main:
    anchor = '          boolean meta = isMetaAction(action);\n'
    if main.count(anchor) != 1:
        raise RuntimeError("Cannot locate one GM-turn meta guard")
    injected = (
        '          if (!meta) {\n'
        '            JSONObject lootResult = new JSONObject(requireGameCore().' + call + ');\n'
        '            if (!lootResult.optBoolean("handled", false) || lootResult.optJSONObject("state") == null)\n'
        '              throw new IllegalStateException("Core loot resolution failed");\n'
        '            before = lootResult.getJSONObject("state");\n'
        '          }\n'
    )
    main = main.replace(anchor, anchor + injected, 1)

if main.count('processExplorationLoot(before.toString(), action') != 1:
    raise RuntimeError("Exploration loot must be resolved exactly once before GM")

# No patch-generated model operations may change the authoritative inventory.
start = main.find('      if (type.equals("inventory_upsert")) {')
if start >= 0:
    end = main.find('      if (type.equals("party_upsert")) {', start)
    if end < 0:
        raise RuntimeError("Could not isolate narrative Inventory operations")
    main = main[:start] + (
        '      if (type.equals("inventory_upsert") || type.equals("inventory_remove")) continue;\n\n'
    ) + main[end:]

start = main.find("  private JSONArray sanitizedInventory(")
end = main.find("  private JSONArray sanitizedParty(", start)
if start < 0 or end < 0:
    raise RuntimeError("Narrative Inventory sanitizer missing")
main = main[:start] + '''  private JSONArray sanitizedInventory(JSONArray current, JSONArray proposed, JSONObject rolls) throws Exception {
    return current == null ? new JSONArray() : new JSONArray(current.toString());
  }

''' + main[end:]

flags_start = main.find("  private JSONObject sanitizedFlags(")
if flags_start < 0:
    raise RuntimeError("Narrative flags sanitizer missing")
flags_end = main.find("\n  private ", flags_start + 1)
if flags_end < 0:
    raise RuntimeError("Narrative flags sanitizer boundary missing")
flags = main[flags_start:flags_end]
if 'patch.remove("worldItems")' not in flags:
    anchor = '    patch.remove("lastRolls");'
    if anchor not in flags:
        raise RuntimeError("flags sanitizer patch anchor missing")
    flags = flags.replace(anchor, '    patch.remove("worldItems");\n' + anchor, 1)
main = main[:flags_start] + flags + main[flags_end:]

# Do not leave the old per-Level + follower-modified GM loot rolls active.
roll_start = main.find("  private JSONObject makeGameplayRolls(")
roll_end = main.find("  private boolean rollSuccess(", roll_start)
if roll_start < 0 or roll_end < 0:
    raise RuntimeError("Gameplay rolls method missing")
rolls = main[roll_start:roll_end]
guard_marker = '// CORE_LOOT_EXCLUSIVE_V1'
if guard_marker not in rolls:
    anchor = "    return rolls;"
    idx = rolls.rfind(anchor)
    if idx < 0:
        raise RuntimeError("Gameplay rolls terminal return missing")
    guard = '''    // CORE_LOOT_EXCLUSIVE_V1: old GM-side loot is disabled.
    rolls.put("loot", new JSONObject().put("eligible", false).put("success", false));
    rolls.put("almondWater", new JSONObject().put("eligible", false).put("success", false));
'''
    rolls = rolls[:idx] + guard + rolls[idx:]
main = main[:roll_start] + rolls + main[roll_end:]

# Narrator only describes loot already present in the authoritative world state.
directive = "ITEM LOOT AUTHORITY: Chỉ Core được sinh Item trong flags.worldItems. GM không được chọn, thêm hoặc xóa Item. Entity bị tiêu diệt: Core roll 80%. Khám phá: Core roll 8%. Item rơi ngoài thế giới, chưa thuộc Inventory."
if "ITEM LOOT AUTHORITY:" not in main:
    writer = "  private String writerPrompt("
    if writer in main:
        start = main.find(writer)
        end = main.find("\n  private ", start + 1)
        if end < 0:
            raise RuntimeError("GM writer method boundary missing")
        method = main[start:end]
        ret = "    return "
        idx = method.rfind(ret)
        if idx < 0:
            raise RuntimeError("GM writer return anchor missing")
        method = method[:idx] + '    String itemAuthority = "' + directive + '\\n";\n' + method[idx:]
        idx = method.rfind(ret)
        method = method[:idx] + method[idx:].replace(ret, '    return itemAuthority + ', 1)
        main = main[:start] + method + main[end:]
    else:
        anchor = "          JSONObject generated = parseModelJson("
        if main.count(anchor) != 1:
            raise RuntimeError("GM writer anchor missing")
        main = main.replace(anchor,
            '          prompt = "' + directive + '\\n" + prompt;\n' + anchor, 1)

MAIN.write_text(main, encoding="utf-8")

# Keep the deterministic item-image UI connected after generated HTML patches.
INDEX = ROOT / "app/src/main/assets/index.html"
html = INDEX.read_text(encoding="utf-8")
if '<script src="loot-item-display.js"></script>' not in html:
    if "</body>" not in html:
        raise RuntimeError("Missing HTML body end for loot image UI")
    html = html.replace("</body>", '<script src="loot-item-display.js"></script>\\n</body>', 1)
    INDEX.write_text(html, encoding="utf-8")
if not (ROOT / "app/src/main/assets/loot-item-display.js").is_file():
    raise RuntimeError("Core-loot image UI script is missing from APK assets")
print("Core-only loot authority preserved: ENTITY=80%, EXPLORE=8%, GM inventory and world loot read-only.")
