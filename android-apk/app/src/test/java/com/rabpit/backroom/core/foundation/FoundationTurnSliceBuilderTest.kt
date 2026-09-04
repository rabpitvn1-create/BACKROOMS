package com.rabpit.backroom.core.foundation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FoundationTurnSliceBuilderTest {
  @get:Rule val temporaryFolder = TemporaryFolder()

  @Test
  fun characterAuditReceivesPartyCanonButNotWorldCatalog() {
    val knowledge = """{"records":[
      {"id":"GAME.TEXT.CORE","domain":"GAME","text":"Core rule","priority":100},
      {"id":"CHAR.IRIS.RUNTIME_CORE","domain":"CHARACTER","text":"Iris gọi Kai là anh.","priority":100},
      {"id":"LEVEL.00","domain":"LEVEL","text":"Level zero yellow rooms.","priority":100}
    ]}"""
    val source = FoundationSource("knowledge/knowledge_db.json", FoundationDigest.sha256(knowledge), knowledge)
    val compiler = FoundationCompiler()
    val build = compiler.compile(listOf(source), "{}")
    val store = FoundationStore(temporaryFolder.newFolder("slice"), JvmAtomicPointerCommitter)
    build.objects.forEach(store::putObject)
    val manifest = compiler.manifest(build)
    store.putManifest(manifest)
    val slice = FoundationTurnSliceBuilder(store).build(
      FoundationHandle(manifest),
      """{"location":"Level 0","party":[{"id":"iris"}]}""",
      "Tôi hỏi Iris.",
      "{}",
      FoundationSliceRole.CHARACTER_AUDIT
    )

    assertTrue(slice.contains("manifestId=${manifest.manifestId}"))
    assertTrue(slice.contains("CHAR.IRIS.RUNTIME_CORE"))
    assertFalse(slice.contains("LEVEL.00"))
  }
}
