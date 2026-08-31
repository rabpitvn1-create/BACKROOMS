package com.rabpit.backroom.core

import android.content.Context
import android.content.res.AssetManager

object AndroidLevelRegistry {
  private const val ROOT = "levels"

  fun load(context: Context): LevelRegistry = load(context.applicationContext.assets)

  fun load(assets: AssetManager): LevelRegistry {
    val documents = mutableListOf<LevelDefinitionDocument>()
    collectJsonDocuments(assets, ROOT, documents)
    return if (documents.isEmpty()) LevelRegistry.empty() else LevelRegistryLoader.load(documents.sortedBy { it.path })
  }

  private fun collectJsonDocuments(
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
      collectJsonDocuments(assets, "$path/$child", documents)
    }
  }
}
