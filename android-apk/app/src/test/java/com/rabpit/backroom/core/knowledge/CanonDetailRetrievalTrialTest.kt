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

  private fun fullSupportDatabase(extraArchiveCards: Int = 0): KnowledgeContextEngine.Database {
    val support = JSONObject(asset("canon_support_db.json"))
    val records = support.getJSONArray("records")
    // Synthetic R2 fixture stays inside the test process, never in the public APK
    // or source control. This tests bulk indexing without publishing novel spoilers.
    repeat(extraArchiveCards) { index ->
      records.put(JSONObject()
        .put("id", "TEST.ARCHIVE." + index)
        .put("domain", "SUPPORT").put("kind", "archive-detail")
        .put("text", "TEST_ONLY load fixture " + "example ".repeat(55))
        .put("source", JSONObject().put("document", "TEST_ONLY")
          .put("anchor", "fixture"))
        .put("authority", "TEST_ONLY").put("mutability", "IMMUTABLE")
        .put("priority", 90).put("tags", JSONArray())
        .put("references", JSONArray()).put("affordances", JSONArray()))
    }
    return KnowledgeContextEngine.parseDatabase(
      asset("knowledge_db.json"), asset("canon_detail_db.json"), support.toString())
  }

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
    val db = fullSupportDatabase(1200)
    assertTrue("must index the large stress corpus", db.records.size > 1500)
    assertTrue(db.records.values.any { it.id.startsWith("CHAR.SYVIAL.DETAIL.") &&
      it.kind == "authored-detail" })
    assertTrue(db.records.values.any { it.id.startsWith("SUPPORT.ARCHIVE.SOURCE.") &&
      it.kind == "source-metadata" })
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
    val db = fullSupportDatabase(1200)
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

  @Test fun diePMinhCanonIsGatedByCoreConfirmedEntityEncounter() {
    val db = database()
    val absent = KnowledgeContextEngine.Builder(db, JSONObject(), "Diệp Minh xuất hiện?", JSONObject()).build()
    assertFalse(absent.contains("<ENTITY.DIEP_MINH.VISUAL>"))
    assertFalse(absent.contains("<ENTITY.DIEP_MINH.CANON>"))
    assertFalse(absent.contains("<CHAR.DIEP_MINH.AUTHORED>"))

    val confirmed = JSONObject().put("pendingEntityEncounter",
      JSONObject().put("entityKey", "diep_minh"))
    val seen = KnowledgeContextEngine.Builder(db, confirmed, "quan sát đối thủ", JSONObject()).build()
    assertTrue(seen.contains("<ENTITY.DIEP_MINH.VISUAL>"))
    assertTrue(seen.contains("<ENTITY.DIEP_MINH.CANON>"))
    assertTrue(seen.contains("Hoàng Thiên Mộc Kiếm"))
    assertFalse("Entity does not auto-recruit a character", seen.contains("<CHAR.DIEP_MINH.AUTHORED>"))
    assertTrue(seen.length <= KnowledgeContextEngine.HARD_CONTEXT_CEILING * 4)
  }

  @Test fun diePMinhCharacterDetailsRequireActualNpcPresence() {
    val state = JSONObject().put("pendingNpcEncounters", JSONArray()
      .put(JSONObject().put("characterId", "diep_minh")))
    val present = packet(state, "Hỏi Diệp Minh về kiếm")
    assertTrue(present.contains("<CHAR.DIEP_MINH.AUTHORED>"))
    assertTrue(present.contains("<CHAR.DIEP_MINH.DETAIL."))
    val absent = packet(JSONObject(), "Hỏi Diệp Minh về kiếm")
    assertFalse(absent.contains("<CHAR.DIEP_MINH.DETAIL."))
    assertFalse(absent.contains("<CHAR.DIEP_MINH.AUTHORED>"))
  }

  @Test fun cultivationLadderOnlyAppearsOnRelevantAction() {
    val db = database()
    val explore = KnowledgeContextEngine.Builder(db, JSONObject(), "khám phá hành lang", JSONObject()).build()
    assertFalse(explore.contains("<WORLD.CULTIVATION_REALMS>"))
    assertFalse(explore.contains("<CHAR.CAO_MINH.REALM>"))
    val realm = KnowledgeContextEngine.Builder(db, JSONObject(), "Cao Minh tự kiểm tra cảnh giới tu vi", JSONObject()).build()
    assertTrue(realm.contains("<WORLD.CULTIVATION_REALMS>"))
    assertTrue(realm.contains("<CHAR.CAO_MINH.REALM>"))
    assertTrue(realm.contains("Chí Tôn Đại Viên Mãn"))
    assertFalse("Disallowed numerical claim", realm.contains("100 tiểu cảnh giới"))
    assertTrue(realm.length <= KnowledgeContextEngine.HARD_CONTEXT_CEILING * 4)
  }

  @Test fun diePMinhPublicRecordsExcludeWriterSecretsAndStaleRealms() {
    val db = database()
    val newRecords = db.records.values.filter {
      it.id.startsWith("CHAR.DIEP_MINH.") ||
        it.id == "ENTITY.DIEP_MINH.CANON" || it.id == "WORLD.CULTIVATION_REALMS" ||
        it.id == "CHAR.CAO_MINH.REALM"
    }
    assertTrue(newRecords.size >= 10)
    assertTrue(newRecords.all {
      it.source.document.startsWith("Google Drive /Novel/Asset/") &&
        it.text.isNotBlank() && !it.text.contains("Vô Lượng", ignoreCase = true) &&
        !it.text.contains("WRITER-SECRET") && !it.text.contains("100 tiểu cảnh giới")
    })
    assertEquals(newRecords.map { it.id }.size, newRecords.map { it.id }.toSet().size)
  }
}
