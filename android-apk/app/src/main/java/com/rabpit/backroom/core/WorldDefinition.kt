package com.rabpit.backroom.core

import org.json.JSONObject
import org.json.JSONArray

/** Authored identity and presentation data; optional scene tags follow the existing UI contract. */
data class WorldEnvironment(
  val id: String, val name: String, val zoneId: String, val canon: String,
  val sceneZoneId: String? = null, val sceneSubzoneId: String? = null
)

/** Immutable clue identity, authored gameplay probe and its visible physical observation. */
data class WorldLawProbe(val id: String, val environmentId: String, val method: String, val clue: String)
enum class WorldLawFamily { PHYSICAL_CORRELATION, SPATIAL_ANCHOR }

/** Player-facing alternative explanations, never an instruction to the GM. */
data class WorldInferenceOption(val id: String, val text: String)
data class WorldInferenceStep(
  val id: String, val question: String, val options: List<WorldInferenceOption>,
  val answerId: String, val conclusion: String
)

/** Reusable rule contracts. PHYSICAL_CORRELATION requires evidence from both sites;
 * SPATIAL_ANCHOR additionally requires marks actually placed by the player. */
data class WorldEvidenceLaw(
  val id: String, val family: WorldLawFamily, val probes: List<WorldLawProbe>,
  val verifyEnvironmentId: String, val successText: String, val failedText: String,
  val physicalAnchors: Set<String> = emptySet(),
  val comparisonPairs: Set<Pair<String, String>> = emptySet(),
  val prerequisites: Set<String> = emptySet(),
  val inferenceSteps: List<WorldInferenceStep> = emptyList()
) {
  fun freeze() = JSONObject().put("id", id).put("family", family.name)
    .put("probes", JSONArray(probes.map { it.id + ":" + it.environmentId + ":" + it.method }))
    .put("verificationSite", verifyEnvironmentId)
    .put("physicalAnchors", JSONArray(physicalAnchors.sorted()))
    .put("comparisonPairs", JSONArray(comparisonPairs.map { "${it.first}:${it.second}" }.sorted()))
    .put("prerequisites", JSONArray(prerequisites.sorted()))
    // The frozen layout records identity and options, not correct answers.
    .put("inferenceSteps", JSONArray(inferenceSteps.map { step ->
      JSONObject().put("id", step.id).put("question", step.question)
        .put("options", JSONArray(step.options.map { it.id + ":" + it.text }))
    }))
}

/** Optional authored investigation; it records a player-visible Canon observation
 * without creating an NPC, item, cosmic rule, or new Level. */
data class WorldFieldStudy(val environmentId: String, val focus: String, val observation: String)

/** Level-specific content and layout policy. No movement, save or action implementation here. */
interface WorldDefinition {
  val nodeId: String
  val version: Int
  val objective: String
  /** Core-derived near-term goal, not a walkthrough or an exit authorization. */
  fun currentObjective(progress: JSONObject): String = objective
  val environments: List<WorldEnvironment>
  val requireAlternateRoutes: Boolean
  /** Identifiers of authored, Core-verifiable passage conditions for this definition. */
  val passageConditions: Set<String> get() = emptySet()
  val evidenceLaws: List<WorldEvidenceLaw> get() = emptyList()
  val fieldStudies: List<WorldFieldStudy> get() = emptyList()
  fun generate(seed: Long): JSONObject
  fun validateLayout(graph: JSONObject)
}

/** Explicit supported definitions; registration never creates an authorized Level route. */
object WorldDefinitions {
  private val definitions: List<WorldDefinition> = listOf(Level0WorldDefinition) +
    listOf("0.1", "0.2", "0.5", "0.7", "manila_room", "the_torment", "red_rooms")
      .map(::Level0SatelliteWorldDefinition)
  fun forNode(nodeId: String): WorldDefinition? = definitions.firstOrNull { it.nodeId == nodeId }
  fun require(nodeId: String): WorldDefinition = requireNotNull(forNode(nodeId)) { "world_definition_unknown" }
}
