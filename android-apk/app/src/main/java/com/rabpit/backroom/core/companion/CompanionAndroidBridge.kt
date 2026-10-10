package com.rabpit.backroom.core.companion

import android.content.Context
import com.rabpit.backroom.core.GameStateCodec
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * The only Android->native companion entry. A WebView never supplies a Core
 * snapshot, actor capabilities, scene, model decision, or success receipt.
 *
 * Each request opens and verifies an existing SQLite slot; all work is
 * serialized by the host's gameplay executor. This is an internal preview
 * seam until non-WAIT atomic executors are qualified.
 */
object CompanionAndroidBridge {
  fun interface Writer { fun write(prompt: String): String }
  fun interface RandomDraw { fun nextInt(bound: Int): Int }

  @JvmStatic @Throws(IOException::class)
  fun createNewGame(context: Context): String {
    CompanionNewGameBootstrap.create(context).use { store ->
      return snapshotProjection(store, null)
    }
  }

  @JvmStatic @Throws(IOException::class)
  fun open(context: Context, slotId: String): String {
    CompanionNewGameBootstrap.open(context, slotId).use { store ->
      return snapshotProjection(store, null)
    }
  }

  @JvmStatic @Throws(IOException::class)
  fun submit(context: Context, slotId: String, exactInput: String, requestId: String,
             writer: Writer, auditor: Writer, nativeDraw: RandomDraw): String {
    CompanionNewGameBootstrap.open(context, slotId).use { store ->
      val host = CompanionNativeWaitInteraction(context, store,
        { privatePrompt -> writer.write(privatePrompt) },
        { privatePrompt -> auditor.write(privatePrompt) },
        { size -> nativeDraw.nextInt(size) })
      val committed = try { host.submit(exactInput, requestId) }
      catch (error: Exception) {
        // Rejecting an unexposed PREPARING request permanently retires that
        // alias. Tell the UI to allocate a NEW alias while keeping the draft.
        // A locked/reserved request must instead reuse the SAME alias on retry
        // to preserve its recorded decision and RNG tape.
        var freshAliasNeeded = false
        try {
          val pending = store.request(requestId)
          if (pending != null && pending.phase == CompanionPendingTurn.Phase.PREPARING) {
            val cancelled = store.cancel(requestId)
            freshAliasNeeded = cancelled.phase == CompanionPendingTurn.Phase.REJECTED
          }
        } catch (suppressed: Exception) { error.addSuppressed(suppressed) }
        if (freshAliasNeeded)
          throw IOException("companion_retry_new_alias: " +
            (error.message ?: "decision_unavailable"), error)
        throw if (error is IOException) error else IOException("companion_native_turn_failed", error)
      }
      val current = snapshotProjection(store, requestId)
      val projected = JSONObject(current)
      projected.put("committedRevision", committed.revision)
      projected.put("action", JSONObject(committed.receipt).getString("action"))
      projected.put("receiptDigest", CompanionDigests.sha256(committed.receipt))
      return projected.toString()
    }
  }

  private fun snapshotProjection(store: CompanionSlotStore, requestId: String?): String {
    val bytes = store.currentSnapshot()
    val state = GameStateCodec.decode(String(bytes, StandardCharsets.UTF_8))
    val revision = store.currentRevision()
    val stop = state.world["journeyStopKey"] ?: throw IOException("companion_stop_missing")
    val response = JSONObject()
      .put("slotId", store.slotId)
      .put("revision", revision)
      .put("turn", revision + 1)
      .put("actor", "Cao Minh")
      .put("location", state.world["location"]?.takeIf { it.isNotBlank() } ?: stop)
      .put("stop", stop)
      .put("elapsedMinutes", state.time.elapsedSubjectiveMinutes)
      .put("combatActive", com.rabpit.backroom.core.Combat93Runtime.active(state) ||
        com.rabpit.backroom.core.CombatRuntime.active(state) != null)
    val events = org.json.JSONArray()
    if (revision > 0) {
      // Replay only receipt-verified native public projections. A WebView
      // reload never converts localStorage logs into world or actor memories.
      for (event in store.events((revision - 255).coerceAtLeast(1), 256)) {
        val raw = JSONObject(event.record)
        val public = PublicEventProjection.project(event.type, raw.getJSONObject("payload"))
          ?: continue
        events.put(JSONObject().put("type", event.type).put("revision", event.revision)
          .put("payload", public))
      }
    }
    response.put("publicEvents", events)
    if (requestId != null) {
      val turn = store.request(requestId) ?: throw IOException("companion_receipt_missing")
      val receipt = turn.receipt ?: throw IOException("companion_receipt_not_committed")
      response.put("turnId", turn.turnId).put("committedRevision", receipt.committedRevision)
      response.put("action", JSONObject(receipt.finalResult).getString("action"))
    }
    // No private brain, canon refs, candidate text, provider context or native
    // slot records ever leave this projection.
    return response.toString()
  }
}
