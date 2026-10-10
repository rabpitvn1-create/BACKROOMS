package com.rabpit.backroom.core.companion

/** Consistency check only. Native snapshot, audits and durable reservation still authorize execution. */
internal object CompanionLockedProposal {
  fun consistent(decided:CharacterDecisionOrchestrator.Decided):Boolean =
    decided.intent==decided.binding.intent && decided.targetId==decided.binding.targetId &&
      decided.itemId==decided.binding.itemId
  fun turnMatches(decided:CharacterDecisionOrchestrator.Decided,turn:String):Boolean =
    decided.binding.interaction?.turnId?.let { it==turn } ?: true
  fun sceneMatches(decided:CharacterDecisionOrchestrator.Decided,scene:String):Boolean =
    decided.binding.sceneId?.let { it==scene } ?: true
}
