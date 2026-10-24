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

# This is called after the three-action bridge patch. EXPLORE and SEARCH use the
# action kind even if a player's free text does not contain search keywords.
if 'private void submitTurnInternal(String stateJson, String actionKind, String action)' in main:
    old_call = 'processExplorationLoot(before.toString(), action)'
    new_call = 'processExplorationLoot(before.toString(), action, actionKind)'
    if old_call in main:
        main = main.replace(old_call, new_call, 1)

if 'processExplorationLoot(before.toString(), action' not in main:
    raise RuntimeError("Exploration loot must be called before sending a turn to GM")

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

# The narrator may describe previously spawned Core loot, never generate it.
directive = "ITEM LOOT AUTHORITY: Vật phẩm xuất hiện chỉ khi Core xác nhận trong flags.worldItems. GM không được tự thêm, xóa hoặc chọn vật phẩm. Sau khi hạ Entity, Core roll 80%; khi khám phá Core roll 8%; không cộng thêm tỷ lệ từ follower hoặc Level. Vật phẩm rơi ra thế giới, không tự vào Inventory."
if "ITEM LOOT AUTHORITY:" not in main:
    # Both the legacy prompt and Canon Runtime 2 writer receive this directive.
    anchor = "          JSONObject generated = parseModelJson("
    if main.count(anchor) == 1:
        main = main.replace(anchor,
            '          prompt = "' + directive + '\\n" + prompt;\n' + anchor, 1)
    else:
        # Generated writerPrompt path: attach to its return instead of relying on
        # a deleted legacy parseModelJson anchor.
        writer = "  private String writerPrompt("
        if writer not in main:
            raise RuntimeError("No GM writer anchor for Core loot directive")
        start = main.find(writer)
        end = main.find("\n  private ", start + 1)
        if end < 0:
            raise RuntimeError("GM writer method boundary missing")
        method = main[start:end]
        return_anchor = "    return "
        idx = method.rfind(return_anchor)
        if idx < 0:
            raise RuntimeError("GM writer return anchor missing")
        method = method[:idx] + '    String itemAuthority = "' + directive + '\\n";\n' + method[idx:]
        method = method[:method.rfind(return_anchor)] + method[method.rfind(return_anchor):].replace(return_anchor, '    return itemAuthority + ', 1)
        main = main[:start] + method + main[end:]

MAIN.write_text(main, encoding="utf-8")
print("Core-only loot authority preserved: ENTITY=80%, EXPLORE=8%, GM inventory and world loot read-only.")
