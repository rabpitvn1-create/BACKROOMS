package com.rabpit.backroom.core;
import org.junit.Test;
import static org.junit.Assert.*;
public final class CompanionCombatRngBridgeTest {
  @Test public void replayNeverEvaluatesOriginalProducer() throws Exception {
    assertEquals(4, (int) CompanionCombatRngBridge.replay(
        () -> CompanionCombatRngBridge.draw(6, () -> { throw new AssertionError("RNG fallback"); }), bound -> 4));
  }
  @Test public void capturePreservesOriginalAndCleansUpAfterFailure() throws Exception {
    final int[] calls = {0,0};
    assertEquals(3, (int) CompanionCombatRngBridge.capture(
        () -> CompanionCombatRngBridge.draw(6, () -> { calls[0]++; return 3; }),
        (bound,value) -> { assertEquals(6,bound); assertEquals(3,value); calls[1]++; }));
    assertArrayEquals(new int[]{1,1},calls);
    try { CompanionCombatRngBridge.replay(() -> { throw new IllegalStateException("injected"); }, bound -> 0); fail(); }
    catch (IllegalStateException expected) { }
    assertEquals(2,CompanionCombatRngBridge.draw(6,()->2));
  }
  @Test public void nestedScopeAndOutOfRangePlaybackFailClosed() throws Exception {
    try { CompanionCombatRngBridge.replay(() -> CompanionCombatRngBridge.replay(()->0,bound->0),bound->0);fail(); }
    catch (IllegalStateException expected) { }
    try { CompanionCombatRngBridge.replay(() -> CompanionCombatRngBridge.draw(6,()->0),bound->6);fail(); }
    catch (IllegalArgumentException expected) { }
  }
}
