package com.rabpit.backroom.core.companion;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.rabpit.backroom.core.companion.CompanionPendingTurn.AdmissionKind;
import com.rabpit.backroom.core.companion.CompanionPendingTurn.DecisionLock;
import com.rabpit.backroom.core.companion.CompanionPendingTurn.Phase;
import com.rabpit.backroom.core.companion.CompanionPendingTurn.Request;
import com.rabpit.backroom.core.companion.CompanionPendingTurn.Reservation;

/** Dependency-free Android instrumentation exercising the production SQLite implementation. */
public final class CompanionStorageInstrumentation extends Instrumentation {
  private Bundle arguments;
  private int passed;
  private File directory;
  private static final byte[] GENESIS = "native-approved-fixture-v1".getBytes(StandardCharsets.UTF_8);
  private interface Case { void run() throws Exception; }

  @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); this.arguments = arguments; start(); }
  @Override public void onStart() {
    try {
      directory = getTargetContext().getDir("companion_storage_smoke", Context.MODE_PRIVATE);
      String mode = arguments == null ? "suite" : arguments.getString("mode", "suite");
      if ("crash".equals(mode)) { armProcessCrash(); throw new AssertionError("process kill returned"); }
      if ("during_commit_crash".equals(mode) || "after_commit_crash".equals(mode)) {
        boolean after = "after_commit_crash".equals(mode);
        CompanionWaitStorageFixtures.armNativeCommitCrash(getTargetContext(), directory, after, () -> {
          Bundle status = new Bundle(); status.putString("stream", "COMPANION_ATOMIC_CRASH_ARMED " + (after ? "after_durable_commit" : "after_receipt_write") + "\n");
          sendStatus(0, status);
        });
        throw new AssertionError("atomic process kill returned");
      }
      if ("native_audit".equals(mode))
        run("native_backend_audit", () -> CompanionNativeAuditFixtures.audit(getTargetContext(),
          arguments.getString("candidate_sha", ""), arguments.getString("trial_id", ""),
          Integer.parseInt(arguments.getString("turns", "1000")), message -> {
            Bundle status = new Bundle(); status.putString("stream", message + "\n"); sendStatus(0, status);
          }));
      else if ("benchmark".equals(mode))
        run("native_backend_benchmark", () -> CompanionWaitStorageFixtures.benchmark(getTargetContext(), directory,
          Integer.parseInt(arguments.getString("turns","1000")), message -> { Bundle status=new Bundle(); status.putString("stream",message+"\n"); sendStatus(0,status); }));
      else if ("during_commit_recover".equals(mode) || "after_commit_recover".equals(mode))
        run("atomic_commit_process_recovery", () -> CompanionWaitStorageFixtures.recoverNativeCommitCrash(
          getTargetContext(), directory, "after_commit_recover".equals(mode)));
      else if ("recover".equals(mode)) run("process_kill_rollback_and_recovery", this::recoverProcessCrash);
      else if ("suite".equals(mode)) {
        run("muse_schema_and_publisher", () -> CompanionMuseStorageFixtures.schemaAndPublication(directory));
        run("verified_genesis_canon_and_brain", () -> CompanionMuseStorageFixtures.verifiedGenesis(getTargetContext()));
        run("test_driver_combat_publication", () -> CompanionWaitStorageFixtures.testDriverCombatPublication(directory));
        run("native_atomic_wait_commit", () -> CompanionWaitStorageFixtures.nativeAtomicCommit(directory));
        run("native_companion_live_wait", () -> CompanionLiveWaitFixtures.INSTANCE.verifiedNewGameAndWait(getTargetContext()));
        run("native_ambiguous_commit_readback", () -> CompanionWaitStorageFixtures.nativeAmbiguousCommit(directory));
        run("concurrent_native_wait_commit", () -> CompanionWaitStorageFixtures.concurrentNativeCommit(directory));
        run("immutable_ledger_corruption_preserved", () -> CompanionWaitStorageFixtures.ledgerCorruptionPreserved(directory));
        run("native_wait_batch_staging", () -> CompanionWaitStorageFixtures.nativeBatchStaging(directory));
        run("full_native_wait_capture", () -> CompanionWaitStorageFixtures.fullNativeCapture(directory));
        run("concurrent_native_wait_capture", () -> CompanionWaitStorageFixtures.concurrentNativeCapture(directory));
        run("native_wait_slot_binding", () -> CompanionWaitStorageFixtures.nativeBinding(directory));
        run("native_reservation_verified_retry", () -> CompanionWaitStorageFixtures.nativeReservationRetry(directory));
        run("fresh_slot_missing_load_isolation", this::freshIsolation);
        run("aliases_conflicts_busy_and_stale", this::admission);
        run("real_transaction_rollback_faults", this::rollback);
        run("reservation_failure_then_exact_replay", this::reservation);
        run("post_lock_cancel_and_reopen", this::pauseRecovery);
        run("concurrent_admission_single_pending", this::concurrency);
        run("foreign_keys_and_active_unique_index", this::constraints);
        run("version_policy_and_blob_corruption_preserved", this::invalidStorage);
        run("physical_corruption_preserved", this::physicalCorruption);
        run("delete_closes_handles_and_preserves_other_slot", this::deleteLifecycle);
        run("delete_excludes_open_and_closed_handle_reuse", this::deleteGuard);
      } else throw new IOException("unknown_test_mode");
      Bundle result = new Bundle();
      result.putString("stream", "\nCOMPANION_STORAGE_PASS api=" + android.os.Build.VERSION.SDK_INT + " cases=" + passed + "\nOK (" + passed + " tests)\n");
      finish(Activity.RESULT_OK, result);
    } catch (Throwable error) {
      Bundle result = new Bundle(); result.putString("stream", "\nCOMPANION_STORAGE_FAIL\n" + Log.getStackTraceString(error));
      finish(Activity.RESULT_CANCELED, result);
    }
  }
  private void run(String name, Case test) throws Exception {
    test.run(); passed++;
    Bundle status = new Bundle(); status.putString("stream", "PASS " + name + "\n"); sendStatus(0, status);
  }
  private static void check(boolean condition) { if (!condition) throw new AssertionError("storage assertion failed"); }
  private static void reject(Case operation) throws Exception {
    try { operation.run(); } catch (IOException | IllegalArgumentException | IllegalStateException | SQLiteException expected) { return; }
    throw new AssertionError("expected rejection");
  }
  private CompanionSlotStore fresh() throws IOException { return CompanionSlotStore.createIn(directory, GENESIS, "p1"); }
  private Request request(CompanionSlotStore s, String id, String input) { return Request.fromPlayerInput(s.slotId, id, 0, "cao_minh", input); }
  private CompanionPendingTurn begin(CompanionSlotStore s) throws IOException { return s.admit(request(s, "r", "Hãy kiểm tra hành lang.")).turn; }
  private CompanionPendingTurn lock(CompanionSlotStore s) throws IOException { begin(s); return s.lockDecision("r", new DecisionLock("cao_minh", 0, "p1", "MOVE:corridor")); }
  private CompanionPendingTurn reserve(CompanionSlotStore s) throws IOException {
    return s.reserve("r", decision -> new Reservation("roll", decision.digest, "p1", "exit:2:0"));
  }
  private SQLiteDatabase raw(File file) { return SQLiteDatabase.openDatabase(file.getPath(), null, SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING); }

  private void freshIsolation() throws Exception {
    CompanionSlotStore a = fresh(); CompanionSlotStore b = fresh();
    try {
      check(!a.slotId.equals(b.slotId) && Arrays.equals(GENESIS, a.genesis()));
      begin(a); check(b.recover() == null);
      check(b.admit(request(a, "r", "input")).admission == AdmissionKind.SLOT_MISMATCH);
      String id = a.slotId; a.close();
      try (CompanionSlotStore loaded = CompanionSlotStore.openIn(directory, id, "p1")) { check(loaded.recover() != null); }
      String missing = "00000000000000000000000000000000";
      reject(() -> CompanionSlotStore.openIn(directory, missing, "p1"));
      check(!new File(directory, "slot-" + missing + ".db").exists());
      reject(() -> CompanionSlotStore.openIn(directory, "../legacy", "p1"));
    } finally { a.close(); b.deleteSlot(); }
  }
  private void admission() throws Exception {
    try (CompanionSlotStore s = fresh()) {
      CompanionPendingTurn first = begin(s);
      check(s.admit(request(s, "alias", "Hãy kiểm tra hành lang.")).admission == AdmissionKind.ALIAS);
      check(s.admit(request(s, "r", "changed")).admission == AdmissionKind.REQUEST_CONFLICT);
      check(s.admit(request(s, "different", "changed")).admission == AdmissionKind.BUSY);
      check(s.admit(Request.fromPlayerInput(s.slotId, "future", 1, "cao_minh", "input")).admission == AdmissionKind.STALE_REVISION);
      check(s.request("alias").turnId.equals(first.turnId));
      s.cancel("r"); check(s.recover() == null && s.request("alias").phase == Phase.REJECTED);
      check(s.admit(request(s, "new", "new input")).admission == AdmissionKind.CREATED);
      check(s.admit(request(s, "r", "Hãy kiểm tra hành lang.")).admission == AdmissionKind.REJECTED_REPLAY);
    }
  }
  private void rollback() throws Exception {
    for (String point : new String[]{"after_turn_write", "after_alias_write", "before_commit"}) {
      try (CompanionSlotStore s = fresh()) {
        s.faultForTest(at -> { if (point.equals(at)) throw new IllegalStateException("injected"); });
        reject(() -> begin(s)); s.faultForTest(at -> {});
        check(s.recover() == null && s.request("r") == null);
        begin(s);
        s.faultForTest(at -> { if (point.equals(at)) throw new IllegalStateException("injected"); });
        reject(() -> s.admit(request(s, "alias", "Hãy kiểm tra hành lang.")));
        s.faultForTest(at -> {}); check(s.request("alias") == null && s.recover().requestAliases.size() == 1);
      }
    }
  }
  private void reservation() throws Exception {
    try (CompanionSlotStore s = fresh()) {
      lock(s); final int[] draws = {0};
      s.faultForTest(at -> { if ("before_commit".equals(at)) throw new IllegalStateException("injected"); });
      reject(() -> s.reserve("r", decision -> { draws[0]++; return new Reservation("roll", decision.digest, "p1", "exit:2:0"); }));
      s.faultForTest(at -> {}); check(s.recover().phase == Phase.DECISION_LOCKED);
      CompanionPendingTurn stored = s.reserve("r", decision -> { draws[0]++; return new Reservation("roll", decision.digest, "p1", "exit:2:0"); });
      check(s.reserve("r", decision -> { throw new AssertionError("redraw"); }).reservation.digest.equals(stored.reservation.digest));
      check(draws[0] == 2); // First work rolled back unexposed; persisted reservation is generated once.
    }
  }
  private void pauseRecovery() throws Exception {
    CompanionSlotStore s = fresh(); String id = s.slotId;
    lock(s); CompanionPendingTurn stored = reserve(s); s.cancel("r"); s.close();
    try (CompanionSlotStore loaded = CompanionSlotStore.openIn(directory, id, "p1")) {
      check(loaded.recover().phase == Phase.SUSPENDED);
      reject(() -> reserve(loaded));
      loaded.resume("r");
      check(loaded.reserve("r", decision -> { throw new AssertionError("redraw"); }).reservation.digest.equals(stored.reservation.digest));
      reject(() -> loaded.lockDecision("r", new DecisionLock("cao_minh", 0, "p1", "SEARCH:box")));
    }
  }
  private void concurrency() throws Exception {
    for (boolean identical : new boolean[]{false, true}) {
      CompanionSlotStore a = fresh(); CompanionSlotStore b = CompanionSlotStore.openIn(directory, a.slotId, "p1");
      ExecutorService pool = Executors.newFixedThreadPool(2); CountDownLatch start = new CountDownLatch(1);
      try {
        Future<CompanionSlotStore.Result> left = pool.submit(() -> { start.await(); return a.admit(request(a, "left", "input A")); });
        Future<CompanionSlotStore.Result> right = pool.submit(() -> { start.await(); return b.admit(request(b, "right", identical ? "input A" : "input B")); });
        start.countDown(); CompanionSlotStore.Result l = left.get(30, TimeUnit.SECONDS); CompanionSlotStore.Result r = right.get(30, TimeUnit.SECONDS);
        check((l.admission == AdmissionKind.CREATED) != (r.admission == AdmissionKind.CREATED));
        check(l.admission == AdmissionKind.CREATED ? r.admission == (identical ? AdmissionKind.ALIAS : AdmissionKind.BUSY) : l.admission == (identical ? AdmissionKind.ALIAS : AdmissionKind.BUSY));
        check(a.recover().turnId.equals(b.recover().turnId));
      } finally { pool.shutdownNow(); a.close(); b.close(); }
    }
  }
  private void constraints() throws Exception {
    CompanionSlotStore s = fresh();
    try {
      begin(s);
      try (SQLiteDatabase db = raw(s.fileForTest())) {
        db.setForeignKeyConstraintsEnabled(true);
        reject(() -> db.execSQL("INSERT INTO request_alias VALUES ('forged','absent')"));
        reject(() -> db.execSQL("INSERT INTO turn_control(turn_id,active_slot,expected_revision,phase,record) VALUES ('forged',1,0,'PREPARING',X'00')"));
        reject(() -> db.execSQL("UPDATE turn_control SET phase='COMMITTED'"));
      }
      String id = s.slotId; s.close();
      try (CompanionSlotStore loaded = CompanionSlotStore.openIn(directory, id, "p1")) { check(loaded.recover() != null); }
    } finally { s.close(); }
  }
  private void invalidStorage() throws Exception {
    CompanionSlotStore s = fresh(); String id = s.slotId; File file = s.fileForTest(); s.close();
    reject(() -> CompanionSlotStore.openIn(directory, id, "wrong-policy")); check(file.isFile());
    try (SQLiteDatabase db = raw(file)) { db.setVersion(99); }
    reject(() -> CompanionSlotStore.openIn(directory, id, "p1")); check(file.isFile());
    try (SQLiteDatabase db = raw(file)) { db.setVersion(1); }
    reject(() -> CompanionSlotStore.openIn(directory, id, "p1")); check(file.isFile());
    try (SQLiteDatabase db = raw(file)) { db.setVersion(CompanionSlotStore.FORMAT_VERSION); }
    s = CompanionSlotStore.openIn(directory, id, "p1"); begin(s); s.close();
    try (SQLiteDatabase db = raw(file)) { db.execSQL("UPDATE turn_control SET record=X'00'"); }
    reject(() -> CompanionSlotStore.openIn(directory, id, "p1")); check(file.isFile());
  }
  private void physicalCorruption() throws Exception {
    CompanionSlotStore s = fresh(); File file = s.fileForTest(); String id = s.slotId; s.close();
    try (FileOutputStream out = new FileOutputStream(file)) { out.write("not a database".getBytes(StandardCharsets.UTF_8)); }
    reject(() -> CompanionSlotStore.openIn(directory, id, "p1")); check(file.isFile() && file.length() == 14);
  }
  private void deleteLifecycle() throws Exception {
    CompanionSlotStore a = fresh(); CompanionSlotStore b = CompanionSlotStore.openIn(directory, a.slotId, "p1");
    CompanionSlotStore other = fresh(); File file = a.fileForTest(); String id = a.slotId;
    try {
      begin(a); a.deleteSlot(); check(!file.exists()); reject(b::recover);
      reject(() -> CompanionSlotStore.openIn(directory, id, "p1"));
      check(other.recover() == null && Arrays.equals(GENESIS, other.genesis()));
      check(new File(file.getPath() + ".lease").exists());
    } finally { a.close(); b.close(); other.deleteSlot(); }
  }
  private void armProcessCrash() throws Exception {
    CompanionSlotStore s = fresh(); lock(s);
    check(getTargetContext().getSharedPreferences("companion_storage_smoke", 0).edit().putString("crash_slot", s.slotId).commit());
    s.faultForTest(point -> {
      if ("after_alias_write".equals(point)) {
        Bundle status = new Bundle(); status.putString("stream", "COMPANION_CRASH_ARMED\n"); sendStatus(0, status);
        android.os.Process.killProcess(android.os.Process.myPid());
        throw new AssertionError("process kill returned");
      }
    });
    reserve(s);
  }
  private void deleteGuard() throws Exception {
    CompanionSlotStore a = fresh(); String id = a.slotId; File file = a.fileForTest();
    CompanionSlotStore b = CompanionSlotStore.openIn(directory, id, "p1");
    try {
      a.faultForTest(point -> {
        if ("after_delete_guard".equals(point)) {
          try { CompanionSlotStore.openIn(directory, id, "p1"); throw new AssertionError("open during deletion"); }
          catch (IOException expected) { /* lease excludes the new opener */ }
          a.close(); // Close/delete interleaving cannot release the guarded lease.
        }
      });
      a.deleteSlot(); check(!file.exists()); reject(b::recover);
      reject(a::deleteSlot);
    } finally { a.close(); b.close(); }
  }
  private void recoverProcessCrash() throws Exception {
    String id = getTargetContext().getSharedPreferences("companion_storage_smoke", 0).getString("crash_slot", null);
    try (CompanionSlotStore s = CompanionSlotStore.openIn(directory, id, "p1")) {
      check(s.recover().phase == Phase.DECISION_LOCKED && s.recover().reservation == null);
      CompanionPendingTurn turn = reserve(s); check(turn.phase == Phase.RESERVED);
      s.cancel("r"); s.resume("r");
      check(s.reserve("r", decision -> { throw new AssertionError("redraw"); }).reservation.digest.equals(turn.reservation.digest));
      check(Arrays.equals(GENESIS, s.genesis()));
    }
  }
}
