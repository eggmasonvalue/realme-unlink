@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.realme.unlink.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BluetoothDisabled
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.NoiseAware
import androidx.compose.material.icons.outlined.NoiseControlOff
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RingVolume
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.SpatialAudio
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.realme.unlink.ConnectionState
import app.realme.unlink.EqSuggestions
import app.realme.unlink.HeadsetCapabilityDiscovery
import app.realme.unlink.HeadsetProtocol
import app.realme.unlink.HeadsetSemantics
import app.realme.unlink.HeadsetSnapshot
import app.realme.unlink.R
import app.realme.unlink.SwitchCatalog

private enum class SheetKind {
    None,
    Noise,
    Equalizer,
    DynamicBass,
    ButtonControls,
}

private enum class NoiseMode(val value: Int, val label: String) {
    Anc(8, "ANC"),
    Off(1, "Off"),
    Ambient(2, "Transparency"),
}

private data class ButtonChoice(
    val function: Int,
    val label: String,
    val icon: ImageVector,
    val supportedByCurrentController: Boolean,
)

private val buttonChoices = listOf(
    ButtonChoice(1, "Play / pause", Icons.Outlined.PlayArrow, supportedByCurrentController = true),
    ButtonChoice(6, "Next track", Icons.Outlined.SkipNext, supportedByCurrentController = true),
    ButtonChoice(5, "Previous track", Icons.Outlined.SkipPrevious, supportedByCurrentController = true),
    ButtonChoice(4, "Voice assistant", Icons.Outlined.Mic, supportedByCurrentController = true),
    ButtonChoice(17, "Game mode", Icons.Outlined.SportsEsports, supportedByCurrentController = true),
    ButtonChoice(0, "None", Icons.Outlined.Block, supportedByCurrentController = true),
)

@Composable
fun UnlinkApp(
    state: ConnectionState,
    busy: Boolean,
    snackbarMessage: String?,
    onSnackbarConsumed: () -> Unit,
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit,
    onNoiseValue: (Int) -> Unit,
    onEqId: (Int) -> Unit,
    onApplyCustomEq: (HeadsetCapabilityDiscovery.EqProfile) -> Unit,
    onBassBand: (HeadsetCapabilityDiscovery.BassEngineValue) -> Unit,
    onSwitch: (Int, Boolean) -> Unit,
    onButtons: (HeadsetCapabilityDiscovery.KeyMapping) -> Unit,
    onFindMode: (Boolean) -> Unit,
    onVolumeEnhancer: (Boolean) -> Unit = {},
    onNoiseCycle: (Int) -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var sheet by remember { mutableStateOf(SheetKind.None) }
    var selectedButtonMapping by remember {
        mutableStateOf<HeadsetCapabilityDiscovery.KeyMapping?>(null)
    }
    val modalSheetState = rememberBottomSheetState(
        SheetValue.Hidden,
        setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val connected = (state as? ConnectionState.Connected)?.snapshot

    LaunchedEffect(snackbarMessage) {
        val message = snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onSnackbarConsumed()
    }

    LaunchedEffect(connected, sheet) {
        val canKeepSheetOpen = when (sheet) {
            SheetKind.None -> true
            SheetKind.Noise -> connected?.noise != null
            SheetKind.Equalizer -> connected?.hasEqualizer == true
            SheetKind.DynamicBass -> connected?.hasDynamicBass == true
            SheetKind.ButtonControls -> connected?.buttonMappings?.isNotEmpty() == true
        }
        if (!canKeepSheetOpen) {
            sheet = SheetKind.None
            selectedButtonMapping = null
        }
    }

    Scaffold(
        modifier = Modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !busy) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh device data")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val current = state) {
                ConnectionState.Checking,
                ConnectionState.Connecting,
                -> LoadingPane(
                    title = "Connecting",
                    message = "Separate the magnetic clasp to power on the neckband.",
                )

                ConnectionState.BluetoothOff -> StatusPane(
                    icon = Icons.Outlined.BluetoothDisabled,
                    title = "Bluetooth is off",
                    message = "Turn on Bluetooth to control your neckband.",
                    primary = "Open Bluetooth settings",
                    openBluetoothSettings = true,
                )

                ConnectionState.PermissionNeeded -> StatusPane(
                    icon = Icons.Outlined.Bluetooth,
                    title = "Nearby devices permission",
                    message = "${stringResource(R.string.app_name)} needs Bluetooth access to talk to your neckband.",
                    primary = "Grant permission",
                    onPrimary = onRequestPermission,
                )

                ConnectionState.NotBonded -> StatusPane(
                    icon = Icons.Outlined.Headphones,
                    title = "Neckband not paired",
                    message = "Pair realme Buds Wireless 5 ANC in system Bluetooth settings, then return here.",
                    primary = "Open Bluetooth settings",
                    openBluetoothSettings = true,
                )

                is ConnectionState.Unavailable -> StatusPane(
                    icon = Icons.Outlined.Headphones,
                    title = current.title,
                    message = current.message,
                    primary = if (current.canRetry) "Try again" else null,
                    onPrimary = onRefresh,
                )

                is ConnectionState.Connected -> ConnectedDashboard(
                    snapshot = current.snapshot,
                    busy = busy,
                    onNoiseValue = onNoiseValue,
                    onEqId = onEqId,
                    onBassBand = onBassBand,
                    onSwitch = onSwitch,
                    onButtons = onButtons,
                    onFindMode = onFindMode,
                    onVolumeEnhancer = onVolumeEnhancer,
                    onOpenNoise = { sheet = SheetKind.Noise },
                    onOpenEqualizer = { sheet = SheetKind.Equalizer },
                    onOpenDynamicBass = { sheet = SheetKind.DynamicBass },
                    onOpenButtons = {
                        selectedButtonMapping = null
                        sheet = SheetKind.ButtonControls
                    },
                )
            }

            if (busy && state is ConnectionState.Connected) {
                LinearProgressIndicator(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                )
            }
        }
    }

    if (sheet != SheetKind.None && connected != null) {
        ModalBottomSheet(
            onDismissRequest = {
                sheet = SheetKind.None
                selectedButtonMapping = null
            },
            sheetState = modalSheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            when (sheet) {
                SheetKind.Noise -> NoiseAdjustmentSheet(
                    snapshot = connected,
                    enabled = !busy,
                    onNoiseValue = onNoiseValue,
                    onToggle = onSwitch,
                    onNoiseCycle = onNoiseCycle,
                )

                SheetKind.Equalizer -> EqualizerSheet(
                    snapshot = connected,
                    enabled = !busy,
                    onSelect = onEqId,
                    onApplyCustomEq = onApplyCustomEq,
                )

                SheetKind.DynamicBass -> DynamicBassSheet(
                    snapshot = connected,
                    enabled = !busy,
                    onBassBand = onBassBand,
                    onToggle = onSwitch,
                )

                SheetKind.ButtonControls -> ButtonControlsSheet(
                    snapshot = connected,
                    selectedMapping = selectedButtonMapping,
                    enabled = !busy,
                    onSelectMapping = { selectedButtonMapping = it },
                    onBack = { selectedButtonMapping = null },
                    onUpdate = onButtons,
                )

                SheetKind.None -> Unit
            }
        }
    }
}

private val HeadsetSnapshot.hasEqualizer: Boolean
    get() = currentEqId != null || eqProfiles.isNotEmpty()

private val HeadsetSnapshot.hasDynamicBass: Boolean
    get() = switches.any { it.id == 29 } ||
        (bassBands.isNotEmpty() &&
            capabilities.supportsCommand(HeadsetProtocol.SET_BASS_ENGINE_COMMAND) &&
            !readFailed(HeadsetProtocol.MULTI_BASS_ENGINE_COMMAND))

@Composable
private fun DeviceHeader(snapshot: HeadsetSnapshot) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = MaterialShapes.Cookie12Sided.toShape(),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.Headphones,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    snapshot.deviceName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Connected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            snapshot.primaryBatteryPercent?.let { percentage ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (snapshot.anyCharging) {
                            Icon(
                                Icons.Outlined.Bolt,
                                contentDescription = "Charging",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            "$percentage%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingPane(title: String, message: String) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(Modifier.size(40.dp))
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun StatusPane(
    icon: ImageVector,
    title: String,
    message: String,
    primary: String? = null,
    onPrimary: (() -> Unit)? = null,
    openBluetoothSettings: Boolean = false,
) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.size(72.dp),
            shape = MaterialShapes.Cookie12Sided.toShape(),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (primary != null) {
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    if (openBluetoothSettings) {
                        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    } else {
                        onPrimary?.invoke()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(primary)
            }
        }
    }
}

@Composable
private fun ConnectedDashboard(
    snapshot: HeadsetSnapshot,
    busy: Boolean,
    onNoiseValue: (Int) -> Unit,
    onEqId: (Int) -> Unit,
    onBassBand: (HeadsetCapabilityDiscovery.BassEngineValue) -> Unit,
    onSwitch: (Int, Boolean) -> Unit,
    onButtons: (HeadsetCapabilityDiscovery.KeyMapping) -> Unit,
    onFindMode: (Boolean) -> Unit,
    onVolumeEnhancer: (Boolean) -> Unit,
    onOpenNoise: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenDynamicBass: () -> Unit,
    onOpenButtons: () -> Unit,
) {
    val gameSwitch = snapshot.switches.firstOrNull { it.id == 6 || it.id == 8 }
    val dynamicBass = snapshot.switches.firstOrNull { it.id == 29 }
    val dualSwitch = snapshot.switches.firstOrNull { it.id == 17 }
    val autoAnswer = snapshot.switches.firstOrNull {
        it.label.equals("Auto answer", ignoreCase = true)
    }
    val writableBassBands = snapshot.bassBands.takeIf {
        snapshot.capabilities.supportsCommand(HeadsetProtocol.SET_BASS_ENGINE_COMMAND) &&
            !snapshot.readFailed(HeadsetProtocol.MULTI_BASS_ENGINE_COMMAND)
    }.orEmpty()
    val hasSettings = dualSwitch != null ||
        gameSwitch != null ||
        snapshot.capabilities.supportsCommand(HeadsetProtocol.SET_FIND_MODE_COMMAND) ||
        snapshot.buttonMappings.isNotEmpty() ||
        autoAnswer != null

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            DeviceHeader(snapshot = snapshot)
        }

        if (snapshot.readFailures.isNotEmpty()) {
            item { PartialDataNotice() }
        }

        snapshot.noise?.let { noise ->
            item {
                NoiseControlCard(
                    noiseValue = noise.value,
                    enabled = !busy,
                    onSelect = onNoiseValue,
                    onAdjust = onOpenNoise,
                )
            }
        }

        item {
            SoundSection(
                snapshot = snapshot,
                busy = busy,
                dynamicBass = dynamicBass,
                writableBassBands = writableBassBands,
                onEqId = onEqId,
                onSwitch = onSwitch,
                onVolumeEnhancer = onVolumeEnhancer,
                onOpenEqualizer = onOpenEqualizer,
                onOpenDynamicBass = onOpenDynamicBass,
            )
        }

        if (hasSettings) {
            item {
                SettingsGroup(
                    snapshot = snapshot,
                    busy = busy,
                    dualSwitch = dualSwitch,
                    gameSwitch = gameSwitch,
                    autoAnswer = autoAnswer,
                    onSwitch = onSwitch,
                    onFindMode = onFindMode,
                    onOpenButtons = onOpenButtons,
                )
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun PartialDataNotice() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "Some device data is unavailable. Refresh to try again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun NoiseControlCard(
    noiseValue: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    onAdjust: () -> Unit,
) {
    val selectedMode = when (noiseValue) {
        1 -> NoiseMode.Off
        2 -> NoiseMode.Ambient
        4, 8, 16, 32 -> NoiseMode.Anc
        else -> null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(R.drawable.ic_mode_anc),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    "Noise control",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = onAdjust,
                    enabled = enabled,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp),
                ) {
                    Icon(
                        Icons.Outlined.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Adjust", style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NoiseMode.values().forEach { mode ->
                    val selected = selectedMode == mode
                    NoiseModeTile(
                        mode = mode,
                        selected = selected,
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val target = when (mode) {
                                NoiseMode.Anc -> if (noiseValue in ANC_VALUES) noiseValue else NoiseMode.Anc.value
                                NoiseMode.Ambient -> NoiseMode.Ambient.value
                                NoiseMode.Off -> NoiseMode.Off.value
                            }
                            if (target != noiseValue) onSelect(target)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun NoiseModeTile(
    mode: NoiseMode,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val iconPainter: Painter = when (mode) {
        NoiseMode.Anc -> painterResource(R.drawable.ic_mode_anc)
        NoiseMode.Off -> rememberVectorPainter(Icons.Outlined.NoiseControlOff)
        NoiseMode.Ambient -> rememberVectorPainter(Icons.Outlined.NoiseAware)
    }

    Surface(
        modifier = modifier
            .heightIn(min = 64.dp)
            .semantics {
                role = Role.Button
                if (selected) stateDescription = "Selected"
            },
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = iconPainter,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                mode.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SoundSection(
    snapshot: HeadsetSnapshot,
    busy: Boolean,
    dynamicBass: SwitchCatalog.LogicalSwitch?,
    writableBassBands: List<HeadsetCapabilityDiscovery.BassEngineValue>,
    onEqId: (Int) -> Unit,
    onSwitch: (Int, Boolean) -> Unit,
    onVolumeEnhancer: (Boolean) -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenDynamicBass: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                var needsDivider = false

                if (snapshot.hasEqualizer) {
                    needsDivider = true
                    val eqSummary = when {
                        snapshot.currentEqId == null -> "Unknown"
                        snapshot.currentEqId in 0..3 -> snapshot.currentEqLabel
                        else -> {
                            val custom = snapshot.customEqProfile
                            val match = custom?.let { EqSuggestions.matchingSuggestion(it)?.name }
                            if (match != null) "Custom ($match)" else "Custom"
                        }
                    }
                    SummaryRow(
                        icon = Icons.Outlined.Equalizer,
                        title = "Equalizer",
                        summary = eqSummary,
                        enabled = !busy && snapshot.currentEqId != null &&
                            !snapshot.readFailed(HeadsetProtocol.EQ_STATUS_COMMAND),
                        onClick = onOpenEqualizer,
                    )
                }

                if (snapshot.hasDynamicBass) {
                    if (needsDivider) RowDivider()
                    needsDivider = true
                    SummaryRow(
                        icon = Icons.Outlined.Speaker,
                        title = "Dynamic bass",
                        summary = dynamicBassSummary(dynamicBass, writableBassBands),
                        enabled = !busy,
                        onClick = onOpenDynamicBass,
                    )
                }

                val spatialSwitch = snapshot.switches.firstOrNull { it.id == 27 }
                spatialSwitch?.let { sw ->
                    if (needsDivider) RowDivider()
                    needsDivider = true
                    SettingRow(
                        icon = Icons.Outlined.SpatialAudio,
                        title = "Spatial audio",
                        summary = if (sw.enabled) "On" else "Off",
                        enabled = !busy,
                        onClick = { onSwitch(sw.id, !sw.enabled) },
                    ) {
                        Switch(
                            checked = sw.enabled,
                            onCheckedChange = { onSwitch(sw.id, it) },
                            enabled = !busy,
                        )
                    }
                }

                snapshot.highVolumeEnabled?.let { enabled ->
                    if (needsDivider) RowDivider()
                    SettingRow(
                        icon = Icons.AutoMirrored.Outlined.VolumeUp,
                        title = "Volume enhancer",
                        summary = if (enabled) "On" else "Off",
                        enabled = !busy && snapshot.capabilities.supportsCommand(HeadsetProtocol.SET_HIGH_VOLUME_COMMAND),
                        onClick = { onVolumeEnhancer(!enabled) },
                    ) {
                        Switch(
                            checked = enabled,
                            onCheckedChange = onVolumeEnhancer,
                            enabled = !busy && snapshot.capabilities.supportsCommand(HeadsetProtocol.SET_HIGH_VOLUME_COMMAND),
                        )
                    }
                }
            }
        }
    }

@Composable
private fun SummaryRow(
    icon: ImageVector,
    title: String,
    summary: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { if (!enabled) stateDescription = "Unavailable" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.secondary,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = "Open $title",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsGroup(
    snapshot: HeadsetSnapshot,
    busy: Boolean,
    dualSwitch: SwitchCatalog.LogicalSwitch?,
    gameSwitch: SwitchCatalog.LogicalSwitch?,
    autoAnswer: SwitchCatalog.LogicalSwitch?,
    onSwitch: (Int, Boolean) -> Unit,
    onFindMode: (Boolean) -> Unit,
    onOpenButtons: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            dualSwitch?.let { sw ->
                SettingRow(
                    icon = Icons.Outlined.Devices,
                    title = "Dual-device",
                    summary = if (sw.enabled) "On" else "Off",
                    enabled = !busy,
                    onClick = { onSwitch(sw.id, !sw.enabled) },
                ) {
                    Switch(
                        checked = sw.enabled,
                        onCheckedChange = { onSwitch(sw.id, it) },
                        enabled = !busy,
                    )
                }
                if (sw.enabled) {
                    MultipointDetails(
                        entries = snapshot.multipoint,
                        available = !snapshot.readFailed(HeadsetProtocol.MULTI_CONNECT_COMMAND),
                    )
                }
            }

            gameSwitch?.let { sw ->
                if (dualSwitch != null) RowDivider()
                SettingRow(
                    icon = Icons.Outlined.SportsEsports,
                    title = "Game mode",
                    summary = if (sw.enabled) "On" else "Off",
                    enabled = !busy,
                    onClick = { onSwitch(sw.id, !sw.enabled) },
                ) {
                    Switch(
                        checked = sw.enabled,
                        onCheckedChange = { onSwitch(sw.id, it) },
                        enabled = !busy,
                    )
                }
            }

            if (snapshot.capabilities.supportsCommand(HeadsetProtocol.SET_FIND_MODE_COMMAND)) {
                if (dualSwitch != null || gameSwitch != null) RowDivider()
                SettingRow(
                    icon = Icons.Outlined.RingVolume,
                    title = "Find neckband",
                    summary = if (snapshot.findModeActive) "Playing a tone" else "Ready",
                    enabled = !busy,
                    onClick = { onFindMode(!snapshot.findModeActive) },
                ) {
                    TextButton(
                        onClick = { onFindMode(!snapshot.findModeActive) },
                        enabled = !busy,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(if (snapshot.findModeActive) "Stop" else "Find")
                    }
                }
            }

            if (snapshot.buttonMappings.isNotEmpty()) {
                if (dualSwitch != null || gameSwitch != null ||
                    snapshot.capabilities.supportsCommand(HeadsetProtocol.SET_FIND_MODE_COMMAND)
                ) {
                    RowDivider()
                }
                SettingRow(
                    icon = Icons.Outlined.TouchApp,
                    title = "Button controls",
                    summary = buttonSummary(snapshot.buttonMappings),
                    enabled = !busy,
                    onClick = onOpenButtons,
                ) {
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = "Open button controls",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            autoAnswer?.let { sw ->
                if (dualSwitch != null || gameSwitch != null ||
                    snapshot.capabilities.supportsCommand(HeadsetProtocol.SET_FIND_MODE_COMMAND) ||
                    snapshot.buttonMappings.isNotEmpty()
                ) {
                    RowDivider()
                }
                SettingRow(
                    icon = Icons.Outlined.Headphones,
                    title = "Auto answer",
                    summary = if (sw.enabled) "On" else "Off",
                    enabled = !busy,
                    onClick = { onSwitch(sw.id, !sw.enabled) },
                ) {
                    Switch(
                        checked = sw.enabled,
                        onCheckedChange = { onSwitch(sw.id, it) },
                        enabled = !busy,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    summary: String,
    enabled: Boolean,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { if (!enabled) stateDescription = "Unavailable" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.secondary,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

@Composable
private fun MultipointDetails(
    entries: List<HeadsetCapabilityDiscovery.MultipointEntry>,
    available: Boolean,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 40.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when {
            !available -> Text(
                "Connected-device status unavailable",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            entries.isEmpty() -> Text(
                "No device status reported",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> entries.forEach { entry ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Bluetooth,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        entry.name.ifBlank { "Device" },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "· ${HeadsetSemantics.multipointStateLabel(entry.state)}" +
                            if (entry.flags == 1) " · This phone" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun buttonSummary(mappings: List<HeadsetCapabilityDiscovery.KeyMapping>): String {
    val editableSlots = mappings
        .filter { it.button == 1 && it.action in 1..4 }
        .distinctBy { it.action }
        .size
    return if (editableSlots > 0) "$editableSlots gestures" else "Available"
}

private fun dynamicBassSummary(
    dynamicBass: SwitchCatalog.LogicalSwitch?,
    bands: List<HeadsetCapabilityDiscovery.BassEngineValue>,
): String = when {
    dynamicBass != null -> if (dynamicBass.enabled) "Dynamic bass on" else "Dynamic bass off"
    bands.isNotEmpty() -> "${bands.size}-band tuning"
    else -> "Available"
}

@Composable
private fun RowDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 40.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    )
}

@Composable
private fun NoiseAdjustmentSheet(
    snapshot: HeadsetSnapshot,
    enabled: Boolean,
    onNoiseValue: (Int) -> Unit,
    onToggle: (Int, Boolean) -> Unit,
    onNoiseCycle: (Int) -> Unit = {},
) {
    val noiseValue = snapshot.noise?.value ?: return
    val windNoise = snapshot.switches.firstOrNull { it.id == 26 }
    val cycleValue = snapshot.noiseCycle?.value
    SheetColumn {
        SheetTitle(title = "Noise control", subtitle = "ANC tuning and button cycling")

        Text(
            "ANC level",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
        )
        listOf(
            32 to "Smart",
            8 to "Max",
            16 to "Moderate",
            4 to "Mild",
        ).forEach { (value, label) ->
            SelectionRow(
                title = label,
                selected = noiseValue == value,
                enabled = enabled,
                onClick = { if (noiseValue != value) onNoiseValue(value) },
            )
        }

        windNoise?.let { sw ->
            Spacer(Modifier.height(8.dp))
            RowDivider()
            Spacer(Modifier.height(8.dp))
            SettingRow(
                icon = Icons.Outlined.Air,
                title = "Smart de-wind",
                summary = if (sw.enabled) "On" else "Off",
                enabled = enabled,
                onClick = { onToggle(sw.id, !sw.enabled) },
            ) {
                Switch(
                    checked = sw.enabled,
                    onCheckedChange = { onToggle(sw.id, it) },
                    enabled = enabled,
                )
            }
        }

        if (cycleValue != null) {
            Spacer(Modifier.height(8.dp))
            RowDivider()
            Spacer(Modifier.height(12.dp))
            Text(
                "Button cycling",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Modes cycled by pressing the physical ANC button",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
            )

            val hasAnc = cycleValue in setOf(3, 9, 11)
            val hasTransparency = cycleValue in setOf(9, 10, 11)
            val hasOff = cycleValue in setOf(3, 10, 11)
            val totalActive = (if (hasAnc) 1 else 0) + (if (hasTransparency) 1 else 0) + (if (hasOff) 1 else 0)

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                FilterChip(
                    selected = hasAnc,
                    onClick = {
                        if (hasAnc && totalActive > 2) {
                            onNoiseCycle(10)
                        } else if (!hasAnc) {
                            onNoiseCycle(11)
                        }
                    },
                    enabled = enabled,
                    label = { Text("ANC") },
                    leadingIcon = if (hasAnc) {
                        { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
                FilterChip(
                    selected = hasTransparency,
                    onClick = {
                        if (hasTransparency && totalActive > 2) {
                            onNoiseCycle(3)
                        } else if (!hasTransparency) {
                            onNoiseCycle(11)
                        }
                    },
                    enabled = enabled,
                    label = { Text("Transparency") },
                    leadingIcon = if (hasTransparency) {
                        { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
                FilterChip(
                    selected = hasOff,
                    onClick = {
                        if (hasOff && totalActive > 2) {
                            onNoiseCycle(9)
                        } else if (!hasOff) {
                            onNoiseCycle(11)
                        }
                    },
                    enabled = enabled,
                    label = { Text("Off") },
                    leadingIcon = if (hasOff) {
                        { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (totalActive <= 2) "At least 2 modes must be selected" else "Cycles between selected modes",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SelectionRow(
    title: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    summary: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics {
                if (selected) stateDescription = "Selected"
                if (!enabled) stateDescription = "Unavailable"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            summary?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (selected) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SheetColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .heightIn(max = 680.dp)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
        content = content,
    )
}

@Composable
private fun SheetTitle(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        onBack?.let {
            IconButton(onClick = it, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.width(4.dp))
        }
        Column {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EqualizerSheet(
    snapshot: HeadsetSnapshot,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    onApplyCustomEq: (HeadsetCapabilityDiscovery.EqProfile) -> Unit,
) {
    val custom = snapshot.customEqProfile

    SheetColumn {
        SheetTitle(title = "Equalizer", subtitle = "Sound profiles & 6-band custom")

        Text(
            "Sound profiles",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
        )
        val officialPresets = listOf(
            0 to "Nature Balance",
            1 to "Bass Boost",
            2 to "Clear Vocals",
            3 to "Clear Bass",
        )
        officialPresets.forEach { (id, label) ->
            SelectionRow(
                title = label,
                selected = snapshot.currentEqId == id,
                enabled = enabled && snapshot.currentEqId != null &&
                    !snapshot.readFailed(HeadsetProtocol.EQ_STATUS_COMMAND),
                onClick = { onSelect(id) },
            )
        }

        if (snapshot.canEditCustomEq && custom != null) {
            val isCustomSelected = snapshot.currentEqId == custom.id || snapshot.currentEqId == 4
            val matchingSuggestion = EqSuggestions.matchingSuggestion(custom)?.name
            val customSummary = matchingSuggestion ?: "${custom.frequencies.size}-band custom"
            SelectionRow(
                title = "Custom",
                summary = customSummary,
                selected = isCustomSelected,
                enabled = enabled && !snapshot.readFailed(HeadsetProtocol.EQ_STATUS_COMMAND),
                onClick = { onSelect(custom.id) },
            )

            Spacer(Modifier.height(8.dp))
            RowDivider()
            Spacer(Modifier.height(12.dp))

            Text(
                "Custom 6-band EQ",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Tap a preset curve to preview dB values, or tune individual bands",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
            )

            var bands by remember(custom.id, custom.dbValues) {
                mutableStateOf(custom.dbValues)
            }
            var selectedPresetId by remember(custom.id, custom.dbValues) {
                mutableStateOf(EqSuggestions.matchingSuggestion(custom)?.id)
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EqSuggestions.suggestions.forEach { suggestion ->
                    FilterChip(
                        selected = selectedPresetId == suggestion.id,
                        onClick = {
                            selectedPresetId = suggestion.id
                            bands = EqSuggestions.apply(custom, suggestion).dbValues
                        },
                        enabled = enabled,
                        label = { Text(suggestion.name) },
                        modifier = Modifier.heightIn(min = 40.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            custom.frequencies.forEachIndexed { index, frequency ->
                val value = bands.getOrElse(index) { 0 }
                val dbText = if (value > 0) "+$value dB" else "$value dB"
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        formatFreq(frequency),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        dbText,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Slider(
                    value = value.toFloat(),
                    onValueChange = { newValue ->
                        selectedPresetId = null
                        val updated = bands.toMutableList()
                        if (index < updated.size) {
                            updated[index] = newValue.toInt()
                            bands = updated
                        }
                    },
                    valueRange = custom.minValue.toFloat()..custom.maxValue.toFloat(),
                    steps = (custom.maxValue - custom.minValue - 1).coerceAtLeast(0),
                    enabled = enabled,
                )
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    onApplyCustomEq(custom.copy(dbValues = bands, selected = true))
                },
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text("Apply custom EQ")
            }
        }
    }
}

@Composable
private fun DynamicBassSheet(
    snapshot: HeadsetSnapshot,
    enabled: Boolean,
    onBassBand: (HeadsetCapabilityDiscovery.BassEngineValue) -> Unit,
    onToggle: (Int, Boolean) -> Unit,
) {
    val dynamicBass = snapshot.switches.firstOrNull { it.id == 29 }
    val bands = snapshot.bassBands.takeIf {
        snapshot.capabilities.supportsCommand(HeadsetProtocol.SET_BASS_ENGINE_COMMAND) &&
            !snapshot.readFailed(HeadsetProtocol.MULTI_BASS_ENGINE_COMMAND)
    }.orEmpty()

    SheetColumn {
        SheetTitle(title = "Dynamic bass", subtitle = "Low-frequency enhancement")

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    "At lower volumes, bass naturally fades from human hearing, making music feel thin and flat. Dynamic bass automatically boosts low frequencies at quiet levels so your music stays rich and full, then balances them as volume rises to prevent distortion.\n\nUse the sliders to tune the baseline strength for each range to your taste.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        dynamicBass?.let { sw ->
            SettingRow(
                icon = Icons.Outlined.Speaker,
                title = "Dynamic bass",
                summary = if (sw.enabled) "On" else "Off",
                enabled = enabled,
                onClick = { onToggle(sw.id, !sw.enabled) },
            ) {
                Switch(
                    checked = sw.enabled,
                    onCheckedChange = { onToggle(sw.id, it) },
                    enabled = enabled,
                )
            }
            if (bands.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                RowDivider()
                Spacer(Modifier.height(8.dp))
            }
        }
        bands.forEach { band ->
            BassBandSlider(
                band = band,
                enabled = enabled,
                onChange = onBassBand,
            )
        }
    }
}

@Composable
private fun BassBandSlider(
    band: HeadsetCapabilityDiscovery.BassEngineValue,
    enabled: Boolean,
    onChange: (HeadsetCapabilityDiscovery.BassEngineValue) -> Unit,
) {
    var pendingValue by remember(band.type, band.currentValue) {
        mutableIntStateOf(band.currentValue)
    }
    val (label, description) = when (band.type) {
        1 -> "Low freq" to "Rumble & sub-bass"
        2 -> "Med freq" to "Punch & kick drums"
        3 -> "High freq" to "Bass guitar & lower warmth"
        else -> (band.type?.let(HeadsetSemantics::bassBandLabel) ?: "Bass") to null
    }
    val dbText = if (pendingValue > 0) "+$pendingValue dB" else "$pendingValue dB"

    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (description != null) {
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                dbText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Slider(
            value = pendingValue.toFloat(),
            onValueChange = { pendingValue = it.toInt() },
            onValueChangeFinished = {
                if (enabled && pendingValue != band.currentValue) {
                    onChange(band.copy(currentValue = pendingValue))
                }
            },
            valueRange = band.minValue.toFloat()..band.maxValue.toFloat(),
            steps = (band.maxValue - band.minValue - 1).coerceAtLeast(0),
            enabled = enabled,
        )
    }
}

@Composable
private fun ButtonControlsSheet(
    snapshot: HeadsetSnapshot,
    selectedMapping: HeadsetCapabilityDiscovery.KeyMapping?,
    enabled: Boolean,
    onSelectMapping: (HeadsetCapabilityDiscovery.KeyMapping) -> Unit,
    onBack: () -> Unit,
    onUpdate: (HeadsetCapabilityDiscovery.KeyMapping) -> Unit,
) {
    val liveSelection = selectedMapping?.let { selected ->
        snapshot.buttonMappings.firstOrNull {
            it.deviceType == selected.deviceType &&
                it.button == selected.button &&
                it.action == selected.action
        } ?: selected
    }

    if (liveSelection != null) {
        ButtonFunctionPicker(
            mapping = liveSelection,
            enabled = enabled,
            onBack = onBack,
            onSelect = { choice ->
                onUpdate(liveSelection.copy(function = choice.function))
                onBack()
            },
        )
        return
    }

    // Only confirmed MFB slots are customizable; ANC cycling has its own controls in Noise adjustment.
    val mappings = snapshot.buttonMappings
        .filter { it.button == 1 && it.action in 1..4 }
        .distinctBy { it.action }
        .sortedBy { it.action }

    SheetColumn {
        SheetTitle(title = "Button controls", subtitle = "Multifunction button")
        Text(
            "Choose a gesture to change its function.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        mappings.forEach { mapping ->
            ButtonAssignmentRow(
                mapping = mapping,
                enabled = enabled,
                onClick = { onSelectMapping(mapping) },
            )
        }
    }
}

@Composable
private fun ButtonAssignmentRow(
    mapping: HeadsetCapabilityDiscovery.KeyMapping,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { if (!enabled) stateDescription = "Unavailable" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                buttonAssignmentLabel(mapping),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                buttonFunctionLabel(mapping.function),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = "Choose assignment",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ButtonFunctionPicker(
    mapping: HeadsetCapabilityDiscovery.KeyMapping,
    enabled: Boolean,
    onBack: () -> Unit,
    onSelect: (ButtonChoice) -> Unit,
) {
    SheetColumn {
        SheetTitle(
            title = "Button controls",
            subtitle = HeadsetSemantics.buttonActionLabel(mapping.action),
            onBack = onBack,
        )
        buttonChoices.forEach { choice ->
            val selected = mapping.function == choice.function
            SelectionChoiceRow(
                choice = choice,
                selected = selected,
                enabled = enabled && choice.supportedByCurrentController,
                onClick = { onSelect(choice) },
            )
        }
    }
}

@Composable
private fun SelectionChoiceRow(
    choice: ButtonChoice,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics {
                if (selected) stateDescription = "Selected"
                if (!enabled) stateDescription = "Unavailable"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            choice.icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(14.dp))
        Text(
            choice.label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
        if (selected) {
            Icon(Icons.Outlined.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun buttonFunctionLabel(function: Int): String = when (function) {
    1 -> "Play / pause"
    4 -> "Voice assistant"
    5 -> "Previous track"
    6 -> "Next track"
    else -> HeadsetSemantics.buttonFunctionLabel(function)
}

private fun buttonAssignmentLabel(
    mapping: HeadsetCapabilityDiscovery.KeyMapping,
): String = if (mapping.button == 1) {
    HeadsetSemantics.buttonActionLabel(mapping.action)
} else {
    "${HeadsetSemantics.buttonControlLabel(mapping.deviceType, mapping.button)} · " +
        HeadsetSemantics.buttonActionLabel(mapping.action)
}

private val ANC_VALUES = setOf(4, 8, 16, 32)

private fun formatFreq(hz: Int): String = when {
    hz >= 1000 && hz % 1000 == 0 -> "${hz / 1000} kHz"
    hz >= 1000 -> String.format(java.util.Locale.US, "%.1f kHz", hz / 1000f)
    else -> "$hz Hz"
}
