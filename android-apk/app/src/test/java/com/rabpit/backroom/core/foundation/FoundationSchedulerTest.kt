package com.rabpit.backroom.core.foundation

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FoundationSchedulerTest {
  @get:Rule val temporaryFolder = TemporaryFolder()

  @Test
  fun installsEverySectionWithDurableCompletedJobs() {
    val store = FoundationStore(temporaryFolder.newFolder("scheduler"), JvmAtomicPointerCommitter)
    val compiler = FoundationCompiler()
    val content = "{}"
    val build = compiler.compile(
      listOf(FoundationSource("levels/0.json", FoundationDigest.sha256(content), content)),
      "{}"
    )
    val manifest = compiler.manifest(build)

    FoundationBuildScheduler().install(store, manifest, build.objects)

    val jobs = JSONObject(store.buildFile(manifest.manifestId, "jobs.json").readText()).getJSONArray("jobs")
    assertEquals(FoundationSection.entries.size, jobs.length())
    assertTrue((0 until jobs.length()).all { jobs.getJSONObject(it).getString("status") == "COMPLETED" })
    assertTrue(store.buildFile(manifest.manifestId, "diagnostics.json").isFile)
  }
}
