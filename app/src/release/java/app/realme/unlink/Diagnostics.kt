package app.realme.unlink

import android.content.Intent

/** Release builds contain no diagnostic implementation or hardware probes. */
object Diagnostics {
    fun isDiagnosticIntent(intent: Intent?): Boolean = false

    fun handleIntent(activity: MainActivity, intent: Intent?) = Unit
}
