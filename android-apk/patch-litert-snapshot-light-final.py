from pathlib import Path

ROOT = Path(__file__).resolve().parent
APP = ROOT / "app"
ASSETS = APP / "src/main/assets"
CORE = APP / "src/main/java/com/rabpit/backroom/core"
MAIN = APP / "src/main/java/com/rabpit/backroom/MainActivity.java"
MODEL = ASSETS / "models/backroom_light.tflite"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


def build_light_model() -> bool:
    try:
        import numpy as np
        import tensorflow as tf
    except Exception as error:
        print(f"LiteRT light model build skipped in lightweight CI: {error}")
        return False

    MODEL.parent.mkdir(parents=True, exist_ok=True)
    inputs = tf.keras.Input(shape=(81, 144, 3), dtype=tf.float32, name="snapshot_rgb")
    luminance_layer = tf.keras.layers.Conv2D(1, 1, use_bias=False, trainable=False, name="luminance")
    luminance = luminance_layer(inputs)
    local_mean = tf.keras.layers.AveragePooling2D(pool_size=(11, 11), strides=(1, 1), padding="same", name="local_mean")(luminance)
    contrast = tf.keras.layers.ReLU(name="positive_local_contrast")(tf.keras.layers.Subtract()([luminance, local_mean]))
    outputs = tf.keras.layers.Concatenate(axis=-1, name="light_features")([luminance, contrast])
    model = tf.keras.Model(inputs, outputs)
    luminance_layer.set_weights([np.array([0.2126, 0.7152, 0.0722], dtype=np.float32).reshape((1, 1, 3, 1))])
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    MODEL.write_bytes(converter.convert())

    interpreter = tf.lite.Interpreter(model_path=str(MODEL))
    interpreter.allocate_tensors()
    input_info = interpreter.get_input_details()[0]
    output_info = interpreter.get_output_details()[0]
    if tuple(input_info["shape"]) != (1, 81, 144, 3):
        raise RuntimeError(f"unexpected LiteRT light input shape: {input_info['shape']}")
    if tuple(output_info["shape"]) != (1, 81, 144, 2):
        raise RuntimeError(f"unexpected LiteRT light output shape: {output_info['shape']}")
    synthetic = np.full((1, 81, 144, 3), 0.12, dtype=np.float32)
    synthetic[:, 8:12, 40:104, :] = 1.0
    interpreter.set_tensor(input_info["index"], synthetic.astype(input_info["dtype"]))
    interpreter.invoke()
    result = interpreter.get_tensor(output_info["index"])
    if float(result[0, 9, 70, 0]) < 0.9 or float(result[0, 9, 70, 1]) <= 0.0:
        raise RuntimeError("LiteRT light model synthetic fixture self-check failed")
    return True


model_built = build_light_model()

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

class SnapshotLightAnalyzer(
  context: Context,
  modelAsset: String = "models/backroom_light.tflite"
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

  private fun loadInterpreter(modelAsset: String): Interpreter? = runCatching {
    val descriptor = app.assets.openFd(modelAsset)
    val mapped = descriptor.createInputStream().channel.use { channel ->
      channel.map(FileChannel.MapMode.READ_ONLY, descriptor.startOffset, descriptor.declaredLength)
    }
    descriptor.close()
    val loaded = Interpreter(mapped, Interpreter.Options().apply { setNumThreads(2) })
    require(loaded.getInputTensor(0).shape().contentEquals(intArrayOf(1, H, W, CHANNELS))) { "unexpected_light_model_input" }
    require(loaded.getOutputTensor(0).shape().contentEquals(intArrayOf(1, H, W, 2))) { "unexpected_light_model_output" }
    loaded
  }.getOrNull()

  fun analyze(source: String): String {
    val bitmap = decodeSource(source) ?: return emptyResult("decode_unavailable")
    val scaledBitmap = Bitmap.createScaledBitmap(bitmap, W, H, true)
    return try {
      val pixels = IntArray(W * H)
      scaledBitmap.getPixels(pixels, 0, W, 0, 0, W, H)
      val features = runFeatures(pixels)
      val lights = detect(features)
      JSONObject().put("model", if (interpreter != null) "litert-luma-contrast-v1" else "cpu-luma-contrast-fallback-v1").put("lights", JSONArray().apply {
        lights.forEach { light ->
          put(JSONObject()
            .put("x", light.minX.toDouble() / W)
            .put("y", light.minY.toDouble() / H)
            .put("w", light.width.toDouble() / W)
            .put("h", light.height.toDouble() / H)
            .put("confidence", light.confidence))
        }
      }).toString()
    } finally {
      if (scaledBitmap !== bitmap && !scaledBitmap.isRecycled) scaledBitmap.recycle()
      if (!bitmap.isRecycled) bitmap.recycle()
    }
  }

  private fun runFeatures(pixels: IntArray): Array<Array<FloatArray>> {
    val runtime = interpreter
    if (runtime != null) {
      val input = ByteBuffer.allocateDirect(W * H * CHANNELS * 4).order(ByteOrder.nativeOrder())
      for (pixel in pixels) {
        input.putFloat(((pixel shr 16) and 0xff) / 255f)
        input.putFloat(((pixel shr 8) and 0xff) / 255f)
        input.putFloat((pixel and 0xff) / 255f)
      }
      input.rewind()
      val output = Array(1) { Array(H) { Array(W) { FloatArray(2) } } }
      synchronized(lock) { runtime.run(input, output) }
      return output[0]
    }
    val lum = Array(H) { FloatArray(W) }
    val integral = Array(H + 1) { DoubleArray(W + 1) }
    for (y in 0 until H) for (x in 0 until W) {
      val pixel = pixels[y * W + x]
      val value = (.2126 * ((pixel shr 16) and 0xff) + .7152 * ((pixel shr 8) and 0xff) + .0722 * (pixel and 0xff)) / 255.0
      lum[y][x] = value.toFloat()
      integral[y + 1][x + 1] = value + integral[y][x + 1] + integral[y + 1][x] - integral[y][x]
    }
    val output = Array(H) { Array(W) { FloatArray(2) } }
    val radius = 5
    for (y in 0 until H) for (x in 0 until W) {
      val x0 = max(0, x - radius); val x1 = min(W - 1, x + radius)
      val y0 = max(0, y - radius); val y1 = min(H - 1, y + radius)
      val sum = integral[y1 + 1][x1 + 1] - integral[y0][x1 + 1] - integral[y1 + 1][x0] + integral[y0][x0]
      val mean = sum / ((x1 - x0 + 1) * (y1 - y0 + 1))
      val value = lum[y][x]
      output[y][x][0] = value
      output[y][x][1] = max(0.0, value.toDouble() - mean).toFloat()
    }
    return output
  }

  private data class Light(
    val minX: Int,
    val minY: Int,
    val maxX: Int,
    val maxY: Int,
    val area: Int,
    val confidence: Double
  ) {
    val width: Int get() = maxX - minX + 1
    val height: Int get() = maxY - minY + 1
  }

  private fun detect(features: Array<Array<FloatArray>>): List<Light> {
    val mask = BooleanArray(W * H)
    for (y in 0 until H) for (x in 0 until W) {
      val luminance = features[y][x][0]
      val contrast = features[y][x][1]
      mask[y * W + x] = luminance >= 0.74f && (contrast >= 0.035f || luminance >= 0.92f)
    }
    val seen = BooleanArray(W * H)
    val found = mutableListOf<Light>()
    val queue = IntArray(W * H)
    for (start in mask.indices) {
      if (!mask[start] || seen[start]) continue
      var head = 0
      var tail = 0
      queue[tail++] = start
      seen[start] = true
      var area = 0
      var minX = W
      var maxX = 0
      var minY = H
      var maxY = 0
      var lumSum = 0.0
      var contrastSum = 0.0
      while (head < tail) {
        val p = queue[head++]
        val x = p % W
        val y = p / W
        area++
        minX = min(minX, x); maxX = max(maxX, x)
        minY = min(minY, y); maxY = max(maxY, y)
        lumSum += features[y][x][0]
        contrastSum += features[y][x][1]
        fun push(q: Int) {
          if (q in mask.indices && mask[q] && !seen[q]) {
            seen[q] = true
            queue[tail++] = q
          }
        }
        if (x > 0) push(p - 1)
        if (x + 1 < W) push(p + 1)
        if (y > 0) push(p - W)
        if (y + 1 < H) push(p + W)
      }
      val bw = maxX - minX + 1
      val bh = maxY - minY + 1
      val boxArea = bw * bh
      val fill = area.toDouble() / max(1, boxArea)
      val aspect = bw.toDouble() / max(1, bh)
      val avgLum = lumSum / max(1, area)
      val avgContrast = contrastSum / max(1, area)
      val fixtureShape = (aspect in 1.25..18.0 && bw >= 3) || (aspect <= 0.78 && bh >= 4) || (area <= 22 && aspect in 0.65..1.55)
      if (area < 2 || area > W * H * 0.09 || fill < 0.30 || minY > H * 0.88 || !fixtureShape) continue
      val confidence = (0.52 + avgLum * 0.24 + avgContrast * 1.8 + min(0.12, sqrt(area.toDouble()) * 0.012)).coerceIn(0.0, 0.99)
      found += Light(minX, minY, maxX, maxY, area, confidence)
    }
    return found.sortedByDescending { it.confidence * sqrt(it.area.toDouble()) }.take(MAX_LIGHTS)
  }

  private fun decodeSource(source: String): Bitmap? = try {
    when {
      source.startsWith("file:///android_asset/") -> {
        val path = source.removePrefix("file:///android_asset/").substringBefore('?').substringBefore('#')
        app.assets.open(path).use { stream -> BitmapFactory.decodeStream(stream) }
      }
      source.startsWith("data:image/") -> {
        val comma = source.indexOf(',')
        if (comma < 0) null else {
          val bytes = Base64.decode(source.substring(comma + 1), Base64.DEFAULT)
          BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
      }
      else -> null
    }
  } catch (_: Exception) { null }

  private fun emptyResult(reason: String): String = JSONObject()
    .put("model", "litert-luma-contrast-v1")
    .put("reason", reason)
    .put("lights", JSONArray())
    .toString()

  override fun close() = synchronized(lock) { interpreter?.close() }
}
''', encoding="utf-8")

main = MAIN.read_text(encoding="utf-8")
core_import = "import com.rabpit.backroom.core.GameCoreFacade;\n"
light_import = "import com.rabpit.backroom.core.SnapshotLightAnalyzer;\n"
if light_import not in main:
    if core_import not in main:
        raise RuntimeError("GameCoreFacade import anchor missing for SnapshotLightAnalyzer")
    main = main.replace(core_import, core_import + light_import, 1)
if "private SnapshotLightAnalyzer snapshotLightAnalyzer;" not in main:
    main = replace_once(main, "  private GameCoreFacade gameCore;\n", "  private GameCoreFacade gameCore;\n  private SnapshotLightAnalyzer snapshotLightAnalyzer;\n", "SnapshotLightAnalyzer field")
if "snapshotLightAnalyzer = new SnapshotLightAnalyzer" not in main:
    main = replace_once(main, "    gameCore = GameCoreFacade.create(getApplicationContext(), BuildConfig.DEBUG);\n", "    gameCore = GameCoreFacade.create(getApplicationContext(), BuildConfig.DEBUG);\n    snapshotLightAnalyzer = new SnapshotLightAnalyzer(getApplicationContext());\n", "SnapshotLightAnalyzer initialization")
if "snapshotLightAnalyzer.close();" not in main:
    main = replace_once(main, "    if (gameCore != null) gameCore.close();\n", "    if (gameCore != null) gameCore.close();\n    if (snapshotLightAnalyzer != null) snapshotLightAnalyzer.close();\n", "SnapshotLightAnalyzer close")
bridge_method = '''    @JavascriptInterface public String analyzeSnapshotLights(String source) {
      if (snapshotLightAnalyzer == null) return "{\\\"lights\\\":[]}";
      try { return snapshotLightAnalyzer.analyze(source == null ? "" : source); }
      catch (Exception ignored) { return "{\\\"lights\\\":[]}"; }
    }

'''
if "@JavascriptInterface public String analyzeSnapshotLights" not in main:
    anchor = "    @JavascriptInterface public void clearCoreState() {\n"
    if anchor not in main:
        raise RuntimeError("GameBridge clearCoreState anchor missing")
    main = main.replace(anchor, bridge_method + anchor, 1)
MAIN.write_text(main, encoding="utf-8")

if model_built and (not MODEL.is_file() or MODEL.stat().st_size <= 0):
    raise RuntimeError("LiteRT Snapshot light model missing after successful build")
print("Snapshot light analyzer wired: LiteRT when model build dependencies are present, deterministic CPU fallback otherwise.")
