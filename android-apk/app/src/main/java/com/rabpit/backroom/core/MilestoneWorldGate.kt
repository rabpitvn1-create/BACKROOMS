package com.rabpit.backroom.core

import org.json.JSONObject

/**
 * Isolation between scripted Checkpoint progression and the old random Exit
 * mechanic. A route inside the same parent Level can still be traversed
 * deliberately; crossing to a new Level must come from a later explicit
 * narrative progression contract, never a hidden-exit roll.
 */
object MilestoneWorldGate {
  @JvmStatic fun allowScriptedRoute(fromId: String, toId: String): Boolean {
    val from = LevelRouteRegistry.node(fromId) ?: return false
    val to = LevelRouteRegistry.node(toId) ?: return false
    return from.parent == to.parent && LevelRouteRegistry.canMove(from.id, to.id)
  }

  /** Keep unrelated exploration, discovery, snapshot and actor information. */
  @JvmStatic fun stripLegacyExitProof(flags: JSONObject): JSONObject {
    val clean = JSONObject(flags.toString())
    val exploration = clean.optJSONObject("exploration")
    if (exploration != null) {
      listOf(
        "hiddenExitStreak", "exitReady", "transitionReady", "exitProgress",
        "confirmedExit", "exitCandidate"
      ).forEach(exploration::remove)
    }
    listOf("exitProgress", "exitChanceThreshold").forEach(clean::remove)
    return clean
  }

  /**
   * An untrusted GM/client cannot revive the hidden Exit by including old
   * flags in a ValidatedWorldStateCommand.
   */
  fun safeguardCandidate(state: GameState, candidate: JSONObject): Boolean {
    if (StoryMilestonePlan.current(state) == null) return true
    val from = LevelRouteRegistry.requested(
      JSONObject(state.world["levelJson"] ?: "{}")) ?: return false
    val proposed = LevelRouteRegistry.requested(candidate.optJSONObject("level"))
    if (candidate.has("level") && proposed == null) return false
    if (proposed != null && proposed.id != from.id &&
      !allowScriptedRoute(from.id, proposed.id)) return false
    val flags = candidate.optJSONObject("flags")
    if (flags != null) candidate.put("flags", stripLegacyExitProof(flags))
    return true
  }
}
