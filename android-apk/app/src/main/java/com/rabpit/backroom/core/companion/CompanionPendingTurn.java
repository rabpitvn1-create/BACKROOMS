package com.rabpit.backroom.core.companion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable S1a pending-turn transitions. Not a repository or gameplay authorization API.
 * The native repository must serialize admission, persist each transition atomically,
 * validate Core/manifest completeness before markCommitted, and publish only after DB commit.
 * No bridge, RNG, provider, legacy storage or Android dependency is connected here.
 */
public final class CompanionPendingTurn {
  public enum Phase { PREPARING, DECISION_LOCKED, RESERVED, SUSPENDED, COMMITTED, REJECTED }
  public enum AdmissionKind {
    PENDING_REPLAY, COMMITTED_REPLAY, REJECTED_REPLAY, ALIAS,
    REQUEST_CONFLICT, SLOT_MISMATCH, STALE_REVISION, BUSY, CLOSED
  }

  public static final class Request {
    public final String slotId;
    public final String requestId;
    public final long expectedRevision;
    public final String inputDigest;

    /** Digest must be computed by the native caller, never accepted from player/model JSON. */
    public Request(String slotId, String requestId, long expectedRevision, String inputDigest) {
      this.slotId = identifier(slotId);
      this.requestId = identifier(requestId);
      if (expectedRevision < 0 || expectedRevision == Long.MAX_VALUE) {
        throw new IllegalArgumentException("revision_out_of_range");
      }
      this.expectedRevision = expectedRevision;
      this.inputDigest = digest(inputDigest);
    }

    /** Exact player input; no normalization or request-ID-dependent digest. */
    public static Request fromPlayerInput(String slotId, String requestId, long revision,
        String actorId, String input) {
      if (!"cao_minh".equals(actorId)) throw new IllegalArgumentException("actor_not_supported");
      Objects.requireNonNull(input, "input");
      return new Request(slotId, requestId, revision, sha256(envelope(
          "companion_interact.v1", actorId, Long.toString(revision), input)));
    }
  }

  public static final class DecisionLock {
    public final String actorId;
    public final long sceneRevision;
    public final String policyVersion;
    public final String canonicalPayload;
    public final String digest;

    /** Payload includes schema, kind, target, duration and evidence references; native validates it. */
    public DecisionLock(String actorId, long sceneRevision, String policyVersion, String canonicalPayload) {
      if (!"cao_minh".equals(actorId)) throw new IllegalArgumentException("actor_not_supported");
      if (sceneRevision < 0) throw new IllegalArgumentException("scene_revision_invalid");
      this.actorId = actorId;
      this.sceneRevision = sceneRevision;
      this.policyVersion = identifier(policyVersion);
      this.canonicalPayload = payload(canonicalPayload);
      this.digest = sha256(envelope("companion_lock.v1", actorId, Long.toString(sceneRevision),
          this.policyVersion, this.canonicalPayload));
    }

    private boolean same(DecisionLock other) {
      return actorId.equals(other.actorId) && sceneRevision == other.sceneRevision
          && policyVersion.equals(other.policyVersion) && canonicalPayload.equals(other.canonicalPayload);
    }
  }

  public static final class Reservation {
    public final String reservationId;
    public final String decisionDigest;
    public final String policyVersion;
    public final String canonicalPayload;
    public final String digest;

    /** The existing native RNG capture supplies the complete tape, including an explicit empty tape. */
    public Reservation(String reservationId, String decisionDigest, String policyVersion, String canonicalPayload) {
      this.reservationId = identifier(reservationId);
      this.decisionDigest = digest(decisionDigest);
      this.policyVersion = identifier(policyVersion);
      this.canonicalPayload = payload(canonicalPayload);
      this.digest = sha256(envelope("companion_reservation.v1", this.reservationId,
          this.decisionDigest, this.policyVersion, this.canonicalPayload));
    }

    private boolean same(Reservation other) {
      return reservationId.equals(other.reservationId) && decisionDigest.equals(other.decisionDigest)
          && policyVersion.equals(other.policyVersion) && canonicalPayload.equals(other.canonicalPayload);
    }
  }

  public static final class Receipt {
    public final long committedRevision;
    public final String manifest;
    public final String finalResult;

    private Receipt(long committedRevision, String manifest, String finalResult) {
      this.committedRevision = committedRevision;
      this.manifest = payload(manifest);
      this.finalResult = payload(finalResult);
    }
  }

  public static final class Admission {
    public final AdmissionKind kind;
    public final CompanionPendingTurn turn;

    private Admission(AdmissionKind kind, CompanionPendingTurn turn) {
      this.kind = kind;
      this.turn = turn;
    }
  }

  public final String slotId;
  public final String turnId;
  public final long expectedRevision;
  public final String inputDigest;
  public final Set<String> requestAliases;
  public final Phase phase;
  public final Phase resumePhase;
  public final DecisionLock decision;
  public final Reservation reservation;
  public final Receipt receipt;

  private CompanionPendingTurn(String slotId, String turnId, long revision, String inputDigest,
      Set<String> aliases, Phase phase, Phase resumePhase, DecisionLock decision,
      Reservation reservation, Receipt receipt) {
    this.slotId = slotId;
    this.turnId = turnId;
    this.expectedRevision = revision;
    this.inputDigest = inputDigest;
    this.requestAliases = Collections.unmodifiableSet(new LinkedHashSet<>(aliases));
    this.phase = phase;
    this.resumePhase = resumePhase;
    this.decision = decision;
    this.reservation = reservation;
    this.receipt = receipt;
  }

  /** Only call when the serialized repository has established there is no active pending turn. */
  public static CompanionPendingTurn begin(Request request, String turnId, long currentRevision) {
    Objects.requireNonNull(request, "request");
    if (request.expectedRevision != currentRevision) throw new IllegalArgumentException("stale_revision");
    Set<String> aliases = new LinkedHashSet<>();
    aliases.add(request.requestId);
    return new CompanionPendingTurn(request.slotId, identifier(turnId), currentRevision,
        request.inputDigest, aliases, Phase.PREPARING, null, null, null, null);
  }

  /** Existing IDs are checked before current revision, so a historical receipt can replay. */
  public Admission admit(Request request, long currentRevision) {
    Objects.requireNonNull(request, "request");
    if (currentRevision < 0) throw new IllegalArgumentException("revision_out_of_range");
    if (!slotId.equals(request.slotId)) return admission(AdmissionKind.SLOT_MISMATCH);
    boolean identical = expectedRevision == request.expectedRevision && inputDigest.equals(request.inputDigest);
    if (requestAliases.contains(request.requestId)) {
      if (!identical) return admission(AdmissionKind.REQUEST_CONFLICT);
      if (phase == Phase.COMMITTED) return admission(currentRevision >= receipt.committedRevision
          ? AdmissionKind.COMMITTED_REPLAY : AdmissionKind.STALE_REVISION);
      if (phase == Phase.REJECTED) return admission(AdmissionKind.REJECTED_REPLAY);
      if (currentRevision != expectedRevision) return admission(AdmissionKind.STALE_REVISION);
      return admission(AdmissionKind.PENDING_REPLAY);
    }
    if (request.expectedRevision != currentRevision || currentRevision != expectedRevision) {
      return admission(AdmissionKind.STALE_REVISION);
    }
    if (phase == Phase.COMMITTED || phase == Phase.REJECTED) return admission(AdmissionKind.CLOSED);
    if (!identical) return admission(AdmissionKind.BUSY);
    Set<String> aliases = new LinkedHashSet<>(requestAliases);
    aliases.add(request.requestId);
    return new Admission(AdmissionKind.ALIAS, new CompanionPendingTurn(slotId, turnId,
        expectedRevision, inputDigest, aliases, phase, resumePhase, decision, reservation, receipt));
  }

  public CompanionPendingTurn lockDecision(DecisionLock lock) {
    Objects.requireNonNull(lock, "lock");
    if (lock.sceneRevision != expectedRevision) throw new IllegalArgumentException("scene_revision_mismatch");
    if (phase == Phase.DECISION_LOCKED && decision.same(lock)) return this;
    requirePhase(Phase.PREPARING);
    return copy(Phase.DECISION_LOCKED, null, lock, null, null);
  }

  public CompanionPendingTurn reserve(Reservation tape) {
    Objects.requireNonNull(tape, "tape");
    if (phase == Phase.RESERVED && reservation.same(tape)) return this;
    requirePhase(Phase.DECISION_LOCKED);
    if (!decision.digest.equals(tape.decisionDigest) || !decision.policyVersion.equals(tape.policyVersion)) {
      throw new IllegalArgumentException("reservation_lock_mismatch");
    }
    return copy(Phase.RESERVED, null, decision, tape, null);
  }

  public CompanionPendingTurn suspend() {
    if (phase == Phase.SUSPENDED) return this;
    if (phase == Phase.COMMITTED || phase == Phase.REJECTED) throw new IllegalStateException("turn_closed");
    return copy(Phase.SUSPENDED, phase, decision, reservation, null);
  }

  public CompanionPendingTurn resume() {
    requirePhase(Phase.SUSPENDED);
    return copy(resumePhase, null, decision, reservation, null);
  }

  /** Cancellation can clear only an unexposed PREPARING turn; after lock it means pause. */
  public CompanionPendingTurn cancel() {
    if (phase == Phase.PREPARING || (phase == Phase.SUSPENDED && resumePhase == Phase.PREPARING)) {
      return copy(Phase.REJECTED, null, null, null, null);
    }
    return suspend();
  }

  /** Call only inside the authorized final DB transaction, never directly from model JSON. */
  public CompanionPendingTurn markCommitted(long currentRevision, String decisionDigest,
      String reservationDigest, String manifest, String finalResult) {
    if (currentRevision != expectedRevision) throw new IllegalArgumentException("stale_revision");
    requirePhase(Phase.RESERVED);
    if (!decision.digest.equals(digest(decisionDigest)) || !reservation.digest.equals(digest(reservationDigest))) {
      throw new IllegalArgumentException("commit_lock_mismatch");
    }
    Receipt result = new Receipt(expectedRevision + 1, manifest, finalResult);
    return copy(Phase.COMMITTED, null, decision, reservation, result);
  }

  private Admission admission(AdmissionKind kind) { return new Admission(kind, this); }

  private CompanionPendingTurn copy(Phase next, Phase resume, DecisionLock lock, Reservation tape, Receipt result) {
    return new CompanionPendingTurn(slotId, turnId, expectedRevision, inputDigest,
        requestAliases, next, resume, lock, tape, result);
  }

  private void requirePhase(Phase required) {
    if (phase != required) throw new IllegalStateException("phase_requires_" + required.name());
  }

  private static String identifier(String value) {
    if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
      throw new IllegalArgumentException("identifier_invalid");
    }
    return value;
  }

  private static String digest(String value) {
    if (value == null || !value.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("digest_invalid");
    return value;
  }

  private static String payload(String value) {
    if (value == null || value.trim().isEmpty() || utf8(value).length > 131072) {
      throw new IllegalArgumentException("payload_invalid");
    }
    return value;
  }

  private static String sha256(String value) {
    try {
      byte[] bytes = MessageDigest.getInstance("SHA-256").digest(utf8(value));
      char[] hex = "0123456789abcdef".toCharArray();
      StringBuilder out = new StringBuilder(64);
      for (byte b : bytes) out.append(hex[(b & 255) >>> 4]).append(hex[b & 15]);
      return out.toString();
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 unavailable", impossible);
    }
  }

  private static String envelope(String... fields) {
    StringBuilder out = new StringBuilder();
    for (String field : fields) out.append(utf8(field).length)
        .append(':').append(field);
    return out.toString();
  }

  private static byte[] utf8(String value) {
    // Java's replacement encoding would make an unpaired surrogate collide with '?'.
    for (int index = 0; index < value.length(); index++) {
      char c = value.charAt(index);
      if (Character.isHighSurrogate(c)) {
        if (++index >= value.length() || !Character.isLowSurrogate(value.charAt(index))) {
          throw new IllegalArgumentException("unicode_invalid");
        }
      } else if (Character.isLowSurrogate(c)) {
        throw new IllegalArgumentException("unicode_invalid");
      }
    }
    return value.getBytes(StandardCharsets.UTF_8);
  }
}
