package com.rabpit.backroom.core.companion;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabaseCorruptException;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.List;
import java.nio.charset.StandardCharsets;

import com.rabpit.backroom.core.companion.CompanionPendingTurn.Admission;
import com.rabpit.backroom.core.companion.CompanionPendingTurn.AdmissionKind;
import com.rabpit.backroom.core.companion.CompanionPendingTurn.DecisionLock;
import com.rabpit.backroom.core.companion.CompanionPendingTurn.Phase;
import com.rabpit.backroom.core.companion.CompanionPendingTurn.Request;
import com.rabpit.backroom.core.companion.CompanionPendingTurn.Reservation;

/**
 * Fresh-slot native storage with atomic verified WAIT publication. Not connected to the game bridge.
 * Caller supplies a natively approved genesis snapshot, never model/UI state.
 */
public final class CompanionSlotStore implements Closeable {
  static final int FORMAT_VERSION = 3;
  static final int APPLICATION_ID = 0x43505331;
  private static final Object LEASE_LOCK = new Object();
  private static final Map<String, Lease> LEASES = new HashMap<>();
  private final File file;
  private final Lease lease;
  private final SQLiteDatabase database;
  public final String slotId;
  private final String policyVersion;
  private boolean closed;
  private Fault fault = point -> {};

  interface Fault { void at(String point); }
  private interface Work<T> { T run() throws IOException; }
  interface NativeWork<T> { T apply(NativeView view) throws IOException; }
  interface NativeValidator { void verify(NativeView view) throws IOException; }
  interface NativeCapture { Reservation capture(NativeView view) throws IOException; }
  /** Created only while the primary writer owns the validated slot transaction. */
  static final class NativeView {
    final String slotId, policyVersion;
    final long revision;
    final CompanionPendingTurn turn;
    private final byte[] snapshot;
    NativeView(String slotId, String policyVersion, long revision, CompanionPendingTurn turn, byte[] snapshot) {
      this.slotId = slotId; this.policyVersion = policyVersion; this.revision = revision;
      this.turn = turn; this.snapshot = snapshot.clone();
    }
    byte[] snapshot() { return snapshot.clone(); }
  }
  public interface ReservationFactory { Reservation capture(DecisionLock decision) throws IOException; }

  public static final class Result {
    public final AdmissionKind admission;
    public final CompanionPendingTurn turn;
    private Result(AdmissionKind admission, CompanionPendingTurn turn) {
      this.admission = admission; this.turn = turn;
    }
  }

  private CompanionSlotStore(File file, Lease lease, SQLiteDatabase database, String slotId, String policyVersion) {
    this.file = file; this.lease = lease; this.database = database;
    this.slotId = slotId; this.policyVersion = policyVersion;
    synchronized (LEASE_LOCK) { lease.stores.add(this); }
  }

  public static CompanionSlotStore create(Context context, byte[] approvedGenesis, String policyVersion) throws IOException {
    return createIn(context.getDir("companion_slots_v3", Context.MODE_PRIVATE), approvedGenesis, policyVersion);
  }
  public static CompanionSlotStore open(Context context, String slotId, String policyVersion) throws IOException {
    return openIn(context.getDir("companion_slots_v3", Context.MODE_PRIVATE), slotId, policyVersion);
  }

  static CompanionSlotStore createIn(File directory, byte[] genesis, String policy) throws IOException {
    if (genesis == null || genesis.length == 0 || genesis.length > 1048576) throw new IOException("genesis_size_invalid");
    if (policy == null || !policy.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) throw new IOException("policy_invalid");
    byte[] snapshot = genesis.clone();
    if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("companion_directory_unavailable");
    String id = UUID.randomUUID().toString().replace("-", "");
    File file = slotFile(directory, id);
    File leaseFile = new File(file.getPath() + ".lease");
    if (!leaseFile.createNewFile()) throw new IOException("slot_identity_collision");
    Lease lease = acquire(file);
    SQLiteDatabase db = null;
    CompanionSlotStore store = null;
    try {
      if (!file.createNewFile()) throw new IOException("slot_identity_collision");
      db = openDatabase(file);
      db.beginTransaction();
      try {
        verifyConfiguration(db);
        db.execSQL("CREATE TABLE slot_meta(singleton INTEGER PRIMARY KEY CHECK(singleton=1),slot_id TEXT NOT NULL UNIQUE,format_version INTEGER NOT NULL CHECK(format_version=3),revision INTEGER NOT NULL CHECK(revision>=0),policy_version TEXT NOT NULL,genesis BLOB NOT NULL,genesis_digest TEXT NOT NULL,snapshot BLOB NOT NULL CHECK(length(snapshot)<=16777216),snapshot_digest TEXT NOT NULL)");
        db.execSQL("CREATE TABLE turn_control(turn_id TEXT PRIMARY KEY,active_slot INTEGER NOT NULL CHECK(active_slot=1),expected_revision INTEGER NOT NULL CHECK(expected_revision>=0),phase TEXT NOT NULL CHECK(phase IN ('PREPARING','DECISION_LOCKED','RESERVED','SUSPENDED','REJECTED','COMMITTED')),record BLOB NOT NULL CHECK(length(record)<=1048576),committed_revision INTEGER UNIQUE,UNIQUE(turn_id,committed_revision),CHECK((phase='COMMITTED' AND committed_revision IS NOT NULL AND committed_revision=expected_revision+1) OR (phase!='COMMITTED' AND committed_revision IS NULL)))");
        db.execSQL("CREATE UNIQUE INDEX one_active_turn ON turn_control(active_slot) WHERE phase NOT IN ('REJECTED','COMMITTED')");
        db.execSQL("CREATE TABLE request_alias(request_id TEXT PRIMARY KEY,turn_id TEXT NOT NULL REFERENCES turn_control(turn_id))");
        db.execSQL("CREATE TABLE native_event(event_id TEXT PRIMARY KEY,turn_id TEXT NOT NULL,revision INTEGER NOT NULL CHECK(revision>0),ordinal INTEGER NOT NULL CHECK(ordinal>=0),type TEXT NOT NULL,record TEXT NOT NULL CHECK(length(record)<=131072),digest TEXT NOT NULL,UNIQUE(turn_id,ordinal),FOREIGN KEY(turn_id,revision) REFERENCES turn_control(turn_id,committed_revision) DEFERRABLE INITIALLY DEFERRED)");
        db.execSQL("CREATE INDEX event_by_revision ON native_event(revision,ordinal)");
        for (String ddl : ObservationSchema.createStatements()) db.execSQL(ddl);
        for (String table : new String[]{"native_event", "request_alias"}) {
          db.execSQL("CREATE TRIGGER " + table + "_no_update BEFORE UPDATE ON " + table + " BEGIN SELECT RAISE(ABORT,'immutable_record'); END");
          db.execSQL("CREATE TRIGGER " + table + "_no_delete BEFORE DELETE ON " + table + " BEGIN SELECT RAISE(ABORT,'immutable_record'); END");
        }
        db.execSQL("CREATE TRIGGER receipt_no_update BEFORE UPDATE ON turn_control WHEN OLD.phase='COMMITTED' BEGIN SELECT RAISE(ABORT,'immutable_receipt'); END");
        db.execSQL("CREATE TRIGGER receipt_no_delete BEFORE DELETE ON turn_control BEGIN SELECT RAISE(ABORT,'immutable_turn'); END");
        ContentValues meta = new ContentValues();
        meta.put("singleton", 1); meta.put("slot_id", id); meta.put("format_version", FORMAT_VERSION);
        meta.put("revision", 0); meta.put("policy_version", policy); meta.put("genesis", snapshot);
        meta.put("genesis_digest", hash(snapshot)); meta.put("snapshot", snapshot); meta.put("snapshot_digest", hash(snapshot));
        db.insertOrThrow("slot_meta", null, meta);
        db.setVersion(FORMAT_VERSION); db.execSQL("PRAGMA application_id=" + APPLICATION_ID);
        db.setTransactionSuccessful();
      } finally { db.endTransaction(); }
      store = new CompanionSlotStore(file, lease, db, id, policy);
      store.validate();
      return store;
    } catch (IOException | RuntimeException error) {
      if (store != null) store.close();
      else { if (db != null) db.close(); release(lease); }
      throw error;
    }
  }

  static CompanionSlotStore openIn(File directory, String id, String policy) throws IOException {
    File file = slotFile(directory, id);
    if (!file.isFile() || !new File(file.getPath() + ".lease").isFile()) throw new IOException("slot_missing");
    Lease lease = acquire(file);
    SQLiteDatabase db = null;
    CompanionSlotStore store = null;
    try {
      if (!file.isFile()) throw new IOException("slot_missing");
      db = openDatabase(file);
      store = new CompanionSlotStore(file, lease, db, id, policy);
      store.validate(); return store;
    } catch (IOException | RuntimeException error) {
      if (store != null) store.close();
      else { if (db != null) db.close(); release(lease); }
      throw error;
    }
  }

  public synchronized byte[] genesis() throws IOException {
    requireOpen();
    try (Cursor c = database.rawQuery("SELECT genesis,genesis_digest FROM slot_meta WHERE singleton=1", null)) {
      if (!c.moveToFirst()) throw new IOException("genesis_missing");
      byte[] bytes = c.getBlob(0);
      if (!hash(bytes).equals(c.getString(1))) throw new IOException("genesis_digest_mismatch");
      return bytes;
    }
  }

  public synchronized Result admit(Request request) throws IOException {
    return transaction(() -> {
      if (!slotId.equals(request.slotId)) return new Result(AdmissionKind.SLOT_MISMATCH, null);
      long revision = revision();
      CompanionPendingTurn prior = byRequest(request.requestId);
      if (prior == null) prior = active();
      if (prior != null) {
        Admission admission = prior.admit(request, revision);
        if (admission.kind == AdmissionKind.ALIAS) write(admission.turn);
        return new Result(admission.kind, admission.turn);
      }
      if (request.expectedRevision != revision) return new Result(AdmissionKind.STALE_REVISION, null);
      CompanionPendingTurn turn = CompanionPendingTurn.begin(request, "turn-" + UUID.randomUUID(), revision);
      write(turn); return new Result(AdmissionKind.CREATED, turn);
    });
  }

  public synchronized CompanionPendingTurn lockDecision(String requestId, DecisionLock decision) throws IOException {
    return transaction(() -> { CompanionPendingTurn next = required(requestId).lockDecision(decision); write(next); return next; });
  }
  /** Factory runs under the DB writer lock. Never performs provider calls or exposes uncommitted dice. */
  public synchronized CompanionPendingTurn reserve(String requestId, ReservationFactory factory) throws IOException {
    return transaction(() -> {
      CompanionPendingTurn turn = required(requestId);
      if (turn.phase == Phase.RESERVED) return turn;
      if (turn.phase != Phase.DECISION_LOCKED) throw new IOException("reservation_phase_invalid");
      CompanionPendingTurn next = turn.reserve(factory.capture(turn.decision));
      write(next); return next;
    });
  }
  /** Native-only seam: no caller snapshot, slot, policy or mutable DB handle. */
  synchronized <T> T inspectNative(String requestId, NativeWork<T> work) throws IOException {
    return transaction(() -> work.apply(nativeView(requestId)));
  }
  /** Validate retries too; only a new capture may call the existing RNG source. */
  synchronized CompanionPendingTurn reserveVerified(String requestId, NativeValidator validator,
      NativeCapture capture) throws IOException {
    return transaction(() -> {
      NativeView view = nativeView(requestId);
      if (view.turn.phase != Phase.DECISION_LOCKED && view.turn.phase != Phase.RESERVED)
        throw new IOException("reservation_phase_invalid");
      validator.verify(view);
      if (view.turn.phase == Phase.RESERVED) return view.turn;
      CompanionPendingTurn next = view.turn.reserve(capture.capture(view));
      write(next); return next;
    });
  }
  private NativeView nativeView(String requestId) throws IOException {
    return new NativeView(slotId, policyVersion, revision(), required(requestId), snapshotWithin());
  }
  public synchronized CompanionPendingTurn cancel(String requestId) throws IOException {
    return transaction(() -> { CompanionPendingTurn next = required(requestId).cancel(); write(next); return next; });
  }
  public synchronized CompanionPendingTurn suspend(String requestId) throws IOException {
    return transaction(() -> { CompanionPendingTurn next = required(requestId).suspend(); write(next); return next; });
  }
  public synchronized CompanionPendingTurn resume(String requestId) throws IOException {
    return transaction(() -> { CompanionPendingTurn next = required(requestId).resume(); write(next); return next; });
  }
  public synchronized CompanionPendingTurn recover() throws IOException { return transaction(this::active); }
  public synchronized CompanionPendingTurn request(String id) throws IOException { return transaction(() -> byRequest(id)); }


  /** Read copies only after the validated read transaction has completed. */
  public synchronized byte[] currentSnapshot() throws IOException {
    return transaction(() -> { verifyHead(true); return snapshotWithin(); });
  }
  public synchronized long currentRevision() throws IOException { return transaction(this::revision); }

  public static final class LedgerEvent {
    public final String id, turnId, type, record, digest;
    public final long revision;
    public final int ordinal;
    LedgerEvent(String id, String turnId, long revision, int ordinal, String type, String record, String digest) {
      this.id=id; this.turnId=turnId; this.revision=revision; this.ordinal=ordinal;
      this.type=type; this.record=record; this.digest=digest;
    }
  }
  public synchronized List<LedgerEvent> events(long firstRevision, int limit) throws IOException {
    if (firstRevision < 1 || limit < 1 || limit > 256) throw new IOException("event_query_invalid");
    return transaction(() -> {
      ArrayList<LedgerEvent> rows = new ArrayList<>();
      try (Cursor c = database.rawQuery("SELECT event_id,turn_id,revision,ordinal,type,record,digest FROM native_event WHERE revision>=? ORDER BY revision,ordinal LIMIT ?",
          new String[]{Long.toString(firstRevision), Integer.toString(limit)})) {
        while(c.moveToNext()) {
          LedgerEvent row = eventRow(c); CompanionLedgerVerifier.verifyEvent(slotId, revision(), row); read(row.turnId); rows.add(row);
        }
      }
      return java.util.Collections.unmodifiableList(rows);
    });
  }
  /**
   * Query only owner-visible records from this slot's native transaction.
   * Each returned turn is receipt-verified before any public payload escapes.
   */
  public synchronized List<ObservationReader.Row> observations(String actorId, int limit) throws IOException {
    return transaction(() -> {
      List<ObservationReader.Row> rows =
          ObservationStorageReader.read(database,slotId,actorId,revision(),limit);
      Set<String> checked = new HashSet<>();
      for (ObservationReader.Row row : rows) {
        if (checked.add(row.getTurnId())) read(row.getTurnId());
      }
      return rows;
    });
  }

  /** Historical identity is checked before the current snapshot or any gameplay replay. */
  public synchronized CompanionPendingTurn.Receipt committedReceipt(Request request) throws IOException {
    return transaction(() -> receiptWithin(request));
  }
  private CompanionPendingTurn.Receipt receiptWithin(Request request) throws IOException {
    if (!slotId.equals(request.slotId)) throw new IOException("slot_mismatch");
    CompanionPendingTurn turn = byRequest(request.requestId);
    if (turn == null) return null;
    Admission admission = turn.admit(request, revision());
    if (admission.kind == AdmissionKind.REQUEST_CONFLICT || admission.kind == AdmissionKind.STALE_REVISION)
      throw new IOException("receipt_request_mismatch");
    return admission.kind == AdmissionKind.COMMITTED_REPLAY ? turn.receipt : null;
  }
  /** Batch is rebuilt under the primary writer. No model/UI snapshot can bypass native replay. */
  public synchronized CompanionPendingTurn.Receipt commitWait(String requestId, long expectedRevision,
      String exactInput, CompanionWaitBatch.Batch candidate) throws IOException {
    Request identity = Request.fromPlayerInput(slotId, requestId, expectedRevision, "cao_minh", exactInput);
    try {
      CompanionPendingTurn.Receipt receipt = transaction(() -> {
        CompanionPendingTurn.Receipt prior = receiptWithin(identity);
        if (prior != null) return prior;
        NativeView view = nativeView(requestId);
        CompanionWaitBatch.Batch batch = CompanionWaitBatch.verifyForCommit(view, requestId, expectedRevision, exactInput, candidate);
        for (CompanionWaitBatch.Event event : batch.getEvents()) {
          ContentValues row = new ContentValues();
          row.put("event_id", event.getId()); row.put("turn_id", batch.getTurnId());
          row.put("revision", expectedRevision + 1); row.put("ordinal", event.getOrdinal());
          row.put("type", event.getType()); row.put("record", event.getRecord()); row.put("digest", event.getDigest());
          database.insertOrThrow("native_event", null, row); fault.at("after_event_write");
        }
        byte[] snapshot = batch.getAfterSnapshot().getBytes(StandardCharsets.UTF_8);
        if (snapshot.length == 0 || snapshot.length > 16777216) throw new IOException("snapshot_size_invalid");
        ContentValues meta = new ContentValues();
        meta.put("revision", expectedRevision + 1); meta.put("snapshot", snapshot); meta.put("snapshot_digest", hash(snapshot));
        if (database.update("slot_meta", meta, "singleton=1 AND revision=?", new String[]{Long.toString(expectedRevision)}) != 1)
          throw new IOException("revision_cas_failed");
        fault.at("after_snapshot_write");
        CompanionPendingTurn committed = view.turn.markCommitted(expectedRevision,
          batch.getDecisionDigest(), batch.getReservationDigest(), batch.getManifest(), batch.getFinalResult());
        write(committed); fault.at("after_receipt_write");
        // Without native sensory proof, the receipt must declare zero observations.
        // The publisher shares the same Core/event/receipt writer transaction.
        ObservationPublisher.publish(database, slotId, batch.getTurnId(), expectedRevision + 1,
            java.util.Collections.emptyList(), point -> fault.at(point));
        fault.at("after_observation_publish"); verifyReceipt(committed);
        return committed.receipt;
      });
      fault.at("after_durable_commit");
      return receipt;
    } catch (IOException | RuntimeException error) {
      // EndTransaction may have committed despite throwing. Read the original identity
      // before considering a retry. Readback itself never captures/stages or injects faults.
      try {
        CompanionPendingTurn.Receipt receipt = transaction(() -> receiptWithin(identity), false);
        if (receipt != null) return receipt;
      } catch (IOException | RuntimeException readback) { error.addSuppressed(readback); }
      throw error;
    }
  }
  private long revision() throws IOException { return scalar("SELECT revision FROM slot_meta WHERE singleton=1"); }
  private byte[] snapshotWithin() throws IOException {
    try (Cursor c = database.rawQuery("SELECT snapshot,snapshot_digest FROM slot_meta WHERE singleton=1", null)) {
      if (!c.moveToFirst()) throw new IOException("snapshot_missing");
      byte[] bytes=c.getBlob(0);
      if (bytes.length==0 || bytes.length>16777216 || !hash(bytes).equals(c.getString(1)))
        throw new IOException("snapshot_digest_mismatch");
      return bytes;
    }
  }
  private LedgerEvent eventRow(Cursor c) {
    return new LedgerEvent(c.getString(0),c.getString(1),c.getLong(2),c.getInt(3),c.getString(4),c.getString(5),c.getString(6));
  }
  private void verifyReceipt(CompanionPendingTurn turn) throws IOException {
    ArrayList<LedgerEvent> events = new ArrayList<>();
    try (Cursor c = database.rawQuery("SELECT event_id,turn_id,revision,ordinal,type,record,digest FROM native_event WHERE turn_id=? ORDER BY ordinal",new String[]{turn.turnId})) {
      while(c.moveToNext()) events.add(eventRow(c));
    }
    CompanionLedgerVerifier.verifyReceipt(slotId, policyVersion, revision(), turn, events);
    // Reject any rows not backed by this WAIT receipt (currently an empty manifest).
    String[] binding = new String[]{turn.turnId, Long.toString(turn.receipt.committedRevision)};
    for (String table : new String[]{"actor_observation", "observation_manifest"}) {
      try (Cursor c = database.rawQuery("SELECT COUNT(*) FROM " + table +
          " WHERE " + (table.equals("actor_observation") ? "created_turn_id" : "turn_id") +
          "=? AND committed_revision=?", binding)) {
        if (!c.moveToFirst() || c.getLong(0) != 0) throw new IOException("observation_receipt_incomplete");
      }
    }
  }
  private void verifyHead(boolean decodeCore) throws IOException {
    byte[] snapshot=snapshotWithin(); long revision=revision();
    if (revision==0) {
      if (!java.util.Arrays.equals(snapshot,genesis()) || scalar("SELECT COUNT(*) FROM native_event")!=0 ||
          scalar("SELECT COUNT(*) FROM turn_control WHERE phase='COMMITTED'")!=0) throw new IOException("genesis_manifest_mismatch");
      return;
    }
    try (Cursor c=database.rawQuery("SELECT turn_id FROM turn_control WHERE committed_revision=?",new String[]{Long.toString(revision)})) {
      if(!c.moveToFirst()) throw new IOException("head_receipt_missing");
      CompanionPendingTurn head=read(c.getString(0));
      if (decodeCore) CompanionLedgerVerifier.verifyHead(snapshot,head);
      else CompanionLedgerVerifier.verifyHeadDigest(snapshot,head);
    }
  }
  private void verifyChain() throws IOException {
    long expected=1; String before=hash(genesis());
    try (Cursor c=database.rawQuery("SELECT turn_id FROM turn_control WHERE phase='COMMITTED' ORDER BY committed_revision",null)) {
      while(c.moveToNext()) {
        CompanionPendingTurn turn=read(c.getString(0));
        before=CompanionLedgerVerifier.verifyChainLink(expected++, before, turn);
      }
    }
    if(expected!=revision()+1 || !before.equals(hash(snapshotWithin()))) throw new IOException("receipt_chain_mismatch");
    if(scalar("SELECT COUNT(*) FROM native_event WHERE revision>"+revision())!=0) throw new IOException("future_event");
  }

  private <T> T transaction(Work<T> work) throws IOException { return transaction(work, true); }
  private <T> T transaction(Work<T> work, boolean injectFault) throws IOException {
    requireOpen();
    database.beginTransaction();
    try {
      verifyConfiguration(database);
      verifyMetadata();
      T result = work.run(); if (injectFault) fault.at("before_commit");
      database.setTransactionSuccessful(); return result;
    } finally { database.endTransaction(); }
  }

  private CompanionPendingTurn required(String id) throws IOException {
    CompanionPendingTurn turn = byRequest(id);
    if (turn == null) throw new IOException("request_unknown"); return turn;
  }
  private CompanionPendingTurn byRequest(String id) throws IOException {
    try (Cursor c = database.rawQuery("SELECT turn_id FROM request_alias WHERE request_id=?", new String[]{id})) {
      return c.moveToFirst() ? read(c.getString(0)) : null;
    }
  }
  private CompanionPendingTurn active() throws IOException {
    try (Cursor c = database.rawQuery("SELECT turn_id FROM turn_control WHERE phase NOT IN ('REJECTED','COMMITTED')", null)) {
      return c.moveToFirst() ? read(c.getString(0)) : null;
    }
  }
  private CompanionPendingTurn read(String id) throws IOException {
    CompanionPendingTurn turn;
    try (Cursor c = database.rawQuery("SELECT expected_revision,phase,record,committed_revision FROM turn_control WHERE turn_id=?", new String[]{id})) {
      if (!c.moveToFirst()) throw new IOException("turn_missing");
      turn = CompanionPendingTurnCodec.decode(c.getBlob(2));
      if (!turn.turnId.equals(id) || !turn.slotId.equals(slotId) || turn.expectedRevision > revision()
          || c.getLong(0) != turn.expectedRevision || !c.getString(1).equals(turn.phase.name())
          || (turn.phase == Phase.COMMITTED ? c.isNull(3) || c.getLong(3) != turn.receipt.committedRevision : !c.isNull(3))
          || (turn.phase != Phase.COMMITTED && turn.phase != Phase.REJECTED && turn.expectedRevision != revision())
          || (turn.decision != null && !policyVersion.equals(turn.decision.policyVersion))) {
        throw new IOException("turn_metadata_mismatch");
      }
    }
    Set<String> aliases = new HashSet<>();
    try (Cursor c = database.rawQuery("SELECT request_id FROM request_alias WHERE turn_id=?", new String[]{id})) {
      while (c.moveToNext()) aliases.add(c.getString(0));
    }
    if (!aliases.equals(turn.requestAliases)) throw new IOException("alias_manifest_mismatch");
    if (turn.phase == Phase.COMMITTED) verifyReceipt(turn);
    return turn;
  }
  private void write(CompanionPendingTurn turn) throws IOException {
    if (!slotId.equals(turn.slotId) || turn.expectedRevision > revision()
        || (turn.decision != null && !policyVersion.equals(turn.decision.policyVersion))) throw new IOException("turn_binding_invalid");
    ContentValues row = new ContentValues(); row.put("turn_id", turn.turnId); row.put("active_slot", 1);
    row.put("expected_revision", turn.expectedRevision); row.put("phase", turn.phase.name());
    row.put("record", CompanionPendingTurnCodec.encode(turn));
    if (turn.receipt == null) row.putNull("committed_revision"); else row.put("committed_revision", turn.receipt.committedRevision);
    if (database.update("turn_control", row, "turn_id=?", new String[]{turn.turnId}) == 0) database.insertOrThrow("turn_control", null, row);
    fault.at("after_turn_write");
    for (String alias : turn.requestAliases) {
      try (Cursor c = database.rawQuery("SELECT turn_id FROM request_alias WHERE request_id=?", new String[]{alias})) {
        if (c.moveToFirst()) {
          if (!turn.turnId.equals(c.getString(0))) throw new IOException("alias_collision");
        } else {
          ContentValues binding = new ContentValues(); binding.put("request_id", alias); binding.put("turn_id", turn.turnId);
          database.insertOrThrow("request_alias", null, binding);
        }
      }
    }
    fault.at("after_alias_write");
  }

  private synchronized void validate() throws IOException {
    transaction(() -> {
    if (database.getVersion() != FORMAT_VERSION || scalar("PRAGMA application_id") != APPLICATION_ID) throw new IOException("storage_version_unsupported");
    try (Cursor c = database.rawQuery("PRAGMA quick_check", null)) {
      if (!c.moveToFirst() || !"ok".equals(c.getString(0))) throw new IOException("database_integrity_failed");
    }
    try (Cursor c = database.rawQuery("PRAGMA foreign_key_check", null)) {
      if (c.moveToFirst()) throw new IOException("database_foreign_key_failed");
    }
    // No in-place migration. Refuse slots with missing schema or immutable provenance guards.
    if (scalar("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name IN ('actor_observation','observation_manifest')") != 2 ||
        scalar("SELECT COUNT(*) FROM sqlite_master WHERE type='trigger' AND name IN ('actor_observation_no_update','actor_observation_no_delete','observation_manifest_no_update','observation_manifest_no_delete')") != 4 ||
        scalar("SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='event_observation_binding'") != 1)
      throw new IOException("observation_schema_incomplete");
    genesis(); snapshotWithin(); verifyChain(); verifyHead(true);
    try (Cursor c = database.rawQuery("SELECT turn_id FROM turn_control", null)) { while (c.moveToNext()) read(c.getString(0)); }
    return null;
    });
  }
  private void verifyMetadata() throws IOException {
    if (database.getVersion() != FORMAT_VERSION || scalar("PRAGMA application_id") != APPLICATION_ID) throw new IOException("storage_version_unsupported");
    try (Cursor c = database.rawQuery("SELECT slot_id,format_version,revision,policy_version FROM slot_meta WHERE singleton=1", null)) {
      if (!c.moveToFirst() || !slotId.equals(c.getString(0)) || c.getInt(1) != FORMAT_VERSION
          || (c.getLong(2) < 0 || c.getLong(2) > Integer.MAX_VALUE) || !policyVersion.equals(c.getString(3))) throw new IOException("slot_metadata_mismatch");
    }
    // Identity/receipt/hash completeness under every writer. Decode Core on load,
    // snapshot publication and gameplay binding/staging, rather than on alias-only reads.
    verifyHead(false);
  }
  private static SQLiteDatabase openDatabase(File file) throws IOException {
    DatabaseErrorHandler preserve = db -> {
      SQLiteDatabaseCorruptException error = new SQLiteDatabaseCorruptException("companion_corrupt_preserved");
      try { db.close(); } catch (RuntimeException closeError) { error.addSuppressed(closeError); }
      throw error;
    };
    SQLiteDatabase db = SQLiteDatabase.openDatabase(file.getPath(), null,
        SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.NO_LOCALIZED_COLLATORS
            | SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING, preserve);
    try {
      db.setForeignKeyConstraintsEnabled(true);
      db.execSQL("PRAGMA synchronous=FULL"); return db;
    } catch (RuntimeException error) { db.close(); throw error; }
  }
  // Called inside a writer transaction: queries use its primary connection, not a reader pool.
  private static void verifyConfiguration(SQLiteDatabase db) throws IOException {
    String journal;
    try (Cursor c = db.rawQuery("PRAGMA journal_mode", null)) {
      if (!c.moveToFirst()) throw new IOException("journal_configuration_missing");
      journal = c.getString(0);
    }
    long synchronous = scalar(db, "PRAGMA synchronous");
    long foreignKeys = scalar(db, "PRAGMA foreign_keys");
    if (!"wal".equalsIgnoreCase(journal) || synchronous != 2 || foreignKeys != 1)
      throw new IOException("connection_configuration_failed: journal=" + journal
          + ", synchronous=" + synchronous + ", foreign_keys=" + foreignKeys);
  }
  private long scalar(String sql) throws IOException { return scalar(database, sql); }
  private static long scalar(SQLiteDatabase db, String sql) throws IOException {
    try (Cursor c = db.rawQuery(sql, null)) { if (!c.moveToFirst()) throw new IOException("pragma_missing"); return c.getLong(0); }
  }
  private void requireOpen() throws IOException { if (closed) throw new IOException("slot_closed"); }
  private static File slotFile(File directory, String id) throws IOException {
    if (id == null || !id.matches("[0-9a-f]{32}")) throw new IOException("slot_id_invalid");
    File file = new File(directory.getCanonicalFile(), "slot-" + id + ".db");
    if (!file.equals(file.getCanonicalFile())) throw new IOException("slot_path_invalid"); return file;
  }
  private static String hash(byte[] bytes) {
    try {
      byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
      StringBuilder hex = new StringBuilder();
      for (byte b : hash) { hex.append("0123456789abcdef".charAt((b & 255) >>> 4)); hex.append("0123456789abcdef".charAt(b & 15)); }
      return hex.toString();
    } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
  }

  void faultForTest(Fault fault) { this.fault = fault; }
  File fileForTest() { return file; }
  @Override public void close() {
    synchronized (this) {
      if (closed) return;
      closed = true;
      try { database.close(); } finally { synchronized (LEASE_LOCK) { lease.stores.remove(this); release(lease); } }
    }
  }
  /** Explicit deletion closes every same-process handle while retaining the cross-process lease. */
  public void deleteSlot() throws IOException {
    ArrayList<CompanionSlotStore> stores;
    synchronized (this) {
      requireOpen();
      synchronized (LEASE_LOCK) {
        if (LEASES.get(lease.key) != lease || lease.deleting || lease.openings != lease.stores.size()) {
          throw new IOException("slot_lifecycle_busy");
        }
        lease.deleting = true; stores = new ArrayList<>(lease.stores);
      }
    }
    try {
      fault.at("after_delete_guard");
      for (CompanionSlotStore store : stores) store.close();
      if (!SQLiteDatabase.deleteDatabase(file)) throw new IOException("slot_delete_failed");
    } finally { synchronized (LEASE_LOCK) { lease.deleting = false; release(lease); } }
    // The tiny lease identity file remains; never unlink a lock inode another process might reference.
  }
  private static final class Lease {
    final String key; final RandomAccessFile handle; final FileLock lock;
    final Set<CompanionSlotStore> stores = new HashSet<>();
    int openings; boolean deleting;
    Lease(String key, RandomAccessFile handle, FileLock lock) { this.key = key; this.handle = handle; this.lock = lock; this.openings = 1; }
  }
  private static Lease acquire(File file) throws IOException {
    synchronized (LEASE_LOCK) {
      String key = file.getCanonicalPath(); Lease prior = LEASES.get(key);
      if (prior != null) { if (prior.deleting) throw new IOException("slot_deleting"); prior.openings++; return prior; }
      RandomAccessFile handle = new RandomAccessFile(key + ".lease", "rw");
      try {
        FileLock lock = handle.getChannel().tryLock();
        if (lock == null) throw new IOException("slot_process_busy");
        Lease lease = new Lease(key, handle, lock); LEASES.put(key, lease); return lease;
      } catch (IOException | RuntimeException error) { handle.close(); throw error; }
    }
  }
  private static void release(Lease lease) {
    synchronized (LEASE_LOCK) {
      if (lease.openings > 0) lease.openings--;
      if (lease.openings == 0 && lease.stores.isEmpty() && !lease.deleting) {
        LEASES.remove(lease.key);
        try { lease.lock.release(); } catch (IOException ignored) { /* handle close also releases lock */ }
        try { lease.handle.close(); } catch (IOException ignored) { /* closed store is never reused */ }
      }
    }
  }
}
