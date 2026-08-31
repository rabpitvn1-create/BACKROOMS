package com.rabpit.backroom.core

/**
 * Authoritative campaign gates for story-owned companion encounters.
 *
 * These gates are deterministic. LiteRT/Gemini may narrate a committed encounter,
 * but neither model may roll, move, or materialize these characters on its own.
 */
object StoryCompanionContinuity {
  const val LUCIA_ID = "lucia"
  const val SYVIAL_ID = "syvial"
  const val IRIS_ID = "iris"

  const val LUCIA_LEVEL = 0
  const val SYVIAL_LEVEL = 37
  const val IRIS_LEVEL = 94

  private val fixedLevels = mapOf(
    LUCIA_ID to LUCIA_LEVEL,
    SYVIAL_ID to SYVIAL_LEVEL,
    IRIS_ID to IRIS_LEVEL
  )

  @JvmStatic
  fun fixedLevel(characterId: String): Int? = fixedLevels[characterId.trim().lowercase()]

  @JvmStatic
  fun isStoryOwned(characterId: String): Boolean = fixedLevel(characterId) != null

  @JvmStatic
  fun randomSpawnAllowed(characterId: String): Boolean = !isStoryOwned(characterId)

  @JvmStatic
  fun canMaterialize(characterId: String, currentLevel: Int, alreadyPresent: Boolean): Boolean {
    if (alreadyPresent) return false
    return fixedLevel(characterId) == currentLevel
  }
}
