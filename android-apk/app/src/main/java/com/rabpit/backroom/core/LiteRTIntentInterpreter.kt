package com.rabpit.backroom.core

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.exp

class LiteRTIntentInterpreter(
  context: Context,
  private val modelAsset: String = "models/backroom_intent.tflite",
  private val labelsAsset: String = "models/backroom_intent_labels.txt",
  private val featureCount: Int = 4096,
  private val highConfidence: Float = .45f,
  private val highMargin: Float = .30f
) : IntentInterpreter, AutoCloseable {
  private val appContext = context.applicationContext
  private val lock = Any()
  @Volatile private var interpreter: Interpreter? = null
  @Volatile private var labels: List<GameIntent>? = null
  @Volatile private var initializationError: String? = null

  fun interpretSync(input: String, context: GameContext): IntentResult {
    val runtime = ensureLoaded()
    if (runtime == null) return IntentResult(listOf(IntentCandidate(input, GameIntent.UNKNOWN, IntentConfidence.LOW, 0f, CommandSource.LITERT, initializationError ?: "model_unavailable")), true)
    val output = Array(1) { FloatArray(runtime.second.size) }
    synchronized(lock) { runtime.first.run(features(input), output) }
    val probabilities = softmax(output[0])
    val index = probabilities.indices.maxByOrNull { probabilities[it] } ?: 0
    val score = probabilities.getOrElse(index) { 0f }
    val runnerUp = probabilities.filterIndexed { candidateIndex, _ -> candidateIndex != index }.maxOrNull() ?: 0f
    val margin = score - runnerUp
    val confidence = when {
      score >= highConfidence && margin >= highMargin -> IntentConfidence.HIGH
      score >= .30f && margin >= .10f -> IntentConfidence.MEDIUM
      else -> IntentConfidence.LOW
    }
    val intent = runtime.second.getOrElse(index) { GameIntent.UNKNOWN }
    return IntentResult(listOf(IntentCandidate(input, intent, confidence, score, CommandSource.LITERT)), confidence != IntentConfidence.HIGH)
  }

  override suspend fun interpret(input: String, context: GameContext): IntentResult = interpretSync(input, context)

  private fun ensureLoaded(): Pair<Interpreter, List<GameIntent>>? {
    val ready = interpreter
    val readyLabels = labels
    if (ready != null && readyLabels != null) return ready to readyLabels
    synchronized(lock) {
      interpreter?.let { loaded -> labels?.let { return loaded to it } }
      return try {
        val loadedLabels = appContext.assets.open(labelsAsset).bufferedReader().useLines { lines ->
          lines.map(String::trim).filter(String::isNotEmpty).map { runCatching { GameIntent.valueOf(it) }.getOrDefault(GameIntent.UNKNOWN) }.toList()
        }
        val descriptor = appContext.assets.openFd(modelAsset)
        val buffer = descriptor.createInputStream().channel.use { channel -> channel.map(FileChannel.MapMode.READ_ONLY, descriptor.startOffset, descriptor.declaredLength) }
        descriptor.close()
        val loaded = Interpreter(buffer, Interpreter.Options().apply { setNumThreads(2) })
        require(loaded.getInputTensor(0).shape().contentEquals(intArrayOf(1, featureCount))) { "unexpected_model_input_shape" }
        require(loaded.getOutputTensor(0).shape().last() == loadedLabels.size) { "label_count_mismatch" }
        interpreter = loaded; labels = loadedLabels; loaded to loadedLabels
      } catch (error: Exception) {
        initializationError = error.message ?: "litert_initialization_failed"; null
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

  override fun close() = synchronized(lock) { interpreter?.close(); interpreter = null; labels = null }
}
