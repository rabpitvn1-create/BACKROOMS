package com.rabpit.backroom.core.companion

import android.content.Context
import com.rabpit.backroom.core.GameStateCodec
import java.nio.charset.StandardCharsets

/**
 * Real-device SQLite/native/Core regression, run by the existing Android
 * instrumentation suite. Stubbed provider and audit isolate native behavior;
 * they are NOT evidence of live remote model/audit correctness.
 */
internal object CompanionLiveWaitFixtures {
  fun verifiedNewGameAndWait(context: Context) {
    val input = "Tôi đề nghị anh quan sát kỹ căn phòng vàng trước khi đi tiếp."
    CompanionNewGameBootstrap.create(context).use { slot ->
      val nativeBefore = slot.currentSnapshot()
      val writer = CompanionNativeWaitInteraction.Model { """{"intent":"WAIT"}""" }
      val audit = CompanionNativeWaitInteraction.Auditor { """{"verdict":"PASS"}""" }
      val rng = CompanionNativeWaitInteraction.NativeRandom { limit ->
        require(limit > 0)
        0
      }
      val host = CompanionNativeWaitInteraction(context, slot, writer, audit, rng)
      val first = host.submit(input, "native-companion-01")
      require(first.revision == 1L) { "wait_live_revision" }
      require(first.receipt.contains("\"action\":\"WAIT\"")) { "wait_live_receipt" }
      val committed = GameStateCodec.decode(first.coreSnapshot)
      require(committed.turn.pending == null) { "wait_live_core_pending" }
      require(committed.turn.completedTurnIds.size == 1) { "wait_live_core_turn" }
      require(!slot.currentSnapshot().contentEquals(nativeBefore)) { "wait_live_no_native_commit" }
      require(slot.events(1, 32).isNotEmpty()) { "wait_live_event_not_durable" }
      val original = first.receipt
      val same = host.submit(input, "native-companion-01")
      require(same.receipt == original && same.revision == 1L) { "wait_live_duplicate_receipt" }
      require(slot.currentRevision() == 1L) { "wait_live_double_commit" }
      val mismatchedInput = "Tôi đề nghị anh đi sang trái ngay lập tức và không chờ nữa."
      try {
        host.submit(mismatchedInput, "native-companion-01")
        error("wait_live_alias_conflict_accepted")
      } catch (_: java.io.IOException) { }
      require(slot.currentRevision() == 1L) { "wait_live_alias_changed_state" }
      val public = CompanionAndroidBridge.open(context, slot.slotId)
      require(org.json.JSONObject(public).getInt("revision") == 1) { "wait_live_readback" }
    }
  }
}
