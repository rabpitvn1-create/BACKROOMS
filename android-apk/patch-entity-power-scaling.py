from pathlib import Path

ROOT = Path(__file__).resolve().parent
COMBAT = ROOT / "app/src/main/java/com/rabpit/backroom/core/CombatRuntime.kt"

combat = COMBAT.read_text(encoding="utf-8")


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


if "ENTITY_POWER_SCALING_R01" not in combat:
    combat = replace_once(
        combat,
        "object CombatRuntime {\n",
        """object CombatRuntime {
  // ENTITY_POWER_SCALING_R01
  // Linear on the Level-0 base: full Levels are 100/110/120/130...%, never compounded.
""",
        "Entity power scaling marker",
    )

    combat = replace_once(
        combat,
        """    val entityName: String,
    val phase: Phase,
""",
        """    val entityName: String,
    val progressionRank: Long,
    val phase: Phase,
""",
        "Combat Snapshot progression rank",
    )

    combat = replace_once(
        combat,
        """    val seed = stableSeed(entityKey, state.turn.currentTurnId, state.time.elapsedSubjectiveMinutes)
    val enhancedEntityMaxHp = if (profile.key == DIEP_MINH_KEY) DIEP_MINH_MAX_HP else profile.maxHp + ENTITY_HP_BONUS
""",
        """    val seed = stableSeed(entityKey, state.turn.currentTurnId, state.time.elapsedSubjectiveMinutes)
    val progressionRank = EntityPowerScaling.rankFor(state)
    val unscaledEntityMaxHp = if (profile.key == DIEP_MINH_KEY) DIEP_MINH_MAX_HP else profile.maxHp + ENTITY_HP_BONUS
    val enhancedEntityMaxHp = EntityPowerScaling.scale(unscaledEntityMaxHp, progressionRank)
""",
        "new encounter scaled Entity HP",
    )

    combat = replace_once(
        combat,
        """      entityName = profile.displayName,
      phase = Phase.ACTIVE,
""",
        """      entityName = profile.displayName,
      progressionRank = progressionRank,
      phase = Phase.ACTIVE,
""",
        "new encounter progression rank snapshot",
    )

    combat = replace_once(
        combat,
        """    put("entityKey", c.entityKey)
    put("entityName", c.entityName)
""",
        """    put("entityKey", c.entityKey)
    put("entityName", c.entityName)
    put("progressionRank", c.progressionRank)
""",
        "combat JSON progression rank projection",
    )

    combat = replace_once(
        combat,
        """    metadata["${PREFIX}entityKey"] = c.entityKey
    metadata["${PREFIX}entityName"] = c.entityName
""",
        """    metadata["${PREFIX}entityKey"] = c.entityKey
    metadata["${PREFIX}entityName"] = c.entityName
    metadata["${PREFIX}progressionRank"] = c.progressionRank.toString()
""",
        "persist combat progression rank",
    )

    combat = replace_once(
        combat,
        """    val canonicalMaxHp = if (profile.key == DIEP_MINH_KEY) DIEP_MINH_MAX_HP else profile.maxHp + ENTITY_HP_BONUS
    val storedMaxHp = m["${PREFIX}entityMaxHp"]?.toIntOrNull()?.coerceAtLeast(1) ?: canonicalMaxHp
""",
        """    val progressionRank = m["${PREFIX}progressionRank"]?.toLongOrNull()?.let(EntityPowerScaling::requireRank) ?: 0L
    val unscaledCanonicalMaxHp = if (profile.key == DIEP_MINH_KEY) DIEP_MINH_MAX_HP else profile.maxHp + ENTITY_HP_BONUS
    val canonicalMaxHp = EntityPowerScaling.scale(unscaledCanonicalMaxHp, progressionRank)
    val storedMaxHp = m["${PREFIX}entityMaxHp"]?.toIntOrNull()?.coerceAtLeast(1) ?: canonicalMaxHp
""",
        "decode scaled Entity HP from encounter rank",
    )

    combat = replace_once(
        combat,
        """      entityName = m["${PREFIX}entityName"] ?: profile.displayName,
      phase = enumOr(Phase.ACTIVE, m["${PREFIX}phase"]),
""",
        """      entityName = m["${PREFIX}entityName"] ?: profile.displayName,
      progressionRank = progressionRank,
      phase = enumOr(Phase.ACTIVE, m["${PREFIX}phase"]),
""",
        "decode progression rank snapshot",
    )

    combat = replace_once(
        combat,
        """max(1, profile.attack + roll(c.copy(eventCounter = c.eventCounter + 47), 7) - when (c.cover) { Cover.HARD -> 8; Cover.PARTIAL -> 4; Cover.EXPOSED -> 0 })""",
        """max(1, EntityPowerScaling.scale(profile.attack, c.progressionRank) + roll(c.copy(eventCounter = c.eventCounter + 47), 7) - when (c.cover) { Cover.HARD -> 8; Cover.PARTIAL -> 4; Cover.EXPOSED -> 0 })""",
        "normal Entity damage scaling",
    )

    combat = replace_once(
        combat,
        """percentDamage(c.playerMaxHp, DIEP_MINH_ATTACK_PERCENT)""",
        """EntityPowerScaling.scale(percentDamage(c.playerMaxHp, DIEP_MINH_ATTACK_PERCENT), c.progressionRank)""",
        "Diệp Minh normal attack scaling",
    )

for marker in (
    "ENTITY_POWER_SCALING_R01",
    "val progressionRank: Long",
    "EntityPowerScaling.rankFor(state)",
    "EntityPowerScaling.scale(unscaledEntityMaxHp, progressionRank)",
    'metadata["${PREFIX}progressionRank"] = c.progressionRank.toString()',
    "EntityPowerScaling.scale(profile.attack, c.progressionRank)",
    "EntityPowerScaling.scale(percentDamage(c.playerMaxHp, DIEP_MINH_ATTACK_PERCENT), c.progressionRank)",
):
    if marker not in combat:
        raise RuntimeError("Entity power scaling contract missing: " + marker)

COMBAT.write_text(combat, encoding="utf-8")
print("Entity power scaling applied: linear +10 percentage points per full Level on Level-0 HP/base damage.")
