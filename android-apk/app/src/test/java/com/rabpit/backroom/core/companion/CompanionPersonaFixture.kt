package com.rabpit.backroom.core.companion

/** Reads the same SHA-pinned source assets as the registry, never a synthetic persona. */
internal object CompanionPersonaFixture {
  fun load(actorId: String) = CompanionCanonPersonaRegistry.load(actorId) { path ->
    val file=listOf("src/main/assets","app/src/main/assets","android-apk/app/src/main/assets")
      .map { java.io.File(it,path) }.firstOrNull { it.isFile }
      ?: error("persona fixture asset missing")
    file.readBytes()
  }
}
