from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
GAME_STATE = CORE / "GameState.kt"
CODEC = CORE / "GameStateCodec.kt"
SCHEMA = CORE / "CharacterStats.kt"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return source.replace(old, new, 1)


SCHEMA.write_text(r'''package com.rabpit.backroom.core

/**
 * Gameplay-normalized character numbers. These values are balance data, not canon claims.
 * Character Codex remains authoritative for identity, role, abilities, and equipment behavior.
 */
enum class StatSource { GAMEPLAY_NORMALIZED, GAMEPLAY_FALLBACK }
enum class EnergyMode { INFINITE, FINITE, NOT_APPLICABLE }
enum class CharacterCondition { HEALTHY, HURT, WOUNDED, CRITICAL, DEFEATED, DEAD }

data class EnergyProfile(
  val mode: EnergyMode = EnergyMode.NOT_APPLICABLE,
  val max: Int? = null
) {
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
  val baseMaxHp: Int = 100,
  val energy: EnergyProfile = EnergyProfile.notApplicable(),
  val regen: HpRegenRule = HpRegenRule(),
  val str: Int = 10,
  val df: Int = 10,
  val agi: Int = 10,
  val crit: Int = 10,
  val combatRole: String = "UNSPECIFIED",
  val statSource: StatSource = StatSource.GAMEPLAY_FALLBACK
)

data class CharacterVitalState(
  val currentHp: Int = 100,
  val condition: CharacterCondition = CharacterCondition.HEALTHY,
  val lastRegenCompletedTurnId: String? = null
)

/** Derived-only view. It must never become an independent source of truth. */
data class EffectiveCharacterStats(
  val maxHp: Int,
  val equipmentHp: Int = 0,
  val str: Int,
  val df: Int,
  val agi: Int,
  val crit: Int,
  val energy: EnergyProfile,
  val regenPerCompletedTurn: Int
)

object CharacterStatProfiles {
  private val kai = CharacterStatProfile(
    baseMaxHp = 100,
    energy = EnergyProfile.infinite(),
    regen = HpRegenRule(4, "kai:passive-regeneration", true),
    str = 82,
    df = 78,
    agi = 92,
    crit = 95,
    combatRole = "COMMANDER / SUPREME MARKSMAN / HIGH-MOBILITY COMBATANT",
    statSource = StatSource.GAMEPLAY_NORMALIZED
  )

  private val iris = CharacterStatProfile(
    baseMaxHp = 100,
    energy = EnergyProfile.infinite(),
    regen = HpRegenRule(4, "iris:passive-regeneration", true),
    str = 58,
    df = 60,
    agi = 84,
    crit = 90,
    combatRole = "SCOUT / TARGET ELIMINATOR / DUAL-GUN MARKSMAN",
    statSource = StatSource.GAMEPLAY_NORMALIZED
  )

  private val syvial = CharacterStatProfile(
    baseMaxHp = 100,
    energy = EnergyProfile.infinite(),
    regen = HpRegenRule(4, "syvial:passive-regeneration", true),
    str = 94,
    df = 84,
    agi = 96,
    crit = 88,
    combatRole = "HIGH-SPEED SWORDSMAN / ASSAULT / COUNTER / EXECUTION",
    statSource = StatSource.GAMEPLAY_NORMALIZED
  )

  private val anNhien = CharacterStatProfile(
    baseMaxHp = 100,
    energy = EnergyProfile.notApplicable(),
    regen = HpRegenRule(),
    str = 10,
    df = 10,
    agi = 10,
    crit = 0,
    combatRole = "PROTECTED FOLLOWER / NON-COMBAT",
    statSource = StatSource.GAMEPLAY_NORMALIZED
  )

  private val fallback = CharacterStatProfile()

  fun forId(characterId: String): CharacterStatProfile = when (characterId.trim().lowercase()) {
    "kai" -> kai
    "iris" -> iris
    "syvial" -> syvial
    "an-nhien", "an_nhien", "annhien" -> anNhien
    else -> fallback
  }
}
''', encoding="utf-8")

state = GAME_STATE.read_text(encoding="utf-8")
old_character = '''data class CharacterState(
  val id: String,
  val name: String,
  val avatarRef: String? = null,
  val healthState: String? = null,
  val injuries: List<String> = emptyList(),
'''
new_character = '''data class CharacterState(
  val id: String,
  val name: String,
  val avatarRef: String? = null,
  val healthState: String? = null,
  val statProfile: CharacterStatProfile = CharacterStatProfiles.forId(id),
  val vitalState: CharacterVitalState = CharacterVitalState(currentHp = CharacterStatProfiles.forId(id).baseMaxHp),
  val injuries: List<String> = emptyList(),
'''
if new_character not in state:
    state = replace_once(state, old_character, new_character, "CharacterState stat/vital fields")
GAME_STATE.write_text(state, encoding="utf-8")

codec = CODEC.read_text(encoding="utf-8")
old_encode = '''  private fun character(value: CharacterState) = JSONObject().apply {
    put("id", value.id); put("name", value.name); putNullable("avatarRef", value.avatarRef)
    putNullable("healthState", value.healthState); put("injuries", JSONArray(value.injuries))
'''
new_encode = '''  private fun character(value: CharacterState) = JSONObject().apply {
    put("id", value.id); put("name", value.name); putNullable("avatarRef", value.avatarRef)
    putNullable("healthState", value.healthState)
    put("statProfile", characterStatProfile(value.statProfile))
    put("vitalState", characterVitalState(value.vitalState))
    put("injuries", JSONArray(value.injuries))
'''
if new_encode not in codec:
    codec = replace_once(codec, old_encode, new_encode, "Character stat encode")

old_decode = '''  private fun decodeCharacter(json: JSONObject) = CharacterState(
    id = json.optString("id"), name = json.optString("name"),
    avatarRef = json.nullableString("avatarRef"), healthState = json.nullableString("healthState"),
    injuries = json.optJSONArray("injuries").strings(),
'''
new_decode = '''  private fun decodeCharacter(json: JSONObject): CharacterState {
    val id = json.optString("id")
    val profile = decodeCharacterStatProfile(json.optJSONObject("statProfile"), id)
    val vital = decodeCharacterVitalState(json.optJSONObject("vitalState"), profile)
    return CharacterState(
    id = id, name = json.optString("name"),
    avatarRef = json.nullableString("avatarRef"), healthState = json.nullableString("healthState"),
    statProfile = profile,
    vitalState = vital,
    injuries = json.optJSONArray("injuries").strings(),
'''
if new_decode not in codec:
    codec = replace_once(codec, old_decode, new_decode, "Character stat decode start")

old_decode_end = '''    physiology = decodePhysiology(json.optJSONObject("physiology")),
    metadata = json.optJSONObject("metadata").stringsMap()
  )

  private fun physiology(value: PhysiologyState) = JSONObject().apply {
'''
new_decode_end = '''    physiology = decodePhysiology(json.optJSONObject("physiology")),
    metadata = json.optJSONObject("metadata").stringsMap()
  )
  }

  private fun characterStatProfile(value: CharacterStatProfile) = JSONObject().apply {
    put("baseMaxHp", value.baseMaxHp)
    put("energy", energyProfile(value.energy))
    put("regen", hpRegenRule(value.regen))
    put("str", value.str)
    put("df", value.df)
    put("agi", value.agi)
    put("crit", value.crit)
    put("combatRole", value.combatRole)
    put("statSource", value.statSource.name)
  }

  private fun decodeCharacterStatProfile(json: JSONObject?, characterId: String): CharacterStatProfile {
    val fallback = CharacterStatProfiles.forId(characterId)
    if (json == null) return fallback
    return CharacterStatProfile(
      baseMaxHp = json.optInt("baseMaxHp", fallback.baseMaxHp).coerceAtLeast(1),
      energy = decodeEnergyProfile(json.optJSONObject("energy"), fallback.energy),
      regen = decodeHpRegenRule(json.optJSONObject("regen"), fallback.regen),
      str = json.optInt("str", fallback.str),
      df = json.optInt("df", fallback.df),
      agi = json.optInt("agi", fallback.agi),
      crit = json.optInt("crit", fallback.crit),
      combatRole = json.optString("combatRole", fallback.combatRole),
      statSource = enumOr(fallback.statSource, json.optString("statSource"))
    )
  }

  private fun energyProfile(value: EnergyProfile) = JSONObject().apply {
    put("mode", value.mode.name)
    putNullable("max", value.max)
  }

  private fun decodeEnergyProfile(json: JSONObject?, fallback: EnergyProfile): EnergyProfile {
    if (json == null) return fallback
    val mode = enumOr(fallback.mode, json.optString("mode"))
    return when (mode) {
      EnergyMode.INFINITE -> EnergyProfile.infinite()
      EnergyMode.NOT_APPLICABLE -> EnergyProfile.notApplicable()
      EnergyMode.FINITE -> EnergyProfile.finite(json.optInt("max", fallback.max ?: 0))
    }
  }

  private fun hpRegenRule(value: HpRegenRule) = JSONObject().apply {
    put("amountPerCompletedTurn", value.amountPerCompletedTurn)
    putNullable("sourceId", value.sourceId)
    put("enabled", value.enabled)
  }

  private fun decodeHpRegenRule(json: JSONObject?, fallback: HpRegenRule): HpRegenRule {
    if (json == null) return fallback
    return HpRegenRule(
      amountPerCompletedTurn = json.optInt("amountPerCompletedTurn", fallback.amountPerCompletedTurn).coerceAtLeast(0),
      sourceId = json.nullableString("sourceId") ?: fallback.sourceId,
      enabled = json.optBoolean("enabled", fallback.enabled)
    )
  }

  private fun characterVitalState(value: CharacterVitalState) = JSONObject().apply {
    put("currentHp", value.currentHp)
    put("condition", value.condition.name)
    putNullable("lastRegenCompletedTurnId", value.lastRegenCompletedTurnId)
  }

  private fun decodeCharacterVitalState(json: JSONObject?, profile: CharacterStatProfile): CharacterVitalState {
    if (json == null) return CharacterVitalState(currentHp = profile.baseMaxHp)
    return CharacterVitalState(
      currentHp = json.optInt("currentHp", profile.baseMaxHp).coerceAtLeast(0),
      condition = enumOr(CharacterCondition.HEALTHY, json.optString("condition")),
      lastRegenCompletedTurnId = json.nullableString("lastRegenCompletedTurnId")
    )
  }

  private fun physiology(value: PhysiologyState) = JSONObject().apply {
'''
if new_decode_end not in codec:
    codec = replace_once(codec, old_decode_end, new_decode_end, "Character stat codec helpers")

CODEC.write_text(codec, encoding="utf-8")

for marker in (
    "data class CharacterStatProfile(",
    "data class CharacterVitalState(",
    "data class EffectiveCharacterStats(",
    "COMMANDER / SUPREME MARKSMAN / HIGH-MOBILITY COMBATANT",
    "SCOUT / TARGET ELIMINATOR / DUAL-GUN MARKSMAN",
    "HIGH-SPEED SWORDSMAN / ASSAULT / COUNTER / EXECUTION",
    "PROTECTED FOLLOWER / NON-COMBAT",
    "val statProfile: CharacterStatProfile = CharacterStatProfiles.forId(id)",
    "put(\"statProfile\", characterStatProfile(value.statProfile))",
    "decodeCharacterStatProfile(json.optJSONObject(\"statProfile\"), id)",
):
    combined = SCHEMA.read_text(encoding="utf-8") + GAME_STATE.read_text(encoding="utf-8") + CODEC.read_text(encoding="utf-8")
    if marker not in combined:
        raise RuntimeError("Character Stat schema marker missing: " + marker)

print("Character Stat schema installed: normalized base stats, energy, regen rule, vitals, and save codec support.")
