package com.saitkaplan.downowder

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class SaveResult { SAVED, UNSUPPORTED, FAILED }

/**
 * Copies a shared image or video into Download/DowNowder/, byte for byte.
 *
 * The file is never decoded: quality, resolution and metadata (EXIF) stay
 * exactly as sent. The entry is added as pending, so no other app sees a
 * half-written file, and it is removed again if the copy does not finish.
 * When the name is already taken, MediaStore picks a free one ("name (1).jpg").
 */
internal class MediaSaver(private val resolver: ContentResolver) {

    /**
     * Saves one file. A sender's content provider can throw any runtime
     * exception; it only fails this file instead of crashing the share.
     * Cancellation is passed on untouched.
     */
    suspend fun save(source: Uri): SaveResult = try {
        saveOrThrow(source)
    } catch (e: CancellationException) {
        throw e
    } catch (e: RuntimeException) {
        logFailure(e)
        SaveResult.FAILED
    }

    private suspend fun saveOrThrow(source: Uri): SaveResult {
        val displayName = queryDisplayName(source)
        val header = readHeader(source) ?: return SaveResult.FAILED
        val type = MediaTypes.resolve(resolver.getType(source), displayName, header)
            ?: return SaveResult.UNSUPPORTED

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName(displayName, type))
            put(MediaStore.MediaColumns.MIME_TYPE, type.mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val target = resolver.insert(DOWNLOADS, values) ?: return SaveResult.FAILED

        // Any failure leaves done false; the pending entry is removed below.
        var done = false
        try {
            copy(source, target)
            val publish = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            done = resolver.update(target, publish, null, null) == 1
        } catch (e: CancellationException) {
            // Must come before IllegalStateException, its parent class.
            throw e
        } catch (e: IOException) {
            logFailure(e)
        } catch (e: SecurityException) {
            logFailure(e)
        } catch (e: IllegalStateException) {
            logFailure(e)
        } finally {
            // Also runs when the coroutine is cancelled mid-copy. A failing
            // delete must not hide the original error.
            if (!done) runCatching { resolver.delete(target, null, null) }
        }
        return if (done) SaveResult.SAVED else SaveResult.FAILED
    }

    private fun logFailure(error: Exception) {
        Log.w(TAG, "Copy failed: ${error.javaClass.simpleName}")
    }

    private suspend fun copy(source: Uri, target: Uri) {
        val input = resolver.openInputStream(source) ?: throw IOException("No input stream")
        input.use { from ->
            val output = resolver.openOutputStream(target) ?: throw IOException("No output stream")
            output.use { to ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = from.read(buffer)
                    if (read < 0) break
                    to.write(buffer, 0, read)
                }
            }
        }
    }

    // InputStream.readNBytes is only available from API 33, so the header is
    // read with a plain loop (a single read may return fewer bytes).
    private fun readHeader(source: Uri): ByteArray? = try {
        resolver.openInputStream(source)?.use { input ->
            val header = ByteArray(MediaTypes.HEADER_SIZE)
            var filled = 0
            while (filled < header.size) {
                val read = input.read(header, filled, header.size - filled)
                if (read < 0) break
                filled += read
            }
            header.copyOf(filled)
        }
    } catch (e: IOException) {
        null
    } catch (e: SecurityException) {
        null
    }

    private fun queryDisplayName(source: Uri): String? = try {
        resolver.query(source, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?.substringAfterLast('/')
            ?.trim()
            // A leading dot would hide the file; ".nomedia" would even hide
            // the whole folder from the gallery.
            ?.trimStart('.')
            ?.takeIf { it.isNotEmpty() }
    } catch (e: SecurityException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    // Keeps the sender's name; adds the real extension when the name has
    // none or one that does not match the content (e.g. "photo.bin").
    private fun fileName(displayName: String?, type: MediaType): String {
        if (displayName == null) {
            val prefix = if (type.kind == MediaKind.IMAGE) "IMG" else "VID"
            val stamp = LocalDateTime.now().format(STAMP_FORMAT)
            return "${prefix}_$stamp.${type.extension}"
        }
        val current = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val matches = MediaTypes.resolve(null, displayName, ByteArray(0))?.mime == type.mime
        return if (current.isNotEmpty() && matches) displayName else "$displayName.${type.extension}"
    }

    private companion object {
        const val TAG = "DowNowder"
        val DOWNLOADS: Uri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val RELATIVE_PATH = "${Environment.DIRECTORY_DOWNLOADS}/DowNowder"
        val STAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.ROOT)
        const val BUFFER_SIZE = 64 * 1024
    }
}
