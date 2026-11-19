package com.saitkaplan.downowder

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The share target: invisible, saves everything it was given, shows one
 * toast and closes. Plain [Activity] on purpose, so the app needs no AndroidX.
 */
class SaveActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var uris: List<Uri> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        uris = intent.sharedUris()
        if (uris.isEmpty()) {
            showToast(getString(R.string.error_nothing_shared))
            finish()
            return
        }

        // A photo from the gallery keeps its location only with this
        // permission. It is asked for once, and only when such a photo is
        // shared; denied, the files are still saved, without location.
        val fromGallery = uris.any { it.authority == MediaStore.AUTHORITY }
        if (fromGallery && !canReadLocation()) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_MEDIA_LOCATION), LOCATION_REQUEST)
        } else {
            startSaving()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_REQUEST) startSaving()
    }

    private fun canReadLocation(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun startSaving() {
        val saver = MediaSaver(contentResolver, canReadLocation())
        scope.launch {
            val results = withContext(Dispatchers.IO) { uris.map { saver.save(it) } }
            showToast(summary(results))
            // Closing only now keeps the read grant on the shared files alive
            // for the whole copy.
            finish()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun summary(results: List<SaveResult>): String {
        val saved = results.count { it == SaveResult.SAVED }
        val unsupported = results.count { it == SaveResult.UNSUPPORTED }
        val notSaved = results.size - saved

        if (results.size == 1) {
            return getString(
                when (results.first()) {
                    SaveResult.SAVED -> R.string.saved_one
                    SaveResult.UNSUPPORTED -> R.string.error_unsupported
                    SaveResult.FAILED -> R.string.error_failed
                },
            )
        }
        return when {
            notSaved == 0 -> resources.getQuantityString(R.plurals.saved_many, saved, saved)
            saved > 0 -> resources.getQuantityString(R.plurals.saved_partial, saved, saved, notSaved)
            unsupported == results.size ->
                resources.getQuantityString(R.plurals.error_unsupported_many, unsupported, unsupported)

            else -> resources.getQuantityString(R.plurals.error_failed_many, notSaved, notSaved)
        }
    }

    // The application context keeps the toast on screen after finish().
    private fun showToast(text: String) {
        Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val LOCATION_REQUEST = 1
    }
}
