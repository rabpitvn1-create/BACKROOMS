package com.rabpit.backroom.core

/** Explicitly unlock a kit for mechanics tests without changing stats or encounter scaling. */
fun GameState.withUnlockedSkills(vararg ids: String): GameState {
  val selected = if (ids.isEmpty()) characters.keys else ids.toSet()
  return copy(characters = characters.mapValues { (id, actor) ->
    if (id in selected) actor.copy(progression = actor.progression.copy(level = 90, exp = 0)) else actor
  })
}
