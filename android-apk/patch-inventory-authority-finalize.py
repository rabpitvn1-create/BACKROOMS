from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
FACADE = CORE / "GameCoreFacade.kt"
ENGINES = CORE / "Engines.kt"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
TEST = TESTS / "InventoryAuthorityRegressionTest.kt"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


facade = FACADE.read_text(encoding="utf-8")

# ---------------------------------------------------------------------------
# 1) Gemini candidate snapshots never own Inventory mutation.
# Inventory changes must come from an explicit command executed by Game State Core.
# This intentionally removes the old snapshot-diff authority where a model supplied
# inventory array could become PICKUP/DROP after narration had already been written.
# ---------------------------------------------------------------------------
legacy_locks = (
    '    val inventoryLocked = isDirectPlayerPickupAction(action) || GameIntent.PICKUP_ITEM in actionIntents || GameIntent.OMNIVAULT_RESTORE in actionIntents\n',
    '    val inventoryLocked = isDirectPlayerPickupAction(action) || GameIntent.OMNIVAULT_RESTORE in actionIntents\n',
)
if '    val inventoryLocked = true // INVENTORY_AUTHORITY: candidate snapshots are read-only\n' not in facade:
    found = [line for line in legacy_locks if line in facade]
    if len(found) != 1:
        raise RuntimeError(f"Inventory authority lock anchor mismatch: found {len(found)}")
    facade = facade.replace(found[0], '    val inventoryLocked = true // INVENTORY_AUTHORITY: candidate snapshots are read-only\n', 1)

# ---------------------------------------------------------------------------
# 2) Item intent may never fall through to free-form GM narration.
# If deterministic resolution cannot produce a command, fail closed. This prevents
# prose such as "đã dùng/đã cho/đã trang bị" without an authoritative state commit.
# ---------------------------------------------------------------------------
item_helper_anchor = '''  private fun isDirectPlayerPickupAction(action: String): Boolean {
'''
item_helper = '''  private fun isAuthoritativeItemIntent(intent: GameIntent): Boolean = intent in setOf(
    GameIntent.PICKUP_ITEM,
    GameIntent.DROP_ITEM,
    GameIntent.USE_ITEM,
    GameIntent.TRANSFER_ITEM,
    GameIntent.EQUIP_ITEM,
    GameIntent.UNEQUIP_ITEM,
    GameIntent.OMNIVAULT_STORE,
    GameIntent.OMNIVAULT_WITHDRAW,
    GameIntent.OMNIVAULT_SCAN,
    GameIntent.OMNIVAULT_COPY,
    GameIntent.OMNIVAULT_RESTORE
  )

'''
if 'private fun isAuthoritativeItemIntent(intent: GameIntent)' not in facade:
    if item_helper_anchor not in facade:
        raise RuntimeError("Item authority helper anchor missing")
    facade = facade.replace(item_helper_anchor, item_helper + item_helper_anchor, 1)

fallback_anchor = '''    if (interpreted.candidates.any { it.intent == GameIntent.NO_ACTION || it.confidence != IntentConfidence.HIGH }) {
      return response(false, legacy, null, "fallback_required")
    }
'''
fallback_replacement = '''    if (interpreted.candidates.any { isAuthoritativeItemIntent(it.intent) && it.confidence != IntentConfidence.HIGH }) {
      val result = syncLegacy(legacy, state, incrementTurn = false)
      val reply = validationReply("item_action_resolution_required")
      appendLog(result, action, reply)
      logger.log(PipelineLogEvent("REJECT", turnId = turnId, details = mapOf("reason" to "item_action_resolution_required")))
      return response(true, result, "item_action_resolution_required", "validation_rejected", reply)
    }
    if (interpreted.candidates.any { it.intent == GameIntent.NO_ACTION || it.confidence != IntentConfidence.HIGH }) {
      return response(false, legacy, null, "fallback_required")
    }
'''
if '"item_action_resolution_required"' not in facade:
    if fallback_anchor not in facade:
        raise RuntimeError("Item fallback authority anchor missing")
    facade = facade.replace(fallback_anchor, fallback_replacement, 1)

resolution_anchor = '''    if (resolvedCommands.size != interpreted.candidates.size || resolvedCommands.isEmpty()) return response(false, legacy, null, "resolution_incomplete")
'''
resolution_replacement = '''    if (resolvedCommands.size != interpreted.candidates.size || resolvedCommands.isEmpty()) {
      if (interpreted.candidates.any { isAuthoritativeItemIntent(it.intent) }) {
        val result = syncLegacy(legacy, state, incrementTurn = false)
        val reply = validationReply("item_action_resolution_required")
        appendLog(result, action, reply)
        return response(true, result, "item_action_resolution_required", "validation_rejected", reply)
      }
      return response(false, legacy, null, "resolution_incomplete")
    }
'''
if resolution_replacement not in facade:
    if resolution_anchor not in facade:
        raise RuntimeError("Item resolution authority anchor missing")
    facade = facade.replace(resolution_anchor, resolution_replacement, 1)

# ---------------------------------------------------------------------------
# 3) A discovered world item is an authoritative availability record before PICKUP.
# Existing final runtime stores validated world state in flagsJson, so this patch adds
# a deliberately small worldItems ledger there rather than introducing a parallel save.
# Direct pickup consumes one available ledger entry and commits PICKUP + world-ledger
# update atomically before any success reply is appended.
# ---------------------------------------------------------------------------
world_types_anchor = '''  private fun isAuthoritativeItemIntent(intent: GameIntent): Boolean = intent in setOf(
'''
world_types = '''  private data class WorldPickup(
    val itemId: String,
    val itemName: String,
    val quantity: Int,
    val metadata: Map<String, String>,
    val flagsJson: String
  )

  private fun resolveWorldPickup(state: GameState, action: String): WorldPickup? {
    val rawFlags = state.world["flagsJson"] ?: return null
    val flags = runCatching { JSONObject(rawFlags) }.getOrNull() ?: return null
    val items = flags.optJSONArray("worldItems") ?: return null
    val normalizedAction = action.lowercase()
    val available = mutableListOf<Pair<Int, JSONObject>>()
    for (index in 0 until items.length()) {
      val item = items.optJSONObject(index) ?: continue
      if (!item.optBoolean("available", true)) continue
      val name = item.optString("name").trim()
      val id = item.optString("id").trim()
      if (name.isBlank() && id.isBlank()) continue
      available += index to item
    }
    if (available.isEmpty()) return null
    val selected = available.firstOrNull { (_, item) ->
      val name = item.optString("name").trim().lowercase()
      val id = item.optString("id").trim().lowercase()
      (name.isNotBlank() && normalizedAction.contains(name)) ||
        (id.isNotBlank() && normalizedAction.contains(id)) ||
        name.split(Regex("\\s+")).filter { it.length >= 4 }.any { normalizedAction.contains(it) }
    } ?: available.singleOrNull() ?: return null
    val index = selected.first
    val item = selected.second
    val quantity = item.optInt("quantity", 1).coerceAtLeast(1)
    val take = 1
    val remaining = quantity - take
    val instanceId = item.optString("instanceId").ifBlank { "world:${item.optString("id").ifBlank { stableItemId(item.optString("name")) }}:$index" }
    val metadata = jsonObjectStrings(item.optJSONObject("metadata")) + mapOf(
      "worldInstanceId" to instanceId,
      "itemOrigin" to "WORLD",
      "omnivaultOriginal" to "true"
    )
    if (remaining <= 0) item.put("available", false).put("quantity", 0)
    else item.put("quantity", remaining)
    flags.put("worldItems", items)
    return WorldPickup(
      itemId = item.optString("id").ifBlank { stableItemId(item.optString("name")) },
      itemName = item.optString("name").ifBlank { item.optString("id") },
      quantity = take,
      metadata = metadata,
      flagsJson = flags.toString()
    )
  }

'''
if 'private data class WorldPickup(' not in facade:
    if world_types_anchor not in facade:
        raise RuntimeError("World pickup type anchor missing")
    facade = facade.replace(world_types_anchor, world_types + world_types_anchor, 1)

pickup_guard_variants = (
'''    if (isDirectPlayerPickupAction(action) || interpreted.candidates.any { it.intent == GameIntent.PICKUP_ITEM }) {
      val result = syncLegacy(legacy, state, incrementTurn = false)
      val reply = validationReply("player_pickup_unavailable")
      appendLog(result, action, reply)
      logger.log(PipelineLogEvent("REJECT", turnId = turnId, details = mapOf("reason" to "player_pickup_unavailable")))
      return response(true, result, "player_pickup_unavailable", "validation_rejected", reply)
    }
''',
'''    if (isDirectPlayerPickupAction(action)) {
      val result = syncLegacy(legacy, state, incrementTurn = false)
      val reply = validationReply("player_pickup_unavailable")
      appendLog(result, action, reply)
      logger.log(PipelineLogEvent("REJECT", turnId = turnId, details = mapOf("reason" to "player_pickup_unavailable")))
      return response(true, result, "player_pickup_unavailable", "validation_rejected", reply)
    }
'''
)
pickup_replacement = '''    if (isDirectPlayerPickupAction(action)) {
      val worldPickup = resolveWorldPickup(pending.state, action)
      if (worldPickup != null) {
        val commands = listOf<GameCommand>(
          ItemCommand(
            commandId = "$turnId:SYSTEM:WORLD_PICKUP",
            turnId = turnId,
            actorId = KAI_ID,
            source = CommandSource.SYSTEM,
            operation = ItemCommand.Operation.PICKUP,
            itemId = worldPickup.itemId,
            itemName = worldPickup.itemName,
            quantity = worldPickup.quantity,
            metadata = worldPickup.metadata
          ),
          ValidatedLegacyStateCommand(
            commandId = "$turnId:SYSTEM:WORLD_PICKUP_FLAGS",
            turnId = turnId,
            source = CommandSource.SYSTEM,
            flagsJson = worldPickup.flagsJson,
            validatedByGameEngine = true
          ),
          timeAdvanceCommand(turnId, action)
        )
        val committed = TurnCoordinator.commit(pending.state, commands)
        if (committed.error != null) {
          val result = syncLegacy(legacy, state, incrementTurn = false)
          val reply = validationReply(committed.error)
          appendLog(result, action, reply)
          return response(true, result, committed.error, "validation_rejected", reply)
        }
        repository.save(committed.state)
        val result = syncLegacy(legacy, committed.state, incrementTurn = true)
        val reply = eventReply(committed.execution?.events.orEmpty())
        appendLog(result, action, reply)
        logger.log(PipelineLogEvent("COMMIT", turnId = turnId, details = mapOf("worldPickup" to worldPickup.itemId)))
        return response(true, result, null, "world_pickup_committed", reply)
      }
      val result = syncLegacy(legacy, state, incrementTurn = false)
      val reply = validationReply("player_pickup_unavailable")
      appendLog(result, action, reply)
      logger.log(PipelineLogEvent("REJECT", turnId = turnId, details = mapOf("reason" to "player_pickup_unavailable")))
      return response(true, result, "player_pickup_unavailable", "validation_rejected", reply)
    }
'''
if '"world_pickup_committed"' not in facade:
    found = [block for block in pickup_guard_variants if block in facade]
    if len(found) != 1:
        raise RuntimeError(f"World pickup guard anchor mismatch: found {len(found)}")
    facade = facade.replace(found[0], pickup_replacement, 1)

# Helpful fail-closed reply for unresolved item commands.
reply_anchor = '      "player_pickup_unavailable" -> "Không thể tự thêm vật phẩm vào Inventory; hãy tìm kiếm hoặc tương tác với môi trường để game xác định kết quả."\n'
reply_line = '      "item_action_resolution_required" -> "Không thể xác thực hành động vật phẩm này từ state hiện tại; Inventory không thay đổi."\n'
if reply_line not in facade:
    if reply_anchor not in facade:
        raise RuntimeError("Validation reply item authority anchor missing")
    facade = facade.replace(reply_anchor, reply_anchor + reply_line, 1)

FACADE.write_text(facade, encoding="utf-8")

# ---------------------------------------------------------------------------
# 4) Regression coverage: medical pickup must be PICKUP, not content USE;
# transfer is atomic; candidate snapshots are read-only by source contract.
# ---------------------------------------------------------------------------
TEST.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class InventoryAuthorityRegressionTest {
  @Test fun bandagePickupDoesNotRunContentUseValidation() {
    val state = CharacterEquipmentSystem.seedFresh(GameState.initial())
    val result = InventoryEngine.execute(state, ItemCommand(
      commandId = "world-bandage-pickup",
      turnId = state.turn.currentTurnId,
      actorId = KAI_ID,
      source = CommandSource.SYSTEM,
      operation = ItemCommand.Operation.PICKUP,
      itemId = BANDAGE_ID,
      itemName = "Băng gạc",
      quantity = 1,
      metadata = mapOf("worldInstanceId" to "world:bandage:1", "itemOrigin" to "WORLD", "omnivaultOriginal" to "true")
    ))
    assertTrue(result.validation.reason ?: "pickup failed", result.applied)
    assertTrue(result.events.contains("inventory_pickup"))
    val bandage = result.state.inventories.getValue(KAI_ID).items.getValue(BANDAGE_ID)
    assertEquals(ContentState.NONE, bandage.contentState)
    assertEquals("true", bandage.metadata["consumable"])
  }

  @Test fun transferFailureLeavesBothInventoriesUntouched() {
    var state = CharacterEquipmentSystem.seedFresh(GameState.initial())
    val beforeKai = state.inventories[KAI_ID]
    val result = InventoryEngine.execute(state, ItemCommand(
      commandId = "bad-transfer",
      turnId = state.turn.currentTurnId,
      actorId = KAI_ID,
      targetId = "missing-character",
      source = CommandSource.RULE,
      operation = ItemCommand.Operation.TRANSFER,
      itemId = "not-owned",
      itemName = "Không tồn tại",
      quantity = 1
    ))
    assertFalse(result.applied)
    assertEquals(state, result.state)
    assertEquals(beforeKai, result.state.inventories[KAI_ID])
  }

  @Test fun useRequiresOwnershipBeforeAnySuccessEvent() {
    val state = CharacterEquipmentSystem.seedFresh(GameState.initial())
    val result = InventoryEngine.execute(state, ItemCommand(
      commandId = "use-missing-bandage",
      turnId = state.turn.currentTurnId,
      actorId = KAI_ID,
      source = CommandSource.RULE,
      operation = ItemCommand.Operation.USE,
      itemId = BANDAGE_ID,
      itemName = "Băng gạc",
      quantity = 1
    ))
    assertFalse(result.applied)
    assertEquals("item_not_owned", result.validation.reason)
    assertTrue(result.events.isEmpty())
  }
}
''', encoding="utf-8")

combined = FACADE.read_text(encoding="utf-8") + "\n" + ENGINES.read_text(encoding="utf-8") + "\n" + TEST.read_text(encoding="utf-8")
for marker in (
    'val inventoryLocked = true // INVENTORY_AUTHORITY: candidate snapshots are read-only',
    'private fun isAuthoritativeItemIntent(intent: GameIntent)',
    'private data class WorldPickup(',
    '"world_pickup_committed"',
    '"item_action_resolution_required"',
    'worldInstanceId',
    'class InventoryAuthorityRegressionTest',
    'bandagePickupDoesNotRunContentUseValidation',
):
    if marker not in combined:
        raise RuntimeError("Inventory authority final contract missing: " + marker)

print("Inventory authority finalizer applied: GM inventory snapshots are read-only; unresolved item actions fail closed; validated world loot commits before narration.")
