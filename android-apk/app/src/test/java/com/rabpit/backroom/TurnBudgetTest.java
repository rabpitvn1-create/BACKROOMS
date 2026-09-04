package com.rabpit.backroom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

public class TurnBudgetTest {
  @Test public void capsProviderTimeoutToRemainingTurnBudget() throws Exception {
    AtomicLong now = new AtomicLong(100L);
    TurnBudget budget = TurnBudget.start(now::get, 1_000L);
    now.set(700L);
    assertEquals(400, budget.timeoutMs(5_000, 100));
  }

  @Test public void failsWhenMinimumUsefulWindowIsGone() {
    AtomicLong now = new AtomicLong(0L);
    TurnBudget budget = TurnBudget.start(now::get, 500L);
    now.set(450L);
    assertThrows(SocketTimeoutException.class, () -> budget.timeoutMs(1_000, 100));
  }
}
