package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CombatTotalTurnIsolationTest {
  @Test fun combatResolutionAdvancesCombatCounterWithoutAdvancingGlobalTurn() {
    val initial = GameState.initial()
    val globalTurnBefore = initial.turn.currentTurnId
    val combat = CombatRuntime.start(initial, "hound")
    val combatCounterBefore = CombatRuntime.active(combat)!!.eventCounter

    val resolution = CombatRuntime.resolve(combat, "EXECUTE", "Né tránh")

    assertTrue(resolution.handled)
    assertEquals(globalTurnBefore, resolution.state.turn.currentTurnId)
    val activeAfter = CombatRuntime.active(resolution.state)
    assertTrue("Combat must remain active after the first evade", activeAfter != null)
    assertEquals(combatCounterBefore + 1, activeAfter!!.eventCounter)
  }

  @Test fun facadeFreezesLegacyTotalTurnOnlyInsideCombat() {
    val source = File("src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt").readText()
    val combatStart = source.indexOf("fun processCombat(legacyStateJson: String, actionKind: String, action: String)")
    val combatEnd = source.indexOf("private fun loadOrMigrate", combatStart)
    assertTrue("Generated processCombat is missing", combatStart >= 0 && combatEnd > combatStart)

    val combatBlock = source.substring(combatStart, combatEnd)
    assertTrue(combatBlock.contains("val output = syncLegacy(legacy, next, incrementTurn = false)"))
    assertFalse(combatBlock.contains("syncLegacy(legacy, next, incrementTurn = true)"))
    assertTrue("Combat must still advance subjective time", combatBlock.contains("reason = \"combat_action\""))

    val ruleStart = source.indexOf("fun processRule(legacyStateJson: String, action: String)")
    assertTrue("Generated processRule is missing", ruleStart >= 0 && ruleStart < combatStart)
    val nonCombatBlock = source.substring(ruleStart, combatStart)
    assertTrue(
      "Normal world actions must keep advancing the global turn",
      nonCombatBlock.contains("syncLegacy(legacy, committed.state, incrementTurn = true)")
    )
  }
}
