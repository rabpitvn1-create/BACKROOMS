package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.*
import com.rabpit.backroom.core.progression.FeaturedJourneyRoutes
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Collections

/** Native replay/staging only. This immutable batch is not a durable receipt. */
object CompanionWaitBatch {
  class Event internal constructor(val id: String, val ordinal: Int, val type: String,
    val payload: String, val record: String, val digest: String)
  class Batch internal constructor(val slotId: String, val turnId: String, val requestId: String,
    val expectedRevision: Long, val inputDigest: String, val decisionDigest: String,
    val reservationDigest: String, val beforeSnapshot: String, val afterSnapshot: String,
    val manifest: String, val finalResult: String, events: List<Event>) {
    val events: List<Event> = Collections.unmodifiableList(ArrayList(events))
  }
  @JvmStatic @Throws(IOException::class)
  fun prepare(store: CompanionSlotStore, requestId: String, revision: Long, input: String): Batch =
    store.inspectNative(requestId) { view -> build(view, requestId, revision, input) }

  internal fun build(view: CompanionSlotStore.NativeView, requestId: String, revision: Long, input: String): Batch {
    require(view.turn.phase == CompanionPendingTurn.Phase.RESERVED) { "batch_requires_reserved" }
    val bound = CompanionWaitSlotBinding.verifyWithin(view, requestId, revision, input)
    val before = String(view.snapshot(), StandardCharsets.UTF_8)
    val original = GameStateCodec.decode(before)
    val outcome = CompanionWaitCapture.replay(original, bound, view.turn.reservation.canonicalPayload)
    val after = outcome.afterSnapshot
    val state = GameStateCodec.decode(after)
    val route = outcome.tape.route
    val target = if (route.completed) FeaturedJourneyRoutes.next(bound.sourceStop)!! else null
    val sessionId = "wait-" + view.turn.turnId
    require(GameStateCodec.decode(after) == state) { "wait_stage_not_stable" }
    val events = arrayListOf<Event>()
    fun event(type: String, payload: JSONObject) {
      val ordinal = events.size
      val id = "event-" + hash("${view.slotId}|${view.turn.turnId}|$ordinal")
      val encoded = CompanionWaitCapture.canonical(payload)
      val record = CompanionWaitCapture.canonical(JSONObject().put("version", "companion_event.v1")
        .put("slot", view.slotId).put("turn", view.turn.turnId).put("revision", revision + 1)
        .put("id", id).put("ordinal", ordinal).put("type", type).put("payload", JSONObject(encoded)))
      events.add(Event(id, ordinal, type, encoded, record, hash(record)))
    }
    event("WAIT_COMPLETED", JSONObject().put("actor", "cao_minh").put("minutes", 30)
      .put("location", original.world.getValue("location")).put("elapsedMinutes", state.time.elapsedSubjectiveMinutes))
    event("EXIT_STREAK_RESOLVED", JSONObject().put("success", outcome.success).put("streak", outcome.nextStreak)
      .put("source", bound.sourceStop).put("target", route.targetStop).put("completed", route.completed))
    if (target != null) event("WORLD_TRANSITION", JSONObject().put("source", bound.sourceStop)
      .put("target", target.targetStopKey).put("node", target.targetNodeId))
    Combat93Runtime.toJson(state)?.takeIf { it.optBoolean("active") }?.let {
      event("COMBAT_STARTED", JSONObject().put("encounterId", it.getString("encounterId"))
        .put("entities", it.getJSONArray("entities")))
    }
    val result = CompanionWaitCapture.canonical(JSONObject().put("version", "companion_result.v1")
      .put("slot", view.slotId).put("turn", view.turn.turnId).put("revision", revision + 1)
      .put("action", "WAIT").put("minutes", 30).put("streak", outcome.nextStreak)
      .put("stop", route.targetStop).put("combatActive", Combat93Runtime.active(state)))
    val manifest = CompanionWaitCapture.canonical(JSONObject().put("version", "companion_manifest.v1")
      .put("slot", view.slotId).put("turn", view.turn.turnId).put("request", requestId)
      .put("expectedRevision", revision).put("committedRevision", revision + 1)
      .put("inputDigest", view.turn.inputDigest).put("decisionDigest", view.turn.decision.digest)
      .put("reservationDigest", view.turn.reservation.digest).put("beforeSnapshotDigest", hash(before))
      .put("afterSnapshotDigest", hash(after)).put("resultDigest", hash(result))
      .put("coreVersion", CURRENT_SAVE_VERSION).put("policy", view.policyVersion)
      .put("nativePolicyDigest", CompanionNativeGameplayRolls.POLICY_DIGEST)
      .put("coreCommandIds", JSONArray((state.turn.executedCommandIds - original.turn.executedCommandIds).toList()))
      .put("actionCheckpoint", JSONObject().put("session", sessionId).put("checkpoint", "resolve").put("minutes", 30))
      .put("events", JSONArray().apply { events.forEach { put(JSONObject().put("id", it.id)
        .put("ordinal", it.ordinal).put("type", it.type).put("digest", it.digest)) } })
      .put("observations", JSONArray()).put("memories", JSONArray()).put("brains", JSONArray()))
    return Batch(view.slotId, view.turn.turnId, requestId, revision, view.turn.inputDigest,
      view.turn.decision.digest, view.turn.reservation.digest, before, after, manifest, result, events)
  }
  // Rebuild on fresh native context; hashes/set membership alone cannot authorize a batch.
  @JvmStatic @JvmName("verifyForCommit")
  internal fun verify(view: CompanionSlotStore.NativeView, requestId: String, revision: Long,
                      input: String, batch: Batch): Batch {
    val native = build(view, requestId, revision, input)
    require(batch.slotId == native.slotId && batch.turnId == native.turnId && batch.requestId == native.requestId &&
      batch.expectedRevision == native.expectedRevision && batch.inputDigest == native.inputDigest &&
      batch.decisionDigest == native.decisionDigest && batch.reservationDigest == native.reservationDigest &&
      batch.beforeSnapshot == native.beforeSnapshot && batch.afterSnapshot == native.afterSnapshot &&
      batch.manifest == native.manifest && batch.finalResult == native.finalResult &&
      batch.events.map { listOf(it.id, it.ordinal, it.type, it.payload, it.record, it.digest) } ==
        native.events.map { listOf(it.id, it.ordinal, it.type, it.payload, it.record, it.digest) }) { "batch_native_mismatch" }
    return native
  }
  internal fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
}
