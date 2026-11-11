package com.saitkaplan.downowder

import android.app.Activity
import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uris = intent.sharedUris()
        if (uris.isEmpty()) {
            showToast(getString(R.string.error_nothing_shared))
            finish()
            return
        }

        val saver = MediaSaver(contentResolver)
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
}
