package app.realme.unlink

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
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
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import app.realme.unlink.ui.UnlinkApp
import app.realme.unlink.ui.UnlinkTheme

class MainActivity : ComponentActivity() {
    private lateinit var controller: HeadsetController

    private var connectionState by mutableStateOf<ConnectionState>(ConnectionState.Checking)
    private var busy by mutableStateOf(false)
    private var snackbarMessage by mutableStateOf<String?>(null)
    private var launcherIconEnabled by mutableStateOf(false)
    private var diagnosticMode = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.all { it }) {
            if (diagnosticMode) {
                Diagnostics.handleIntent(this, intent)
            } else {
                connectionState = ConnectionState.Connecting
                controller.onForeground()
            }
        } else {
            connectionState = ConnectionState.PermissionNeeded
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        diagnosticMode = Diagnostics.isDiagnosticIntent(intent)
        launcherIconEnabled = LauncherIconToggle.isEnabled(this)
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

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                launcherIconEnabled = LauncherIconToggle.isEnabled(this@MainActivity)
                if (diagnosticMode) {
                    Log.i(TAG, "onStart -> diagnostic owns headset transport")
                    return
                }
                Log.i(TAG, "onStart -> connecting headset lifecycle")
                if (!hasBluetoothPermission()) {
                    connectionState = ConnectionState.PermissionNeeded
                    return
                }
                if (connectionState !is ConnectionState.Connected) {
                    connectionState = ConnectionState.Connecting
                }
                controller.onForeground()
            }

            override fun onStop(owner: LifecycleOwner) {
                Log.i(TAG, "onStop -> disconnecting RFCOMM cleanly")
                controller.onBackground()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                controller.close()
            }
        })

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
                    onButtons = controller::setButtonMapping,
                    onFindMode = controller::setFindMode,
                    onVolumeEnhancer = controller::setHighVolume,
                    onNoiseCycle = controller::setNoiseCycle,
                    launcherIconEnabled = launcherIconEnabled,
                    onToggleLauncherIcon = { enabled ->
                        LauncherIconToggle.setEnabled(this, enabled)
                        launcherIconEnabled = LauncherIconToggle.isEnabled(this)
                        snackbarMessage = if (launcherIconEnabled) {
                            "App icon added to app drawer"
                        } else {
                            "App icon hidden from app drawer"
                        }
                    },
                )
            }
        }

        Diagnostics.handleIntent(this, intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val wasDiagnostic = diagnosticMode
        diagnosticMode = Diagnostics.isDiagnosticIntent(intent)
        when {
            diagnosticMode -> {
                controller.onBackground()
                Diagnostics.handleIntent(this, intent)
            }
            wasDiagnostic && hasBluetoothPermission() -> controller.onForeground()
        }
    }

    internal fun hasBluetoothPermission(): Boolean = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    internal fun requestBluetoothPermission() {
        permissionLauncher.launch(REQUIRED_PERMISSIONS)
    }

    companion object {
        private const val TAG = "UnlinkMain"

        @SuppressLint("InlinedApi")
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
        )
    }
}
