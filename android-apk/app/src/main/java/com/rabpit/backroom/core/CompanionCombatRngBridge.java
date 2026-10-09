package com.rabpit.backroom.core;

import java.util.concurrent.Callable;
import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;

/** Thread-confined capture/replay seam around the EXISTING scoped CombatChoice producer. */
public final class CompanionCombatRngBridge {
  public interface Recorder { void record(int bound, int value); }
  private static final ThreadLocal<Session> CURRENT = new ThreadLocal<>();
  private static final class Session {
    final Recorder recorder;
    final IntUnaryOperator replay;
    Session(Recorder recorder, IntUnaryOperator replay) { this.recorder = recorder; this.replay = replay; }
  }
  private CompanionCombatRngBridge() {}
  public static <T> T capture(Callable<T> work, Recorder recorder) throws Exception {
    return run(work, new Session(java.util.Objects.requireNonNull(recorder), null));
  }
  public static <T> T replay(Callable<T> work, IntUnaryOperator playback) throws Exception {
    return run(work, new Session(null, java.util.Objects.requireNonNull(playback)));
  }
  private static <T> T run(Callable<T> work, Session session) throws Exception {
    if (CURRENT.get() != null) throw new IllegalStateException("nested_combat_capture");
    CURRENT.set(session);
    try { return work.call(); } finally { CURRENT.remove(); }
  }
  // The producer is lazy: replay cannot instantiate/consume the legacy TurnRng.
  static int draw(int bound, IntSupplier originalProducer) {
    if (bound <= 0) throw new IllegalArgumentException("combat_bound_invalid");
    Session session = CURRENT.get();
    if (session == null) return originalProducer.getAsInt();
    int value = session.replay != null ? session.replay.applyAsInt(bound) : originalProducer.getAsInt();
    if (value < 0 || value >= bound) throw new IllegalArgumentException("combat_draw_invalid");
    if (session.recorder != null) session.recorder.record(bound, value);
    return value;
  }
}
