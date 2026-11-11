package com.saitkaplan.downowder

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build

/**
 * The content URIs a share intent carries, in order and without duplicates.
 *
 * Only `content://` URIs are accepted. A `file://` URI would let any app ask
 * this one to copy a file from its own private storage into Download/.
 */
internal fun Intent.sharedUris(): List<Uri> {
    val uris = LinkedHashSet<Uri>()

    // When the system starts this activity it copies EXTRA_STREAM into
    // ClipData, so ClipData covers SEND and SEND_MULTIPLE on every supported
    // version without the deprecated untyped extra getters.
    clipData?.let { clip ->
        for (index in 0 until clip.itemCount) {
            clip.getItemAt(index).uri?.let(uris::add)
        }
    }

    if (uris.isEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        when (action) {
            Intent.ACTION_SEND ->
                getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)?.let(uris::add)

            Intent.ACTION_SEND_MULTIPLE ->
                getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    ?.let(uris::addAll)
        }
    }

    return uris.filter { it.scheme == ContentResolver.SCHEME_CONTENT }
}
