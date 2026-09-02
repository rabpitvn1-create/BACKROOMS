package com.rabpit.backroom.core

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.exp

enum class DirectorEvidencePreference { SEARCH, ENVIRONMENT, ANOMALY, SURVIVOR }

data class BackroomsDirectorContext(
  val actionKind: ActionKind,
  val levelId: String,
  val zoneId: String,
  val zoneTags: Set<String>,
  val visitCount: Int,
  val revision: Int,
  val recentMutationKind: String?,
  val discoveredEvidenceCount: Int,
  val discoveredSourceCounts: Map<EvidenceSource, Int>,
  val candidateSourceCounts: Map<EvidenceSource, Int>
)

data class DirectorEvidenceSelection(
  val evidence: List<EvidenceState>,
  val trace: DirectorDecisionTrace? = null
)

fun interface BackroomsDirectorPolicy {
  fun choose(context: BackroomsDirectorContext): DirectorEvidencePreference?
}

/**
 * Selects only from evidence that the deterministic Level runtime has already proven legal.
 * A policy may rank/select an evidence source, but it cannot invent evidence, change conditions,
 * rewrite the locked escape blueprint, or directly mutate GameState.
 */
class BackroomsDirector(
  private val policy: BackroomsDirectorPolicy? = null,
  private val closeablePolicy: AutoCloseable? = policy as? AutoCloseable,
  private val telemetry: BackroomsDirectorTelemetryStore? = null
) : AutoCloseable {

  fun selectEvidence(
    level: LevelInstanceState,
    definition: LevelDefinition,
    kind: ActionKind,
    eligible: List<EvidenceState>
  ): List<EvidenceState> = selectEvidenceWithTrace(level, definition, kind, eligible).evidence

  fun selectEvidenceWithTrace(
    level: LevelInstanceState,
    definition: LevelDefinition,
    kind: ActionKind,
    eligible: List<EvidenceState>
  ): DirectorEvidenceSelection {
    if (eligible.isEmpty()) return DirectorEvidenceSelection(emptyList())
    val context = context(level, kind, eligible)
    val availableSources = context.candidateSourceCounts.filterValues { it > 0 }.keys
    val rawModelSource = policy?.choose(context)?.toEvidenceSource()
    val modelAccepted = rawModelSource != null && rawModelSource in availableSources
    val selectedSource = if (modelAccepted) rawModelSource!! else fallbackSource(context, availableSources)

    val sourceCandidates = eligible.filter { selectedSource in it.sources }.ifEmpty { eligible }
    val ordered = sourceCandidates.sortedWith(
      compareBy<EvidenceState> { evidencePriority(level, definition, it) }.thenBy { it.id }
    )
    // SEARCH stays investigative and incremental. Environmental events may surface a small cluster
    // of evidence from the same source when one observation legitimately supports multiple facts.
    val limit = if (kind == ActionKind.SEARCH) 1 else 2
    val selected = ordered.take(limit)
    val trace = DirectorDecisionTrace(
      sessionId = BackroomsDirectorTelemetryPrivacy.opaqueSessionId(level),
      actionKind = kind,
      features = BackroomsDirectorFeatures.describe(context),
      candidateSourceCounts = context.candidateSourceCounts,
      modelPreferredSource = rawModelSource,
      modelAccepted = modelAccepted,
      selectedSource = selectedSource,
      discoveredEvidenceBefore = level.evidence.values.count(EvidenceState::discovered),
      discoveredFactBefore = level.discoveredFacts.size,
      worldRevisionBefore = level.revision
    )
    return DirectorEvidenceSelection(selected, trace)
  }

  fun recordOutcome(trace: DirectorDecisionTrace?, after: LevelInstanceState, surfacedCount: Int) {
    if (trace == null || telemetry == null) return
    val evidenceAfter = after.evidence.values.count(EvidenceState::discovered)
    val factsAfter = after.discoveredFacts.size
    telemetry.record(
      DirectorTelemetryRecord(
        sessionId = trace.sessionId,
        actionKind = trace.actionKind,
        features = trace.features,
        candidateSourceCounts = trace.candidateSourceCounts,
        modelPreferredSource = trace.modelPreferredSource,
        modelAccepted = trace.modelAccepted,
        fallbackUsed = !trace.modelAccepted,
        selectedSource = trace.selectedSource,
        surfacedCount = surfacedCount.coerceAtLeast(0),
        discoveredEvidenceBefore = trace.discoveredEvidenceBefore,
        discoveredEvidenceAfter = evidenceAfter,
        discoveredFactBefore = trace.discoveredFactBefore,
        discoveredFactAfter = factsAfter,
        unlockedFact = factsAfter > trace.discoveredFactBefore,
        worldRevisionBefore = trace.worldRevisionBefore,
        worldRevisionAfter = after.revision
      )
    )
  }

  fun exportTelemetry(): String = telemetry?.exportJsonl().orEmpty()
  fun clearTelemetry(): Boolean = telemetry?.clear() ?: true

  private fun context(
    level: LevelInstanceState,
    kind: ActionKind,
    eligible: List<EvidenceState>
  ): BackroomsDirectorContext {
    val discovered = level.evidence.values.filter(EvidenceState::discovered)
    return BackroomsDirectorContext(
      actionKind = kind,
      levelId = level.levelId,
      zoneId = level.currentZoneId,
      zoneTags = level.zones[level.currentZoneId]?.tags.orEmpty(),
      visitCount = level.environment["visits:${level.currentZoneId}"]?.toIntOrNull() ?: 0,
      revision = level.revision,
      recentMutationKind = level.mutations.lastOrNull()?.kind,
      discoveredEvidenceCount = discovered.size,
      discoveredSourceCounts = EvidenceSource.values().associateWith { source ->
        discovered.count { source in it.sources }
      },
      candidateSourceCounts = EvidenceSource.values().associateWith { source ->
        eligible.count { source in it.sources }
      }
    )
  }

  private fun fallbackSource(
    context: BackroomsDirectorContext,
    available: Set<EvidenceSource>
  ): EvidenceSource {
    val order = when {
      context.actionKind == ActionKind.SEARCH -> listOf(EvidenceSource.SEARCH)
      context.recentMutationKind == "execute" -> listOf(
        EvidenceSource.ANOMALY, EvidenceSource.ENVIRONMENT, EvidenceSource.SURVIVOR
      )
      context.visitCount >= 2 -> listOf(
        EvidenceSource.ENVIRONMENT, EvidenceSource.ANOMALY, EvidenceSource.SURVIVOR
      )
      context.visitCount <= 1 -> listOf(
        EvidenceSource.SURVIVOR, EvidenceSource.ANOMALY, EvidenceSource.ENVIRONMENT
      )
      else -> listOf(EvidenceSource.ANOMALY, EvidenceSource.ENVIRONMENT, EvidenceSource.SURVIVOR)
    }
    return order.firstOrNull(available::contains) ?: available.sortedBy { it.name }.first()
  }

  private fun evidencePriority(
    level: LevelInstanceState,
    definition: LevelDefinition,
    evidence: EvidenceState
  ): Int {
    val required = evidence.supports.intersect(level.escapeBlueprint.requiredFacts)
    if (required.isEmpty()) return 10_000
    val minEvidence = definition.generationConstraints.minEvidencePerRequiredFact.coerceAtLeast(1)
    val minSources = definition.generationConstraints.minEvidenceSourceTypesPerRequiredFact.coerceAtLeast(1)
    return required.minOf { fact ->
      if (fact in level.discoveredFacts) return@minOf 8_000
      val supporting = level.evidence.values.filter { it.discovered && fact in it.supports }
      val evidenceGap = (minEvidence - supporting.size).coerceAtLeast(0)
      val sourceGap = (minSources - supporting.flatMap { it.sources }.toSet().size).coerceAtLeast(0)
      evidenceGap * 100 + sourceGap * 10
    }
  }

  override fun close() {
    runCatching { closeablePolicy?.close() }
  }

  companion object {
    @JvmField val DETERMINISTIC = BackroomsDirector()

    /**
     * Historical name retained for factory compatibility. Since PR #174 the packaged LiteRT asset
     * belongs exclusively to WorldDirector pressure proposals, so evidence selection remains
     * deterministic Core behavior while this factory only enables local evidence telemetry.
     */
    fun liteRT(context: Context): BackroomsDirector {
      val appContext = context.applicationContext
      return BackroomsDirector(
        telemetry = SharedPreferencesBackroomsDirectorTelemetryStore(appContext)
      )
    }
  }
}

object BackroomsDirectorFeatures {
  fun describe(context: BackroomsDirectorContext): String = buildList {
    add("action_${context.actionKind.name.lowercase()}")
    add(when {
      context.visitCount <= 1 -> "visit_first"
      context.visitCount == 2 -> "visit_repeat"
      else -> "visit_deep"
    })
    add(if (context.revision <= 2) "revision_early" else "revision_changed")
    context.recentMutationKind?.takeIf(String::isNotBlank)?.let { add("recent_${sanitize(it)}") }
    context.zoneTags.sorted().forEach { add("zone_${sanitize(it)}") }
    EvidenceSource.values().forEach { source ->
      val token = source.name.lowercase()
      val candidateCount = context.candidateSourceCounts[source] ?: 0
      if (candidateCount > 0) add("candidate_$token")
      val discoveredCount = context.discoveredSourceCounts[source] ?: 0
      add(if (discoveredCount == 0) "unseen_$token" else "seen_$token")
    }
    add(when {
      context.discoveredEvidenceCount == 0 -> "evidence_none"
      context.discoveredEvidenceCount <= 3 -> "evidence_some"
      else -> "evidence_many"
    })
  }.joinToString(" ")

  private fun sanitize(value: String): String = value.lowercase().replace(Regex("[^a-z0-9_]+"), "_").trim('_')
}

/**
 * Legacy evidence-source policy implementation retained for isolated experiments/tests only.
 * It is intentionally not wired to the application factory because the packaged director model
 * now uses WorldPressureProposal labels instead of DirectorEvidencePreference labels.
 */
class LiteRTBackroomsDirectorPolicy(
  context: Context,
  private val modelAsset: String = "models/backrooms_director.tflite",
  private val labelsAsset: String = "models/backrooms_director_labels.txt",
  private val featureCount: Int = 4096,
  private val highConfidence: Float = .40f,
  private val highMargin: Float = .15f
) : BackroomsDirectorPolicy, AutoCloseable {
  private val appContext = context.applicationContext
  private val lock = Any()
  @Volatile private var interpreter: Interpreter? = null
  @Volatile private var labels: List<DirectorEvidencePreference>? = null
  @Volatile private var initializationError: String? = null

  override fun choose(context: BackroomsDirectorContext): DirectorEvidencePreference? {
    val runtime = ensureLoaded() ?: return null
    val output = Array(1) { FloatArray(runtime.second.size) }
    synchronized(lock) { runtime.first.run(features(BackroomsDirectorFeatures.describe(context)), output) }
    val probabilities = softmax(output[0])
    val index = probabilities.indices.maxByOrNull { probabilities[it] } ?: return null
    val score = probabilities.getOrElse(index) { 0f }
    val runnerUp = probabilities.filterIndexed { candidateIndex, _ -> candidateIndex != index }.maxOrNull() ?: 0f
    if (score < highConfidence || score - runnerUp < highMargin) return null
    return runtime.second.getOrNull(index)
  }

  fun loadError(): String? = initializationError

  private fun ensureLoaded(): Pair<Interpreter, List<DirectorEvidencePreference>>? {
    val ready = interpreter
    val readyLabels = labels
    if (ready != null && readyLabels != null) return ready to readyLabels
    synchronized(lock) {
      interpreter?.let { loaded -> labels?.let { return loaded to it } }
      return try {
        val loadedLabels = appContext.assets.open(labelsAsset).bufferedReader().useLines { lines ->
          lines.map(String::trim).filter(String::isNotEmpty).map { DirectorEvidencePreference.valueOf(it) }.toList()
        }
        require(loadedLabels.isNotEmpty()) { "director_labels_empty" }
        val descriptor = appContext.assets.openFd(modelAsset)
        val buffer = descriptor.createInputStream().channel.use { channel ->
          channel.map(FileChannel.MapMode.READ_ONLY, descriptor.startOffset, descriptor.declaredLength)
        }
        descriptor.close()
        val loaded = Interpreter(buffer, Interpreter.Options().apply { setNumThreads(2) })
        require(loaded.getInputTensor(0).shape().contentEquals(intArrayOf(1, featureCount))) {
          "unexpected_director_model_input_shape"
        }
        require(loaded.getOutputTensor(0).shape().last() == loadedLabels.size) { "director_label_count_mismatch" }
        interpreter = loaded
        labels = loadedLabels
        loaded to loadedLabels
      } catch (error: Exception) {
        initializationError = error.message ?: "director_litert_initialization_failed"
        null
      }
    }
  }

  private fun features(text: String): ByteBuffer {
    val vector = FloatArray(featureCount)
    val tokens = tokenize(text)
    tokens.forEach { addFeature(vector, "w:$it") }
    tokens.zipWithNext().forEach { (left, right) -> addFeature(vector, "b:$left|$right") }
    val compact = tokens.joinToString(" ")
    for (size in 3..5) {
      if (compact.length < size) continue
      for (index in 0..compact.length - size) addFeature(vector, "c$size:${compact.substring(index, index + size)}")
    }
    val norm = kotlin.math.sqrt(vector.sumOf { (it * it).toDouble() }).toFloat().coerceAtLeast(1f)
    val buffer = ByteBuffer.allocateDirect(featureCount * 4).order(ByteOrder.nativeOrder())
    vector.forEach { buffer.putFloat(it / norm) }
    buffer.rewind()
    return buffer
  }

  private fun addFeature(vector: FloatArray, feature: String) {
    val index = (feature.hashCode() and Int.MAX_VALUE) % featureCount
    vector[index] += 1f
  }

  private fun tokenize(text: String): List<String> = text.lowercase()
    .replace(Regex("[^\\p{L}\\p{N}_]+"), " ").trim().split(Regex("\\s+")).filter(String::isNotBlank)

  private fun softmax(logits: FloatArray): FloatArray {
    val max = logits.maxOrNull() ?: 0f
    val values = FloatArray(logits.size) { exp((logits[it] - max).toDouble()).toFloat() }
    val total = values.sum().coerceAtLeast(1e-7f)
    return FloatArray(values.size) { values[it] / total }
  }

  override fun close() = synchronized(lock) {
    interpreter?.close()
    interpreter = null
    labels = null
  }
}

private fun DirectorEvidencePreference.toEvidenceSource(): EvidenceSource = when (this) {
  DirectorEvidencePreference.SEARCH -> EvidenceSource.SEARCH
  DirectorEvidencePreference.ENVIRONMENT -> EvidenceSource.ENVIRONMENT
  DirectorEvidencePreference.ANOMALY -> EvidenceSource.ANOMALY
  DirectorEvidencePreference.SURVIVOR -> EvidenceSource.SURVIVOR
}
