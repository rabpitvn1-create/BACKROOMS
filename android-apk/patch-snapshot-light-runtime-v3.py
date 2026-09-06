from pathlib import Path

ROOT = Path(__file__).resolve().parent
APP = ROOT / "app"
ASSETS = APP / "src/main/assets"
CORE = APP / "src/main/java/com/rabpit/backroom/core"
MAIN = APP / "src/main/java/com/rabpit/backroom/MainActivity.java"
MODEL = ASSETS / "models/backroom_light_v3.tflite"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


def build_model() -> bool:
    try:
        import numpy as np
        import tensorflow as tf
    except Exception as error:
        print(f"Snapshot light LiteRT model build skipped in lightweight CI: {error}")
        return False

    MODEL.parent.mkdir(parents=True, exist_ok=True)
    inputs = tf.keras.Input(shape=(81, 144, 3), dtype=tf.float32, name="snapshot_rgb")
    luma_layer = tf.keras.layers.Conv2D(1, 1, use_bias=False, trainable=False, name="luma")
    luma = luma_layer(inputs)
    local = tf.keras.layers.AveragePooling2D(pool_size=(nine := 9, nine), strides=(1, 1), padding="same", name="local_mean")(luma)
    contrast = tf.keras.layers.ReLU(name="positive_contrast")(tf.keras.layers.Subtract()([luma, local]))
    outputs = tf.keras.layers.Concatenate(axis=-1, name="features")([luma, contrast])
    model = tf.keras.Model(inputs, outputs)
    luma_layer.set_weights([np.array([0.2126, 0.7152, 0.0722], dtype=np.float32).reshape((1, 1, 3, 1))])
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    MODEL.write_bytes(converter.convert())
    return MODEL.is_file() and MODEL.stat().st_size > 0


model_built = build_model()

(CORE / "SnapshotLightAnalyzer.kt").write_text(r'''package com.rabpit.backroom.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class SnapshotLightAnalyzer @JvmOverloads constructor(
  context: Context,
  modelAsset: String = "models/backroom_light_v3.tflite"
) : AutoCloseable {
  companion object {
    private const val W = 144
    private const val H = 81
    private const val CHANNELS = 3
    private const val MAX_LIGHTS = 8
  }

  private val app = context.applicationContext
  private val lock = Any()
  private val interpreter: Interpreter? = loadInterpreter(modelAsset)

  private fun loadInterpreter(asset: String): Interpreter? = runCatching {
    val descriptor = app.assets.openFd(asset)
    val mapped = descriptor.createInputStream().channel.use { channel ->
      channel.map(FileChannel.MapMode.READ_ONLY, descriptor.startOffset, descriptor.declaredLength)
    }
    descriptor.close()
    Interpreter(mapped, Interpreter.Options().apply { setNumThreads(2) }).also { runtime ->
      require(runtime.getInputTensor(0).shape().contentEquals(intArrayOf(1, H, W, CHANNELS)))
      require(runtime.getOutputTensor(0).shape().contentEquals(intArrayOf(1, H, W, 2)))
    }
  }.getOrNull()

  fun analyze(source: String): String {
    val bitmap = decodeSource(source) ?: return emptyResult("decode_unavailable")
    val scaled = Bitmap.createScaledBitmap(bitmap, W, H, true)
    return try {
      val pixels = IntArray(W * H)
      scaled.getPixels(pixels, 0, W, 0, 0, W, H)
      val lights = detect(runFeatures(pixels))
      JSONObject()
        .put("model", if (interpreter != null) "litert-light-v3" else "cpu-light-v3")
        .put("lights", JSONArray().apply {
          lights.forEach { light ->
            put(JSONObject()
              .put("x", light.centerX / W)
              .put("y", light.centerY / H)
              .put("w", light.width.toDouble() / W)
              .put("h", light.height.toDouble() / H)
              .put("kind", light.kind)
              .put("confidence", light.confidence))
          }
        })
        .toString()
    } finally {
      if (scaled !== bitmap && !scaled.isRecycled) scaled.recycle()
      if (!bitmap.isRecycled) bitmap.recycle()
    }
  }

  private fun runFeatures(pixels: IntArray): Array<Array<FloatArray>> {
    interpreter?.let { runtime ->
      val input = ByteBuffer.allocateDirect(W * H * CHANNELS * 4).order(ByteOrder.nativeOrder())
      pixels.forEach { pixel ->
        input.putFloat(((pixel shr 16) and 0xff) / 255f)
        input.putFloat(((pixel shr 8) and 0xff) / 255f)
        input.putFloat((pixel and 0xff) / 255f)
      }
      input.rewind()
      val output = Array(1) { Array(H) { Array(W) { FloatArray(2) } } }
      synchronized(lock) { runtime.run(input, output) }
      return output[0]
    }

    val luma = Array(H) { FloatArray(W) }
    val integral = Array(H + 1) { DoubleArray(W + 1) }
    for (y in 0 until H) for (x in 0 until W) {
      val p = pixels[y * W + x]
      val value = (.2126 * ((p shr 16) and 0xff) + .7152 * ((p shr 8) and 0xff) + .0722 * (p and 0xff)) / 255.0
      luma[y][x] = value.toFloat()
      integral[y + 1][x + 1] = value + integral[y][x + 1] + integral[y + 1][x] - integral[y][x]
    }
    val output = Array(H) { Array(W) { FloatArray(2) } }
    val radius = 4
    for (y in 0 until H) for (x in 0 until W) {
      val x0 = max(0, x - radius); val x1 = min(W - 1, x + radius)
      val y0 = max(0, y - radius); val y1 = min(H - 1, y + radius)
      val sum = integral[y1 + 1][x1 + 1] - integral[y0][x1 + 1] - integral[y1 + 1][x0] + integral[y0][x0]
      val mean = sum / ((x1 - x0 + 1) * (y1 - y0 + 1))
      val value = luma[y][x]
      output[y][x][0] = value
      output[y][x][1] = max(0.0, value.toDouble() - mean).toFloat()
    }
    return output
  }

  private data class Light(
    val centerX: Double,
    val centerY: Double,
    val width: Int,
    val height: Int,
    val kind: String,
    val area: Int,
    val confidence: Double
  )

  private fun detect(features: Array<Array<FloatArray>>): List<Light> {
    val mask = BooleanArray(W * H)
    for (y in 0 until H) for (x in 0 until W) {
      val luma = features[y][x][0]
      val contrast = features[y][x][1]
      mask[y * W + x] = luma >= .76f && (contrast >= .028f || luma >= .93f)
    }

    val seen = BooleanArray(W * H)
    val queue = IntArray(W * H)
    val found = mutableListOf<Light>()
    for (start in mask.indices) {
      if (!mask[start] || seen[start]) continue
      var head = 0
      var tail = 0
      queue[tail++] = start
      seen[start] = true
      var area = 0
      var minX = W
      var minY = H
      var maxX = 0
      var maxY = 0
      var sumLuma = 0.0
      var sumContrast = 0.0

      while (head < tail) {
        val p = queue[head++]
        val x = p % W
        val y = p / W
        area++
        minX = min(minX, x); maxX = max(maxX, x)
        minY = min(minY, y); maxY = max(maxY, y)
        sumLuma += features[y][x][0]
        sumContrast += features[y][x][1]

        fun visit(q: Int) {
          if (q in mask.indices && mask[q] && !seen[q]) {
            seen[q] = true
            queue[tail++] = q
          }
        }
        if (x > 0) visit(p - 1)
        if (x + 1 < W) visit(p + 1)
        if (y > 0) visit(p - W)
        if (y + 1 < H) visit(p + W)
        if (x > 0 && y > 0) visit(p - W - 1)
        if (x + 1 < W && y > 0) visit(p - W + 1)
        if (x > 0 && y + 1 < H) visit(p + W - 1)
        if (x + 1 < W && y + 1 < H) visit(p + W + 1)
      }

      val width = maxX - minX + 1
      val height = maxY - minY + 1
      val boxArea = width * height
      val aspect = width.toDouble() / max(1, height)
      val fill = area.toDouble() / max(1, boxArea)
      val avgLuma = sumLuma / max(1, area)
      val avgContrast = sumContrast / max(1, area)
      val linear = aspect >= 1.35 || aspect <= .74
      val point = area <= 24 && aspect in .65..1.55
      if (area < 2 || area > W * H * .075 || fill < .28 || minY > H * .90 || (!linear && !point)) continue

      val confidence = (.50 + avgLuma * .25 + avgContrast * 1.65 + min(.12, sqrt(area.toDouble()) * .012)).coerceIn(0.0, .99)
      found += Light(
        centerX = (minX + maxX + 1) / 2.0,
        centerY = (minY + maxY + 1) / 2.0,
        width = width,
        height = height,
        kind = if (linear) "linear" else "point",
        area = area,
        confidence = confidence
      )
    }
    return found.sortedByDescending { it.confidence * sqrt(it.area.toDouble()) }.take(MAX_LIGHTS)
  }

  private fun decodeSource(source: String): Bitmap? = try {
    when {
      source.startsWith("file:///android_asset/") -> {
        val path = source.removePrefix("file:///android_asset/").substringBefore('?').substringBefore('#')
        app.assets.open(path).use(BitmapFactory::decodeStream)
      }
      source.startsWith("data:image/") -> {
        val comma = source.indexOf(',')
        if (comma < 0) null else Base64.decode(source.substring(comma + 1), Base64.DEFAULT).let { bytes ->
          BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
      }
      else -> null
    }
  } catch (_: Exception) { null }

  private fun emptyResult(reason: String) = JSONObject()
    .put("model", "light-v3")
    .put("reason", reason)
    .put("lights", JSONArray())
    .toString()

  override fun close() {
    synchronized(lock) { interpreter?.close() }
  }
}
''', encoding="utf-8")

main = MAIN.read_text(encoding="utf-8")
core_import = "import com.rabpit.backroom.core.GameCoreFacade;\n"
light_import = "import com.rabpit.backroom.core.SnapshotLightAnalyzer;\n"
if light_import not in main:
    if core_import not in main:
        raise RuntimeError("GameCoreFacade import anchor missing")
    main = main.replace(core_import, core_import + light_import, 1)
if "private SnapshotLightAnalyzer snapshotLightAnalyzer;" not in main:
    main = replace_once(main, "  private GameCoreFacade gameCore;\n", "  private GameCoreFacade gameCore;\n  private SnapshotLightAnalyzer snapshotLightAnalyzer;\n", "SnapshotLightAnalyzer field")
if "snapshotLightAnalyzer = new SnapshotLightAnalyzer" not in main:
    main = replace_once(main, "    gameCore = GameCoreFacade.create(getApplicationContext(), BuildConfig.DEBUG);\n", "    gameCore = GameCoreFacade.create(getApplicationContext(), BuildConfig.DEBUG);\n    snapshotLightAnalyzer = new SnapshotLightAnalyzer(getApplicationContext());\n", "SnapshotLightAnalyzer init")
if "snapshotLightAnalyzer.close();" not in main:
    main = replace_once(main, "    if (gameCore != null) gameCore.close();\n", "    if (gameCore != null) gameCore.close();\n    if (snapshotLightAnalyzer != null) snapshotLightAnalyzer.close();\n", "SnapshotLightAnalyzer close")
bridge = '''    @JavascriptInterface public String analyzeSnapshotLights(String source) {
      if (snapshotLightAnalyzer == null) return "{\\\"lights\\\":[]}";
      try { return snapshotLightAnalyzer.analyze(source == null ? "" : source); }
      catch (Exception ignored) { return "{\\\"lights\\\":[]}"; }
    }

'''
if "@JavascriptInterface public String analyzeSnapshotLights" not in main:
    anchor = "    @JavascriptInterface public void clearCoreState() {\n"
    if anchor not in main:
        raise RuntimeError("GameBridge anchor missing")
    main = main.replace(anchor, bridge + anchor, 1)
MAIN.write_text(main, encoding="utf-8")

if model_built and (not MODEL.is_file() or MODEL.stat().st_size <= 0):
    raise RuntimeError("Snapshot light v3 LiteRT model missing after build")

analyzer = (CORE / "SnapshotLightAnalyzer.kt").read_text(encoding="utf-8")
for marker in [
    "centerX = (minX + maxX + 1) / 2.0",
    "centerY = (minY + maxY + 1) / 2.0",
    "if (x > 0 && y > 0) visit(p - W - 1)",
]:
    if marker not in analyzer:
        raise RuntimeError(f"Snapshot light center contract missing: {marker}")

print("Snapshot light runtime v3 wired with bounding-box-centered fixture metadata, 8-neighbor grouping and deterministic fallback.")
