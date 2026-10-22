package com.rabpit.backroom.core

/** Unlocks are derived from each actor's level; never accepted from UI or saved flags. */
object CharacterSkillUnlocks {
  fun requiredLevel(characterId: String, name: String): Int? {
    val all = CompanionSkillCatalog.forCharacter(characterId)
    val skill = all.firstOrNull { it.name == name } ?: return null
    if (skill.kind == "PASSIVE") return 0
    val ordered = all.filter { it.kind != "PASSIVE" }.sortedBy { it.kind == "ULTIMATE" }
    return ordered.indexOf(skill) * 10
  }

  fun isUnlocked(characterId: String, name: String, level: Int): Boolean =
    requiredLevel(characterId, name)?.let { level >= it } ?: false

  fun isUnlocked(state: GameState, characterId: String, name: String): Boolean =
    state.characters[characterId]?.let { isUnlocked(characterId, name, it.progression.level) } ?: false

  /** Discard ongoing effects whose source skill is locked (including loaded combat state). */
  fun enforceCombatState(state: GameState): GameState {
    val effects = listOf(
      Triple(CAO_MINH_ID, "Thiên Ma Bộ", listOf("combat.caoMinhQuickStepTurns")),
      Triple(CAO_MINH_ID, "Huyết Ma Tứ Liên", listOf("combat.caoMinhBleedTurns")),
      Triple(IRIS_ID, "Honeycomb Fire", listOf("combat.irisArmorBreakTurns")),
      Triple(IRIS_ID, "ARGUS // Thousandfold Execution", listOf("combat.irisExposedTurns")),
      Triple(SYVIAL_ID, "Devil Trigger", listOf("combat.syvialDevilTrigger")),
      Triple(SYVIAL_ID, "Crimson Guillotine", listOf("combat.syvialBleedTurns")),
      Triple(SYVIAL_ID, "Spatial Dominion", listOf("combat.syvialDisorientTurns")),
      Triple(LUCIA_ID, "Toxic Burst", listOf("combat.luciaPoisonTurns", "combat.luciaPoisonPercent")),
      Triple(LUCIA_ID, "Armor-Piercing Burst", listOf("combat.luciaArmorBreakTurns", "combat.luciaArmorBreakPercent")),
      Triple(LUCIA_ID, "Rending Burst", listOf("combat.luciaBleedTurns", "combat.luciaBleedPercent")),
      Triple(LUC_TRAM_ID, "Thiên Kiếm Định Giới", listOf("combat.lucTramDomainWave"))
    )
    val blocked = effects.filterNot { isUnlocked(state, it.first, it.second) }.flatMap { it.third }.toMutableSet()
    listOf("Huyết Sát Kiếm Ấn", "Phá Giáp Ma Kiếm", "Ma Tâm Chấn", "Huyết Độc Ma Khí", "Huyết Liệt Ma Ấn")
      .forEachIndexed { index, name -> if (!isUnlocked(state, CAO_MINH_ID, name)) blocked += "combat.caoMinhProc$index" }
    // Old aggregate poison does not identify Toxic vs Corrosive. Until both
    // are open, require an explicit unlocked source written by the current proc.
    val poisonSource = state.metadata["combat.luciaPoisonSource"]
    if ((poisonSource == null && !isUnlocked(state, LUCIA_ID, "Corrosive Burst")) ||
      (poisonSource != null && (poisonSource !in setOf("Toxic Burst", "Corrosive Burst") ||
        !isUnlocked(state, LUCIA_ID, poisonSource)))) {
      blocked += listOf("combat.luciaPoisonTurns", "combat.luciaPoisonPercent", "combat.luciaPoisonSource")
    }
    val metadata = state.metadata - blocked
    return if (metadata == state.metadata) state else state.copy(metadata = metadata)
  }
}
