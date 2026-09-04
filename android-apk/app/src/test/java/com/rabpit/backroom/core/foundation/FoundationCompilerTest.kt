package com.rabpit.backroom.core.foundation

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoundationCompilerTest {
  private val compiler = FoundationCompiler()

  @Test
  fun compilesAllSectionsDeterministically() {
    val first = compiler.compile(sources("Iris giữ đúng xưng hô."), projection())
    val second = compiler.compile(sources("Iris giữ đúng xưng hô.").reversed(), projection())

    assertEquals(first.sourcePackHash, second.sourcePackHash)
    assertEquals(FoundationSection.entries.toSet(), first.objects.map { it.section }.toSet())
    assertEquals(first.objects.map { it.objectHash }, second.objects.map { it.objectHash })
    first.objects.forEach { assertEquals(it.objectHash, FoundationDigest.sha256(it.json)) }
  }

  @Test
  fun characterOnlyChangeInvalidatesPartySection() {
    val before = compiler.compile(sources("Iris giữ đúng xưng hô."), projection())
    val after = compiler.compile(sources("Iris gọi Kai là anh."), projection())
    val beforeHashes = before.objects.associate { it.section to it.objectHash }
    val afterHashes = after.objects.associate { it.section to it.objectHash }

    assertNotEquals(before.sourcePackHash, after.sourcePackHash)
    assertNotEquals(beforeHashes[FoundationSection.PARTY], afterHashes[FoundationSection.PARTY])
    FoundationSection.entries.filter { it != FoundationSection.PARTY }.forEach { section ->
      assertEquals("unexpected invalidation for $section", beforeHashes[section], afterHashes[section])
    }
  }

  @Test
  fun manifestIdentityIgnoresWallClock() {
    val build = compiler.compile(sources("Iris giữ đúng xưng hô."), projection())
    val first = compiler.manifest(build, 1L)
    val second = compiler.manifest(build, 2L)
    assertEquals(first.manifestId, second.manifestId)
    assertTrue(first.manifestId.matches(Regex("[a-f0-9]{64}")))
  }

  private fun sources(characterText: String): List<FoundationSource> {
    val knowledge = JSONObject("""{
      "records": [
        {"id":"GAME.TEXT.CORE","domain":"GAME","text":"Core rule","priority":100},
        {"id":"CHAR.IRIS.RUNTIME_CORE","domain":"CHARACTER","text":"$characterText","priority":100},
        {"id":"WRITING.PLAYER_AGENCY","domain":"WRITING","text":"Agency rule","priority":100}
      ]
    }""").toString()
    val level = """{"id":"level-0","name":"The Lobby"}"""
    return listOf(
      FoundationSource("knowledge/knowledge_db.json", FoundationDigest.sha256(knowledge), knowledge),
      FoundationSource("levels/level-0.json", FoundationDigest.sha256(level), level)
    )
  }

  private fun projection(): String = """{
    "projectionSchemaVersion":1,
    "saveVersion":5,
    "level":{"id":"level-0"},
    "party":{"memberIds":["kai","iris"]},
    "characters":{"iris":{"presence":"ACTIVE"}},
    "story":{"questId":"Q1"}
  }"""
}
