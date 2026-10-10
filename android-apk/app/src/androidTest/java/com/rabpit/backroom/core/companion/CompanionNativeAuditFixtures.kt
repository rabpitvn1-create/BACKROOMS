package com.rabpit.backroom.core.companion

import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.rabpit.backroom.core.GameState
import com.rabpit.backroom.core.GameStateCodec
import com.rabpit.backroom.core.progression.FeaturedJourneyRoutes
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.function.Consumer

/** Native WAIT storage measurements, never integrated companion qualification. */
object CompanionNativeAuditFixtures {
  private const val INPUT = "Tôi đứng chờ tại nơi này trong ba mươi phút"
  private const val POLICY = CompanionWaitAuthorizer.WAIT_POLICY
  private fun snapshot(): ByteArray {
    val stop = "level-0"
    val node = FeaturedJourneyRoutes.nodeIdAt(stop)!!
    val level = JSONObject().put("number", 0).put("stopKey", stop).put("nodeId", node)
    val flags = JSONObject().put("exploration", JSONObject().put("exitStreakNode", stop).put("exitStreak", 0))
    val state = GameState.initial().copy(world = mapOf("location" to "present-scene",
      "journeyStopKey" to stop, "worldNodeId" to node, "levelJson" to level.toString(), "flagsJson" to flags.toString()))
    return GameStateCodec.encode(GameStateCodec.decode(GameStateCodec.encode(state))).toByteArray(StandardCharsets.UTF_8)
  }
  private fun sha256(value: String): String = java.security.MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
  private fun sqliteVersion(file: File): String = android.database.sqlite.SQLiteDatabase.openDatabase(
    file.path,null,android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use { db ->
      db.rawQuery("SELECT sqlite_version()",null).use { cursor -> check(cursor.moveToFirst()); cursor.getString(0) }
    }
  private fun footprints(file: File) = JSONObject().put("db",file.length())
    .put("wal",File(file.path+"-wal").length()).put("shm",File(file.path+"-shm").length())
    .put("journal",File(file.path+"-journal").length())

  @JvmStatic fun audit(context: Context, candidateSha: String, trialId: String, turns: Int, status: Consumer<String>) {
    require(candidateSha.matches(Regex("[0-9a-f]{40}")))
    require(trialId.matches(Regex("[A-Za-z0-9_-]{1,80}")))
    require(turns == 300 || turns == 1000)
    val outDir = File(context.filesDir,"companion_audit")
    check(outDir.isDirectory || outDir.mkdirs())
    val output = File(outDir,"$trialId.json")
    check(!output.exists()) { "existing_trial_evidence" }
    val genesis = snapshot()
    val store = CompanionSlotStore.create(context,genesis,POLICY)
    val slot = store.slotId
    val file = store.fileForTest()
    var measuredSqliteVersion = ""
    val samples = JSONArray()
    val milestones = JSONArray()
    var draws = 0L
    var first17Manifest: String? = null
    val started = System.currentTimeMillis()
    val workload = JSONObject().put("version","native_wait_30m_bound_minus_one_v2")
      .put("input",INPUT).put("policy",POLICY).put("genesis_sha256",sha256(String(genesis,StandardCharsets.UTF_8)))
      .put("callback","original_native_capture_bound_minus_one")
      .put("budget_stages",JSONArray(listOf("context_decode","admit","lock","reserve","prepare","commit","receipt_retry")))
      .put("minutes_per_turn",30).put("warm_p95_excludes_first_turns",20)
    val workloadBytes = workload.toString()
    try {
      measuredSqliteVersion = sqliteVersion(file)
      for (index in 0 until turns) {
        val revision = index.toLong()
        val turn = index+1
        val requestId = "audit-$turn"
        val stages = JSONObject()
        fun <T> measured(name: String, operation: () -> T): T {
          val before = SystemClock.elapsedRealtimeNanos()
          val value = operation()
          stages.put(name,SystemClock.elapsedRealtimeNanos()-before)
          return value
        }
        val beforeChanges = store.totalChangesForTest()
        val turnStart = SystemClock.elapsedRealtimeNanos()
        // Native snapshot decode only; not provider context assembly or retrieval.
        val state = measured("context_decode") { GameStateCodec.decode(String(store.currentSnapshot(),StandardCharsets.UTF_8)) }
        val location = state.world.getValue("location")
        val request = CompanionPendingTurn.Request.fromPlayerInput(slot,requestId,revision,"cao_minh",INPUT)
        val admitted = measured("admit") { store.admit(request) }
        check(admitted.admission == CompanionPendingTurn.AdmissionKind.CREATED)
        measured("lock") { store.lockDecision(requestId,CompanionPendingTurn.DecisionLock("cao_minh",revision,POLICY,
          "companion_decision.v1|cao_minh|WAIT|$revision|30|${location.toByteArray(StandardCharsets.UTF_8).size}:$location")) }
        val drawsBefore = draws
        val reserved = measured("reserve") { CompanionWaitCapture.reserve(store,requestId,revision,INPUT) { draws++; it-1 } }
        val batch = measured("prepare") { CompanionWaitBatch.prepare(store,requestId,revision,INPUT) }
        val receipt = measured("commit") { store.commitWait(requestId,revision,INPUT,batch) }
        check(receipt.committedRevision == turn.toLong())
        val retry = measured("receipt_retry") { CompanionWaitTestDriver.submit(store,requestId,revision,INPUT) { error("audit receipt redraw") } }
        check(retry.manifest == receipt.manifest && retry.finalResult == receipt.finalResult)
        val turnElapsed = SystemClock.elapsedRealtimeNanos()-turnStart
        val afterChanges = store.totalChangesForTest()
        check(afterChanges >= beforeChanges)
        check(draws-drawsBefore == 5L) { "unexpected_native_draw_count" }
        if (turn == 17) first17Manifest = receipt.manifest
        samples.put(JSONObject().put("turn",turn).put("request_id",requestId).put("slot_id",slot)
          .put("stages_ns",stages).put("turn_elapsed_ns",turnElapsed)
          .put("logical_rows_changed",afterChanges-beforeChanges)
          .put("logical_rows_definition","primary_connection_SQLite_total_changes_delta")
          .put("sidecars_bytes",footprints(file)).put("rng_draws",draws-drawsBefore)
          .put("reservation_digest",reserved.reservation.digest)
          .put("receipt_manifest_sha256",sha256(receipt.manifest)))
        if (turn == 300 || turn == 500 || turn == 1000) {
          val beforeSnapshot = store.currentSnapshot()
          val old = store.events(17,2)
          check(old.size == 2 && old.all { it.revision == 17L } && old[0].type == "WAIT_COMPLETED")
          val beforeRetryDraws = draws
          val historical = CompanionWaitTestDriver.submit(store,"audit-17",16,INPUT) { error("audit historical redraw") }
          check(historical.committedRevision == 17L && historical.manifest == first17Manifest)
          check(store.currentSnapshot().contentEquals(beforeSnapshot) && draws == beforeRetryDraws)
          check(store.currentRevision() == turn.toLong())
          check(GameStateCodec.decode(String(beforeSnapshot,StandardCharsets.UTF_8)).time.elapsedSubjectiveMinutes == turn*30L)
          check(store.observations("cao_minh",10).isEmpty() && store.observations("luc_tram",10).isEmpty())
          check(store.memoryHistory("cao_minh").isEmpty() && store.memoryHistory("luc_tram").isEmpty())
          milestones.put(JSONObject().put("turn",turn).put("historical_revision",17)
            .put("old_event17","PASS").put("old_receipt17","PASS").put("owner_evidence","EMPTY_EVIDENCE_ONLY")
            .put("elapsed_subjective_minutes",turn*30L)
            .put("historical_event_types",JSONArray(old.map { it.type })).put("historical_receipt_revision",historical.committedRevision)
            .put("historical_manifest_sha256",sha256(historical.manifest))
            .put("retry_rng_draws",draws-beforeRetryDraws).put("current_snapshot_unchanged",true)
            .put("owner_observation_rows",0).put("other_actor_observation_rows",0)
            .put("owner_memory_rows",0).put("other_actor_memory_rows",0).put("isolation_scope","EMPTY_EVIDENCE_ONLY"))
        }
        if (turn%100 == 0) status.accept("COMPANION_AUDIT_PROGRESS trial=$trialId turns=$turn")
      }
      store.close()
      val reopenStart = SystemClock.elapsedRealtimeNanos()
      val reopenProof = JSONObject()
      CompanionSlotStore.open(context,slot,POLICY).use { loaded ->
        check(loaded.currentRevision() == turns.toLong())
        check(GameStateCodec.decode(String(loaded.currentSnapshot(),StandardCharsets.UTF_8)).time.elapsedSubjectiveMinutes == turns*30L)
        val historicalEvents = loaded.events(17,2)
        check(historicalEvents.size == 2 && historicalEvents.all { it.revision == 17L })
        val historical = CompanionWaitTestDriver.submit(loaded,"audit-17",16,INPUT) { error("audit reopen redraw") }
        check(historical.committedRevision == 17L && historical.manifest == first17Manifest)
        reopenProof.put("revision",loaded.currentRevision()).put("historical_receipt_revision",historical.committedRevision)
          .put("historical_manifest_sha256",sha256(historical.manifest)).put("retry_rng_draws",0)
          .put("elapsed_subjective_minutes",turns*30L)
      }
      val reopenMs = (SystemClock.elapsedRealtimeNanos()-reopenStart)/1_000_000.0
      val result = JSONObject().put("version","native_backend_audit.v2").put("source_sha",candidateSha)
        .put("trial_id",trialId).put("api",Build.VERSION.SDK_INT).put("scope","BACKEND_WAIT_ONLY")
        .put("slot_id",slot).put("started_at_unix_ms",started).put("actual_turns",turns)
        .put("workload",workload).put("workload_sha256",sha256(workloadBytes))
        .put("environment",JSONObject().put("device",Build.MODEL).put("android_build",Build.FINGERPRINT)
          .put("sqlite_version",measuredSqliteVersion).put("runtime","production_Context_slot_Core_WAIT_SQLite").put("process_id",android.os.Process.myPid()))
        .put("samples",samples).put("milestones",milestones).put("reopen_proof",reopenProof)
        .put("reopen_ms",reopenMs).put("reopen_verified",true)
        .put("physical_io","NOT_MEASURED").put("sql_statement_count","NOT_MEASURED")
        .put("cold_process","RUNNER_FORCE_STOP_REQUIRED").put("warm_p95_excludes_first_turns",20)
        .put("provider","NOT_IMPLEMENTED_IN_THIS_BACKEND_AUDIT")
        .put("positive_observation_or_private_promise","NOT_PROVEN").put("power_loss","NOT_TESTED")
      val temp = File(outDir,"$trialId.json.tmp")
      check(!temp.exists())
      FileOutputStream(temp).use { stream -> stream.write(result.toString().toByteArray(StandardCharsets.UTF_8)); stream.fd.sync() }
      check(temp.renameTo(output)) { "audit_evidence_publish_failed" }
      status.accept("COMPANION_AUDIT_FILE="+output.path)
    } finally { store.close() }
  }
}
