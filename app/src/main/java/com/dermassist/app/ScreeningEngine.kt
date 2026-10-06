package com.dermassist.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/** Bundled CPU inference; no Play-services dependency, upload, or network request. */
class ScreeningEngine(private val context: Context) {
    fun assess(path: String): ScreeningResult {
        val start = SystemClock.elapsedRealtime()
        return try {
            val config = JSONObject(context.assets.open("screening.json").bufferedReader().use { it.readText() })
            require(config.getInt("schema_version") == 1)
            val modelId = config.getString("model_id")
            val size = config.getInt("input_size")
            require(size in 32..512)
            val bitmap = BitmapFactory.decodeFile(path) ?: return ScreeningResult(reason = "This photo could not be read. Please choose another photo.")
            val scaled = try { Bitmap.createScaledBitmap(bitmap, size, size, true) } catch (e: Exception) { bitmap.recycle(); throw e }
            val pixels = IntArray(size * size)
            try { scaled.getPixels(pixels, 0, size, 0, 0, size, size) }
            finally { if (scaled !== bitmap) scaled.recycle(); bitmap.recycle() }
            val quality = config.getJSONObject("quality")
            val rejection = ImageQuality.rejection(pixels, size, size, quality.getDouble("min_luminance"), quality.getDouble("max_luminance"), quality.getDouble("min_laplacian_variance"))
            if (rejection != null) return ScreeningResult(ScreeningStatus.PHOTO_REJECTED, reason = rejection, modelId = modelId)
            val input = ByteBuffer.allocateDirect(size * size * 3 * 4).order(ByteOrder.nativeOrder())
            pixels.forEach { p ->
                input.putFloat(((p shr 16) and 255) / 127.5f - 1f)
                input.putFloat(((p shr 8) and 255) / 127.5f - 1f)
                input.putFloat((p and 255) / 127.5f - 1f)
            }
            input.rewind()
            val classesJson = config.getJSONArray("classes")
            val classes = List(classesJson.length()) { classesJson.getString(it) }
            require(classes.size in 2..10)
            val bytes = context.assets.open("screening.tflite").use { it.readBytes() }
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
            require(digest == config.getString("model_sha256"))
            val model = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply { put(bytes); rewind() }
            val count = classes.size + config.getInt("embedding_size")
            require(count in 3..8192)
            val output = arrayOf(FloatArray(count))
            Interpreter(model, Interpreter.Options().setNumThreads(2)).use { interpreter ->
                require(interpreter.getInputTensor(0).shape().contentEquals(intArrayOf(1, size, size, 3)))
                require(interpreter.getOutputTensor(0).shape().contentEquals(intArrayOf(1, count)))
                interpreter.run(input, output)
            }
            val probability = ScreeningMath.probabilities(output[0].copyOfRange(0, classes.size), config.getDouble("temperature"))
            val centroidJson = config.getJSONArray("centroids")
            val centroids = List(centroidJson.length()) { i -> centroidJson.getJSONArray(i).let { array -> DoubleArray(array.length()) { array.getDouble(it) } } }
            val distance = ScreeningMath.distance(output[0].copyOfRange(classes.size, count), centroids)
            val top = probability.indices.maxBy { probability[it] }
            val policy = config.getJSONObject("policy")
            val accepted = ScreeningMath.accepts(probability[top], distance, policy.getDouble("confidence_threshold"), policy.getDouble("distance_threshold"), policy.getBoolean("enabled"))
            val deployment = config.optJSONObject("deployment")
            if (deployment?.optBoolean("suggestions_enabled", false) != true) {
                return ScreeningResult(ScreeningStatus.UNCERTAIN,
                    reason = deployment?.optString("reason") ?: "This research model is not validated for condition suggestions. Your concern remains unassessed.",
                    modelId = modelId, latencyMs = SystemClock.elapsedRealtime() - start)
            }
            ScreeningResult(
                status = if (accepted) ScreeningStatus.EXPERIMENTAL_MATCH else ScreeningStatus.UNCERTAIN,
                condition = if (accepted) classes[top] else null,
                reason = if (accepted) "The model found a visual pattern resembling ${classes[top].lowercase()}. It was trained on a limited public dataset and can be wrong. Symptoms are recorded separately and do not change this image-only prediction."
                    else "The model could not provide a sufficiently reliable suggestion. The photo may show a condition outside its training categories. This does not mean the concern is harmless.",
                modelId = modelId, latencyMs = SystemClock.elapsedRealtime() - start,
            )
        } catch (_: Exception) {
            ScreeningResult(reason = "Screening could not run on this device. You can still save your notes and discuss the concern with a health worker.")
        }
    }
}
