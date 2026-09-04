package com.rabpit.backroom.core.foundation

import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal enum class FoundationJobStatus { QUEUED, RUNNING, COMPLETED, FAILED }

internal data class FoundationJob(
  val section: FoundationSection,
  val objectHash: String,
  val status: FoundationJobStatus = FoundationJobStatus.QUEUED,
  val attempts: Int = 0,
  val leaseUntilEpochMs: Long = 0L,
  val error: String? = null
)

/** Durable at-least-once ledger; content addressing makes recovered work idempotent. */
internal class FoundationJobLedger(
  private val store: FoundationStore,
  private val manifestId: String,
  jobs: List<FoundationJob>
) {
  private val values = linkedMapOf<FoundationSection, FoundationJob>()

  init {
    val persisted = readPersisted()
    jobs.forEach { requested ->
      val old = persisted[requested.section]
      val reusable = old?.objectHash == requested.objectHash &&
        (old.status != FoundationJobStatus.COMPLETED || store.readObject(old.objectHash) != null)
      values[requested.section] = if (reusable) old!! else requested
    }
    recoverExpired(System.currentTimeMillis())
    persist()
  }

  @Synchronized
  fun lease(section: FoundationSection, now: Long, durationMs: Long): FoundationJob {
    val current = values.getValue(section)
    if (current.status == FoundationJobStatus.COMPLETED) return current
    val leased = current.copy(
      status = FoundationJobStatus.RUNNING,
      attempts = current.attempts + 1,
      leaseUntilEpochMs = now + durationMs,
      error = null
    )
    values[section] = leased
    persist()
    return leased
  }

  @Synchronized
  fun complete(section: FoundationSection) {
    values[section] = values.getValue(section).copy(
      status = FoundationJobStatus.COMPLETED,
      leaseUntilEpochMs = 0L,
      error = null
    )
    persist()
  }

  @Synchronized
  fun fail(section: FoundationSection, error: Throwable) {
    values[section] = values.getValue(section).copy(
      status = FoundationJobStatus.FAILED,
      leaseUntilEpochMs = 0L,
      error = (error.message ?: error::class.java.simpleName).take(500)
    )
    persist()
  }

  @Synchronized
  fun recoverExpired(now: Long) {
    values.entries.forEach { (section, job) ->
      if (job.status == FoundationJobStatus.RUNNING && job.leaseUntilEpochMs <= now) {
        values[section] = job.copy(status = FoundationJobStatus.QUEUED, leaseUntilEpochMs = 0L)
      }
    }
  }

  @Synchronized
  fun snapshot(): List<FoundationJob> = values.values.toList()

  private fun readPersisted(): Map<FoundationSection, FoundationJob> = runCatching {
    val file = store.buildFile(manifestId, "jobs.json")
    if (!file.isFile) return@runCatching emptyMap()
    val array = JSONObject(file.readText()).getJSONArray("jobs")
    buildMap {
      for (index in 0 until array.length()) {
        val item = array.getJSONObject(index)
        val section = FoundationSection.fromWireName(item.getString("section"))
        put(section, FoundationJob(
          section = section,
          objectHash = item.getString("objectHash"),
          status = FoundationJobStatus.valueOf(item.getString("status")),
          attempts = item.optInt("attempts", 0),
          leaseUntilEpochMs = item.optLong("leaseUntilEpochMs", 0L),
          error = item.optString("error").takeIf(String::isNotBlank)
        ))
      }
    }
  }.getOrDefault(emptyMap())

  private fun persist() {
    val json = JSONObject()
      .put("schemaVersion", 1)
      .put("manifestId", manifestId)
      .put("jobs", JSONArray().apply {
        values.values.sortedBy { it.section.wireName }.forEach { job ->
          put(JSONObject()
            .put("section", job.section.wireName)
            .put("objectHash", job.objectHash)
            .put("status", job.status.name)
            .put("attempts", job.attempts)
            .put("leaseUntilEpochMs", job.leaseUntilEpochMs)
            .put("error", job.error ?: JSONObject.NULL))
        }
      })
    store.writeBuildFile(manifestId, "jobs.json", FoundationJson.canonical(json))
  }
}

/** Two local workers consume one durable queue; no network is needed for a build. */
internal class FoundationBuildScheduler(
  private val executor: ExecutorService = Executors.newFixedThreadPool(2) { runnable ->
    Thread(runnable, "foundation-section-worker").apply { isDaemon = true }
  }
) {
  fun install(store: FoundationStore, manifest: FoundationManifest, objects: List<FoundationObject>) {
    val ledger = FoundationJobLedger(
      store,
      manifest.manifestId,
      objects.map { FoundationJob(it.section, it.objectHash) }
    )
    val futures = objects.map { value ->
      executor.submit(Callable {
        val lease = ledger.lease(value.section, System.currentTimeMillis(), 30_000L)
        if (lease.status == FoundationJobStatus.COMPLETED) return@Callable
        try {
          store.putObject(value)
          ledger.complete(value.section)
        } catch (error: Throwable) {
          ledger.fail(value.section, error)
          throw error
        }
      })
    }
    futures.forEach { it.get() }
    check(ledger.snapshot().all { it.status == FoundationJobStatus.COMPLETED }) { "Foundation build is incomplete" }
    store.writeBuildFile(manifest.manifestId, "diagnostics.json", FoundationJson.canonical(JSONObject()
      .put("schemaVersion", 1)
      .put("manifestId", manifest.manifestId)
      .put("sourcePackHash", manifest.sourcePackHash)
      .put("builtAtEpochMs", manifest.createdAtEpochMs)
      .put("workerCount", 2)
      .put("remoteEnrichmentEnabled", false)
      .put("completedSections", JSONArray(FoundationSection.entries.map { it.wireName }))))
  }
}
