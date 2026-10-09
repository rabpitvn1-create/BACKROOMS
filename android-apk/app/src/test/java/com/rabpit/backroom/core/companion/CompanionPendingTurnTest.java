package com.rabpit.backroom.core.companion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class CompanionPendingTurnTest {
  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> cases() {
    Collection<Object[]> cases = new ArrayList<>();
    for (Map.Entry<String, Runnable> entry : CompanionPendingTurnFixtures.cases().entrySet()) {
      cases.add(new Object[] {entry.getKey(), entry.getValue()});
    }
    return cases;
  }

  private final Runnable fixture;
  public CompanionPendingTurnTest(String name, Runnable fixture) { this.fixture = fixture; }
  @Test public void productionPendingContract() { fixture.run(); }
}
