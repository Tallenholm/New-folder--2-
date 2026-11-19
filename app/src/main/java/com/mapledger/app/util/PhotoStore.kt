package com.mapledger.app.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Odometer / receipt / screenshot evidence lives in private app storage (not the shared gallery). */
object PhotoStore {
    private fun dir(ctx: Context) = File(ctx.filesDir, "photos").apply { mkdirs() }

    /** A new file + content:// URI for ActivityResultContracts.TakePicture. */
    fun newCaptureTarget(ctx: Context, prefix: String): Pair<File, Uri> {
        val f = File(dir(ctx), "${prefix}_${System.currentTimeMillis()}.jpg")
        return f to FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    }

    /** Copies a picked image into private storage; returns the stored absolute path. */
    fun importPicked(ctx: Context, src: Uri, prefix: String): String? = runCatching {
        val f = File(dir(ctx), "${prefix}_${System.currentTimeMillis()}.jpg")
        ctx.contentResolver.openInputStream(src)?.use { input -> f.outputStream().use { input.copyTo(it) } } ?: return null
        f.absolutePath
    }.getOrNull()
}
