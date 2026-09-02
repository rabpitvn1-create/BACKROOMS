package com.rabpit.backroom.core

import java.text.Normalizer

/** Checks the actual prose, not the writer's self-reported JSON claims. */
object LevelNarrativePolicy {
  @JvmStatic fun contradictsArea(areaId: String, reply: String): Boolean {
    val text = Normalizer.normalize(reply.lowercase(), Normalizer.Form.NFD)
      .replace(Regex("\\p{M}+"), "").replace('đ', 'd')
    // The Lobby's authored exit leads into epsilon. Parking architecture is a stale Level 1 cue.
    // Keep this scoped to the two authored yellow-room areas; other sublevels can have other materials.
    if (areaId in setOf("0", "epsilon")) {
      if (Regex("\\b(?:be tong|bai (?:do|dau) xe|gara|garage|parking|cot chiu luc|vach son)\\b")
          .containsMatchIn(text)) return true
    }
    return false
  }
}
