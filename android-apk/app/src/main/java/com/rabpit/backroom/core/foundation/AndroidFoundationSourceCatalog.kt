package com.rabpit.backroom.core.foundation

import android.content.Context

class AndroidFoundationSourceCatalog(context: Context) {
  private val assets = context.applicationContext.assets
  @Volatile private var cached: List<FoundationSource>? = null

  companion object {
    private val ROOTS = listOf("knowledge", "level_catalog", "level_profiles", "levels", "campaign_story")
  }

  fun load(): List<FoundationSource> {
    cached?.let { return it }
    return synchronized(this) {
      cached ?: ROOTS
        .flatMap(::filesUnder)
        .distinct()
        .sorted()
        .map { path ->
          val bytes = assets.open(path).use { it.readBytes() }
          FoundationSource(path, FoundationDigest.sha256(bytes), bytes.toString(Charsets.UTF_8))
        }
        .also { cached = it }
    }
  }

  private fun filesUnder(path: String): List<String> {
    val children = assets.list(path).orEmpty().sorted()
    if (children.isEmpty()) return runCatching { assets.open(path).close(); listOf(path) }.getOrDefault(emptyList())
    return children.flatMap { child -> filesUnder("$path/$child") }
  }
}
