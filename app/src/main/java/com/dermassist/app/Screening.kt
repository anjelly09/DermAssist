package com.dermassist.app

import kotlin.math.exp
import kotlin.math.sqrt

enum class ScreeningStatus { EXPERIMENTAL_MATCH, UNCERTAIN, PHOTO_REJECTED, UNAVAILABLE }

data class ScreeningResult(
    val status: ScreeningStatus = ScreeningStatus.UNAVAILABLE,
    val condition: String? = null,
    val reason: String = "No model is installed; this record contains no diagnosis or screening prediction.",
    val modelId: String? = null,
    val latencyMs: Long = 0,
) {
    val title: String get() = when (status) {
        ScreeningStatus.EXPERIMENTAL_MATCH -> "Possible pattern: ${condition ?: "unknown"}"
        ScreeningStatus.PHOTO_REJECTED -> "A clearer photo is needed"
        else -> "Unable to assess"
    }
    fun reportText() = "$title\n$reason\nEducational screening prototype, not a diagnosis. It cannot rule out other conditions or establish that skin is healthy.\n" +
        (modelId?.let { "Model: $it\n" } ?: "")
}

/** Stable softmax and cosine-distance rejection, mirrored by ml/metrics.py. */
object ScreeningMath {
    fun probabilities(logits: FloatArray, temperature: Double): DoubleArray {
        require(temperature.isFinite() && temperature > 0 && logits.isNotEmpty() && logits.all { it.isFinite() })
        val scaled = logits.map { it / temperature }
        val max = scaled.max()
        val exponentials = scaled.map { exp(it - max) }
        val sum = exponentials.sum()
        return exponentials.map { it / sum }.toDoubleArray()
    }
    fun distance(embedding: FloatArray, centroids: List<DoubleArray>): Double {
        require(embedding.all { it.isFinite() } && centroids.isNotEmpty())
        val norm = sqrt(embedding.sumOf { it.toDouble() * it }).coerceAtLeast(1e-8)
        return 1 - centroids.maxOf { centroid ->
            require(centroid.size == embedding.size && centroid.all { it.isFinite() })
            val centroidNorm = sqrt(centroid.sumOf { it * it }).coerceAtLeast(1e-8)
            embedding.indices.sumOf { embedding[it] * centroid[it] } / (norm * centroidNorm)
        }
    }
    fun accepts(probability: Double, distance: Double, confidenceThreshold: Double, distanceThreshold: Double, enabled: Boolean) =
        enabled && probability.isFinite() && distance.isFinite() && probability >= confidenceThreshold && distance <= distanceThreshold
}

object ImageQuality {
    /** Technical guardrails for extreme exposure/flat images, not validated skin detection. */
    fun rejection(pixels: IntArray, width: Int, height: Int, minLuma: Double, maxLuma: Double, minVariance: Double): String? {
        require(width >= 3 && height >= 3 && pixels.size == width * height)
        val gray = DoubleArray(pixels.size) { i ->
            val p = pixels[i]
            .299 * ((p shr 16) and 255) + .587 * ((p shr 8) and 255) + .114 * (p and 255)
        }
        val mean = gray.average()
        if (mean < minLuma) return "The photo is very dark. Retake it in even, natural light."
        if (mean > maxLuma) return "The photo is overexposed. Retake it away from glare."
        var sum = 0.0; var squared = 0.0; var n = 0
        for (y in 1 until height - 1) for (x in 1 until width - 1) {
            val i = y * width + x
            val lap = gray[i-width] + gray[i+width] + gray[i-1] + gray[i+1] - 4 * gray[i]
            sum += lap; squared += lap * lap; n++
        }
        if (squared / n - (sum / n) * (sum / n) < minVariance) return "The photo has too little detail. Hold the camera steady and refocus on the area."
        return null
    }
}
