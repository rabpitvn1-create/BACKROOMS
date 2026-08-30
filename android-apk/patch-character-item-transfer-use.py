from pathlib import Path
import re

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


def replace_block(source: str, pattern: str, replacement: str, marker: str, label: str) -> str:
    if marker in source:
        return source
    updated, count = re.subn(pattern, replacement, source, count=1, flags=re.DOTALL)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 structural block, found {count}")
    return updated


# ---------------------------------------------------------------------------
# Issue #124 / extensible character item actions.
#
# Character resolution is derived from GameState. New characters do not need a
# new resolver branch: unique ID, full display name, unique first name and
# metadata["aliases"] all become aliases automatically. Ambiguous aliases fail
# closed instead of silently selecting whichever character happened to be
# inserted first.
# ---------------------------------------------------------------------------
intent = INTENT.read_text(encoding="utf-8")
new_resolvers = r'''private data class ResolverAlias(val text: String, val ids: Set<String>)

private fun resolverCharacterAliases(context: GameContext): List<ResolverAlias> {
  val candidates = linkedMapOf<String, MutableSet<String>>()

  fun register(raw: String, id: String) {
    if (id !in context.state.characters) return
    val alias = raw.trim().lowercase().replace(Regex("\\s+"), " ")
    if (alias.isBlank()) return
    candidates.getOrPut(alias) { linkedSetOf() }.add(id)
  }

  context.state.characters.forEach { (id, character) ->
    register(id, id)
    register(character.name, id)
    val firstName = character.name.trim().substringBefore(' ').lowercase()
    if (firstName.length >= 3) register(firstName, id)
    character.metadata["aliases"].orEmpty()
      .split(',', ';', '|')
      .map(String::trim)
      .filter(String::isNotBlank)
      .forEach { register(it, id) }
  }
  context.actorAliases.forEach { (alias, id) -> register(alias, id) }

  return candidates.entries
    .map { (alias, ids) -> ResolverAlias(alias, ids.toSet()) }
    .sortedByDescending { it.text.length }
}

private fun resolverAliasRegex(alias: String): Regex = Regex(
  "(?<![\\p{L}\\p{N}_])${Regex.escape(alias)}(?![\\p{L}\\p{N}_])",
  RegexOption.IGNORE_CASE
)

private data class ResolverMention(val start: Int, val end: Int, val ids: Set<String>)

private fun resolverMentions(clause: String, context: GameContext): List<ResolverMention> =
  resolverCharacterAliases(context).mapNotNull { alias ->
    resolverAliasRegex(alias.text).find(clause)?.let {
      ResolverMention(it.range.first, it.range.last, alias.ids)
    }
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
      val actorMention = mentions
        .filter { it.end < action.range.first }
        .maxWithOrNull(compareBy<ResolverMention> { it.end }.thenBy { it.start })
      if (actorMention != null) return actorMention.ids.singleOrNull()

      // First-person transfer commands without an explicit source belong to Kai.
      if (transferVerb.containsMatchIn(action.value)) return KAI_ID
    }

    val firstMention = mentions.firstOrNull() ?: return KAI_ID
    return firstMention.ids.singleOrNull()
  }
}

class DefaultTargetResolver : TargetResolver {
  private val transferVerb = Regex("(?:đưa|trao|chuyển)", RegexOption.IGNORE_CASE)

  override fun resolve(clause: String, context: GameContext): String? {
    val aliases = resolverCharacterAliases(context)

    // Explicit "cho/sang <character>" recipient syntax has highest authority.
    // If that alias belongs to more than one character, resolution fails closed.
    val recipient = aliases.mapNotNull { alias ->
      val regex = Regex(
        "(?:cho|sang)\\s+${Regex.escape(alias.text)}(?![\\p{L}\\p{N}_])",
        RegexOption.IGNORE_CASE
      )
      regex.find(clause)?.let { Triple(it.range.first, alias.text.length, alias) }
    }.minWithOrNull(
      compareBy<Triple<Int, Int, ResolverAlias>> { it.first }.thenByDescending { it.second }
    )
    if (recipient != null) return recipient.third.ids.singleOrNull()

    val transfer = transferVerb.find(clause)
    if (transfer != null) {
      // Also support "Kai đưa Iris hai chai nước" without a "cho" connector.
      return resolverMentions(clause, context)
        .filter { it.start > transfer.range.last }
        .minWithOrNull(compareBy<ResolverMention> { it.start }.thenByDescending { it.end - it.start })
        ?.ids
        ?.singleOrNull()
    }

    return resolverMentions(clause, context)
      .firstOrNull { mention -> mention.ids.singleOrNull()?.let { it != KAI_ID } == true }
      ?.ids
      ?.singleOrNull()
  }
}
'''
intent = replace_block(
    intent,
    r'class DefaultActorResolver : ActorResolver \{.*?\n\}\n\nclass DefaultTargetResolver : TargetResolver \{.*?\n\}\n\n(?=class DefaultQuantityResolver)',
    new_resolvers + "\n\n",
    "private data class ResolverAlias",
    "Issue 124 generic actor/recipient resolver",
)

# Canonical Vietnamese aliases for the current official item pool. Future
# characters remain fully data-driven; this map only translates item vocabulary.
official_aliases = r'''  private val officialVietnameseAliases = linkedMapOf(
    "đèn pin" to ItemCatalog.FLASHLIGHT,
    "bật lửa" to ItemCatalog.LIGHTER,
    "nước hạnh nhân" to ItemCatalog.ALMOND_WATER,
    "thực phẩm đóng hộp" to ItemCatalog.CANNED_FOOD,
    "đồ hộp" to ItemCatalog.CANNED_FOOD,
    "pin" to ItemCatalog.BATTERY,
    "nhiên liệu bật lửa" to ItemCatalog.LIGHTER_FUEL,
    "băng gạc" to ItemCatalog.BANDAGE,
    "thuốc sát trùng" to ItemCatalog.ANTISEPTIC,
    "thuốc giảm đau" to ItemCatalog.PAINKILLER,
    "cá mòi ba cô gái" to ItemCatalog.SARDINES,
    "nước suối la vie" to ItemCatalog.LA_VIE
  )
'''
if "officialVietnameseAliases" not in intent:
    class_anchor = "class DefaultItemResolver : ItemResolver {\n"
    intent = replace_once(
        intent,
        class_anchor,
        class_anchor + official_aliases,
        "Issue 124 official Vietnamese item aliases",
    )

alias_lookup = r'''    officialVietnameseAliases.entries
      .firstOrNull { (alias, _) -> resolverAliasRegex(alias).containsMatchIn(sourceClause) }
      ?.let { (alias, id) -> return id to (ItemCatalog.find(id)?.name ?: alias) }
'''
if "officialVietnameseAliases.entries" not in intent:
    source_anchor = '    val sourceClause = clause.replace(resultTail, " ")\n'
    intent = replace_once(
        intent,
        source_anchor,
        source_anchor + alias_lookup,
        "Issue 124 localized official item resolution",
    )

INTENT.write_text(intent, encoding="utf-8")


# ---------------------------------------------------------------------------
# Item effects are actor-owned.
#
# Inventory transfer was already owner/target based, but official consumable
# effects still hard-coded Kai. Route HP, bleeding, infection and pain through
# command.actorId so every present/future CharacterState uses the same engine.
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
# Regression coverage deliberately uses synthetic future characters. Lucia is
# retained as the original bug reproduction, while Mika/Reina prove that adding
# a CharacterState plus metadata aliases is enough; no resolver code branch is
# required. Duplicate aliases must resolve to nothing instead of the wrong NPC.
# ---------------------------------------------------------------------------
TEST.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class CharacterItemTransferUseTest {
  private fun luciaPartyState(): GameState {
    val state = LuciaCanon.ensure(GameState.initial())
    return state.copy(party = state.party.copy(memberIds = (state.party.memberIds + LUCIA_ID).distinct()))
  }

  private fun genericPartyState(): GameState {
    val initial = GameState.initial()
    val mikaId = "future:mika"
    val reinaId = "future:reina"
    val mika = CharacterState(
      mikaId,
      "Mika Sol",
      metadata = mapOf("aliases" to "mika,mika sol,msol", "inventoryProfile" to "normal")
    )
    val reina = CharacterState(
      reinaId,
      "Reina Kuroha",
      metadata = mapOf("aliases" to "reina,reina kuroha,kuroha", "inventoryProfile" to "normal")
    )
    return initial.copy(
      characters = initial.characters + (mikaId to mika) + (reinaId to reina),
      inventories = initial.inventories + (mikaId to InventoryState(mikaId)) + (reinaId to InventoryState(reinaId)),
      party = initial.party.copy(memberIds = listOf(KAI_ID, mikaId, reinaId))
    )
  }

  private fun context(state: GameState) = GameContext(
    state = state,
    actorAliases = mapOf("kai" to KAI_ID),
    itemAliases = mapOf("bandage" to ItemCatalog.BANDAGE)
  )

  private fun grant(state: GameState, ownerId: String, itemId: String): GameState {
    val item = ItemCatalog.find(itemId)!!
    val result = InventoryEngine.execute(state, ItemCommand(
      commandId = "grant-$ownerId-$itemId",
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

  @Test fun originalLuciaTransferAndUseCommandsResolveCorrectly() {
    val state = luciaPartyState()
    val resolver = CommandResolver()
    val localContext = GameContext(
      state = state,
      actorAliases = mapOf("kai" to KAI_ID, "lucia" to LUCIA_ID),
      itemAliases = mapOf("bandage" to ItemCatalog.BANDAGE)
    )

    val transfer = resolver.resolve(
      IntentCandidate("đưa băng gạc cho Lucia", GameIntent.TRANSFER_ITEM, IntentConfidence.HIGH, .99f, CommandSource.RULE),
      0, "TURN_1", localContext
    ) as ItemCommand
    assertEquals(KAI_ID, transfer.actorId)
    assertEquals(LUCIA_ID, transfer.targetId)
    assertEquals(ItemCatalog.BANDAGE, transfer.itemId)

    val use = resolver.resolve(
      IntentCandidate("Lucia dùng băng gạc", GameIntent.USE_ITEM, IntentConfidence.HIGH, .99f, CommandSource.RULE),
      0, "TURN_1", localContext
    ) as ItemCommand
    assertEquals(LUCIA_ID, use.actorId)
    assertEquals(ItemCatalog.BANDAGE, use.itemId)

    val reverse = resolver.resolve(
      IntentCandidate("Lucia đưa băng gạc cho Kai", GameIntent.TRANSFER_ITEM, IntentConfidence.HIGH, .99f, CommandSource.RULE),
      0, "TURN_1", localContext
    ) as ItemCommand
    assertEquals(LUCIA_ID, reverse.actorId)
    assertEquals(KAI_ID, reverse.targetId)
  }

  @Test fun futureCharactersResolveFromStateAndMetadataWithoutBranches() {
    val state = genericPartyState()
    val resolver = CommandResolver()

    val transfer = resolver.resolve(
      IntentCandidate("Mika đưa băng gạc cho Kuroha", GameIntent.TRANSFER_ITEM, IntentConfidence.HIGH, .99f, CommandSource.RULE),
      0, "TURN_1", context(state)
    ) as ItemCommand
    assertEquals("future:mika", transfer.actorId)
    assertEquals("future:reina", transfer.targetId)
    assertEquals(ItemCatalog.BANDAGE, transfer.itemId)

    val use = resolver.resolve(
      IntentCandidate("msol dùng băng gạc", GameIntent.USE_ITEM, IntentConfidence.HIGH, .99f, CommandSource.RULE),
      0, "TURN_1", context(state)
    ) as ItemCommand
    assertEquals("future:mika", use.actorId)
  }

  @Test fun ambiguousCharacterAliasFailsClosed() {
    val initial = genericPartyState()
    val alexOne = CharacterState("future:alex-1", "Alex One", metadata = mapOf("aliases" to "alex"))
    val alexTwo = CharacterState("future:alex-2", "Alex Two", metadata = mapOf("aliases" to "alex"))
    val state = initial.copy(
      characters = initial.characters + (alexOne.id to alexOne) + (alexTwo.id to alexTwo),
      inventories = initial.inventories + (alexOne.id to InventoryState(alexOne.id)) + (alexTwo.id to InventoryState(alexTwo.id))
    )
    val resolved = CommandResolver().resolve(
      IntentCandidate("Alex dùng băng gạc", GameIntent.USE_ITEM, IntentConfidence.HIGH, .99f, CommandSource.RULE),
      0, "TURN_1", context(state)
    )
    assertNull(resolved)
  }

  @Test fun genericCharactersCanTransferAndUseHealingItems() {
    val mikaId = "future:mika"
    val reinaId = "future:reina"
    var state = grant(genericPartyState(), mikaId, ItemCatalog.BANDAGE)

    val transfer = InventoryEngine.execute(state, ItemCommand(
      commandId = "mika-to-reina-bandage", turnId = null, actorId = mikaId, targetId = reinaId,
      source = CommandSource.RULE, operation = ItemCommand.Operation.TRANSFER,
      itemId = ItemCatalog.BANDAGE, itemName = "Bandage", quantity = 1
    ))
    assertTrue(transfer.validation.reason.orEmpty(), transfer.applied)
    assertFalse(transfer.state.inventories.getValue(mikaId).items.containsKey(ItemCatalog.BANDAGE))
    assertEquals(1, transfer.state.inventories.getValue(reinaId).items.getValue(ItemCatalog.BANDAGE).quantity)

    state = CharacterStatEngine.setCurrentHp(transfer.state, reinaId, 50)
    val kaiHpBefore = state.characters.getValue(KAI_ID).vitalState.currentHp
    val used = InventoryEngine.execute(state, ItemCommand(
      commandId = "reina-use-bandage", turnId = null, actorId = reinaId,
      source = CommandSource.RULE, operation = ItemCommand.Operation.USE,
      itemId = ItemCatalog.BANDAGE, itemName = "Bandage", quantity = 1
    ))
    assertTrue(used.validation.reason.orEmpty(), used.applied)
    assertEquals(65, used.state.characters.getValue(reinaId).vitalState.currentHp)
    assertEquals(kaiHpBefore, used.state.characters.getValue(KAI_ID).vitalState.currentHp)
    assertFalse(used.state.inventories.getValue(reinaId).items.containsKey(ItemCatalog.BANDAGE))
  }

  @Test fun genericCharacterFoodUseUpdatesOnlyItsPhysiology() {
    val reinaId = "future:reina"
    var state = genericPartyState()
    val reina = state.characters.getValue(reinaId)
    state = state.copy(characters = state.characters + (reinaId to reina.copy(
      physiology = reina.physiology.copy(minutesSinceFood = 180L)
    )))
    state = grant(state, reinaId, ItemCatalog.CANNED_FOOD)

    val used = InventoryEngine.execute(state, ItemCommand(
      commandId = "reina-eat", turnId = null, actorId = reinaId,
      source = CommandSource.RULE, operation = ItemCommand.Operation.USE,
      itemId = ItemCatalog.CANNED_FOOD, itemName = "Canned Food", quantity = 1
    ))
    assertTrue(used.validation.reason.orEmpty(), used.applied)
    assertEquals(0L, used.state.characters.getValue(reinaId).physiology.minutesSinceFood)
  }

  @Test fun zeroHpFutureCharacterCannotConsumeHealingItem() {
    val reinaId = "future:reina"
    var state = grant(genericPartyState(), reinaId, ItemCatalog.BANDAGE)
    state = CharacterStatEngine.setCurrentHp(state, reinaId, 0)
    val used = InventoryEngine.execute(state, ItemCommand(
      commandId = "reina-zero-bandage", turnId = null, actorId = reinaId,
      source = CommandSource.RULE, operation = ItemCommand.Operation.USE,
      itemId = ItemCatalog.BANDAGE, itemName = "Bandage", quantity = 1
    ))
    assertFalse(used.applied)
    assertEquals("healing_target_defeated", used.validation.reason)
    assertEquals(1, used.state.inventories.getValue(reinaId).items.getValue(ItemCatalog.BANDAGE).quantity)
  }
}
''', encoding="utf-8")

combined = (
    INTENT.read_text(encoding="utf-8") + "\n" +
    ENGINES.read_text(encoding="utf-8") + "\n" +
    TEST.read_text(encoding="utf-8")
)
for marker in (
    'character.metadata["aliases"]',
    "private data class ResolverAlias",
    "ids.singleOrNull()",
    "officialVietnameseAliases",
    '"băng gạc" to ItemCatalog.BANDAGE',
    "OfficialItemEffects.apply(state, command.actorId, source, owned)",
    "heal(state: GameState, actorId: String, amount: Int)",
    "treatLightBleeding(state: GameState, actorId: String)",
    "reduceCondition(state: GameState, actorId: String, infection: Boolean)",
    "futureCharactersResolveFromStateAndMetadataWithoutBranches",
    "ambiguousCharacterAliasFailsClosed",
    "genericCharactersCanTransferAndUseHealingItems",
    "genericCharacterFoodUseUpdatesOnlyItsPhysiology",
):
    if marker not in combined:
        raise RuntimeError("Issue #124 extensible character item contract missing: " + marker)

print(
    "Issue #124 finalized: item transfer/use is generic across CharacterState, "
    "metadata aliases are data-driven, ambiguous names fail closed, and item effects follow actorId."
)
