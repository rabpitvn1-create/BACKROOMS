package com.rabpit.backroom.core.companion

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Collections

/**
 * Immutable canon source descriptors. These are not campaign memories or prompt text.
 * Only explicitly pinned actor-owned sources are accepted; revision mismatch fails closed.
 */
internal object CompanionCanonPersonaRegistry {
  const val VERSION = "companion_canon_persona.v1"
  fun interface SourceReader { fun read(assetPath: String): ByteArray }

  class Persona internal constructor(
    val actorId: String, val knowledgeNamespace: String,
    val sourcePath: String, val sourceRevision: String, val sourceSha256: String,
    traitRefs: List<String>, ethicalRefs: List<String>, voiceRefs: List<String>,
    knowledgeLockRefs: List<String>
  ) {
    val traitRefs: List<String> = freeze(traitRefs)
    val ethicalRefs: List<String> = freeze(ethicalRefs)
    val voiceRefs: List<String> = freeze(voiceRefs)
    val knowledgeLockRefs: List<String> = freeze(knowledgeLockRefs)
    val registryVersion: String get() = VERSION
    private fun freeze(refs: List<String>): List<String> =
      Collections.unmodifiableList(ArrayList(refs))
  }

  private val profiles = mapOf(
    "cao_minh" to Persona(
      "cao_minh", "CHAR.KAI", "knowledge/novel_asset/CAO_MINH_CODEX.md", "R17",
      "c78ba2978c4635b83c9cc64385a6e6b3ebeaf57dbfa75f576ff0a84dc4b5e61c",
      listOf("CAO-PER-01", "CAO-PREF-01"),
      listOf("CAO-LIFE-02"),
      listOf("CAO-DLG-R17", "CAO-DLG-HARD-01", "CAO-DLG-MECHANICS-01"),
      listOf("CAO-BACKROOMS-01", "CAO-VOICE-SAMPLES-02")
    ),
    "luc_tram" to Persona(
      "luc_tram", "CHAR.LUC_TRAM", "knowledge/novel_asset/LUC_TRAM_CODEX.md", "R05",
      "26e6a407f4dcccdf0e74c3f855d577609a803eacf524af4679cbf3752758f100",
      listOf("LUC-PER-R02", "LUC-SELF-01"),
      listOf("LUC-READ-FIRST-R04", "LUC-CANON-R03"),
      listOf("LUC-DIALOGUE-MASTER-R05", "LUC-DIALOGUE-R05"),
      listOf("LUC-BACKROOMS-R03", "LUC-AGENCY-R03", "LUC-DIALOGUE-SAMPLES-R05")
    )
  )

  /** Registry descriptor only; does NOT verify packaged bytes. Native create/open must call load(). */
  fun descriptor(actorId: String): Persona =
    profiles[actorId] ?: throw IOException("persona_actor_unsupported")

  fun accepts(persona: Persona): Boolean {
    val pinned=profiles[persona.actorId] ?: return false
    return persona.knowledgeNamespace == pinned.knowledgeNamespace && persona.sourcePath == pinned.sourcePath &&
      persona.sourceRevision == pinned.sourceRevision && persona.sourceSha256 == pinned.sourceSha256 &&
      persona.traitRefs == pinned.traitRefs && persona.ethicalRefs == pinned.ethicalRefs &&
      persona.voiceRefs == pinned.voiceRefs && persona.knowledgeLockRefs == pinned.knowledgeLockRefs
  }

  fun acceptsContext(packet: ActorContextBuilder.Packet): Boolean {
    val pinned=profiles[packet.actorId] ?: return false
    return packet.pins.personaRevision == pinned.sourceRevision && packet.pins.personaSha256 == pinned.sourceSha256 &&
      packet.canonRefs.traitRefs == pinned.traitRefs && packet.canonRefs.ethicalRefs == pinned.ethicalRefs &&
      packet.canonRefs.voiceStyleRefs == pinned.voiceRefs
  }

  fun load(actorId: String, reader: SourceReader): Persona {
    val persona = profiles[actorId] ?: throw IOException("persona_actor_unsupported")
    val input = reader.read(persona.sourcePath)
    if (input.isEmpty() || input.size > 262144) throw IOException("persona_source_bound")
    val bytes = input.copyOf()
    if (CompanionDigests.sha256(bytes) != persona.sourceSha256) throw IOException("persona_source_mismatch")
    val source = try {
      StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: java.nio.charset.CharacterCodingException) { throw IOException("persona_source_encoding") }
    val refs = persona.traitRefs + persona.ethicalRefs + persona.voiceRefs + persona.knowledgeLockRefs
    if (refs.any { !source.contains(it) }) throw IOException("persona_source_reference_missing")
    return persona
  }
}
