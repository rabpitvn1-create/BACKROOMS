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

  @Test fun activePartyAndConfirmedCurrentRollGrantActorCards() {
    assertTrue(packet("""{"party":[{"id":"lucia","presence":"ACTIVE"}]}""").contains("<CHAR.LUCIA.AUTHORED>"))
    assertFalse(packet("""{"party":[{"id":"lucia","presence":"ABSENT"}]}""").contains("<CHAR.LUCIA.AUTHORED>"))
    assertFalse(packet("""{"party":[{"id":"iris_unknown"}]}""").contains("<CHAR.IRIS."))
    assertTrue(packet("""{"turn":4}""", rolls = """{"turn":4,"luciaEncounter":{"eligible":true,"success":true}}""").contains("<CHAR.LUCIA.AUTHORED>"))
  }

  @Test fun entityMentionAndLevelReferencesCannotInventEncounter() {
    val result = packet("""{"level":{"number":5,"id":"5"}}""", "hound, the beast, Jeff")
    assertFalse(result.contains("<ENTITY.HOUND>"))
    assertFalse(result.contains("<ENTITY.BEAST_LEVEL_5>"))
    assertFalse(result.contains("<ENTITY.JEFF>"))
    assertFalse(result.contains("<ENTITY.HOUND.VISUAL>"))
  }

  @Test fun onlyEligibleCurrentRollOrActiveCombatGrantsEntityVisual() {
    val roll = """{"turn":4,"selectedEntityKey":"hound","entityEncounter":{"eligible":true,"success":true}}"""
    assertTrue(packet("""{"turn":4}""", rolls = roll).contains("<ENTITY.HOUND.VISUAL>"))
    assertFalse(packet("""{"turn":5}""", rolls = roll).contains("<ENTITY.HOUND.VISUAL>"))
    assertFalse(packet("""{"turn":4}""", rolls = JSONObject(roll).put("meta", true).toString()).contains("<ENTITY.HOUND.VISUAL>"))
    assertFalse(packet("""{"turn":4}""", rolls = roll.replace("\"eligible\":true", "\"eligible\":false")).contains("<ENTITY.HOUND.VISUAL>"))
    assertTrue(packet("""{"combat":{"active":true,"entityKey":"hound"}}""").contains("<ENTITY.HOUND.VISUAL>"))
  }

  @Test fun exactNodeAndParentStaySeparate() {
    val result = packet("""{"level":{"number":0,"id":"red_rooms"}}""")
    assertTrue(result.contains("<LEVEL.00.AUTHORED>"))
    assertTrue(result.contains("<LEVEL.NODE.red_rooms>"))
    assertFalse(result.contains("<LEVEL.01.AUTHORED>"))
    assertFalse(packet("""{"level":{"number":7,"id":"7"}}""").contains("<LEVEL.07.AUTHORED>"))
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
    assertFalse(result.contains("<CHAR.CAO_MINH.AUTHORED>"))
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
}
