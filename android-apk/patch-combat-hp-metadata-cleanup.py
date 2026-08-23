from pathlib import Path

ROOT = Path(__file__).resolve().parent
COMBAT = ROOT / "app/src/main/java/com/rabpit/backroom/core/CombatRuntime.kt"
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
print("Combat HP metadata cleanup applied: CharacterVitalState is the only Kai HP source.")
