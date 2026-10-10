package com.rabpit.backroom.core.companion

import android.content.Context
import com.rabpit.backroom.core.GameStateCodec
import com.rabpit.backroom.core.progression.FeaturedJourneyRoutes
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

/** Real Android SQLite/Core end-to-end: SEARCH, MOVE, replay and memory. */
internal object CompanionOrdinaryActionInstrumentation {
  private const val INPUT = "Tôi gợi ý anh quan sát và tự chọn bước tiếp theo phù hợp."

  fun nativeActionsAndActorMemories(context: Context) {
    var slotId = ""
    CompanionNewGameBootstrap.create(context).use { store ->
      slotId = store.slotId
      val apiCalls=AtomicInteger(0)
      val drawCalls=AtomicInteger(0)
      val provider=CompanionNativeWaitInteraction.Model {
        apiCalls.incrementAndGet()
        """{"intent":"SEARCH","targetId":null,"itemId":null}"""
      }
      val audit=CompanionNativeWaitInteraction.Auditor { """{"verdict":"PASS"}""" }
      val random=CompanionNativeWaitInteraction.NativeRandom { bound ->
        drawCalls.incrementAndGet()
        bound-1
      }
      val host=CompanionNativeWaitInteraction(context,store,provider,audit,random)
      val before=GameStateCodec.decode(String(store.currentSnapshot(),StandardCharsets.UTF_8))
      val result=host.submit(INPUT,"native-search-1")
      require(result.revision==1L) { "search_revision" }
      require(JSONObject(result.receipt).getString("action")=="SEARCH") { "search_receipt_kind" }
      val after=GameStateCodec.decode(result.coreSnapshot)
      require(after.time.elapsedSubjectiveMinutes==before.time.elapsedSubjectiveMinutes+5L) { "search_time" }
      require(after.turn.pending==null && after.turn.completedTurnIds.size==1) { "search_core_incomplete" }
      require(after.metadata["lastAction.kind"]=="SEARCH") { "search_core_kind" }
      require(store.observations("cao_minh",10).size==1) { "search_observation_not_saved" }
      require(store.memoryHistory("cao_minh").size==1) { "search_memory_not_saved" }
      require(store.events(1,10).any { it.type=="ACTOR_ACTION_COMPLETED" }) { "search_event_missing" }
      val draws=drawCalls.get()
      val calls=apiCalls.get()
      val repeated=host.submit(INPUT,"native-search-1")
      require(repeated.receipt==result.receipt && repeated.revision==1L) { "search_receipt_replay" }
      require(drawCalls.get()==draws && apiCalls.get()==calls) { "search_retry_generated_decision_or_rolls" }
      require(store.currentRevision()==1L) { "search_double_commit" }

      val stop=after.world["journeyStopKey"] ?: error("search_stop_missing")
      val next=FeaturedJourneyRoutes.next(stop) ?: error("search_route_missing")
      val movement=CompanionNativeWaitInteraction(context,store,
        {JSONObject().put("intent","MOVE").put("targetId",next.targetStopKey)
          .put("itemId",JSONObject.NULL).toString()},
        audit,random)
      val moved=movement.submit(INPUT,"native-move-2")
      require(moved.revision==2L && JSONObject(moved.receipt).getString("action")=="MOVE") {
        "move_receipt"
      }
      val state=GameStateCodec.decode(moved.coreSnapshot)
      require(state.time.elapsedSubjectiveMinutes==after.time.elapsedSubjectiveMinutes+10L) { "move_time" }
      require(state.turn.completedTurnIds.size==2) { "move_turn" }
      require(store.memoryHistory("cao_minh").size==2) { "move_memory_not_saved" }
      val memoryIds=store.memoryHistory("cao_minh").map { it.memoryId }
      require(memoryIds.distinct().size==2) { "memory_identity_collision" }
      require(store.events(1,16).count { it.type=="ACTOR_ACTION_COMPLETED" }==2) {
        "native_actions_not_in_ledger"
      }
    }
    // Reopen the real SQLite database, validate head and owner visibility.
    val reopened=CompanionAndroidBridge.open(context,slotId)
    val public=JSONObject(reopened)
    require(public.getLong("revision")==2L) { "native_action_reopen_revision" }
    val publicEvents=public.getJSONArray("publicEvents")
    require((0 until publicEvents.length()).count {
      publicEvents.getJSONObject(it).getString("type")=="ACTOR_ACTION_COMPLETED"
    }==2) { "native_action_public_projection" }
  }
}
