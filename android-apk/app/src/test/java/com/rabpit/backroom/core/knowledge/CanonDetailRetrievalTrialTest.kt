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

  @Test fun supportDocumentsAreIndexedButSpoilerArchiveIsNotRetrievable() {
    val db = KnowledgeContextEngine.parseDatabase(
      asset("knowledge_db.json"), asset("canon_detail_db.json"), asset("canon_support_db.json"))
    assertTrue("must index the large support corpus", db.records.size > 1200)
    assertTrue(db.records.values.any { it.id.startsWith("CHAR.SYVIAL.DETAIL.") &&
      it.kind == "authored-detail" })
    assertTrue(db.records.values.any { it.id.startsWith("SUPPORT.HUYET_TAY_CAO_GIA.DETAIL.") &&
      it.kind == "archive-detail" })
    for (record in db.records.values.filter {
      it.id.startsWith("SUPPORT.") || it.id.startsWith("CHAR.SYVIAL.DETAIL.")
    }) {
      assertTrue(record.id, record.source.document.startsWith("Google Drive /Novel/Asset/"))
      assertTrue(record.id, record.text.isNotBlank())
    }
    val state = JSONObject().put("level", JSONObject().put("number", 0).put("id", "0"))
    val result = KnowledgeContextEngine.Builder(db, state,
      "Kể lại Huyết Tẩy Cao Gia tại Táng Kiếm Cốc", JSONObject()).build()
    assertFalse("no undiscovered historical narrative", result.contains("<SUPPORT.HUYET_TAY_CAO_GIA."))
    assertFalse("no undiscovered sword narrative", result.contains("<SUPPORT.TANG_KIEM_COC."))
  }

  @Test fun supportRetrievalFromActionStaysWithinPacketHardCeiling() {
    val db = KnowledgeContextEngine.parseDatabase(
      asset("knowledge_db.json"), asset("canon_detail_db.json"), asset("canon_support_db.json"))
    val state = JSONObject().put("level", JSONObject().put("number", 0).put("id", "0"))
    val cases = listOf(
      "Cao Minh cảm nhận linh khí trong Backrooms",
      "Hỏi về hồi phục linh lực của tu sĩ",
      "Quan sát viện nghiên cứu ASYNC và dự án KV31",
      "Nói chuyện với Lucia"
    )
    val npcState = JSONObject(state.toString()).put("pendingNpcEncounters",
      JSONArray().put(JSONObject().put("characterId", "lucia")))
    val start = System.nanoTime()
    for (attempt in 0 until 12) for (action in cases) {
      val inputState = if (action.contains("Lucia")) npcState else state
      val result = KnowledgeContextEngine.Builder(db, inputState, action, JSONObject()).build()
      assertTrue(action, result.length <= KnowledgeContextEngine.HARD_CONTEXT_CEILING * 4)
      assertTrue(action, result.endsWith("[END_KNOWLEDGE_PACKET]"))
      assertFalse("archive hidden", result.contains("<SUPPORT.HUYET_TAY_CAO_GIA."))
      if (action.contains("linh khí")) assertTrue("aura support", result.contains("<SUPPORT.LINH_KHI."))
      if (action.contains("KV31")) assertTrue("ASYNC support", result.contains("<SUPPORT.ASYNC."))
    }
    val elapsedMs = (System.nanoTime() - start) / 1_000_000
    println("CanonR2SupportStress: cards=" + db.records.size +
      ", 48 offline packets, elapsedMs=" + elapsedMs +
      ", hardCeiling=" + KnowledgeContextEngine.HARD_CONTEXT_CEILING)
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
