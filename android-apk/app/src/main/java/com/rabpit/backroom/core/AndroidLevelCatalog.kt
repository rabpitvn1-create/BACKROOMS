package com.rabpit.backroom.core

import android.content.Context
import android.content.res.AssetManager

object AndroidLevelCatalog {
  private const val ROOT = "level_catalog"

  fun load(context: Context): LevelCatalog = load(context.applicationContext.assets)

  fun load(assets: AssetManager): LevelCatalog {
    val documents = mutableListOf<LevelCatalogDocument>()
    collectJsonDocuments(assets, ROOT, documents)
    return if (documents.isEmpty()) LevelCatalog.empty() else LevelCatalogLoader.load(documents.sortedBy { it.path })
  }

  private fun collectJsonDocuments(
    assets: AssetManager,
    path: String,
    documents: MutableList<LevelCatalogDocument>
  ) {
    val children = assets.list(path).orEmpty()
    if (children.isEmpty()) {
      if (!path.endsWith(".json", ignoreCase = true)) return
      if (path.substringAfterLast('/').startsWith("_")) return
      val content = assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
      documents += LevelCatalogDocument(path, content)
      return
    }

    children.sorted().forEach { child -> collectJsonDocuments(assets, "$path/$child", documents) }
  }
}
