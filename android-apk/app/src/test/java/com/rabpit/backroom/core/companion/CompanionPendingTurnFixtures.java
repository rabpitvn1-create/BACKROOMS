package com.rabpit.backroom.core.companion;

import java.util.LinkedHashMap;
import java.util.Map;

import com.rabpit.backroom.core.companion.CompanionPendingTurn.AdmissionKind;
import com.rabpit.backroom.core.companion.CompanionPendingTurn.DecisionLock;
import com.rabpit.backroom.core.companion.CompanionPendingTurn.Phase;
import com.rabpit.backroom.core.companion.CompanionPendingTurn.Request;
import com.rabpit.backroom.core.companion.CompanionPendingTurn.Reservation;

/** The same production class is exercised by both host javac and Android JUnit. */
public final class CompanionPendingTurnFixtures {
  private CompanionPendingTurnFixtures() {}

  public static Map<String, Runnable> cases() {
    Map<String, Runnable> cases = new LinkedHashMap<>();
    cases.put("aliases retain identity and immutable original", CompanionPendingTurnFixtures::aliases);
    cases.put("changed input and revision cannot reuse request", CompanionPendingTurnFixtures::requestConflict);
    cases.put("different input cannot evict pending", CompanionPendingTurnFixtures::busy);
    cases.put("cross-slot and stale requests fail", CompanionPendingTurnFixtures::isolation);
    cases.put("pre-lock cancellation closes without receipt", CompanionPendingTurnFixtures::prelockCancel);
    cases.put("post-lock cancellation retains decision", CompanionPendingTurnFixtures::lockedCancel);
    cases.put("post-reservation cancellation retains exact tape", CompanionPendingTurnFixtures::reservedCancel);
    cases.put("repair cannot change locked decision", CompanionPendingTurnFixtures::changedDecision);
    cases.put("reservation binding and policy checked", CompanionPendingTurnFixtures::reservationBinding);
    cases.put("reserved tape cannot be replaced", CompanionPendingTurnFixtures::replaceReservation);
    cases.put("commit guards revision and both digests", CompanionPendingTurnFixtures::commitGuards);
    cases.put("historical alias replays exact committed receipt", CompanionPendingTurnFixtures::receiptReplay);
    cases.put("terminal states cannot rewind or commit twice", CompanionPendingTurnFixtures::closedStates);
    cases.put("scene actor and revision boundaries checked", CompanionPendingTurnFixtures::boundaries);
    cases.put("native digest preserves input and ignores request id", CompanionPendingTurnFixtures::inputDigests);
    cases.put("lock and tape digests include identity and version", CompanionPendingTurnFixtures::lockDigests);
    cases.put("pause has no hidden transition or lock bypass", CompanionPendingTurnFixtures::suspendedGuards);
    cases.put("malformed digests ids and oversized payload reject", CompanionPendingTurnFixtures::malformed);
    cases.put("malformed Unicode cannot alias a replacement character", CompanionPendingTurnFixtures::invalidUnicode);
    return cases;
  }

  public static void main(String[] args) {
    for (Map.Entry<String, Runnable> test : cases().entrySet()) {
      test.getValue().run();
      System.out.println("PASS: " + test.getKey());
    }
    System.out.println(cases().size() + " production pending fixtures passed");
  }

  private static Request request(String id, long revision, String input) {
    return Request.fromPlayerInput("slot-A", id, revision, "cao_minh", input);
  }
  private static CompanionPendingTurn preparing() {
    return CompanionPendingTurn.begin(request("request-1", 7, "Hãy kiểm tra hành lang."), "turn-8", 7);
  }
  private static DecisionLock decision(long revision, String payload) {
    return new DecisionLock("cao_minh", revision, "policy-v1", payload);
  }
  private static CompanionPendingTurn locked() { return preparing().lockDecision(decision(7, "MOVE:corridor")); }
  private static Reservation tape(CompanionPendingTurn turn, String id, String payload) {
    return new Reservation(id, turn.decision.digest, "policy-v1", payload);
  }
  private static CompanionPendingTurn reserved() {
    CompanionPendingTurn turn = locked();
    return turn.reserve(tape(turn, "roll-8", "exit:2:0;route:stop-2"));
  }
  private static CompanionPendingTurn committed(CompanionPendingTurn turn) {
    return turn.markCommitted(7, turn.decision.digest, turn.reservation.digest, "manifest-8", "saved-reply-8");
  }
  private static void check(boolean condition) { if (!condition) throw new AssertionError("check failed"); }
  private static void rejects(Runnable operation) {
    try { operation.run(); }
    catch (IllegalArgumentException | IllegalStateException expected) { return; }
    throw new AssertionError("expected fail-closed rejection");
  }
  private static void aliases() {
    CompanionPendingTurn original = reserved();
    CompanionPendingTurn.Admission alias = original.admit(request("request-2", 7, "Hãy kiểm tra hành lang."), 7);
    check(alias.kind == AdmissionKind.ALIAS && alias.turn.requestAliases.size() == 2);
    check(original.requestAliases.size() == 1 && alias.turn.turnId.equals(original.turnId));
    check(alias.turn.reservation == original.reservation && alias.turn.decision == original.decision);
    check(alias.turn.admit(request("request-2", 7, "Hãy kiểm tra hành lang."), 7).kind == AdmissionKind.PENDING_REPLAY);
    try { alias.turn.requestAliases.clear(); throw new AssertionError("aliases mutable"); }
    catch (UnsupportedOperationException expected) { /* immutable */ }
  }
  private static void requestConflict() {
    CompanionPendingTurn t = preparing();
    check(t.admit(request("request-1", 7, "changed"), 7).kind == AdmissionKind.REQUEST_CONFLICT);
    check(t.admit(request("request-1", 8, "Hãy kiểm tra hành lang."), 8).kind == AdmissionKind.REQUEST_CONFLICT);
  }
  private static void busy() {
    CompanionPendingTurn t = reserved();
    CompanionPendingTurn.Admission denied = t.admit(request("new-id", 7, "new input"), 7);
    check(denied.kind == AdmissionKind.BUSY && denied.turn == t);
  }
  private static void isolation() {
    CompanionPendingTurn t = preparing();
    Request other = Request.fromPlayerInput("slot-B", "request-1", 7, "cao_minh", "Hãy kiểm tra hành lang.");
    check(t.admit(other, 7).kind == AdmissionKind.SLOT_MISMATCH);
    check(t.admit(request("new-id", 6, "Hãy kiểm tra hành lang."), 7).kind == AdmissionKind.STALE_REVISION);
    check(t.admit(request("request-1", 7, "Hãy kiểm tra hành lang."), 8).kind == AdmissionKind.STALE_REVISION);
    rejects(() -> CompanionPendingTurn.begin(request("r", 7, "input"), "t", 8));
  }
  private static void prelockCancel() {
    for (CompanionPendingTurn t : new CompanionPendingTurn[] {preparing(), preparing().suspend()}) {
      CompanionPendingTurn cancelled = t.cancel();
      check(cancelled.phase == Phase.REJECTED && cancelled.receipt == null && cancelled.expectedRevision == 7);
      check(cancelled.admit(request("request-1", 7, "Hãy kiểm tra hành lang."), 7).kind == AdmissionKind.REJECTED_REPLAY);
      check(cancelled.admit(request("new-id", 7, "Hãy kiểm tra hành lang."), 7).kind == AdmissionKind.CLOSED);
    }
  }
  private static void lockedCancel() {
    CompanionPendingTurn t = locked();
    CompanionPendingTurn paused = t.cancel();
    check(paused.phase == Phase.SUSPENDED && paused.resumePhase == Phase.DECISION_LOCKED);
    check(paused.decision == t.decision && paused.reservation == null);
    check(paused.cancel() == paused && paused.resume().phase == Phase.DECISION_LOCKED);
  }
  private static void reservedCancel() {
    CompanionPendingTurn t = reserved();
    CompanionPendingTurn paused = t.cancel();
    check(paused.resumePhase == Phase.RESERVED && paused.reservation == t.reservation);
    CompanionPendingTurn resumed = paused.resume();
    check(resumed.phase == Phase.RESERVED && resumed.reservation == t.reservation);
    check(committed(resumed).receipt.committedRevision == 8);
  }
  private static void changedDecision() {
    CompanionPendingTurn t = locked();
    check(t.lockDecision(decision(7, "MOVE:corridor")) == t);
    rejects(() -> t.lockDecision(decision(7, "SEARCH:box")));
    rejects(() -> reserved().lockDecision(decision(7, "MOVE:corridor")));
  }
  private static void reservationBinding() {
    CompanionPendingTurn t = locked();
    DecisionLock changed = decision(7, "WAIT");
    rejects(() -> t.reserve(new Reservation("r", changed.digest, "policy-v1", "0")));
    rejects(() -> t.reserve(new Reservation("r", t.decision.digest, "policy-v2", "0")));
    rejects(() -> preparing().reserve(tape(t, "r", "0")));
    check(t.reserve(tape(t, "empty", "[]")).phase == Phase.RESERVED);
  }
  private static void replaceReservation() {
    CompanionPendingTurn t = reserved();
    check(t.reserve(tape(t, "roll-8", "exit:2:0;route:stop-2")) == t);
    rejects(() -> t.reserve(tape(t, "roll-8", "exit:2:1")));
    rejects(() -> t.reserve(tape(t, "new-id", "exit:2:0;route:stop-2")));
  }
  private static void commitGuards() {
    CompanionPendingTurn t = reserved();
    rejects(() -> t.markCommitted(8, t.decision.digest, t.reservation.digest, "m", "r"));
    rejects(() -> t.markCommitted(7, decision(7, "WAIT").digest, t.reservation.digest, "m", "r"));
    rejects(() -> t.markCommitted(7, t.decision.digest, tape(t, "changed", "[]").digest, "m", "r"));
    check(t.phase == Phase.RESERVED && t.receipt == null);
    rejects(() -> locked().markCommitted(7, t.decision.digest, t.reservation.digest, "m", "r"));
  }
  private static void receiptReplay() {
    CompanionPendingTurn t = reserved().admit(request("alias", 7, "Hãy kiểm tra hành lang."), 7).turn;
    CompanionPendingTurn done = committed(t);
    CompanionPendingTurn.Admission replay = done.admit(request("alias", 7, "Hãy kiểm tra hành lang."), 27);
    check(replay.kind == AdmissionKind.COMMITTED_REPLAY && replay.turn.receipt == done.receipt);
    check(replay.turn.receipt.finalResult.equals("saved-reply-8") && replay.turn.receipt.committedRevision == 8);
    check(done.admit(request("alias", 7, "Hãy kiểm tra hành lang."), 7).kind == AdmissionKind.STALE_REVISION);
    check(done.admit(request("unknown", 7, "Hãy kiểm tra hành lang."), 8).kind == AdmissionKind.STALE_REVISION);
  }
  private static void closedStates() {
    CompanionPendingTurn t = reserved();
    CompanionPendingTurn done = committed(t);
    rejects(done::resume); rejects(done::cancel); rejects(done::suspend);
    rejects(() -> committed(done));
    CompanionPendingTurn rejected = preparing().cancel();
    rejects(() -> rejected.lockDecision(decision(7, "MOVE:corridor")));
    rejects(rejected::resume); rejects(rejected::suspend);
  }
  private static void boundaries() {
    rejects(() -> preparing().lockDecision(decision(8, "MOVE:corridor")));
    rejects(() -> new DecisionLock("player_presence", 7, "p", "{}"));
    rejects(() -> request("r", Long.MAX_VALUE, "input"));
    rejects(() -> request("r", -1, "input"));
    CompanionPendingTurn t = CompanionPendingTurn.begin(request("r", Long.MAX_VALUE - 1, "input"), "t", Long.MAX_VALUE - 1);
    t = t.lockDecision(decision(Long.MAX_VALUE - 1, "WAIT"));
    t = t.reserve(tape(t, "r", "[]"));
    check(t.markCommitted(Long.MAX_VALUE - 1, t.decision.digest, t.reservation.digest, "m", "r").receipt.committedRevision == Long.MAX_VALUE);
  }
  private static void inputDigests() {
    Request r = request("r", 7, "🙂 é");
    check(r.inputDigest.equals("88c7d9dfc1c9f70739fe4f97a5c0083cfcf6e896f6e4a68325224c6e2591bafb"));
    check(r.inputDigest.equals(request("alias", 7, "🙂 é").inputDigest));
    check(!r.inputDigest.equals(request("r", 8, "🙂 é").inputDigest));
    check(!r.inputDigest.equals(request("r", 7, "🙂 e\u0301").inputDigest));
    check(!r.inputDigest.equals(request("r", 7, "🙂 é ").inputDigest));
  }
  private static void lockDigests() {
    DecisionLock a = decision(7, "MOVE:corridor");
    check(!a.digest.equals(decision(8, "MOVE:corridor").digest));
    check(!a.digest.equals(new DecisionLock("cao_minh", 7, "policy-v2", "MOVE:corridor").digest));
    Reservation r = new Reservation("r", a.digest, "policy-v1", "[]");
    check(!r.digest.equals(new Reservation("other", a.digest, "policy-v1", "[]").digest));
    check(r.digest.equals(new Reservation("r", a.digest, "policy-v1", "[]").digest));
  }
  private static void suspendedGuards() {
    CompanionPendingTurn t = reserved().suspend();
    check(t.suspend() == t);
    rejects(() -> t.reserve(t.reservation));
    rejects(() -> t.lockDecision(t.decision));
    rejects(() -> committed(t));
    check(t.admit(request("new-alias", 7, "Hãy kiểm tra hành lang."), 7).turn.phase == Phase.SUSPENDED);
    rejects(preparing()::resume);
  }
  private static void malformed() {
    rejects(() -> new Request("../slot", "r", 7, "bad"));
    rejects(() -> new Request("s", "r", 7, "bad"));
    rejects(() -> decision(7, " "));
    rejects(() -> decision(7, "🙂".repeat(32769)));
    CompanionPendingTurn t = reserved();
    rejects(() -> t.markCommitted(7, t.decision.digest, t.reservation.digest, " ", "r"));
  }
  private static void invalidUnicode() {
    rejects(() -> request("r", 7, "\uD800"));
    rejects(() -> request("r", 7, "\uDC00"));
    rejects(() -> request("r", 7, "\uD800x"));
    rejects(() -> decision(7, "\uD800"));
    check(request("r", 7, "?").inputDigest.length() == 64);
  }
}
