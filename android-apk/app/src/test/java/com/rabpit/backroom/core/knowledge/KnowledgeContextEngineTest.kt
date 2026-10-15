package com.rabpit.backroom.core.knowledge

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class KnowledgeContextEngineTest {
  private fun packet(state: String = "{}", action: String = "", rolls: String = "{}"): String {
    val file = listOf(
      File("src/main/assets/knowledge/knowledge_db.json"),
      File("app/src/main/assets/knowledge/knowledge_db.json")
    ).first { it.isFile }
    return KnowledgeContextEngine.Builder(
      KnowledgeContextEngine.parseDatabase(file.readText()), JSONObject(state), action, JSONObject(rolls)
    ).build()
  }

  @Test fun mentionedNamesDoNotGrantPresenceOrCards() {
    val result = packet(action = "Lucia, Trác Lâm, Lục Trầm, Tịch Quang, Iris, Syvial")
    for (id in listOf("LUCIA", "TRAC_LAM", "LUC_TRAM", "IRIS", "SYVIAL")) {
      assertFalse(id, result.contains("<CHAR.$id."))
    }
  }

  @Test fun activePartyAndCoreConfirmedActorsGrantActorCards() {
    assertTrue(packet("""{"party":[{"id":"lucia","presence":"ACTIVE"}]}""").contains("<CHAR.LUCIA.AUTHORED>"))
    assertFalse(packet("""{"party":[{"id":"lucia","presence":"ABSENT"}]}""").contains("<CHAR.LUCIA.AUTHORED>"))
    assertFalse(packet("""{"party":[{"id":"iris_unknown"}]}""").contains("<CHAR.IRIS."))
    assertFalse(packet("""{"turn":4}""", rolls = """{"turn":4,"luciaEncounter":{"eligible":true,"success":true}}""").contains("<CHAR.LUCIA.AUTHORED>"))
    // Core-confirmed present actors: pendingNpcEncounters from beginAction (first meeting, not yet in Party).
    assertTrue(packet("""{"pendingNpcEncounters":[{"characterId":"lucia","flagKey":"lucia"}]}""").contains("<CHAR.LUCIA.AUTHORED>"))
    assertTrue(packet(state = "{}", action = "đi tiếp",
      rolls = """{"pendingNpcEncounters":[{"characterId":"luc_tram","flagKey":"lucTram"}]}""").contains("<CHAR.LUC_TRAM.AUTHORED>"))
    // encounteredCharacters: present=true grants cards; present=false (met before) does not.
    assertTrue(packet("""{"encounteredCharacters":[{"id":"lucia","present":true,"inParty":false}]}""").contains("<CHAR.LUCIA.AUTHORED>"))
    assertFalse(packet("""{"encounteredCharacters":[{"id":"lucia","present":false,"inParty":false}]}""").contains("<CHAR.LUCIA.AUTHORED>"))
    assertFalse(packet("""{"encounteredCharacters":[{"id":"luc_tram","present":false,"inParty":false}]}""").contains("<CHAR.LUC_TRAM."))
  }

  @Test fun previouslyEncounteredAbsentActorsDoNotReceiveSceneStatusOrDetailCards() {
    val state = """{"encounteredCharacters":[
      {"id":"lucia","present":false,"inParty":false},
      {"id":"iris","present":false,"inParty":false},
      {"id":"syvial","present":false,"inParty":false}
    ]}"""
    val result = packet(state, action = "khám phá hành lang")
    assertFalse(result.contains("ENCOUNTERED_ABSENT"))
    assertFalse(result.contains("NPC relationship status"))
    for (id in listOf("LUCIA", "IRIS", "SYVIAL")) {
      assertFalse(id, result.contains("<CHAR.$id."))
    }
  }

  @Test fun registeredFutureActorUsesCorePresenceWithoutHardcodedName() {
    val file = listOf(
      File("src/main/assets/knowledge/knowledge_db.json"),
      File("app/src/main/assets/knowledge/knowledge_db.json")
    ).first { it.isFile }
    val catalog = JSONObject(file.readText())
    catalog.getJSONArray("records").put(JSONObject()
      .put("id", "CHAR.NOVA.AUTHORED").put("domain", "CHARACTER")
      .put("kind", "authored").put("text", "TEST_ONLY Nova has a precise, independent voice.")
      .put("authority", "AUTHOR").put("mutability", "IMMUTABLE")
      .put("priority", 34)
      .put("source", JSONObject().put("document", "TEST_ONLY").put("anchor", "fixture")))
    val db = KnowledgeContextEngine.parseDatabase(catalog.toString())
    fun build(state: JSONObject, action: String = "khám phá"): String =
      KnowledgeContextEngine.Builder(db, state, action, JSONObject()).build()

    val absent = JSONObject().put("encounteredCharacters", org.json.JSONArray()
      .put(JSONObject().put("id", "nova").put("present", false)))
    assertFalse(build(absent, "Nova có ở đây không?").contains("<CHAR.NOVA.AUTHORED>"))
    val present = JSONObject().put("pendingNpcEncounters", org.json.JSONArray()
      .put(JSONObject().put("characterId", "nova")))
    val packet = build(present)
    assertTrue(packet.contains("<CHAR.NOVA.AUTHORED>"))
    assertTrue(packet.contains("nova: PRESENT, inParty=false"))
    val forged = JSONObject().put("party", org.json.JSONArray()
      .put(JSONObject().put("id", "nova_unknown").put("presence", "ACTIVE")))
    assertFalse(build(forged).contains("<CHAR.NOVA.AUTHORED>"))
  }

  @Test fun entityMentionAndLevelReferencesCannotInventEncounter() {
    val result = packet("""{"level":{"number":5,"id":"5"}}""", "hound, the beast, Jeff")
    assertFalse(result.contains("<ENTITY.HOUND>"))
    assertFalse(result.contains("<ENTITY.BEAST_LEVEL_5>"))
    assertFalse(result.contains("<ENTITY.JEFF>"))
    assertFalse(result.contains("<ENTITY.HOUND.VISUAL>"))
  }

  @Test fun randomRollCannotGrantEntityVisualButCoreSceneAndCombatCan() {
    val roll = """{"turn":4,"selectedEntityKey":"hound","entityEncounter":{"eligible":true,"success":true}}"""
    assertFalse(packet("""{"turn":4}""", rolls = roll).contains("<ENTITY.HOUND.VISUAL>"))
    assertFalse(packet("""{"turn":5}""", rolls = roll).contains("<ENTITY.HOUND.VISUAL>"))
    assertFalse(packet("""{"turn":4}""", rolls = JSONObject(roll).put("meta", true).toString()).contains("<ENTITY.HOUND.VISUAL>"))
    assertFalse(packet("""{"turn":4}""", rolls = roll.replace("\"eligible\":true", "\"eligible\":false")).contains("<ENTITY.HOUND.VISUAL>"))
    assertTrue(packet("""{"flags":{"entityEncounterKey":"hound"}}""").contains("<ENTITY.HOUND.VISUAL>"))
    assertTrue(packet("""{"combat":{"active":true,"entityKey":"hound"}}""").contains("<ENTITY.HOUND.VISUAL>"))
  }

  @Test fun exactNodeAndParentStaySeparate() {
    val result = packet("""{"level":{"number":0,"id":"red_rooms"}}""")
    assertTrue(result.contains("<LEVEL.00.AUTHORED>"))
    assertTrue(result.contains("<LEVEL.NODE.red_rooms>"))
    assertFalse(result.contains("<LEVEL.01.AUTHORED>"))
    assertFalse(packet("""{"level":{"number":7,"id":"7"}}""").contains("<LEVEL.07.AUTHORED>"))
  }

  @Test fun largeCanonRegistrySelectsOnlyPresentActorDetailsWithoutGrowingPromptWithLibrary() {
    val records = org.json.JSONArray()
    fun record(id: String, domain: String, kind: String, tags: List<String>, priority: Int = 80) =
      JSONObject().put("id", id).put("domain", domain).put("kind", kind)
        .put("text", "Verified details for $id")
        .put("authority", "AUTHOR").put("mutability", "IMMUTABLE")
        .put("priority", priority).put("tags", org.json.JSONArray(tags))
        .put("source", JSONObject().put("document", "TEST_ONLY").put("anchor", "fixture"))
    for (i in 1..20) {
      records.put(record("CHAR.NPC_$i.DETAIL.PROFILE.001", "CHARACTER",
        "authored-detail", listOf("detail:profile")))
    }
    for (i in 1..100) {
      records.put(record("LEVEL.$i.DETAIL.ENVIRONMENT.001", "LEVEL",
        "authored-detail", listOf("detail:environment")))
    }
    for (i in 1..50) {
      records.put(record("ENTITY.SYNTH_$i.VISUAL", "ENTITY",
        "visual-card", listOf("synthetic_$i")))
    }
    // Author-only record must never reach the writer even when its actor appears.
    records.put(record("CHAR.NPC_1.DETAIL.VISUAL.SECRET", "CHARACTER",
      "authored-detail", listOf("detail:profile"))
      .put("authority", "WRITER-SECRET"))
    val database = KnowledgeContextEngine.parseDatabase(JSONObject().put("records", records).toString())
    fun selected(state: JSONObject): String =
      KnowledgeContextEngine.Builder(database, state, "đi tiếp", JSONObject()).build()
    val absent = selected(JSONObject())
    val present = selected(JSONObject().put("pendingNpcEncounters",
      org.json.JSONArray().put(JSONObject().put("characterId", "npc_1"))))
    assertFalse(absent.contains("<CHAR.NPC_1.DETAIL.PROFILE.001>"))
    assertTrue(present.contains("<CHAR.NPC_1.DETAIL.PROFILE.001>"))
    assertFalse(present.contains("<CHAR.NPC_2.DETAIL.PROFILE.001>"))
    assertFalse(present.contains("CHAR.NPC_1.DETAIL.VISUAL.SECRET"))
    assertTrue(present.length <= KnowledgeContextEngine.HARD_CONTEXT_CEILING * 4)
    // Corpus size is unrelated to the per-turn payload without Core presence.
    assertTrue(absent.length < 1200)
  }

  @Test fun writerSecretCannotEnterThroughReferenceExpansion() {
    val db = KnowledgeContextEngine.parseDatabase("""{"records":[
      {"id":"GAME.TEXT.CORE","domain":"GAME_RULE","kind":"rule","text":"TEST_ONLY boundary","authority":"CORE","mutability":"IMMUTABLE","priority":1,"references":["TEST.SECRET"],"source":{"document":"TEST_ONLY","anchor":"fixture"}},
      {"id":"TEST.SECRET","domain":"CHARACTER","kind":"secret","text":"TEST_ONLY hidden detail","authority":"WRITER-SECRET","mutability":"IMMUTABLE","priority":1,"references":[],"source":{"document":"TEST_ONLY","anchor":"fixture"}}
    ]}""")
    val result = KnowledgeContextEngine.Builder(db, JSONObject(), "", JSONObject()).build()
    assertTrue(result.contains("<GAME.TEXT.CORE>"))
    assertFalse(result.contains("TEST.SECRET"))
    assertFalse(result.contains("hidden detail"))
  }

  @Test fun metadataBudgetKeepsWholeRecordsAndMandatoryLocks() {
    val raw = File("src/main/assets/knowledge/knowledge_db.json").let {
      if (it.isFile) it else File("app/src/main/assets/knowledge/knowledge_db.json")
    }.readText()
    val root = JSONObject(raw)
    val records = root.getJSONArray("records")
    for (i in 0 until records.length()) {
      val record = records.getJSONObject(i)
      if (record.getInt("priority") > 30) {
        record.getJSONObject("source").put("anchor", "TEST_ONLY".repeat(1000))
      }
    }
    val result = KnowledgeContextEngine.Builder(
      KnowledgeContextEngine.parseDatabase(root.toString()), JSONObject(), "", JSONObject()
    ).build()
    assertTrue(result.length <= KnowledgeContextEngine.HARD_CONTEXT_CEILING * 4)
    assertTrue(result.endsWith("[END_KNOWLEDGE_PACKET]"))
    assertTrue(result.contains("<WRITING.KNOWLEDGE_BOUNDARY>"))
    assertTrue(result.contains("<WRITING.PLAYER_AGENCY>"))
    // Long provenance remains locally traceable but cannot crowd out Canon.
    assertFalse(result.contains("TEST_ONLY".repeat(1000)))
    assertTrue(result.contains("<CHAR.CAO_MINH.AUTHORED>"))
  }

  @Test fun canonTextAndAuthorityStayInPromptWhileSourceRemainsLocal() {
    val sourcePath = "https://example.invalid/private/very-long-provenance-record"
    val raw = JSONObject().put("records", org.json.JSONArray().put(JSONObject()
      .put("id", "GAME.TEXT.CORE").put("domain", "GAME_RULE")
      .put("kind", "hard-lock").put("text", "Verified gameplay Canon text must survive.")
      .put("authority", "CORE").put("mutability", "IMMUTABLE").put("priority", 10)
      .put("source", JSONObject().put("document", sourcePath).put("anchor", "test-section"))))
    val db = KnowledgeContextEngine.parseDatabase(raw.toString())
    assertEquals(sourcePath, db.records.getValue("GAME.TEXT.CORE").source.document)
    val packet = KnowledgeContextEngine.Builder(db, JSONObject(), "", JSONObject()).build()
    assertTrue(packet.contains("Verified gameplay Canon text must survive."))
    assertTrue(packet.contains("authority=CORE; mutability=IMMUTABLE"))
    assertFalse(packet.contains(sourcePath))
    assertFalse(packet.contains("source="))
    assertFalse(packet.contains("; why="))
  }

  @Test fun fullPartyAndLargeContinuityPreserveKnowledgeHardLocksWithinBudget() {
    val state = JSONObject().put("turn", 40)
      .put("level", JSONObject().put("number", 4).put("id", "4"))
      .put("party", org.json.JSONArray().apply {
        for (id in listOf("iris", "syvial", "luc_tram"))
          put(JSONObject().put("id", id).put("presence", "ACTIVE"))
      })
      .put("flags", JSONObject().put("storyContinuity", "TEST_ONLY".repeat(1000)))
      .put("log", org.json.JSONArray().apply {
        repeat(4) { put(JSONObject().put("role", "gm").put("text", "TEST_ONLY".repeat(100))) }
      })
    val result = packet(state.toString())
    assertTrue(result.length <= KnowledgeContextEngine.HARD_CONTEXT_CEILING * 4)
    for (id in listOf("CHAR.IRIS.RUNTIME_CORE", "CHAR.SYVIAL.RUNTIME_CORE",
      "CHAR.LUC_TRAM.REVIVAL_R06", "CHAR.LUC_TRAM.OPEN_R06", "WRITING.KNOWLEDGE_BOUNDARY")) {
      assertTrue(id, result.contains("<$id>"))
    }
    assertTrue(result.endsWith("[END_KNOWLEDGE_PACKET]"))
  }
  @Test fun fullPartyAndEveryConfirmedEntityFitTheMandatoryBudget() {
    val state = JSONObject().put("turn", 40)
      .put("level", JSONObject().put("number", 4).put("id", "4"))
      .put("party", org.json.JSONArray().apply {
        for (id in listOf("iris", "syvial", "luc_tram"))
          put(JSONObject().put("id", id).put("presence", "ACTIVE"))
      })
      .put("flags", JSONObject().put("storyContinuity", "TEST_ONLY".repeat(1000)))
      .put("log", org.json.JSONArray().apply {
        repeat(4) { put(JSONObject().put("role", "gm").put("text", "TEST_ONLY".repeat(100))) }
      })
    val file = listOf(File("src/main/assets/knowledge/knowledge_db.json"),
      File("app/src/main/assets/knowledge/knowledge_db.json")).first { it.isFile }
    val db = KnowledgeContextEngine.parseDatabase(file.readText())
    for (record in db.records.values.filter { it.id.startsWith("ENTITY.") && it.id.endsWith(".VISUAL") }) {
      val key = record.tags.first()
      state.put("pendingEntityEncounter", JSONObject().put("entityKey", key))
      state.getJSONObject("flags").put("entityEncounterKey", key)
      val result = KnowledgeContextEngine.Builder(db, state, "đi tiếp", JSONObject()).build()
      assertTrue(record.id, result.length <= KnowledgeContextEngine.HARD_CONTEXT_CEILING * 4)
      assertTrue(record.id, result.contains("<${record.id}>"))
      for (id in listOf("CHAR.IRIS.RUNTIME_CORE", "CHAR.SYVIAL.RUNTIME_CORE",
        "CHAR.LUC_TRAM.REVIVAL_R06", "CHAR.LUC_TRAM.OPEN_R06")) {
        assertTrue(id, result.contains("<$id>"))
      }
    }
  }

  @Test fun npcBehaviorGuidanceFollowsConfirmedPresenceAndRecruitment() {
    val absent = packet()
    assertFalse(absent.contains("Lucia is present"))
    assertFalse(absent.contains("Luc Tram R06 cannot remember"))
    val meeting = packet("""{"pendingNpcEncounters":[{"characterId":"lucia"}]}""")
    assertTrue(meeting.contains("Lucia is present, NOT recruited"))
    assertTrue(meeting.contains("PRESENT, inParty=false"))
    val recruited = packet("""{"party":[{"id":"lucia","presence":"ACTIVE"}]}""")
    assertTrue(recruited.contains("IN_PARTY — recruited Party member"))
    assertFalse(recruited.contains("Lucia is present, NOT recruited"))
  }

}
