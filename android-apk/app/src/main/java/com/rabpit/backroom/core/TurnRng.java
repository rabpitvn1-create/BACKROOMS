package com.rabpit.backroom.core;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.EnumMap;

/** Deterministic, scope-isolated mechanical RNG for one pre-commit turn attempt. */
final class TurnRng {
  enum Scope {
    PLAYER_ACTION,
    CANDIDATE_SELECTION,
    WORLD_REACTION,
    CASCADE,
    COMBAT
  }

  private final String turnId;
  private final int preTurnStateVersion;
  private final String canonVersion;
  private final String rngSchemaVersion;
  private final EnumMap<Scope, Integer> counters = new EnumMap<>(Scope.class);

  TurnRng(String turnId, int preTurnStateVersion, String canonVersion, String rngSchemaVersion) {
    if (turnId == null || turnId.trim().isEmpty()) throw new IllegalArgumentException("turnId is required");
    this.turnId = turnId;
    this.preTurnStateVersion = Math.max(0, preTurnStateVersion);
    this.canonVersion = canonVersion == null ? "" : canonVersion;
    this.rngSchemaVersion = rngSchemaVersion == null ? "" : rngSchemaVersion;
    for (Scope scope : Scope.values()) counters.put(scope, 0);
  }

  int nextInt(Scope scope, int bound) {
    if (scope == null) throw new IllegalArgumentException("scope is required");
    if (bound <= 0) throw new IllegalArgumentException("bound must be positive");
    long value = nextPositiveLong(scope);
    return (int)(value % bound);
  }

  double nextUnit(Scope scope) {
    long value = nextPositiveLong(scope);
    return (value >>> 10) * 0x1.0p-53;
  }

  int drawsUsed(Scope scope) {
    Integer value = counters.get(scope);
    return value == null ? 0 : value;
  }

  void resume(Scope scope, int drawsUsed) {
    if (scope == null) throw new IllegalArgumentException("scope is required");
    if (drawsUsed < 0) throw new IllegalArgumentException("drawsUsed must be non-negative");
    int current = counters.get(scope);
    if (current != 0 && current != drawsUsed) {
      throw new IllegalStateException("Cannot resume an already-consumed RNG scope");
    }
    counters.put(scope, drawsUsed);
  }

  String drawKey(Scope scope, int drawSeq) {
    return turnId + "|" + scope.name() + "|" + drawSeq + "|"
        + preTurnStateVersion + "|" + canonVersion + "|" + rngSchemaVersion;
  }

  private long nextPositiveLong(Scope scope) {
    int drawSeq = counters.get(scope);
    counters.put(scope, drawSeq + 1);
    byte[] digest = sha256(drawKey(scope, drawSeq));
    long value = ByteBuffer.wrap(digest, 0, Long.BYTES).getLong();
    return value & Long.MAX_VALUE;
  }

  private static byte[] sha256(String value) {
    try {
      return MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
