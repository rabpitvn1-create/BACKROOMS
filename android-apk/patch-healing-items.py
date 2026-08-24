from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"

HEALING = CORE / "HealingItems.kt"
ITEM_CONTENT = CORE / "ItemContent.kt"
ENGINES = CORE / "Engines.kt"
TEST = TESTS / "HealingItemTest.kt"
ITEM_CATALOG = CORE / "ItemCatalog.kt"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


# The official whole-unit item catalog supersedes the historical two-item healing generator.
# Keep a small compatibility facade because later Omnivault finalizers intentionally recognize
# HealingItems.normalize(), but never restore the retired IDs, stats, or independent loot rules.
MODERN_OFFICIAL_ITEMS = ITEM_CATALOG.exists() and 'OfficialItem(BANDAGE, "Bandage"' in ITEM_CATALOG.read_text(encoding="utf-8")
if MODERN_OFFICIAL_ITEMS:
    modern_healing = r'''package com.rabpit.backroom.core

const val BANDAGE_ID = ItemCatalog.BANDAGE
const val ANTISEPTIC_ID = ItemCatalog.ANTISEPTIC

object HealingItems {
  const val DROP_ROLL_KEY = "official-item-pool"
  const val BANDAGE_NAME = "Bandage"
  const val ANTISEPTIC_NAME = "Antiseptic"
  const val BANDAGE_HEAL_HP = 15
  const val ANTISEPTIC_HEAL_HP = 10

  private fun key(item: ItemStack): String = "${item.itemId} ${item.archetypeId} ${item.name}".lowercase()
  fun healAmount(item: ItemStack): Int = when {
    key(item).contains("bandage") || key(item).contains("băng gạc") -> BANDAGE_HEAL_HP
    key(item).contains("antiseptic") || key(item).contains("thuốc sát trùng") -> ANTISEPTIC_HEAL_HP
    else -> 0
  }

  fun normalize(item: ItemStack): ItemStack? {
    val id = when (healAmount(item)) {
      BANDAGE_HEAL_HP -> BANDAGE_ID
      ANTISEPTIC_HEAL_HP -> ANTISEPTIC_ID
      else -> return null
    }
    val canonical = ItemCatalog.stack(id) ?: return null
    return canonical.copy(
      itemId = id,
      quantity = item.quantity,
      condition = item.condition,
      metadata = canonical.metadata + item.metadata - "remainingContent" - "contentAmount" - "contentPercent" - "contentState"
    )
  }
}
'''
    HEALING.write_text(modern_healing, encoding="utf-8")

    item_content = ITEM_CONTENT.read_text(encoding="utf-8")
    hook = '    HealingItems.normalize(item)?.let { return it }\n'
    if hook not in item_content:
        anchor = '  fun normalize(item: ItemStack): ItemStack {\n'
        item_content = replace_once(item_content, anchor, anchor + hook, "Official healing compatibility hook")
        ITEM_CONTENT.write_text(item_content, encoding="utf-8")

    engines = ENGINES.read_text(encoding="utf-8")
    old_heal = '''  private fun heal(state: GameState, amount: Int): GameState {
    val max = state.metadata["combat.playerMaxHp"]?.toIntOrNull()?.coerceAtLeast(1) ?: 100
    val hp = state.metadata["combat.playerHp"]?.toIntOrNull()?.coerceIn(0, max) ?: max
    return state.copy(metadata = state.metadata + mapOf("combat.playerMaxHp" to max.toString(), "combat.playerHp" to (hp + amount).coerceAtMost(max).toString()))
  }
'''
    authoritative_heal = '''  private fun heal(state: GameState, amount: Int): GameState {
    val character = state.characters[KAI_ID] ?: return state
    val maxHp = CharacterStatEngine.effective(state, KAI_ID).maxHp
    val currentHp = character.vitalState.currentHp.coerceIn(0, maxHp)
    if (character.presence == CharacterPresence.DEAD || currentHp <= 0) return state
    return CharacterStatEngine.setCurrentHp(state, KAI_ID, (currentHp + amount).coerceAtMost(maxHp))
  }
'''
    engines = replace_once(engines, old_heal, authoritative_heal, "Official healing HP authority")
    ENGINES.write_text(engines, encoding="utf-8")

    TEST.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class HealingItemTest {
  @Test fun officialHealingCompatibilityUsesFinalStatsAndPool() {
    assertEquals(ItemCatalog.BANDAGE, BANDAGE_ID)
    assertEquals(ItemCatalog.ANTISEPTIC, ANTISEPTIC_ID)
    assertEquals(15, HealingItems.BANDAGE_HEAL_HP)
    assertEquals(10, HealingItems.ANTISEPTIC_HEAL_HP)
    assertEquals("official-item-pool", HealingItems.DROP_ROLL_KEY)
    assertEquals(11, ItemCatalog.items.size)
  }
}
''', encoding="utf-8")
    print("Official healing compatibility prepared: Bandage +15, Antiseptic +10, shared 11-item pool.")


# ---------------------------------------------------------------------------
# Healing-item catalog. These remain ordinary generic loot: there is no
# dedicated bandage/antiseptic RNG. The existing per-Level `loot` roll owns
# discovery chance, so their drop chance follows the same gate as other loot.
# ---------------------------------------------------------------------------
HEALING.write_text(r'''package com.rabpit.backroom.core

const val BANDAGE_ID = "medical:bandage"
const val ANTISEPTIC_ID = "medical:antiseptic"

object HealingItems {
  const val DROP_ROLL_KEY = "loot"
  const val BANDAGE_NAME = "Băng gạc"
  const val ANTISEPTIC_NAME = "Thuốc sát trùng"
  const val BANDAGE_HEAL_HP = 10
  const val ANTISEPTIC_HEAL_HP = 20

  private fun normalizedName(value: String): String = value.trim().lowercase()

  fun healAmount(item: ItemStack): Int = when {
    item.itemId == BANDAGE_ID || item.archetypeId == BANDAGE_ID || normalizedName(item.name) in setOf("băng gạc", "bang gac", "bandage") -> BANDAGE_HEAL_HP
    item.itemId == ANTISEPTIC_ID || item.archetypeId == ANTISEPTIC_ID || normalizedName(item.name) in setOf("thuốc sát trùng", "thuoc sat trung", "antiseptic") -> ANTISEPTIC_HEAL_HP
    else -> 0
  }

  fun normalize(item: ItemStack): ItemStack? {
    val heal = healAmount(item)
    if (heal <= 0) return null
    val bandage = heal == BANDAGE_HEAL_HP
    val id = if (bandage) BANDAGE_ID else ANTISEPTIC_ID
    val name = if (bandage) BANDAGE_NAME else ANTISEPTIC_NAME
    return item.copy(
      itemId = id,
      name = name,
      archetypeId = id,
      contentState = ContentState.NONE,
      metadata = item.metadata + mapOf(
        "consumable" to "true",
        "consumedOnUse" to "true",
        "healHp" to heal.toString(),
        "dropRoll" to DROP_ROLL_KEY,
        "itemCategory" to "medical"
      )
    )
  }
}
''', encoding="utf-8")


# Canonicalize model/story variants such as "bandage" or unaccented Vietnamese
# into the two authoritative item IDs before stacking or use effects are read.
item_content = ITEM_CONTENT.read_text(encoding="utf-8")
normalize_anchor = '''  fun normalize(item: ItemStack): ItemStack {
    val profile = profileFor(item.name, item.archetypeId)
'''
normalize_replacement = '''  fun normalize(item: ItemStack): ItemStack {
    HealingItems.normalize(item)?.let { return it }
    val profile = profileFor(item.name, item.archetypeId)
'''
if not MODERN_OFFICIAL_ITEMS:
    item_content = replace_once(item_content, normalize_anchor, normalize_replacement, "Healing item normalization")
elif '    HealingItems.normalize(item)?.let { return it }\n' not in item_content:
    raise RuntimeError("Official healing compatibility hook disappeared before legacy structure pass")
ITEM_CONTENT.write_text(item_content, encoding="utf-8")


# Apply healing only after the inventory mutation succeeds. The character HP
# path is authoritative and clamps to Effective Max HP. Zero HP / dead state is
# not revived by a consumable, matching the existing no-rescue-at-zero rule.
engines = ENGINES.read_text(encoding="utf-8")
if 'healHp: Int' not in engines:
    start = engines.find('private fun finishItemUse(\n')
    end = engines.find('\nprivate fun useItem(', start)
    if start < 0 or end < 0:
        raise RuntimeError("Healing item finishItemUse anchors missing")
    finish = r'''private fun finishItemUse(
  originalState: GameState,
  inventoryResult: ExecutionResult,
  command: ItemCommand,
  physiologyEffects: Set<String>,
  healHp: Int
): ExecutionResult {
  if (!inventoryResult.applied) return inventoryResult
  if (physiologyEffects.isEmpty() && healHp <= 0) return inventoryResult
  var current = inventoryResult.state
  val events = inventoryResult.events.toMutableList()
  physiologyEffects.forEachIndexed { index, effect ->
    val operation = when (effect) {
      "WATER" -> PhysiologyCommand.Operation.RECORD_WATER
      "FOOD" -> PhysiologyCommand.Operation.RECORD_FOOD
      else -> return ExecutionResult(originalState, false, validation = ValidationResult(false, "physiology_effect_invalid"))
    }
    val physiology = PhysiologyEngine.execute(current, PhysiologyCommand(
      commandId = "${command.commandId}:PHYS:$index",
      turnId = command.turnId,
      actorId = command.actorId,
      targetId = command.actorId,
      source = CommandSource.SYSTEM,
      operation = operation
    ))
    if (!physiology.applied) return ExecutionResult(originalState, false, validation = physiology.validation)
    current = physiology.state
    events += physiology.events
  }
  if (healHp > 0) {
    val character = current.characters[command.actorId]
      ?: return ExecutionResult(originalState, false, validation = ValidationResult(false, "actor_unknown"))
    val maxHp = CharacterStatEngine.effective(current, command.actorId).maxHp
    val beforeHp = character.vitalState.currentHp.coerceIn(0, maxHp)
    val requested = healHp.toLong() * command.quantity.toLong()
    val nextHp = (beforeHp.toLong() + requested).coerceAtMost(maxHp.toLong()).toInt()
    current = CharacterStatEngine.setCurrentHp(current, command.actorId, nextHp)
    events += if (nextHp > beforeHp) "hp_healed:${nextHp - beforeHp}" else "hp_already_full"
  }
  return inventoryResult.copy(state = current, events = events)
}
'''
    engines = engines[:start] + finish + engines[end:]

use_anchor = '''  val physiologyEffects = parsePhysiologyEffects(owned.metadata["physiologyEffect"])
    ?: return invalid(state, "physiology_effect_invalid")
'''
use_replacement = '''  val physiologyEffects = parsePhysiologyEffects(owned.metadata["physiologyEffect"])
    ?: return invalid(state, "physiology_effect_invalid")
  val healingAmount = HealingItems.healAmount(owned)
  if (healingAmount > 0) {
    val actor = state.characters[command.actorId] ?: return invalid(state, "actor_unknown")
    if (actor.presence == CharacterPresence.DEAD || actor.vitalState.currentHp <= 0) {
      return invalid(state, "healing_target_defeated")
    }
  }
'''
engines = replace_once(engines, use_anchor, use_replacement, "Healing item pre-use validation")
old_call = 'finishItemUse(state, inventoryResult, command, physiologyEffects)'
new_call = 'finishItemUse(state, inventoryResult, command, physiologyEffects, healingAmount)'
if old_call in engines:
    engines = engines.replace(old_call, new_call)
if new_call not in engines:
    raise RuntimeError("Healing item finishItemUse call missing")
ENGINES.write_text(engines, encoding="utf-8")


# Give the writer an explicit contract: these two items share the existing
# generic loot roll. A failed loot roll may not be silently converted into a
# bandage/antiseptic discovery, and a successful roll still does not auto-pickup.
main = MAIN.read_text(encoding="utf-8")
if 'HEALING ITEM HARD LOCK:' not in main:
    return_marker = '    return actionDirective + "'
    pos = main.find(return_marker)
    if pos < 0:
        raise RuntimeError("Healing item writerPrompt return anchor missing")
    directive = (
        '    String healingItemDirective = "HEALING ITEM HARD LOCK: Băng gạc là consumable hồi đúng 10 HP; '
        'Thuốc sát trùng là consumable hồi đúng 20 HP. Cả hai dùng chung generic loot roll hiện có của Level '
        '(roll key loot), không có roll riêng, không pity và không tăng/giảm lootThresholds. Chỉ khi loot.success=true '
        'mới được tạo cơ hội phát hiện mới; loot thất bại không được bù bằng hai vật phẩm này. Loot success chỉ mở cơ hội '
        'nhận biết/tương tác, không tự đặt vật phẩm vào Inventory. Khi dùng, hồi không vượt Effective Max HP và không hồi sinh nhân vật 0 HP/DEAD.";\n'
    )
    main = main[:pos] + directive + main[pos:]
    main = main.replace(
        return_marker,
        '    return actionDirective + "\\n" + healingItemDirective + "\\n" + "',
        1,
    )
MAIN.write_text(main, encoding="utf-8")


# Regression tests cover exact heal values, consumption, clamping, and the
# no-revive rule. DROP_ROLL_KEY verifies that no separate healing-item roll was introduced.
TEST.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class HealingItemTest {
  private fun fresh(): GameState = CharacterEquipmentSystem.seedFresh(GameState.initial())

  private fun add(state: GameState, id: String, name: String, quantity: Int = 1): GameState {
    val result = InventoryEngine.execute(state, ItemCommand(
      commandId = "add:$id:$quantity",
      turnId = null,
      actorId = KAI_ID,
      source = CommandSource.SYSTEM,
      operation = ItemCommand.Operation.PICKUP,
      itemId = id,
      itemName = name,
      quantity = quantity
    ))
    assertTrue(result.validation.reason ?: "pickup failed", result.applied)
    return result.state
  }

  private fun use(state: GameState, id: String, name: String, quantity: Int = 1): ExecutionResult =
    InventoryEngine.execute(state, ItemCommand(
      commandId = "use:$id:$quantity",
      turnId = null,
      actorId = KAI_ID,
      source = CommandSource.RULE,
      operation = ItemCommand.Operation.USE,
      itemId = id,
      itemName = name,
      quantity = quantity
    ))

  @Test fun bandageHealsExactlyTenAndConsumesOne() {
    var state = add(fresh(), BANDAGE_ID, "Băng gạc", 2)
    state = CharacterStatEngine.setCurrentHp(state, KAI_ID, 40)
    val result = use(state, BANDAGE_ID, "Băng gạc")
    assertTrue(result.applied)
    assertEquals(50, result.state.characters.getValue(KAI_ID).vitalState.currentHp)
    assertEquals(1, result.state.inventories.getValue(KAI_ID).items.getValue(BANDAGE_ID).quantity)
    assertTrue(result.events.contains("hp_healed:10"))
  }

  @Test fun antisepticHealsTwentyAndClampsToEffectiveMaxHp() {
    var state = add(fresh(), ANTISEPTIC_ID, "Thuốc sát trùng")
    val maxHp = CharacterStatEngine.effective(state, KAI_ID).maxHp
    state = CharacterStatEngine.setCurrentHp(state, KAI_ID, maxHp - 5)
    val result = use(state, ANTISEPTIC_ID, "Thuốc sát trùng")
    assertTrue(result.applied)
    assertEquals(maxHp, result.state.characters.getValue(KAI_ID).vitalState.currentHp)
    assertFalse(result.state.inventories.getValue(KAI_ID).items.containsKey(ANTISEPTIC_ID))
    assertTrue(result.events.contains("hp_healed:5"))
  }

  @Test fun healingItemCannotReviveZeroHpAndIsNotConsumed() {
    var state = add(fresh(), ANTISEPTIC_ID, "Thuốc sát trùng")
    state = CharacterStatEngine.setCurrentHp(state, KAI_ID, 0)
    val result = use(state, ANTISEPTIC_ID, "Thuốc sát trùng")
    assertFalse(result.applied)
    assertEquals("healing_target_defeated", result.validation.reason)
    assertEquals(1, result.state.inventories.getValue(KAI_ID).items.getValue(ANTISEPTIC_ID).quantity)
  }

  @Test fun healingItemsShareTheOrdinaryLootGate() {
    assertEquals("loot", HealingItems.DROP_ROLL_KEY)
    assertEquals(10, HealingItems.BANDAGE_HEAL_HP)
    assertEquals(20, HealingItems.ANTISEPTIC_HEAL_HP)
  }
}
''', encoding="utf-8")

combined = (
    HEALING.read_text(encoding="utf-8") + "\n" +
    ITEM_CONTENT.read_text(encoding="utf-8") + "\n" +
    ENGINES.read_text(encoding="utf-8") + "\n" +
    MAIN.read_text(encoding="utf-8") + "\n" +
    TEST.read_text(encoding="utf-8")
)
for marker in (
    'const val BANDAGE_HEAL_HP = 10',
    'const val ANTISEPTIC_HEAL_HP = 20',
    'const val DROP_ROLL_KEY = "loot"',
    'HealingItems.normalize(item)?.let { return it }',
    '"hp_healed:${nextHp - beforeHp}"',
    '"healing_target_defeated"',
    'HEALING ITEM HARD LOCK:',
    'loot.success=true',
    'class HealingItemTest',
):
    if marker not in combined:
        raise RuntimeError("Healing item contract missing: " + marker)

if 'thresholdRoll("bandage"' in main or 'thresholdRoll("antiseptic"' in main or 'healingLoot' in main:
    raise RuntimeError("Healing items must not introduce a dedicated drop roll")

print("Healing items applied: Bandage +10 HP, Antiseptic +20 HP, shared generic loot gate.")

if MODERN_OFFICIAL_ITEMS:
    # The legacy section above is intentionally allowed to install the structural hooks expected by
    # older finalizers. Restore the official facade and make that hook non-healing; ItemCatalog's
    # OfficialItemEffects remains the single HP/status authority, preventing double healing.
    HEALING.write_text(modern_healing, encoding="utf-8")
    engines = ENGINES.read_text(encoding="utf-8")
    engines = engines.replace(
        '  val healingAmount = HealingItems.healAmount(owned)\n',
        '  val healingAmount = 0 // OfficialItemEffects owns all healing for the 11-item catalog.\n',
        1,
    )
    effect_anchor = '''  fun apply(state: GameState, inventory: InventoryState, item: ItemStack): ExecutionResult {
    var next = state
'''
    effect_guard = '''  fun apply(state: GameState, inventory: InventoryState, item: ItemStack): ExecutionResult {
    val requestedHeal = item.metadata["healHp"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
    if (requestedHeal > 0) {
      val actor = state.characters[KAI_ID] ?: return invalid(state, "actor_unknown")
      if (actor.presence == CharacterPresence.DEAD || actor.vitalState.currentHp <= 0) return invalid(state, "healing_target_defeated")
    }
    var next = state
'''
    engines = replace_once(engines, effect_anchor, effect_guard, "Official healing defeated-state guard")
    ENGINES.write_text(engines, encoding="utf-8")

    main = MAIN.read_text(encoding="utf-8")
    main = main.replace(
        "Băng gạc là consumable hồi đúng 10 HP; Thuốc sát trùng là consumable hồi đúng 20 HP.",
        "Bandage hồi đúng 15 HP và xử lý Bleeding nhẹ; Antiseptic hồi đúng 10 HP và giảm Infection 50%.",
    ).replace(
        "Cả hai dùng chung generic loot roll hiện có của Level (roll key loot)",
        "Cả hai thuộc chung official 11-item Level loot pool",
    )
    MAIN.write_text(main, encoding="utf-8")

    TEST.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class HealingItemTest {
  @Test fun officialHealingCompatibilityUsesFinalStatsAndPool() {
    assertEquals(ItemCatalog.BANDAGE, BANDAGE_ID)
    assertEquals(ItemCatalog.ANTISEPTIC, ANTISEPTIC_ID)
    assertEquals(15, HealingItems.BANDAGE_HEAL_HP)
    assertEquals(10, HealingItems.ANTISEPTIC_HEAL_HP)
    assertEquals("official-item-pool", HealingItems.DROP_ROLL_KEY)
    assertEquals(11, ItemCatalog.items.size)
  }
}
''', encoding="utf-8")
    print("Official healing compatibility finalized without legacy stats or double healing.")
