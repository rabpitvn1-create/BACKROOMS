package com.rabpit.backroom.core.foundation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FoundationStoreTest {
  @get:Rule val temporaryFolder = TemporaryFolder()

  @Test
  fun persistsAndActivatesCompleteManifest() {
    val store = FoundationStore(temporaryFolder.newFolder("foundation"), JvmAtomicPointerCommitter)
    val compiler = FoundationCompiler()
    val source = FoundationSource("levels/level-0.json", FoundationDigest.sha256("{}"), "{}")
    val build = compiler.compile(listOf(source), "{}")
    build.objects.forEach(store::putObject)
    val manifest = compiler.manifest(build, 42L)

    store.putManifest(manifest)
    store.activate(manifest)

    assertEquals(manifest.manifestId, store.loadActive()?.manifest?.manifestId)
    assertEquals(build.objects.first().json, store.readObject(build.objects.first().objectHash))
  }

  @Test
  fun corruptedObjectFailsClosedWithoutTouchingGameSave() {
    val root = temporaryFolder.newFolder("foundation-corrupt")
    val store = FoundationStore(root, JvmAtomicPointerCommitter)
    val content = "{\"safe\":true}"
    val hash = FoundationDigest.sha256(content)
    val objectFile = root.resolve("objects/$hash.json")
    objectFile.writeText("corrupt")

    assertNull(store.readObject(hash))
    assertTrue(root.resolve("quarantine").listFiles().orEmpty().any { it.name.contains(hash) })
    assertTrue("Foundation must not own a game-state save path", !root.resolve("game_state").exists())
  }
}
