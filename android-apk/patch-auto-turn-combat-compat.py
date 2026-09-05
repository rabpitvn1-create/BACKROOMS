from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
COMBAT = CORE / "CombatCore.kt"
PROJECTION = CORE / "CharacterDetailProjection.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


# healthState predates the numeric combat HP and can carry descriptive injury/condition canon.
# Combat HP is persisted in combat.* metadata, so never overwrite the older semantic field.
combat = COMBAT.read_text(encoding="utf-8")
combat = replace_once(
    combat,
    '''    return character.copy(
      healthState = "${safe.currentHp}/${safe.maxHp}",
      metadata = character.metadata + mapOf(''',
    '''    return character.copy(
      metadata = character.metadata + mapOf(''',
    "combat healthState preservation",
)
COMBAT.write_text(combat, encoding="utf-8")

# Keep the existing projection API source-compatible for tests and any callers that construct a
# projection directly. Projector-created values still provide the authoritative combat numbers.
projection = PROJECTION.read_text(encoding="utf-8")
projection = replace_once(
    projection,
    '''  val statusEffects: List<StatusEffect>,
  val combat: CharacterCombatProjection
)''',
    '''  val statusEffects: List<StatusEffect>,
  val combat: CharacterCombatProjection = CharacterCombatProjection(
    currentHp = CombatRules.BASE_HP,
    maxHp = CombatRules.BASE_HP,
    hpStat = CombatRules.BASE_STAT,
    defend = CombatRules.BASE_STAT,
    defensePoints = CombatRules.defensePoints(CombatRules.BASE_STAT),
    agi = CombatRules.BASE_STAT,
    evasionPercent = CombatRules.BASE_EVASION * 100.0,
    crit = CombatRules.BASE_STAT,
    critPercent = CombatRules.BASE_CRIT * 100.0,
    survival = 0,
    survivalTarget = CombatRules.FIRST_SURVIVAL_TARGET,
    growthPerCompletion = 2
  )
)''',
    "character projection constructor compatibility",
)
projection = replace_once(
    projection,
    '      healthState = "${combatStats.currentHp}/${combatStats.maxHp}",',
    '      healthState = character.healthState,',
    "descriptive healthState projection preservation",
)
PROJECTION.write_text(projection, encoding="utf-8")

for path, tokens in {
    COMBAT: ['metadata = character.metadata + mapOf(', 'healthState = "${safe.currentHp}/${safe.maxHp}"'],
    PROJECTION: ['val combat: CharacterCombatProjection = CharacterCombatProjection(', 'healthState = character.healthState'],
}.items():
    text = path.read_text(encoding="utf-8")
    if path == COMBAT:
        if tokens[1] in text:
            raise RuntimeError("Combat progression still overwrites descriptive healthState")
        if tokens[0] not in text:
            raise RuntimeError("Combat metadata persistence disappeared")
    else:
        for token in tokens:
            if token not in text:
                raise RuntimeError(f"Combat projection compatibility marker missing: {token}")

print("Auto-turn Combat compatibility applied: descriptive healthState preserved and projection constructor remains source-compatible.")
