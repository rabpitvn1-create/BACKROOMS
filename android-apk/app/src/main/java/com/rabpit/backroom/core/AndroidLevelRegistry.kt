package com.rabpit.backroom.core

import android.content.Context
import android.content.res.AssetManager

object AndroidLevelRegistry {
  private const val DEFINITION_ROOT = "levels"
  private const val PROFILE_ROOT = "level_profiles"

  fun load(context: Context): LevelRegistry = load(context.applicationContext.assets)

  fun load(assets: AssetManager): LevelRegistry {
    val definitionDocuments = mutableListOf<LevelDefinitionDocument>()
    collectDefinitionDocuments(assets, DEFINITION_ROOT, definitionDocuments)
    val explicitRegistry = if (definitionDocuments.isEmpty()) {
      LevelRegistry.empty()
    } else {
      LevelRegistryLoader.load(definitionDocuments.sortedBy { it.path })
    }

    val profileDocuments = mutableListOf<ProceduralLevelProfileDocument>()
    collectProfileDocuments(assets, PROFILE_ROOT, profileDocuments)
    if (profileDocuments.isEmpty()) return explicitRegistry

    val catalog = AndroidLevelCatalog.load(assets)
    val compiledProfiles = ProceduralLevelProfileLoader.load(profileDocuments.sortedBy { it.path }, catalog)
    val explicitDefinitions = explicitRegistry.ids().map(explicitRegistry::require)
    val explicitIds = explicitDefinitions.map { it.id }.toSet()
    val collisions = compiledProfiles.map { it.id }.filter(explicitIds::contains).sorted()
    require(collisions.isEmpty()) { "procedural_profile_conflicts_with_explicit_level:${collisions.joinToString(",")}" }

    return LevelRegistry.from(explicitDefinitions + compiledProfiles)
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
