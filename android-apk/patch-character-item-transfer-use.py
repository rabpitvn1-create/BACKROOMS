from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
INTENT = CORE / "IntentPipeline.kt"
ENGINES = CORE / "Engines.kt"
TEST = TESTS / "CharacterItemTransferUseTest.kt"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


# ---------------------------------------------------------------------------
# Issue #124: item commands must distinguish the acting character from the
# recipient. A recipient name after "cho/sang" must never silently become the
# source actor. Character IDs and unambiguous first names are valid aliases so
# Lucia can be addressed naturally even when her display name contains a nick.
# ---------------------------------------------------------------------------
intent = INTENT.read_text(encoding="utf-8")
old_resolvers = r'''class DefaultActorResolver : ActorResolver {
  override fun resolve(clause: String, context: GameContext): String? =
    context.actorAliases.entries.firstOrNull { clause.contains(it.key, true) }?.value ?: KAI_ID
}

class DefaultTargetResolver : TargetResolver {
  override fun resolve(clause: String, context: GameContext): String? = context.actorAliases.entries
    .firstOrNull { it.value != KAI_ID && Regex("\\b${Regex.escape(it.key)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(clause) }?.value
}
'''
new_resolvers = r'''private fun resolverCharacterAliases(context: GameContext): List<Pair<String, String>> {
  val aliases = linkedMapOf<String, String>()
  context.actorAliases.forEach { (alias, id) -> if (alias.isNotBlank()) aliases.putIfAbsent(alias.trim().lowercase(), id) }
  context.state.characters.forEach { (id, character) ->
    if (id.isNotBlank()) aliases.putIfAbsent(id.trim().lowercase(), id)
    val firstName = character.name.trim().substringBefore(' ').trim('"', '\'', '“', '”').lowercase()
    if (firstName.length >= 4) aliases.putIfAbsent(firstName, id)
  }
  return aliases.entries.map { it.key to it.value }.sortedByDescending { it.first.length }
}

private fun resolverAliasRegex(alias: String): Regex = Regex(
  "(?<![\\p{L}\\p{N}_])${Regex.escape(alias)}(?![\\p{L}\\p{N}_])",
  RegexOption.IGNORE_CASE
)

private data class ResolverMention(val start: Int, val end: Int, val id: String)

private fun resolverMentions(clause: String, context: GameContext): List<ResolverMention> =
  resolverCharacterAliases(context).mapNotNull { (alias, id) ->
    resolverAliasRegex(alias).find(clause)?.let { ResolverMention(it.range.first, it.range.last, id) }
  }.sortedWith(compareBy<ResolverMention> { it.start }.thenByDescending { it.end - it.start })

class DefaultActorResolver : ActorResolver {
  private val actionVerb = Regex(
    "(?:nhặt|lượm|cầm\\s+lên|lấy\\s+lên|vứt|thả|bỏ\\s+xuống|đưa|trao|chuyển|dùng|sử\\s+dụng|uống|ăn|trang\\s+bị|đeo|mặc|tháo|cởi)",
    RegexOption.IGNORE_CASE
  )
  private val transferVerb = Regex("(?:đưa|trao|chuyển)", RegexOption.IGNORE_CASE)

  override fun resolve(clause: String, context: GameContext): String? {
    val mentions = resolverMentions(clause, context)
    val action = actionVerb.find(clause)
    if (action != null) {
      // The actor is the nearest explicit character before the action verb.
      mentions.filter { it.end < action.range.first }.maxByOrNull { it.end }?.let { return it.id }
      // "đưa băng gạc cho Lucia" is a first-person player command: Kai is the
      // source and Lucia is the recipient, not both source and target.
      if (transferVerb.containsMatchIn(action.value)) return KAI_ID
    }
    return mentions.firstOrNull()?.id ?: KAI_ID
  }
}

class DefaultTargetResolver : TargetResolver {
  private val transferVerb = Regex("(?:đưa|trao|chuyển)", RegexOption.IGNORE_CASE)

  override fun resolve(clause: String, context: GameContext): String? {
    val aliases = resolverCharacterAliases(context)

    // Explicit recipient syntax has highest authority and can target Kai too,
    // allowing transfers in both directions between Party characters.
    val recipient = aliases.mapNotNull { (alias, id) ->
      val regex = Regex("(?:cho|sang)\\s+${Regex.escape(alias)}(?![\\p{L}\\p{N}_])", RegexOption.IGNORE_CASE)
      regex.find(clause)?.let { Triple(it.range.first, alias.length, id) }
    }.minWithOrNull(compareBy<Triple<Int, Int, String>> { it.first }.thenByDescending { it.second })
    if (recipient != null) return recipient.third

    val transfer = transferVerb.find(clause)
    if (transfer != null) {
      // Legacy "Kai đưa Iris hai chai nước" has no "cho". The first character
      // after the transfer verb is the recipient; a source named before it is
      // therefore never selected as the target.
      return resolverMentions(clause, context)
        .filter { it.start > transfer.range.last }
        .minByOrNull { it.start }
        ?.id
    }

    // Preserve the established non-transfer target behavior.
    return resolverMentions(clause, context).firstOrNull { it.id != KAI_ID }?.id
  }
}
'''
intent = replace_once(intent, old_resolvers, new_resolvers, "Issue 124 actor/recipient resolution")

# Canonical Vietnamese aliases for the official item pool. The UI descriptions
# are Vietnamese, so deterministic commands must accept the same vocabulary.
item_anchor = 'class DefaultItemResolver : ItemResolver {\n  private val pronoun = Regex("\\\\b(?:nó|vật đó|cái đó|món đó|thứ đó)\\\\b", RegexOption.IGNORE_CASE)\n'
item_new = '''class DefaultItemResolver : ItemResolver {
  private val officialVietnameseAliases = linkedMapOf(
    "đèn pin" to ItemCatalog.FLASHLIGHT,
    "bật lửa" to ItemCatalog.LIGHTER,
    "nước hạnh nhân" to ItemCatalog.ALMOND_WATER,
    "thực phẩm đóng hộp" to ItemCatalog.CANNED_FOOD,
    "đồ hộp" to ItemCatalog.CANNED_FOOD,
    "nhiên liệu bật lửa" to ItemCatalog.LIGHTER_FUEL,
    "băng gạc" to ItemCatalog.BANDAGE,
    "thuốc sát trùng" to ItemCatalog.ANTISEPTIC,
    "thuốc giảm đau" to ItemCatalog.PAINKILLER,
    "cá mòi ba cô gái" to ItemCatalog.SARDINES,
    "nước suối la vie" to ItemCatalog.LA_VIE
  )
  private val pronoun = Regex("\\b(?:nó|vật đó|cái đó|món đó|thứ đó)\\b", RegexOption.IGNORE_CASE)
'''
# Use a less escape-sensitive anchor fallback because this source is Kotlin text.
if "officialVietnameseAliases" not in intent:
    simple_anchor = '''class DefaultItemResolver : ItemResolver {
  private val pronoun = Regex("\\b(?:nó|vật đó|cái đó|món đó|thứ đó)\\b", RegexOption.IGNORE_CASE)
'''
    intent = replace_once(intent, simple_anchor, item_new, "Issue 124 official Vietnamese item aliases")

resolve_anchor = '''    val sourceClause = clause.replace(resultTail, " ")
    context.itemAliases.entries.firstOrNull { sourceClause.contains(it.key, true) }?.let { return it.value to it.key }
'''
resolve_new = '''    val sourceClause = clause.replace(resultTail, " ")
    officialVietnameseAliases.entries.firstOrNull { (alias, _) -> resolverAliasRegex(alias).containsMatchIn(sourceClause) }?.let { (alias, id) ->
      return id to (ItemCatalog.find(id)?.name ?: alias)
    }
    context.itemAliases.entries.firstOrNull { sourceClause.contains(it.key, true) }?.let { return it.value to it.key }
'''
intent = replace_once(intent, resolve_anchor, resolve_new, "Issue 124 localized official item resolution")
INTENT.write_text(intent, encoding="utf-8")


# ---------------------------------------------------------------------------
# Official item effects currently hard-code Kai. Route healing, Bleeding
# treatment, Infection and Pain reduction through the ItemCommand actor instead.
# Recharge already follows InventoryState.ownerId and needs no special case.
# ---------------------------------------------------------------------------
engines = ENGINES.read_text(encoding="utf-8")
engines = replace_once(
    engines,
    '    val effected = OfficialItemEffects.apply(state, source, owned)\n',
    '    val effected = OfficialItemEffects.apply(state, command.actorId, source, owned)\n',
    "Issue 124 official item actor call",
)

old_effect_head = '''  fun apply(state: GameState, inventory: InventoryState, item: ItemStack): ExecutionResult {
    val requestedHeal = item.metadata["healHp"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
    if (requestedHeal > 0) {
      val actor = state.characters[KAI_ID] ?: return invalid(state, "actor_unknown")
      if (actor.presence == CharacterPresence.DEAD || actor.vitalState.currentHp <= 0) return invalid(state, "healing_target_defeated")
    }
    var next = state
'''
new_effect_head = '''  fun apply(state: GameState, actorId: String, inventory: InventoryState, item: ItemStack): ExecutionResult {
    val requestedHeal = item.metadata["healHp"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
    if (requestedHeal > 0) {
      val actor = state.characters[actorId] ?: return invalid(state, "actor_unknown")
      if (actor.presence == CharacterPresence.DEAD || actor.vitalState.currentHp <= 0) return invalid(state, "healing_target_defeated")
    }
    var next = state
'''
engines = replace_once(engines, old_effect_head, new_effect_head, "Issue 124 official item actor guard")
engines = replace_once(
    engines,
    '    item.metadata["healHp"]?.toIntOrNull()?.takeIf { it > 0 }?.let { next = heal(next, it) }\n',
    '    item.metadata["healHp"]?.toIntOrNull()?.takeIf { it > 0 }?.let { next = heal(next, actorId, it) }\n',
    "Issue 124 actor healing call",
)
engines = replace_once(
    engines,
    '      "BLEEDING_LIGHT" -> next = treatLightBleeding(next)\n',
    '      "BLEEDING_LIGHT" -> next = treatLightBleeding(next, actorId)\n',
    "Issue 124 actor bleeding treatment",
)
engines = replace_once(
    engines,
    '      "INFECTION_50" -> next = reduceCondition(next, infection = true)\n      "PAIN_50" -> next = reduceCondition(next, infection = false)\n',
    '      "INFECTION_50" -> next = reduceCondition(next, actorId, infection = true)\n      "PAIN_50" -> next = reduceCondition(next, actorId, infection = false)\n',
    "Issue 124 actor condition treatment",
)

old_heal = '''  private fun heal(state: GameState, amount: Int): GameState {
    val character = state.characters[KAI_ID] ?: return state
    val maxHp = CharacterStatEngine.effective(state, KAI_ID).maxHp
    val currentHp = character.vitalState.currentHp.coerceIn(0, maxHp)
    if (character.presence == CharacterPresence.DEAD || currentHp <= 0) return state
    return CharacterStatEngine.setCurrentHp(state, KAI_ID, (currentHp + amount).coerceAtMost(maxHp))
  }
'''
new_heal = '''  private fun heal(state: GameState, actorId: String, amount: Int): GameState {
    val character = state.characters[actorId] ?: return state
    val maxHp = CharacterStatEngine.effective(state, actorId).maxHp
    val currentHp = character.vitalState.currentHp.coerceIn(0, maxHp)
    if (character.presence == CharacterPresence.DEAD || currentHp <= 0) return state
    return CharacterStatEngine.setCurrentHp(state, actorId, (currentHp + amount).coerceAtMost(maxHp))
  }
'''
engines = replace_once(engines, old_heal, new_heal, "Issue 124 actor HP authority")

old_bleed = '''  private fun treatLightBleeding(state: GameState): GameState {
    val kai = state.characters[KAI_ID] ?: return state
    val bleeding = kai.statusIds.mapNotNull(state.statuses::get).firstOrNull {
      it.type.equals("BLEEDING", true) && (it.metadata["tier"]?.lowercase() in setOf(null, "light", "mild", "1"))
    } ?: return state
    return state.copy(statuses = state.statuses - bleeding.id, characters = state.characters + (KAI_ID to kai.copy(statusIds = kai.statusIds - bleeding.id)))
  }
'''
new_bleed = '''  private fun treatLightBleeding(state: GameState, actorId: String): GameState {
    val actor = state.characters[actorId] ?: return state
    val bleeding = actor.statusIds.mapNotNull(state.statuses::get).firstOrNull {
      it.type.equals("BLEEDING", true) && (it.metadata["tier"]?.lowercase() in setOf(null, "light", "mild", "1"))
    } ?: return state
    return state.copy(statuses = state.statuses - bleeding.id, characters = state.characters + (actorId to actor.copy(statusIds = actor.statusIds - bleeding.id)))
  }
'''
engines = replace_once(engines, old_bleed, new_bleed, "Issue 124 actor bleeding authority")

old_condition = '''  private fun reduceCondition(state: GameState, infection: Boolean): GameState {
    val kai = state.characters[KAI_ID] ?: return state
    val p = kai.physiology
    val current = if (infection) p.infectionState else p.painState
'''
new_condition = '''  private fun reduceCondition(state: GameState, actorId: String, infection: Boolean): GameState {
    val actor = state.characters[actorId] ?: return state
    val p = actor.physiology
    val current = if (infection) p.infectionState else p.painState
'''
engines = replace_once(engines, old_condition, new_condition, "Issue 124 actor condition authority")
engines = replace_once(
    engines,
    '    return state.copy(characters = state.characters + (KAI_ID to kai.copy(physiology = nextP)))\n',
    '    return state.copy(characters = state.characters + (actorId to actor.copy(physiology = nextP)))\n',
    "Issue 124 actor physiology write",
)
ENGINES.write_text(engines, encoding="utf-8")


# ---------------------------------------------------------------------------
# Regression: natural Vietnamese commands resolve source/recipient correctly;
# Kai can hand Bandage to Lucia; Lucia consumes her own Bandage, heals herself,
# and Kai's HP is untouched. Zero-HP companions cannot be revived by consumables.
# ---------------------------------------------------------------------------
TEST.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class CharacterItemTransferUseTest {
  private fun partyState(): GameState {
    var state = LuciaCanon.ensure(GameState.initial())
    return state.copy(party = state.party.copy(memberIds = (state.party.memberIds + LUCIA_ID).distinct()))
  }

  private fun commandContext(state: GameState) = GameContext(
    state = state,
    actorAliases = mapOf("kai" to KAI_ID, "lucia" to LUCIA_ID),
    itemAliases = mapOf("bandage" to ItemCatalog.BANDAGE)
  )

  private fun grantBandageTo(state: GameState, ownerId: String): GameState {
    val item = ItemCatalog.find(ItemCatalog.BANDAGE)!!
    val result = InventoryEngine.execute(state, ItemCommand(
      commandId = "grant-bandage-$ownerId",
      turnId = null,
      actorId = ownerId,
      source = CommandSource.SYSTEM,
      operation = ItemCommand.Operation.PICKUP,
      itemId = item.id,
      itemName = item.name,
      quantity = 1,
      metadata = item.metadata
    ))
    assertTrue(result.validation.reason.orEmpty(), result.applied)
    return result.state
  }

  @Test fun transferRecipientDoesNotBecomeSourceActor() {
    val state = partyState()
    val resolver = CommandResolver()
    val transfer = resolver.resolve(
      IntentCandidate("đưa băng gạc cho Lucia", GameIntent.TRANSFER_ITEM, IntentConfidence.HIGH, .99f, CommandSource.RULE),
      0, "TURN_1", commandContext(state)
    ) as ItemCommand
    assertEquals(KAI_ID, transfer.actorId)
    assertEquals(LUCIA_ID, transfer.targetId)
    assertEquals(ItemCatalog.BANDAGE, transfer.itemId)

    val use = resolver.resolve(
      IntentCandidate("Lucia dùng băng gạc", GameIntent.USE_ITEM, IntentConfidence.HIGH, .99f, CommandSource.RULE),
      0, "TURN_1", commandContext(state)
    ) as ItemCommand
    assertEquals(LUCIA_ID, use.actorId)
    assertEquals(ItemCatalog.BANDAGE, use.itemId)

    val reverse = resolver.resolve(
      IntentCandidate("Lucia đưa băng gạc cho Kai", GameIntent.TRANSFER_ITEM, IntentConfidence.HIGH, .99f, CommandSource.RULE),
      0, "TURN_1", commandContext(state)
    ) as ItemCommand
    assertEquals(LUCIA_ID, reverse.actorId)
    assertEquals(KAI_ID, reverse.targetId)
  }

  @Test fun kaiCanTransferBandageAndLuciaCanUseItOnHerself() {
    var state = grantBandageTo(partyState(), KAI_ID)
    val transfer = InventoryEngine.execute(state, ItemCommand(
      commandId = "kai-to-lucia-bandage", turnId = null, actorId = KAI_ID, targetId = LUCIA_ID,
      source = CommandSource.RULE, operation = ItemCommand.Operation.TRANSFER,
      itemId = ItemCatalog.BANDAGE, itemName = "Bandage", quantity = 1
    ))
    assertTrue(transfer.validation.reason.orEmpty(), transfer.applied)
    assertFalse(transfer.state.inventories.getValue(KAI_ID).items.containsKey(ItemCatalog.BANDAGE))
    assertEquals(1, transfer.state.inventories.getValue(LUCIA_ID).items.getValue(ItemCatalog.BANDAGE).quantity)

    state = CharacterStatEngine.setCurrentHp(transfer.state, LUCIA_ID, 50)
    val kaiHpBefore = state.characters.getValue(KAI_ID).vitalState.currentHp
    val used = InventoryEngine.execute(state, ItemCommand(
      commandId = "lucia-use-bandage", turnId = null, actorId = LUCIA_ID,
      source = CommandSource.RULE, operation = ItemCommand.Operation.USE,
      itemId = ItemCatalog.BANDAGE, itemName = "Bandage", quantity = 1
    ))
    assertTrue(used.validation.reason.orEmpty(), used.applied)
    assertEquals(65, used.state.characters.getValue(LUCIA_ID).vitalState.currentHp)
    assertEquals(kaiHpBefore, used.state.characters.getValue(KAI_ID).vitalState.currentHp)
    assertFalse(used.state.inventories.getValue(LUCIA_ID).items.containsKey(ItemCatalog.BANDAGE))
  }

  @Test fun companionAtZeroHpCannotConsumeHealingItem() {
    var state = grantBandageTo(partyState(), LUCIA_ID)
    state = CharacterStatEngine.setCurrentHp(state, LUCIA_ID, 0)
    val used = InventoryEngine.execute(state, ItemCommand(
      commandId = "lucia-zero-bandage", turnId = null, actorId = LUCIA_ID,
      source = CommandSource.RULE, operation = ItemCommand.Operation.USE,
      itemId = ItemCatalog.BANDAGE, itemName = "Bandage", quantity = 1
    ))
    assertFalse(used.applied)
    assertEquals("healing_target_defeated", used.validation.reason)
    assertEquals(1, used.state.inventories.getValue(LUCIA_ID).items.getValue(ItemCatalog.BANDAGE).quantity)
  }
}
''', encoding="utf-8")

combined = INTENT.read_text(encoding="utf-8") + "\n" + ENGINES.read_text(encoding="utf-8") + "\n" + TEST.read_text(encoding="utf-8")
for marker in (
    "resolverCharacterAliases(context: GameContext)",
    "officialVietnameseAliases",
    '"băng gạc" to ItemCatalog.BANDAGE',
    "OfficialItemEffects.apply(state, command.actorId, source, owned)",
    "heal(state: GameState, actorId: String, amount: Int)",
    "treatLightBleeding(state: GameState, actorId: String)",
    "reduceCondition(state: GameState, actorId: String, infection: Boolean)",
    "class CharacterItemTransferUseTest",
    "kaiCanTransferBandageAndLuciaCanUseItOnHerself",
):
    if marker not in combined:
        raise RuntimeError("Issue #124 item transfer/use contract missing: " + marker)

if "state.characters[KAI_ID] ?: return invalid(state, \"actor_unknown\")" in ENGINES.read_text(encoding="utf-8"):
    raise RuntimeError("Issue #124: official healing guard still hard-codes Kai")

print("Issue #124 applied: character-to-character item transfer and actor-owned consumable use are authoritative.")
