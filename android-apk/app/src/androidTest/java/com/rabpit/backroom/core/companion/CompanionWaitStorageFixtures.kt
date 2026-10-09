package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.*
import com.rabpit.backroom.core.progression.FeaturedJourneyRoutes
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets

/** Production slot + codec + authorizer; no host database or fake reducer. */
object CompanionWaitStorageFixtures {
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
  private inline fun rejects(reason: String, operation: () -> Unit) {
    try { operation() } catch (error: IOException) {
      check(error.message == reason) { "expected $reason got ${error.message}" }; return
    }
    error("missing rejection $reason")
  }
  @JvmStatic fun nativeBinding(directory: File) {
    val bytes = snapshot()
    CompanionSlotStore.createIn(directory, bytes, POLICY).use { store ->
      val req = CompanionPendingTurn.Request.fromPlayerInput(store.slotId, "native", 0, "cao_minh", INPUT)
      store.admit(req)
      rejects("decision_phase_invalid") { CompanionWaitSlotBinding.verify(store, "native", 0, INPUT) }
      store.lockDecision("native", CompanionPendingTurn.DecisionLock("cao_minh", 0, POLICY,
        "companion_decision.v1|cao_minh|WAIT|0|30|13:present-scene"))
      val bound = CompanionWaitSlotBinding.verify(store, "native", 0, INPUT)
      check(bound.sourceStop == "level-0" && bound.previousStreak == 0)
      store.admit(CompanionPendingTurn.Request.fromPlayerInput(store.slotId, "alias", 0, "cao_minh", INPUT))
      check(CompanionWaitSlotBinding.verify(store, "alias", 0, INPUT).snapshotDigest == bound.snapshotDigest)
      rejects("input_mismatch") { CompanionWaitSlotBinding.verify(store, "native", 0, INPUT + "!") }
      rejects("revision_mismatch") { CompanionWaitSlotBinding.verify(store, "native", 1, INPUT) }
      rejects("request_unknown") { CompanionWaitSlotBinding.verify(store, "unknown", 0, INPUT) }
      // Returning a copied snapshot cannot change the following authoritative read.
      store.inspectNative("native") { view -> view.snapshot().fill(0); null }
      check(CompanionWaitSlotBinding.verify(store, "native", 0, INPUT).snapshotDigest == bound.snapshotDigest)
      store.faultForTest { point -> if (point == "before_commit") throw IllegalStateException("injected") }
      try { CompanionWaitSlotBinding.verify(store, "native", 0, INPUT); error("fault missing") }
      catch (_: IllegalStateException) { }
      store.faultForTest { }
      check(store.request("native").phase == CompanionPendingTurn.Phase.DECISION_LOCKED)
    }
  }
  @JvmStatic fun nativeReservationRetry(directory: File) {
    // Control seam fixture only. Full WAIT capture is qualified in its own slice.
    CompanionSlotStore.createIn(directory, snapshot(), POLICY).use { store ->
      store.admit(CompanionPendingTurn.Request.fromPlayerInput(store.slotId, "r", 0, "cao_minh", INPUT))
      store.lockDecision("r", CompanionPendingTurn.DecisionLock("cao_minh", 0, POLICY,
        "companion_decision.v1|cao_minh|WAIT|0|30|13:present-scene"))
      var draws = 0
      val verify = CompanionSlotStore.NativeValidator { view -> CompanionWaitSlotBinding.verifyWithin(view, "r", 0, INPUT) }
      val capture = CompanionSlotStore.NativeCapture { view ->
        draws++
        CompanionPendingTurn.Reservation("fixture", view.turn.decision.digest, POLICY, "control-seam-fixture")
      }
      store.faultForTest { point -> if (point == "before_commit") throw IllegalStateException("injected") }
      try { store.reserveVerified("r", verify, capture); error("fault missing") } catch (_: IllegalStateException) { }
      store.faultForTest { }
      check(store.request("r").phase == CompanionPendingTurn.Phase.DECISION_LOCKED)
      val saved = store.reserveVerified("r", verify, capture)
      check(draws == 2)
      check(store.reserveVerified("r", verify) { error("redraw") }.reservation.digest == saved.reservation.digest)
      rejects("input_mismatch") {
        store.reserveVerified("r", { view -> CompanionWaitSlotBinding.verifyWithin(view, "r", 0, INPUT + "!") }) { error("redraw") }
      }
      check(draws == 2)
    }
  }
  @JvmStatic fun fullNativeCapture(directory: File) {
    val store = CompanionSlotStore.createIn(directory, snapshot(), POLICY)
    val slot = store.slotId
    var draws = 0
    try {
      store.admit(CompanionPendingTurn.Request.fromPlayerInput(slot, "full", 0, "cao_minh", INPUT))
      store.lockDecision("full", CompanionPendingTurn.DecisionLock("cao_minh", 0, POLICY,
        "companion_decision.v1|cao_minh|WAIT|0|30|13:present-scene"))
      store.faultForTest { point -> if (point == "before_commit") throw IllegalStateException("injected") }
      try { CompanionWaitCapture.reserve(store, "full", 0, INPUT) { draws++; it - 1 }; error("fault missing") }
      catch (_: IllegalStateException) { }
      store.faultForTest { }
      check(draws == 5 && store.request("full").phase == CompanionPendingTurn.Phase.DECISION_LOCKED)
      val saved = CompanionWaitCapture.reserve(store, "full", 0, INPUT) { draws++; it - 1 }
      check(draws == 10)
      val replay = CompanionWaitCapture.readReserved(store, "full", 0, INPUT)
      check(replay.tape.draws.size == 5 && replay.encoded == saved.reservation.canonicalPayload)
      store.admit(CompanionPendingTurn.Request.fromPlayerInput(slot, "full-alias", 0, "cao_minh", INPUT))
      check(CompanionWaitCapture.reserve(store, "full-alias", 0, INPUT) { error("redraw") }
        .reservation.digest == saved.reservation.digest)
      rejects("input_mismatch") { CompanionWaitCapture.reserve(store, "full", 0, INPUT + "!") { error("redraw") } }
      store.close()
      CompanionSlotStore.openIn(directory, slot, POLICY).use { loaded ->
        check(CompanionWaitCapture.readReserved(loaded, "full-alias", 0, INPUT).encoded == replay.encoded)
        check(CompanionWaitCapture.reserve(loaded, "full", 0, INPUT) { error("redraw") }
          .reservation.digest == saved.reservation.digest)
      }
    } finally { store.close() }
  }
  @JvmStatic fun concurrentNativeCapture(directory: File) {
    val first = CompanionSlotStore.createIn(directory, snapshot(), POLICY)
    val second = CompanionSlotStore.openIn(directory, first.slotId, POLICY)
    val executor = java.util.concurrent.Executors.newFixedThreadPool(2)
    val start = java.util.concurrent.CountDownLatch(1)
    val draws = java.util.concurrent.atomic.AtomicInteger()
    try {
      first.admit(CompanionPendingTurn.Request.fromPlayerInput(first.slotId, "left", 0, "cao_minh", INPUT))
      first.admit(CompanionPendingTurn.Request.fromPlayerInput(first.slotId, "right", 0, "cao_minh", INPUT))
      first.lockDecision("left", CompanionPendingTurn.DecisionLock("cao_minh", 0, POLICY,
        "companion_decision.v1|cao_minh|WAIT|0|30|13:present-scene"))
      val left = executor.submit<CompanionPendingTurn> { start.await(); CompanionWaitCapture.reserve(first, "left", 0, INPUT) { draws.incrementAndGet(); 0 } }
      val right = executor.submit<CompanionPendingTurn> { start.await(); CompanionWaitCapture.reserve(second, "right", 0, INPUT) { draws.incrementAndGet(); 0 } }
      start.countDown()
      val a = left.get(30, java.util.concurrent.TimeUnit.SECONDS)
      val b = right.get(30, java.util.concurrent.TimeUnit.SECONDS)
      check(a.reservation.digest == b.reservation.digest && draws.get() == 6)
      val replay = CompanionWaitCapture.readReserved(first, "left", 0, INPUT)
      check(replay.encoded == a.reservation.canonicalPayload)
      check(replay.tape.draws.drop(6).all { it.purpose == CompanionRollTape.Purpose.COMBAT_INITIAL })
      check(replay.tape.draws.size > 6 && Combat93Runtime.active(GameStateCodec.decode(replay.afterSnapshot)))
      check(GameStateCodec.decode(replay.afterSnapshot).time.elapsedSubjectiveMinutes == 30L)
    } finally { executor.shutdownNow(); first.close(); second.close() }
  }

  @JvmStatic fun nativeBatchStaging(directory: File) {
    for (zero in listOf(false, true)) {
      CompanionSlotStore.createIn(directory, snapshot(), POLICY).use { store ->
        store.admit(CompanionPendingTurn.Request.fromPlayerInput(store.slotId, "batch", 0, "cao_minh", INPUT))
        store.lockDecision("batch", CompanionPendingTurn.DecisionLock("cao_minh", 0, POLICY,
          "companion_decision.v1|cao_minh|WAIT|0|30|13:present-scene"))
        val reserved = CompanionWaitCapture.reserve(store, "batch", 0, INPUT) { if (zero) 0 else it - 1 }
        val first = CompanionWaitBatch.prepare(store, "batch", 0, INPUT)
        val second = CompanionWaitBatch.prepare(store, "batch", 0, INPUT)
        check(first.afterSnapshot == second.afterSnapshot && first.manifest == second.manifest)
        val after = GameStateCodec.decode(first.afterSnapshot)
        check(after.time.elapsedSubjectiveMinutes == 30L && ActionRuntime.activeSession(after) == null)
        check(Combat93Runtime.active(after) == zero)
        check(store.genesis().contentEquals(snapshot()))
        check(store.request("batch").reservation.digest == reserved.reservation.digest)
        store.inspectNative("batch") { view -> CompanionWaitBatch.verify(view, "batch", 0, INPUT, first) }
      }
    }
  }


  private fun reserveBatch(store: CompanionSlotStore, id: String, revision: Long): CompanionWaitBatch.Batch {
    val state=GameStateCodec.decode(String(store.currentSnapshot(),StandardCharsets.UTF_8))
    val location=state.world.getValue("location")
    check(store.admit(CompanionPendingTurn.Request.fromPlayerInput(store.slotId,id,revision,"cao_minh",INPUT)).admission ==
      CompanionPendingTurn.AdmissionKind.CREATED)
    store.lockDecision(id,CompanionPendingTurn.DecisionLock("cao_minh",revision,POLICY,
      "companion_decision.v1|cao_minh|WAIT|$revision|30|${location.toByteArray(StandardCharsets.UTF_8).size}:$location"))
    CompanionWaitCapture.reserve(store,id,revision,INPUT) { it-1 }
    return CompanionWaitBatch.prepare(store,id,revision,INPUT)
  }
  @JvmStatic fun nativeAtomicCommit(directory: File) {
    val store=CompanionSlotStore.createIn(directory,snapshot(),POLICY)
    val id=store.slotId
    try {
      val batch=reserveBatch(store,"commit",0)
      store.admit(CompanionPendingTurn.Request.fromPlayerInput(id,"commit-alias",0,"cao_minh",INPUT))
      for(point in listOf("after_event_write","after_snapshot_write","after_turn_write","after_alias_write","after_receipt_write","before_commit")) {
        store.faultForTest { if(it==point) throw IllegalStateException("injected:$point") }
        try { store.commitWait("commit",0,INPUT,batch); error("fault missing:$point") } catch(e: IllegalStateException) {
          check(e.message=="injected:$point")
        }
        store.faultForTest { }
        check(store.currentRevision()==0L && store.currentSnapshot().contentEquals(snapshot()) && store.events(1,100).isEmpty())
        check(store.request("commit").phase==CompanionPendingTurn.Phase.RESERVED)
      }
      val forged=CompanionWaitBatch.Batch(batch.slotId,batch.turnId,batch.requestId,batch.expectedRevision,batch.inputDigest,
        batch.decisionDigest,batch.reservationDigest,batch.beforeSnapshot,batch.beforeSnapshot,batch.manifest,batch.finalResult,batch.events)
      try { store.commitWait("commit",0,INPUT,forged); error("forged commit accepted") } catch(_: IllegalArgumentException) { }
      check(store.currentRevision()==0L)
      val receipt=store.commitWait("commit",0,INPUT,batch)
      check(receipt.manifest==batch.manifest && receipt.finalResult==batch.finalResult && store.currentRevision()==1L)
      check(store.currentSnapshot().contentEquals(batch.afterSnapshot.toByteArray(StandardCharsets.UTF_8)))
      check(store.recover()==null && store.events(1,100).map { it.record }==batch.events.map { it.record })
      val second=reserveBatch(store,"second",1)
      store.commitWait("second",1,INPUT,second)
      check(store.currentRevision()==2L && GameStateCodec.decode(String(store.currentSnapshot(),StandardCharsets.UTF_8)).time.elapsedSubjectiveMinutes==60L)
      check(store.commitWait("commit-alias",0,INPUT,batch).finalResult==receipt.finalResult)
      check(store.committedReceipt(CompanionPendingTurn.Request.fromPlayerInput(id,"commit",0,"cao_minh",INPUT)).manifest==receipt.manifest)
      rejects("receipt_request_mismatch") {
        store.committedReceipt(CompanionPendingTurn.Request.fromPlayerInput(id,"commit",0,"cao_minh",INPUT+"!"))
      }
      check(store.admit(CompanionPendingTurn.Request.fromPlayerInput(id,"unknown-stale",0,"cao_minh",INPUT)).admission==
        CompanionPendingTurn.AdmissionKind.STALE_REVISION)
      check(store.admit(CompanionPendingTurn.Request.fromPlayerInput(id,"commit",0,"cao_minh",INPUT)).admission==
        CompanionPendingTurn.AdmissionKind.COMMITTED_REPLAY)
      store.close()
      CompanionSlotStore.openIn(directory,id,POLICY).use { loaded ->
        check(loaded.currentRevision()==2L && loaded.events(1,100).size==4)
        check(loaded.committedReceipt(CompanionPendingTurn.Request.fromPlayerInput(id,"commit-alias",0,"cao_minh",INPUT)).finalResult==receipt.finalResult)
      }
    } finally { store.close() }
  }
  @JvmStatic fun nativeAmbiguousCommit(directory: File) {
    CompanionSlotStore.createIn(directory,snapshot(),POLICY).use { store ->
      val batch=reserveBatch(store,"ambiguous",0)
      store.faultForTest { if(it=="after_durable_commit") throw IllegalStateException("callback failed after commit") }
      val receipt=store.commitWait("ambiguous",0,INPUT,batch)
      store.faultForTest { }
      check(receipt.finalResult==batch.finalResult && store.currentRevision()==1L)
      check(store.commitWait("ambiguous",0,INPUT,batch).manifest==receipt.manifest)
      check(store.events(1,100).size==batch.events.size)
    }
  }
  @JvmStatic fun concurrentNativeCommit(directory: File) {
    val a=CompanionSlotStore.createIn(directory,snapshot(),POLICY)
    val b=CompanionSlotStore.openIn(directory,a.slotId,POLICY)
    val pool=java.util.concurrent.Executors.newFixedThreadPool(2)
    val start=java.util.concurrent.CountDownLatch(1)
    try {
      val leftBatch=reserveBatch(a,"left-commit",0)
      a.admit(CompanionPendingTurn.Request.fromPlayerInput(a.slotId,"right-commit",0,"cao_minh",INPUT))
      val rightBatch=CompanionWaitBatch.prepare(b,"right-commit",0,INPUT)
      val left=pool.submit<CompanionPendingTurn.Receipt> { start.await(); a.commitWait("left-commit",0,INPUT,leftBatch) }
      val right=pool.submit<CompanionPendingTurn.Receipt> { start.await(); b.commitWait("right-commit",0,INPUT,rightBatch) }
      start.countDown()
      val one=left.get(30,java.util.concurrent.TimeUnit.SECONDS); val two=right.get(30,java.util.concurrent.TimeUnit.SECONDS)
      check(one.manifest==two.manifest && one.finalResult==two.finalResult && a.currentRevision()==1L)
      check(a.events(1,100).size==leftBatch.events.size && b.currentSnapshot().contentEquals(a.currentSnapshot()))
    } finally { pool.shutdownNow(); a.close(); b.close() }
  }
  @JvmStatic fun ledgerCorruptionPreserved(directory: File) {
    val store=CompanionSlotStore.createIn(directory,snapshot(),POLICY)
    val slot=store.slotId; val file=store.fileForTest()
    val batch=reserveBatch(store,"ledger",0); store.commitWait("ledger",0,INPUT,batch); store.close()
    android.database.sqlite.SQLiteDatabase.openDatabase(file.path,null,
      android.database.sqlite.SQLiteDatabase.OPEN_READWRITE or android.database.sqlite.SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING).use { db ->
      for(sql in listOf("UPDATE native_event SET type='forged'", "DELETE FROM native_event",
        "UPDATE request_alias SET request_id='forged'", "UPDATE turn_control SET record=X'00' WHERE phase='COMMITTED'")) {
        try { db.execSQL(sql); error("immutable mutation accepted") } catch(_: android.database.sqlite.SQLiteException) { }
      }
      // Bypass the trigger as a local-file adversary: load must still detect incompleteness.
      db.execSQL("DROP TRIGGER native_event_no_delete")
      db.execSQL("DELETE FROM native_event WHERE ordinal=1")
    }
    try { CompanionSlotStore.openIn(directory,slot,POLICY).close(); error("incomplete ledger loaded") } catch(_:IOException) { }
    check(file.isFile)
  }
  @JvmStatic fun armNativeCommitCrash(context: android.content.Context, directory: File, afterCommit: Boolean, armed: Runnable) {
    val store=CompanionSlotStore.createIn(directory,snapshot(),POLICY)
    val batch=reserveBatch(store,"kill-commit",0)
    check(context.getSharedPreferences("companion_storage_smoke",0).edit()
      .putString(if(afterCommit) "after_commit_slot" else "during_commit_slot",store.slotId).commit())
    val boundary=if(afterCommit) "after_durable_commit" else "after_receipt_write"
    store.faultForTest { if(it==boundary) {
      armed.run()
      android.os.Process.killProcess(android.os.Process.myPid())
    } }
    store.commitWait("kill-commit",0,INPUT,batch)
    error("process kill returned")
  }
  @JvmStatic fun recoverNativeCommitCrash(context: android.content.Context, directory: File, afterCommit: Boolean) {
    val slot=context.getSharedPreferences("companion_storage_smoke",0)
      .getString(if(afterCommit) "after_commit_slot" else "during_commit_slot",null)!!
    CompanionSlotStore.openIn(directory,slot,POLICY).use { store ->
      if(afterCommit) {
        check(store.currentRevision()==1L && store.recover()==null)
        check(store.request("kill-commit").phase==CompanionPendingTurn.Phase.COMMITTED)
      } else {
        check(store.currentRevision()==0L && store.request("kill-commit").phase==CompanionPendingTurn.Phase.RESERVED)
        check(store.events(1,100).isEmpty())
        store.commitWait("kill-commit",0,INPUT,CompanionWaitBatch.prepare(store,"kill-commit",0,INPUT))
      }
      val receipt=store.committedReceipt(CompanionPendingTurn.Request.fromPlayerInput(slot,"kill-commit",0,"cao_minh",INPUT))
      check(receipt.committedRevision==1L && store.currentRevision()==1L && store.events(1,100).size==2)
      check(GameStateCodec.decode(String(store.currentSnapshot(),StandardCharsets.UTF_8)).time.elapsedSubjectiveMinutes==30L)
    }
  }

  @JvmStatic fun testDriverCombatPublication(directory: File) {
    CompanionSlotStore.createIn(directory,snapshot(),POLICY).use { store ->
      var draws=0
      val receipt=CompanionWaitTestDriver.submit(store,"driver-combat",0,INPUT) { draws++; 0 }
      check(draws==6 && receipt.committedRevision==1L)
      val saved=store.currentSnapshot()
      check(Combat93Runtime.active(GameStateCodec.decode(String(saved,StandardCharsets.UTF_8))))
      check(store.events(1,100).map { it.type }==listOf("WAIT_COMPLETED","EXIT_STREAK_RESOLVED","COMBAT_STARTED"))
      check(CompanionWaitTestDriver.submit(store,"driver-combat",0,INPUT) { error("historical redraw") }.finalResult==receipt.finalResult)
      check(store.currentSnapshot().contentEquals(saved))
      val id=store.slotId; store.close()
      CompanionSlotStore.openIn(directory,id,POLICY).use { loaded ->
        check(loaded.currentSnapshot().contentEquals(saved))
        check(CompanionWaitTestDriver.submit(loaded,"driver-combat",0,INPUT) { error("reopen redraw") }.manifest==receipt.manifest)
      }
    }
  }
  @JvmStatic fun benchmark(context: android.content.Context, directory: File, turns: Int, status: java.util.function.Consumer<String>) {
    require(turns in setOf(1000,5000,10000))
    val store=CompanionSlotStore.createIn(directory,snapshot(),POLICY)
    val slot=store.slotId; val file=store.fileForTest()
    val latencies=arrayListOf<Long>(); var peakHeap=0L; var peakPss=0L; var draws=0L
    val started=android.os.SystemClock.elapsedRealtime()
    try {
      for(index in 0 until turns) {
        val start=android.os.SystemClock.elapsedRealtimeNanos()
        val receipt=CompanionWaitTestDriver.submit(store,"bench-"+(index+1),index.toLong(),INPUT) { draws++; it-1 }
        check(receipt.committedRevision==index+1L)
        latencies.add(android.os.SystemClock.elapsedRealtimeNanos()-start)
        if((index+1)%100==0) {
          val runtime=Runtime.getRuntime()
          peakHeap=maxOf(peakHeap,runtime.totalMemory()-runtime.freeMemory())
          peakPss=maxOf(peakPss,android.os.Debug.getPss())
          status.accept("COMPANION_BENCH_PROGRESS turns="+(index+1))
        }
        if(index+1 in setOf(1000,5000,10000)) {
          val count=index+1; val ordered=latencies.sorted()
          fun percentile(p: Double)=ordered[(kotlin.math.ceil(count*p).toInt()-1).coerceIn(0,count-1)]/1_000_000.0
          val before=store.currentSnapshot()
          check(store.currentRevision()==count.toLong())
          check(GameStateCodec.decode(String(before,StandardCharsets.UTF_8)).time.elapsedSubjectiveMinutes==count*30L)
          val old=store.events(17,2)
          check(old.size==2 && old.all { it.revision==17L } && old[0].type=="WAIT_COMPLETED")
          val oldReceipt=CompanionWaitTestDriver.submit(store,"bench-17",16,INPUT) { error("benchmark old receipt redraw") }
          check(oldReceipt.committedRevision==17L && store.currentSnapshot().contentEquals(before) && draws==count*5L)
          val result=JSONObject().put("version","companion_backend_benchmark.v1").put("turns",count)
            .put("api",android.os.Build.VERSION.SDK_INT).put("driver","native-WAIT-no-encounter")
            .put("backend","real-Core-real-Android-SQLite").put("rng","deterministic-test-callback")
            .put("elapsedMs",android.os.SystemClock.elapsedRealtime()-started).put("p50Ms",percentile(0.50))
            .put("p95Ms",percentile(0.95)).put("p99Ms",percentile(0.99)).put("snapshotBytes",before.size)
            .put("dbBytes",file.length()).put("walBytes",File(file.path+"-wal").length())
            .put("sampledPeakJavaHeapBytes",peakHeap).put("sampledPeakPssKiB",peakPss)
            .put("oldEvent17","PASS").put("oldReceipt17","PASS").put("physicalDevice",false)
            .put("providerLatency","NOT_MEASURED").put("powerLoss","NOT_TESTED")
          status.accept("COMPANION_BENCH_RESULT="+result.toString())
        }
      }
      store.close()
      val reloadStart=android.os.SystemClock.elapsedRealtime()
      CompanionSlotStore.openIn(directory,slot,POLICY).use { loaded ->
        check(loaded.currentRevision()==turns.toLong() && loaded.events(17,2).all { it.revision==17L })
        check(CompanionWaitTestDriver.submit(loaded,"bench-17",16,INPUT) { error("reload historical redraw") }.committedRevision==17L)
        status.accept("COMPANION_BENCH_RELOAD="+JSONObject().put("turns",turns)
          .put("elapsedMs",android.os.SystemClock.elapsedRealtime()-reloadStart).put("verifiedChain",true).toString())
      }
    } finally { store.close() }
  }
}
