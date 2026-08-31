package com.rabpit.backroom.core

import android.content.Context
import android.content.res.AssetManager

object AndroidLevelRegistry {
  private const val DEFINITION_ROOT = "levels"
  private const val PROFILE_ROOT = "level_profiles"
  private val PLACEHOLDER_STATUSES = setOf("placeholder", "content-placeholder", "intentionally-unimplemented")

  fun load(context: Context): LevelRegistry = load(context.applicationContext.assets)

  fun load(assets: AssetManager): LevelRegistry {
    val catalog = AndroidLevelCatalog.load(assets)

    val definitionDocuments = mutableListOf<LevelDefinitionDocument>()
    collectDefinitionDocuments(assets, DEFINITION_ROOT, definitionDocuments)
    val explicitRegistry = if (definitionDocuments.isEmpty()) {
      LevelRegistry.empty()
    } else {
      LevelRegistryLoader.load(definitionDocuments.sortedBy { it.path })
    }
    val explicitDefinitions = explicitRegistry.ids().map(explicitRegistry::require)

    val profileDocuments = mutableListOf<ProceduralLevelProfileDocument>()
    collectProfileDocuments(assets, PROFILE_ROOT, profileDocuments)
    val compiledProfiles = if (profileDocuments.isEmpty()) {
      emptyList()
    } else {
      ProceduralLevelProfileLoader.load(
        profileDocuments.sortedBy { it.path },
        catalog,
        explicitDefinitions.associateBy { it.id }
      )
    }

    val explicitIds = explicitDefinitions.map { it.id }.toSet()
    val collisions = compiledProfiles.map { it.id }.filter(explicitIds::contains).sorted()
    require(collisions.isEmpty()) { "procedural_profile_conflicts_with_explicit_level:${collisions.joinToString(",")}" }

    val registry = LevelRegistry.from(explicitDefinitions + compiledProfiles)

    registry.ids().forEach { id ->
      require(catalog.contains(id)) { "registered_level_not_in_catalog:$id" }
    }
    catalog.ids().forEach { id ->
      val entry = catalog.require(id)
      val placeholder = entry.metadata["contentStatus"]?.trim()?.lowercase() in PLACEHOLDER_STATUSES
      require(placeholder || registry.contains(id)) { "catalog_level_not_implemented:$id" }
      require(!placeholder || !registry.contains(id)) { "catalog_placeholder_conflicts_with_implementation:$id" }
    }

    return registry
  }

  private fun collectDefinitionDocuments(
    assets: AssetManager,
    path: String,
    documents: MutableList<LevelDefinitionDocument>
  ) {
    val children = assets.list(path).orEmpty()
    if (children.isEmpty()) {
      if (!path.endsWith(".json", ignoreCase = true)) return
      if (path.substringAfterLast('/').startsWith("_")) return
      val content = assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
      documents += LevelDefinitionDocument(path, content)
      return
    }

    children.sorted().forEach { child ->
      collectDefinitionDocuments(assets, "$path/$child", documents)
    }
  }

  private fun collectProfileDocuments(
    assets: AssetManager,
    path: String,
    documents: MutableList<ProceduralLevelProfileDocument>
  ) {
    val children = assets.list(path).orEmpty()
    if (children.isEmpty()) {
      if (!path.endsWith(".json", ignoreCase = true)) return
      if (path.substringAfterLast('/').startsWith("_")) return
      val content = assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
      documents += ProceduralLevelProfileDocument(path, content)
      return
    }

    children.sorted().forEach { child ->
      collectProfileDocuments(assets, "$path/$child", documents)
    }
  }
}
