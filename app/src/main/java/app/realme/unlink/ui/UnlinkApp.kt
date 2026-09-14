@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.realme.unlink.ui

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BluetoothDisabled
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.SpeakerPhone
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.realme.unlink.ConnectionState
import app.realme.unlink.EqSuggestions
import app.realme.unlink.HeadsetCapabilityDiscovery
import app.realme.unlink.HeadsetSemantics
import app.realme.unlink.HeadsetSnapshot
import app.realme.unlink.R
import app.realme.unlink.SwitchCatalog

enum class SheetKind { None, CustomEq }

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
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var sheet by remember { mutableStateOf(SheetKind.None) }
    val modalSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(snackbarMessage) {
        val message = snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onSnackbarConsumed()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !busy) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val s = state) {
                ConnectionState.Checking,
                ConnectionState.Connecting,
                -> LoadingPane(
                    title = "Connecting",
                    message = "Separate magnetic earbud clasps to power on neckband",
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
                    title = s.title,
                    message = s.message,
                    primary = if (s.canRetry) "Try again" else null,
                    onPrimary = onRefresh,
                )

                is ConnectionState.Connected -> ConnectedDashboard(
                    snapshot = s.snapshot,
                    busy = busy,
                    onNoiseValue = onNoiseValue,
                    onEqId = onEqId,
                    onBassBand = onBassBand,
                    onSwitch = onSwitch,
                    onButtons = onButtons,
                    onFindMode = onFindMode,
                    onOpenCustomEq = { sheet = SheetKind.CustomEq },
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

    val connected = (state as? ConnectionState.Connected)?.snapshot
    if (sheet == SheetKind.CustomEq && connected != null) {
        ModalBottomSheet(
            onDismissRequest = { sheet = SheetKind.None },
            sheetState = modalSheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            CustomEqSheet(
                snapshot = connected,
                onApply = {
                    onApplyCustomEq(it)
                    sheet = SheetKind.None
                },
                onClose = { sheet = SheetKind.None },
            )
        }
    }
}

@Composable
private fun DeviceHeroHeader(snapshot: HeadsetSnapshot) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
        tonalElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                modifier = Modifier.size(72.dp),
                shape = MaterialShapes.Cookie12Sided.toShape(),
                color = MaterialTheme.colorScheme.primary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.Headphones,
                        contentDescription = null,
                        modifier = Modifier.size(38.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                snapshot.deviceName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Connection Status Chip
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Connected",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }

                // Battery Gauge Pill (No Emoji)
                val pct = snapshot.primaryBatteryPercent
                if (pct != null) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (pct > 90) Icons.Outlined.BatteryFull else Icons.Outlined.BatteryChargingFull,
                                contentDescription = "Battery",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "$pct%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
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
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(Modifier.size(48.dp))
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
            modifier = Modifier.size(80.dp),
            shape = MaterialShapes.Cookie12Sided.toShape(),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (primary != null) {
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    if (openBluetoothSettings) {
                        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    } else {
                        onPrimary?.invoke()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(primary)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp, start = 4.dp),
    )
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
    onOpenCustomEq: () -> Unit,
) {
    val gameSwitch = snapshot.switches.firstOrNull { it.id == 6 || it.id == 8 }
    val volumeEnhancer = snapshot.switches.firstOrNull { it.id == 9 }
    val windNoise = snapshot.switches.firstOrNull { it.id == 26 }
    val dynamicBass = snapshot.switches.firstOrNull { it.id == 29 }
    val dualSwitch = snapshot.switches.firstOrNull { it.id == 17 }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            DeviceHeroHeader(snapshot = snapshot)
        }

        // --- 1. Sound & Noise Control ---
        item { SectionHeader("Sound & Noise Control") }

        snapshot.noise?.let { noise ->
            item {
                NoiseCard(
                    noiseValue = noise.value,
                    windNoise = windNoise,
                    enabled = !busy,
                    onSelect = onNoiseValue,
                    onToggle = onSwitch,
                )
            }
        }

        if (snapshot.currentEqId != null || snapshot.eqProfiles.isNotEmpty()) {
            item {
                EqCard(
                    snapshot = snapshot,
                    enabled = !busy,
                    onSelect = onEqId,
                    onEditCustom = onOpenCustomEq,
                )
            }
        }

        item {
            BassCard(
                bands = snapshot.bassBands,
                dynamicBass = dynamicBass,
                enabled = !busy,
                onChange = onBassBand,
                onToggle = onSwitch,
            )
        }

        gameSwitch?.let { sw ->
            item {
                GameModeCard(
                    gameSwitch = sw,
                    enabled = !busy,
                    onToggle = onSwitch,
                )
            }
        }

        volumeEnhancer?.let { sw ->
            item {
                ToggleRowCard(
                    title = "Volume Enhancer",
                    icon = Icons.Outlined.Headphones,
                    sw = sw,
                    enabled = !busy,
                    onToggle = onSwitch,
                )
            }
        }

        // --- 2. Hardware Controls ---
        item { SectionHeader("Hardware Controls") }

        item {
            HardwareButtonsCard(
                mappings = snapshot.buttonMappings,
                enabled = !busy,
                onUpdate = onButtons,
            )
        }

        item {
            FindCard(
                active = snapshot.findModeActive,
                enabled = !busy,
                onToggle = onFindMode,
            )
        }

        // --- 3. Connectivity & OS ---
        item { SectionHeader("Connectivity & OS") }

        item {
            DualDeviceCard(
                entries = snapshot.multipoint,
                dualSwitch = dualSwitch,
                enabled = !busy,
                onToggle = onSwitch,
            )
        }


        item {
            SpatialAudioCard()
        }

        snapshot.firmwareVersion?.let { version ->
            item {
                Text(
                    "Firmware v$version",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoiseCard(
    noiseValue: Int,
    windNoise: SwitchCatalog.LogicalSwitch?,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    onToggle: (Int, Boolean) -> Unit,
) {
    val ancModes = listOf(
        NoiseLabels.Mode(8, "Strong ANC", "Strong noise cancellation"),
        NoiseLabels.Mode(4, "Mild ANC", "Mild noise cancellation"),
        NoiseLabels.Mode(32, "Smart ANC", "Smart noise cancellation"),
        NoiseLabels.Mode(2, "Transparency", "Transparency mode"),
        NoiseLabels.Mode(1, "Off", "Noise control off"),
    )

    SectionCard(title = "Noise Control", icon = Icons.Outlined.Hearing, accentColor = MaterialTheme.colorScheme.primary) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ancModes.forEach { mode ->
                val selected = mode.value == noiseValue
                FilterChip(
                    selected = selected,
                    onClick = { if (enabled) onSelect(mode.value) },
                    enabled = enabled,
                    label = { Text(mode.shortLabel, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
        }
        windNoise?.let { sw ->
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Wind Noise Reduction", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = sw.enabled,
                    onCheckedChange = { if (enabled) onToggle(sw.id, it) },
                    enabled = enabled,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EqCard(
    snapshot: HeadsetSnapshot,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    onEditCustom: () -> Unit,
) {
    val presetNames = mapOf(
        0 to "Original",
        1 to "Serenade",
        2 to "Deep Bass",
        3 to "Clear Bass",
        4 to "Custom",
    )

    SectionCard(title = "Equalizer", icon = Icons.Outlined.Tune, accentColor = MaterialTheme.colorScheme.secondary) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = snapshot.currentEqId == 0,
                onClick = { if (enabled) onSelect(0) },
                enabled = enabled,
                label = { Text("Original") },
            )
            snapshot.eqProfiles.forEach { profile ->
                val rawName = profile.name.ifBlank { presetNames[profile.id] ?: "Profile ${profile.id}" }
                val label = if (rawName.startsWith("Custom", ignoreCase = true)) "Custom" else rawName
                FilterChip(
                    selected = snapshot.currentEqId == profile.id,
                    onClick = { if (enabled) onSelect(profile.id) },
                    enabled = enabled,
                    label = { Text(label) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(
            onClick = onEditCustom,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Custom EQ (6-band)")
        }
    }
}

@Composable
private fun BassCard(
    bands: List<HeadsetCapabilityDiscovery.BassEngineValue>,
    dynamicBass: SwitchCatalog.LogicalSwitch?,
    enabled: Boolean,
    onChange: (HeadsetCapabilityDiscovery.BassEngineValue) -> Unit,
    onToggle: (Int, Boolean) -> Unit,
) {
    SectionCard(title = "Bass Tuner", icon = Icons.AutoMirrored.Outlined.VolumeUp, accentColor = MaterialTheme.colorScheme.tertiary) {
        dynamicBass?.let { sw ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Dynamic Bass Boost",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Switch(
                    checked = sw.enabled,
                    onCheckedChange = { if (enabled) onToggle(sw.id, it) },
                    enabled = enabled,
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        bands.forEach { band ->
            val label = band.type?.let { HeadsetSemantics.bassBandLabel(it) } ?: "Bass"
            val db = band.currentValue
            val dbText = if (db > 0) "+$db dB" else "$db dB"

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    dbText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Slider(
                value = db.toFloat(),
                onValueChange = { newVal ->
                    if (enabled) {
                        onChange(band.copy(currentValue = newVal.toInt()))
                    }
                },
                valueRange = band.minValue.toFloat()..band.maxValue.toFloat(),
                steps = (band.maxValue - band.minValue - 1).coerceAtLeast(0),
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun GameModeCard(
    gameSwitch: SwitchCatalog.LogicalSwitch,
    enabled: Boolean,
    onToggle: (Int, Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Sensors,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Game Mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                        Text(
                            "45ms",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
                Text(
                    "Ultra-low latency audio sync",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = gameSwitch.enabled,
                onCheckedChange = { if (enabled) onToggle(gameSwitch.id, it) },
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun ToggleRowCard(
    title: String,
    icon: ImageVector,
    sw: SwitchCatalog.LogicalSwitch,
    enabled: Boolean,
    onToggle: (Int, Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Switch(
                checked = sw.enabled,
                onCheckedChange = { if (enabled) onToggle(sw.id, it) },
                enabled = enabled,
            )
        }
    }
}


/**
 * Protocol function mapping spec according to HeadsetSemantics:
 * 1 -> Play / Pause
 * 2 -> Next Track
 * 3 -> Previous Track
 * 4 -> Voice Assistant
 * 5 -> Noise Mode
 * 6 -> Switch Device
 * 7 -> Game Mode
 * 0 -> Off
 */
@Composable
private fun GestureDropdownMenu(
    selectedFunction: Int,
    enabled: Boolean = true,
    onSelectFunction: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val functions = listOf(1, 2, 3, 4, 5, 6, 7, 0)

    Box {
        Surface(
            onClick = { if (enabled) expanded = true },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            tonalElevation = 2.dp,
            enabled = enabled,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    HeadsetSemantics.buttonFunctionLabel(selectedFunction),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Outlined.ArrowDropDown,
                    contentDescription = "Select action",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            functions.forEach { func ->
                DropdownMenuItem(
                    text = {
                        Text(
                            HeadsetSemantics.buttonFunctionLabel(func),
                            fontWeight = if (func == selectedFunction) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        onSelectFunction(func)
                        expanded = false
                    },
                    leadingIcon = {
                        val icon = when (func) {
                            1 -> Icons.Outlined.PlayArrow
                            2 -> Icons.AutoMirrored.Outlined.ArrowForward
                            3 -> Icons.AutoMirrored.Outlined.ArrowBack
                            4 -> Icons.Outlined.Mic
                            5 -> Icons.Outlined.Hearing
                            6 -> Icons.Outlined.SwapHoriz
                            7 -> Icons.Outlined.Sensors
                            else -> Icons.Outlined.Block
                        }
                        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                )
            }
        }
    }
}

@Composable
private fun HardwareButtonsCard(
    mappings: List<HeadsetCapabilityDiscovery.KeyMapping>,
    enabled: Boolean,
    onUpdate: (HeadsetCapabilityDiscovery.KeyMapping) -> Unit,
) {
    SectionCard(title = "Multifunction Remote (MFB)", icon = Icons.Outlined.TouchApp, accentColor = MaterialTheme.colorScheme.primary) {
        val defaultMfbMappings = listOf(
            HeadsetCapabilityDiscovery.KeyMapping(deviceType = 1, button = 1, action = 1, function = 1),
            HeadsetCapabilityDiscovery.KeyMapping(deviceType = 1, button = 1, action = 2, function = 2),
            HeadsetCapabilityDiscovery.KeyMapping(deviceType = 1, button = 1, action = 3, function = 3),
            HeadsetCapabilityDiscovery.KeyMapping(deviceType = 1, button = 1, action = 4, function = 5),
        )
        val activeMappings = if (mappings.isNotEmpty()) {
            mappings.filter { it.button == 1 || it.deviceType == 1 }
                .distinctBy { it.action }
                .sortedBy { it.action }
        } else defaultMfbMappings

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            activeMappings.forEachIndexed { index, mapping ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        HeadsetSemantics.buttonActionLabel(mapping.action),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )
                    GestureDropdownMenu(
                        selectedFunction = mapping.function,
                        enabled = enabled,
                        onSelectFunction = { newFunc ->
                            onUpdate(mapping.copy(function = newFunc))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DualDeviceCard(
    entries: List<HeadsetCapabilityDiscovery.MultipointEntry>,
    dualSwitch: SwitchCatalog.LogicalSwitch?,
    enabled: Boolean,
    onToggle: (Int, Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.SpeakerPhone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Dual Connection",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (dualSwitch?.enabled == true) "Multi-device audio switching active" else "Connect two devices simultaneously",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                dualSwitch?.let { sw ->
                    Switch(
                        checked = sw.enabled,
                        onCheckedChange = { if (enabled) onToggle(sw.id, it) },
                        enabled = enabled,
                    )
                }
            }

            if (dualSwitch?.enabled == true) {
                Spacer(Modifier.height(12.dp))
                if (entries.isEmpty()) {
                    Text(
                        "No active device connection reported",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    entries.forEach { entry ->
                        val isThisPhone = entry.flags == 1
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.Bluetooth,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                entry.name.ifBlank { "Device" },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "· " + HeadsetSemantics.multipointStateLabel(entry.state) + (if (isThisPhone) " (This Phone)" else ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpatialAudioCard() {
    val context = LocalContext.current
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    val spatializerStatus = remember(audioManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2 && audioManager != null) {
            runCatching {
                val s = audioManager.spatializer
                val isAvail = s.isAvailable
                val isEnabled = s.isEnabled
                if (isEnabled) "Active in Android OS"
                else if (isAvail) "Available in OS"
                else "Supported"
            }.getOrDefault("Supported")
        } else {
            "Supported"
        }
    }

    SectionCard(title = "Spatial Audio", icon = Icons.Outlined.GraphicEq, accentColor = MaterialTheme.colorScheme.tertiary) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "OS Spatializer Status",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Text(
                        spatializerStatus,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            OutlinedButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { context.startActivity(intent) }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Configure Spatial Audio in System Settings")
            }
        }
    }
}

@Composable
private fun FindCard(
    active: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    SectionCard(title = "Find Earbuds", icon = Icons.Outlined.MusicNote, accentColor = MaterialTheme.colorScheme.error) {
        Text(
            "Play a tone to locate your neckband earbuds.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { onToggle(!active) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (active) "Stop Tone" else "Start Tone")
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: ImageVector,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = accentColor,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomEqSheet(
    snapshot: HeadsetSnapshot,
    onApply: (HeadsetCapabilityDiscovery.EqProfile) -> Unit,
    onClose: () -> Unit,
) {
    val base = snapshot.customEqProfile
        ?: HeadsetCapabilityDiscovery.EqProfile(
            selected = false,
            minValue = -6,
            maxValue = 6,
            id = 4,
            name = "Custom",
            frequencies = listOf(50, 250, 1000, 4000, 8000, 16000),
            dbValues = listOf(0, 0, 0, 0, 0, 0),
        )
    var bands by remember(base.id, base.dbValues) { mutableStateOf(base.dbValues) }
    var selectedPresetId by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        // Scrollable content
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Text(
                "Custom Equalizer",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(14.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EqSuggestions.suggestions.forEach { suggestion ->
                    FilterChip(
                        selected = (selectedPresetId == suggestion.id),
                        onClick = {
                            selectedPresetId = suggestion.id
                            bands = EqSuggestions.apply(base, suggestion).dbValues
                        },
                        label = { Text(suggestion.name) },
                    )
                }
            }
            Spacer(Modifier.height(20.dp))

            base.frequencies.forEachIndexed { index, freq ->
                val targetVal = bands.getOrElse(index) { 0 }
                val dbText = if (targetVal > 0) "+$targetVal dB" else "$targetVal dB"

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(formatFreq(freq), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(dbText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }

                Slider(
                    value = targetVal.toFloat(),
                    onValueChange = { newVal ->
                        selectedPresetId = null
                        val list = bands.toMutableList()
                        if (index < list.size) {
                            list[index] = newVal.toInt()
                            bands = list
                        }
                    },
                    valueRange = base.minValue.toFloat()..base.maxValue.toFloat(),
                    steps = (base.maxValue - base.minValue - 1).coerceAtLeast(0),
                )
                Spacer(Modifier.height(4.dp))
            }
        }

        // Pinned Bottom Action Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Button(
                    onClick = { onApply(base.copy(dbValues = bands, selected = true)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("Apply Custom EQ", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                TextButton(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

private fun formatFreq(hz: Int): String = when {
    hz >= 1000 && hz % 1000 == 0 -> "${hz / 1000} kHz"
    hz >= 1000 -> String.format("%.1f kHz", hz / 1000f)
    else -> "$hz Hz"
}
