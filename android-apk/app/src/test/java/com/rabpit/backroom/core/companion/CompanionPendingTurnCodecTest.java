package com.rabpit.backroom.core.companion;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

import com.rabpit.backroom.core.companion.CompanionPendingTurn.AdmissionKind;
import com.rabpit.backroom.core.companion.CompanionPendingTurn.DecisionLock;
import com.rabpit.backroom.core.companion.CompanionPendingTurn.Phase;
import com.rabpit.backroom.core.companion.CompanionPendingTurn.Request;
import com.rabpit.backroom.core.companion.CompanionPendingTurn.Reservation;

public class CompanionPendingTurnCodecTest {
  private CompanionPendingTurn preparing() {
    return CompanionPendingTurn.begin(Request.fromPlayerInput("s", "r", 7, "cao_minh", "Hỏi Cao Minh về hành lang 🙂"), "t", 7);
  }
  private CompanionPendingTurn locked() {
    return preparing().lockDecision(new DecisionLock("cao_minh", 7, "p1", "TALK:🙂"));
  }
  private CompanionPendingTurn reserved() {
    CompanionPendingTurn t = locked();
    return t.reserve(new Reservation("roll", t.decision.digest, "p1", "exit:2:0"));
  }
  private CompanionPendingTurn committed() {
    CompanionPendingTurn t = reserved();
    return t.markCommitted(7, t.decision.digest, t.reservation.digest, "manifest", "reply");
  }
  @Test public void everyLegalPhaseRoundTripsExactly() throws IOException {
    for (CompanionPendingTurn t : new CompanionPendingTurn[] {preparing(), locked(), reserved(),
        preparing().suspend(), locked().suspend(), reserved().suspend(), preparing().cancel(), committed()}) {
      byte[] bytes = CompanionPendingTurnCodec.encode(t);
      CompanionPendingTurn restored = CompanionPendingTurnCodec.decode(bytes);
      assertArrayEquals(bytes, CompanionPendingTurnCodec.encode(restored));
      assertEquals(t.phase, restored.phase); assertEquals(t.resumePhase, restored.resumePhase);
      assertEquals(t.inputDigest, restored.inputDigest); assertEquals(t.requestAliases, restored.requestAliases);
      if (t.decision != null) assertEquals(t.decision.digest, restored.decision.digest);
      if (t.reservation != null) assertEquals(t.reservation.digest, restored.reservation.digest);
      if (t.receipt != null) assertEquals(t.receipt.finalResult, restored.receipt.finalResult);
    }
  }
  @Test public void aliasesRecoverAndReplayReceiptWithoutNewTurn() throws IOException {
    CompanionPendingTurn t = reserved().admit(Request.fromPlayerInput("s", "alias", 7, "cao_minh", "Hỏi Cao Minh về hành lang 🙂"), 7).turn;
    t = t.markCommitted(7, t.decision.digest, t.reservation.digest, "m", "exact saved reply");
    CompanionPendingTurn restored = CompanionPendingTurnCodec.decode(CompanionPendingTurnCodec.encode(t));
    assertEquals(AdmissionKind.COMMITTED_REPLAY, restored.admit(Request.fromPlayerInput("s", "alias", 7, "cao_minh", "Hỏi Cao Minh về hành lang 🙂"), 42).kind);
    assertEquals("exact saved reply", restored.receipt.finalResult);
  }
  @Test public void suspendedReservationCannotRerollAfterRecovery() throws IOException {
    CompanionPendingTurn t = CompanionPendingTurnCodec.decode(CompanionPendingTurnCodec.encode(reserved().cancel()));
    assertEquals(Phase.SUSPENDED, t.cancel().phase);
    CompanionPendingTurn resumed = t.resume();
    assertEquals(Phase.RESERVED, resumed.phase);
    try { resumed.reserve(new Reservation("new", resumed.decision.digest, "p1", "exit:2:1")); fail(); }
    catch (IllegalStateException expected) { /* original tape is locked */ }
  }
  @Test public void everyTruncationAndSingleByteCorruptionRejects() throws IOException {
    byte[] valid = CompanionPendingTurnCodec.encode(reserved());
    for (int i = 0; i < valid.length; i++) {
      reject(Arrays.copyOf(valid, i));
      byte[] changed = valid.clone(); changed[i] ^= 1; reject(changed);
    }
    reject(null); reject(new byte[CompanionPendingTurnCodec.MAX_RECORD_BYTES + 1]);
  }
  @Test public void unsupportedVersionAndTrailingBytesRejectEvenWithValidChecksum() throws Exception {
    byte[] body = body(CompanionPendingTurnCodec.encode(preparing()));
    ByteBuffer.wrap(body).putInt(4, 99); reject(seal(body));
    byte[] extra = Arrays.copyOf(body(CompanionPendingTurnCodec.encode(preparing())), body.length + 1);
    reject(seal(extra));
  }
  @Test public void inconsistentPhaseAndResumeRejectWithValidChecksum() throws Exception {
    byte[] body = body(CompanionPendingTurnCodec.encode(reserved()));
    replace(body, "RESERVED", "REJECTED"); reject(seal(body));
    body = body(CompanionPendingTurnCodec.encode(reserved().suspend()));
    replace(body, "RESERVED", "REJECTED"); reject(seal(body));
  }
  @Test public void invalidFieldLengthAndUnicodeRejectWithValidChecksum() throws Exception {
    byte[] body = body(CompanionPendingTurnCodec.encode(preparing()));
    ByteBuffer.wrap(body).putInt(8, Integer.MAX_VALUE); reject(seal(body));
    body = body(CompanionPendingTurnCodec.encode(preparing()));
    body[12] = (byte) 0xff; reject(seal(body));
  }
  @Test public void decisionAndReservationBindingsRecheckedOnLoad() throws Exception {
    byte[] body = body(CompanionPendingTurnCodec.encode(reserved()));
    // Change only the reservation's decision digest; checksum is recomputed but binding must reject.
    String lockDigest = reserved().decision.digest;
    replace(body, lockDigest, (lockDigest.charAt(0) == 'a' ? "b" : "a") + lockDigest.substring(1));
    reject(seal(body));
  }
  @Test public void aliasFloodBoundSurvivesReload() throws IOException {
    CompanionPendingTurn t = preparing();
    for (int i = 1; i < CompanionPendingTurn.MAX_REQUEST_ALIASES; i++) {
      t = t.admit(Request.fromPlayerInput("s", "a" + i, 7, "cao_minh", "Hỏi Cao Minh về hành lang 🙂"), 7).turn;
    }
    t = CompanionPendingTurnCodec.decode(CompanionPendingTurnCodec.encode(t));
    assertEquals(AdmissionKind.ALIAS_LIMIT, t.admit(Request.fromPlayerInput("s", "overflow", 7, "cao_minh", "Hỏi Cao Minh về hành lang 🙂"), 7).kind);
    assertEquals(AdmissionKind.PENDING_REPLAY, t.admit(Request.fromPlayerInput("s", "a1", 7, "cao_minh", "Hỏi Cao Minh về hành lang 🙂"), 7).kind);
  }
  private static byte[] body(byte[] record) { return Arrays.copyOf(record, record.length - 32); }
  private static byte[] seal(byte[] body) throws Exception {
    byte[] record = Arrays.copyOf(body, body.length + 32);
    System.arraycopy(MessageDigest.getInstance("SHA-256").digest(body), 0, record, body.length, 32);
    return record;
  }
  private static void replace(byte[] body, String before, String after) {
    byte[] from = before.getBytes(StandardCharsets.UTF_8); byte[] to = after.getBytes(StandardCharsets.UTF_8);
    assertEquals(from.length, to.length);
    for (int i = 0; i <= body.length - from.length; i++) {
      if (Arrays.equals(from, Arrays.copyOfRange(body, i, i + from.length))) {
        System.arraycopy(to, 0, body, i, to.length); return;
      }
    }
    fail("fixture field missing");
  }
  private static void reject(byte[] bytes) {
    try { CompanionPendingTurnCodec.decode(bytes); fail("damaged record accepted"); }
    catch (IOException expected) { /* fail closed */ }
  }
}
