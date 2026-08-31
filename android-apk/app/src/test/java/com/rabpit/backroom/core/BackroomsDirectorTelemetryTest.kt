package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class BackroomsDirectorTelemetryTest {
  private class MemoryStore : BackroomsDirectorTelemetryStore {
    val records = mutableListOf<DirectorTelemetryRecord>()
    override fun record(record: DirectorTelemetryRecord) { records += record }
    override fun exportJsonl(): String = records.joinToString("\n") { DirectorTelemetryJson.encode(it).toString() }
    override fun clear(): Boolean { records.clear(); return true }
  }

  @Test fun decisionTelemetryRecordsOnlySanitizedBehaviorAndOutcome() {
    val definition = definition()
    val level = GenericLevelGenerator.generate(definition, "secret-run-seed")
    val store = MemoryStore()
    val director = BackroomsDirector(
      policy = BackroomsDirectorPolicy { DirectorEvidencePreference.ANOMALY },
      telemetry = store
    )
    val eligible = listOf(
      level.evidence.getValue("anomaly-f"),
      level.evidence.getValue("environment-f")
    )

    val selection = director.selectEvidenceWithTrace(level, definition, ActionKind.EXPLORE, eligible)
    assertEquals("anomaly-f", selection.evidence.single().id)

    val selected = selection.evidence.single()
    val afterEvidence = level.evidence + (selected.id to selected.copy(discovered = true, discoveredAtRevision = level.revision))
    val after = level.copy(
      evidence = afterEvidence,
      discoveredFacts = setOf("F"),
      revision = level.revision + 1
    )
    director.recordOutcome(selection.trace, after, surfacedCount = 1)

    assertEquals(1, store.records.size)
    val record = store.records.single()
    assertEquals(ActionKind.EXPLORE, record.actionKind)
    assertEquals(EvidenceSource.ANOMALY, record.modelPreferredSource)
    assertTrue(record.modelAccepted)
    assertFalse(record.fallbackUsed)
    assertEquals(EvidenceSource.ANOMALY, record.selectedSource)
    assertEquals(1, record.surfacedCount)
    assertTrue(record.unlockedFact)
    assertNotEquals("secret-run-seed", record.sessionId)

    val exported = store.exportJsonl()
    assertTrue(BackroomsDirectorTelemetryPrivacy.validateExport(exported))
    assertFalse(exported.contains("director.test"))
    assertFalse(exported.contains("entry"))
    assertFalse(exported.contains("anomaly-f"))
    assertFalse(exported.contains("requiredFacts"))
    assertFalse(exported.contains("requiredActions"))
    assertFalse(exported.contains("solutionId"))
  }

  @Test fun illegalModelPreferenceIsRecordedAsDeterministicFallback() {
    val definition = definition()
    val level = GenericLevelGenerator.generate(definition, "fallback-seed")
    val store = MemoryStore()
    val director = BackroomsDirector(
      policy = BackroomsDirectorPolicy { DirectorEvidencePreference.SURVIVOR },
      telemetry = store
    )
    val eligible = listOf(level.evidence.getValue("environment-f"))

    val selection = director.selectEvidenceWithTrace(level, definition, ActionKind.EXPLORE, eligible)
    val selected = selection.evidence.single()
    val after = level.copy(
      evidence = level.evidence + (selected.id to selected.copy(discovered = true, discoveredAtRevision = level.revision))
    )
    director.recordOutcome(selection.trace, after, 1)

    val record = store.records.single()
    assertEquals(EvidenceSource.SURVIVOR, record.modelPreferredSource)
    assertFalse(record.modelAccepted)
    assertTrue(record.fallbackUsed)
    assertEquals(EvidenceSource.ENVIRONMENT, record.selectedSource)
  }

  @Test fun telemetryJsonRoundTripPreservesSafeSchema() {
    val record = DirectorTelemetryRecord(
      sessionId = "opaque123",
      actionKind = ActionKind.SEARCH,
      features = "action_search visit_first candidate_search unseen_search",
      candidateSourceCounts = mapOf(EvidenceSource.SEARCH to 2),
      modelPreferredSource = EvidenceSource.SEARCH,
      modelAccepted = true,
      fallbackUsed = false,
      selectedSource = EvidenceSource.SEARCH,
      surfacedCount = 1,
      discoveredEvidenceBefore = 0,
      discoveredEvidenceAfter = 1,
      discoveredFactBefore = 0,
      discoveredFactAfter = 0,
      unlockedFact = false,
      worldRevisionBefore = 2,
      worldRevisionAfter = 2
    )

    val decoded = DirectorTelemetryJson.decode(DirectorTelemetryJson.encode(record))
    assertEquals(record, decoded)
    assertTrue(BackroomsDirectorTelemetryPrivacy.validateExport(DirectorTelemetryJson.encode(record).toString()))
  }

  private fun definition(): LevelDefinition {
    val zone = ZoneState("entry", "Entry", emptySet(), setOf("entry", "escape"))
    val evidence = mapOf(
      "anomaly-f" to EvidenceState("anomaly-f", setOf("F"), setOf(EvidenceSource.ANOMALY), "entry"),
      "environment-f" to EvidenceState("environment-f", setOf("F"), setOf(EvidenceSource.ENVIRONMENT), "entry")
    )
    val exit = LevelActionRule(
      id = "exit",
      matchGroups = listOf(setOf("exit")),
      effects = listOf(LevelEffect(LevelEffectType.COMPLETE_LEVEL))
    )
    return LevelDefinition(
      id = "director.test",
      name = "Director telemetry test",
      initialZoneId = "entry",
      zones = mapOf("entry" to zone),
      escapeBlueprint = EscapeBlueprintState("hidden-solution", setOf("F"), listOf("exit")),
      evidence = evidence,
      actions = mapOf("exit" to exit),
      canonProfile = LevelCanonProfile(requiredZoneTags = setOf("entry", "escape")),
      generationConstraints = ProceduralGenerationConstraints(
        minZones = 1,
        maxZones = 2,
        minEvidencePerRequiredFact = 2,
        minEvidenceSourceTypesPerRequiredFact = 2,
        maxRequiredActions = 2
      )
    )
  }
}
