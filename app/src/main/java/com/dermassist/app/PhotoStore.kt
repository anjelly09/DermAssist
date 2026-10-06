package com.dermassist.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File

class PhotoStore(private val context: Context) {
    private val directory get() = File(context.cacheDir, "captures").apply { mkdirs() }
    fun newCapture(): File = File.createTempFile("capture-", ".jpg", directory)
    fun clearExcept(paths: Set<String>) {
        directory.listFiles()?.filter { it.absolutePath !in paths }?.forEach { it.delete() }
    }
    fun delete(path: String?) { path?.let { File(it).takeIf { f -> f.parentFile == directory }?.delete() } }

    /** Two-pass decode bounds memory use; re-encoding drops EXIF, including GPS. */
    fun prepare(uri: Uri): String {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "This photo could not be opened. Choose a JPEG or PNG photo." }
        require(!PhotoRules.tooSmall(bounds.outWidth, bounds.outHeight)) { "This photo is too small. Take a closer, higher-resolution photo." }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("This photo could not be opened. Please choose another photo.")
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
            }
        }
        var rotated: Bitmap? = null
        var output: File? = null
        try {
            rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            output = newCapture()
            output.outputStream().use { check(rotated.compress(Bitmap.CompressFormat.JPEG, 88, it)) }
            return output.absolutePath
        } catch (e: Exception) {
            output?.delete()
            throw e
        } finally {
            if (rotated !== decoded) rotated?.recycle()
            decoded.recycle()
        }
    }
}
