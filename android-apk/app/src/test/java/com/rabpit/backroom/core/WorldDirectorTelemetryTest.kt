package com.rabpit.backroom.core

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Test

class WorldDirectorTelemetryTest {

  private class TestSharedPreferences : SharedPreferences {
    val data = mutableMapOf<String, String>()

    override fun getAll(): MutableMap<String, *> = HashMap(data)
    override fun getString(key: String?, defValue: String?): String? = data[key] ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = null
    override fun getInt(key: String?, defValue: Int): Int = data[key]?.toIntOrNull() ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = data[key]?.toLongOrNull() ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = data[key]?.toFloatOrNull() ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key]?.toBooleanStrictOrNull() ?: defValue
    override fun contains(key: String?): Boolean = data.containsKey(key)
    override fun edit(): SharedPreferences.Editor = TestEditor()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    inner class TestEditor : SharedPreferences.Editor {
      private val pendingPuts = mutableMapOf<String, String?>()
      private val pendingRemoves = mutableSetOf<String>()
      private var clearAll = false

      override fun putString(key: String?, value: String?): SharedPreferences.Editor {
        if (key != null) pendingPuts[key] = value
        return this
      }
      override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
      override fun putInt(key: String?, value: Int): SharedPreferences.Editor = putString(key, value.toString())
      override fun putLong(key: String?, value: Long): SharedPreferences.Editor = putString(key, value.toString())
      override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = putString(key, value.toString())
      override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = putString(key, value.toString())
      override fun remove(key: String?): SharedPreferences.Editor {
        if (key != null) pendingRemoves.add(key)
        return this
      }
      override fun clear(): SharedPreferences.Editor {
        clearAll = true
        return this
      }
      override fun commit(): Boolean {
        if (clearAll) data.clear()
        pendingRemoves.forEach { data.remove(it) }
        pendingPuts.forEach { (k, v) -> if (v != null) data[k] = v else data.remove(k) }
        return true
      }
      override fun apply() { commit() }
    }
  }

  private class TestContext(val prefs: SharedPreferences) : ContextWrapper(null) {
    override fun getApplicationContext(): Context = this
    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
  }

  private class MemoryStore : WorldDirectorTelemetryStore {
    val records = mutableListOf<WorldDirectorTelemetryRecord>()
    override fun record(record: WorldDirectorTelemetryRecord) { records += record }
    override fun exportJsonl(): String = records.joinToString("\n") { WorldDirectorTelemetryJson.encode(it).toString() }
    override fun clear(): Boolean { records.clear(); return true }
  }

  private class FailingStore : WorldDirectorTelemetryStore {
    override fun record(record: WorldDirectorTelemetryRecord) { throw RuntimeException("Disk write failure") }
    override fun exportJsonl(): String = ""
    override fun clear(): Boolean = false
  }

  @Test fun decisionTelemetryUsesSeparateSchemaAndStorageNamespace() {
    val definition = definition()
    val state = state(definition)
    val store = MemoryStore()
    val director = WorldDirector(
      policy = WorldDirectorPolicy { WorldPressureProposal.ENTITY_PRESSURE },
      telemetry = store
    )

    val decision = director.propose(state, definition, ActionKind.EXPLORE, "TURN_1")

    assertEquals(WorldPressureProposal.ENTITY_PRESSURE, decision.accepted)
    assertEquals(1, store.records.size)

    val record = store.records.single()
    assertEquals(1, record.schemaVersion)
    assertEquals(ActionKind.EXPLORE, record.actionKind)
    assertEquals("TURN_1", record.turnId)
    assertEquals(WorldPressureProposal.ENTITY_PRESSURE, record.rawProposed)
    assertEquals(WorldPressureProposal.ENTITY_PRESSURE, record.acceptedProposal)
    assertEquals("core_accepted_proposal", record.reason)
    assertTrue(record.modelAccepted)

    val exported = store.exportJsonl()
    assertTrue(WorldDirectorTelemetryPrivacy.validateExport(exported))
    assertFalse(exported.contains("world.director.test"))
    assertFalse(exported.contains("zone-secret-99"))
    assertFalse(exported.contains("escapeBlueprint"))
    assertFalse(exported.contains("requiredFacts"))
    assertFalse(exported.contains("solutionId"))
  }

  @Test fun sharedPreferencesStoreRecordsExportsAndClears() {
    val prefs = TestSharedPreferences()
    val context = TestContext(prefs)
    val store = SharedPreferencesWorldDirectorTelemetryStore(context)

    val record = createSampleRecord("sess1", "TURN_1", ActionKind.EXPLORE)
    store.record(record)

    val jsonl = store.exportJsonl()
    assertTrue(jsonl.contains("sess1"))
    assertTrue(jsonl.contains("TURN_1"))
    assertTrue(WorldDirectorTelemetryPrivacy.validateExport(jsonl))

    val cleared = store.clear()
    assertTrue(cleared)
    assertEquals("", store.exportJsonl())
  }

  @Test fun sharedPreferencesStoreEnforcesBoundedFifoEviction() {
    val prefs = TestSharedPreferences()
    val context = TestContext(prefs)
    val store = SharedPreferencesWorldDirectorTelemetryStore(context, maxRecords = 5)

    for (i in 1..10) {
      store.record(createSampleRecord("sess_$i", "TURN_$i", ActionKind.EXPLORE))
    }

    val lines = store.exportJsonl().lines().filter(String::isNotBlank)
    assertEquals(5, lines.size)
    assertFalse(store.exportJsonl().contains("sess_1"))
    assertFalse(store.exportJsonl().contains("sess_5"))
    assertTrue(store.exportJsonl().contains("sess_6"))
    assertTrue(store.exportJsonl().contains("sess_10"))
  }

  @Test fun sharedPreferencesStoreHandlesMalformedRowsSafely() {
    val prefs = TestSharedPreferences()
    prefs.data["world_director_records_v1"] = """[{"invalid": "json"}, "not_an_object", {"sessionId": "valid1", "actionKind": "EXPLORE", "turnId": "T1", "features": "f", "legalProposals": ["NONE"], "rawProposed": null, "acceptedProposal": "NONE", "reason": "model_abstained", "modelAccepted": false, "visitCount": 1, "discoveredEvidenceCount": 0, "worldRevision": 1, "schemaVersion": 1}]"""
    val context = TestContext(prefs)
    val store = SharedPreferencesWorldDirectorTelemetryStore(context)

    val jsonl = store.exportJsonl()
    val lines = jsonl.lines().filter(String::isNotBlank)
    assertEquals(1, lines.size)
    assertTrue(jsonl.contains("valid1"))
  }

  @Test fun retryReplayDeduplicationPreventsDuplicateTurnRecords() {
    val prefs = TestSharedPreferences()
    val context = TestContext(prefs)
    val store1 = SharedPreferencesWorldDirectorTelemetryStore(context)

    val record = createSampleRecord("sess_dedupe", "TURN_SAME", ActionKind.EXPLORE)
    store1.record(record)
    store1.record(record)

    val lines = store1.exportJsonl().lines().filter(String::isNotBlank)
    assertEquals(1, lines.size)

    // Simulate app restart / store re-creation
    val store2 = SharedPreferencesWorldDirectorTelemetryStore(context)
    store2.record(record)
    val linesAfterRestart = store2.exportJsonl().lines().filter(String::isNotBlank)
    assertEquals(1, linesAfterRestart.size)
  }

  @Test fun backroomsDirectorAndWorldDirectorTelemetryAreSeparated() {
    val prefsDirector = TestSharedPreferences()
    val prefsWorld = TestSharedPreferences()

    val contextDirector = TestContext(prefsDirector)
    val contextWorld = TestContext(prefsWorld)

    val backroomsStore = SharedPreferencesBackroomsDirectorTelemetryStore(contextDirector)
    val worldStore = SharedPreferencesWorldDirectorTelemetryStore(contextWorld)

    val backroomsRecord = DirectorTelemetryRecord(
      sessionId = "bd1",
      actionKind = ActionKind.EXPLORE,
      features = "f_backrooms",
      candidateSourceCounts = mapOf(EvidenceSource.ANOMALY to 1),
      modelPreferredSource = EvidenceSource.ANOMALY,
      modelAccepted = true,
      fallbackUsed = false,
      selectedSource = EvidenceSource.ANOMALY,
      surfacedCount = 1,
      discoveredEvidenceBefore = 0,
      discoveredEvidenceAfter = 1,
      discoveredFactBefore = 0,
      discoveredFactAfter = 0,
      unlockedFact = false,
      worldRevisionBefore = 1,
      worldRevisionAfter = 1
    )
    backroomsStore.record(backroomsRecord)

    val worldRecord = createSampleRecord("wd1", "TURN_1", ActionKind.EXPLORE)
    worldStore.record(worldRecord)

    assertFalse(worldStore.exportJsonl().contains("f_backrooms"))
    assertFalse(backroomsStore.exportJsonl().contains("sess_wd1"))
  }

  @Test fun featureTextMatchesWorldDirectorFeaturesDescribeExactly() {
    val definition = definition()
    val state = state(definition)
    val store = MemoryStore()
    val director = WorldDirector(
      policy = WorldDirectorPolicy { WorldPressureProposal.MAZE_PRESSURE },
      telemetry = store
    )

    director.propose(state, definition, ActionKind.EXPLORE, "TURN_2")

    val record = store.records.single()
    val expectedContext = WorldDirectorContext(
      actionKind = ActionKind.EXPLORE,
      safeZoneTags = WorldDirectorFeatures.safeZoneTags(setOf("entry", "loop")),
      visitCount = 3,
      revision = state.levelInstance!!.revision,
      recentMutationKind = null,
      discoveredEvidenceCount = 0,
      legalProposals = setOf(WorldPressureProposal.NONE, WorldPressureProposal.MAZE_PRESSURE, WorldPressureProposal.ENTITY_PRESSURE, WorldPressureProposal.ITEM_OPPORTUNITY)
    )
    val expectedFeatureText = WorldDirectorFeatures.describe(expectedContext)
    assertEquals(expectedFeatureText, record.features)
  }

  @Test fun legalProposalSetAndAcceptedRejectedAbstainDecisionsAreRecorded() {
    val definition = definition(allowEntities = false)
    val state = state(definition)
    val store = MemoryStore()

    val directorAbstain = WorldDirector(WorldDirectorPolicy { null }, telemetry = store)
    directorAbstain.propose(state, definition, ActionKind.EXPLORE, "TURN_3")
    val abstainRecord = store.records.last()
    assertNull(abstainRecord.rawProposed)
    assertEquals(WorldPressureProposal.NONE, abstainRecord.acceptedProposal)
    assertEquals("model_abstained", abstainRecord.reason)
    assertFalse(abstainRecord.modelAccepted)

    val directorIllegal = WorldDirector(WorldDirectorPolicy { WorldPressureProposal.ENTITY_PRESSURE }, telemetry = store)
    directorIllegal.propose(state, definition, ActionKind.EXPLORE, "TURN_4")
    val illegalRecord = store.records.last()
    assertEquals(WorldPressureProposal.ENTITY_PRESSURE, illegalRecord.rawProposed)
    assertEquals(WorldPressureProposal.NONE, illegalRecord.acceptedProposal)
    assertEquals("core_rejected_illegal_proposal", illegalRecord.reason)
    assertFalse(illegalRecord.modelAccepted)
  }

  @Test fun noForbiddenFieldsOrLeaksSerializedInExport() {
    val record = createSampleRecord("opaqueabc123", "TURN_5", ActionKind.SEARCH)

    val encoded = WorldDirectorTelemetryJson.encode(record)
    val exported = encoded.toString()

    assertTrue(WorldDirectorTelemetryPrivacy.validateExport(exported))
    for (forbidden in listOf("levelId", "zoneId", "evidenceId", "escapeBlueprint", "playerText", "input", "apiKey", "secret", "entityId", "itemId", "inventory")) {
      assertFalse("Forbidden key present: $forbidden", encoded.has(forbidden))
    }
  }

  @Test fun jsonRoundTripPreservesSafeRecordStructure() {
    val record = createSampleRecord("opaque789", "TURN_6", ActionKind.EXPLORE)

    val json = WorldDirectorTelemetryJson.encode(record)
    val decoded = WorldDirectorTelemetryJson.decode(json)

    assertEquals(record, decoded)
    assertTrue(WorldDirectorTelemetryPrivacy.validateExport(json.toString()))
  }

  @Test fun telemetryWriteFailureCannotAffectGameDecision() {
    val definition = definition()
    val state = state(definition)
    val failingStore = FailingStore()
    val director = WorldDirector(
      policy = WorldDirectorPolicy { WorldPressureProposal.ENTITY_PRESSURE },
      telemetry = failingStore
    )

    val decision = director.propose(state, definition, ActionKind.EXPLORE, "TURN_7")
    assertEquals(WorldPressureProposal.ENTITY_PRESSURE, decision.accepted)
    assertEquals("core_accepted_proposal", decision.reason)
  }

  private fun createSampleRecord(sessionId: String, turnId: String, kind: ActionKind) = WorldDirectorTelemetryRecord(
    sessionId = sessionId,
    actionKind = kind,
    turnId = turnId,
    features = "action_${kind.name.lowercase()} visit_first candidate_none candidate_item_opportunity",
    legalProposals = listOf(WorldPressureProposal.NONE, WorldPressureProposal.ITEM_OPPORTUNITY),
    rawProposed = WorldPressureProposal.ITEM_OPPORTUNITY,
    acceptedProposal = WorldPressureProposal.ITEM_OPPORTUNITY,
    reason = "core_accepted_proposal",
    modelAccepted = true,
    visitCount = 1,
    discoveredEvidenceCount = 0,
    worldRevision = 1
  )

  private fun definition(allowEntities: Boolean = true): LevelDefinition {
    val zoneId = "zone-secret-99"
    val zones = mapOf(
      "a" to ZoneState("a", "A", setOf("b", "c"), setOf("entry", "loop")),
      "b" to ZoneState("b", "B", setOf("a", "c"), setOf("dark")),
      "c" to ZoneState("c", "C", setOf("a", "b"), setOf("escape", "level_transition"))
    )
    val evidence = mapOf(
      "e1" to EvidenceState("e1", setOf("F"), setOf(EvidenceSource.SEARCH), "a")
    )
    val exit = LevelActionRule(
      id = "exit",
      matchGroups = listOf(setOf("exit")),
      effects = listOf(LevelEffect(LevelEffectType.COMPLETE_LEVEL))
    )
    return LevelDefinition(
      id = "world.director.test",
      name = "World Director Test",
      initialZoneId = "a",
      zones = zones,
      escapeBlueprint = EscapeBlueprintState("hidden-solution", setOf("F"), listOf("exit")),
      evidence = evidence,
      actions = mapOf("exit" to exit),
      generationConstraints = ProceduralGenerationConstraints(
        minZones = 3,
        maxZones = 8,
        minEvidencePerRequiredFact = 1,
        minEvidenceSourceTypesPerRequiredFact = 1,
        maxRequiredActions = 2,
        allowEntities = allowEntities,
        proceduralTopology = true
      )
    )
  }

  private fun state(definition: LevelDefinition): GameState {
    val level = GenericLevelGenerator.generate(definition, "seed-123").copy(
      currentZoneId = "a",
      environment = mapOf("visits:a" to "3")
    )
    return GameState.initial().copy(levelInstance = level)
  }
}
