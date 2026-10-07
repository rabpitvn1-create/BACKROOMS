from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
FACADE = CORE / "GameCoreFacade.kt"
REDUCER = CORE / "StateReducer.kt"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/ItemSourceAuthorityFinalTest.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return text.replace(old, new, 1)


# ---------------------------------------------------------------------------
# 1) Runtime pickup authority: only ENTITY overflow drops or CHEST contents.
# Everything else in flags.worldItems is quarantined into flags.legacyWorldItems
# and can no longer be acquired by the active runtime.
# ---------------------------------------------------------------------------
facade = FACADE.read_text(encoding="utf-8")

old_available = '''    for (index in 0 until items.length()) {
      val item = items.optJSONObject(index) ?: continue
      if (!item.optBoolean("available", true)) continue
      val name = item.optString("name").trim()
      val id = item.optString("id").trim()
      if (name.isBlank() && id.isBlank()) continue
      available += index to item
    }
'''
new_available = '''    for (index in 0 until items.length()) {
      val item = items.optJSONObject(index) ?: continue
      if (!item.optBoolean("available", true)) continue
      val metadata = item.optJSONObject("metadata")
      val origin = metadata?.optString("itemOrigin", "")?.trim()?.uppercase().orEmpty()
      val authoritative = origin == "ENTITY" ||
        (origin == "CHEST" && metadata?.optString("chestId", "")?.trim()?.isNotEmpty() == true)
      if (!authoritative) continue
      val name = item.optString("name").trim()
      val id = item.optString("id").trim()
      if (name.isBlank() && id.isBlank()) continue
      available += index to item
    }
'''
if new_available not in facade:
    facade = replace_once(facade, old_available, new_available, "authoritative world-item filter")

old_metadata = '''    val metadata = jsonObjectStrings(item.optJSONObject("metadata")) + mapOf(
      "worldInstanceId" to instanceId,
      "itemOrigin" to "WORLD",
      "omnivaultOriginal" to "true"
    )
'''
new_metadata = '''    val metadata = jsonObjectStrings(item.optJSONObject("metadata")) + mapOf(
      "worldInstanceId" to instanceId,
      "omnivaultOriginal" to "true"
    )
'''
if new_metadata not in facade:
    facade = replace_once(facade, old_metadata, new_metadata, "preserve authoritative item origin")

load_anchor = '''  private fun loadOrMigrate(legacy: JSONObject): GameState {
    if (repository.exists()) return repository.load()
    val migrated = GameStateCodec.decode(legacy)
    repository.save(migrated)
    return migrated
  }
'''
load_replacement = '''  private fun quarantineRetiredItemSources(state: GameState): GameState {
    val rawFlags = state.world["flagsJson"] ?: return state
    val flags = runCatching { JSONObject(rawFlags) }.getOrNull() ?: return state
    val worldItems = flags.optJSONArray("worldItems") ?: return state
    val active = JSONArray()
    val legacy = flags.optJSONArray("legacyWorldItems") ?: JSONArray()
    var changed = false
    for (index in 0 until worldItems.length()) {
      val item = worldItems.optJSONObject(index) ?: continue
      val metadata = item.optJSONObject("metadata")
      val origin = metadata?.optString("itemOrigin", "")?.trim()?.uppercase().orEmpty()
      val authoritative = origin == "ENTITY" ||
        (origin == "CHEST" && metadata?.optString("chestId", "")?.trim()?.isNotEmpty() == true)
      if (authoritative) {
        active.put(item)
      } else {
        val retired = JSONObject(item.toString())
          .put("legacyReason", "retired_item_source")
        legacy.put(retired)
        changed = true
      }
    }
    if (!changed) return state
    flags.put("worldItems", active)
    flags.put("legacyWorldItems", legacy)
    return state.copy(world = state.world + ("flagsJson" to flags.toString()))
  }

  private fun loadOrMigrate(legacy: JSONObject): GameState {
    if (repository.exists()) {
      val loaded = repository.load()
      val normalized = quarantineRetiredItemSources(loaded)
      if (normalized != loaded) repository.save(normalized)
      return normalized
    }
    val migrated = quarantineRetiredItemSources(GameStateCodec.decode(legacy))
    repository.save(migrated)
    return migrated
  }
'''
if "private fun quarantineRetiredItemSources(" not in facade:
    facade = replace_once(facade, load_anchor, load_replacement, "legacy item-source quarantine")

FACADE.write_text(facade, encoding="utf-8")


# ---------------------------------------------------------------------------
# 2) Defense in depth: Gemini can never mint a pickup command. Explicit
# non-authoritative itemOrigin values are also rejected at the reducer boundary.
# SYSTEM setup commands without an origin remain compatible with canonical seed
# and tests; live acquisition paths must attach ENTITY or CHEST provenance.
# ---------------------------------------------------------------------------
reducer = REDUCER.read_text(encoding="utf-8")
validator_anchor = '''    if (command is ItemCommand && command.operation == ItemCommand.Operation.PICKUP &&
      command.source !in setOf(CommandSource.GEMINI, CommandSource.SYSTEM)) {
      return ValidationResult(false, "player_pickup_unavailable")
    }
'''
validator_replacement = '''    if (command is ItemCommand && command.operation == ItemCommand.Operation.PICKUP) {
      if (command.source == CommandSource.GEMINI) {
        return ValidationResult(false, "item_source_not_authoritative")
      }
      if (command.source != CommandSource.SYSTEM) {
        return ValidationResult(false, "player_pickup_unavailable")
      }
      val origin = command.metadata["itemOrigin"]?.trim()?.uppercase()
      if (origin != null && origin !in setOf("ENTITY", "CHEST")) {
        return ValidationResult(false, "item_source_not_authoritative")
      }
      if (origin == "CHEST" && command.metadata["chestId"].isNullOrBlank()) {
        return ValidationResult(false, "chest_source_missing")
      }
    }
'''
if validator_replacement not in reducer:
    reducer = replace_once(reducer, validator_anchor, validator_replacement, "pickup provenance validator")
REDUCER.write_text(reducer, encoding="utf-8")


# ---------------------------------------------------------------------------
# 3) GM orchestration no longer exposes or applies inventory_upsert. Generic
# loot/story/world discovery may narrate or discover a Chest, but it cannot mint
# an Item. Inventory removal remains available for validated consumption/loss.
# ---------------------------------------------------------------------------
main = MAIN.read_text(encoding="utf-8")
pattern = re.compile(
    r'      if \(type\.equals\("inventory_upsert"\)\) \{.*?\n      \}\n\n      if \(type\.equals\("inventory_remove"\)\) \{',
    re.DOTALL,
)
replacement = '''      if (type.equals("inventory_upsert")) {
        // LEGACY_ITEM_SOURCE: GM-side item creation is retired. Active item sources are ENTITY and CHEST only.
        continue;
      }

      if (type.equals("inventory_remove")) {'''
if "LEGACY_ITEM_SOURCE: GM-side item creation is retired" not in main:
    main, count = pattern.subn(replacement, main, count=1)
    if count != 1:
        raise RuntimeError(f"retire GM inventory_upsert: expected 1 block, found {count}")

main = main.replace(
    'patch_player{patch}; inventory_upsert{item,basis}; inventory_remove{name,basis}; ',
    'patch_player{patch}; inventory_remove{name,basis}; ',
)
old_contract = 'Inventory chỉ đổi khi Kai thật sự lấy/nhận/copy/trao/mất/tiêu thụ vật; nhìn thấy không đồng nghĩa sở hữu. MadGod roll success chỉ mở discovery route, không tự đưa set vào inventory. '
new_contract = 'GM không được tạo hoặc thêm Item. Item mới chỉ được Game State Core cấp từ Entity drop hoặc Chest contents đã tồn tại trong authoritative state; generic loot/story/world discovery không có quyền tạo Item. Loot success chỉ có thể mở discovery của Chest, không sinh vật phẩm rời. MadGod discovery không tự đưa set vào Inventory. '
if old_contract in main:
    main = main.replace(old_contract, new_contract, 1)
elif new_contract not in main:
    raise RuntimeError("GM item-source contract anchor missing")

# Healing effects stay active, but the old generic-loot spawn rule is retired.
healing_pattern = re.compile(r'String healingItemDirective = "HEALING ITEM HARD LOCK:.*?";\n', re.DOTALL)
if "Nguồn generic-loot cũ là LEGACY" not in main:
    main, healing_count = healing_pattern.subn(
        'String healingItemDirective = "HEALING ITEM HARD LOCK: Băng gạc hồi đúng 10 HP; Thuốc sát trùng hồi đúng 20 HP. Nguồn generic-loot cũ là LEGACY và không còn quyền tạo Item. Hai vật phẩm chỉ có thể xuất hiện từ Entity drop hoặc Chest contents authoritative; loot.success không được sinh vật phẩm rời. Khi dùng, hồi không vượt Effective Max HP và không hồi sinh nhân vật 0 HP/DEAD.";\n',
        main,
        count=1,
    )
    if healing_count != 1:
        raise RuntimeError(f"retire healing generic-loot directive: expected 1 anchor, found {healing_count}")

MAIN.write_text(main, encoding="utf-8")


# ---------------------------------------------------------------------------
# 4) Focused regression coverage for the final source-of-truth contract.
# ---------------------------------------------------------------------------
TEST.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class ItemSourceAuthorityFinalTest {
  private fun base(): GameState = CharacterEquipmentSystem.seedFresh(GameState.initial())

  @Test fun geminiCannotMintPickupAndRetiredOriginIsRejected() {
    val state = base()
    val gemini = StateReducer.execute(state, ItemCommand(
      commandId = "gemini-mint", turnId = state.turn.currentTurnId, actorId = KAI_ID,
      source = CommandSource.GEMINI, operation = ItemCommand.Operation.PICKUP,
      itemId = "invented", itemName = "Invented Item", metadata = mapOf("itemOrigin" to "CHEST", "chestId" to "fake")
    ))
    assertFalse(gemini.applied)
    assertEquals("item_source_not_authoritative", gemini.validation.reason)

    val retired = StateReducer.execute(state, ItemCommand(
      commandId = "world-mint", turnId = state.turn.currentTurnId, actorId = KAI_ID,
      source = CommandSource.SYSTEM, operation = ItemCommand.Operation.PICKUP,
      itemId = "loose", itemName = "Loose Item", metadata = mapOf("itemOrigin" to "WORLD")
    ))
    assertFalse(retired.applied)
    assertEquals("item_source_not_authoritative", retired.validation.reason)
  }

  @Test fun entityAndChestProvenanceRemainValid() {
    var state = base()
    val entity = StateReducer.execute(state, ItemCommand(
      commandId = "entity-drop", turnId = state.turn.currentTurnId, actorId = KAI_ID,
      source = CommandSource.SYSTEM, operation = ItemCommand.Operation.PICKUP,
      itemId = "entity-water", itemName = "Almond Water", metadata = mapOf("itemOrigin" to "ENTITY")
    ))
    assertTrue(entity.validation.reason ?: "entity pickup failed", entity.applied)
    state = entity.state

    val chest = StateReducer.execute(state, ItemCommand(
      commandId = "chest-drop", turnId = state.turn.currentTurnId, actorId = KAI_ID,
      source = CommandSource.SYSTEM, operation = ItemCommand.Operation.PICKUP,
      itemId = "chest-bandage", itemName = "Băng gạc",
      metadata = mapOf("itemOrigin" to "CHEST", "chestId" to "chest:test:1")
    ))
    assertTrue(chest.validation.reason ?: "chest pickup failed", chest.applied)
  }

  @Test fun chestRequiresStableChestId() {
    val state = base()
    val result = StateReducer.execute(state, ItemCommand(
      commandId = "chest-without-id", turnId = state.turn.currentTurnId, actorId = KAI_ID,
      source = CommandSource.SYSTEM, operation = ItemCommand.Operation.PICKUP,
      itemId = "bad-chest", itemName = "Bad Chest Item", metadata = mapOf("itemOrigin" to "CHEST")
    ))
    assertFalse(result.applied)
    assertEquals("chest_source_missing", result.validation.reason)
  }
}
''', encoding="utf-8")

combined = FACADE.read_text(encoding="utf-8") + "\n" + REDUCER.read_text(encoding="utf-8") + "\n" + MAIN.read_text(encoding="utf-8") + "\n" + TEST.read_text(encoding="utf-8")
for marker in (
    'origin == "ENTITY"',
    'origin == "CHEST"',
    'legacyWorldItems',
    'item_source_not_authoritative',
    'chest_source_missing',
    'LEGACY_ITEM_SOURCE: GM-side item creation is retired',
    'Item mới chỉ được Game State Core cấp từ Entity drop hoặc Chest contents',
    'class ItemSourceAuthorityFinalTest',
):
    if marker not in combined:
        raise RuntimeError("Final item-source authority contract missing: " + marker)

print("Final item-source authority applied: active acquisition is ENTITY/CHEST only; retired WORLD/GM/generic-loot sources are quarantined to Legacy.")
