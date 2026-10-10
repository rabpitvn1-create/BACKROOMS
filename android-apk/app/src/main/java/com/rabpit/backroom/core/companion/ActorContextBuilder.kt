package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.companion.BrainContracts.BrainState
import com.rabpit.backroom.core.companion.MemoryRetrieval.MemoryView
import org.json.JSONObject
import org.json.JSONArray
import java.util.Collections

/**
 * C1 actor-private context builder with canon/knowledge firewall (issue #509).
 *
 * Assembles the private prompt packet for ONE actor. Firewall rules (enforced
 * here, not by convention):
 *
 * 1. Persona: whitelisted registry refs ONLY (trait/ethical ref IDs). Never the
 *    whole Codex text; voice samples are style references, never campaign
 *    history; knowledge locks are locks, not narrative facts.
 * 2. Brain/memory: actor-owned only, filtered BEFORE relevance/budget. Another
 *    actor's POV, private data, or hypothetical episodes can never enter.
 * 3. Scene evidence: public projections only (PublicEventProjection); raw GM
 *    payloads are rejected, not redacted.
 * 4. TOLD/INFERRED/DISPUTED entries carry explicit stance + provenance labels.
 *    OPEN claims stay OPEN; dynamic pronouns/relations resolve from campaign
 *    state, never from canon speculation.
 * 5. Version pins (persona revision, rule version, policy version) are embedded;
 *    a pin mismatch fails the build.
 *
 * This builder does NOT modify the GM KnowledgeContextEngine and never reuses
 * it as a private packet. No provider calls.
 *
 * Pure Kotlin: no Android, no I/O.
 */
internal object ActorContextBuilder {
  class SceneEvidence(val slotId: String, val ownerActorId: String,
    val eventType: String, projection: JSONObject,
    val sourceObservationId:String?=null, val sourceEventId:String?=null) {
    private val json = CompanionWaitCapture.canonical(projection)
    val projection: JSONObject get() = JSONObject(json)
  }

  class Packet(
    val slotId: String,
    val actorId: String,
    canonRefs: CanonRefs,
    brain: BrainView,
    memories: List<MemoryView>,
    sceneEvidence: List<SceneEvidence>,
    val pins: Pins,
    val truncated: Boolean
  ) {
    val canonRefs=canonRefs.copy(traitRefs=freeze(canonRefs.traitRefs),ethicalRefs=freeze(canonRefs.ethicalRefs),
      voiceStyleRefs=freeze(canonRefs.voiceStyleRefs))
    val brain=brain.copy(beliefs=freeze(brain.beliefs.map { it.copy(evidenceIds=freeze(it.evidenceIds)) }),goals=freeze(brain.goals))
    val memories=freeze(memories.map { it.copy(involvedActorIds=Collections.unmodifiableSet(HashSet(it.involvedActorIds))) })
    val sceneEvidence=freeze(sceneEvidence)
  }
  private fun <T> freeze(values:Collection<T>):List<T> = Collections.unmodifiableList(ArrayList(values))

  /** One complete, versioned wire representation for hashing and provider input. */
  fun canonical(packet:Packet):String {
    fun strings(values:Collection<String>)=JSONArray(values)
    val pins=packet.pins
    val json=JSONObject().put("version","actor_private_context.v1").put("slot",packet.slotId).put("actor",packet.actorId)
      .put("pins",JSONObject().put("personaRevision",pins.personaRevision).put("personaSha256",pins.personaSha256)
        .put("ruleVersion",pins.ruleVersion).put("policyVersion",pins.policyVersion))
      .put("canonRefs",JSONObject().put("traits",strings(packet.canonRefs.traitRefs)).put("ethics",strings(packet.canonRefs.ethicalRefs))
        .put("voice",JSONObject().put("usage","STYLE_ONLY_NOT_EPISODIC_HISTORY").put("refs",strings(packet.canonRefs.voiceStyleRefs))))
      .put("brain",JSONObject().put("mood",packet.brain.mood).put("beliefs",JSONArray().apply {
        packet.brain.beliefs.forEach { b -> put(JSONObject().put("proposition",b.proposition).put("stance",b.stance)
          .put("speaker",b.speakerRef ?: JSONObject.NULL).put("evidence",strings(b.evidenceIds)).put("worldTruth",false)) }
      }).put("goals",JSONArray().apply { packet.brain.goals.forEach { g ->
        put(JSONObject().put("description",g.description).put("status",g.status))
      } }))
      .put("memories",JSONArray().apply { packet.memories.forEach { m ->
        put(JSONObject().put("id",m.memoryId).put("slot",m.slotId).put("owner",m.ownerActorId)
          .put("observation",m.observationId).put("turn",m.createdTurnId).put("revision",m.committedRevision)
          .put("subjectiveSummary",m.summary).put("topic",m.topic).put("salience",m.salience.name)
          .put("supersedes",m.supersedesMemoryId ?: JSONObject.NULL).put("scene",m.sceneId)
          .put("actors",strings(m.involvedActorIds.sorted())).put("worldTruth",false))
      } })
      .put("sceneEvidence",JSONArray().apply { packet.sceneEvidence.forEach { e ->
        put(JSONObject().put("slot",e.slotId).put("owner",e.ownerActorId).put("eventType",e.eventType)
          .put("observation",e.sourceObservationId ?: JSONObject.NULL).put("event",e.sourceEventId ?: JSONObject.NULL)
          .put("public",e.projection))
      } }).put("truncated",packet.truncated)
    return CompanionWaitCapture.canonical(json)
  }
  fun digest(packet:Packet):String = CompanionDigests.sha256(canonical(packet))

  data class CanonRefs(
    val traitRefs: List<String>,
    val ethicalRefs: List<String>,
    /** Voice refs labeled as style only — never presented as history. */
    val voiceStyleRefs: List<String>
  )

  data class BrainView(
    val beliefs: List<BeliefView>,
    val goals: List<GoalView>,
    val mood: String
  )
  data class BeliefView(val proposition: String, val stance: String, val speakerRef: String?,
                        val evidenceIds: List<String>)
  data class GoalView(val description: String, val status: String)

  data class Pins(
    val personaRevision: String,
    val personaSha256: String,
    val ruleVersion: String,
    val policyVersion: String
  )

  data class Input(
    val slotId: String,
    val actorId: String,
    val persona: CompanionCanonPersonaRegistry.Persona,
    val brain: BrainState,
    val memories: List<MemoryView>,
    val sceneEvidence: List<SceneEvidence>,
    val maxMemories: Int = 20,
    val maxPacketBytes:Int = 32768
  )

  fun build(input: Input): Packet {
    require(input.slotId.isNotBlank() && input.brain.slotId == input.slotId) { "context_slot_mismatch" }
    require(input.maxMemories in 0..100) { "context_budget_invalid" }
    require(input.maxPacketBytes in 1024..131072) { "context_budget_invalid" }
    require(input.brain.beliefs.size<=128 && input.brain.goals.size<=128 && input.sceneEvidence.size<=128 &&
      input.memories.size<=1000) { "context_records_bound" }
    require(input.brain.ruleVersion == BrainContracts.RULE_VERSION) { "context_rule_mismatch" }
    // Firewall 1: ownership before anything else.
    require(input.brain.actorId == input.actorId) { "context_brain_not_owned" }
    require(input.persona.actorId == input.actorId) { "context_persona_not_owned" }
    require(CompanionCanonPersonaRegistry.accepts(input.persona)) { "context_persona_unpinned" }
    val ownedMemories = input.memories.filter { it.ownerActorId == input.actorId && it.slotId == input.slotId }
    require(ownedMemories.size == input.memories.size) { "context_memory_not_owned" }
    require(ownedMemories.map { it.memoryId }.distinct().size == ownedMemories.size) { "context_memory_duplicate" }
    require(ownedMemories.all { it.summary.length<=EpisodicMemory.SUMMARY_MAX_CHARS && Charsets.UTF_8.newEncoder().canEncode(it.summary) && it.committedRevision>0 &&
      it.observationId.isNotBlank() && it.createdTurnId.isNotBlank() && it.summary.none { ch -> Character.isISOControl(ch) } }) {
      "context_memory_invalid"
    }
    // Firewall 2: no raw GM payloads — projections must be non-null (denied = null).
    // (SceneEvidence carries already-projected JSONObjects; a null projection
    // never reaches this builder by construction.)
    val publicEvidence = input.sceneEvidence.map { e ->
      require(e.slotId == input.slotId && e.ownerActorId == input.actorId) { "context_evidence_not_owned" }
      val sanitized = PublicEventProjection.project(e.eventType, e.projection)
        ?: throw IllegalArgumentException("context_projection_denied")
      require(CompanionWaitCapture.canonical(sanitized) == CompanionWaitCapture.canonical(e.projection)) {
        "context_projection_unclassified"
      }
      SceneEvidence(e.slotId, e.ownerActorId, e.eventType, sanitized,e.sourceObservationId,e.sourceEventId)
    }
    // Firewall 3: pins.
    val pins = Pins(
      personaRevision = input.persona.sourceRevision,
      personaSha256 = input.persona.sourceSha256,
      ruleVersion = BrainContracts.RULE_VERSION,
      policyVersion = CompanionExposurePolicy.VERSION)

    val beliefs = input.brain.beliefs.map { b ->
      BeliefView(
        proposition = CompanionWaitCapture.canonical(JSONArray(listOf(b.claim.subjectRef,b.claim.predicateId,b.claim.objectRef,b.claim.polarity.name))),
        // Stance + provenance explicit; TOLD stays UNKNOWN, DISPUTED stays DISPUTED.
        stance = b.stance.name,
        speakerRef = b.claim.speakerRef,
        evidenceIds = b.evidenceObservationIds)
    }
    val goals = input.brain.goals.map { g ->
      GoalView(description = g.promise.scope, status = g.status.name)
    }
    val brain = BrainView(beliefs, goals, input.brain.mood.mood.name)

    // Canon refs only — never whole Codex text.
    val canon = CanonRefs(
      traitRefs = input.persona.traitRefs,
      ethicalRefs = input.persona.ethicalRefs,
      voiceStyleRefs = input.persona.voiceRefs)

    val memories = ownedMemories.take(input.maxMemories).toMutableList()
    fun packet()=Packet(
      slotId = input.slotId,
      actorId = input.actorId,
      canonRefs = canon,
      brain = brain,
      memories = memories,
      sceneEvidence = publicEvidence,
      pins = pins,
      truncated = ownedMemories.size > memories.size)
    var result=packet()
    while(canonical(result).toByteArray(Charsets.UTF_8).size > input.maxPacketBytes && memories.isNotEmpty()) {
      memories.removeAt(memories.lastIndex);result=packet()
    }
    require(canonical(result).toByteArray(Charsets.UTF_8).size<=input.maxPacketBytes) { "context_packet_budget_exceeded" }
    return result
  }
}
