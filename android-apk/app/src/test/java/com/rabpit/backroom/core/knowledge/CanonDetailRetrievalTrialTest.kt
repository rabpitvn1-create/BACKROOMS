package com.rabpit.backroom.core.knowledge

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Offline R2 author-canon integration; Milestone is intentionally absent. */
class CanonDetailRetrievalTrialTest {
  private fun asset(name: String): String = listOf(
    File("src/main/assets/knowledge/${name}"),
    File("app/src/main/assets/knowledge/${name}")
  ).first { it.isFile }.readText()

  private fun database() = KnowledgeContextEngine.parseDatabase(
    asset("knowledge_db.json"), asset("canon_detail_db.json"))

  private fun packet(state: JSONObject, action: String): String =
    KnowledgeContextEngine.Builder(database(), state, action, JSONObject()).build()

  @Test fun detailCatalogIsMergedAndSourceTraceable() {
    val db = database()
    assertTrue(db.records.size > 89)
    assertTrue(db.records.values.count { it.kind == "authored-detail" } >= 100)
    assertTrue(db.records.values.filter { it.kind == "authored-detail" }.all {
      it.source.document.startsWith("Google Drive /Novel/Asset/") &&
        it.text.isNotBlank() && it.text.length < 800 &&
        !it.text.contains("[WRITER-SECRET]")
    })
    assertTrue(db.records.containsKey("CHAR.CAO_MINH.AUTHORED"))
  }

  @Test fun firstMeetingLoadsLuciaDetailsButNeverUnconfirmedLucTram() {
    val state = JSONObject()
      .put("level", JSONObject().put("number", 0).put("id", "0"))
      .put("pendingNpcEncounters", JSONArray().put(
        JSONObject().put("characterId", "lucia").put("name", "Lucia Lục")))
    val result = packet(state, "nói chuyện với Lucia")
    assertTrue("confirmed Lucia card", result.contains("<CHAR.LUCIA.AUTHORED>"))
    assertTrue("contextual Lucia detail", result.contains("<CHAR.LUCIA.DETAIL."))
    assertFalse("no unconfirmed Lục Trầm detail", result.contains("<CHAR.LUC_TRAM.DETAIL."))
    assertTrue(result.contains("[END_KNOWLEDGE_PACKET]"))
  }

  @Test fun mentioningAbsentActorNeverPromotesHerToPresentCanon() {
    val state = JSONObject().put("level", JSONObject().put("number", 4).put("id", "4"))
    val result = packet(state, "Hỏi xem Lục Trầm ở đâu")
    assertFalse(result.contains("<CHAR.LUC_TRAM.DETAIL."))
    assertFalse(result.contains("<CHAR.LUC_TRAM.AUTHORED>"))
  }

  @Test fun environmentLookupUsesCurrentLevelWithoutFutureLevelLeak() {
    val state = JSONObject().put("level", JSONObject().put("number", 0).put("id", "0"))
    val result = packet(state, "khám phá môi trường")
    assertTrue(result.contains("<LEVEL.00.AUTHORED>"))
    assertTrue("Level 0 authored detail", result.contains("<LEVEL.00.DETAIL.") ||
      result.contains("<WORLD.DETAIL."))
    assertFalse(result.contains("<LEVEL.01.DETAIL."))
    assertTrue(result.length <= KnowledgeContextEngine.HARD_CONTEXT_CEILING * 4)
  }
}
