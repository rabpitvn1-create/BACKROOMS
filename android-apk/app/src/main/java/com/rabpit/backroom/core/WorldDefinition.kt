package com.rabpit.backroom.core

import org.json.JSONObject

/** Authored identity and presentation data; optional scene tags follow the existing UI contract. */
data class WorldEnvironment(
  val id: String, val name: String, val zoneId: String, val canon: String,
  val sceneZoneId: String? = null, val sceneSubzoneId: String? = null
)

/** Level-specific content and layout policy. No movement, save or action implementation here. */
interface WorldDefinition {
  val nodeId: String
  val version: Int
  val objective: String
  val environments: List<WorldEnvironment>
  val requireAlternateRoutes: Boolean
  /** Identifiers of authored, Core-verifiable passage conditions for this definition. */
  val passageConditions: Set<String> get() = emptySet()
  fun generate(seed: Long): JSONObject
  fun validateLayout(graph: JSONObject)
}

/** Explicit supported definitions; registration never creates an authorized Level route. */
object WorldDefinitions {
  private val definitions: List<WorldDefinition> = listOf(Level0WorldDefinition)
  fun forNode(nodeId: String): WorldDefinition? = definitions.firstOrNull { it.nodeId == nodeId }
  fun require(nodeId: String): WorldDefinition = requireNotNull(forNode(nodeId)) { "world_definition_unknown" }
}
