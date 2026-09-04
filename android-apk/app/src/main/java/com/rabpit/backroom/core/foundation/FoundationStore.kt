package com.rabpit.backroom.core.foundation

import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

fun interface FoundationPointerCommitter {
  fun commit(target: File, bytes: ByteArray)
}

object AndroidAtomicPointerCommitter : FoundationPointerCommitter {
  override fun commit(target: File, bytes: ByteArray) {
    target.parentFile?.mkdirs()
    val atomic = AtomicFile(target)
    val output = atomic.startWrite()
    try {
      output.write(bytes)
      output.fd.sync()
      atomic.finishWrite(output)
    } catch (error: Throwable) {
      atomic.failWrite(output)
      throw error
    }
  }
}

object JvmAtomicPointerCommitter : FoundationPointerCommitter {
  override fun commit(target: File, bytes: ByteArray) {
    target.parentFile?.mkdirs()
    val temporary = File(target.parentFile, ".${target.name}.${System.nanoTime()}.tmp")
    FileOutputStream(temporary).use { output -> output.write(bytes); output.fd.sync() }
    check(temporary.renameTo(target) || run {
      target.writeBytes(bytes)
      temporary.delete()
      true
    }) { "Unable to activate Foundation pointer" }
  }
}

class FoundationStore(
  private val root: File,
  private val pointerCommitter: FoundationPointerCommitter = AndroidAtomicPointerCommitter
) {
  private val objects = File(root, "objects")
  private val manifests = File(root, "manifests")
  private val builds = File(root, "builds")
  private val quarantine = File(root, "quarantine")
  private val active = File(root, "active.json")

  init {
    listOf(objects, manifests, builds, quarantine).forEach { it.mkdirs() }
  }

  @Synchronized
  fun putObject(value: FoundationObject) {
    require(FoundationDigest.sha256(value.json) == value.objectHash) { "Foundation object hash mismatch" }
    writeImmutable(File(objects, "${value.objectHash}.json"), value.json)
  }

  @Synchronized
  fun putManifest(value: FoundationManifest) {
    writeImmutable(File(manifests, "${value.manifestId}.json"), value.toJson())
    File(builds, value.manifestId).mkdirs()
  }

  @Synchronized
  fun activate(value: FoundationManifest) {
    require(validate(value)) { "Cannot activate incomplete Foundation manifest" }
    val pointer = FoundationJson.canonical(JSONObject()
      .put("schemaVersion", FoundationCompiler.SCHEMA_VERSION)
      .put("manifestId", value.manifestId))
    pointerCommitter.commit(active, pointer.toByteArray(Charsets.UTF_8))
  }

  @Synchronized
  fun loadActive(): FoundationHandle? {
    if (!active.isFile) return null
    return runCatching {
      val pointer = JSONObject(active.readText())
      loadManifest(pointer.getString("manifestId"))
    }.getOrElse {
      quarantine(active, "active")
      null
    }
  }

  @Synchronized
  fun loadManifest(manifestId: String): FoundationHandle? {
    if (!manifestId.matches(Regex("[a-f0-9]{64}"))) return null
    val file = File(manifests, "$manifestId.json")
    if (!file.isFile) return null
    return runCatching {
      val manifest = FoundationManifest.fromJson(file.readText())
      require(manifest.manifestId == manifestId && validate(manifest))
      FoundationHandle(manifest)
    }.getOrElse {
      quarantine(file, "manifest")
      null
    }
  }

  @Synchronized
  fun readObject(hash: String): String? {
    if (!hash.matches(Regex("[a-f0-9]{64}"))) return null
    val file = File(objects, "$hash.json")
    if (!file.isFile) return null
    return runCatching {
      file.readText().also { require(FoundationDigest.sha256(it) == hash) }
    }.getOrElse {
      quarantine(file, "object")
      null
    }
  }

  @Synchronized
  fun writeBuildFile(manifestId: String, name: String, content: String) {
    require(manifestId.matches(Regex("[a-f0-9]{64}"))) { "Invalid Foundation build id" }
    require(name in setOf("jobs.json", "diagnostics.json")) { "Unsupported Foundation build file" }
    pointerCommitter.commit(File(builds, "$manifestId/$name"), content.toByteArray(Charsets.UTF_8))
  }

  fun buildFile(manifestId: String, name: String): File = File(builds, "$manifestId/$name")

  private fun validate(manifest: FoundationManifest): Boolean =
    manifest.schemaVersion == FoundationCompiler.SCHEMA_VERSION &&
      manifest.compilerVersion == FoundationCompiler.COMPILER_VERSION &&
      manifest.objects.keys == FoundationSection.entries.toSet() &&
      manifest.objects.values.all { hash ->
        val file = File(objects, "$hash.json")
        file.isFile && runCatching { FoundationDigest.sha256(file.readText()) == hash }.getOrDefault(false)
      }

  private fun writeImmutable(target: File, content: String) {
    target.parentFile?.mkdirs()
    if (target.isFile) {
      if (target.readText() == content) return
      quarantine(target, "collision")
    }
    val temporary = File(target.parentFile, ".${target.name}.${System.nanoTime()}.tmp")
    FileOutputStream(temporary).use { output ->
      output.write(content.toByteArray(Charsets.UTF_8))
      output.fd.sync()
    }
    check(temporary.renameTo(target)) { "Unable to install immutable Foundation object" }
  }

  private fun quarantine(file: File, reason: String) {
    if (!file.exists()) return
    quarantine.mkdirs()
    file.renameTo(File(quarantine, "${reason}-${System.currentTimeMillis()}-${file.name}"))
  }
}
