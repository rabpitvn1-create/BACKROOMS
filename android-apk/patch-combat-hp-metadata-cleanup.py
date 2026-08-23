from pathlib import Path

ROOT = Path(__file__).resolve().parent
COMBAT = ROOT / "app/src/main/java/com/rabpit/backroom/core/CombatRuntime.kt"
DETAIL = ROOT / "app/src/main/java/com/rabpit/backroom/core/CharacterDetailProjection.kt"
text = COMBAT.read_text(encoding="utf-8")

# CharacterVitalState is now the sole source of truth for Kai HP. Remove every residual reference
# to the retired combat.playerHp / combat.playerMaxHp constants after the larger status patch runs.
text = text.replace('  private const val PLAYER_HP = "combat.playerHp"\n', '')
text = text.replace('  private const val PLAYER_MAX_HP = "combat.playerMaxHp"\n', '')
text = text.replace(
    '    val playerMax = state.metadata[PLAYER_MAX_HP]?.toIntOrNull()?.coerceIn(1, 999) ?: 100\n'
    '    val playerHp = state.metadata[PLAYER_HP]?.toIntOrNull()?.coerceIn(0, playerMax) ?: playerMax\n',
    '    val effective = CharacterStatEngine.effective(state, KAI_ID)\n'
    '    val playerMax = effective.maxHp\n'
    '    val playerHp = state.characters[KAI_ID]?.vitalState?.currentHp?.coerceIn(0, playerMax) ?: playerMax\n'
)
text = text.replace('    metadata[PLAYER_HP] = c.playerHp.toString()\n', '')
text = text.replace('    metadata[PLAYER_MAX_HP] = c.playerMaxHp.toString()\n', '')
text = text.replace(
    '    val playerMax = m[PLAYER_MAX_HP]?.toIntOrNull()?.coerceAtLeast(1) ?: 100\n'
    '    return Snapshot(\n',
    '    val playerMax = CharacterStatEngine.effective(state, KAI_ID).maxHp\n'
    '    val playerHp = state.characters[KAI_ID]?.vitalState?.currentHp?.coerceIn(0, playerMax) ?: playerMax\n'
    '    return Snapshot(\n'
)
text = text.replace(
    '      playerHp = m[PLAYER_HP]?.toIntOrNull()?.coerceIn(0, playerMax) ?: playerMax,\n',
    '      playerHp = playerHp,\n'
)
legacy_clear = '''  private fun clearCombatOnly(state: GameState): GameState {
    val preservedHp = state.metadata[PLAYER_HP]
    val preservedMax = state.metadata[PLAYER_MAX_HP]
    val metadata = state.metadata.filterKeys { !it.startsWith(PREFIX) }.toMutableMap()
    if (preservedHp != null) metadata[PLAYER_HP] = preservedHp
    if (preservedMax != null) metadata[PLAYER_MAX_HP] = preservedMax
    return state.copy(metadata = metadata)
  }
'''
modern_clear = '''  private fun clearCombatOnly(state: GameState): GameState {
    val metadata = state.metadata.filterKeys { !it.startsWith(PREFIX) }
    return state.copy(metadata = metadata)
  }
'''
text = text.replace(legacy_clear, modern_clear)

if "PLAYER_HP" in text or "PLAYER_MAX_HP" in text:
    remaining = [line.strip() for line in text.splitlines() if "PLAYER_HP" in line or "PLAYER_MAX_HP" in line]
    raise RuntimeError("Legacy combat HP metadata reference remains: " + " | ".join(remaining))

if "CharacterStatEngine.effective(state, KAI_ID).maxHp" not in text:
    raise RuntimeError("CombatRuntime no longer reads effective Kai Max HP")
if "CharacterStatEngine.setCurrentHp" not in text:
    raise RuntimeError("CombatRuntime no longer writes authoritative Kai HP")

COMBAT.write_text(text, encoding="utf-8")

# CharacterDetailProjection is a public test/UI projection used by older call sites with named
# constructor arguments. New Status fields get safe defaults so adding RPG presentation data does
# not force unrelated existing tests or callers to manufacture values they do not care about.
detail = DETAIL.read_text(encoding="utf-8")
replacements = {
    '  val role: String,\n': '  val role: String = "UNSPECIFIED",\n',
    '  val energyDisplay: String,\n': '  val energyDisplay: String = "N/A",\n',
    '  val regenPerCompletedTurn: Int,\n': '  val regenPerCompletedTurn: Int = 0,\n',
    '  val condition: CharacterCondition,\n': '  val condition: CharacterCondition = CharacterCondition.HEALTHY,\n',
    '  val str: StatLineProjection,\n': '  val str: StatLineProjection = StatLineProjection(10, 0, 10),\n',
    '  val df: StatLineProjection,\n': '  val df: StatLineProjection = StatLineProjection(10, 0, 10),\n',
    '  val agi: StatLineProjection,\n': '  val agi: StatLineProjection = StatLineProjection(10, 0, 10),\n',
    '  val crit: StatLineProjection,\n': '  val crit: StatLineProjection = StatLineProjection(10, 0, 10),\n',
    '  val inventoryDetails: List<ItemDetailProjection>,\n': '  val inventoryDetails: List<ItemDetailProjection> = emptyList(),\n',
    '  val equipmentDetails: List<ItemDetailProjection>,\n': '  val equipmentDetails: List<ItemDetailProjection> = emptyList(),\n',
}
for old, new in replacements.items():
    if new not in detail:
        if old not in detail:
            raise RuntimeError("CharacterDetailProjection compatibility anchor missing: " + old.strip())
        detail = detail.replace(old, new, 1)
DETAIL.write_text(detail, encoding="utf-8")

print("Combat HP metadata cleanup and Character Detail projection compatibility applied.")
