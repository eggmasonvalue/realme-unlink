package app.realme.unlink

import android.content.Intent
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Debug-only ADB diagnostics. This source set is absent from release artifacts. */
object Diagnostics {
    fun isDiagnosticIntent(intent: Intent?): Boolean = intent != null &&
        (intent.getBooleanExtra(EXTRA_READ_BUTTON_MAPPINGS, false) ||
            intent.getBooleanExtra(EXTRA_RESTORE_BUTTON_BASELINE, false) ||
            intent.getBooleanExtra(EXTRA_RUN_BUTTON_TEST, false) ||
            intent.getBooleanExtra(EXTRA_RUN_EVIDENCE_MATRIX, false))

    fun handleIntent(activity: MainActivity, intent: Intent?) {
        if (!isDiagnosticIntent(intent)) return
        if (!activity.hasBluetoothPermission()) {
            activity.requestBluetoothPermission()
            return
        }

        if (!running.compareAndSet(false, true)) {
            Log.w(TAG, "Ignoring diagnostic request while another probe is running")
            return
        }
        val executor = Executors.newSingleThreadExecutor()
        executor.execute {
            try {
                when {
                    intent?.getBooleanExtra(EXTRA_READ_BUTTON_MAPPINGS, false) == true -> {
                        val result = ReadOnlyButtonMappingProbe(activity.applicationContext).run()
                        Log.i(
                            BUTTON_READ_TAG,
                            "device=${result.deviceName} " +
                                "payload=${HeadsetProtocol.hex(result.rawPayload)}",
                        )
                        result.mappings.forEach { mapping ->
                            Log.i(
                                BUTTON_READ_TAG,
                                "mapping=d${mapping.deviceType}/b${mapping.button}/" +
                                    "a${mapping.action}/f${mapping.function}",
                            )
                        }
                        result.trace.forEach { Log.d(BUTTON_READ_TAG, it) }
                    }

                    intent?.getBooleanExtra(EXTRA_RESTORE_BUTTON_BASELINE, false) == true -> {
                        val result = ButtonMappingPersistenceProbe(activity.applicationContext)
                            .restoreConfirmedBaseline()
                        Log.i(BUTTON_TEST_TAG, result)
                    }

                    intent?.getBooleanExtra(EXTRA_RUN_BUTTON_TEST, false) == true -> {
                        val findings = ButtonMappingPersistenceProbe(activity.applicationContext)
                            .validateConservativeRemaps()
                        Log.i(
                            BUTTON_TEST_TAG,
                            "Conservative button test finished findings=${findings.size}",
                        )
                        findings.forEach { Log.i(BUTTON_TEST_TAG, "RESULT: $it") }
                    }

                    intent?.getBooleanExtra(EXTRA_RUN_EVIDENCE_MATRIX, false) == true -> {
                        val result = HeadsetEvidenceProbe(activity.applicationContext).run()
                        Log.i(
                            EVIDENCE_TAG,
                            "Evidence matrix finished device=${result.deviceName} " +
                                "findings=${result.findings.size}",
                        )
                        result.findings.forEach { Log.i(EVIDENCE_TAG, "FINDING: $it") }
                    }
                }
            } catch (error: Exception) {
                Log.e(TAG, "Diagnostic failed", error)
            } finally {
                running.set(false)
                executor.shutdown()
                activity.runOnUiThread { activity.finishAndRemoveTask() }
            }
        }
    }

    private val running = AtomicBoolean(false)

    private const val EXTRA_READ_BUTTON_MAPPINGS = "read_button_mappings"
    private const val EXTRA_RUN_EVIDENCE_MATRIX = "run_evidence_matrix"
    private const val EXTRA_RUN_BUTTON_TEST = "run_button_test"
    private const val EXTRA_RESTORE_BUTTON_BASELINE = "restore_button_baseline"
    private const val EVIDENCE_TAG = "UnlinkEvidence"
    private const val BUTTON_READ_TAG = "UnlinkButtonRead"
    private const val BUTTON_TEST_TAG = "UnlinkButtonTest"
    private const val TAG = "UnlinkDiagnostics"
}
