package app.realme.unlink

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import app.realme.unlink.ui.UnlinkApp
import app.realme.unlink.ui.UnlinkTheme
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var controller: HeadsetController

    private var connectionState by mutableStateOf<ConnectionState>(ConnectionState.Checking)
    private var busy by mutableStateOf(false)
    private var snackbarMessage by mutableStateOf<String?>(null)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.all { it }) {
            connectionState = ConnectionState.Connecting
            controller.onForeground()
        } else {
            connectionState = ConnectionState.PermissionNeeded
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        controller = HeadsetController(applicationContext)
        controller.setListener { event ->
            runOnUiThread {
                when (event) {
                    is HeadsetController.UiEvent.State -> connectionState = event.state
                    is HeadsetController.UiEvent.Message -> snackbarMessage = event.text
                    is HeadsetController.UiEvent.Busy -> busy = event.busy
                }
            }
        }

        setContent {
            UnlinkTheme {
                UnlinkApp(
                    state = connectionState,
                    busy = busy,
                    snackbarMessage = snackbarMessage,
                    onSnackbarConsumed = { snackbarMessage = null },
                    onRefresh = {
                        if (!hasBluetoothPermission()) {
                            requestBluetoothPermission()
                        } else {
                            controller.refresh()
                        }
                    },
                    onRequestPermission = { requestBluetoothPermission() },
                    onNoiseValue = controller::setNoiseValue,
                    onEqId = controller::setEqId,
                    onApplyCustomEq = controller::applyCustomEq,
                    onBassBand = controller::setBassBand,
                    onSwitch = controller::setSwitch,
                    onButtons = controller::setButtonMappings,
                    onFindMode = controller::setFindMode,
                )
            }
        }

        if (BuildConfig.ENABLE_DIAGNOSTICS &&
            intent?.getBooleanExtra(EXTRA_RUN_EVIDENCE_MATRIX, false) == true
        ) {
            runEvidenceMatrixAutomation()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "onResume")
        if (!hasBluetoothPermission()) {
            connectionState = ConnectionState.PermissionNeeded
            return
        }
        // Enter connecting immediately so the UI never sits on "Checking" waiting for a tap.
        if (connectionState !is ConnectionState.Connected) {
            connectionState = ConnectionState.Connecting
        }
        controller.onForeground()
    }

    override fun onPause() {
        Log.i(TAG, "onPause")
        controller.onBackground()
        super.onPause()
    }

    override fun onDestroy() {
        controller.close()
        super.onDestroy()
    }

    private fun hasBluetoothPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            connectionState = ConnectionState.Connecting
            controller.onForeground()
            return
        }
        permissionLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun runEvidenceMatrixAutomation() {
        if (!hasBluetoothPermission()) {
            requestBluetoothPermission()
            return
        }
        val executor = Executors.newSingleThreadExecutor()
        executor.execute {
            try {
                val probe = HeadsetEvidenceProbe(applicationContext)
                val result = probe.run()
                Log.i(
                    EVIDENCE_TAG,
                    "Evidence matrix finished device=${result.deviceName} " +
                        "findings=${result.findings.size}",
                )
                result.findings.forEach { Log.i(EVIDENCE_TAG, "FINDING: $it") }
            } catch (error: Exception) {
                Log.e(EVIDENCE_TAG, "Evidence matrix failed", error)
            } finally {
                executor.shutdown()
            }
        }
    }

    companion object {
        const val EXTRA_RUN_EVIDENCE_MATRIX = "run_evidence_matrix"
        private const val EVIDENCE_TAG = "UnlinkEvidence"
        private const val TAG = "UnlinkMain"
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
        )
    }
}
