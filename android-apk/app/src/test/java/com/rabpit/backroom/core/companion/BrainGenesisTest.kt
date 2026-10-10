package com.rabpit.backroom.core.companion

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

/** P1b genesis fixtures (issue #502): pins, empty brain, version mismatch, legacy alias. */
class BrainGenesisTest {
  private fun persona(actor: String = "cao_minh") =
    CompanionCanonPersonaRegistry.Persona(
      actor, if (actor == "cao_minh") "CHAR.KAI" else "CHAR.LUC_TRAM",
      "knowledge/novel_asset/X.md", if (actor == "cao_minh") "R17" else "R05",
      "0".repeat(64), listOf(), listOf(), listOf(), listOf())

  @Test fun genesisPinsRegistryAndEmptyBrain() {
    val record = BrainGenesis.genesis(persona("cao_minh"))
    assertEquals("cao_minh", record.pins.actorId)
    assertEquals("CHAR.KAI", record.pins.knowledgeNamespace)
    assertEquals("R17", record.pins.personaRevision)
    assertEquals("rule_table.v1", record.pins.ruleVersion)
    assertEquals("companion_exposure.v1", record.pins.policyVersion)
    assertTrue(record.brain.beliefs.isEmpty())
    assertTrue(record.brain.goals.isEmpty())
    assertTrue(record.brain.relationships.isEmpty())
    assertTrue(record.brain.memoryObservationIds.isEmpty())
    assertEquals(BrainGenesis.InitialBrain.Mood.NEUTRAL, record.brain.mood)
  }

  @Test fun genesisLucTramPinsR05() {
    val record = BrainGenesis.genesis(persona("luc_tram"))
    assertEquals("R05", record.pins.personaRevision)
    assertEquals("CHAR.LUC_TRAM", record.pins.knowledgeNamespace)
  }

  @Test fun legacyAliasRejected() {
    try {
      BrainGenesis.genesis(persona("ka" + "i"))
      fail("expected genesis_legacy_alias")
    } catch (e: IOException) {
      assertEquals("genesis_legacy_alias", e.message)
    }
  }

  @Test fun verifyOnLoadAcceptsMatchingPins() {
    val record = BrainGenesis.genesis(persona("cao_minh"))
    BrainGenesis.verifyOnLoad(record.pins, persona("cao_minh"))  // no throw
  }

  @Test fun verifyOnLoadRejectsRevisionMismatch() {
    val record = BrainGenesis.genesis(persona("cao_minh"))
    val changed = CompanionCanonPersonaRegistry.Persona(
      "cao_minh", "CHAR.KAI", "knowledge/novel_asset/X.md", "R18",
      "0".repeat(64), listOf(), listOf(), listOf(), listOf())
    try {
      BrainGenesis.verifyOnLoad(record.pins, changed)
      fail("expected genesis_persona_revision_mismatch")
    } catch (e: IOException) {
      assertEquals("genesis_persona_revision_mismatch", e.message)
    }
  }

  @Test fun verifyOnLoadRejectsShaMismatch() {
    val record = BrainGenesis.genesis(persona("cao_minh"))
    val changed = CompanionCanonPersonaRegistry.Persona(
      "cao_minh", "CHAR.KAI", "knowledge/novel_asset/X.md", "R17",
      "1".repeat(64), listOf(), listOf(), listOf(), listOf())
    try {
      BrainGenesis.verifyOnLoad(record.pins, changed)
      fail("expected genesis_persona_source_mismatch")
    } catch (e: IOException) {
      assertEquals("genesis_persona_source_mismatch", e.message)
    }
  }

  @Test fun verifyOnLoadRejectsActorMismatch() {
    val record = BrainGenesis.genesis(persona("cao_minh"))
    try {
      BrainGenesis.verifyOnLoad(record.pins, persona("luc_tram"))
      fail("expected genesis_actor_mismatch")
    } catch (e: IOException) {
      assertEquals("genesis_actor_mismatch", e.message)
    }
  }

  @Test fun schemaHasSingletonAndImmutableTriggers() {
    val stmts = BrainGenesisSchema.createStatements()
    assertEquals(3, stmts.size)
    assertTrue(stmts.any { it.contains("genesis_pins") && it.contains("singleton") })
    val triggers = stmts.filter { it.trimStart().startsWith("CREATE TRIGGER") }
    assertEquals(2, triggers.size)
    assertTrue(triggers.all { it.contains("BEGIN SELECT RAISE") && it.trimEnd().endsWith("END;") })
    assertTrue(stmts.any { it.contains("immutable_genesis") })
    // legacy alias banned at the schema level too
    assertTrue(stmts.any { it.contains("NOT IN ('ka' || 'i','KAI')") })
  }
}
