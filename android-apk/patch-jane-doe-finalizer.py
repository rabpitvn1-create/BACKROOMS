from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
COMBAT = CORE / "CombatRuntime.kt"
ITEMS = CORE / "ItemCatalog.kt"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
ASSET = ROOT / "app/src/main/assets/entity/Jane.png"


def once(text, old, new, label):
    if new in text:
        return text
    if text.count(old) != 1:
        raise RuntimeError(f"{label}: expected one anchor, found {text.count(old)}")
    return text.replace(old, new, 1)


if not ASSET.is_file() or ASSET.stat().st_size <= 0 or ASSET.read_bytes()[:8] != b"\x89PNG\r\n\x1a\n":
    raise RuntimeError("Jane.png is missing, empty, or invalid")

combat = COMBAT.read_text(encoding="utf-8")
combat = once(combat, "  private const val JOHN_DOE_MAX_HP = 1234\n", "  private const val JOHN_DOE_MAX_HP = 2323\n", "Jane HP")
anchor = '  private const val JOHN_DOE_STUN_TURNS_KEY = "combat.johnDoeStunTurns"\n'
extra = '''  private const val JANE_DOE_EVASION_BP = 2500
  private const val JANE_DOE_LILITH_PROC = 10
  private const val JANE_DOE_LILITH_STAT = 5
  private const val JANE_DOE_LILITH_KEY = "combat.janeDoeLilithCoreActive"
  private const val JANE_DOE_MOON_PROC = 20
  private const val JANE_DOE_MOON_DAMAGE = 7
  private const val JANE_DOE_MOON_CD = 3
  private const val JANE_DOE_VOLLEY_PROC = 15
  private const val JANE_DOE_VOLLEY_DAMAGE = 3
  private const val JANE_DOE_VOLLEY_CD = 4
  private const val JANE_DOE_PIN_PROC = 20
  private const val JANE_DOE_PIN_ESCAPE = 15
  private const val JANE_DOE_PIN_CD = 4
'''
if "private const val JANE_DOE_EVASION_BP = 2500" not in combat:
    combat = once(combat, anchor, anchor + extra, "Jane constants")
combat = once(combat, 'Profile(JOHN_DOE_KEY, "John Doe", JOHN_DOE_MAX_HP, 0, 6, 9)', 'Profile(JOHN_DOE_KEY, "Jane Doe", JOHN_DOE_MAX_HP, 0, 6, 9)', "Jane profile")
combat = once(combat, "    val enhancedEntityMaxHp = balancedEntityBaseHp + if (balancedEntityBaseHp > 1000 && profile.key != KAI_DEVIL_WITHIN_KEY) 200 else 0\n", "    val enhancedEntityMaxHp = balancedEntityBaseHp + if (balancedEntityBaseHp > 1000 && profile.key != KAI_DEVIL_WITHIN_KEY && profile.key != JOHN_DOE_KEY) 200 else 0\n", "Jane exact start HP")
combat = once(combat, "    val canonicalMaxHp = balancedEntityBaseHp + if (balancedEntityBaseHp > 1000 && profile.key != KAI_DEVIL_WITHIN_KEY) 200 else 0\n", "    val canonicalMaxHp = balancedEntityBaseHp + if (balancedEntityBaseHp > 1000 && profile.key != KAI_DEVIL_WITHIN_KEY && profile.key != JOHN_DOE_KEY) 200 else 0\n", "Jane exact migrated HP")

helper_anchor = '  private fun janeBleedKey(characterId: String): String = "${PREFIX}jane.bleed.$characterId"\n\n'
helpers = r'''  private fun janeDoeLilith(state: GameState): Boolean = state.metadata[JANE_DOE_LILITH_KEY].equals("true", true)

  private fun janeDoeDamage(state: GameState, maxHp: Int, percent: Int): Int {
    val base = percentDamage(maxHp, percent)
    return if (janeDoeLilith(state)) max(1, (base * 105 + 99) / 100) else base
  }

  private fun entityEvasionBp(state: GameState, entityKey: String): Int =
    if (entityKey != JOHN_DOE_KEY) ENTITY_EVASION_PERCENT * 100
    else if (janeDoeLilith(state)) (JANE_DOE_EVASION_BP * 105 + 99) / 100 else JANE_DOE_EVASION_BP

'''
if "private fun janeDoeLilith(" not in combat:
    combat = once(combat, helper_anchor, helper_anchor + helpers, "Jane helpers")

lilith = r'''    if (c.entityKey == JOHN_DOE_KEY && !janeDoeLilith(resolvedState) && roll(c.copy(eventCounter = c.eventCounter + 1901), 100) < JANE_DOE_LILITH_PROC) {
      resolvedState = withCombatText(resolvedState, JANE_DOE_LILITH_KEY, "true")
      val bonus = percentDamage(c.entityMaxHp, JANE_DOE_LILITH_STAT)
      val maxHp = c.entityMaxHp + bonus
      val hp = min(maxHp, c.entityHp + bonus)
      c = c.copy(entityMaxHp = maxHp, entityHp = hp, entityCondition = condition(hp, maxHp))
      log += "Lilith Core tự kích hoạt: Jane Doe +5% Max HP, +5% Attack và +5% Evasion cho phần còn lại của encounter."
    }

'''
if "Lilith Core tự kích hoạt" not in combat:
    combat = once(combat, "    when (intent) {\n", lilith + "    when (intent) {\n", "Lilith proc")
combat = once(combat, "        val evasionRoll = roll(c.copy(eventCounter = c.eventCounter + 13), 100)\n        val entityEvaded = evasionRoll < ENTITY_EVASION_PERCENT\n", "        val evasionRoll = roll(c.copy(eventCounter = c.eventCounter + 13), 10000)\n        val entityEvaded = evasionRoll < entityEvasionBp(resolvedState, c.entityKey)\n", "Jane evasion")
combat = combat.replace('"${c.entityName} né đòn (17% evasion) và giành lại áp lực."', '"${c.entityName} né đòn (${entityEvasionBp(resolvedState, c.entityKey) / 100.0}% evasion) và giành lại áp lực."')
combat = once(combat, "        val luciaFullAutoEvasionRoll = roll(c.copy(eventCounter = c.eventCounter + 607), 100)\n        if (luciaFullAutoEvasionRoll >= ENTITY_EVASION_PERCENT) {\n", "        val luciaFullAutoEvasionRoll = roll(c.copy(eventCounter = c.eventCounter + 607), 10000)\n        if (luciaFullAutoEvasionRoll >= entityEvasionBp(resolvedState, c.entityKey)) {\n", "Lucia vs Jane evasion")
combat = combat.replace("JOHN_DOE_KEY -> percentDamage(targetMaxHp, JOHN_DOE_ATTACK_PERCENT)", "JOHN_DOE_KEY -> janeDoeDamage(resolvedState, targetMaxHp, JOHN_DOE_ATTACK_PERCENT)")
combat = combat.replace("val damage = percentDamage(c.playerMaxHp, JOHN_DOE_ATTACK_PERCENT)", "val damage = janeDoeDamage(resolvedState, c.playerMaxHp, JOHN_DOE_ATTACK_PERCENT)")

bow = r'''    if (c.entityKey == JOHN_DOE_KEY && c.entityHp > 0) {
      val targets = entityCombatActionTargets(resolvedState)
      if (targets.isNotEmpty() && killerSkillReady(resolvedState, "jane_doe.moonpiercer", c.eventCounter) && roll(c.copy(eventCounter = c.eventCounter + 1931), 100) < JANE_DOE_MOON_PROC) {
        val id = if (KAI_ID in targets) KAI_ID else targets.first()
        val target = resolvedState.characters[id]
        if (target != null) {
          val maxHp = CharacterStatEngine.effective(resolvedState, id).maxHp
          val before = target.vitalState.currentHp.coerceIn(0, maxHp)
          val damage = min(before, janeDoeDamage(resolvedState, maxHp, JANE_DOE_MOON_DAMAGE))
          resolvedState = CharacterStatEngine.setCurrentHp(resolvedState, id, before - damage)
          resolvedState = useKillerSkill(resolvedState, "jane_doe.moonpiercer", c.eventCounter, JANE_DOE_MOON_CD)
          log += "Moonpiercer: Jane Doe bắn xuyên vật che vào ${target.name}, -$damage HP."
        }
      }
      if (targets.isNotEmpty() && killerSkillReady(resolvedState, "jane_doe.thorn_volley", c.eventCounter) && roll(c.copy(eventCounter = c.eventCounter + 1951), 100) < JANE_DOE_VOLLEY_PROC) {
        val hits = mutableListOf<String>()
        targets.forEach { id ->
          val target = resolvedState.characters[id] ?: return@forEach
          val maxHp = CharacterStatEngine.effective(resolvedState, id).maxHp
          val before = target.vitalState.currentHp.coerceIn(0, maxHp)
          if (before <= 0) return@forEach
          val damage = min(before, janeDoeDamage(resolvedState, maxHp, JANE_DOE_VOLLEY_DAMAGE))
          resolvedState = CharacterStatEngine.setCurrentHp(resolvedState, id, before - damage)
          hits += "${target.name} -$damage HP"
        }
        resolvedState = useKillerSkill(resolvedState, "jane_doe.thorn_volley", c.eventCounter, JANE_DOE_VOLLEY_CD)
        if (hits.isNotEmpty()) log += "Thorn Volley: ${hits.joinToString("; ")}."
      }
      if (killerSkillReady(resolvedState, "jane_doe.shadow_pin", c.eventCounter) && roll(c.copy(eventCounter = c.eventCounter + 1973), 100) < JANE_DOE_PIN_PROC) {
        c = c.copy(escapeProgress = max(0, c.escapeProgress - JANE_DOE_PIN_ESCAPE), momentum = max(-3, c.momentum - 1))
        resolvedState = useKillerSkill(resolvedState, "jane_doe.shadow_pin", c.eventCounter, JANE_DOE_PIN_CD)
        log += "Shadow Pin: mũi tên ghim đường rút, -$JANE_DOE_PIN_ESCAPE điểm Escape và -1 Momentum."
      }
    }

'''
poison_anchor = "    if (c.entityKey == JOHN_DOE_KEY && c.eventCounter % JOHN_DOE_POISON_INTERVAL_TURNS == 0 &&\n"
if "Moonpiercer: Jane Doe" not in combat:
    combat = once(combat, poison_anchor, bow + poison_anchor, "Jane bow skills")
combat = combat.replace("John Doe", "Jane Doe")

items = ITEMS.read_text(encoding="utf-8")
start = items.index("object EntityLootEngine {\n")
end = items.index("object LevelLootEngine {\n", start)
engine = r'''object EntityLootEngine {
  const val DROP_CHANCE_PERCENT = 100
  fun dropChancePercent(state: GameState): Int = DROP_CHANCE_PERCENT
  private fun jane(defeatId: String) = defeatId.contains(":john_doe:")
  private fun slot(state: GameState, defeatId: String, n: Int, rng: LootRng): GameState {
    val suffix = if (n == 1) "" else ":$n"
    val marker = "entityLootRolled:$defeatId$suffix"
    val lootId = "entityLoot:$defeatId$suffix"
    if (state.world[marker] != null) return if (state.world[lootId] == null) state else WorldLootAcquisition.acquire(state, lootId, KAI_ID).state
    val item = ItemCatalog.items[rng.nextInt(ItemCatalog.items.size)].stack()
    val selected = state.copy(world = state.world + mapOf(marker to item.itemId, lootId to "${item.itemId}|${item.name}|1|ENTITY_DROP"))
    return WorldLootAcquisition.acquire(selected, lootId, KAI_ID).state
  }
  fun onDefeat(state: GameState, defeatId: String, rng: LootRng): GameState {
    if (defeatId.isBlank()) return state
    var next = slot(state, defeatId, 1, rng)
    if (jane(defeatId)) next = slot(next, defeatId, 2, rng)
    return next
  }
}

'''
items = items[:start] + engine + items[end:]
ITEMS.write_text(items, encoding="utf-8")

start = combat.index("  private fun defeatLootNarration(state: GameState, defeatId: String): String {\n")
end = combat.index("  private fun clearCombatOnly(state: GameState): GameState {\n", start)
narration = r'''  private fun defeatLootNarration(state: GameState, defeatId: String): String {
    val first = state.world["entityLootRolled:$defeatId"] ?: return ""
    val second = state.world["entityLootRolled:$defeatId:2"]
    val names = listOfNotNull(first, second).map { ItemCatalog.find(it)?.name ?: it }.joinToString(" và ")
    val pending = state.world["entityLoot:$defeatId"] != null || state.world["entityLoot:$defeatId:2"] != null
    return if (!pending) " $names đã rơi ra và được tự động thêm vào Inventory của Kai." else " $names đang chờ hoàn tất nhận vào Inventory."
  }

'''
combat = combat[:start] + narration + combat[end:]
COMBAT.write_text(combat, encoding="utf-8")

main = MAIN.read_text(encoding="utf-8")
main = once(main, 'thresholdRoll("johnDoeEncounter", 10000, EntityEncounterPolicy.scaledThreshold(1000),', 'thresholdRoll("johnDoeEncounter", 10000, 1000,', "Jane 10% encounter")
main = main.replace("John Doe", "Jane Doe").replace("John.png", "Jane.png")
MAIN.write_text(main, encoding="utf-8")

ct = TESTS / "CombatRuntimeTest.kt"
t = ct.read_text(encoding="utf-8").replace("John Doe", "Jane Doe")
t = t.replace("assertEquals(1434, CombatRuntime.active(state)!!.entityMaxHp)", "assertEquals(2323, CombatRuntime.active(state)!!.entityMaxHp)")
t = t.replace("assertEquals(minOf(1434, before + 30), CombatRuntime.active(next.state)!!.entityHp)", "assertEquals(minOf(2323, before + 30), CombatRuntime.active(next.state)!!.entityHp)")
ct.write_text(t, encoding="utf-8")
kb = TESTS / "KaiMonsterBalanceTest.kt"
if kb.is_file():
    s = kb.read_text(encoding="utf-8").replace('assertEquals(1434, CombatRuntime.active(CombatRuntime.start(GameState.initial(), "john_doe"))!!.entityMaxHp)', 'assertEquals(2323, CombatRuntime.active(CombatRuntime.start(GameState.initial(), "john_doe"))!!.entityMaxHp)')
    kb.write_text(s, encoding="utf-8")

for marker in ("JOHN_DOE_MAX_HP = 2323", '"Jane Doe", JOHN_DOE_MAX_HP', "JANE_DOE_EVASION_BP = 2500", "Lilith Core tự kích hoạt", "Moonpiercer: Jane Doe", "Thorn Volley:", "Shadow Pin:", "ENTITY_EVASION_PERCENT = 17"):
    if marker not in combat:
        raise RuntimeError("Jane combat contract missing: " + marker)
for marker in ('thresholdRoll("johnDoeEncounter", 10000, 1000,', 'case "john_doe": name = "Jane Doe"; break;', '"john_doe".equals(entityKey) ? "Jane.png"'):
    if marker not in main:
        raise RuntimeError("Jane encounter/asset contract missing: " + marker)
if 'thresholdRoll("johnDoeEncounter", 10000, EntityEncounterPolicy.scaledThreshold(1000),' in main:
    raise RuntimeError("Jane was incorrectly returned to the scaled shared pool")
if 'if (jane(defeatId)) next = slot(next, defeatId, 2, rng)' not in items:
    raise RuntimeError("Jane two-drop contract missing")

print("Jane Doe finalizer applied: exact 2323 HP, 25% base Evasion, Lilith Core, three bow skills, independent 10% encounter, Jane.png, two guaranteed random drops.")
