package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.GameStateCodec
import com.rabpit.backroom.core.KAI_ID
import com.rabpit.backroom.core.progression.FeaturedJourneyRoutes
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.StandardCharsets

class CompanionNewGameBootstrapTest {
  @Test fun freshCompanionSeedUsesCoreOwnedLevelZeroAndCorrectIdentity() {
    val first = CompanionNewGameBootstrap.seed()
    val second = CompanionNewGameBootstrap.seed()
    assertArrayEquals(first, second)
    val state = GameStateCodec.decode(String(first, StandardCharsets.UTF_8))
    assertEquals("cao_minh", KAI_ID)
    assertEquals("Cao Minh", state.characters.getValue(KAI_ID).name)
    assertEquals(KAI_ID, state.party.leaderId)
    assertEquals(1, state.party.memberIds.size)
    val node = FeaturedJourneyRoutes.nodeIdAt("level-0")
    assertNotNull(node)
    assertEquals(node, state.world["worldNodeId"])
    assertEquals("level-0", state.world["journeyStopKey"])
    val level = JSONObject(state.world.getValue("levelJson"))
    assertEquals(0, level.getInt("number"))
    assertEquals(node, level.getString("nodeId"))
    val streak = JSONObject(state.world.getValue("flagsJson")).getJSONObject("exploration")
    assertEquals(0, streak.getInt("exitStreak"))
    assertEquals("level-0", streak.getString("exitStreakNode"))
    assertTrue(state.turn.completedTurnIds.isEmpty())
    assertNull(state.turn.pending)
  }
}
