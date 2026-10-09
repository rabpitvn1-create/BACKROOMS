package com.rabpit.backroom.core.companion

import java.nio.charset.StandardCharsets
import java.util.Collections

/**
 * Pure eligibility policy, not a source of perception facts or a persistence authority.
 * Only a reviewed native adapter may supply facts. No bridge/provider/JSON entry point.
 * Runtime observation creation remains disabled until that adapter and atomic storage qualify.
 */
internal object CompanionExposurePolicy {
  const val VERSION = "companion_exposure.v1"
  enum class Fact { YES, NO, UNKNOWN }
  enum class Channel { SEEN, HEARD }
  enum class Publication { PERCEPTIBLE, PRIVATE_GM, UNCLASSIFIED }

  data class Scope(val slotId: String, val turnId: String, val revision: Long,
                   val eventId: String, val sceneId: String) {
    init {
      require(slotId.matches(Regex("[0-9a-f]{32}"))) { "slot_invalid" }
      require(revision > 0) { "revision_invalid" }
      requireId(turnId, "turn_invalid")
      requireId(eventId, "event_invalid")
      require(sceneId.isNotBlank() && sceneId.toByteArray(StandardCharsets.UTF_8).size <= 256 &&
        wellFormedText(sceneId) && sceneId.none { it.isISOControl() }) { "scene_invalid" }
    }
  }

  class Event(val scope: Scope, val publication: Publication, channels: Set<Channel>) {
    val channels: Set<Channel> = Collections.unmodifiableSet(HashSet(channels))
    init {
      require(publication == Publication.PERCEPTIBLE || channels.isEmpty()) { "private_channels_invalid" }
    }
  }

  data class NativeFacts(
    val scope: Scope, val actorId: String,
    val sceneMember: Fact, val inReach: Fact, val conscious: Fact,
    val visible: Fact, val audible: Fact
  ) {
    init { requireId(actorId, "actor_invalid") }
  }

  /** References only. Eligibility grants neither access to a GM payload nor objective truth. */
  data class Eligible(val scope: Scope, val actorId: String, val channel: Channel) {
    val policyVersion: String get() = VERSION
  }

  fun eligible(event: Event, facts: List<NativeFacts>): List<Eligible> {
    require(facts.size <= 64) { "exposure_bound" }
    val inputs = facts.toList()
    require(inputs.size <= 64) { "exposure_bound" }
    val actors = HashSet<String>()
    for (fact in inputs) {
      require(fact.scope == event.scope) { "evidence_scope_mismatch" }
      require(actors.add(fact.actorId)) { "duplicate_actor_evidence" }
    }
    if (event.publication != Publication.PERCEPTIBLE) return emptyList()
    val result = ArrayList<Eligible>()
    for (fact in inputs.sortedBy { it.actorId }) {
      if (fact.sceneMember != Fact.YES || fact.inReach != Fact.YES || fact.conscious != Fact.YES) continue
      if (Channel.SEEN in event.channels && fact.visible == Fact.YES)
        result.add(Eligible(event.scope, fact.actorId, Channel.SEEN))
      if (Channel.HEARD in event.channels && fact.audible == Fact.YES)
        result.add(Eligible(event.scope, fact.actorId, Channel.HEARD))
    }
    return Collections.unmodifiableList(result)
  }

  private fun wellFormedText(value: String): Boolean {
    var index = 0
    while (index < value.length) {
      val c = value[index++]
      if (Character.isHighSurrogate(c)) {
        if (index == value.length || !Character.isLowSurrogate(value[index++])) return false
      } else if (Character.isLowSurrogate(c)) return false
    }
    return true
  }

  private fun requireId(value: String, reason: String) {
    require(value.matches(Regex("[A-Za-z0-9_.:-]{1,160}"))) { reason }
  }
}
