from pathlib import Path

ROOT = Path(__file__).resolve().parent
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
KNOWLEDGE_BUILDER = ROOT / "patch-knowledge-context-builder.py"
INTENT = ROOT / "app/src/main/java/com/rabpit/backroom/core/IntentPipeline.kt"
COMMAND = ROOT / "app/src/main/java/com/rabpit/backroom/core/CommandPipeline.kt"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/OmnivaultNaturalFlowTest.kt"
text = FACADE.read_text(encoding="utf-8")

old_guard = "    if (isDirectPlayerPickupAction(action) || interpreted.candidates.any { it.intent == GameIntent.PICKUP_ITEM }) {\n"
new_guard = "    if (isDirectPlayerPickupAction(action)) {\n"
if new_guard not in text:
    count = text.count(old_guard)
    if count != 1:
        raise RuntimeError(f"Pickup false-positive guard expected one legacy match, found {count}")
    text = text.replace(old_guard, new_guard, 1)
if old_guard in text:
    raise RuntimeError("LiteRT PICKUP_ITEM classification can still reject non-pickup prose")

old_inventory_lock = "    val inventoryLocked = isDirectPlayerPickupAction(action) || GameIntent.PICKUP_ITEM in actionIntents || GameIntent.OMNIVAULT_RESTORE in actionIntents\n"
new_inventory_lock = "    val inventoryLocked = isDirectPlayerPickupAction(action) || GameIntent.OMNIVAULT_RESTORE in actionIntents\n"
if new_inventory_lock not in text:
    count = text.count(old_inventory_lock)
    if count != 1:
        raise RuntimeError(f"Validated inventory lock expected one legacy match, found {count}")
    text = text.replace(old_inventory_lock, new_inventory_lock, 1)

old_inventory_assertion = r'''    val inventoryAssertion = Regex("(?:thêm|bỏ|đưa).{0,80}(?:vào|trong)\\s+(?:inventory|kho đồ|túi đồ)", RegexOption.IGNORE_CASE)
'''
new_inventory_assertion = r'''    val inventoryAssertion = Regex("(?:thêm|đưa).{0,80}(?:vào|trong)\\s+(?:inventory|kho đồ|túi đồ)", RegexOption.IGNORE_CASE)
'''
if new_inventory_assertion not in text:
    count = text.count(old_inventory_assertion)
    if count != 1:
        raise RuntimeError(f"Inventory assertion expected one legacy match, found {count}")
    text = text.replace(old_inventory_assertion, new_inventory_assertion, 1)

# Keep the historical combined scan-source/template warning untouched here because the later
# MadGod patch intentionally anchors on that exact line before adding its own validation messages.
translations = {
    '"precise_content_amount_forbidden" -> "This action is not available."': '"precise_content_amount_forbidden" -> "Hành động này không khả dụng với lượng nội dung được chỉ định."',
    '"item_content_empty" -> "This action is not available."': '"item_content_empty" -> "Vật phẩm này hiện không có nội dung khả dụng."',
    '"insufficient_item_quantity", "item_not_owned" -> "This action is not available."': '"insufficient_item_quantity", "item_not_owned" -> "Kai không có đủ vật phẩm cần thiết cho hành động này."',
    'else -> "This action is not available."': 'else -> "Hành động này không khả dụng trong trạng thái hiện tại."',
}
for old, new in translations.items():
    if old in text:
        text = text.replace(old, new)

pickup_line = '      "player_pickup_unavailable" -> "Không thể tự thêm vật phẩm vào Inventory; hãy tìm kiếm hoặc tương tác với môi trường để game xác định kết quả."\n'
party_anchor = '      "party_full" -> "Party đã đủ tối đa bốn thành viên."\n'
if pickup_line not in text:
    if party_anchor not in text:
        raise RuntimeError("validationReply party anchor missing")
    text = text.replace(party_anchor, pickup_line + party_anchor, 1)
FACADE.write_text(text, encoding="utf-8")

java = MAIN.read_text(encoding="utf-8")
old_world_inventory = r'''        boolean allowedNew = false;
        JSONObject beforeFlagsForItem = before.optJSONObject("flags");
        JSONObject beforeMadGodForItem = beforeFlagsForItem != null ? beforeFlagsForItem.optJSONObject("madGod") : null;
        JSONObject explorationForItem = beforeFlagsForItem != null ? beforeFlagsForItem.optJSONObject("exploration") : null;
        JSONObject omnivaultForItem = beforeFlagsForItem != null ? beforeFlagsForItem.optJSONObject("omnivault") : null;
        boolean establishedStructured = false;
        if (explorationForItem != null) establishedStructured = lower(explorationForItem.toString()).contains(lower(name));
        if (!establishedStructured && omnivaultForItem != null) establishedStructured = lower(omnivaultForItem.toString()).contains(lower(name));
        if (!establishedStructured && beforeMadGodForItem != null) establishedStructured = lower(beforeMadGodForItem.toString()).contains(lower(name));
        boolean madGodAlreadySpawned = beforeMadGodForItem != null && beforeMadGodForItem.optBoolean("spawned", false);
        if (existing >= 0) allowedNew = true;
        else if (acquisitionIntent(action)) {
          if (madGod) allowedNew = madGodAlreadySpawned && establishedStructured;
          else if (almond) allowedNew = establishedStructured || rollSuccess(rolls, "almondWater");
          else if (containsAny(action, "copy", "sao chép")) allowedNew = establishedStructured;
          else allowedNew = establishedStructured || rollSuccess(rolls, "loot");
        }
'''
new_world_inventory = r'''        boolean allowedNew = false;
        JSONObject beforeFlagsForItem = before.optJSONObject("flags");
        JSONObject beforeMadGodForItem = beforeFlagsForItem != null ? beforeFlagsForItem.optJSONObject("madGod") : null;
        JSONObject explorationForItem = beforeFlagsForItem != null ? beforeFlagsForItem.optJSONObject("exploration") : null;
        JSONObject omnivaultForItem = beforeFlagsForItem != null ? beforeFlagsForItem.optJSONObject("omnivault") : null;
        boolean establishedStructured = false;
        if (explorationForItem != null) establishedStructured = lower(explorationForItem.toString()).contains(lower(name));
        if (!establishedStructured && omnivaultForItem != null) establishedStructured = lower(omnivaultForItem.toString()).contains(lower(name));
        if (!establishedStructured && beforeMadGodForItem != null) establishedStructured = lower(beforeMadGodForItem.toString()).contains(lower(name));
        boolean madGodAlreadySpawned = beforeMadGodForItem != null && beforeMadGodForItem.optBoolean("spawned", false);
        String acquisitionBasis = lower(op.optString("basis", "")).trim();
        boolean worldAcquisition = acquisitionBasis.equals("world_consequence");
        boolean directAcquisition = acquisitionIntent(action);
        boolean copyIntent = containsAny(action, "copy", "sao chép", "nhân bản", "tạo thêm", "tạo ra thêm", "nhân thêm");
        boolean almondRoll = rollSuccess(rolls, "almondWater");
        boolean lootRoll = rollSuccess(rolls, "loot");
        if (existing >= 0) allowedNew = true;
        else if (madGod) allowedNew = directAcquisition && madGodAlreadySpawned && establishedStructured;
        else if (copyIntent) allowedNew = directAcquisition && establishedStructured;
        else if (almond) allowedNew = (directAcquisition || worldAcquisition) && (establishedStructured || almondRoll);
        else allowedNew = (directAcquisition || worldAcquisition) && (establishedStructured || lootRoll);
'''
if new_world_inventory not in java:
    count = java.count(old_world_inventory)
    if count != 1:
        raise RuntimeError(f"World acquisition reducer expected one legacy match, found {count}")
    java = java.replace(old_world_inventory, new_world_inventory, 1)
MAIN.write_text(java, encoding="utf-8")

builder = KNOWLEDGE_BUILDER.read_text(encoding="utf-8")
prompt_anchor = '      "Inventory chỉ đổi khi Kai thật sự lấy/nhận/copy/trao/mất/tiêu thụ vật; nhìn thấy không đồng nghĩa sở hữu. MadGod roll success chỉ mở discovery route, không tự đưa set vào inventory. " +\n'
world_prompt_line = r'''      "Khi GAMEPLAY_ROLLS hợp lệ tạo loot/Almond Water và reply xác nhận môi trường hoặc NPC thực sự giao vật đó cho Kai, bắt buộc kèm inventory_upsert với basis:\"world_consequence\" trong cùng response; nếu không có op hợp lệ thì không được kể rằng Kai đã nhận hoặc sở hữu vật. " +
'''
if world_prompt_line not in builder:
    if prompt_anchor not in builder:
        raise RuntimeError("Knowledge writer Inventory prompt anchor missing")
    builder = builder.replace(prompt_anchor, prompt_anchor + world_prompt_line, 1)
KNOWLEDGE_BUILDER.write_text(builder, encoding="utf-8")

intent_text = INTENT.read_text(encoding="utf-8")
old_blank_item = '    if (name.isBlank()) return null\n'
new_blank_item = '    if (name.isBlank()) return context.lastReferencedItemId?.let { knownPair(it, context) }\n'
if new_blank_item not in intent_text:
    if intent_text.count(old_blank_item) != 1:
        raise RuntimeError(f"Omnivault blank-item fallback expected one match, found {intent_text.count(old_blank_item)}")
    intent_text = intent_text.replace(old_blank_item, new_blank_item, 1)
INTENT.write_text(intent_text, encoding="utf-8")

command_text = COMMAND.read_text(encoding="utf-8")
resolver_anchor = ''') {
  fun resolve(candidate: IntentCandidate, index: Int, turnId: String, context: GameContext): GameCommand? {
'''
resolver_with_sequence = ''') {
  fun resolveSequence(candidates: List<IntentCandidate>, turnId: String, context: GameContext): List<GameCommand?> {
    var resolutionContext = context
    return candidates.mapIndexed { index, candidate ->
      val command = resolve(candidate, index, turnId, resolutionContext)
      val itemId = when (command) {
        is ItemCommand -> command.itemId
        is OmnivaultCommand -> command.itemId
        else -> null
      }
      if (itemId != null) resolutionContext = resolutionContext.copy(lastReferencedItemId = itemId)
      command
    }
  }

  fun resolve(candidate: IntentCandidate, index: Int, turnId: String, context: GameContext): GameCommand? {
'''
if 'fun resolveSequence(candidates: List<IntentCandidate>' not in command_text:
    if command_text.count(resolver_anchor) != 1:
        raise RuntimeError(f"CommandResolver sequence anchor expected one match, found {command_text.count(resolver_anchor)}")
    command_text = command_text.replace(resolver_anchor, resolver_with_sequence, 1)

old_quantity = '    val quantity = quantityResolver.resolve(candidate.clause)\n'
new_quantity = '''    val rawQuantity = quantityResolver.resolve(candidate.clause)
    val quantity = resolvedQuantity(candidate, actor, item, rawQuantity, context)
'''
if new_quantity not in command_text:
    if command_text.count(old_quantity) != 1:
        raise RuntimeError(f"CommandResolver quantity anchor expected one match, found {command_text.count(old_quantity)}")
    command_text = command_text.replace(old_quantity, new_quantity, 1)

helper_anchor = '''  private fun itemCommand(id: String, turn: String, actor: String, target: String?, source: CommandSource, operation: ItemCommand.Operation, item: Pair<String, String>, quantity: Int, slot: String? = null) =
'''
quantity_helper = r'''  private fun resolvedQuantity(candidate: IntentCandidate, actor: String, item: Pair<String, String>?, rawQuantity: Int, context: GameContext): Int {
    if (candidate.intent != GameIntent.OMNIVAULT_COPY || item == null) return rawQuantity
    val targetTotal = Regex("(?:thành|tổng\\s+cộng|đủ)\\s+(?:\\d+|một|hai|ba|bốn|năm|sáu|bảy|tám|chín|mười|một\\s+trăm)\\b", RegexOption.IGNORE_CASE)
      .containsMatchIn(candidate.clause)
    if (!targetTotal) return rawQuantity
    val existing = context.state.inventories[actor]?.items?.get(item.first)?.quantity ?: 0
    return (rawQuantity - existing).coerceAtLeast(1)
  }

'''
if 'private fun resolvedQuantity(candidate: IntentCandidate' not in command_text:
    if helper_anchor not in command_text:
        raise RuntimeError("CommandResolver quantity helper anchor missing")
    command_text = command_text.replace(helper_anchor, quantity_helper + helper_anchor, 1)
COMMAND.write_text(command_text, encoding="utf-8")

facade_text = FACADE.read_text(encoding="utf-8")
old_resolve_all = '    val resolvedCommands = interpreted.candidates.mapIndexedNotNull { index, candidate -> resolver.resolve(candidate, index, turnId, context) }\n'
new_resolve_all = '    val resolvedCommands = resolver.resolveSequence(interpreted.candidates, turnId, context).filterNotNull()\n'
if new_resolve_all not in facade_text:
    if facade_text.count(old_resolve_all) != 1:
        raise RuntimeError(f"GameCoreFacade sequential resolver anchor expected one match, found {facade_text.count(old_resolve_all)}")
    facade_text = facade_text.replace(old_resolve_all, new_resolve_all, 1)
FACADE.write_text(facade_text, encoding="utf-8")

TEST.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class OmnivaultNaturalFlowTest {
  private fun withAlmondWater(): GameState {
    val state = GameState.initial()
    val inventory = state.inventories[KAI_ID] ?: InventoryState(KAI_ID)
    val almond = ItemStack("almond-water", "Almond Water", 1, "SEALED")
    return state.copy(inventories = state.inventories + (KAI_ID to inventory.copy(items = inventory.items + (almond.itemId to almond))))
  }

  @Test fun scanThenCopyCarriesReferenceAndTargetsRequestedTotal() {
    val state = withAlmondWater()
    val context = GameContext(state)
    val interpreted = RuleIntentInterpreter().interpretSync("Kai quét Almond Water rồi nhân bản thành 10 chai", context)
    assertEquals(listOf(GameIntent.OMNIVAULT_SCAN, GameIntent.OMNIVAULT_COPY), interpreted.candidates.map { it.intent })
    val commands = CommandResolver().resolveSequence(interpreted.candidates, state.turn.currentTurnId, context).filterNotNull()
    assertEquals(2, commands.size)
    val scan = commands[0] as OmnivaultCommand
    val copy = commands[1] as OmnivaultCommand
    assertEquals("almond-water", scan.itemId)
    assertEquals("almond-water", copy.itemId)
    assertEquals(9, copy.quantity)
    val result = StateReducer.executeAll(state, commands)
    assertTrue(result.applied)
    assertEquals(10, result.state.inventories.getValue(KAI_ID).items.getValue("almond-water").quantity)
    assertEquals("9", result.state.inventories.getValue(KAI_ID).items.getValue("almond-water").metadata["omnivaultCopyCount"])
    assertTrue("almond-water" in result.state.omnivault.markedSourceIds)
    assertEquals("almond-water", result.state.omnivault.scanSlots.single().sourceItemId)
  }

  @Test fun copyWithoutTemplateStillRequiresCanonicalScan() {
    val state = withAlmondWater()
    val rejected = StateReducer.execute(state, OmnivaultCommand(
      "copy-without-scan", state.turn.currentTurnId, KAI_ID, source = CommandSource.RULE,
      operation = OmnivaultCommand.Operation.COPY, itemId = "almond-water", itemName = "Almond Water", quantity = 9
    ))
    assertFalse(rejected.applied)
    assertEquals("scan_template_missing", rejected.validation.reason)
  }

  @Test fun previousTurnReferenceResolvesCopyClauseWithoutRepeatingItemName() {
    val seed = withAlmondWater()
    val state = seed.copy(metadata = seed.metadata + ("lastReferencedItemId" to "almond-water"))
    val context = GameContext(state)
    val candidate = RuleIntentInterpreter().interpretSync("nhân bản thành 10 chai", context).candidates.single()
    assertEquals(GameIntent.OMNIVAULT_COPY, candidate.intent)
    val command = CommandResolver().resolve(candidate, 0, state.turn.currentTurnId, context) as OmnivaultCommand
    assertEquals("almond-water", command.itemId)
    assertEquals(9, command.quantity)
  }

  @Test fun createAdditionalCopiesKeepsAdditiveMeaning() {
    val state = withAlmondWater()
    val context = GameContext(state)
    val candidate = RuleIntentInterpreter().interpretSync("tạo thêm 3 bản Almond Water", context).candidates.single()
    val command = CommandResolver().resolve(candidate, 0, state.turn.currentTurnId, context) as OmnivaultCommand
    assertEquals(3, command.quantity)
  }
}
''', encoding="utf-8")

final_facade = FACADE.read_text(encoding="utf-8")
final_java = MAIN.read_text(encoding="utf-8")
final_builder = KNOWLEDGE_BUILDER.read_text(encoding="utf-8")
final_intent = INTENT.read_text(encoding="utf-8")
final_command = COMMAND.read_text(encoding="utf-8")
for token in (
    "if (isDirectPlayerPickupAction(action))",
    new_inventory_lock.strip(),
    '(?:thêm|đưa).{0,80}(?:vào|trong)\\\\s+(?:inventory|kho đồ|túi đồ)',
    "player_pickup_unavailable",
    "Hành động này không khả dụng trong trạng thái hiện tại.",
    "resolver.resolveSequence(interpreted.candidates, turnId, context).filterNotNull()",
):
    if token not in final_facade:
        raise RuntimeError(f"Search/world-acquisition/Omnivault facade contract missing: {token}")
for token in (
    'String acquisitionBasis = lower(op.optString("basis", "")).trim();',
    'boolean worldAcquisition = acquisitionBasis.equals("world_consequence");',
    '(directAcquisition || worldAcquisition)',
):
    if token not in final_java:
        raise RuntimeError(f"World acquisition reducer contract missing: {token}")
if 'if (name.isBlank()) return context.lastReferencedItemId?.let { knownPair(it, context) }' not in final_intent:
    raise RuntimeError("Omnivault item-reference contract missing")
for token in (
    'fun resolveSequence(candidates: List<IntentCandidate>',
    'private fun resolvedQuantity(candidate: IntentCandidate',
    'rawQuantity - existing',
):
    if token not in final_command:
        raise RuntimeError(f"Omnivault command-resolution contract missing: {token}")
if 'basis:\\"world_consequence\\"' not in final_builder:
    raise RuntimeError("Writer prompt does not require world_consequence Inventory handoff")
if old_inventory_lock in final_facade or old_inventory_assertion in final_facade:
    raise RuntimeError("Legacy inventory false-positive lock survived")

print("World handoffs remain synchronized; Omnivault Scan -> Copy now preserves item references and requested total quantities while keeping the 3-slot/template rules authoritative.")