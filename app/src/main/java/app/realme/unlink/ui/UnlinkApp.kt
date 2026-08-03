package app.realme.unlink.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BluetoothDisabled
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SpeakerPhone
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.realme.unlink.ConnectionState
import app.realme.unlink.EqSuggestions
import app.realme.unlink.HeadsetCapabilityDiscovery
import app.realme.unlink.HeadsetSemantics
import app.realme.unlink.HeadsetSnapshot
import app.realme.unlink.SwitchCatalog

enum class SheetKind { None, CustomEq, Buttons }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
    onButtons: (List<HeadsetCapabilityDiscovery.KeyMapping>) -> Unit,
    onFindMode: (Boolean) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var sheet by remember { mutableStateOf(SheetKind.None) }

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
                    Column {
                        Text("Realme Unlink", style = MaterialTheme.typography.titleLarge)
                        Text(
                            connectionSubtitle(state),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
                    message = "Reaching your earbuds…",
                )

                ConnectionState.BluetoothOff -> StatusPane(
                    icon = Icons.Outlined.BluetoothDisabled,
                    title = "Bluetooth is off",
                    message = "Turn on Bluetooth to control your earbuds.",
                    primary = "Open Bluetooth settings",
                    openBluetoothSettings = true,
                )

                ConnectionState.PermissionNeeded -> StatusPane(
                    icon = Icons.Outlined.Bluetooth,
                    title = "Nearby devices permission",
                    message = "Realme Unlink needs Bluetooth access to talk to your earbuds.",
                    primary = "Grant permission",
                    onPrimary = onRequestPermission,
                )

                ConnectionState.NotBonded -> StatusPane(
                    icon = Icons.Outlined.Headphones,
                    title = "Earbuds not paired",
                    message = "Pair realme Buds Wireless 5 ANC in system Bluetooth settings, then return here.",
                    primary = "Open Bluetooth settings",
                    openBluetoothSettings = true,
                )

                is ConnectionState.Unavailable -> StatusPane(
                    icon = Icons.Outlined.Sensors,
                    title = s.title,
                    message = s.message,
                    primary = if (s.canRetry) "Try again" else null,
                    onPrimary = if (s.canRetry) onRefresh else null,
                )

                is ConnectionState.Connected -> ConnectedDashboard(
                    snapshot = s.snapshot,
                    busy = busy,
                    onNoiseValue = onNoiseValue,
                    onEqId = onEqId,
                    onBassBand = onBassBand,
                    onSwitch = onSwitch,
                    onFindMode = onFindMode,
                    onOpenCustomEq = { sheet = SheetKind.CustomEq },
                    onOpenButtons = { sheet = SheetKind.Buttons },
                )
            }

            AnimatedVisibility(
                visible = busy && state is ConnectionState.Connected,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp),
            ) {
                LinearWavyProgressIndicator(Modifier.fillMaxWidth(0.4f))
            }
        }
    }

    val connected = (state as? ConnectionState.Connected)?.snapshot
    if (sheet != SheetKind.None && connected != null) {
        ModalBottomSheet(onDismissRequest = { sheet = SheetKind.None }) {
            when (sheet) {
                SheetKind.CustomEq -> CustomEqSheet(
                    snapshot = connected,
                    onApply = {
                        onApplyCustomEq(it)
                        sheet = SheetKind.None
                    },
                    onClose = { sheet = SheetKind.None },
                )

                SheetKind.Buttons -> ButtonsSheet(
                    mappings = connected.buttonMappings,
                    onSave = {
                        onButtons(it)
                        sheet = SheetKind.None
                    },
                    onClose = { sheet = SheetKind.None },
                )

                SheetKind.None -> Unit
            }
        }
    }
}

private fun connectionSubtitle(state: ConnectionState): String = when (state) {
    ConnectionState.Checking, ConnectionState.Connecting -> "Connecting…"
    ConnectionState.BluetoothOff -> "Bluetooth off"
    ConnectionState.PermissionNeeded -> "Permission needed"
    ConnectionState.NotBonded -> "Not paired"
    is ConnectionState.Connected -> state.snapshot.deviceName
    is ConnectionState.Unavailable -> "Disconnected"
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingPane(title: String, message: String) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularWavyProgressIndicator(Modifier.size(56.dp))
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
private fun ConnectedDashboard(
    snapshot: HeadsetSnapshot,
    busy: Boolean,
    onNoiseValue: (Int) -> Unit,
    onEqId: (Int) -> Unit,
    onBassBand: (HeadsetCapabilityDiscovery.BassEngineValue) -> Unit,
    onSwitch: (Int, Boolean) -> Unit,
    onFindMode: (Boolean) -> Unit,
    onOpenCustomEq: () -> Unit,
    onOpenButtons: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { StatusRow(snapshot) }
        if (snapshot.batteries.isNotEmpty()) {
            item { BatteryCard(snapshot) }
        }
        snapshot.noise?.let { noise ->
            item {
                NoiseCard(
                    noiseValue = noise.value,
                    enabled = !busy,
                    onSelect = onNoiseValue,
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
        if (snapshot.bassBands.isNotEmpty()) {
            item {
                BassCard(
                    bands = snapshot.bassBands,
                    enabled = !busy,
                    onChange = onBassBand,
                )
            }
        }
        val featureSwitches = snapshot.switches.filter { it.id != 17 && it.id != 38 }
        if (featureSwitches.isNotEmpty()) {
            item {
                SwitchesCard(
                    switches = featureSwitches,
                    enabled = !busy,
                    onToggle = onSwitch,
                )
            }
        }
        if (snapshot.buttonMappings.isNotEmpty()) {
            item {
                ActionsCard(
                    title = "Buttons",
                    subtitle = summarizeButtons(snapshot.buttonMappings),
                    icon = Icons.Outlined.TouchApp,
                    actionLabel = "Customize",
                    onAction = onOpenButtons,
                    enabled = !busy,
                )
            }
        }
        // In-ear: this neckband answers 0x0109 with flags=0x01 for both buds even when one is
        // worn (live check: right in-ear still reported ear=false). Vendor apps rely on push
        // events (out of v1 scope). Hide the card rather than show a permanent false "Out".
        if (snapshot.wear.any { it.inEar }) {
            item { WearCard(snapshot.wear) }
        }
        if (snapshot.multipoint.isNotEmpty() || snapshot.switches.any { it.id == 17 }) {
            item {
                DualDeviceCard(
                    entries = snapshot.multipoint,
                    dualSwitch = snapshot.switches.firstOrNull { it.id == 17 },
                    enabled = !busy,
                    onToggle = onSwitch,
                )
            }
        }
        item {
            FindCard(
                active = snapshot.findModeActive,
                enabled = !busy,
                onToggle = onFindMode,
            )
        }
        snapshot.firmwareVersion?.let { version ->
            item {
                Text(
                    "Firmware $version",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun StatusRow(snapshot: HeadsetSnapshot) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                snapshot.deviceName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Connected",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        snapshot.primaryBatteryPercent?.let { pct ->
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "$pct%",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (snapshot.anyCharging) {
                    Text(
                        "Charging",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun BatteryCard(snapshot: HeadsetSnapshot) {
    SectionCard(title = "Battery", icon = Icons.Outlined.BatteryChargingFull) {
        if (snapshot.batteries.size == 1) {
            val level = snapshot.batteries.first()
            Text(
                "${level.percentage}%" + if (level.charging) " · charging" else "",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                snapshot.batteries.forEach { level ->
                    Column(Modifier.weight(1f)) {
                        Text(
                            HeadsetSemantics.wearRole(level.deviceType),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "${level.percentage}%",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (level.charging) {
                            Text(
                                "Charging",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoiseCard(
    noiseValue: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    SectionCard(title = "Noise control", icon = Icons.Outlined.Hearing) {
        Text(
            NoiseLabels.modes.firstOrNull { it.value == noiseValue }?.fullLabel
                ?: HeadsetSemantics.noiseDisplayLabel(1, noiseValue),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NoiseLabels.modes.forEach { mode ->
                FilterChip(
                    selected = mode.value == noiseValue,
                    onClick = { if (enabled) onSelect(mode.value) },
                    enabled = enabled,
                    label = { Text(mode.shortLabel) },
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
    SectionCard(title = "EQ mode", icon = Icons.Outlined.Tune) {
        Text(
            "Now · ${snapshot.currentEqLabel}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
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
                FilterChip(
                    selected = snapshot.currentEqId == profile.id,
                    onClick = { if (enabled) onSelect(profile.id) },
                    enabled = enabled,
                    label = { Text(profile.name.ifBlank { "Profile ${profile.id}" }) },
                )
            }
        }
        if (snapshot.customEqProfile != null) {
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(
                onClick = onEditCustom,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Edit custom EQ")
            }
        }
    }
}

@Composable
private fun BassCard(
    bands: List<HeadsetCapabilityDiscovery.BassEngineValue>,
    enabled: Boolean,
    onChange: (HeadsetCapabilityDiscovery.BassEngineValue) -> Unit,
) {
    SectionCard(title = "Clear Bass", icon = Icons.AutoMirrored.Outlined.VolumeUp) {
        bands.forEach { band ->
            val label = band.type?.let { HeadsetSemantics.bassBandLabel(it) ?: "Band $it" }
                ?: "Bass"
            var slider by remember(band.type, band.currentValue) {
                mutableFloatStateOf(band.currentValue.toFloat())
            }
            Text(
                "$label · ${slider.toInt()} dB",
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = slider,
                onValueChange = { slider = it },
                valueRange = band.minValue.toFloat()..band.maxValue.toFloat(),
                steps = (band.maxValue - band.minValue - 1).coerceAtLeast(0),
                enabled = enabled,
                onValueChangeFinished = {
                    val next = band.copy(currentValue = slider.toInt())
                    if (next.currentValue != band.currentValue) {
                        onChange(next)
                    }
                },
            )
        }
    }
}

@Composable
private fun SwitchesCard(
    switches: List<SwitchCatalog.LogicalSwitch>,
    enabled: Boolean,
    onToggle: (Int, Boolean) -> Unit,
) {
    SectionCard(title = "Sound effects", icon = Icons.Outlined.Settings) {
        switches.forEach { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(item.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = item.enabled,
                    onCheckedChange = { if (enabled) onToggle(item.id, it) },
                    enabled = enabled,
                )
            }
        }
    }
}

@Composable
private fun WearCard(wear: List<HeadsetCapabilityDiscovery.WearStatus>) {
    SectionCard(title = "In-ear detection", icon = Icons.Outlined.Sensors) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            wear.filter { it.deviceType == 1 || it.deviceType == 2 }.forEach { entry ->
                val role = HeadsetSemantics.wearRole(entry.deviceType)
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = if (entry.inEar) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(role, style = MaterialTheme.typography.labelMedium)
                        Text(
                            when {
                                entry.inEar -> "In ear"
                                entry.inBox -> "Docked"
                                else -> "Out"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
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
    SectionCard(title = "Dual device", icon = Icons.Outlined.SpeakerPhone) {
        if (entries.isEmpty()) {
            Text(
                "No connected devices reported",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            entries.forEach { entry ->
                val phone = if (entry.flags == 1) " · this phone" else ""
                Text(
                    "${entry.name.ifBlank { "Device" }} · " +
                        HeadsetSemantics.multipointStateLabel(entry.state) + phone,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(6.dp))
            }
        }
        dualSwitch?.let { sw ->
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Allow dual device",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(
                    checked = sw.enabled,
                    onCheckedChange = { if (enabled) onToggle(sw.id, it) },
                    enabled = enabled,
                )
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
    SectionCard(title = "Find device", icon = Icons.Outlined.MusicNote) {
        Text(
            if (active) {
                "Playing a tone on the earbuds."
            } else {
                "Play a tone to locate the earbuds."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        if (active) {
            Button(
                onClick = { onToggle(false) },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Stop")
            }
        } else {
            FilledTonalButton(
                onClick = { onToggle(true) },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Start tone")
            }
        }
    }
}

@Composable
private fun ActionsCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    actionLabel: String,
    onAction: () -> Unit,
    enabled: Boolean,
) {
    SectionCard(title = title, icon = icon) {
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(
            onClick = onAction,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(actionLabel)
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
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
    val base = snapshot.customEqProfile ?: return
    var bands by remember(base.id, base.dbValues) { mutableStateOf(base.dbValues) }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
    ) {
        Text("Custom EQ", style = MaterialTheme.typography.headlineSmall)
        Text(
            base.name.ifBlank { "Custom1" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text("Suggestions", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EqSuggestions.suggestions.forEach { suggestion ->
                FilterChip(
                    selected = false,
                    onClick = {
                        bands = EqSuggestions.apply(base, suggestion).dbValues
                    },
                    label = { Text(suggestion.name) },
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        base.frequencies.forEachIndexed { index, freq ->
            val value = bands.getOrElse(index) { 0 }
            var slider by remember(index, value) { mutableFloatStateOf(value.toFloat()) }
            // Keep slider synced when suggestion chips rewrite bands.
            LaunchedEffect(bands) {
                slider = bands.getOrElse(index) { 0 }.toFloat()
            }
            Text(
                formatFreq(freq) + " · ${slider.toInt()} dB",
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = slider,
                onValueChange = {
                    slider = it
                    bands = bands.toMutableList().also { list ->
                        if (index < list.size) list[index] = it.toInt()
                    }
                },
                valueRange = base.minValue.toFloat()..base.maxValue.toFloat(),
                steps = (base.maxValue - base.minValue - 1).coerceAtLeast(0),
            )
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onApply(base.copy(dbValues = bands, selected = true)) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Apply to ${base.name.ifBlank { "Custom1" }}")
        }
        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ButtonsSheet(
    mappings: List<HeadsetCapabilityDiscovery.KeyMapping>,
    onSave: (List<HeadsetCapabilityDiscovery.KeyMapping>) -> Unit,
    onClose: () -> Unit,
) {
    val functions = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0)
    var draft by remember(mappings) { mutableStateOf(mappings) }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
    ) {
        Text("Buttons", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Multifunction and volume keys on the neckband.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        draft.forEachIndexed { index, mapping ->
            Text(
                HeadsetSemantics.buttonControlLabel(mapping.deviceType, mapping.button) +
                    " · " + HeadsetSemantics.buttonActionLabel(mapping.action),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                functions.forEach { function ->
                    FilterChip(
                        selected = mapping.function == function,
                        onClick = {
                            draft = draft.toMutableList().also {
                                it[index] = mapping.copy(function = function)
                            }
                        },
                        label = {
                            Text(HeadsetSemantics.buttonFunctionLabel(function))
                        },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        Button(
            onClick = { onSave(draft) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save")
        }
        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}

private fun summarizeButtons(mappings: List<HeadsetCapabilityDiscovery.KeyMapping>): String {
    return mappings.take(3).joinToString(" · ") {
        HeadsetSemantics.buttonControlLabel(it.deviceType, it.button) + " " +
            HeadsetSemantics.buttonActionLabel(it.action).lowercase() + " → " +
            HeadsetSemantics.buttonFunctionLabel(it.function)
    } + if (mappings.size > 3) "…" else ""
}

private fun formatFreq(hz: Int): String = when {
    hz >= 1000 && hz % 1000 == 0 -> "${hz / 1000} kHz"
    hz >= 1000 -> String.format("%.1f kHz", hz / 1000f)
    else -> "$hz Hz"
}
