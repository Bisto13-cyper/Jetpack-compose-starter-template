package com.superapp.app.features.webview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import java.io.File

/** Saves/loads the custom photo for the WebView node (square, stored in app-private storage). */
object WebViewNodeIconStore {
    private const val FILE_NAME = "webview_node_icon.png"
    private const val MAX_SIZE = 512

    /** Changes whenever the photo is saved or cleared, so Compose can refresh. */
    var version by mutableStateOf(0)
        private set

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE_NAME)

    fun hasCustomIcon(context: Context): Boolean = file(context).exists()

    fun saveFromUri(context: Context, uri: Uri): Boolean {
        return try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > MAX_SIZE * 2 || bounds.outHeight / sample > MAX_SIZE * 2) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val source = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return false
            val side = minOf(source.width, source.height)
            val square = Bitmap.createBitmap(
                source,
                (source.width - side) / 2,
                (source.height - side) / 2,
                side,
                side
            )
            val result = if (side > MAX_SIZE) Bitmap.createScaledBitmap(square, MAX_SIZE, MAX_SIZE, true) else square
            file(context).outputStream().use { result.compress(Bitmap.CompressFormat.PNG, 100, it) }
            version++
            true
        } catch (e: Exception) {
            false
        }
    }

    fun clear(context: Context) {
        file(context).delete()
        version++
    }

    fun loadBitmap(context: Context): ImageBitmap? {
        val f = file(context)
        if (!f.exists()) return null
        return BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
    }
}

/**
 * Drop-in node icon: shows the custom photo if one was chosen, otherwise the emoji.
 * Use it wherever the WebView node currently draws its emoji.
 */
@Composable
fun WebViewNodeIcon(
    emoji: String,
    modifier: Modifier = Modifier,
    emojiSize: TextUnit = 28.sp
) {
    val context = LocalContext.current
    val version = WebViewNodeIconStore.version
    val bitmap = remember(version) { WebViewNodeIconStore.loadBitmap(context) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(CircleShape)
        )
    } else {
        Text(text = emoji, fontSize = emojiSize, modifier = modifier)
    }
}
