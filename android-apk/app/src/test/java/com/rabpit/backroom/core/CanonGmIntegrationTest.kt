package com.rabpit.backroom.core

import com.rabpit.backroom.core.knowledge.KnowledgeContextEngine
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Canon → GM regression tests for post-Milestone encounter flow.
 *
 * Verifies that the KnowledgeContextEngine (GM prompt builder) correctly:
 * - Includes Canon cards for Core-confirmed NPC encounters
 * - Excludes cards for unconfirmed, name-only, or departed NPCs
 * - Preserves mandatory voice cards under budget pressure
 * - Does not leak Canon for legacy characters (An Nhien)
 *
 * These tests verify the wiring that Agents 1/2 built: Core confirms encounters,
 * GM only narrates. The Builder is tested directly with state JSON.
 */
class CanonGmIntegrationTest {
  private fun packet(state: String = "{}", action: String = "", rolls: String = "{}"): String {
    val file = listOf(
      File("src/main/assets/knowledge/knowledge_db.json"),
      File("app/src/main/assets/knowledge/knowledge_db.json")
    ).first { it.isFile }
    return KnowledgeContextEngine.Builder(
      KnowledgeContextEngine.parseDatabase(file.readText()), JSONObject(state), action, JSONObject(rolls)
    ).build()
  }

  // --- Test 1: Forced Lucia first meeting, Level 0, not in Party ---

  @Test fun `forced Lucia first meeting includes authored card and meeting event`() {
    // Core confirms Lucia first meeting via a pending encounter (NOT a Milestone).
    // Lucia is NOT in the Party; a first meeting never implies recruitment.
    val state = JSONObject()
      .put("level", JSONObject().put("number", 0).put("id", "0"))
      .put("party", JSONArray().apply {
        put(JSONObject().put("id", "cao_minh").put("presence", "ACTIVE"))
      })
      .put("pendingNpcEncounters", JSONArray().apply {
        put(JSONObject().put("characterId", "lucia").put("flagKey", "lucia"))
      })
      .toString()
    val result = packet(state, action = "khám phá khu vực xung quanh")

    // GM receives the encounter: Lucia's authored card is in the packet.
    assertTrue("CHAR.LUCIA.AUTHORED", result.contains("<CHAR.LUCIA.AUTHORED>"))
    // Present actors includes lucia.
    assertTrue("present actors", result.contains("lucia"))
    // Core encounter grants presence to retriever, not Party membership.
    assertTrue("Core encounter presence", result.contains("Present actors: cao_minh, lucia"))
    assertFalse("Never project encounter as Party membership", JSONObject(state).getJSONArray("party").toString().contains("lucia"))
  }

  @Test fun `Lucia first meeting does not leak other NPC cards`() {
    val state = JSONObject()
      .put("level", JSONObject().put("number", 0).put("id", "0"))
      .put("pendingNpcEncounters", JSONArray().apply {
        put(JSONObject().put("characterId", "lucia").put("flagKey", "lucia"))
      })
      .toString()
    val result = packet(state)

    assertTrue(result.contains("<CHAR.LUCIA.AUTHORED>"))
    // Luc Tram cards must NOT appear — different character, different conditions.
    assertFalse("LUC_TRAM leak", result.contains("<CHAR.LUC_TRAM."))
    // Iris/Syvial cards must NOT appear.
    assertFalse("IRIS leak", result.contains("<CHAR.IRIS."))
    assertFalse("SYVIAL leak", result.contains("<CHAR.SYVIAL."))
  }

  // --- R07: Four newer Followers must have the same Core-grounded
  // first-meeting narration context as Lucia, without leaking absent actors. ---

  @Test fun `four new first meetings load authored identity clothing weapon and role only when confirmed`() {
    val cases = listOf(
      Triple("catherine", "CHAR.CATHERINE.AUTHORED", "Vesper Nocturne"),
      Triple("janet", "CHAR.JANET.AUTHORED", "Bayou Belle"),
      Triple("nguyen_quoc_nam", "CHAR.NGUYEN_QUOC_NAM.AUTHORED", "Hành Lang 01"),
      Triple("hua_tieu_lan", "CHAR.HUA_TIEU_LAN.AUTHORED", "Trường Xuân")
    )
    for ((id, card, weapon) in cases) {
      val state = JSONObject()
        .put("level", JSONObject().put("number", 4).put("id", "4"))
        .put("party", JSONArray().put(JSONObject().put("id", "cao_minh").put("presence", "ACTIVE")))
        .put("pendingNpcEncounters", JSONArray().put(
          JSONObject().put("characterId", id).put("flagKey", id)))
        .toString()
      val canonical = packet(state, "đi khám phá khu vực")
      assertTrue("$id lacks mandatory authored card", canonical.contains("<$card>"))
      assertTrue("$id lacks visible weapon", canonical.contains(weapon))
      assertTrue("$id not scene-present", canonical.contains("PRESENT, inParty=false"))
      assertFalse("$id should not be auto-recruited", canonical.contains("$id: IN_PARTY"))
      for ((otherId, otherCard, _) in cases) {
        if (otherId != id) assertFalse("$id leaked $otherId", canonical.contains("<$otherCard>"))
      }
      val absent = packet(JSONObject()
        .put("level", JSONObject().put("number", 4).put("id", "4"))
        .toString(), "Có $id xuất hiện ở đây không?")
      assertFalse("$id can only be selected after Core confirmation", absent.contains("<$card>"))
    }
  }

  @Test fun `first meeting cards cannot be dropped by long story context`() {
    val state = JSONObject().put("level", JSONObject().put("number", 4).put("id", "4"))
      .put("pendingNpcEncounters", JSONArray().put(
        JSONObject().put("characterId", "janet").put("flagKey", "janet")))
      .put("flags", JSONObject().put("storyContinuity", "REPETITION_TEST".repeat(850)))
      .put("log", JSONArray().apply {
        repeat(4) { put(JSONObject().put("role", "gm")
          .put("text", "REPETITION_TEST".repeat(160))) }
      }).toString()
    val result = packet(state, "khám phá")
    assertTrue(result.contains("<CHAR.JANET.AUTHORED>"))
    assertTrue(result.endsWith("[END_KNOWLEDGE_PACKET]"))
    assertTrue(result.length <= com.rabpit.backroom.core.knowledge.KnowledgeContextEngine.HARD_CONTEXT_CEILING * 4)
  }

  @Test fun `Trac Lam first contact loads 2099 SRU Canon only when Core confirms him`() {
    val confirmed = JSONObject()
      .put("level", JSONObject().put("number", 0).put("id", "0.7"))
      .put("pendingNpcEncounters", JSONArray().put(
        JSONObject().put("characterId", "trac_lam").put("flagKey", "tracLam")))
    val first = packet(confirmed.toString(), "khám phá")
    assertTrue(first.contains("<CHAR.TRAC_LAM.AUTHORED>"))
    assertTrue(first.contains("M-92 Gemini"))
    assertTrue(first.contains("AEGIS-K4"))
    assertTrue(first.contains("trac_lam: PRESENT, inParty=false"))
    assertFalse(first.contains("trac_lam: IN_PARTY"))
    assertFalse("Do not conjure Luc Tram into an absent scene",
      first.contains("<CHAR.LUC_TRAM.AUTHORED>"))
    val absent = packet(JSONObject()
      .put("level", JSONObject().put("number", 0).put("id", "0.7"))
      .toString(), "Trác Lâm có ở đây không?")
    assertFalse(absent.contains("<CHAR.TRAC_LAM.AUTHORED>"))
  }

  // --- Test 2: Luc Tram Level 4+, not in Party ---

  @Test fun `Luc Tram Level 4 present loads runtime core and revival cards`() {
    // Luc Tram is present (confirmed encounter) at Level 4+, not in party.
    val state = JSONObject()
      .put("level", JSONObject().put("number", 4).put("id", "4"))
      .put("party", JSONArray().apply {
        put(JSONObject().put("id", "cao_minh").put("presence", "ACTIVE"))
      })
      .put("pendingNpcEncounters", JSONArray().apply {
        put(JSONObject().put("characterId", "luc_tram").put("flagKey", "lucTram"))
      })
      .toString()
    val result = packet(state, action = "đi về phía trước")

    // All three Luc Tram cards load.
    assertTrue("RUNTIME_CORE", result.contains("<CHAR.LUC_TRAM.RUNTIME_CORE>"))
    assertTrue("REVIVAL_R06", result.contains("<CHAR.LUC_TRAM.REVIVAL_R06>"))
    assertTrue("OPEN_R06", result.contains("<CHAR.LUC_TRAM.OPEN_R06>"))
    // Memory-loss condition: REVIVAL_R06 is the post-revival amnesia firewall.
    // It must be present to enforce that Luc Tram doesn't remember Cao Minh.
    assertTrue("amnesia firewall present", result.contains("REVIVAL_R06"))
  }

  @Test fun `Luc Tram cards not mixed with Lucia cards`() {
    val state = JSONObject()
      .put("level", JSONObject().put("number", 4).put("id", "4"))
      .put("party", JSONArray().apply {
        put(JSONObject().put("id", "cao_minh").put("presence", "ACTIVE"))
      })
      .put("pendingNpcEncounters", JSONArray().apply {
        put(JSONObject().put("characterId", "luc_tram").put("flagKey", "lucTram"))
      })
      .toString()
    val result = packet(state)

    assertTrue(result.contains("<CHAR.LUC_TRAM.RUNTIME_CORE>"))
    // Lucia is NOT present — her card must NOT leak.
    assertFalse("LUCIA leak", result.contains("<CHAR.LUCIA.AUTHORED>"))
  }

  // --- Test 3: Name-only mention → NO card leak ---

  @Test fun `name-only mention does not load Canon for unconfirmed character`() {
    // Player mentions "Lục Trầm" by name, but Core has NOT confirmed a meeting.
    // No party entry, no scheduled event, no flags.
    val state = JSONObject()
      .put("level", JSONObject().put("number", 4).put("id", "4"))
      .put("party", JSONArray().apply {
        put(JSONObject().put("id", "cao_minh").put("presence", "ACTIVE"))
      })
      .toString()
    val result = packet(state, action = "Lục Trầm có ở đây không?")

    // Retriever must NOT load Canon for unconfirmed characters.
    assertFalse("LUC_TRAM.AUTHORED leak", result.contains("<CHAR.LUC_TRAM.AUTHORED>"))
    assertFalse("LUC_TRAM.RUNTIME_CORE leak", result.contains("<CHAR.LUC_TRAM.RUNTIME_CORE>"))
    assertFalse("LUC_TRAM.REVIVAL_R06 leak", result.contains("<CHAR.LUC_TRAM.REVIVAL_R06>"))
    assertFalse("LUC_TRAM.OPEN_R06 leak", result.contains("<CHAR.LUC_TRAM.OPEN_R06>"))
  }

  @Test fun `name-only mention of Lucia does not load her card`() {
    val state = JSONObject()
      .put("level", JSONObject().put("number", 0).put("id", "0"))
      .toString()
    val result = packet(state, action = "Hứa Thuý Mai là ai?")

    assertFalse("LUCIA leak on name mention", result.contains("<CHAR.LUCIA.AUTHORED>"))
  }

  // --- Test 4: NPC left area → NOT auto-considered present ---

  @Test fun `NPC met before but not present is excluded from packet`() {
    // Lucia was encountered before (flags show encountered=true),
    // but she is NOT in party and NOT in current scene.
    // She left the area — should NOT be auto-considered present.
    val state = JSONObject()
      .put("level", JSONObject().put("number", 1).put("id", "1"))
      .put("party", JSONArray().apply {
        put(JSONObject().put("id", "cao_minh").put("presence", "ACTIVE"))
      })
      .put("flags", JSONObject()
        .put("lucia", JSONObject()
          .put("encountered", true)
          .put("spawned", true)
          .put("present", false)))  // explicitly NOT present
      .toString()
    val result = packet(state, action = "khám phá")

    // eligible() must exclude her — not in presentActors.
    assertFalse("LUCIA should not be present", result.contains("<CHAR.LUCIA.AUTHORED>"))
  }

  @Test fun `NPC in party with ABSENT presence is excluded`() {
    val state = JSONObject()
      .put("party", JSONArray().apply {
        put(JSONObject().put("id", "cao_minh").put("presence", "ACTIVE"))
        put(JSONObject().put("id", "lucia").put("presence", "ABSENT"))
      })
      .toString()
    val result = packet(state)

    assertFalse("ABSENT Lucia", result.contains("<CHAR.LUCIA.AUTHORED>"))
  }

  // --- Test 5: Budget/full party → essential voice cards preserved ---

  @Test fun `full party with long log preserves essential character voice cards`() {
    // Full party: iris, syvial, luc_tram (all ACTIVE).
    // Long log: 4 long entries to pressure the budget.
    // Essential voice cards must NOT be dropped.
    val state = JSONObject()
      .put("turn", 40)
      .put("level", JSONObject().put("number", 4).put("id", "4"))
      .put("party", JSONArray().apply {
        for (id in listOf("cao_minh", "iris", "syvial", "luc_tram"))
          put(JSONObject().put("id", id).put("presence", "ACTIVE"))
      })
      .put("flags", JSONObject().put("storyContinuity", "TEST_ONLY".repeat(500)))
      .put("log", JSONArray().apply {
        repeat(4) { put(JSONObject().put("role", "gm").put("text", "TEST_ONLY".repeat(100))) }
      })
      .toString()
    val result = packet(state)

    // Essential character voice cards still present despite budget pressure.
    assertTrue("IRIS voice", result.contains("<CHAR.IRIS.RUNTIME_CORE>"))
    assertTrue("SYVIAL voice", result.contains("<CHAR.SYVIAL.RUNTIME_CORE>"))
    assertTrue("LUC_TRAM voice", result.contains("<CHAR.LUC_TRAM.RUNTIME_CORE>"))
    // Packet is complete and within hard ceiling.
    assertTrue(result.endsWith("[END_KNOWLEDGE_PACKET]"))
    assertTrue(result.length <= KnowledgeContextEngine.HARD_CONTEXT_CEILING * 4)
  }

  // --- Test 6: API fail/abort → once-only not consumed ---

  @Test fun `aborted turn does not mark NPC encountered`() {
    // Simulate: Core prepares Lucia encounter (pending), then turn aborts.
    // The once-only flag must NOT be consumed.
    val state = GameState.initial()
    val encounter = NpcSpawnTable.NpcEncounter(LUCIA_ID, "lucia")
    val result = SpawnResult(entityKey = null, npcEncounters = listOf(encounter))

    // Phase 1: prepare (deferred, not committed)
    val prepared = SpawnMechanism.prepareSpawns(state, result)
    val pending = SpawnMechanism.pendingNpcEncounters(prepared)
    assertEquals(1, pending.size)
    assertEquals(LUCIA_ID, pending[0].characterId)

    // Verify NOT yet marked as encountered (deferred).
    val flagsBefore = JSONObject(prepared.world["flagsJson"] ?: "{}")
    val luciaBefore = flagsBefore.optJSONObject("lucia")
    assertTrue("not marked before commit",
      luciaBefore == null || !luciaBefore.optBoolean("encountered", false))

    // Abort: discard pending.
    val aborted = SpawnMechanism.discardPendingNpcEncounters(prepared)
    val pendingAfter = SpawnMechanism.pendingNpcEncounters(aborted)
    assertTrue("pending cleared on abort", pendingAfter.isEmpty())

    // Verify still NOT marked as encountered after abort.
    val flagsAfter = JSONObject(aborted.world["flagsJson"] ?: "{}")
    val luciaAfter = flagsAfter.optJSONObject("lucia")
    assertTrue("not marked after abort",
      luciaAfter == null || !luciaAfter.optBoolean("encountered", false))
  }

  @Test fun `retry after abort can re-trigger the same encounter`() {
    // After abort, retry should be able to trigger the encounter again
    // (once-only not consumed). The pending mechanism is idempotent.
    val state = GameState.initial()
    val encounter = NpcSpawnTable.NpcEncounter(LUCIA_ID, "lucia")
    val result = SpawnResult(entityKey = null, npcEncounters = listOf(encounter))

    // First attempt: prepare then abort.
    val prepared1 = SpawnMechanism.prepareSpawns(state, result)
    val aborted = SpawnMechanism.discardPendingNpcEncounters(prepared1)

    // Retry: prepare again — should work (not blocked by once-only).
    val prepared2 = SpawnMechanism.prepareSpawns(aborted, result)
    val pending2 = SpawnMechanism.pendingNpcEncounters(prepared2)
    assertEquals("retry re-triggers", 1, pending2.size)

    // Commit on success: now marked.
    val committed = SpawnMechanism.commitPendingNpcEncounters(prepared2)
    val flags = JSONObject(committed.world["flagsJson"] ?: "{}")
    assertTrue("marked after commit",
      flags.optJSONObject("lucia")?.optBoolean("encountered") == true)
  }

  @Test fun `commit is idempotent and clears pending`() {
    val state = GameState.initial()
    val encounter = NpcSpawnTable.NpcEncounter(LUCIA_ID, "lucia")
    val result = SpawnResult(entityKey = null, npcEncounters = listOf(encounter))

    val prepared = SpawnMechanism.prepareSpawns(state, result)
    val committed1 = SpawnMechanism.commitPendingNpcEncounters(prepared)
    // Second commit should be a no-op (pending already cleared).
    val committed2 = SpawnMechanism.commitPendingNpcEncounters(committed1)

    val flags1 = JSONObject(committed1.world["flagsJson"] ?: "{}")
    val flags2 = JSONObject(committed2.world["flagsJson"] ?: "{}")
    // Both show encountered (idempotent, not double-applied).
    assertTrue(flags1.optJSONObject("lucia")?.optBoolean("encountered") == true)
    assertTrue(flags2.optJSONObject("lucia")?.optBoolean("encountered") == true)
    // Pending cleared in both.
    assertTrue(SpawnMechanism.pendingNpcEncounters(committed1).isEmpty())
    assertTrue(SpawnMechanism.pendingNpcEncounters(committed2).isEmpty())
  }

  // --- An Nhien is LEGACY: verify no auto-activation ---

  @Test fun `An Nhien legacy character is not auto-activated`() {
    // An Nhien exists in initial state (legacy), but should not be
    // auto-added to GM packet without explicit presence.
    val state = JSONObject()
      .put("level", JSONObject().put("number", 0).put("id", "0"))
      .put("party", JSONArray().apply {
        put(JSONObject().put("id", "cao_minh").put("presence", "ACTIVE"))
      })
      .toString()
    val result = packet(state, action = "khám phá")

    // No An Nhien card should appear without explicit encounter.
    // (There is no CHAR.AN_NHIEN.* record in knowledge_db.json — verify no leak
    // via any an_nhien reference.)
    assertFalse("AN_NHIEN leak", result.contains("AN_NHIEN"))
    assertFalse("an_nhien leak", result.lowercase().contains("an_nhien"))
  }
}
