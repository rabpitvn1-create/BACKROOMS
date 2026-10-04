package com.rabpit.backroom.core;

import java.util.Arrays;
import java.util.Calendar;
import java.util.TimeZone;
import org.junit.Test;
import static org.junit.Assert.*;

public class NarrationProviderSchedulerTest {
  private boolean[] all() { boolean[] result = new boolean[NarrationProviderScheduler.SOURCE_COUNT]; Arrays.fill(result, true); return result; }
  private boolean[] geminiOnly() { boolean[] result = all(); for (int i = 5; i < NarrationProviderScheduler.SOURCE_COUNT; i++) result[i] = false; return result; }
  private boolean[] active() { boolean[] result = all(); result[NarrationProviderScheduler.LUNA] = false; return result; }
  private boolean[] fallback() { boolean[] result = active(); result[NarrationProviderScheduler.GEHIHI] = false; return result; }
  private boolean[] none() { return new boolean[NarrationProviderScheduler.SOURCE_COUNT]; }

  @Test public void backgroundDistributesAcrossFiveAvailableGeminiSources() {
    NarrationProviderScheduler scheduler = new NarrationProviderScheduler();
    for (int key = 0; key < 5; key++) {
      int source = scheduler.acquire(true, false, geminiOnly(), none(), 0, 0);
      assertEquals(key, source);
      scheduler.succeeded(source, true, 500);
    }
    assertEquals(0, scheduler.acquire(true, false, geminiOnly(), none(), 0, 0));
  }
  @Test public void limitedKeyRestsWithoutStoppingOtherProjects() {
    NarrationProviderScheduler scheduler = new NarrationProviderScheduler();
    int first = scheduler.acquire(true, false, geminiOnly(), none(), 0, 0);
    scheduler.failed(first, true, 429, 0, 60_000, 0, 0);
    for (int key = 1; key < 5; key++) {
      int source = scheduler.acquire(true, false, geminiOnly(), none(), 0, 1);
      assertEquals(key, source);
      scheduler.succeeded(source, true, 500);
    }
    assertEquals(1, scheduler.acquire(true, false, geminiOnly(), none(), 0, 2));
  }
  @Test public void foregroundAndEmergencySkipFiveKeyChainAndBusyBackgroundSource() {
    NarrationProviderScheduler scheduler = new NarrationProviderScheduler();
    assertEquals(NarrationProviderScheduler.GEHIHI, scheduler.acquire(true, false, all(), none(), 0, 0));
    assertEquals(NarrationProviderScheduler.GEHIHI, scheduler.acquire(false, false, all(), none(), 0, 0));
    assertEquals(NarrationProviderScheduler.HAKU, scheduler.acquire(true, true, active(), none(), 0, 0));
    assertEquals(NarrationProviderScheduler.SOL, scheduler.acquire(false, false, active(), none(), 1, 0));
  }
  @Test public void twoGeminiFailuresReachHakuAndOverloadRestsWholeModel() {
    NarrationProviderScheduler scheduler = new NarrationProviderScheduler();
    boolean[] tried = none();
    for (int attempts = 0; attempts < 2; attempts++) {
      int source = scheduler.acquire(true, false, geminiOnly(), tried, attempts, 0);
      assertTrue(source >= 0 && source < 5);
      tried[source] = true;
      scheduler.failed(source, true, 503, 0, 0, 0, 0);
    }
    assertEquals(NarrationProviderScheduler.HAKU, scheduler.acquire(true, false, fallback(), tried, 2, 0));
    boolean[] geminiOnly = all();
    for (int i = 5; i < NarrationProviderScheduler.SOURCE_COUNT; i++) geminiOnly[i] = false;
    assertEquals(-1, scheduler.acquire(true, false, geminiOnly, none(), 0, 1));
    assertTrue(scheduler.acquire(true, false, geminiOnly, none(), 0, 31_000) >= 0);
  }
  @Test public void dailyQuotaAndInvalidCredentialsDoNotHotLoop() {
    NarrationProviderScheduler scheduler = new NarrationProviderScheduler();
    boolean[] configured = none(); configured[0] = true;
    assertEquals(0, scheduler.acquire(true, false, configured, none(), 0, 0));
    scheduler.failed(0, true, 429, 0, 1000, 3_600_000, 100);
    assertEquals(-1, scheduler.acquire(true, false, configured, none(), 0, 100_000));
    assertEquals(0, scheduler.acquire(true, false, configured, none(), 0, 3_600_101));
    scheduler.failed(0, true, 401, 3_600_101, 0, 0, 0);
    assertEquals(-1, scheduler.acquire(true, false, configured, none(), 0, Long.MAX_VALUE / 2));
  }
  @Test public void gehihiWinsNormalBackgroundAndHasReservedForegroundLane() {
    NarrationProviderScheduler scheduler = new NarrationProviderScheduler();
    assertEquals(NarrationProviderScheduler.GEHIHI, scheduler.acquire(true, false, all(), none(), 0, 0));
    assertEquals(NarrationProviderScheduler.GEHIHI, scheduler.acquire(false, false, all(), none(), 0, 0));
    assertEquals(NarrationProviderScheduler.HAKU, scheduler.acquire(true, false, all(), none(), 0, 0));
    scheduler.failed(NarrationProviderScheduler.GEHIHI, true, 503, 0, 0, 0, 0);
    assertEquals(NarrationProviderScheduler.SOL, scheduler.acquire(false, false, active(), none(), 0, 1));
  }
  @Test public void concurrentOldGehihiSuccessCannotClearNewRateLimit() {
    NarrationProviderScheduler scheduler = new NarrationProviderScheduler();
    scheduler.acquire(true, false, all(), none(), 0, 0);
    scheduler.acquire(false, false, all(), none(), 0, 0);
    scheduler.failed(NarrationProviderScheduler.GEHIHI, true, 429, 1, 60_000, 0, 0);
    scheduler.succeeded(NarrationProviderScheduler.GEHIHI, false, 100);
    boolean[] configured = none(); configured[NarrationProviderScheduler.GEHIHI] = true;
    assertEquals(-1, scheduler.acquire(false, false, configured, none(), 0, 100));
  }
  @Test public void measuredLatencyCanPromoteHealthyHakuAboveSlowGemini() {
    NarrationProviderScheduler scheduler = new NarrationProviderScheduler();
    for (int i = 0; i < 5; i++) scheduler.succeeded(i, true, 15_000);
    scheduler.succeeded(NarrationProviderScheduler.HAKU, true, 500);
    assertEquals(NarrationProviderScheduler.HAKU, scheduler.acquire(true, false, fallback(), none(), 0, 0));
  }
  @Test public void absentOrCoolingGehihiUsesHakuWithoutCoolingOtherAccounts() {
    NarrationProviderScheduler scheduler = new NarrationProviderScheduler();
    assertEquals(NarrationProviderScheduler.HAKU, scheduler.acquire(false, false, fallback(), none(), 0, 0));
    scheduler.succeeded(NarrationProviderScheduler.HAKU, false, 2000);
    assertEquals(NarrationProviderScheduler.GEHIHI, scheduler.acquire(false, false, active(), none(), 0, 0));
    scheduler.failed(NarrationProviderScheduler.GEHIHI, false, 429, 0, 60_000, 0, 0);
    assertEquals(NarrationProviderScheduler.HAKU, scheduler.acquire(false, false, active(), none(), 0, 1));
  }
  @Test public void slowGehihiCanYieldToHealthyHaku() {
    NarrationProviderScheduler scheduler = new NarrationProviderScheduler();
    scheduler.succeeded(NarrationProviderScheduler.GEHIHI, true, 15_000);
    scheduler.succeeded(NarrationProviderScheduler.HAKU, true, 500);
    assertEquals(NarrationProviderScheduler.HAKU, scheduler.acquire(true, false, active(), none(), 0, 0));
  }
  @Test public void dailyResetUsesPacificMidnightAcrossDaylightSaving() {
    Calendar now = Calendar.getInstance(TimeZone.getTimeZone("America/Los_Angeles"));
    now.clear(); now.set(2026, Calendar.OCTOBER, 4, 23, 30, 0);
    assertEquals(30 * 60_000L, NarrationProviderScheduler.nextPacificDailyResetDelay(now.getTimeInMillis()));
    now.clear(); now.set(2026, Calendar.NOVEMBER, 1, 0, 0, 0);
    assertEquals(25 * 60 * 60_000L, NarrationProviderScheduler.nextPacificDailyResetDelay(now.getTimeInMillis()));
  }
}
