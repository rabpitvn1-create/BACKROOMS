from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
STATS = CORE / "CharacterStats.kt"
CODEC = CORE / "GameStateCodec.kt"
SYSTEM = CORE / "CharacterEquipmentSystem.kt"
TEST = TESTS / "LuciaRegenIntervalTest.kt"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


# Add an interval to the existing generic regeneration rule. Existing profiles keep
# interval=1, so Kai/Iris/Syvial retain their established every-turn regeneration.
stats = STATS.read_text(encoding="utf-8")
stats = replace_once(
    stats,
    '''data class HpRegenRule(
  val amountPerCompletedTurn: Int = 0,
  val sourceId: String? = null,
  val enabled: Boolean = false
)
''',
    '''data class HpRegenRule(
  val amountPerCompletedTurn: Int = 0,
  val sourceId: String? = null,
  val enabled: Boolean = false,
  val intervalCompletedTurns: Int = 1
)
''',
    "HP regen interval schema",
)
stats = replace_once(
    stats,
    '''data class CharacterVitalState(
  val currentHp: Int = 100,
  val condition: CharacterCondition = CharacterCondition.HEALTHY,
  val lastRegenCompletedTurnId: String? = null
)
''',
    '''data class CharacterVitalState(
  val currentHp: Int = 100,
  val condition: CharacterCondition = CharacterCondition.HEALTHY,
  val lastRegenCompletedTurnId: String? = null,
  val completedTurnsSinceRegen: Int = 0
)
''',
    "HP regen progress schema",
)
stats = replace_once(
    stats,
    '''  private val lucia = CharacterStatProfile(
    baseMaxHp = 100,
    energy = EnergyProfile.notApplicable(),
    regen = HpRegenRule(),
''',
    '''  private val lucia = CharacterStatProfile(
    baseMaxHp = 100,
    energy = EnergyProfile.notApplicable(),
    regen = HpRegenRule(2, "lucia:three-turn-regeneration", true, 3),
''',
    "Lucia three-turn regeneration profile",
)
STATS.write_text(stats, encoding="utf-8")

# Persist both the configured interval and Lucia's in-progress turn counter so
# save/load cannot reset the three-turn cadence.
codec = CODEC.read_text(encoding="utf-8")
codec = replace_once(
    codec,
    '''  private fun hpRegenRule(value: HpRegenRule) = JSONObject().apply {
    put("amountPerCompletedTurn", value.amountPerCompletedTurn)
    putNullable("sourceId", value.sourceId)
    put("enabled", value.enabled)
  }
''',
    '''  private fun hpRegenRule(value: HpRegenRule) = JSONObject().apply {
    put("amountPerCompletedTurn", value.amountPerCompletedTurn)
    putNullable("sourceId", value.sourceId)
    put("enabled", value.enabled)
    put("intervalCompletedTurns", value.intervalCompletedTurns)
  }
''',
    "HP regen interval encode",
)
codec = replace_once(
    codec,
    '''    return HpRegenRule(
      amountPerCompletedTurn = json.optInt("amountPerCompletedTurn", fallback.amountPerCompletedTurn).coerceAtLeast(0),
      sourceId = json.nullableString("sourceId") ?: fallback.sourceId,
      enabled = json.optBoolean("enabled", fallback.enabled)
    )
''',
    '''    return HpRegenRule(
      amountPerCompletedTurn = json.optInt("amountPerCompletedTurn", fallback.amountPerCompletedTurn).coerceAtLeast(0),
      sourceId = json.nullableString("sourceId") ?: fallback.sourceId,
      enabled = json.optBoolean("enabled", fallback.enabled),
      intervalCompletedTurns = json.optInt("intervalCompletedTurns", fallback.intervalCompletedTurns).coerceAtLeast(1)
    )
''',
    "HP regen interval decode",
)
codec = replace_once(
    codec,
    '''  private fun characterVitalState(value: CharacterVitalState) = JSONObject().apply {
    put("currentHp", value.currentHp)
    put("condition", value.condition.name)
    putNullable("lastRegenCompletedTurnId", value.lastRegenCompletedTurnId)
  }
''',
    '''  private fun characterVitalState(value: CharacterVitalState) = JSONObject().apply {
    put("currentHp", value.currentHp)
    put("condition", value.condition.name)
    putNullable("lastRegenCompletedTurnId", value.lastRegenCompletedTurnId)
    put("completedTurnsSinceRegen", value.completedTurnsSinceRegen)
  }
''',
    "HP regen progress encode",
)
codec = replace_once(
    codec,
    '''    return CharacterVitalState(
      currentHp = json.optInt("currentHp", profile.baseMaxHp).coerceAtLeast(0),
      condition = enumOr(CharacterCondition.HEALTHY, json.optString("condition")),
      lastRegenCompletedTurnId = json.nullableString("lastRegenCompletedTurnId")
    )
''',
    '''    return CharacterVitalState(
      currentHp = json.optInt("currentHp", profile.baseMaxHp).coerceAtLeast(0),
      condition = enumOr(CharacterCondition.HEALTHY, json.optString("condition")),
      lastRegenCompletedTurnId = json.nullableString("lastRegenCompletedTurnId"),
      completedTurnsSinceRegen = json.optInt("completedTurnsSinceRegen", 0).coerceAtLeast(0)
    )
''',
    "HP regen progress decode",
)
CODEC.write_text(codec, encoding="utf-8")

# Count every distinct completed gameplay/combat turn. Heal only when the configured
# interval is reached, then reset the counter. Duplicate turn IDs remain idempotent.
system = SYSTEM.read_text(encoding="utf-8")
system = replace_once(
    system,
    '''      val rule = character.statProfile.regen
      if (!rule.enabled || rule.amountPerCompletedTurn <= 0 || character.vitalState.lastRegenCompletedTurnId == completedTurnId) return@forEach
      val healed = (hp + rule.amountPerCompletedTurn).coerceAtMost(effective.maxHp)
      val vital = character.vitalState.copy(
        currentHp = healed,
        condition = conditionFor(healed, effective.maxHp, character.vitalState.condition, character.presence),
        lastRegenCompletedTurnId = completedTurnId
      )
''',
    '''      val rule = character.statProfile.regen
      if (!rule.enabled || rule.amountPerCompletedTurn <= 0 || character.vitalState.lastRegenCompletedTurnId == completedTurnId) return@forEach
      val interval = rule.intervalCompletedTurns.coerceAtLeast(1)
      val completed = (character.vitalState.completedTurnsSinceRegen + 1).coerceAtMost(interval)
      val shouldHeal = completed >= interval
      val healed = if (shouldHeal) (hp + rule.amountPerCompletedTurn).coerceAtMost(effective.maxHp) else hp
      val vital = character.vitalState.copy(
        currentHp = healed,
        condition = conditionFor(healed, effective.maxHp, character.vitalState.condition, character.presence),
        lastRegenCompletedTurnId = completedTurnId,
        completedTurnsSinceRegen = if (shouldHeal) 0 else completed
      )
''',
    "interval regeneration runtime",
)
SYSTEM.write_text(system, encoding="utf-8")

TEST.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Test

class LuciaRegenIntervalTest {
  private fun injuredLucia(): GameState {
    val base = LuciaCanon.ensure(GameState.initial())
    val lucia = base.characters.getValue(LUCIA_ID)
    return base.copy(characters = base.characters + (
      LUCIA_ID to lucia.copy(
        vitalState = lucia.vitalState.copy(
          currentHp = 90,
          completedTurnsSinceRegen = 0,
          lastRegenCompletedTurnId = null
        )
      )
    ))
  }

  @Test fun luciaHealsTwoHpOnlyAfterThreeDistinctCompletedTurns() {
    val start = injuredLucia()
    val first = CharacterStatEngine.applyCompletedTurnRegen(start, "TURN_1")
    assertEquals(90, first.characters.getValue(LUCIA_ID).vitalState.currentHp)
    assertEquals(1, first.characters.getValue(LUCIA_ID).vitalState.completedTurnsSinceRegen)

    val second = CharacterStatEngine.applyCompletedTurnRegen(first, "TURN_2")
    assertEquals(90, second.characters.getValue(LUCIA_ID).vitalState.currentHp)
    assertEquals(2, second.characters.getValue(LUCIA_ID).vitalState.completedTurnsSinceRegen)

    val third = CharacterStatEngine.applyCompletedTurnRegen(second, "TURN_3")
    assertEquals(92, third.characters.getValue(LUCIA_ID).vitalState.currentHp)
    assertEquals(0, third.characters.getValue(LUCIA_ID).vitalState.completedTurnsSinceRegen)

    val duplicate = CharacterStatEngine.applyCompletedTurnRegen(third, "TURN_3")
    assertEquals(92, duplicate.characters.getValue(LUCIA_ID).vitalState.currentHp)
    assertEquals(0, duplicate.characters.getValue(LUCIA_ID).vitalState.completedTurnsSinceRegen)
  }

  @Test fun saveLoadPreservesLuciaThreeTurnRegenProgress() {
    val first = CharacterStatEngine.applyCompletedTurnRegen(injuredLucia(), "TURN_1")
    val second = CharacterStatEngine.applyCompletedTurnRegen(first, "TURN_2")
    val restored = GameStateCodec.decode(GameStateCodec.encode(second))
    val third = CharacterStatEngine.applyCompletedTurnRegen(restored, "COMBAT_TURN_3")

    val lucia = third.characters.getValue(LUCIA_ID)
    assertEquals(92, lucia.vitalState.currentHp)
    assertEquals(0, lucia.vitalState.completedTurnsSinceRegen)
    assertEquals(3, lucia.statProfile.regen.intervalCompletedTurns)
    assertEquals(2, lucia.statProfile.regen.amountPerCompletedTurn)
  }
}
''', encoding="utf-8")

combined = "\n".join(
    path.read_text(encoding="utf-8") for path in (STATS, CODEC, SYSTEM, TEST)
)
for marker in (
    "val intervalCompletedTurns: Int = 1",
    "val completedTurnsSinceRegen: Int = 0",
    'HpRegenRule(2, "lucia:three-turn-regeneration", true, 3)',
    'put("intervalCompletedTurns", value.intervalCompletedTurns)',
    'put("completedTurnsSinceRegen", value.completedTurnsSinceRegen)',
    "val shouldHeal = completed >= interval",
    "class LuciaRegenIntervalTest",
):
    if marker not in combined:
        raise RuntimeError("Lucia regeneration contract missing: " + marker)

print("Lucia regeneration installed: +2 HP after every 3 distinct completed turns, persisted across save/load.")
