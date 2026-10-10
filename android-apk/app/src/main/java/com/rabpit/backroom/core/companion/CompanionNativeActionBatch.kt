package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.CURRENT_SAVE_VERSION
import com.rabpit.backroom.core.GameStateCodec
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.StandardCharsets

/** Fully replayable, no-provider native ordinary action receipt candidate. */
internal object CompanionNativeActionBatch {
  const val VERSION = "companion_manifest.v2"
  data class Event(val id: String,val ordinal: Int,val type: String,
                   val record: String,val digest: String)
  data class Batch(
    val slotId: String,val turnId: String,val requestId: String,val expectedRevision: Long,
    val inputDigest: String,val decisionDigest: String,val reservationDigest: String,
    val before: String,val after: String,val manifest: String,val finalResult: String,
    val events: List<Event>,val observation: ObservationCandidate,val memory: EpisodicMemory.MemoryRecord)

  @JvmStatic @Throws(IOException::class)
  fun prepare(store: CompanionSlotStore,requestId: String,revision: Long,input: String): Batch =
    store.inspectNative(requestId) { view -> build(view,requestId,revision,input) }

  internal fun build(view: CompanionSlotStore.NativeView,requestId: String,
                     revision: Long,input: String): Batch {
    require(view.turn.phase == CompanionPendingTurn.Phase.RESERVED &&
      view.revision == revision && view.turn.expectedRevision == revision &&
      requestId in view.turn.requestAliases && view.turn.reservation != null &&
      view.turn.decision != null) { "ordinary_batch_unreserved" }
    val request = CompanionPendingTurn.Request.fromPlayerInput(
      view.slotId,requestId,revision,"cao_minh",input)
    require(request.inputDigest == view.turn.inputDigest) { "ordinary_batch_input_mismatch" }
    val before=String(view.snapshot(),StandardCharsets.UTF_8)
    val replay=CompanionNativeActionCapture.replay(view,requestId,revision,input,
      view.turn.reservation.canonicalPayload)
    val staged=replay.staged
    val after=staged.after
    val state=GameStateCodec.decode(after)
    require(GameStateCodec.encode(state) == after) { "ordinary_batch_state_invalid" }
    val events=ArrayList<Event>()

    fun event(type: String,payload: JSONObject) {
      val ordinal=events.size
      val id="event-"+CompanionDigests.sha256(view.slotId+"|"+view.turn.turnId+"|"+ordinal)
      val record=CompanionWaitCapture.canonical(JSONObject()
        .put("version","companion_event.v1").put("slot",view.slotId)
        .put("turn",view.turn.turnId).put("revision",revision+1)
        .put("id",id).put("ordinal",ordinal)
        .put("type",type).put("payload",payload))
      events.add(Event(id,ordinal,type,record,CompanionDigests.sha256(record)))
    }
    event("ACTOR_ACTION_COMPLETED",JSONObject()
      .put("actor","cao_minh").put("intent",staged.intent.name)
      .put("scene",staged.fromStop).put("minutes",staged.minutes)
      .put("location",staged.fromStop)
      .put("toStop",staged.toStop)
      .put("elapsedMinutes",state.time.elapsedSubjectiveMinutes))
    event("EXIT_STREAK_RESOLVED",JSONObject()
      .put("success",staged.exitWon).put("streak",staged.streak)
      .put("source",staged.fromStop).put("target",staged.toStop)
      .put("completed",staged.completed))
    if (staged.completed) event("WORLD_TRANSITION",JSONObject()
      .put("source",staged.fromStop).put("target",staged.toStop)
      .put("node",state.world.getValue("worldNodeId")))
    val combat=com.rabpit.backroom.core.Combat93Runtime.toJson(state)
    if (combat?.optBoolean("active") == true) event("COMBAT_STARTED",JSONObject()
      .put("encounterId",combat.getString("encounterId"))
      .put("entities",combat.getJSONArray("entities")))
    require(events.size in 2..4)

    // Native actor perception: own completed action is visible only to its
    // verified, conscious actor. No GM prose and no candidate discoveries.
    val scope = CompanionExposurePolicy.Scope(view.slotId,view.turn.turnId,revision+1,
      events[0].id,staged.fromStop)
    val access = CompanionExposurePolicy.eligible(
      CompanionExposurePolicy.Event(scope,CompanionExposurePolicy.Publication.PERCEPTIBLE,
        setOf(CompanionExposurePolicy.Channel.SEEN)),
      listOf(CompanionExposurePolicy.NativeFacts(scope,"cao_minh",
        CompanionExposurePolicy.Fact.YES,CompanionExposurePolicy.Fact.YES,
        CompanionExposurePolicy.Fact.YES,CompanionExposurePolicy.Fact.YES,
        CompanionExposurePolicy.Fact.UNKNOWN))).single()
    val public=PublicEventProjection.project("ACTOR_ACTION_COMPLETED",
      JSONObject(events[0].record).getJSONObject("payload"))
      ?: error("ordinary_public_projection_denied")
    val observation=ObservationCandidate.fromEligible(access,public)
    val memory=EpisodicMemory.fromObservation(
      observation,view.slotId,"cao_minh","action_"+staged.intent.name.lowercase())

    val result=CompanionWaitCapture.canonical(JSONObject()
      .put("version","companion_result.v2")
      .put("slot",view.slotId).put("turn",view.turn.turnId)
      .put("revision",revision+1).put("action",staged.intent.name)
      .put("minutes",staged.minutes).put("streak",staged.streak)
      .put("stop",staged.toStop).put("combatActive",combat?.optBoolean("active") == true))
    val manifest=CompanionWaitCapture.canonical(JSONObject()
      .put("version",VERSION).put("slot",view.slotId).put("turn",view.turn.turnId)
      .put("request",requestId).put("expectedRevision",revision)
      .put("committedRevision",revision+1)
      .put("inputDigest",view.turn.inputDigest)
      .put("decisionDigest",view.turn.decision.digest)
      .put("reservationDigest",view.turn.reservation.digest)
      .put("beforeSnapshotDigest",CompanionDigests.sha256(before))
      .put("afterSnapshotDigest",CompanionDigests.sha256(after))
      .put("resultDigest",CompanionDigests.sha256(result))
      .put("coreVersion",CURRENT_SAVE_VERSION)
      .put("policy",view.policyVersion)
      .put("nativePolicyDigest",CompanionNativeGameplayRolls.POLICY_DIGEST)
      .put("coreCommandIds",JSONArray(listOf(staged.commandId)))
      .put("actionCheckpoint",JSONObject().put("session","actor-"+view.turn.turnId)
        .put("checkpoint","resolve").put("minutes",staged.minutes))
      .put("events",JSONArray().apply { events.forEach {
        put(JSONObject().put("id",it.id).put("ordinal",it.ordinal)
          .put("type",it.type).put("digest",it.digest)) } })
      .put("observations",JSONArray(listOf(JSONObject()
        .put("id",observation.observationId)
        .put("digest",ObservationPublisherDigest.of(observation)))))
      .put("memories",JSONArray(listOf(JSONObject()
        .put("id",memory.memoryId).put("actor","cao_minh")
        .put("observationId",observation.observationId))))
      .put("brains",JSONArray()))
    return Batch(view.slotId,view.turn.turnId,requestId,revision,
      view.turn.inputDigest,view.turn.decision.digest,view.turn.reservation.digest,
      before,after,manifest,result,events,observation,memory)
  }

  @JvmStatic @Throws(IOException::class)
  fun verifyForCommit(view: CompanionSlotStore.NativeView, requestId: String,revision: Long,
                      input: String, candidate: Batch): Batch {
    val actual=build(view,requestId,revision,input)
    require(candidate.slotId==actual.slotId && candidate.turnId==actual.turnId &&
      candidate.requestId==actual.requestId && candidate.expectedRevision==actual.expectedRevision &&
      candidate.inputDigest==actual.inputDigest && candidate.decisionDigest==actual.decisionDigest &&
      candidate.reservationDigest==actual.reservationDigest &&
      candidate.before==actual.before && candidate.after==actual.after &&
      candidate.manifest==actual.manifest && candidate.finalResult==actual.finalResult &&
      candidate.events==actual.events &&
      candidate.observation.publicPayloadJson==actual.observation.publicPayloadJson &&
      candidate.observation.observationId==actual.observation.observationId &&
      candidate.memory==actual.memory) { "native_action_batch_mismatch" }
    return actual
  }
}
