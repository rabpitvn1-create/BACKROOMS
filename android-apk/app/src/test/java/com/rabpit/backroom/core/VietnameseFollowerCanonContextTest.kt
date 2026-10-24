package com.rabpit.backroom.core

import com.rabpit.backroom.core.canon.*
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class VietnameseFollowerCanonContextTest {
  /** Gradle runs JVM tests from app/, while local invocations may start at the repo root. */
  private fun root(): File = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
    .flatMap { sequenceOf(it, File(it, "android-apk")) }
    .firstOrNull { File(it, "app/src/main/assets/knowledge/knowledge_db.json").isFile }
    ?: error("Cannot locate Android canon fixtures from ${System.getProperty("user.dir")}")
  private fun corpus(): String = File(root(), "app/src/main/assets/knowledge/knowledge_db.json").readText()
  private fun state(vararg ids: String): JSONObject = JSONObject().put("turn", 7)
    .put("followerEncounters", JSONArray().apply {
      ids.forEach { put(JSONObject().put("id", it).put("presence", "ACTIVE")) }
    })
  private fun locked(key: String, turn: Int = 7) = JSONObject().put("turn", turn)
    .put(key, JSONObject().put("eligible", true).put("success", true))
  private fun packet(state: JSONObject, action: String, rolls: JSONObject = JSONObject().put("turn", 7)): String =
    VietnameseFollowerCanonContext.buildFromJson(corpus(), state.toString(), action, rolls.toString())

  @Test fun actualCorpusParsesWorldAndMetaRulesWithCorrectVisibility() {
    val records = CanonRecord.parseAll(JSONObject(corpus()))
    assertEquals(KnowledgeVisibility.META, records.first { it.id == "GAME.TEXT.CORE" }.knowledgeVisibility)
    assertEquals(KnowledgeVisibility.WORLD, records.first { it.id == "WORLD.CORE" }.knowledgeVisibility)
    val store = JsonCanonStore.parse(corpus())
    val scene = LegacyJsonSceneResolver().resolve(state().toString(), "đi về phía trước", "{\"turn\":7}", false)
    val selection = CanonResolver(store).resolve(scene, store.snapshot())
    assertTrue(selection.required.any { it.id == "GAME.TEXT.CORE" })
    assertTrue(selection.required.any { it.id == "WORLD.CORE" })
  }

  @Test fun mentioningAbsentNamesDoesNotOpenTheirCards() {
    val absent = state()
    val action = "Tôi gọi Nguyễn Quốc Nam và hỏi Hứa Tiểu Lan về Anh Tý"
    assertEquals("", packet(absent, action))
    val store = JsonCanonStore.parse(corpus())
    val scene = LegacyJsonSceneResolver().resolve(absent.toString(), action, "{\"turn\":7}", false)
    val selection = CanonResolver(store).resolve(scene, store.snapshot())
    assertFalse((selection.required + selection.optional).any {
      it.entityId in setOf("CHAR.NGUYEN_QUOC_NAM", "CHAR.HUA_TIEU_LAN")
    })
  }

  @Test fun corePresenceUsesExactIdsAndHonoursInactiveAndEmptyProjection() {
    val missing = state().put("followerEncounters", JSONArray()
      .put(JSONObject().put("id", NGUYEN_QUOC_NAM_ID).put("presence", "MISSING")))
    assertEquals("", packet(missing, "hỏi Nam về gia đình"))
    val stale = state().put("partyDetails", JSONObject().put("members", JSONArray()))
      .put("party", JSONArray().put(JSONObject().put("id", HUA_TIEU_LAN_ID).put("presence", "ACTIVE")))
    assertEquals("", packet(stale, "hỏi Tiểu Lan"))
    val scene = LegacyJsonSceneResolver().resolve(state(NGUYEN_QUOC_NAM_ID, HUA_TIEU_LAN_ID).toString(), "hỏi chuyện", "{\"turn\":7}", false)
    assertTrue(NGUYEN_QUOC_NAM_ID in scene.actorsPresent)
    assertTrue(HUA_TIEU_LAN_ID in scene.actorsPresent)
    assertFalse("nguyen-quoc-nam" in scene.actorsPresent)
  }

  @Test fun firstMeetingExceptionNeedsCurrentLockedSuccessAndDoesNotRevealReincarnation() {
    val first = packet(state(), "bước qua cửa", locked("huaTieuLanEncounter"))
    assertTrue(first.contains("CHAR.HUA_TIEU_LAN.FIRST_ENCOUNTER"))
    assertTrue(first.contains("gọi thành tiếng Anh Tý"))
    assertFalse(first.contains("REINCARNATION_SECRET"))
    assertFalse(first.contains("Anh Tý chuyển sinh thành Cao Minh"))
    assertFalse(first.contains("CHAR.NGUYEN_QUOC_NAM"))
    assertEquals("", packet(state(), "bước qua cửa", locked("huaTieuLanEncounter", 6)))
    val failed = locked("huaTieuLanEncounter").apply { getJSONObject("huaTieuLanEncounter").put("success", false) }
    assertEquals("", packet(state(), "bước qua cửa", failed))
    val later = packet(state(HUA_TIEU_LAN_ID), "hỏi chuyện")
    assertFalse(later.contains("FIRST_ENCOUNTER"))
    assertTrue(later.contains("ADDR.HUA_TIEU_LAN.CAO_MINH"))
  }

  @Test fun categorySelectionIsBoundedAndUnknownIsNeverPromoted() {
    val basic = packet(state(NGUYEN_QUOC_NAM_ID), "đi tiếp")
    assertTrue(basic.contains("CHAR.NGUYEN_QUOC_NAM.RUNTIME_CORE"))
    assertFalse(basic.contains("CHAR.NGUYEN_QUOC_NAM.FAMILY"))
    val family = packet(state(NGUYEN_QUOC_NAM_ID), "hỏi về vợ và con gái")
    assertTrue(family.contains("CHAR.NGUYEN_QUOC_NAM.FAMILY"))
    assertFalse(family.contains("CHAR.NGUYEN_QUOC_NAM.ULTIMATE"))
    val lan = packet(state(HUA_TIEU_LAN_ID), "hỏi chuyện")
    assertTrue(lan.contains("[CHAR.HUA_TIEU_LAN.UNKNOWN_FIELDS] [UNKNOWN]"))
    assertFalse(lan.contains("[CHAR.HUA_TIEU_LAN.UNKNOWN_FIELDS] [CONFIRMED]"))
    val busy = packet(state(NGUYEN_QUOC_NAM_ID, HUA_TIEU_LAN_ID),
      "hỏi về gia đình, ngoại hình, quá khứ, tình cảm, súng và chiến đấu")
    assertTrue(CanonTokens.estimate(busy) <= 1400)
    assertTrue(busy.endsWith("[END_NAMED_FOLLOWER_CANON]"))
    assertTrue(busy.contains("CHAR.HUA_TIEU_LAN.LIMITS"))
    assertTrue(busy.contains("CHAR.NGUYEN_QUOC_NAM.LIMITS"))
  }

  @Test fun cr2FetchesSelectedCategoriesForUnderscoreActorIdsWithoutCodexExpansion() {
    val store = JsonCanonStore.parse(corpus())
    val scene = LegacyJsonSceneResolver().resolve(state(NGUYEN_QUOC_NAM_ID).toString(), "hỏi Nam về gia đình", "{\"turn\":7}", false)
    val selection = CanonResolver(store).resolve(scene, store.snapshot())
    val selected = selection.required + selection.optional
    assertTrue(selected.any { it.id == "CHAR.NGUYEN_QUOC_NAM.RUNTIME_CORE" })
    assertTrue(selected.any { it.id == "CHAR.NGUYEN_QUOC_NAM.FAMILY" })
    assertFalse(selected.any { it.id == "CHAR.NGUYEN_QUOC_NAM.SKILLS" })
    assertFalse(selected.any { it.entityId == "CHAR.HUA_TIEU_LAN" })
  }

  @Test fun everyNewCardHasExactSourceHashAndHeadingAnchorAndSecretIsNeverShow() {
    val records = CanonRecord.parseAll(JSONObject(corpus())).filter {
      it.entityId in setOf("CHAR.NGUYEN_QUOC_NAM", "CHAR.HUA_TIEU_LAN")
    }
    assertEquals(30, records.size)
    records.forEach { record ->
      val file = File(root(), "canon_sources/Characters/${record.source.documentId}.md")
      val bytes = file.readBytes()
      val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
      assertEquals(record.id, hash, record.source.contentSha256)
      assertTrue(record.id, file.readText().contains(record.source.anchor))
    }
    val secret = records.first { it.id == "CHAR.HUA_TIEU_LAN.REINCARNATION_SECRET" }
    assertEquals(KnowledgeVisibility.RESTRICTED, secret.knowledgeVisibility)
    assertEquals(SpoilerPolicy.NEVER_SHOW, secret.spoilerPolicy)
    val packet = packet(state(HUA_TIEU_LAN_ID), "hỏi về Anh Tý chuyển sinh và ký ức kiếp trước")
    assertFalse(packet.contains("REINCARNATION_SECRET"))
    assertFalse(packet.contains("Sự thật hậu trường"))
  }

  @Test fun actualCr2CompilerFitsNamedPartyAndFirstEncounterWithinRuntimeBudget() {
    val store = JsonCanonStore.parse(corpus())
    val cases = listOf(
      state(NGUYEN_QUOC_NAM_ID, HUA_TIEU_LAN_ID) to JSONObject().put("turn", 7),
      state() to locked("huaTieuLanEncounter"))
    for ((current, rolls) in cases) {
      val scene = LegacyJsonSceneResolver().resolve(current.toString(), "hỏi chuyện và quan sát", rolls.toString(), false)
      val selection = CanonResolver(store).resolve(scene, store.snapshot())
      val result = ContextCompiler().compile(scene, selection, ProviderProfile(),
        ContextBudget(1_048_576, 2048, 512, 250_000, 6000))
      assertTrue(result.toString(), result is ContextPacketResult.Ready)
      result as ContextPacketResult.Ready
      assertTrue(result.estimatedTokens <= 6000)
      assertTrue(result.includedIds.contains("CHAR.HUA_TIEU_LAN.RUNTIME_CORE"))
      assertFalse(result.includedIds.contains("CHAR.HUA_TIEU_LAN.REINCARNATION_SECRET"))
    }
  }
}
