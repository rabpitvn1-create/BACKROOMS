package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.*
import org.json.JSONObject
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Completeness/provenance checks, not authentication against local file edits. */
object CompanionLedgerVerifier {
  private fun integer(obj: JSONObject, key: String, value: Long) {
    val actual = obj.get(key)
    require(actual is Number && actual.toString() == value.toString())
  }
  private inline fun checked(block: () -> Unit) {
    try { block() } catch (e: Exception) { throw IOException("ledger_manifest_invalid", e) }
  }
  @JvmStatic @Throws(IOException::class)
  fun verifyEvent(slot: String, current: Long, event: CompanionSlotStore.LedgerEvent) = checked {
    val row = JSONObject(event.record)
    require(event.revision in 1..current && event.ordinal >= 0)
    integer(row,"revision",event.revision); integer(row,"ordinal",event.ordinal.toLong())
    require(event.digest == CompanionWaitBatch.hash(event.record))
    require(event.record == CompanionWaitCapture.canonical(row))
    require(row.getString("version") == "companion_event.v1" && row.getString("slot") == slot &&
      row.getString("turn") == event.turnId && row.getLong("revision") == event.revision &&
      row.getString("id") == event.id && row.getInt("ordinal") == event.ordinal &&
      row.getString("type") == event.type && row.getJSONObject("payload").length() > 0)
    require(event.id == "event-" + CompanionWaitBatch.hash("$slot|${event.turnId}|${event.ordinal}"))
    require(row.keys().asSequence().toSet() == setOf("version","slot","turn","revision","id","ordinal","type","payload"))
  }
  @JvmStatic @Throws(IOException::class)
  fun verifyReceipt(slot: String, policy: String, current: Long, turn: CompanionPendingTurn,
                    events: List<CompanionSlotStore.LedgerEvent>) = checked {
    require(turn.phase == CompanionPendingTurn.Phase.COMMITTED && turn.slotId == slot)
    require(turn.receipt.committedRevision == turn.expectedRevision + 1 && turn.receipt.committedRevision <= current)
    val m = JSONObject(turn.receipt.manifest)
    require(CompanionWaitCapture.canonical(m) == turn.receipt.manifest)
    integer(m,"expectedRevision",turn.expectedRevision); integer(m,"committedRevision",turn.receipt.committedRevision)
    integer(m,"coreVersion",CURRENT_SAVE_VERSION.toLong())
    require(m.keys().asSequence().toSet() == setOf("version","slot","turn","request","expectedRevision","committedRevision",
      "inputDigest","decisionDigest","reservationDigest","beforeSnapshotDigest","afterSnapshotDigest",
      "resultDigest","coreVersion","policy","nativePolicyDigest","coreCommandIds","actionCheckpoint",
      "events","observations","memories","brains"))
    require(m.getString("version") == "companion_manifest.v1" && m.getString("slot") == slot &&
      m.getString("turn") == turn.turnId && m.getString("request") in turn.requestAliases &&
      m.getLong("expectedRevision") == turn.expectedRevision && m.getLong("committedRevision") == turn.receipt.committedRevision &&
      m.getString("inputDigest") == turn.inputDigest && m.getString("decisionDigest") == turn.decision.digest &&
      m.getString("reservationDigest") == turn.reservation.digest && m.getString("policy") == policy &&
      turn.decision.policyVersion == policy && turn.reservation.policyVersion == policy &&
      m.getInt("coreVersion") == CURRENT_SAVE_VERSION &&
      m.getString("nativePolicyDigest") == CompanionNativeGameplayRolls.POLICY_DIGEST)
    for(key in listOf("beforeSnapshotDigest","afterSnapshotDigest","resultDigest"))
      require(m.getString(key).matches(Regex("[0-9a-f]{64}")))
    require(m.getString("resultDigest") == CompanionWaitBatch.hash(turn.receipt.finalResult))
    val result = JSONObject(turn.receipt.finalResult)
    integer(result,"revision",turn.receipt.committedRevision); integer(result,"minutes",30)
    require(CompanionWaitCapture.canonical(result) == turn.receipt.finalResult &&
      result.getString("version") == "companion_result.v1" && result.getString("slot") == slot &&
      result.getString("turn") == turn.turnId && result.getLong("revision") == turn.receipt.committedRevision &&
      result.getString("action") == "WAIT" && result.getInt("minutes") == 30)
    val rows = m.getJSONArray("events")
    require(events.size == rows.length() && events.size in 2..4)
    events.forEachIndexed { index, event ->
      verifyEvent(slot, current, event)
      require(event.turnId == turn.turnId && event.revision == turn.receipt.committedRevision && event.ordinal == index)
      val row = rows.getJSONObject(index)
      integer(row,"ordinal",index.toLong())
      require(row.keys().asSequence().toSet() == setOf("id","ordinal","type","digest") && row.getString("id") == event.id &&
        row.getInt("ordinal") == index && row.getString("type") == event.type && row.getString("digest") == event.digest)
    }
    require(events[0].type == "WAIT_COMPLETED" && events[1].type == "EXIT_STREAK_RESOLVED")
    require(events.map { it.id }.distinct().size == events.size && events.map { it.type }.distinct().size == events.size)
    require(events.drop(2).all { it.type in setOf("WORLD_TRANSITION","COMBAT_STARTED") })
    for(key in listOf("observations","memories","brains")) require(m.getJSONArray(key).length() == 0)
    val commands = m.getJSONArray("coreCommandIds")
    require(commands.length() == 1 && commands.getString(0) == turn.turnId + ":NATIVE:WAIT")
    val checkpoint = m.getJSONObject("actionCheckpoint")
    integer(checkpoint,"minutes",30)
    require(checkpoint.keys().asSequence().toSet() == setOf("session","checkpoint","minutes") &&
      checkpoint.getString("session") == "wait-" + turn.turnId && checkpoint.getString("checkpoint") == "resolve" &&
      checkpoint.getInt("minutes") == 30)
  }
  @JvmStatic @Throws(IOException::class)
  fun verifyHead(snapshot: ByteArray, turn: CompanionPendingTurn) = checked {
    val decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
    val text = decoder.decode(ByteBuffer.wrap(snapshot)).toString()
    val state = GameStateCodec.decode(text)
    require(GameStateCodec.encode(state) == text && state.turn.pending == null && ActionRuntime.activeSession(state) == null)
    require(JSONObject(turn.receipt.manifest).getString("afterSnapshotDigest") == CompanionWaitBatch.hash(text))
    require(turn.turnId in state.turn.completedTurnIds && turn.turnId + ":NATIVE:WAIT" in state.turn.executedCommandIds)
  }
  @JvmStatic @Throws(IOException::class)
  fun verifyChainLink(revision: Long, before: String, turn: CompanionPendingTurn): String {
    var after = ""
    checked {
      val m = JSONObject(turn.receipt.manifest)
      require(turn.receipt.committedRevision == revision && m.getString("beforeSnapshotDigest") == before)
      after = m.getString("afterSnapshotDigest")
    }
    return after
  }
}
