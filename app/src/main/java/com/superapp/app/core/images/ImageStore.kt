package com.superapp.app.core.images

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max

/**
 * SETTINGS FILE 3 - Turns a photo the user picked into a safe private copy.
 *
 * Why a copy? A picked photo only gives temporary access. So we copy it
 * into the app's own private folder. The copy is:
 *  - shrunk (max 2048 px on the longest side) so it never uses too much memory
 *  - rotated correctly (phone photos often store their rotation separately)
 *  - saved as JPEG, 90% quality
 *
 * Nothing is uploaded anywhere. The file stays inside the app.
 *
 * This helper is reusable: backgrounds, custom circle icons, and any
 * future feature that needs a user photo can call it.
 *
 * HOW TO CHANGE:
 *  - Max size : change maxSide (default 2048)
 *  - Quality  : change 90 in compress()
 *  - Folder   : pass a different `folder` name, for example "icons"
 */
object ImageStore {

    /**
     * Copies and saves the picked photo. Returns the saved file path,
     * or null if the photo could not be read.
     * Call it from a background thread (Dispatchers.IO).
     */
    fun importImage(
        context: Context,
        uri: Uri,
        folder: String = "backgrounds",
        maxSide: Int = 2048
    ): String? {
        return runCatching {
            val resolver = context.contentResolver

            // 1) Read only the size, to decide how much to shrink.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2

            // 2) Decode the shrunk image.
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return null

            // 3) Fix the rotation if the photo needs it.
            val degrees = resolver.openInputStream(uri)?.use { readRotation(it) } ?: 0
            val bitmap = if (degrees != 0) {
                val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            } else {
                decoded
            }

            // 4) Save it inside the app's private folder.
            val dir = File(context.filesDir, folder).apply { mkdirs() }
            val file = File(dir, "img_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            file.absolutePath
        }.getOrNull()
    }

    /**
     * Deletes a saved image. It only deletes files inside the app's own
     * private folder, never anything else on the phone.
     */
    fun delete(context: Context, path: String?) {
        if (path == null) return
        runCatching {
            val file = File(path)
            val root = context.filesDir.canonicalPath
            if (file.canonicalPath.startsWith(root)) file.delete()
        }
    }

    /**
     * Loads a saved image for drawing, shrunk so it stays light.
     * Returns null if the file is missing or broken.
     */
    fun loadScaled(path: String, maxSide: Int = 1440): ImageBitmap? {
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)

            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2

            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(path, options)?.asImageBitmap()
        }.getOrNull()
    }

    private fun readRotation(stream: InputStream): Int {
        return runCatching {
            when (
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        }.getOrDefault(0)
    }
}
