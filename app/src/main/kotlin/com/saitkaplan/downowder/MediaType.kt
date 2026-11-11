package com.saitkaplan.downowder

import android.webkit.MimeTypeMap
import java.util.Locale

internal enum class MediaKind { IMAGE, VIDEO }

internal data class MediaType(val mime: String, val kind: MediaKind, val extension: String)

/**
 * Decides whether a shared file is an image or a video.
 *
 * Order: a specific type declared by the sender, then the file's first bytes,
 * then the file name's extension. Files sent as documents often arrive as
 * application/octet-stream, so the bytes are the reliable source; the
 * extension is only a last resort because it can be wrong.
 */
internal object MediaTypes {

    /** Bytes [sniff] needs from the start of the file. */
    const val HEADER_SIZE = 64

    fun resolve(declaredMime: String?, displayName: String?, header: ByteArray): MediaType? =
        fromMime(declaredMime) ?: sniff(header) ?: fromExtension(displayName)

    private fun fromMime(mime: String?): MediaType? {
        val clean = mime?.lowercase(Locale.ROOT)?.substringBefore(';')?.trim() ?: return null
        val kind = when {
            clean.startsWith("image/") -> MediaKind.IMAGE
            clean.startsWith("video/") -> MediaKind.VIDEO
            else -> return null
        }
        // A wildcard such as image/* says nothing about the real format.
        if (clean.endsWith("/*")) return null
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(clean) ?: return null
        return MediaType(clean, kind, extension)
    }

    private fun fromExtension(displayName: String?): MediaType? {
        val extension = displayName?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)
        if (extension.isNullOrEmpty()) return null
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: return null
        return fromMime(mime)
    }

    private fun sniff(h: ByteArray): MediaType? = when {
        h.startsWith(0xFF, 0xD8, 0xFF) -> image("image/jpeg", "jpg")
        h.startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> image("image/png", "png")
        h.ascii(0, 6) == "GIF87a" || h.ascii(0, 6) == "GIF89a" -> image("image/gif", "gif")
        h.ascii(0, 4) == "RIFF" && h.ascii(8, 4) == "WEBP" -> image("image/webp", "webp")
        h.ascii(0, 4) == "RIFF" && h.ascii(8, 4) == "AVI " -> video("video/x-msvideo", "avi")
        h.ascii(4, 4) == "ftyp" -> isoMedia(h.ascii(8, 4))
        h.startsWith(0x1A, 0x45, 0xDF, 0xA3) ->
            if (h.ascii(0, h.size).contains("webm")) {
                video("video/webm", "webm")
            } else {
                video("video/x-matroska", "mkv")
            }

        else -> null
    }

    // ISO base media files (MP4, MOV, 3GP, HEIC, AVIF) name their format in
    // the "major brand" right after the ftyp box type. Brands are listed
    // explicitly: the same container also carries audio (M4A) and raw
    // camera files, which must not be saved as video.
    private fun isoMedia(brand: String): MediaType? = when (brand) {
        "heic", "heix", "heim", "heis", "hevc", "hevx" -> image("image/heic", "heic")
        "mif1", "msf1" -> image("image/heif", "heif")
        "avif", "avis" -> image("image/avif", "avif")
        "qt  " -> video("video/quicktime", "mov")
        "3gp4", "3gp5", "3gp6", "3gg6" -> video("video/3gpp", "3gp")
        "3g2a" -> video("video/3gpp2", "3g2")
        in MP4_BRANDS -> video("video/mp4", "mp4")
        else -> null
    }

    private val MP4_BRANDS = setOf(
        "isom", "iso2", "iso3", "iso4", "iso5", "iso6", "mp41", "mp42", "avc1",
        "M4V ", "M4VH", "M4VP", "dash", "mmp4", "MSNV", "NDAS", "XAVC",
    )

    private fun image(mime: String, extension: String) = MediaType(mime, MediaKind.IMAGE, extension)

    private fun video(mime: String, extension: String) = MediaType(mime, MediaKind.VIDEO, extension)

    private fun ByteArray.startsWith(vararg bytes: Int): Boolean =
        size >= bytes.size && bytes.indices.all { (this[it].toInt() and 0xFF) == bytes[it] }

    private fun ByteArray.ascii(offset: Int, length: Int): String =
        if (offset + length > size) "" else String(this, offset, length, Charsets.US_ASCII)
}
