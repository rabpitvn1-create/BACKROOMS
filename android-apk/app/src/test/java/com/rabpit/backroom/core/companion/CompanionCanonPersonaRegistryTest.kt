package com.rabpit.backroom.core.companion

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.*
import org.junit.Test

class CompanionCanonPersonaRegistryTest {
  private val reader = CompanionCanonPersonaRegistry.SourceReader { path ->
    val candidates = listOf(Paths.get("src/main/assets", path), Paths.get("app/src/main/assets", path),
      Paths.get("android-apk/app/src/main/assets", path))
    Files.readAllBytes(candidates.first { Files.isRegularFile(it) })
  }
  private fun load(actor: String) = CompanionCanonPersonaRegistry.load(actor, reader)
  private fun rejects(reason: String, operation: () -> Unit) {
    try { operation(); fail("expected " + reason) } catch (e: IOException) { assertEquals(reason, e.message) }
  }

  @Test fun packagedCurrentCaoMinhSourceIsPinnedAndKeepsKnowledgeNamespace() {
    val p = load("cao_minh")
    assertEquals("R17", p.sourceRevision)
    assertEquals("CHAR.KAI", p.knowledgeNamespace)
    assertEquals("c78ba2978c4635b83c9cc64385a6e6b3ebeaf57dbfa75f576ff0a84dc4b5e61c", p.sourceSha256)
    assertEquals(listOf("CAO-LIFE-02"), p.ethicalRefs)
    assertTrue(p.voiceRefs.contains("CAO-DLG-R17"))
    assertTrue(p.knowledgeLockRefs.contains("CAO-BACKROOMS-01"))
  }

  @Test fun lucTramR05DialogueOverrideIsNotMistakenForReadFirstR04() {
    val p = load("luc_tram")
    assertEquals("R05", p.sourceRevision)
    assertEquals("CHAR.LUC_TRAM", p.knowledgeNamespace)
    assertEquals("26e6a407f4dcccdf0e74c3f855d577609a803eacf524af4679cbf3752758f100", p.sourceSha256)
    assertTrue(p.ethicalRefs.contains("LUC-READ-FIRST-R04"))
    assertTrue(p.voiceRefs.contains("LUC-DIALOGUE-MASTER-R05"))
    assertTrue(p.knowledgeLockRefs.contains("LUC-BACKROOMS-R03"))
  }

  @Test fun knownActorsReadOnlyTheirFixedOwnedSourcePath() {
    val paths = arrayListOf<String>()
    for (actor in listOf("cao_minh", "luc_tram")) {
      CompanionCanonPersonaRegistry.load(actor) { path -> paths.add(path); reader.read(path) }
    }
    assertEquals(listOf("knowledge/novel_asset/CAO_MINH_CODEX.md",
      "knowledge/novel_asset/LUC_TRAM_CODEX.md"), paths)
  }

  @Test fun unknownActorAndLegacyIdentityDoNotDefaultToCaoMinh() {
    // The identity overlay rewrites whole quoted legacy ID tokens across app sources.
    // Construct the legacy input at runtime so generated tests still exercise that ID.
    val legacyActor = charArrayOf('k', 'a', 'i').concatToString()
    for (actor in listOf(legacyActor, "Lucia", "unknown", "", "../cao_minh")) {
      rejects("persona_actor_unsupported") {
        CompanionCanonPersonaRegistry.load(actor) { error("unsupported actor must not read") }
      }
    }
  }

  @Test fun anotherActorsSourceAndEditedCanonRejectWithoutFallback() {
    val other = reader.read("knowledge/novel_asset/LUC_TRAM_CODEX.md")
    rejects("persona_source_mismatch") { CompanionCanonPersonaRegistry.load("cao_minh") { other } }
    val edited = reader.read("knowledge/novel_asset/CAO_MINH_CODEX.md").copyOf()
    edited[0] = (edited[0].toInt() xor 1).toByte()
    rejects("persona_source_mismatch") { CompanionCanonPersonaRegistry.load("cao_minh") { edited } }
  }

  @Test fun sourceIsBoundedAndReadErrorsPropagate() {
    for (bytes in listOf(ByteArray(0), ByteArray(262145))) {
      rejects("persona_source_bound") { CompanionCanonPersonaRegistry.load("cao_minh") { bytes } }
    }
    rejects("asset_missing") { CompanionCanonPersonaRegistry.load("cao_minh") { throw IOException("asset_missing") } }
  }

  @Test fun personaReferencesAreImmutableAndRecheckingDoesNotMutateThem() {
    val p = load("cao_minh")
    for (refs in listOf(p.traitRefs, p.ethicalRefs, p.voiceRefs, p.knowledgeLockRefs)) {
      try { (refs as MutableList<String>).clear(); fail("mutable persona refs") }
      catch (_: UnsupportedOperationException) { }
    }
    assertSame(p, load("cao_minh"))
    assertEquals(CompanionCanonPersonaRegistry.VERSION, p.registryVersion)
  }

  @Test fun everyLoadRevalidatesSourceEvenAfterSuccessfulInitialization() {
    val bytes = reader.read("knowledge/novel_asset/CAO_MINH_CODEX.md").copyOf()
    val verified = CompanionCanonPersonaRegistry.load("cao_minh") { bytes }
    bytes[0] = (bytes[0].toInt() xor 1).toByte()
    rejects("persona_source_mismatch") { CompanionCanonPersonaRegistry.load("cao_minh") { bytes } }
    assertEquals("R17", verified.sourceRevision)
    assertEquals(listOf("CAO-LIFE-02"), verified.ethicalRefs)
  }
}
