from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
INDEX = ROOT / "app/src/main/assets/index.html"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"

STATS = CORE / "CharacterStats.kt"
PROGRESSION = CORE / "CharacterProgressionCore.kt"
SYSTEM = CORE / "CharacterEquipmentSystem.kt"
CODEC = CORE / "GameStateCodec.kt"
POKER = CORE / "PokerDiceCore.kt"
COMBAT = CORE / "CombatRuntime.kt"
DETAIL = CORE / "CharacterDetailProjection.kt"
DETAIL_JSON = CORE / "CharacterDetailJson.kt"
FACADE = CORE / "GameCoreFacade.kt"
CATALOG = CORE / "CompanionSkillCatalog.kt"
MADGOD = CORE / "MadGodCanon.kt"

def replace_exact(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise RuntimeError(f"{label}: anchor missing")
    return text.replace(old, new, 1)

def replace_re(text: str, pattern: str, repl: str, label: str, flags=re.S) -> str:
    out, count = re.subn(pattern, repl, text, count=1, flags=flags)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    return out

# ---------------------------------------------------------------------------
# 1.1.93a authority: one Base Stat schema only. Compatibility getters are
# derived aliases for older call sites; they are never serialized or upgraded.
# ---------------------------------------------------------------------------
STATS.write_text(r'''package com.rabpit.backroom.core

enum class StatSource { GAMEPLAY_NORMALIZED, GAMEPLAY_FALLBACK }
enum class EnergyMode { INFINITE, FINITE, NOT_APPLICABLE }
enum class CharacterCondition { HEALTHY, HURT, WOUNDED, CRITICAL, DEFEATED, DEAD }

data class EnergyProfile(val mode: EnergyMode = EnergyMode.NOT_APPLICABLE, val max: Int? = null) {
  companion object {
    fun infinite() = EnergyProfile(EnergyMode.INFINITE, null)
    fun finite(max: Int) = EnergyProfile(EnergyMode.FINITE, max.coerceAtLeast(0))
    fun notApplicable() = EnergyProfile(EnergyMode.NOT_APPLICABLE, null)
  }
}

data class HpRegenRule(
  val amountPerCompletedTurn: Int = 0,
  val sourceId: String? = null,
  val enabled: Boolean = false
)

data class CharacterStatProfile(
  val baseMaxHp: Int = CharacterProgressionCore.BASE_MAX_HP,
  val str: Int = CharacterProgressionCore.BASE_STAT,
  val def: Int = CharacterProgressionCore.BASE_STAT,
  val skl: Int = CharacterProgressionCore.BASE_STAT,
  val vit: Int = CharacterProgressionCore.BASE_STAT,
  val energy: EnergyProfile = EnergyProfile.notApplicable(),
  val regen: HpRegenRule = HpRegenRule(),
  val combatRole: String = "UNSPECIFIED",
  val statSource: StatSource = StatSource.GAMEPLAY_NORMALIZED,
  val schema: String = CharacterProgressionCore.SCHEMA
) {
}

data class CharacterVitalState(
  val currentHp: Int = CharacterProgressionCore.BASE_MAX_HP,
  val condition: CharacterCondition = CharacterCondition.HEALTHY,
  val lastRegenCompletedTurnId: String? = null
)

data class EffectiveCharacterStats(
  val maxHp: Int,
  val str: Int,
  val def: Int,
  val skl: Int,
  val vit: Int,
  val criticalChancePercent: Int,
  val evasionPercent: Int,
  val resCriticalPercent: Int,
  val resEvasionPercent: Int,
  val criticalDamagePercent: Int = CharacterStatCore.CRITICAL_DAMAGE_PERCENT,
  val energy: EnergyProfile = EnergyProfile.notApplicable(),
  val regenPerCompletedTurn: Int = 0
) {
}

object CharacterStatProfiles {
  private fun role(id: String): String = when (id.trim().lowercase()) {
    "cao_minh", "kai" -> "VẠN GIỚI MA TÔN / MA ĐẠO KIẾM TU"
    "iris" -> "SCOUT / TARGET ELIMINATOR / DUAL-GUN MARKSMAN"
    "syvial" -> "HIGH-SPEED SWORDSMAN / ASSAULT / COUNTER / EXECUTION"
    "lucia" -> "TACTICAL RIFLEWOMAN / SQUAD LEADER / FOLLOWER"
    "an-nhien", "an_nhien", "annhien" -> "PROTECTED FOLLOWER / NON-COMBAT"
    else -> "UNSPECIFIED"
  }

  private fun energy(id: String): EnergyProfile = when (id.trim().lowercase()) {
    "cao_minh", "kai", "iris", "syvial" -> EnergyProfile.infinite()
    else -> EnergyProfile.notApplicable()
  }

  fun forId(characterId: String): CharacterStatProfile =
    CharacterStatProfile(combatRole = role(characterId), energy = energy(characterId))

  fun initialVitals(characterId: String): CharacterVitalState =
    CharacterVitalState(currentHp = CharacterProgressionCore.BASE_MAX_HP)
}
''', encoding="utf-8")

PROGRESSION.write_text(r'''package com.rabpit.backroom.core

import java.util.Locale
import kotlin.math.max
import kotlin.math.min

object CharacterProgressionCore {
  const val SCHEMA = "core_stats_v1"
  const val BASE_STAT = 5
  const val MAX_STAT = 999
  const val BASE_MAX_HP = 50
  const val CORE_KEY = "characterProgression.coreResource"
  private const val LEGACY_CORE_KEY = "poker.core.count"
  private val STAT_KEYS = setOf("STR", "DEF", "SKL", "VIT")

  data class Upgrade(
    val state: GameState,
    val characterId: String,
    val stat: String,
    val value: Int,
    val cost: Int,
    val coreRemaining: Int
  )

  fun coreCount(state: GameState): Int =
    (state.metadata[CORE_KEY] ?: state.metadata[LEGACY_CORE_KEY])
      ?.toIntOrNull()?.coerceAtLeast(0) ?: 0

  fun baseStat(state: GameState, characterId: String, rawStat: String): Int {
    val p = state.characters[characterId]?.statProfile ?: CharacterStatProfiles.forId(characterId)
    return when (normalizeStat(rawStat)) {
      "STR" -> p.str
      "DEF" -> p.def
      "SKL" -> p.skl
      "VIT" -> p.vit
      else -> BASE_STAT
    }.coerceIn(BASE_STAT, MAX_STAT)
  }

  fun statPercent(stat: Int): Int = 100 + 10 * (stat.coerceIn(BASE_STAT, MAX_STAT) - BASE_STAT)
  fun statPercent(state: GameState, characterId: String, stat: String): Int =
    statPercent(baseStat(state, characterId, stat))

  fun upgradeCost(currentStat: Int): Int =
    1 + (currentStat.coerceIn(BASE_STAT, MAX_STAT) - BASE_STAT) / 2

  fun grantCore(state: GameState, amount: Int): GameState {
    if (amount <= 0) return normalize(state)
    val next = min(Int.MAX_VALUE.toLong(), coreCount(state).toLong() + amount.toLong()).toInt()
    return normalize(state.copy(metadata = sanitizeMetadata(state.metadata) + (CORE_KEY to next.toString())))
  }

  fun upgrade(state: GameState, rawCharacterId: String, rawStat: String): Upgrade {
    val normalized = normalize(state)
    val id = rawCharacterId.trim().lowercase(Locale.ROOT)
    require(id.isNotEmpty() && normalized.characters.containsKey(id)) { "Nhân vật không tồn tại." }
    val stat = normalizeStat(rawStat)
    require(stat in STAT_KEYS) { "Chỉ có thể nâng STR, DEF, SKL hoặc VIT." }
    val character = normalized.characters.getValue(id)
    val current = baseStat(normalized, id, stat)
    check(current < MAX_STAT) { "Chỉ số đã đạt giới hạn." }
    val cost = upgradeCost(current)
    val core = coreCount(normalized)
    check(core >= cost) { "Không đủ Core. Cần $cost Core." }

    val oldMax = CharacterStatCore.effective(normalized, id).maxHp
    val p = character.statProfile
    val nextProfile = when (stat) {
      "STR" -> p.copy(str = current + 1)
      "DEF" -> p.copy(def = current + 1)
      "SKL" -> p.copy(skl = current + 1)
      else -> p.copy(vit = current + 1)
    }
    var next = normalized.copy(
      characters = normalized.characters + (id to character.copy(statProfile = nextProfile)),
      metadata = sanitizeMetadata(normalized.metadata) + (CORE_KEY to (core - cost).toString())
    )
    if (stat == "VIT" && character.vitalState.currentHp > 0) {
      val newMax = CharacterStatCore.effective(next, id).maxHp
      next = setCurrentHp(next, id, character.vitalState.currentHp + max(0, newMax - oldMax))
    }
    return Upgrade(next, id, stat, current + 1, cost, core - cost)
  }

  fun normalize(input: GameState): GameState {
    val migratedCore = coreCount(input)
    var state = input.copy(metadata = sanitizeMetadata(input.metadata) + (CORE_KEY to migratedCore.toString()))
    val chars = state.characters.toMutableMap()
    state.characters.forEach { (id, c) ->
      val raw = c.statProfile
      val canonical = if (raw.schema == SCHEMA) {
        raw.copy(
          baseMaxHp = BASE_MAX_HP,
          str = raw.str.coerceIn(BASE_STAT, MAX_STAT),
          def = raw.def.coerceIn(BASE_STAT, MAX_STAT),
          skl = raw.skl.coerceIn(BASE_STAT, MAX_STAT),
          vit = raw.vit.coerceIn(BASE_STAT, MAX_STAT),
          regen = HpRegenRule(),
          schema = SCHEMA
        )
      } else CharacterStatProfiles.forId(id)
      val provisional = state.copy(characters = state.characters + (id to c.copy(statProfile = canonical)))
      val maxHp = CharacterStatCore.effective(provisional, id).maxHp
      val hp = c.vitalState.currentHp.coerceIn(0, maxHp)
      chars[id] = c.copy(
        statProfile = canonical,
        vitalState = c.vitalState.copy(
          currentHp = hp,
          condition = CharacterStatEngine.conditionFor(hp, maxHp, c.vitalState.condition, c.presence)
        )
      )
    }
    return state.copy(characters = chars)
  }

  fun protectFromCandidate(authoritative: GameState, candidate: GameState): GameState {
    val auth = normalize(authoritative)
    val cand = normalize(candidate)
    val chars = cand.characters.toMutableMap()
    auth.characters.forEach { (id, a) ->
      val c = chars[id] ?: return@forEach
      chars[id] = c.copy(statProfile = a.statProfile, vitalState = a.vitalState)
    }
    return cand.copy(
      characters = chars,
      metadata = sanitizeMetadata(cand.metadata) + (CORE_KEY to coreCount(auth).toString())
    )
  }

  fun sanitizeMetadata(metadata: Map<String, String>): Map<String, String> =
    metadata.filterKeys { key ->
      !key.startsWith("poker.core.") &&
      !key.startsWith("characterRpg") &&
      !key.startsWith("baseStats") &&
      !key.startsWith("explorer") &&
      key != "level" && key != "exp" && key != "EXP"
    }

  fun normalizeStat(raw: String): String {
    val s = raw.trim().uppercase(Locale.ROOT)
    return if (s in STAT_KEYS) s else ""
  }

  private fun setCurrentHp(state: GameState, id: String, hp: Int): GameState {
    val c = state.characters[id] ?: return state
    val maxHp = CharacterStatCore.effective(state, id).maxHp
    val value = hp.coerceIn(0, maxHp)
    return state.copy(characters = state.characters + (id to c.copy(
      vitalState = c.vitalState.copy(
        currentHp = value,
        condition = CharacterStatEngine.conditionFor(value, maxHp, c.vitalState.condition, c.presence)
      )
    )))
  }
}

object CharacterStatCore {
  const val DAI_DAO_MA_TON_STAT_BONUS_PERCENT = 10
  const val CRITICAL_DAMAGE_PERCENT = 150

  fun isCaoMinh(id: String): Boolean = id.equals(KAI_ID, true) || id.equals("cao_minh", true)

  private fun passiveBonus(base: Int, id: String): Int =
    if (isCaoMinh(id)) (base.coerceIn(CharacterProgressionCore.BASE_STAT, CharacterProgressionCore.MAX_STAT) *
      DAI_DAO_MA_TON_STAT_BONUS_PERCENT + 50) / 100 else 0

  fun effective(state: GameState, characterId: String): EffectiveCharacterStats {
    val p = state.characters[characterId]?.statProfile ?: CharacterStatProfiles.forId(characterId)
    val str = (p.str.coerceIn(5, 999) + passiveBonus(p.str, characterId)).coerceAtMost(CharacterProgressionCore.MAX_STAT)
    val def = (p.def.coerceIn(5, 999) + passiveBonus(p.def, characterId)).coerceAtMost(CharacterProgressionCore.MAX_STAT)
    val skl = (p.skl.coerceIn(5, 999) + passiveBonus(p.skl, characterId)).coerceAtMost(CharacterProgressionCore.MAX_STAT)
    val vit = (p.vit.coerceIn(5, 999) + passiveBonus(p.vit, characterId)).coerceAtMost(CharacterProgressionCore.MAX_STAT)
    val maxHp = scaleByPercent(CharacterProgressionCore.BASE_MAX_HP, CharacterProgressionCore.statPercent(vit))
    return EffectiveCharacterStats(
      maxHp = maxHp,
      str = str, def = def, skl = skl, vit = vit,
      criticalChancePercent = criticalChance(skl),
      evasionPercent = evasion(vit),
      resCriticalPercent = criticalResistance(def),
      resEvasionPercent = evasionResistance(skl),
      criticalDamagePercent = CRITICAL_DAMAGE_PERCENT,
      energy = p.energy,
      regenPerCompletedTurn = 0
    )
  }

  fun criticalChance(skl: Int): Int = (5 + (skl.coerceAtLeast(5) - 5) * 2).coerceAtMost(50)
  fun evasion(vit: Int): Int = ((vit.coerceAtLeast(5) - 5) * 2).coerceAtMost(35)
  fun criticalResistance(def: Int): Int = ((def.coerceAtLeast(5) - 5) * 2).coerceAtMost(50)
  fun evasionResistance(skl: Int): Int = ((skl.coerceAtLeast(5) - 5) * 2).coerceAtMost(50)

  fun scaleByPercent(value: Int, percent: Int): Int {
    val scaled = max(0L, value.toLong()) * max(0, percent).toLong()
    return min(Int.MAX_VALUE.toLong(), (scaled + 50L) / 100L).toInt()
  }

  fun basicDamage(baseDamage: Int, str: Int, handPercent: Int = 100, attackPercent: Int = 100): Int {
    var out = scaleByPercent(baseDamage, CharacterProgressionCore.statPercent(str))
    out = scaleByPercent(out, handPercent)
    return scaleByPercent(out, attackPercent)
  }

  fun skillDamage(baseDamage: Int, skillPercent: Int, skl: Int, handPercent: Int = 100): Int {
    var out = scaleByPercent(baseDamage, skillPercent)
    out = scaleByPercent(out, CharacterProgressionCore.statPercent(skl))
    return scaleByPercent(out, handPercent)
  }

  fun defendedIncomingDamage(rawDamage: Int, def: Int): Int {
    if (rawDamage <= 0) return 0
    val percent = CharacterProgressionCore.statPercent(def).coerceAtLeast(1)
    return max(1, ((rawDamage.toLong() * 100L + percent / 2L) / percent).toInt())
  }

  fun criticalDamage(damage: Int): Int = scaleByPercent(damage, CRITICAL_DAMAGE_PERCENT)
  fun effectiveChance(chance: Int, resistance: Int): Int = (chance - resistance).coerceIn(0, 100)
}

object CaoMinhCombatPassive {
  private const val STACK_KEY = "combat.daiDaoMaTonStacks"
  const val HEAL_PERCENT = 10
  const val ATTACK_PER_TURN_PERCENT = 20
  const val CRITICAL_PER_TURN_PERCENT = 20
  const val ALLY_CRITICAL_BONUS_PERCENT = 50
  const val MAX_COMBAT_CRITICAL_PERCENT = 100

  fun stacks(state: GameState): Int = state.metadata[STACK_KEY]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
  fun attackPercent(state: GameState): Int = 100 + ATTACK_PER_TURN_PERCENT * stacks(state)
  fun criticalChance(state: GameState, base: Int): Int =
    (base + CRITICAL_PER_TURN_PERCENT * stacks(state)).coerceIn(0, MAX_COMBAT_CRITICAL_PERCENT)
  fun allyCriticalChance(base: Int): Int = (base + ALLY_CRITICAL_BONUS_PERCENT).coerceIn(0, MAX_COMBAT_CRITICAL_PERCENT)

  fun afterCaoMinhTurn(state: GameState): GameState {
    val c = state.characters[KAI_ID] ?: return state
    if (c.presence == CharacterPresence.DEAD || c.vitalState.currentHp <= 0) return state
    val maxHp = CharacterStatCore.effective(state, KAI_ID).maxHp
    val heal = max(1, (maxHp * HEAL_PERCENT + 50) / 100)
    val hp = min(maxHp, c.vitalState.currentHp + heal)
    val nextStack = stacks(state) + 1
    return state.copy(
      characters = state.characters + (KAI_ID to c.copy(
        vitalState = c.vitalState.copy(currentHp = hp, condition = CharacterStatEngine.conditionFor(hp, maxHp, c.vitalState.condition, c.presence))
      )),
      metadata = state.metadata + (STACK_KEY to nextStack.toString())
    )
  }
}
''', encoding="utf-8")

# ---------------------------------------------------------------------------
# Save schema: serialize only STR/DEF/SKL/VIT. Old schemas reset to 5/5/5/5;
# current HP is retained then clamped by normalize().
# ---------------------------------------------------------------------------
codec = CODEC.read_text(encoding="utf-8")
codec = replace_re(
    codec,
    r'''  private fun characterStatProfile\(value: CharacterStatProfile\) = JSONObject\(\)\.apply \{.*?\n  private fun physiology\(value: PhysiologyState\)''',
    r'''  private fun characterStatProfile(value: CharacterStatProfile) = JSONObject().apply {
    put("schema", CharacterProgressionCore.SCHEMA)
    put("baseMaxHp", CharacterProgressionCore.BASE_MAX_HP)
    put("STR", value.str)
    put("DEF", value.def)
    put("SKL", value.skl)
    put("VIT", value.vit)
  }

  private fun decodeCharacterStatProfile(json: JSONObject?, characterId: String): CharacterStatProfile {
    val fallback = CharacterStatProfiles.forId(characterId)
    if (json == null || json.optString("schema") != CharacterProgressionCore.SCHEMA) return fallback
    return fallback.copy(
      baseMaxHp = CharacterProgressionCore.BASE_MAX_HP,
      str = json.optInt("STR", CharacterProgressionCore.BASE_STAT).coerceIn(5, 999),
      def = json.optInt("DEF", CharacterProgressionCore.BASE_STAT).coerceIn(5, 999),
      skl = json.optInt("SKL", CharacterProgressionCore.BASE_STAT).coerceIn(5, 999),
      vit = json.optInt("VIT", CharacterProgressionCore.BASE_STAT).coerceIn(5, 999),
      schema = CharacterProgressionCore.SCHEMA
    )
  }

  private fun characterVitalState(value: CharacterVitalState) = JSONObject().apply {
    put("currentHp", value.currentHp)
    put("condition", value.condition.name)
  }

  private fun decodeCharacterVitalState(json: JSONObject?, profile: CharacterStatProfile): CharacterVitalState =
    CharacterVitalState(
      currentHp = json?.optInt("currentHp", CharacterProgressionCore.BASE_MAX_HP)?.coerceAtLeast(0)
        ?: CharacterProgressionCore.BASE_MAX_HP,
      condition = enumOr(CharacterCondition.HEALTHY, json?.optString("condition").orEmpty())
    )

  private fun physiology(value: PhysiologyState)''',
    "canonical stat codec helpers"
)
# Normalize every decoded state after follower/equipment migration.
# Match the complete decode() return so nested calls cannot confuse parenthesis balancing.
codec, decoded_return_count = re.subn(
    r'    return CharacterEquipmentSystem\.normalize\(([^\n]+)\)',
    r'    return CharacterProgressionCore.normalize(CharacterEquipmentSystem.normalize(\1))',
    codec,
    count=1,
)
if decoded_return_count != 1:
    raise RuntimeError("Current GameState decode normalization anchor missing")
CODEC.write_text(codec, encoding="utf-8")

# ---------------------------------------------------------------------------
# Equipment remains equipment. Numeric bonus fields may exist for source
# compatibility, but final definitions are neutralized and never affect stats.
# ---------------------------------------------------------------------------
system = SYSTEM.read_text(encoding="utf-8")
system = re.sub(r'bonuses\s*=\s*EquipmentBonuses\([^)]*\)', 'bonuses = EquipmentBonuses()', system)
system = replace_re(
    system,
    r'''data class EquipmentBonuses\(.*?\n\}\n\ndata class WeaponGameplayStats''',
    r'''class EquipmentBonuses {
  fun any() = false
  override fun equals(other: Any?): Boolean = other is EquipmentBonuses
  override fun hashCode(): Int = 0
  override fun toString(): String = "EquipmentBonuses()"
}

data class WeaponGameplayStats''',
    "remove equipment stat schema"
)
stat_engine = r'''object CharacterStatEngine {
  fun effective(state: GameState, characterId: String): EffectiveCharacterStats =
    CharacterStatCore.effective(state, characterId)

  private fun fallback(characterId: String): EffectiveCharacterStats {
    val p = CharacterStatProfiles.forId(characterId)
    val str = p.str.coerceIn(5, 999)
    val def = p.def.coerceIn(5, 999)
    val skl = p.skl.coerceIn(5, 999)
    val vit = p.vit.coerceIn(5, 999)
    return EffectiveCharacterStats(
      maxHp = CharacterStatCore.scaleByPercent(CharacterProgressionCore.BASE_MAX_HP, CharacterProgressionCore.statPercent(vit)),
      str = str, def = def, skl = skl, vit = vit,
      criticalChancePercent = CharacterStatCore.criticalChance(skl),
      evasionPercent = CharacterStatCore.evasion(vit),
      resCriticalPercent = CharacterStatCore.criticalResistance(def),
      resEvasionPercent = CharacterStatCore.evasionResistance(skl),
      energy = p.energy
    )
  }

  fun conditionFor(currentHp: Int, maxHp: Int, old: CharacterCondition? = null, presence: CharacterPresence? = null): CharacterCondition {
    if (presence == CharacterPresence.DEAD || old == CharacterCondition.DEAD) return CharacterCondition.DEAD
    if (currentHp <= 0) return CharacterCondition.DEFEATED
    val ratio = currentHp.toDouble() / maxHp.coerceAtLeast(1).toDouble()
    return when {
      ratio > .75 -> CharacterCondition.HEALTHY
      ratio > .50 -> CharacterCondition.HURT
      ratio > .25 -> CharacterCondition.WOUNDED
      else -> CharacterCondition.CRITICAL
    }
  }

  fun setCurrentHp(state: GameState, characterId: String, hp: Int): GameState {
    val character = state.characters[characterId] ?: return state
    val maxHp = effective(state, characterId).maxHp
    val nextHp = hp.coerceIn(0, maxHp)
    return state.copy(characters = state.characters + (characterId to character.copy(
      vitalState = character.vitalState.copy(
        currentHp = nextHp,
        condition = conditionFor(nextHp, maxHp, character.vitalState.condition, character.presence)
      )
    )))
  }

  fun preserveMissingHp(before: GameState, afterEquipment: GameState, characterId: String): GameState {
    val hp = before.characters[characterId]?.vitalState?.currentHp ?: return afterEquipment
    return setCurrentHp(afterEquipment, characterId, hp)
  }

  fun applyCompletedTurnRegen(state: GameState, completedTurnId: String): GameState =
    CharacterProgressionCore.normalize(state)

  fun weaponDamage(state: GameState, characterId: String): Int {
    val equipmentId = state.characters[characterId]?.equipmentId ?: characterId
    val weaponId = state.equipment[equipmentId]?.slots?.get(EquipmentSlot.WEAPON.key) ?: return 18
    return EquipmentCatalog.definition(weaponId)?.weapon?.dmg ?: 18
  }

  fun skillBaseDamage(state: GameState, characterId: String): Int {
    val sklScaled = CharacterStatCore.scaleByPercent(
      weaponDamage(state, characterId),
      CharacterProgressionCore.statPercent(effective(state, characterId).skl)
    )
    return if (CharacterStatCore.isCaoMinh(characterId)) {
      CharacterStatCore.scaleByPercent(sklScaled, CaoMinhCombatPassive.attackPercent(state))
    } else sklScaled
  }
}

object CombatStatMath {
  fun critChancePercent(skl: Int): Int = CharacterStatCore.criticalChance(skl)
  fun defenseReduction(defRating: Int): Int = 0
  fun agilityDefense(vitRating: Int): Int = 0
}
'''
system = replace_re(system, r'object CharacterStatEngine \{.*?\nobject EquipmentEngine \{', stat_engine + '\nobject EquipmentEngine {', "canonical CharacterStatEngine")
equipment_system = r'''object CharacterEquipmentSystem {
  private const val SCHEMA_VERSION = "2-core-stats-1193a"

  fun seedFresh(state: GameState): GameState = normalizeInternal(state, true)
  fun normalize(state: GameState): GameState = normalizeInternal(state, state.metadata["characterEquipmentSchemaVersion"] != SCHEMA_VERSION)

  private fun normalizeInternal(source: GameState, seedStarting: Boolean): GameState {
    // Preserve the follower compatibility installed earlier in the patch chain.
    // Lucia exists in Core state without being forced into Party.
    val input = LuciaCanon.ensure(source)
    val inventories = input.inventories.toMutableMap()
    val equipment = input.equipment.toMutableMap()
    input.characters.keys.forEach { characterId ->
      var inv = inventories[characterId] ?: InventoryState(characterId)
      val eq = equipment[characterId] ?: EquipmentState(characterId)
      val slots = eq.slots.toMutableMap()
      val loadout = EquipmentCatalog.startingLoadout(characterId)
      if (seedStarting) {
        loadout.forEach { (slot, itemId) ->
          if (slot.key !in slots) slots[slot.key] = itemId
          if (itemId !in inv.items) inv = inv.copy(items = inv.items + (itemId to EquipmentCatalog.stackFor(itemId)))
        }
      }
      slots.values.distinct().forEach { itemId ->
        if (itemId !in inv.items) inv = inv.copy(items = inv.items + (itemId to EquipmentCatalog.stackFor(itemId)))
      }
      inv = inv.copy(items = inv.items.mapValues { (_, stack) -> EquipmentCatalog.mergeDefinitionMetadata(stack) })
      inventories[characterId] = inv
      equipment[characterId] = eq.copy(slots = slots)
    }
    var next = input.copy(
      inventories = inventories,
      equipment = equipment,
      metadata = input.metadata + ("characterEquipmentSchemaVersion" to SCHEMA_VERSION)
    )
    val chars = next.characters.toMutableMap()
    next.characters.forEach { (id, character) ->
      val maxHp = CharacterStatCore.effective(next, id).maxHp
      val hp = character.vitalState.currentHp.coerceIn(0, maxHp)
      chars[id] = character.copy(
        vitalState = character.vitalState.copy(
          currentHp = hp,
          condition = CharacterStatEngine.conditionFor(hp, maxHp, character.vitalState.condition, character.presence)
        ),
        metadata = character.metadata.filterKeys { !it.startsWith("derived.equipment") && it != "derived.effectiveMaxHp" }
      )
    }
    next = next.copy(characters = chars)
    // Fresh/load normalization must expose the canonical 1.1.93a stat schema too,
    // otherwise follower constructors can temporarily leak their retired HP/stat baseline.
    return CharacterProgressionCore.normalize(next)
  }
}
'''
system_start = system.index("object CharacterEquipmentSystem {")
system = system[:system_start] + equipment_system.rstrip() + "\n"
SYSTEM.write_text(system, encoding="utf-8")

# ---------------------------------------------------------------------------
# Poker Dice mechanics are deliberately untouched. Only Core storage/progression
# delegates to the canonical CharacterProgressionCore.
# ---------------------------------------------------------------------------
poker = POKER.read_text(encoding="utf-8")
progression_block = r'''  fun coreCount(state: GameState): Int = CharacterProgressionCore.coreCount(state)

  fun coreStat(state: GameState, characterId: String, stat: String): Int =
    CharacterProgressionCore.baseStat(state, characterId, stat)

  fun statPercent(state: GameState, characterId: String, stat: String): Int =
    CharacterProgressionCore.statPercent(state, characterId, stat)

  fun scaleByPercent(value: Int, percent: Int): Int = CharacterStatCore.scaleByPercent(value, percent)

  fun upgradeCost(currentStat: Int): Int = CharacterProgressionCore.upgradeCost(currentStat)

  fun grantCore(state: GameState, amount: Int): GameState = CharacterProgressionCore.grantCore(state, amount)

  fun upgrade(state: GameState, rawCharacterId: String, rawStat: String): UpgradeResult {
    val result = CharacterProgressionCore.upgrade(state, rawCharacterId, rawStat)
    return UpgradeResult(result.state, result.characterId, result.stat, result.value, result.cost, result.coreRemaining)
  }

  fun statsJson(state: GameState, characterId: String): JSONObject {
    val id = characterId.trim().lowercase(Locale.ROOT)
    val effective = CharacterStatCore.effective(state, id)
    val stats = JSONObject()
    STAT_KEYS.forEach { stat ->
      val base = coreStat(state, id, stat)
      val eff = when (stat) { "STR" -> effective.str; "DEF" -> effective.def; "SKL" -> effective.skl; else -> effective.vit }
      stats.put(stat, JSONObject()
        .put("base", base)
        .put("effective", eff)
        .put("multiplierPercent", statPercent(state, id, stat))
        .put("nextCoreCost", if (base >= MAX_STAT) JSONObject.NULL else upgradeCost(base)))
    }
    return JSONObject()
      .put("schema", CharacterProgressionCore.SCHEMA)
      .put("sharedCore", coreCount(state))
      .put("characterId", id)
      .put("stats", stats)
  }

'''
poker = replace_re(poker, r'  fun coreCount\(state: GameState\): Int =.*?\n  fun prepare\(', progression_block + '  fun prepare(', "Poker Core delegation")
poker = re.sub(r'\n  private fun statKey\(characterId: String, stat: String\): String \{.*?\n  \}\n', '\n', poker, flags=re.S)
poker = poker.replace('  private const val CORE_COUNT = PREFIX + "core.count"\n', '')
poker = poker.replace(' * Core stats multiply the already-normalized gameplay projection.\n', ' * Core upgrades the canonical STR/DEF/SKL/VIT Base Stat directly.\n')
POKER.write_text(poker, encoding="utf-8")

# ---------------------------------------------------------------------------
# Character projection exposes only canonical stats. Equipment JSON keeps weapon
# data/abilities but does not project numeric stat bonuses.
# ---------------------------------------------------------------------------
DETAIL.write_text(r'''package com.rabpit.backroom.core

data class StatLineProjection(val base: Int, val equipment: Int = 0, val effective: Int = base, val passive: Int = effective - base)

data class ItemDetailProjection(
  val id: String,
  val name: String,
  val quantity: Int,
  val type: String? = null,
  val slot: String? = null,
  val rarity: String? = null,
  val equipped: Boolean = false,
  val equippedSlots: List<String> = emptyList(),
  val statItem: Boolean = false,
  val classification: String? = null,
  val weapon: WeaponGameplayStats? = null,
  val abilities: List<EquipmentAbility> = emptyList(),
  val restrictions: List<String> = emptyList(),
  val components: List<EquipmentComponent> = emptyList()
)

data class CharacterDetailProjection(
  val id: String,
  val name: String,
  val avatarRef: String?,
  val presence: CharacterPresence,
  val isLeader: Boolean,
  val healthState: String?,
  val currentHp: Int,
  val maxHp: Int,
  val role: String = "UNSPECIFIED",
  val energyDisplay: String = "N/A",
  val regenPerCompletedTurn: Int = 0,
  val condition: CharacterCondition = CharacterCondition.HEALTHY,
  val str: StatLineProjection = StatLineProjection(5),
  val def: StatLineProjection = StatLineProjection(5),
  val skl: StatLineProjection = StatLineProjection(5),
  val vit: StatLineProjection = StatLineProjection(5),
  val criticalChancePercent: Int = 5,
  val criticalDamagePercent: Int = 150,
  val evasionPercent: Int = 0,
  val criticalResistancePercent: Int = 0,
  val evasionResistancePercent: Int = 0,
  val injuries: List<String>,
  val physiology: DerivedPhysiologyStatus,
  val inventory: List<ItemStack>,
  val inventoryDetails: List<ItemDetailProjection> = emptyList(),
  val inventoryCapacityUsed: Int = 0,
  val inventoryCapacityMax: Int = 9,
  val equipment: Map<String, String>,
  val equipmentDetails: List<ItemDetailProjection> = emptyList(),
  val statusEffects: List<StatusEffect>
) {
}

data class PartyDetailProjection(
  val leaderId: String,
  val maxMembers: Int,
  val elapsedSubjectiveMinutes: Long,
  val members: List<CharacterDetailProjection>
)

object CharacterDetailProjector {
  fun projectParty(state: GameState): PartyDetailProjection {
    val normalized = CharacterProgressionCore.normalize(CharacterEquipmentSystem.normalize(state))
    return PartyDetailProjection(
      normalized.party.leaderId, normalized.party.maxMembers, normalized.time.elapsedSubjectiveMinutes,
      normalized.party.memberIds.mapNotNull { projectCharacter(normalized, it) }
    )
  }

  fun projectCharacter(state: GameState, characterId: String): CharacterDetailProjection? {
    val normalized = CharacterProgressionCore.normalize(CharacterEquipmentSystem.normalize(state))
    val c = normalized.characters[characterId] ?: return null
    val effective = CharacterStatCore.effective(normalized, characterId)
    val base = c.statProfile
    val inventory = normalized.inventories[c.inventoryId]?.items?.values.orEmpty().sortedBy { it.itemId }
    val equipment = normalized.equipment[c.equipmentId]?.slots.orEmpty().toSortedMap()
    fun itemDetail(item: ItemStack): ItemDetailProjection {
      val def = EquipmentCatalog.definition(item.itemId)
      val slots = equipment.filterValues { it == item.itemId }.keys.sorted()
      return ItemDetailProjection(
        id = item.itemId, name = def?.name ?: item.name, quantity = item.quantity,
        type = def?.type, slot = def?.primarySlot?.key ?: item.metadata["slot"], rarity = def?.rarity,
        equipped = slots.isNotEmpty(), equippedSlots = slots,
        statItem = def?.weapon != null, classification = def?.classification?.name,
        weapon = def?.weapon, abilities = def?.abilities.orEmpty(), restrictions = def?.restrictions.orEmpty(),
        components = def?.components.orEmpty()
      )
    }
    val details = inventory.map(::itemDetail)
    return CharacterDetailProjection(
      id = c.id, name = c.name, avatarRef = c.avatarRef, presence = c.presence,
      isLeader = normalized.party.leaderId == c.id, healthState = c.healthState,
      currentHp = c.vitalState.currentHp.coerceIn(0, effective.maxHp), maxHp = effective.maxHp,
      role = base.combatRole,
      energyDisplay = when (base.energy.mode) {
        EnergyMode.INFINITE -> "∞"
        EnergyMode.FINITE -> (base.energy.max ?: 0).toString()
        EnergyMode.NOT_APPLICABLE -> "N/A"
      },
      regenPerCompletedTurn = effective.regenPerCompletedTurn,
      condition = c.vitalState.condition,
      str = StatLineProjection(base.str, 0, effective.str),
      def = StatLineProjection(base.def, 0, effective.def),
      skl = StatLineProjection(base.skl, 0, effective.skl),
      vit = StatLineProjection(base.vit, 0, effective.vit),
      criticalChancePercent = effective.criticalChancePercent,
      criticalDamagePercent = effective.criticalDamagePercent,
      evasionPercent = effective.evasionPercent,
      criticalResistancePercent = effective.resCriticalPercent,
      evasionResistancePercent = effective.resEvasionPercent,
      injuries = c.injuries.toList(), physiology = PhysiologyStatusPolicy.derive(c.physiology),
      inventory = inventory.toList(), inventoryDetails = details,
      inventoryCapacityUsed = InventoryCapacityPolicy.usedSlots(normalized, c.id),
      inventoryCapacityMax = InventoryCapacityPolicy.maxSlots(normalized, c.id),
      equipment = equipment, equipmentDetails = details.filter { it.equipped },
      statusEffects = c.statusIds.mapNotNull(normalized.statuses::get)
    )
  }
}
''', encoding="utf-8")

DETAIL_JSON.write_text(r'''package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject

object CharacterDetailJson {
  fun encodeParty(projection: PartyDetailProjection): JSONObject = JSONObject().apply {
    put("leaderId", projection.leaderId)
    put("maxMembers", projection.maxMembers)
    put("elapsedSubjectiveMinutes", projection.elapsedSubjectiveMinutes)
    put("members", JSONArray().apply { projection.members.forEach { put(encodeCharacter(it)) } })
  }

  fun encodeCharacter(c: CharacterDetailProjection): JSONObject = JSONObject().apply {
    put("id", c.id); put("name", c.name); c.avatarRef?.let { put("avatar", it) }
    put("presence", c.presence.name); put("isLeader", c.isLeader); c.healthState?.let { put("healthState", it) }
    put("currentHp", c.currentHp); put("maxHp", c.maxHp); put("role", c.role)
    put("energy", c.energyDisplay); put("hpRegen", c.regenPerCompletedTurn); put("condition", c.condition.name)
    put("stats", JSONObject().apply {
      put("STR", stat(c.str)); put("DEF", stat(c.def)); put("SKL", stat(c.skl)); put("VIT", stat(c.vit))
    })
    put("combatStatus", JSONObject()
      .put("criticalChancePercent", c.criticalChancePercent)
      .put("criticalDamagePercent", c.criticalDamagePercent)
      .put("evasionPercent", c.evasionPercent)
      .put("criticalResistancePercent", c.criticalResistancePercent)
      .put("evasionResistancePercent", c.evasionResistancePercent))
    put("injuries", JSONArray(c.injuries))
    put("physiology", JSONObject().apply {
      put("hunger", c.physiology.hunger.name); put("thirst", c.physiology.thirst.name); put("sleepDeprivation", c.physiology.sleepDeprivation.name)
      c.physiology.foodPercent?.let { put("foodPercent", it) }; c.physiology.waterPercent?.let { put("waterPercent", it) }; c.physiology.restPercent?.let { put("restPercent", it) }
      c.physiology.pain?.let { put("pain", it) }; c.physiology.infection?.let { put("infection", it) }; c.physiology.thermal?.let { put("thermal", it) }
    })
    val details = if (c.inventoryDetails.isNotEmpty()) c.inventoryDetails else c.inventory.map { raw ->
      ItemDetailProjection(raw.itemId, raw.name, raw.quantity)
    }
    put("inventory", JSONArray().apply { details.forEach { put(item(it)) } })
    put("inventoryCapacity", JSONObject().put("used", c.inventoryCapacityUsed).put("max", c.inventoryCapacityMax))
    put("equipment", JSONObject(c.equipment))
    put("equipmentItems", JSONArray().apply { c.equipmentDetails.forEach { put(item(it)) } })
    put("skills", JSONArray().apply {
      CompanionSkillCatalog.forCharacter(c.id).forEach { skill -> put(JSONObject().apply {
        put("name", skill.name)
        put("kind", skill.kind)
        put("trigger", skill.trigger)
        put("effect", skill.effect)
        skill.note?.let { put("note", it) }
      }) }
    })
    put("statuses", JSONArray().apply { c.statusEffects.forEach { e -> put(JSONObject().put("id", e.id).put("type", e.type).put("persistent", e.persistent)) } })
  }

  private fun stat(x: StatLineProjection) = JSONObject()
    .put("base", x.base).put("passive", x.passive).put("effective", x.effective)

  private fun weapon(x: WeaponGameplayStats) = JSONObject().apply {
    put("DMG", x.dmg); x.ammoDisplay?.let { put("ammo", it) }; x.rpmCapability?.let { put("rpm", it) }; put("fireModes", JSONArray(x.fireModes))
  }

  private fun item(x: ItemDetailProjection) = JSONObject().apply {
    put("id", x.id); put("name", x.name); put("quantity", x.quantity)
    x.type?.let { put("type", it) }; x.slot?.let { put("slot", it) }; x.rarity?.let { put("rarity", it) }
    put("equipped", x.equipped); put("equippedSlots", JSONArray(x.equippedSlots)); put("statItem", x.weapon != null)
    put("consumesInventorySlot", !x.equipped)
    x.classification?.let { put("classification", it) }; x.weapon?.let { put("weapon", weapon(it)) }
    put("abilities", JSONArray().apply { x.abilities.forEach { a -> put(JSONObject().put("name", a.name).put("description", a.description).also { o -> a.importantLimit?.let { o.put("limit", it) } }) } })
    put("restrictions", JSONArray(x.restrictions))
  }
}
''', encoding="utf-8")

# ---------------------------------------------------------------------------
# Combat wiring. Keep every existing roll and action order; only replace stat
# projection/math around those already-existing rolls.
# ---------------------------------------------------------------------------
combat = COMBAT.read_text(encoding="utf-8")
# Direct attack: scale current physical/basic damage by STR, Poker hand, then Cao combat Attack stack.
combat = combat.replace(
'''          val base = weaponDamage + variance + c.opening * 5 + max(0, c.momentum) * 2
          val normalized = if (critical) base * 3 / 2 else base
''',
'''          val rawBase = weaponDamage + variance + c.opening * 5 + max(0, c.momentum) * 2
          val base = CharacterStatCore.basicDamage(rawBase, effective.str, PokerDiceCore.attackPercent(resolvedState), CaoMinhCombatPassive.attackPercent(resolvedState))
          val normalized = if (critical) CharacterStatCore.criticalDamage(base) else base
''')
# If the pre-Poker base variant survived, wire it without adding/removing RNG calls.
combat = combat.replace(
'''          val base = weaponDamage + variance + c.opening * 7 + max(0, c.momentum) * 3
          val damage = max(1, base - profile.armor)
''',
'''          val rawBase = weaponDamage + variance + c.opening * 7 + max(0, c.momentum) * 3
          val base = CharacterStatCore.basicDamage(rawBase, effective.str, PokerDiceCore.attackPercent(resolvedState), CaoMinhCombatPassive.attackPercent(resolvedState))
          val damage = max(1, base - profile.armor)
''')
# Existing crit roll remains; only its threshold is canonical and stack-aware.
combat = combat.replace(
'          val critChance = CombatStatMath.critChancePercent(effective.crit)\n',
'          val critChance = CaoMinhCombatPassive.criticalChance(resolvedState, effective.criticalChancePercent)\n'
)
# Existing entity evasion roll remains; SKL resistance subtracts from the existing threshold.
combat = combat.replace(
'        val entityEvaded = evasionRoll < ENTITY_EVASION_PERCENT\n',
'        val entityEvaded = evasionRoll < (ENTITY_EVASION_PERCENT - CharacterStatEngine.effective(resolvedState, KAI_ID).resEvasionPercent).coerceAtLeast(0)\n'
)
# Existing enemy response roll remains; VIT evasion lowers the same hit chance.
combat = combat.replace(
'    val enemyChance = (profile.aggression * 8 - defense + max(0, -c.momentum) * 7).coerceIn(8, 88)\n',
'    val playerEvasion = CharacterStatEngine.effective(resolvedState, KAI_ID).evasionPercent\n    val enemyChance = (profile.aggression * 8 - defense + max(0, -c.momentum) * 7 - playerEvasion).coerceIn(0, 88)\n'
)
# Canonical DEF reduction: calculate the already-existing raw hit, then divide by DEF multiplier.
incoming_pattern = re.compile(r'''      val effective = CharacterStatEngine\.effective\((?:state|resolvedState), KAI_ID\)\n      val mitigation = CombatStatMath\.defenseReduction\(effective\.df\) \+ CombatStatMath\.agilityDefense\(effective\.agi\)\n      val damage = max\(1, profile\.attack \+ roll\(c\.copy\(eventCounter = c\.eventCounter \+ 47\), 7\) - when \(c\.cover\) \{ Cover\.HARD -> 8; Cover\.PARTIAL -> 4; Cover\.EXPOSED -> 0 \} - mitigation\)''')
combat = incoming_pattern.sub(
'''      val effective = CharacterStatEngine.effective(resolvedState, KAI_ID)
      val rawIncoming = max(1, profile.attack + roll(c.copy(eventCounter = c.eventCounter + 47), 7) - when (c.cover) { Cover.HARD -> 8; Cover.PARTIAL -> 4; Cover.EXPOSED -> 0 })
      val damage = CharacterStatCore.defendedIncomingDamage(rawIncoming, effective.def)''',
    combat, count=1
)
# Skills use SKL projection while weaponDamage itself remains the raw weapon property.
combat = combat.replace('CharacterStatEngine.weaponDamage(resolvedState, IRIS_ID)', 'CharacterStatEngine.skillBaseDamage(resolvedState, IRIS_ID)')
combat = combat.replace('CharacterStatEngine.weaponDamage(resolvedState, SYVIAL_ID)', 'CharacterStatEngine.skillBaseDamage(resolvedState, SYVIAL_ID)')
combat = combat.replace(
'''    val isGuiltyCrownTurn = c.eventCounter % KAI_GUILTY_CROWN_INTERVAL_TURNS == 0
    if (!isGuiltyCrownTurn && c.entityHp > 0) {
      val weaponDamage = CharacterStatEngine.weaponDamage(resolvedState, KAI_ID)
''',
'''    val isGuiltyCrownTurn = c.eventCounter % KAI_GUILTY_CROWN_INTERVAL_TURNS == 0
    if (!isGuiltyCrownTurn && c.entityHp > 0) {
      val weaponDamage = CharacterStatEngine.skillBaseDamage(resolvedState, KAI_ID)
''')
# Cao skill/proc call sites (direct/basic ATTACK still uses raw weaponDamage above).
combat = combat.replace('val damagePerSlash = CharacterStatEngine.weaponDamage(resolvedState, KAI_ID)', 'val damagePerSlash = CharacterStatEngine.skillBaseDamage(resolvedState, KAI_ID)')
combat = combat.replace('val weaponDamage = CharacterStatEngine.weaponDamage(resolvedState, KAI_ID)\n        val proc', 'val weaponDamage = CharacterStatEngine.skillBaseDamage(resolvedState, KAI_ID)\n        val proc')
combat = combat.replace(
'val damage = CharacterStatEngine.weaponDamage(resolvedState, KAI_ID) * spec[2].toInt() / 100',
'val damage = CharacterStatCore.scaleByPercent(CharacterStatEngine.weaponDamage(resolvedState, KAI_ID), CaoMinhCombatPassive.attackPercent(resolvedState)) * spec[2].toInt() / 100'
)

# Apply Dai Dao heal/stack after a successfully resolved Cao combat turn. Encode writes
# combat HP first; passive then updates authoritative CharacterVitalState/stack.
combat = combat.replace(
'    val next = encode(resolvedState, c)\n    return Resolution(next, true, log.joinToString(" "))\n',
'    val next = CaoMinhCombatPassive.afterCaoMinhTurn(encode(resolvedState, c))\n    return Resolution(next, true, log.joinToString(" "))\n'
)
combat = combat.replace(
'      val persisted = encode(resolvedState, c.copy(phase = Phase.RESOLVED, entityCondition = EntityCondition.DESTROYED))\n      val cleared = clearCombatOnly(persisted)\n',
'      val persisted = CaoMinhCombatPassive.afterCaoMinhTurn(encode(resolvedState, c.copy(phase = Phase.RESOLVED, entityCondition = EntityCondition.DESTROYED)))\n      val cleared = clearCombatOnly(persisted)\n'
)
combat = combat.replace(
'      val persisted = encode(resolvedState, c.copy(phase = Phase.RESOLVED))\n      val cleared = clearCombatOnly(persisted)\n',
'      val persisted = CaoMinhCombatPassive.afterCaoMinhTurn(encode(resolvedState, c.copy(phase = Phase.RESOLVED)))\n      val cleared = clearCombatOnly(persisted)\n'
)
# The Game Core is the sole source of Entity victory rewards.
# Inject after all historic combat transformations, not into GM prose.
old_victory = 'return Resolution(cleared, true, log.joinToString(" ") + " ${c.entityName} đã bị tiêu diệt.", entityDestroyed = true)'
new_victory = '''val loot = OfflineEntityLoot.award(cleared, c.encounterId, c.seed)
      return Resolution(loot.state, true, log.joinToString(" ") + " ${c.entityName} đã bị tiêu diệt. " + loot.text, entityDestroyed = true)'''
if old_victory not in combat:
    raise RuntimeError("Missing Entity victory reward anchor")
combat = combat.replace(old_victory, new_victory)

COMBAT.write_text(combat, encoding="utf-8")

# ---------------------------------------------------------------------------
# Cao Minh passive description and dead equipment-stat metadata.
# ---------------------------------------------------------------------------
if CATALOG.exists():
    text = CATALOG.read_text(encoding="utf-8")
    text = text.replace(
      's("Ma Tôn Vạn Giới", "PASSIVE", "Luôn hoạt động", "+50 Max HP, +15 STR, +30 DF, +12 AGI; kế thừa bonus giáp MadGod theo gameplay hiện hành.", "Không chiếm ô trang bị, không nhân/stack qua save-load hoặc equip."),',
      's("Đại Đạo Ma Tôn", "PASSIVE", "Luôn hoạt động", "+10% Base STR/DEF/SKL/VIT; không đổi Core cost. STR tăng sát thương vật lý/đánh thường; DEF tăng giảm sát thương và Critical Resistance; SKL tăng Critical, Evasion Resistance và skill damage; VIT tăng Max HP và Evasion. Sau mỗi lượt Cao Minh: hồi 10% Max HP, +20% Attack và +20 điểm % Critical; đồng đội +50 điểm % Critical; Critical cap 100%.", "Passive tách khỏi Base Stat và reset combat stack khi combat kết thúc."),'
    )
    CATALOG.write_text(text, encoding="utf-8")

if MADGOD.exists():
    mg = MADGOD.read_text(encoding="utf-8")
    for name in ("ARMOR_DF","ARMOR_STR","ARMOR_AGI","ARMOR_HP","ARMOR_ENE","ARMOR_CRIT"):
      mg = re.sub(rf'const val {name} = .*', f'const val {name} = 0', mg, count=1)
    mg = mg.replace("Ma Tôn Vạn Giới", "Đại Đạo Ma Tôn")
    MADGOD.write_text(mg, encoding="utf-8")

# Protect candidate commits from forged Base Stat/Core/HP changes.
if FACADE.exists():
    facade = FACADE.read_text(encoding="utf-8")
    anchor = '''    repository.save(committed.state)
    val synchronized = syncLegacy(candidate, committed.state, incrementTurn = false)
'''
    if anchor in facade:
      facade = facade.replace(anchor, '''    val protectedState = CharacterProgressionCore.protectFromCandidate(pending.state, committed.state)
    repository.save(protectedState)
    val synchronized = syncLegacy(candidate, protectedState, incrementTurn = false)
''', 1)
    FACADE.write_text(facade, encoding="utf-8")

# Remove stale prompt references to retired character-stat schemas and keep
# Cao Minh's compact GM skill canon synchronized with the final 1.1.93a runtime.
if MAIN.exists():
    main = MAIN.read_text(encoding="utf-8")
    main = main.replace(
      'HP nền 100; STR 7, DF 7, AGI 8, CRIT 7.',
      'Character Stats theo 1.1.93a: Base STR/DEF/SKL/VIT đều bắt đầu 5; baseMaxHp 50 và Max HP derive từ VIT.'
    )
    main = main.replace(
      'Ma Tôn Vạn Giới là passive kế thừa giáp MadGod, không phải trang bị.',
      'Đại Đạo Ma Tôn là passive nội tại theo Character Stats 1.1.93a, không phải trang bị.'
    )
    main = main.replace(
      'Ma Tôn Vạn Giới: Luôn hoạt động; +50 Max HP, +15 STR, +30 DF, +12 AGI; kế thừa bonus giáp MadGod theo gameplay hiện hành.',
      'Đại Đạo Ma Tôn: Luôn hoạt động; +10% Base STR/DEF/SKL/VIT; không đổi Core cost. STR tăng sát thương vật lý/đánh thường; DEF tăng giảm sát thương và Critical Resistance; SKL tăng Critical, Evasion Resistance và skill damage; VIT tăng Max HP và Evasion. Sau mỗi lượt Cao Minh: hồi 10% Max HP, +20% Attack và +20 điểm % Critical; đồng đội +50 điểm % Critical; Critical cap 100%.'
    )
    MAIN.write_text(main, encoding="utf-8")

# Minimal UI replacement: canonical labels only; no equipment numeric-stat comparison.
if INDEX.exists():
    html = INDEX.read_text(encoding="utf-8")
    html = html.replace("['DF',statText(member.stats&&member.stats.DF)],['AGI',statText(member.stats&&member.stats.AGI)],['CRIT',statText(member.stats&&member.stats.CRIT)]",
                        "['DEF',statText(member.stats&&member.stats.DEF)],['SKL',statText(member.stats&&member.stats.SKL)],['VIT',statText(member.stats&&member.stats.VIT)]")
    html = re.sub(r'''function statRows\(item\)\{.*?\n  \}''',
                  '''function statRows(item){const w=item.weapon||{},rows=[];if(w.DMG!=null)rows.push(['DMG',w.DMG]);if(w.ammo!=null)rows.push(['Ammo',w.ammo]);if(w.rpm!=null)rows.push(['Full Auto',w.rpm+' RPM']);return rows\n  }''',
                  html, count=1, flags=re.S)
    html = re.sub(r'''function comparisonRows\(c\)\{.*?\}''', 'function comparisonRows(c){return[]}', html, count=1)
    INDEX.write_text(html, encoding="utf-8")

# ---------------------------------------------------------------------------
# Regression tests equivalent to 1.1.93a authority. Also replace obsolete
# generated tests that assert the retired high-number/equipment stat schema.
# ---------------------------------------------------------------------------
# Round-trip tests must compare against the same canonical post-load normalization
# now applied by GameStateCodec.decode().
codec_test = TESTS / "GameStateCodecTest.kt"
if codec_test.exists():
    text = codec_test.read_text(encoding="utf-8")
    text = text.replace(
      '    val canonicalState = CharacterEquipmentSystem.normalize(SpecialFollowersCanon.ensure(AnNhienCanon.ensure(state)))\n',
      '    val canonicalState = CharacterProgressionCore.normalize(CharacterEquipmentSystem.normalize(SpecialFollowersCanon.ensure(AnNhienCanon.ensure(state))))\n'
    )
    codec_test.write_text(text, encoding="utf-8")

(TESTS / "CharacterStats1193aRegressionTest.kt").write_text(r'''package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CharacterStats1193aRegressionTest {
  @Test fun baselineAndCaoPassiveMatch1193a() {
    val state = CharacterProgressionCore.normalize(GameState.initial())
    val base = state.characters.getValue(KAI_ID).statProfile
    assertEquals(listOf(5,5,5,5), listOf(base.str, base.def, base.skl, base.vit))
    val effective = CharacterStatCore.effective(state, KAI_ID)
    assertEquals(listOf(6,6,6,6), listOf(effective.str, effective.def, effective.skl, effective.vit))
    assertEquals(55, effective.maxHp)
    assertEquals(50, state.characters.getValue(KAI_ID).vitalState.currentHp)
    assertEquals(7, effective.criticalChancePercent)
    assertEquals(2, effective.evasionPercent)
    assertEquals(2, effective.resCriticalPercent)
    assertEquals(2, effective.resEvasionPercent)
    assertEquals(150, effective.criticalDamagePercent)
    val capped = state.copy(characters = state.characters + (KAI_ID to state.characters.getValue(KAI_ID).copy(
      statProfile = base.copy(str=999, def=999, skl=999, vit=999)
    )))
    val cappedEffective = CharacterStatCore.effective(capped, KAI_ID)
    assertEquals(listOf(999,999,999,999), listOf(cappedEffective.str,cappedEffective.def,cappedEffective.skl,cappedEffective.vit))
  }

  @Test fun legacySchemaResetsInsteadOfMappingAndPreservesHpSafely() {
    val encoded = JSONObject(GameStateCodec.encode(GameState.initial()))
    val chars = encoded.getJSONObject("characters")
    val cao = chars.getJSONObject(KAI_ID)
    cao.put("statProfile", JSONObject()
      .put("baseMaxHp", 999).put("str", 82).put("df", 78).put("agi", 92).put("crit", 95)
      .put("baseStats", JSONObject().put("LUCK", 500)).put("level", 77).put("exp", 99999))
    cao.put("vitalState", JSONObject().put("currentHp", 31).put("condition", "HURT"))
    val migrated = GameStateCodec.decode(encoded.toString())
    val p = migrated.characters.getValue(KAI_ID).statProfile
    assertEquals(listOf(5,5,5,5), listOf(p.str,p.def,p.skl,p.vit))
    assertEquals(31, migrated.characters.getValue(KAI_ID).vitalState.currentHp)
    val out = JSONObject(GameStateCodec.encode(migrated)).getJSONObject("characters").getJSONObject(KAI_ID).getJSONObject("statProfile")
    assertEquals(setOf("schema","baseMaxHp","STR","DEF","SKL","VIT"), out.keys().asSequence().toSet())
  }

  @Test fun costSharedCoreAndPerCharacterProgressionAreCanonical() {
    assertEquals(listOf(1,1,2,2,3,3), (5..10).map(CharacterProgressionCore::upgradeCost))
    var state = CharacterProgressionCore.grantCore(GameState.initial(), 10)
    val other = state.characters.keys.firstOrNull { it != KAI_ID }
    if (other != null) {
      state = CharacterProgressionCore.upgrade(state, KAI_ID, "STR").state
      assertEquals(6, CharacterProgressionCore.baseStat(state, KAI_ID, "STR"))
      assertEquals(5, CharacterProgressionCore.baseStat(state, other, "STR"))
      assertEquals(9, CharacterProgressionCore.coreCount(state))
    }
  }

  @Test fun vitUpgradeRaisesMaxAndPreservesMissingHp() {
    var state = CharacterProgressionCore.grantCore(GameState.initial(), 2)
    state = CharacterStatEngine.setCurrentHp(state, KAI_ID, 40)
    val beforeMax = CharacterStatEngine.effective(state, KAI_ID).maxHp
    val result = CharacterProgressionCore.upgrade(state, KAI_ID, "VIT")
    val afterMax = CharacterStatEngine.effective(result.state, KAI_ID).maxHp
    assertTrue(afterMax > beforeMax)
    assertEquals(40 + (afterMax - beforeMax), result.state.characters.getValue(KAI_ID).vitalState.currentHp)
  }

  @Test fun derivedCapsAndDamageProjectionMatch1193a() {
    assertEquals(50, CharacterStatCore.criticalChance(999))
    assertEquals(35, CharacterStatCore.evasion(999))
    assertEquals(50, CharacterStatCore.criticalResistance(999))
    assertEquals(50, CharacterStatCore.evasionResistance(999))
    assertEquals(30, CharacterStatCore.basicDamage(30, 5))
    assertEquals(33, CharacterStatCore.basicDamage(30, 6))
    assertEquals(77, CharacterStatCore.skillDamage(30, 170, 5, 150))
    assertEquals(84, CharacterStatCore.skillDamage(30, 170, 6, 150))
    assertEquals(20, CharacterStatCore.defendedIncomingDamage(20, 5))
    assertEquals(18, CharacterStatCore.defendedIncomingDamage(20, 6))
    assertEquals(10, CharacterStatCore.defendedIncomingDamage(20, 15))
    assertEquals(150, CharacterStatCore.criticalDamage(100))
  }

  @Test fun equipmentCannotCreateSecondStatSchemaAndWeaponDamageStaysWeaponProperty() {
    val state = GameState.initial()
    val before = CharacterStatEngine.effective(state, KAI_ID)
    val stripped = state.copy(equipment = state.equipment + (KAI_ID to EquipmentState(KAI_ID)))
    val after = CharacterStatEngine.effective(stripped, KAI_ID)
    assertEquals(before.copy(), after)
    val weapon = CharacterStatEngine.weaponDamage(state, KAI_ID)
    val id = state.equipment.getValue(KAI_ID).slots["weapon"]
    assertEquals(EquipmentCatalog.definition(id!!)?.weapon?.dmg ?: 18, weapon)
  }

  @Test fun antiForgeRestoresStatsCoreAndHp() {
    var authoritative = CharacterProgressionCore.grantCore(GameState.initial(), 7)
    authoritative = CharacterStatEngine.setCurrentHp(authoritative, KAI_ID, 33)
    val c = authoritative.characters.getValue(KAI_ID)
    val forged = authoritative.copy(
      characters = authoritative.characters + (KAI_ID to c.copy(
        statProfile = c.statProfile.copy(str=999,def=999,skl=999,vit=999),
        vitalState = c.vitalState.copy(currentHp=55)
      )),
      metadata = authoritative.metadata + (CharacterProgressionCore.CORE_KEY to "99999")
    )
    val protected = CharacterProgressionCore.protectFromCandidate(authoritative, forged)
    assertEquals(c.statProfile, protected.characters.getValue(KAI_ID).statProfile)
    assertEquals(33, protected.characters.getValue(KAI_ID).vitalState.currentHp)
    assertEquals(7, CharacterProgressionCore.coreCount(protected))
  }

  @Test fun daiDaoCombatStackIsSeparateAndResetsWithCombatMetadata() {
    var state = CharacterStatEngine.setCurrentHp(GameState.initial(), KAI_ID, 30)
    val baseCrit = CharacterStatEngine.effective(state, KAI_ID).criticalChancePercent
    val skillBefore = CharacterStatEngine.skillBaseDamage(state, KAI_ID)
    state = CaoMinhCombatPassive.afterCaoMinhTurn(state)
    assertEquals(1, CaoMinhCombatPassive.stacks(state))
    assertEquals(120, CaoMinhCombatPassive.attackPercent(state))
    assertTrue(CharacterStatEngine.skillBaseDamage(state, KAI_ID) > skillBefore)
    assertEquals((baseCrit + 20).coerceAtMost(100), CaoMinhCombatPassive.criticalChance(state, baseCrit))
    assertEquals(55, CaoMinhCombatPassive.allyCriticalChance(5))
    assertEquals(36, state.characters.getValue(KAI_ID).vitalState.currentHp)
    val cleared = state.copy(metadata = state.metadata.filterKeys { !it.startsWith("combat.") })
    assertEquals(0, CaoMinhCombatPassive.stacks(cleared))
  }
}
''', encoding="utf-8")

(TESTS / "CharacterStatSchemaTest.kt").write_text(r'''package com.rabpit.backroom.core
import org.junit.Assert.*
import org.junit.Test
class CharacterStatSchemaTest {
  @Test fun onlyCanonicalBaseStatsAreStored() {
    val p = CharacterStatProfiles.forId(KAI_ID)
    assertEquals(50, p.baseMaxHp)
    assertEquals(listOf(5,5,5,5), listOf(p.str,p.def,p.skl,p.vit))
  }
}
''', encoding="utf-8")

(TESTS / "CharacterStatusEquipmentSystemTest.kt").write_text(r'''package com.rabpit.backroom.core
import org.junit.Assert.*
import org.junit.Test
class CharacterStatusEquipmentSystemTest {
  @Test fun equipmentDoesNotAlterCanonicalStats() {
    val state = GameState.initial()
    val before = CharacterStatEngine.effective(state, KAI_ID)
    val stripped = state.copy(equipment = state.equipment + (KAI_ID to EquipmentState(KAI_ID)))
    assertEquals(before, CharacterStatEngine.effective(stripped, KAI_ID))
  }
  @Test fun weaponDamageIsRawWeaponAttribute() {
    val state = GameState.initial()
    val id = state.equipment.getValue(KAI_ID).slots.getValue("weapon")
    assertEquals(EquipmentCatalog.definition(id)!!.weapon!!.dmg, CharacterStatEngine.weaponDamage(state, KAI_ID))
  }
}
''', encoding="utf-8")

(TESTS / "PokerDiceCoreBackportTest.kt").write_text(r'''package com.rabpit.backroom.core
import org.junit.Assert.*
import org.junit.Test
class PokerDiceCoreBackportTest {
  @Test fun handClassificationIsUnchanged() {
    assertEquals("FSF", PokerDiceCore.classify(listOf(4,4,4,4,4)))
    assertEquals("SSF", PokerDiceCore.classify(listOf(1,2,3,4,5)))
    assertEquals("STRAIGHT", PokerDiceCore.classify(listOf(2,3,4,5,6)))
    assertEquals("FOUR OF A KIND", PokerDiceCore.classify(listOf(6,6,6,6,2)))
    assertEquals("FULL HOUSE", PokerDiceCore.classify(listOf(3,3,3,5,5)))
    assertEquals("TWO PAIR", PokerDiceCore.classify(listOf(1,1,5,5,3)))
  }
  @Test fun rerollCapAndActionBindingAreUnchanged() {
    var state = PokerDiceCore.prepare(GameState.initial(), PokerDiceCore.DIRECT_COMBAT_ACTION, "E1")
    repeat(3) {
      val held = PokerDiceCore.diceJson(state)!!.getJSONArray("held")
      for (i in 0 until 5) if (held.optBoolean(i,false)) state = PokerDiceCore.setHold(state,i,false)
      state = PokerDiceCore.reroll(state)
    }
    try { PokerDiceCore.reroll(state); fail("fourth reroll must fail") } catch (_: IllegalStateException) {}
    try { PokerDiceCore.prepare(state, "Bỏ chạy", "E1"); fail("action switch must fail") } catch (_: IllegalStateException) {}
  }
  @Test fun coreUpgradesCanonicalBaseStat() {
    var state = PokerDiceCore.grantCore(GameState.initial(), 4)
    val r = PokerDiceCore.upgrade(state, KAI_ID, "STR")
    assertEquals(6, PokerDiceCore.coreStat(r.state, KAI_ID, "STR"))
    assertEquals(3, PokerDiceCore.coreCount(r.state))
    assertEquals(110, PokerDiceCore.statPercent(r.state, KAI_ID, "STR"))
  }
}
''', encoding="utf-8")

(TESTS / "InventoryCapacityNewGameTest.kt").write_text(r'''package com.rabpit.backroom.core
import org.junit.Assert.*
import org.junit.Test

class InventoryCapacityNewGameTest {
  private fun freshAll(): GameState =
    CharacterProgressionCore.normalize(CharacterEquipmentSystem.normalize(
      SpecialFollowersCanon.ensure(AnNhienCanon.ensure(GameState.initial()))
    ))

  @Test fun equippedItemsConsumeZeroCapacityForAllFourCharacters() {
    val state = freshAll()
    listOf(KAI_ID, IRIS_ID, SYVIAL_ID, AN_NHIEN_ID).forEach { id ->
      assertTrue("character must exist: $id", state.characters.containsKey(id))
      val equippedIds = InventoryCapacityPolicy.equippedItemIds(state, id)
      assertTrue("expected equipped loadout: $id", equippedIds.isNotEmpty())
      equippedIds.forEach { itemId ->
        assertTrue(state.inventories.getValue(id).items.containsKey(itemId))
        assertFalse(InventoryCapacityPolicy.consumesSlot(state, id, itemId))
      }
      assertEquals(0, InventoryCapacityPolicy.usedSlots(state, id))
      assertEquals(InventoryPolicy.profileFor(state, id).maxTypes, InventoryCapacityPolicy.maxSlots(state, id))
    }
  }

  @Test fun unequipMakesTheSameOwnedItemConsumeOneSlotAndReequipReleasesIt() {
    val initial = freshAll()
    val unequip = EquipmentEngine.unequip(initial, ItemCommand(
      "U", null, KAI_ID, source=CommandSource.UI, operation=ItemCommand.Operation.UNEQUIP,
      itemId=KAI_BLACKBLOOD_ARMOR_ID, itemName="Huyết Ma Chiến Khải", slot="armor"
    ))
    assertTrue(unequip.applied)
    assertTrue(unequip.state.inventories.getValue(KAI_ID).items.containsKey(KAI_BLACKBLOOD_ARMOR_ID))
    assertEquals(1, InventoryCapacityPolicy.usedSlots(unequip.state, KAI_ID))
    val reEquip = EquipmentEngine.equip(unequip.state, ItemCommand(
      "E", null, KAI_ID, source=CommandSource.UI, operation=ItemCommand.Operation.EQUIP,
      itemId=KAI_BLACKBLOOD_ARMOR_ID, itemName="Huyết Ma Chiến Khải", slot="armor"
    ))
    assertTrue(reEquip.applied)
    assertEquals(0, InventoryCapacityPolicy.usedSlots(reEquip.state, KAI_ID))
  }

  @Test fun madGodWeaponIsOneOwnedZeroCapacityItemAndDoesNotAlterBaseStats() {
    var state = freshAll()
    val inv = state.inventories.getValue(KAI_ID)
    state = state.copy(inventories = state.inventories + (KAI_ID to inv.copy(
      items = inv.items + (MADGOD_SET_ID to EquipmentCatalog.stackFor(MADGOD_SET_ID))
    )))
    val before = state.characters.getValue(KAI_ID).statProfile
    val equip = EquipmentEngine.equip(state, ItemCommand(
      "M", null, KAI_ID, source=CommandSource.UI, operation=ItemCommand.Operation.EQUIP,
      itemId=MADGOD_SET_ID, itemName="Huyết Ma Kiếm · Ma Tôn", slot="weapon"
    ))
    assertTrue(equip.applied)
    assertEquals(1, equip.state.equipment.getValue(KAI_ID).slots.values.count { it == MADGOD_SET_ID })
    assertTrue(equip.state.inventories.getValue(KAI_ID).items.containsKey(MADGOD_SET_ID))
    assertFalse(InventoryCapacityPolicy.consumesSlot(equip.state, KAI_ID, MADGOD_SET_ID))
    assertEquals(before, equip.state.characters.getValue(KAI_ID).statProfile)
  }

  @Test fun saveLoadRecalculatesCapacityFromOwnershipAndEquipmentReferences() {
    val loaded = GameStateCodec.decode(GameStateCodec.encode(freshAll()))
    listOf(KAI_ID, IRIS_ID, SYVIAL_ID, AN_NHIEN_ID).forEach { id ->
      assertEquals(0, InventoryCapacityPolicy.usedSlots(loaded, id))
    }
  }

  @Test fun freshNewGameProjectionUsesCanonical1193aStatsAndCapacity() {
    val state = freshAll()
    val kai = CharacterDetailProjector.projectParty(state).members.first { it.id == KAI_ID }
    assertEquals(50, kai.currentHp)
    assertEquals(55, kai.maxHp)
    assertEquals("∞", kai.energyDisplay)
    assertEquals(0, kai.regenPerCompletedTurn)
    assertEquals(listOf(5,5,5,5), listOf(kai.str.base, kai.def.base, kai.skl.base, kai.vit.base))
    assertEquals(listOf(6,6,6,6), listOf(kai.str.effective, kai.def.effective, kai.skl.effective, kai.vit.effective))
    assertEquals(0, kai.inventoryCapacityUsed)
    assertEquals(InventoryPolicy.KAI.maxTypes, kai.inventoryCapacityMax)
    assertEquals(3, kai.equipment.values.toSet().size)
    assertEquals(3, kai.inventoryDetails.count { it.equipped })
  }
}
''', encoding="utf-8")

# Retired generated tests with old Cao equipment-stat expectations.
cao_test = TESTS / "CaoMinhSkillsEquipmentTest.kt"
if cao_test.exists():
    cao_test.write_text(r'''package com.rabpit.backroom.core
import org.junit.Assert.*
import org.junit.Test
class CaoMinhSkillsEquipmentTest {
  @Test fun daiDaoIsIntrinsicAndEquipmentDoesNotChangeBaseStats() {
    val state = GameState.initial()
    val skills = CompanionSkillCatalog.forCharacter(KAI_ID)
    val passive = skills.single { it.name == "Đại Đạo Ma Tôn" }
    assertEquals("PASSIVE", passive.kind)
    assertTrue(passive.effect.contains("+10% Base STR/DEF/SKL/VIT"))
    assertFalse(passive.effect.contains("+50 Max HP"))
    assertFalse(passive.effect.contains("AGI"))
    val base = state.characters.getValue(KAI_ID).statProfile
    assertEquals(listOf(5,5,5,5), listOf(base.str,base.def,base.skl,base.vit))
    val stripped = state.copy(equipment = state.equipment + (KAI_ID to EquipmentState(KAI_ID)))
    assertEquals(CharacterStatEngine.effective(state,KAI_ID), CharacterStatEngine.effective(stripped,KAI_ID))
  }
  @Test fun nonStatSkillKitRemainsPresent() {
    val skills = CompanionSkillCatalog.forCharacter(KAI_ID)
    assertTrue(skills.any { it.name == "Huyết Ma Nhị Thập Tứ Trảm" })
  }
}
''', encoding="utf-8")

madgod_test = TESTS / "MadGodEquipmentTest.kt"
if madgod_test.exists():
    madgod_test.write_text(r'''package com.rabpit.backroom.core
import org.junit.Assert.*
import org.junit.Test
class MadGodEquipmentTest {
  @Test fun cheatWeaponKeepsWeaponDamageButNoCharacterStatBonus() {
    val def = EquipmentCatalog.definition(MADGOD_SET_ID)!!
    assertEquals(55, def.weapon!!.dmg)
    assertFalse(def.bonuses.any())
  }
}
''', encoding="utf-8")

lucia_test = TESTS / "LuciaFollowerTest.kt"
if lucia_test.exists():
    text = lucia_test.read_text(encoding="utf-8")
    text = replace_re(text, r'  @Test fun luciaUsesHundredHpAndAllRequestedStatsAreAtMostTen\(\) \{.*?\n  \}', r'''  @Test fun luciaUsesCanonical1193aBaseStats() {
    val state = CharacterProgressionCore.normalize(GameState.initial())
    val lucia = state.characters.getValue(LUCIA_ID)
    assertEquals(50, lucia.statProfile.baseMaxHp)
    assertEquals(listOf(5,5,5,5), listOf(lucia.statProfile.str,lucia.statProfile.def,lucia.statProfile.skl,lucia.statProfile.vit))
    assertEquals(50, CharacterStatEngine.effective(state, LUCIA_ID).maxHp)
  }''', "Lucia stat regression")
    lucia_test.write_text(text, encoding="utf-8")

# Final combat regression compatibility: retain current combat sequencing, but
# update expectations for canonical 1.1.93a HP, SKL skill projection and Đại Đạo heal.
combat_test = TESTS / "CombatRuntimeTest.kt"
if combat_test.exists():
    text = combat_test.read_text(encoding="utf-8")
    text = text.replace(
      '''    val expectedMaxHp = CharacterStatEngine.effective(GameState.initial(), KAI_ID).maxHp
    assertEquals(175, expectedMaxHp)
    assertEquals(expectedMaxHp, combat.playerMaxHp)
    assertEquals(expectedMaxHp, combat.playerHp)
''',
      '''    val initial = GameState.initial()
    val expectedMaxHp = CharacterStatEngine.effective(initial, KAI_ID).maxHp
    val expectedHp = initial.characters.getValue(KAI_ID).vitalState.currentHp
    assertEquals(55, expectedMaxHp)
    assertEquals(50, expectedHp)
    assertEquals(expectedMaxHp, combat.playerMaxHp)
    assertEquals(expectedHp, combat.playerHp)
'''
    )
    # Huyết Ma Nhị Thập Tứ Trảm is a skill: SKL=6 raises its 32-DMG weapon base
    # before the existing 115% per-slash multiplier. No RNG/turn ordering changes.
    text = text.replace('mỗi trảm -36 HP', 'mỗi trảm -40 HP')
    text = text.replace('tổng -864 HP', 'tổng -960 HP')

    old_diep = '''    assertTrue(result.reply.contains("Devils And Gold"))
    assertEquals(kaiBefore - maxOf(1, (kaiMax * 5 + 99) / 100), result.state.characters.getValue(KAI_ID).vitalState.currentHp)
    assertEquals(irisBefore - maxOf(1, (irisMax * 5 + 99) / 100), result.state.characters.getValue("iris").vitalState.currentHp)
'''
    new_diep = '''    assertTrue(result.reply.contains("Devils And Gold"))
    val kaiDamage = maxOf(1, (kaiMax * 5 + 99) / 100)
    val daiDaoHeal = maxOf(1, (kaiMax * CaoMinhCombatPassive.HEAL_PERCENT + 50) / 100)
    assertEquals(minOf(kaiMax, kaiBefore - kaiDamage + daiDaoHeal), result.state.characters.getValue(KAI_ID).vitalState.currentHp)
    assertEquals(irisBefore - maxOf(1, (irisMax * 5 + 99) / 100), result.state.characters.getValue("iris").vitalState.currentHp)
'''
    if old_diep in text:
        text = text.replace(old_diep, new_diep, 1)
    combat_test.write_text(text, encoding="utf-8")

# Final static guards. The forbidden names may survive as compatibility getters or
# dead equipment fields, but no serialized/progression path may expose them.
for path in (STATS, PROGRESSION, SYSTEM, POKER, CODEC, DETAIL, DETAIL_JSON):
    if not path.exists():
        raise RuntimeError(f"missing generated runtime file: {path.name}")

for marker in (
  'const val BASE_STAT = 5',
  'const val MAX_STAT = 999',
  'const val BASE_MAX_HP = 50',
  '1 + (currentStat.coerceIn(BASE_STAT, MAX_STAT) - BASE_STAT) / 2',
  'const val DAI_DAO_MA_TON_STAT_BONUS_PERCENT = 10',
  'const val CRITICAL_DAMAGE_PERCENT = 150',
  'const val ALLY_CRITICAL_BONUS_PERCENT = 50',
):
    if marker not in PROGRESSION.read_text(encoding="utf-8"):
        raise RuntimeError("1.1.93a authority marker missing: " + marker)

for runtime_path in (STATS, DETAIL, DETAIL_JSON, SYSTEM):
    runtime_text = runtime_path.read_text(encoding="utf-8")
    for forbidden in (
        'val df:', 'val agi:', 'val crit:',
        'put("DF"', 'put("AGI"', 'put("CRIT"',
        '.bonuses.hp', '.bonuses.str', '.bonuses.df', '.bonuses.agi', '.bonuses.crit',
        'EquipmentBonuses(hp =', 'EquipmentBonuses(str =', 'EquipmentBonuses(df =',
        'EquipmentBonuses(agi =', 'EquipmentBonuses(crit ='
    ):
        if forbidden in runtime_text:
            raise RuntimeError(f"legacy stat surface remains in {runtime_path.name}: {forbidden}")

if MAIN.exists():
    main_final = MAIN.read_text(encoding="utf-8")
    for forbidden in (
        'HP nền 100; STR 7, DF 7, AGI 8, CRIT 7.',
        'Ma Tôn Vạn Giới: Luôn hoạt động; +50 Max HP, +15 STR, +30 DF, +12 AGI',
        'Ma Tôn Vạn Giới là passive kế thừa giáp MadGod',
    ):
        if forbidden in main_final:
            raise RuntimeError("stale legacy character-stat prompt remains: " + forbidden)
    for required in (
        'Đại Đạo Ma Tôn là passive nội tại theo Character Stats 1.1.93a',
        'Đại Đạo Ma Tôn: Luôn hoạt động; +10% Base STR/DEF/SKL/VIT',
    ):
        if required not in main_final:
            raise RuntimeError("Cao Minh canonical passive prompt missing: " + required)

codec_final = CODEC.read_text(encoding="utf-8")
for forbidden in ('put("df"', 'put("agi"', 'put("crit"', 'put("level"', 'put("exp"', 'put("baseStats"'):
    if forbidden in codec_final:
        raise RuntimeError("legacy stat serialization remains: " + forbidden)

print("Character Stats restored to 1.1.93a authority: STR/DEF/SKL/VIT, canonical Core progression, derived combat stats, Dai Dao Ma Ton and migration guards.")
