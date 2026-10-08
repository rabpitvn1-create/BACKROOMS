package com.rabpit.backroom.core.knowledge

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class NovelAssetRoutingTest {
  private val knowledgeJson: String by lazy {
    val options = listOf(
      Path.of("src/main/assets/knowledge/knowledge_db.json"),
      Path.of("app/src/main/assets/knowledge/knowledge_db.json"),
      Path.of("android-apk/app/src/main/assets/knowledge/knowledge_db.json")
    )
    String(Files.readAllBytes(options.firstOrNull { Files.isRegularFile(it) }
      ?: error("Missing source-backed knowledge database")), Charsets.UTF_8)
  }

  private fun packet(action: String, known: Boolean = false, present: Boolean = false): String {
    val state = JSONObject().put("title", "MAIN_BACKROOMS - Cao Minh")
      .put("turn", 6)
      .put("level", JSONObject().put("number", 0))
      .put("flags", JSONObject().put("tracLamContacted", known))
    if (present) {
      state.put("party", JSONArray().put(JSONObject().put("id", "trac_lam")))
    }
    return KnowledgeContextEngine.buildForTest(knowledgeJson, state.toString(), action, "{}")
  }

  @Test fun relevantCharacterAndWorldSourcesAreSelectable() {
    assertTrue(packet("Tôi hỏi Lục Trầm về Tịch Quang.")
      .contains("<NOVEL_ASSET.LUC_TRAM_CODEX."))
    assertTrue(packet("Kiểm tra Huyết Ma Chiến Khải.")
      .contains("<NOVEL_ASSET.CAO_MINH_CODEX."))
    assertTrue(packet("Tìm hiểu linh khí.")
      .contains("<NOVEL_ASSET.BACKROOMS_LINH_KHI."))
  }

  @Test fun tracLamKnowledgeIsContactGated() {
    assertFalse(packet("Tìm hồ sơ Trác Lâm của SRU-03.")
      .contains("<NOVEL_ASSET.TRAC_LAM_CODEX."))
    assertTrue(packet("Tôi hỏi Trác Lâm.", known = true)
      .contains("<NOVEL_ASSET.TRAC_LAM_CODEX."))
    assertTrue(packet("Quan sát hành lang.", present = true)
      .contains("<NOVEL_ASSET.TRAC_LAM_CODEX.C0001>"))
  }

  @Test fun quietTurnDoesNotAutoRevealWriterSecrets() {
    val quiet = packet("Tiếp tục quan sát hành lang yên tĩnh.")
    assertFalse(quiet.contains("<NOVEL_ASSET.HUYET_TAY_CAO_GIA."))
    assertFalse(quiet.contains("<NOVEL_ASSET.TANG_KIEM_COC."))
    assertFalse(quiet.contains("<NOVEL_ASSET.TRAC_LAM_CODEX."))
  }
}
