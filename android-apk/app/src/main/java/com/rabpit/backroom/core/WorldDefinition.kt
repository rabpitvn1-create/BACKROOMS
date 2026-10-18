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

/** Reusable rule contracts. PHYSICAL_CORRELATION requires evidence from both sites;
 * SPATIAL_ANCHOR additionally requires marks actually placed by the player. */
data class WorldEvidenceLaw(
  val id: String, val family: WorldLawFamily, val probes: List<WorldLawProbe>,
  val verifyEnvironmentId: String, val successText: String, val failedText: String,
  val physicalAnchors: Set<String> = emptySet()
) {
  fun freeze() = JSONObject().put("id", id).put("family", family.name)
    .put("probes", JSONArray(probes.map { it.id + ":" + it.environmentId + ":" + it.method }))
    .put("verificationSite", verifyEnvironmentId)
    .put("physicalAnchors", JSONArray(physicalAnchors.sorted()))
}

/** Optional authored investigation; it records a player-visible Canon observation
 * without creating an NPC, item, cosmic rule, or new Level. */
data class WorldFieldStudy(val environmentId: String, val focus: String, val observation: String)

/** Level-specific content and layout policy. No movement, save or action implementation here. */
interface WorldDefinition {
  val nodeId: String
  val version: Int
  val objective: String
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
  private val definitions: List<WorldDefinition> = listOf(Level0WorldDefinition)
  fun forNode(nodeId: String): WorldDefinition? = definitions.firstOrNull { it.nodeId == nodeId }
  fun require(nodeId: String): WorldDefinition = requireNotNull(forNode(nodeId)) { "world_definition_unknown" }
}
