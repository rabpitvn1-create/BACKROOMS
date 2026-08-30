from pathlib import Path


ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
LEDGER = CORE / "WorldItemLedger.kt"
INTENT = CORE / "IntentPipeline.kt"
ENGINES = CORE / "Engines.kt"
KNOWLEDGE = CORE / "knowledge/KnowledgeContextEngine.kt"
INDEX = ROOT / "app/src/main/assets/index.html"
TEST = TESTS / "ItemInteractionCoherenceTest.kt"


def once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


# 1) Recover known physical items at narration time instead of waiting for pickup.
ledger = LEDGER.read_text(encoding="utf-8")
ledger_anchor = '''  private fun localAvailable(items: JSONArray, location: String): List<Pair<Int, JSONObject>> {
'''
ledger_insert = '''  fun reconcileNarrative(
    flagsJson: String?,
    location: String?,
    narrative: String,
    ownedInventoryJson: String? = null
  ): String {
    val currentLocation = location.orEmpty().trim()
    val root = flags(flagsJson)
    val items = root.optJSONArray("worldItems") ?: JSONArray()
    val firstInferredIndex = items.length()
    inferFromRecentNarrative(items, currentLocation, listOf(narrative))

    val owned = runCatching { JSONArray(ownedInventoryJson ?: "[]") }.getOrElse { JSONArray() }
    val ownedIds = linkedSetOf<String>()
    val ownedNames = linkedSetOf<String>()
    for (index in 0 until owned.length()) {
      val item = owned.optJSONObject(index) ?: continue
      normalized(item.optString("id", "")).takeIf(String::isNotBlank)?.let(ownedIds::add)
      normalized(item.optString("name", "")).takeIf(String::isNotBlank)?.let(ownedNames::add)
    }
    for (index in items.length() - 1 downTo firstInferredIndex) {
      val item = items.optJSONObject(index) ?: continue
      if (normalized(item.optString("id", "")) in ownedIds || normalized(item.optString("name", "")) in ownedNames) {
        items.remove(index)
      }
    }
    root.put("worldItems", items)
    return root.toString()
  }

  private fun localAvailable(items: JSONArray, location: String): List<Pair<Int, JSONObject>> {
'''
ledger = once(ledger, ledger_anchor, ledger_insert, "narrative world-item reconciliation")
LEDGER.write_text(ledger, encoding="utf-8")

main = MAIN.read_text(encoding="utf-8")
main_anchor = '''  private JSONObject applyModelOperations(JSONObject before, JSONArray ops, JSONObject rolls, String action) throws Exception {
'''
main_insert = '''  private void reconcileNarratedWorldItems(JSONObject state, String reply) throws Exception {
    JSONObject flags = state.optJSONObject("flags");
    JSONArray inventory = state.optJSONArray("inventory");
    String updatedFlags = com.rabpit.backroom.core.WorldItemLedger.INSTANCE.reconcileNarrative(
      flags != null ? flags.toString() : null,
      state.optString("location", ""),
      reply,
      inventory != null ? inventory.toString() : "[]"
    );
    state.put("flags", new JSONObject(updatedFlags));
  }

  private JSONObject applyModelOperations(JSONObject before, JSONArray ops, JSONObject rolls, String action) throws Exception {
'''
main = once(main, main_anchor, main_insert, "MainActivity narrated item helper")

candidate_anchor = '''          JSONObject candidateState = meta
            ? new JSONObject(before.toString())
            : applyModelOperations(before, generated.optJSONArray("ops"), rolls, action);
          int risk = meta ? 0 : validatedTurnRisk(before, candidateState, generated);
'''
candidate_new = '''          JSONObject candidateState = meta
            ? new JSONObject(before.toString())
            : applyModelOperations(before, generated.optJSONArray("ops"), rolls, action);
          if (!meta) reconcileNarratedWorldItems(candidateState, reply);
          int risk = meta ? 0 : validatedTurnRisk(before, candidateState, generated);
'''
main = once(main, candidate_anchor, candidate_new, "initial narrated item reconciliation")

repair_anchor = '''            candidateState = applyModelOperations(before, generated.optJSONArray("ops"), rolls, action);
            risk = validatedTurnRisk(before, candidateState, generated);
'''
repair_new = '''            candidateState = applyModelOperations(before, generated.optJSONArray("ops"), rolls, action);
            reconcileNarratedWorldItems(candidateState, reply);
            risk = validatedTurnRisk(before, candidateState, generated);
'''
main = once(main, repair_anchor, repair_new, "repaired narrated item reconciliation")
MAIN.write_text(main, encoding="utf-8")


# 2) Highlight available local world items immediately and keep owned item names highlighted.
index = INDEX.read_text(encoding="utf-8")
highlight_old = '''  function worldItemNames(){
    var current=currentState(),flags=current&&current.flags;
    var items=flags&&Array.isArray(flags.worldItems)?flags.worldItems:[];
    return uniqueNames(items.map(function(item){return item&&item.name}));
  }
'''
highlight_new = '''  function worldItemNames(){
    var current=currentState(),flags=current&&current.flags;
    var items=flags&&Array.isArray(flags.worldItems)?flags.worldItems:[];
    var location=String(current&&current.location||'').trim().toLocaleLowerCase('vi-VN');
    return uniqueNames(items.filter(function(item){
      if(!item||item.available===false||Number(item.quantity||1)<=0)return false;
      var itemLocation=String(item.locationKey||'').trim().toLocaleLowerCase('vi-VN');
      return !location||!itemLocation||location===itemLocation;
    }).map(function(item){return item&&item.name}));
  }
  function ownedItemNames(){
    var current=currentState(),items=current&&Array.isArray(current.inventory)?current.inventory:[];
    return uniqueNames(items.map(function(item){return typeof item==='string'?item:item&&item.name}));
  }
'''
index = once(index, highlight_old, highlight_new, "available/owned item highlight sources")
index = once(
    index,
    "      addNamedRanges(text,worldItemNames(),'item',20,ranges);\n",
    "      addNamedRanges(text,uniqueNames(worldItemNames().concat(ownedItemNames())),'item',20,ranges);\n",
    "combined world/inventory item highlighting",
)
INDEX.write_text(index, encoding="utf-8")


# 3) Recipient aliases are never item candidates. First-person use-on-recipient belongs to Kai.
intent = INTENT.read_text(encoding="utf-8")
actor_start = intent.index("class DefaultActorResolver : ActorResolver {")
actor_end = intent.index("class DefaultTargetResolver : TargetResolver {", actor_start)
actor_block = intent[actor_start:actor_end]
actor_field_old = '''  private val transferVerb = Regex("(?:đưa|trao|chuyển)", RegexOption.IGNORE_CASE)

  override fun resolve(clause: String, context: GameContext): String? {
'''
actor_field_new = r'''  private val transferVerb = Regex("(?:đưa|trao|chuyển)", RegexOption.IGNORE_CASE)
  private val useVerb = Regex("(?:dùng|sử\\s+dụng|uống|ăn)", RegexOption.IGNORE_CASE)
  private val recipientAfterVerb = Regex("(?:cho|lên)\\s+", RegexOption.IGNORE_CASE)

  override fun resolve(clause: String, context: GameContext): String? {
'''
actor_block = once(actor_block, actor_field_old, actor_field_new, "use-on-recipient actor patterns")
intent = intent[:actor_start] + actor_block + intent[actor_end:]
actor_logic_old = '''      // First-person transfer commands without an explicit source belong to Kai.
      if (transferVerb.containsMatchIn(action.value)) return KAI_ID
'''
actor_logic_new = '''      // First-person transfer/use commands without an explicit source belong to Kai.
      if (transferVerb.containsMatchIn(action.value)) return KAI_ID
      if (useVerb.containsMatchIn(action.value) && recipientAfterVerb.find(clause, action.range.last + 1) != null) return KAI_ID
'''
intent = once(intent, actor_logic_old, actor_logic_new, "first-person target use actor")

resolver_header_old = '''class DefaultItemResolver : ItemResolver {
  private val officialVietnameseAliases = linkedMapOf(
'''
resolver_header_new = r'''class DefaultItemResolver : ItemResolver {
  private fun withoutCharacterAliases(clause: String, context: GameContext): String {
    var result = clause
    resolverCharacterAliases(context).forEach { alias -> result = resolverAliasRegex(alias.text).replace(result, " ") }
    return result.replace(Regex("\\s+"), " ").trim()
  }

  private val officialVietnameseAliases = linkedMapOf(
'''
intent = once(intent, resolver_header_old, resolver_header_new, "character alias stripping helper")

source_old = '''    val sourceClause = clause.replace(resultTail, " ")
    officialVietnameseAliases.entries
      .firstOrNull { (alias, _) -> resolverAliasRegex(alias).containsMatchIn(sourceClause) }
      ?.let { (alias, id) -> return id to (ItemCatalog.find(id)?.name ?: alias) }
    context.itemAliases.entries.firstOrNull { sourceClause.contains(it.key, true) }?.let { return it.value to it.key }

    val normalizedClause = normalize(sourceClause)
'''
source_new = '''    val sourceClause = clause.replace(resultTail, " ")
    val itemClause = withoutCharacterAliases(sourceClause, context)
    officialVietnameseAliases.entries
      .firstOrNull { (alias, _) -> resolverAliasRegex(alias).containsMatchIn(itemClause) }
      ?.let { (alias, id) -> return id to (ItemCatalog.find(id)?.name ?: alias) }
    context.itemAliases.entries.firstOrNull { itemClause.contains(it.key, true) }?.let { return it.value to it.key }

    val normalizedClause = normalize(itemClause)
'''
intent = once(intent, source_old, source_new, "item-only resolver clause")
intent = once(
    intent,
    r'''    val name = sourceClause.replace(noise, " ").replace(Regex("[^\\p{L}\\p{N}_ -]+"), " ").replace(Regex("\\s+"), " ").trim()
''',
    r'''    val name = itemClause.replace(noise, " ").replace(Regex("[^\\p{L}\\p{N}_ -]+"), " ").replace(Regex("\\s+"), " ").trim()
''',
    "fallback item name excludes characters",
)
INTENT.write_text(intent, encoding="utf-8")


# 4) Consumables remain owned/consumed by the actor but can affect an explicit living target.
engines = ENGINES.read_text(encoding="utf-8")
finish_sig_old = '''private fun finishItemUse(
  originalState: GameState,
  inventoryResult: ExecutionResult,
  command: ItemCommand,
  physiologyEffects: Set<String>,
  healHp: Int
): ExecutionResult {
'''
finish_sig_new = '''private fun finishItemUse(
  originalState: GameState,
  inventoryResult: ExecutionResult,
  command: ItemCommand,
  beneficiaryId: String,
  physiologyEffects: Set<String>,
  healHp: Int
): ExecutionResult {
'''
engines = once(engines, finish_sig_old, finish_sig_new, "item use beneficiary signature")
engines = once(
    engines,
    "      actorId = command.actorId,\n      targetId = command.actorId,\n",
    "      actorId = beneficiaryId,\n      targetId = beneficiaryId,\n",
    "physiology beneficiary command",
)
engines = once(engines, "    val character = current.characters[command.actorId]\n", "    val character = current.characters[beneficiaryId]\n", "beneficiary health lookup")
engines = once(engines, "    val maxHp = CharacterStatEngine.effective(current, command.actorId).maxHp\n", "    val maxHp = CharacterStatEngine.effective(current, beneficiaryId).maxHp\n", "beneficiary max HP")
engines = once(engines, "    current = CharacterStatEngine.setCurrentHp(current, command.actorId, nextHp)\n", "    current = CharacterStatEngine.setCurrentHp(current, beneficiaryId, nextHp)\n", "beneficiary HP commit")

beneficiary_anchor = '''  val physiologyEffects = parsePhysiologyEffects(owned.metadata["physiologyEffect"])
    ?: return invalid(state, "physiology_effect_invalid")
  val healingAmount = 0 // OfficialItemEffects owns all healing for the 11-item catalog.
'''
beneficiary_new = '''  val physiologyEffects = parsePhysiologyEffects(owned.metadata["physiologyEffect"])
    ?: return invalid(state, "physiology_effect_invalid")
  val targetable = owned.metadata["healHp"]?.toIntOrNull()?.let { it > 0 } == true ||
    physiologyEffects.isNotEmpty() || owned.metadata.containsKey("statusTreatment") || owned.metadata.containsKey("conditionReduction")
  val beneficiaryId = if (targetable) command.targetId ?: command.actorId else command.actorId
  if (beneficiaryId !in state.characters) return invalid(state, "target_unknown")
  val healingAmount = 0 // OfficialItemEffects owns all healing for the 11-item catalog.
'''
engines = once(engines, beneficiary_anchor, beneficiary_new, "targetable consumable beneficiary")
engines = once(engines, "    val effected = OfficialItemEffects.apply(state, command.actorId, source, owned)\n", "    val effected = OfficialItemEffects.apply(state, beneficiaryId, source, owned)\n", "official effects target")
engines = engines.replace("finishItemUse(state, inventoryResult, command, physiologyEffects, healingAmount)", "finishItemUse(state, inventoryResult, command, beneficiaryId, physiologyEffects, healingAmount)")
engines = engines.replace("finishItemUse(state, changed(state, \"item_used\"), command, physiologyEffects, healingAmount)", "finishItemUse(state, changed(state, \"item_used\"), command, beneficiaryId, physiologyEffects, healingAmount)")
if "finishItemUse(state, inventoryResult, command, physiologyEffects, healingAmount)" in engines:
    raise RuntimeError("not all item-use beneficiary call sites were updated")
ENGINES.write_text(engines, encoding="utf-8")


# 5) Give the writer authoritative companion vitals instead of name-only party rows.
knowledge = KNOWLEDGE.read_text(encoding="utf-8")
party_anchor = '''      state.optJSONArray("party")?.let { party -> out.put("party", compactArray(party, 4)) }
      val flags = state.optJSONObject("flags")
'''
party_new = '''      state.optJSONArray("party")?.let { party -> out.put("party", compactArray(party, 4)) }
      state.optJSONObject("partyDetails")?.optJSONArray("members")?.let { members ->
        val vitals = JSONArray()
        for (index in 0 until minOf(members.length(), 4)) {
          val member = members.optJSONObject(index) ?: continue
          vitals.put(JSONObject().apply {
            listOf("id", "name", "presence", "currentHp", "maxHp", "condition").forEach { key ->
              if (member.has(key)) put(key, member.get(key))
            }
          })
        }
        if (vitals.length() > 0) out.put("partyVitals", vitals)
      }
      val flags = state.optJSONObject("flags")
'''
knowledge = once(knowledge, party_anchor, party_new, "party vitals in writer context")
KNOWLEDGE.write_text(knowledge, encoding="utf-8")


TEST.write_text(r'''package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ItemInteractionCoherenceTest {
  private fun luciaState(): GameState {
    val state = LuciaCanon.ensure(GameState.initial())
    return state.copy(party = state.party.copy(memberIds = (state.party.memberIds + LUCIA_ID).distinct()))
  }

  private fun grant(state: GameState, ownerId: String, itemId: String): GameState {
    val item = ItemCatalog.find(itemId)!!
    val result = StateReducer.execute(state, ItemCommand(
      commandId = "grant-$ownerId-$itemId", turnId = state.turn.currentTurnId, actorId = ownerId,
      source = CommandSource.SYSTEM, operation = ItemCommand.Operation.PICKUP,
      itemId = item.id, itemName = item.name, metadata = item.metadata
    ))
    assertTrue(result.validation.reason.orEmpty(), result.applied)
    return result.state
  }

  @Test fun narratedBandageIsAvailableBeforePickupAndOwnedCopiesAreNotDuplicated() {
    val prose = "Trong hốc tường có một gói Bandage còn nguyên niêm phong nằm trên nền."
    val flags = WorldItemLedger.reconcileNarrative(null, "Level 0 / Lobby", prose, "[]")
    val item = JSONObject(flags).getJSONArray("worldItems").getJSONObject(0)
    assertEquals(ItemCatalog.BANDAGE, item.getString("id"))
    assertTrue(item.getBoolean("available"))
    assertEquals("Level 0 / Lobby", item.getString("locationKey"))

    val owned = JSONArray().put(JSONObject().put("id", ItemCatalog.BANDAGE).put("name", "Bandage"))
    val noDuplicate = WorldItemLedger.reconcileNarrative(null, "Level 0 / Lobby", prose, owned.toString())
    assertEquals(0, JSONObject(noDuplicate).getJSONArray("worldItems").length())
  }

  @Test fun omittedTransferUsesRememberedPickupInsteadOfRecipientName() {
    val state = grant(luciaState(), KAI_ID, ItemCatalog.BANDAGE)
    assertEquals(ItemCatalog.BANDAGE, state.metadata["lastReferencedItemId"])
    val context = GameContext(state, mapOf("kai" to KAI_ID, "lucia" to LUCIA_ID))
    val command = CommandResolver().resolve(
      IntentCandidate("Đưa cho Lucia", GameIntent.TRANSFER_ITEM, IntentConfidence.HIGH, .99f, CommandSource.RULE),
      0, state.turn.currentTurnId, context
    ) as ItemCommand
    assertEquals(KAI_ID, command.actorId)
    assertEquals(LUCIA_ID, command.targetId)
    assertEquals(ItemCatalog.BANDAGE, command.itemId)
    val result = StateReducer.execute(state, command)
    assertTrue(result.validation.reason.orEmpty(), result.applied)
    assertFalse(result.state.inventories.getValue(KAI_ID).items.containsKey(ItemCatalog.BANDAGE))
    assertEquals(1, result.state.inventories.getValue(LUCIA_ID).items.getValue(ItemCatalog.BANDAGE).quantity)
  }

  @Test fun omittedTransferWithoutRememberedItemFailsClosed() {
    val state = luciaState()
    val context = GameContext(state, mapOf("kai" to KAI_ID, "lucia" to LUCIA_ID))
    assertNull(CommandResolver().resolve(
      IntentCandidate("Đưa cho Lucia", GameIntent.TRANSFER_ITEM, IntentConfidence.HIGH, .99f, CommandSource.RULE),
      0, state.turn.currentTurnId, context
    ))
  }

  @Test fun kaiCanUseOwnedBandageOnLowHpLucia() {
    var state = grant(luciaState(), KAI_ID, ItemCatalog.BANDAGE)
    state = CharacterStatEngine.setCurrentHp(state, LUCIA_ID, 20)
    val kaiHp = state.characters.getValue(KAI_ID).vitalState.currentHp
    val command = CommandResolver().resolve(
      IntentCandidate("Dùng băng gạc cho Lucia", GameIntent.USE_ITEM, IntentConfidence.HIGH, .99f, CommandSource.RULE),
      0, state.turn.currentTurnId, GameContext(state, mapOf("kai" to KAI_ID, "lucia" to LUCIA_ID))
    ) as ItemCommand
    assertEquals(KAI_ID, command.actorId)
    assertEquals(LUCIA_ID, command.targetId)
    val result = StateReducer.execute(state, command)
    assertTrue(result.validation.reason.orEmpty(), result.applied)
    assertEquals(35, result.state.characters.getValue(LUCIA_ID).vitalState.currentHp)
    assertEquals(kaiHp, result.state.characters.getValue(KAI_ID).vitalState.currentHp)
    assertFalse(result.state.inventories.getValue(KAI_ID).items.containsKey(ItemCatalog.BANDAGE))
  }

  @Test fun otherHealingConsumablesUseTheSameTargetPath() {
    listOf(ItemCatalog.ANTISEPTIC to 10, ItemCatalog.PAINKILLER to 10, ItemCatalog.ALMOND_WATER to 5).forEachIndexed { index, pair ->
      var state = grant(luciaState(), KAI_ID, pair.first)
      state = CharacterStatEngine.setCurrentHp(state, LUCIA_ID, 20)
      val item = ItemCatalog.find(pair.first)!!
      val result = StateReducer.execute(state, ItemCommand(
        commandId = "target-use-$index", turnId = state.turn.currentTurnId,
        actorId = KAI_ID, targetId = LUCIA_ID, source = CommandSource.RULE,
        operation = ItemCommand.Operation.USE, itemId = item.id, itemName = item.name
      ))
      assertTrue("${item.name}: ${result.validation.reason}", result.applied)
      assertEquals(20 + pair.second, result.state.characters.getValue(LUCIA_ID).vitalState.currentHp)
    }
  }

  @Test fun generatedRuntimeCarriesHighlightAndPartyVitalContracts() {
    val html = File("src/main/assets/index.html").readText()
    assertTrue(html.contains("item.available===false"))
    assertTrue(html.contains("worldItemNames().concat(ownedItemNames())"))
    val knowledge = File("src/main/java/com/rabpit/backroom/core/knowledge/KnowledgeContextEngine.kt").readText()
    assertTrue(knowledge.contains("out.put(\"partyVitals\", vitals)"))
    assertTrue(knowledge.contains("\"currentHp\", \"maxHp\""))
  }
}
''', encoding="utf-8")

required = {
    LEDGER: ("fun reconcileNarrative(",),
    MAIN: ("reconcileNarratedWorldItems(candidateState, reply);",),
    INDEX: ("ownedItemNames()", "item.available===false"),
    INTENT: ("withoutCharacterAliases", "recipientAfterVerb"),
    ENGINES: ("beneficiaryId", "OfficialItemEffects.apply(state, beneficiaryId"),
    KNOWLEDGE: ("partyVitals", "currentHp", "maxHp"),
    TEST: ("Đưa cho Lucia", "Dùng băng gạc cho Lucia"),
}
for path, markers in required.items():
    source = path.read_text(encoding="utf-8")
    for marker in markers:
        if marker not in source:
            raise RuntimeError(f"Missing item-interaction contract {marker!r} in {path.name}")

print("Item interaction coherence applied: immediate discovery highlight, recipient-safe transfer, target use, party vitals, regressions.")
