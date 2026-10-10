package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.GameState
import com.rabpit.backroom.core.GameStateCodec
import org.json.JSONObject
import java.io.IOException

/** Event/snapshot provenance boundary. No bridge accepts facts or caller event JSON. */
internal object NativeObservationSource {
  class VerifiedEvent private constructor(
    val scope: CompanionExposurePolicy.Scope,
    val eventType: String,
    private val snapshot: String,
    private val projection: String?
  ) {
    internal fun state(): GameState = GameStateCodec.decode(snapshot)
    /** A field whitelist is not a perception authorization. */
    fun publicProjection(): JSONObject? = projection?.let { JSONObject(it) }
    fun facts(actorId: String): CompanionExposurePolicy.NativeFacts =
      NativePerceptionAdapter.perceive(state(), scope, actorId)

    /** Current Core lacks positive sensory fields: this path cannot invent an observation. */
    fun candidates(actorIds: List<String>): List<ObservationCandidate> {
      require(actorIds.size<=64 && actorIds.toSet().size==actorIds.size) { "observation_actor_bound" }
      val snapshot=state()
      val facts=actorIds.map { NativePerceptionAdapter.perceive(snapshot,scope,it) }
      val public=publicProjection() ?: return emptyList()
      val channels=if(eventType == "EXIT_STREAK_RESOLVED" && !public.getBoolean("completed")) emptySet()
        else setOf(CompanionExposurePolicy.Channel.SEEN)
      val event=CompanionExposurePolicy.Event(scope,CompanionExposurePolicy.Publication.PERCEPTIBLE,channels)
      val eligible=CompanionExposurePolicy.eligible(event,facts)
      return java.util.Collections.unmodifiableList(eligible.map { ObservationCandidate.fromEligible(it,public) })
    }

    companion object {
      @Throws(IOException::class)
      fun read(store: CompanionSlotStore, requestId: String, expectedRevision: Long,
        exactInput: String, eventId: String): VerifiedEvent = store.inspectNative(requestId) { view ->
        // build() revalidates pending identity, exact input, snapshot, reserved tape and native replay.
        val batch=CompanionWaitBatch.build(view,requestId,expectedRevision,exactInput)
        val event=batch.events.singleOrNull { it.id == eventId }
          ?: throw IOException("observation_event_missing")
        val before=GameStateCodec.decode(batch.beforeSnapshot)
        val after=GameStateCodec.decode(batch.afterSnapshot)
        val payload=JSONObject(event.payload)
        val state=when(event.type) {
          "WAIT_COMPLETED" -> before
          "EXIT_STREAK_RESOLVED" -> if (payload.getBoolean("completed")) after else before
          "WORLD_TRANSITION", "COMBAT_STARTED" -> after
          else -> throw IOException("observation_event_unsupported")
        }
        val scene=sceneAt(state) ?: throw IOException("observation_scene_missing")
        val scope=CompanionExposurePolicy.Scope(batch.slotId,batch.turnId,
          batch.expectedRevision+1,event.id,scene)
        val public=PublicEventProjection.project(event.type,payload)?.let { CompanionWaitCapture.canonical(it) }
        VerifiedEvent(scope,event.type,GameStateCodec.encode(state),public)
      }
    }
  }
  /** Stop key preserves named-area identity even when two areas share a WorldNodeId. */
  internal fun sceneAt(state: GameState): String? =
    state.world["journeyStopKey"]?.takeIf { it.isNotBlank() }
      ?: state.world["location"]?.takeIf { it.isNotBlank() }
      ?: state.world["worldNodeId"]?.takeIf { it.isNotBlank() }
}
