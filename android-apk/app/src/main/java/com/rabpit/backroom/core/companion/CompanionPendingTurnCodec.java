package com.rabpit.backroom.core.companion;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import com.rabpit.backroom.core.companion.CompanionPendingTurn.Admission;
import com.rabpit.backroom.core.companion.CompanionPendingTurn.AdmissionKind;
import com.rabpit.backroom.core.companion.CompanionPendingTurn.DecisionLock;
import com.rabpit.backroom.core.companion.CompanionPendingTurn.Phase;
import com.rabpit.backroom.core.companion.CompanionPendingTurn.Request;
import com.rabpit.backroom.core.companion.CompanionPendingTurn.Reservation;

/** Bounded V1 record encoding, not DB persistence. Checksums detect damage, not malicious edits. */
public final class CompanionPendingTurnCodec {
  private static final int MAGIC = 0x43505431;
  private static final int VERSION = 1;
  private static final int MAX_FIELD_BYTES = 131072;
  public static final int MAX_RECORD_BYTES = 1048576;
  private static final int CHECKSUM_BYTES = 32;
  private CompanionPendingTurnCodec() {}

  public static byte[] encode(CompanionPendingTurn turn) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    DataOutputStream out = new DataOutputStream(buffer);
    out.writeInt(MAGIC); out.writeInt(VERSION);
    text(out, turn.slotId); text(out, turn.turnId); out.writeLong(turn.expectedRevision);
    text(out, turn.inputDigest); text(out, turn.phase.name());
    text(out, turn.resumePhase == null ? "-" : turn.resumePhase.name());
    out.writeInt(turn.requestAliases.size());
    for (String alias : turn.requestAliases) text(out, alias);
    out.writeBoolean(turn.decision != null);
    if (turn.decision != null) {
      text(out, turn.decision.actorId); out.writeLong(turn.decision.sceneRevision);
      text(out, turn.decision.policyVersion); text(out, turn.decision.canonicalPayload);
    }
    out.writeBoolean(turn.reservation != null);
    if (turn.reservation != null) {
      text(out, turn.reservation.reservationId); text(out, turn.reservation.decisionDigest);
      text(out, turn.reservation.policyVersion); text(out, turn.reservation.canonicalPayload);
    }
    out.writeBoolean(turn.receipt != null);
    if (turn.receipt != null) {
      out.writeLong(turn.receipt.committedRevision);
      text(out, turn.receipt.manifest); text(out, turn.receipt.finalResult);
    }
    out.flush();
    byte[] body = buffer.toByteArray();
    if (body.length + CHECKSUM_BYTES > MAX_RECORD_BYTES) throw new IOException("record_too_large");
    out.write(checksum(body)); out.flush();
    return buffer.toByteArray();
  }

  /** Reconstruct through production transitions, so corrupt phase/binding data cannot bypass guards. */
  public static CompanionPendingTurn decode(byte[] record) throws IOException {
    if (record == null || record.length < CHECKSUM_BYTES + 8 || record.length > MAX_RECORD_BYTES) {
      throw new IOException("record_size_invalid");
    }
    // Copy once: caller mutation after this point cannot change what was verified.
    byte[] body = Arrays.copyOf(record, record.length - CHECKSUM_BYTES);
    byte[] expected = Arrays.copyOfRange(record, body.length, record.length);
    if (!MessageDigest.isEqual(checksum(body), expected)) throw new IOException("record_checksum_mismatch");
    DataInputStream in = new DataInputStream(new ByteArrayInputStream(body));
    try {
      if (in.readInt() != MAGIC || in.readInt() != VERSION) throw new IOException("record_version_unsupported");
      String slotId = text(in); String turnId = text(in); long revision = in.readLong();
      String inputDigest = text(in); Phase phase = Phase.valueOf(text(in));
      String resumeName = text(in);
      Phase resume = "-".equals(resumeName) ? null : Phase.valueOf(resumeName);
      if ((phase == Phase.SUSPENDED) != (resume != null)
          || (resume != null && resume != Phase.PREPARING && resume != Phase.DECISION_LOCKED && resume != Phase.RESERVED)) {
        throw new IOException("resume_phase_invalid");
      }
      int aliasCount = in.readInt();
      if (aliasCount < 1 || aliasCount > CompanionPendingTurn.MAX_REQUEST_ALIASES) throw new IOException("alias_count_invalid");
      CompanionPendingTurn turn = CompanionPendingTurn.begin(new Request(slotId, text(in), revision, inputDigest), turnId, revision);
      for (int i = 1; i < aliasCount; i++) {
        Admission alias = turn.admit(new Request(slotId, text(in), revision, inputDigest), revision);
        if (alias.kind != AdmissionKind.ALIAS) throw new IOException("alias_duplicate");
        turn = alias.turn;
      }
      Phase active = phase == Phase.SUSPENDED ? resume : phase;
      boolean needsDecision = active == Phase.DECISION_LOCKED || active == Phase.RESERVED || active == Phase.COMMITTED;
      if (flag(in) != needsDecision) throw new IOException("decision_phase_mismatch");
      if (needsDecision) turn = turn.lockDecision(new DecisionLock(text(in), in.readLong(), text(in), text(in)));
      boolean needsReservation = active == Phase.RESERVED || active == Phase.COMMITTED;
      if (flag(in) != needsReservation) throw new IOException("reservation_phase_mismatch");
      if (needsReservation) turn = turn.reserve(new Reservation(text(in), text(in), text(in), text(in)));
      if (flag(in) != (phase == Phase.COMMITTED)) throw new IOException("receipt_phase_mismatch");
      if (phase == Phase.COMMITTED) {
        if (in.readLong() != revision + 1) throw new IOException("receipt_revision_invalid");
        turn = turn.markCommitted(revision, turn.decision.digest, turn.reservation.digest, text(in), text(in));
      } else if (phase == Phase.REJECTED) turn = turn.cancel();
      else if (phase == Phase.SUSPENDED) turn = turn.suspend();
      if (in.available() != 0) throw new IOException("record_trailing_data");
      return turn;
    } catch (IllegalArgumentException | IllegalStateException error) {
      throw new IOException("record_contract_invalid", error);
    }
  }

  private static boolean flag(DataInputStream in) throws IOException {
    int flag = in.readUnsignedByte();
    if (flag > 1) throw new IOException("record_flag_invalid");
    return flag == 1;
  }

  private static void text(DataOutputStream out, String value) throws IOException {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    if (bytes.length > MAX_FIELD_BYTES) throw new IOException("field_too_large");
    out.writeInt(bytes.length); out.write(bytes);
  }

  private static String text(DataInputStream in) throws IOException {
    int length = in.readInt();
    if (length < 0 || length > MAX_FIELD_BYTES || length > in.available()) throw new IOException("field_length_invalid");
    byte[] bytes = new byte[length]; in.readFully(bytes);
    return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
  }

  private static byte[] checksum(byte[] bytes) {
    try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
    catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
  }
}
