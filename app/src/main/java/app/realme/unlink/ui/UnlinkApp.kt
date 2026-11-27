package app.realme.unlink.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.outlined.NoiseAware
import androidx.compose.material.icons.outlined.NoiseControlOff
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NoiseAware
import androidx.compose.material.icons.rounded.NoiseControlOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RingVolume
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SpatialAudio
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalToggleButton
import androidx.compose.material3.FilledTonalToggleButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberSliderState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import kotlin.math.roundToInt

private enum class SheetKind {
    None,
    Noise,
    Equalizer,
    DynamicBass,
    ButtonControls,
}

private enum class ScreenKind {
    Loading,
    BluetoothOff,
    PermissionNeeded,
    NotBonded,
    Unavailable,
    Connected,
}

private enum class NoiseMode(val value: Int, val label: String) {
    Anc(8, "ANC"),
    Off(1, "Off"),
    Ambient(2, "Ambient"),
}

private data class ButtonChoice(
    val function: Int,
    val label: String,
    val icon: ImageVector,
    val supportedByCurrentController: Boolean,
)

private val buttonChoices = listOf(
    ButtonChoice(1, "Play / pause", Icons.Rounded.PlayArrow, supportedByCurrentController = true),
    ButtonChoice(6, "Next track", Icons.Rounded.SkipNext, supportedByCurrentController = true),
    ButtonChoice(5, "Previous track", Icons.Rounded.SkipPrevious, supportedByCurrentController = true),
    ButtonChoice(4, "Voice assistant", Icons.Rounded.Mic, supportedByCurrentController = true),
    ButtonChoice(17, "Game mode", Icons.Rounded.SportsEsports, supportedByCurrentController = true),
    ButtonChoice(0, "None", Icons.Rounded.Block, supportedByCurrentController = true),
)

@OptIn(ExperimentalMaterial3Api::class)
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
    launcherIconEnabled: Boolean = false,
    onToggleLauncherIcon: (Boolean) -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var sheet by remember { mutableStateOf(SheetKind.None) }
    var menuExpanded by remember { mutableStateOf(false) }
    var selectedButtonMapping by remember {
        mutableStateOf<HeadsetCapabilityDiscovery.KeyMapping?>(null)
    }
    var pullRefreshing by remember { mutableStateOf(false) }
    val modalSheetState = rememberBottomSheetState(
        SheetValue.Hidden,
        setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val connected = (state as? ConnectionState.Connected)?.snapshot

    LaunchedEffect(busy) {
        if (!busy) pullRefreshing = false
    }

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

    val screenKind = when (state) {
        ConnectionState.Checking,
        ConnectionState.Connecting,
        -> ScreenKind.Loading

        ConnectionState.BluetoothOff -> ScreenKind.BluetoothOff
        ConnectionState.PermissionNeeded -> ScreenKind.PermissionNeeded
        ConnectionState.NotBonded -> ScreenKind.NotBonded
        is ConnectionState.Unavailable -> ScreenKind.Unavailable
        is ConnectionState.Connected -> ScreenKind.Connected
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                    )
                },
                actions = {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                            TooltipAnchorPosition.Below,
                        ),
                        tooltip = { PlainTooltip { Text("Refresh device data") } },
                        state = rememberTooltipState(),
                    ) {
                        IconButton(
                            onClick = onRefresh,
                            enabled = !busy,
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh device data")
                        }
                    }
                    Box {
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                                TooltipAnchorPosition.Below,
                            ),
                            tooltip = { PlainTooltip { Text("More options") } },
                            state = rememberTooltipState(),
                        ) {
                            IconButton(
                                onClick = { menuExpanded = true },
                                shapes = IconButtonDefaults.shapes(),
                            ) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "More options")
                            }
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (launcherIconEnabled) {
                                            "Hide from app drawer"
                                        } else {
                                            "Show in app drawer"
                                        },
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (launcherIconEnabled) {
                                            Icons.Rounded.VisibilityOff
                                        } else {
                                            Icons.Rounded.Apps
                                        },
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onToggleLauncherIcon(!launcherIconEnabled)
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AnimatedContent(
                targetState = screenKind,
                transitionSpec = {
                    fadeIn(animationSpec = effectsSpec) togetherWith
                        fadeOut(animationSpec = effectsSpec)
                },
                label = "connection_screen",
            ) { targetScreen ->
                when (targetScreen) {
                    ScreenKind.Loading -> LoadingPane(
                        title = "Connecting",
                        message = "Separate the magnetic clasp to power on the neckband.",
                    )

                    ScreenKind.BluetoothOff -> StatusPane(
                        icon = Icons.Rounded.BluetoothDisabled,
                        title = "Bluetooth is off",
                        message = "Turn on Bluetooth to control your neckband.",
                        primary = "Open Bluetooth settings",
                        openBluetoothSettings = true,
                    )

                    ScreenKind.PermissionNeeded -> StatusPane(
                        icon = Icons.Rounded.Bluetooth,
                        title = "Nearby devices permission",
                        message = "${stringResource(R.string.app_name)} needs Bluetooth access to talk to your neckband.",
                        primary = "Grant permission",
                        onPrimary = onRequestPermission,
                    )

                    ScreenKind.NotBonded -> StatusPane(
                        icon = Icons.Rounded.Headphones,
                        title = "Neckband not paired",
                        message = "Pair realme Buds Wireless 5 ANC in system Bluetooth settings, then return here.",
                        primary = "Open Bluetooth settings",
                        openBluetoothSettings = true,
                    )

                    ScreenKind.Unavailable -> {
                        val unavailable = state as? ConnectionState.Unavailable
                        StatusPane(
                            icon = Icons.Rounded.Headphones,
                            title = unavailable?.title ?: "Headset unavailable",
                            message = unavailable?.message ?: "Try refreshing once the neckband is powered on.",
                            primary = if (unavailable?.canRetry != false) "Try again" else null,
                            onPrimary = onRefresh,
                        )
                    }

                    ScreenKind.Connected -> {
                        val snapshot = connected
                        if (snapshot != null) {
                            ConnectedDashboard(
                                snapshot = snapshot,
                                busy = busy,
                                pullRefreshing = pullRefreshing,
                                onPullRefresh = {
                                    pullRefreshing = true
                                    onRefresh()
                                },
                                onNoiseValue = onNoiseValue,
                                onSwitch = onSwitch,
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
                    }
                }
            }

            AnimatedVisibility(
                visible = busy && !pullRefreshing && state is ConnectionState.Connected,
                enter = fadeIn(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()),
                exit = fadeOut(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
            ) {
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
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
private fun dashboardSegmentedColors(): ListItemColors = ListItemDefaults.segmentedColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
)

@Composable
private fun dashboardSwitchSegmentedColors(): ListItemColors = ListItemDefaults.segmentedColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
    selectedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    selectedContentColor = MaterialTheme.colorScheme.onSurface,
    selectedLeadingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedTrailingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedSupportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
private fun sheetSegmentedColors(): ListItemColors = ListItemDefaults.segmentedColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
    selectedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    selectedLeadingContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    selectedTrailingContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    selectedSupportingContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
)

@Composable
private fun sheetSwitchSegmentedColors(): ListItemColors = ListItemDefaults.segmentedColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    selectedContentColor = MaterialTheme.colorScheme.onSurface,
    selectedLeadingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedTrailingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedSupportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
private fun ExpressiveSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    enabled: Boolean,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        thumbContent = {
            Icon(
                imageVector = if (checked) Icons.Rounded.Check else Icons.Rounded.Close,
                contentDescription = null,
                modifier = Modifier.size(SwitchDefaults.IconSize),
            )
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DeviceHeader(snapshot: HeadsetSnapshot) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = MaterialShapes.Cookie12Sided.toShape(),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Headphones,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                snapshot.deviceName,
                style = MaterialTheme.typography.titleMediumEmphasized,
                maxLines = 2,
                modifier = Modifier.weight(1f),
            )

            snapshot.primaryBatteryPercent?.let { percentage ->
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (snapshot.anyCharging) {
                            Icon(
                                Icons.Rounded.Bolt,
                                contentDescription = "Charging",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            "$percentage%",
                            style = MaterialTheme.typography.labelMediumEmphasized,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingPane(title: String, message: String) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ContainedLoadingIndicator()
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.headlineSmallEmphasized)
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
        Text(
            title,
            style = MaterialTheme.typography.headlineSmallEmphasized,
            textAlign = TextAlign.Center,
        )
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
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(primary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ConnectedDashboard(
    snapshot: HeadsetSnapshot,
    busy: Boolean,
    pullRefreshing: Boolean,
    onPullRefresh: () -> Unit,
    onNoiseValue: (Int) -> Unit,
    onSwitch: (Int, Boolean) -> Unit,
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

    val pullToRefreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = pullRefreshing,
        onRefresh = onPullRefresh,
        state = pullToRefreshState,
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pullToRefreshState,
                isRefreshing = pullRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) {
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
}

@Composable
private fun PartialDataNotice() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Info,
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
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
                val modeSummary = when (noiseValue) {
                    1 -> "Off"
                    2 -> "Ambient"
                    4 -> "ANC · Mild"
                    8 -> "ANC · Max"
                    16 -> "ANC · Moderate"
                    32 -> "ANC · Smart"
                    else -> null
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Noise control",
                        style = MaterialTheme.typography.bodyLargeEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (modeSummary != null) {
                        Text(
                            modeSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(
                    onClick = onAdjust,
                    enabled = enabled,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(),
                ) {
                    Icon(
                        Icons.Rounded.Tune,
                        contentDescription = "Adjust noise control",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            val modes = NoiseMode.entries
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                modes.forEachIndexed { index, mode ->
                    val selected = selectedMode == mode
                    val iconPainter: Painter = when (mode) {
                        NoiseMode.Anc -> painterResource(R.drawable.ic_mode_anc)
                        NoiseMode.Off -> rememberVectorPainter(
                            if (selected) Icons.Rounded.NoiseControlOff else Icons.Outlined.NoiseControlOff,
                        )
                        NoiseMode.Ambient -> rememberVectorPainter(
                            if (selected) Icons.Rounded.NoiseAware else Icons.Outlined.NoiseAware,
                        )
                    }
                    ToggleButton(
                        checked = selected,
                        onCheckedChange = {
                            val target = when (mode) {
                                NoiseMode.Anc -> if (noiseValue in ANC_VALUES) noiseValue else NoiseMode.Anc.value
                                NoiseMode.Ambient -> NoiseMode.Ambient.value
                                NoiseMode.Off -> NoiseMode.Off.value
                            }
                            if (target != noiseValue) onSelect(target)
                        },
                        enabled = enabled,
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                        colors = ToggleButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .semantics { role = Role.RadioButton },
                    ) {
                        Icon(
                            painter = iconPainter,
                            contentDescription = mode.label,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}

private sealed interface SoundItem {
    data class Navigation(
        val icon: ImageVector,
        val title: String,
        val summary: String,
        val enabled: Boolean,
        val onClick: () -> Unit,
    ) : SoundItem

    data class Toggle(
        val icon: ImageVector,
        val title: String,
        val checked: Boolean,
        val enabled: Boolean,
        val onCheckedChange: (Boolean) -> Unit,
    ) : SoundItem
}

@Composable
private fun SoundSection(
    snapshot: HeadsetSnapshot,
    busy: Boolean,
    dynamicBass: SwitchCatalog.LogicalSwitch?,
    writableBassBands: List<HeadsetCapabilityDiscovery.BassEngineValue>,
    onSwitch: (Int, Boolean) -> Unit,
    onVolumeEnhancer: (Boolean) -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenDynamicBass: () -> Unit,
) {
    val items = buildList<SoundItem> {
        if (snapshot.hasEqualizer) {
            val eqSummary = when {
                snapshot.currentEqId == null -> "Unknown"
                snapshot.currentEqId in 0..3 -> snapshot.currentEqLabel
                else -> {
                    val custom = snapshot.customEqProfile
                    val match = custom?.let { EqSuggestions.matchingSuggestion(it)?.name }
                    if (match != null) "Custom ($match)" else "Custom"
                }
            }
            add(
                SoundItem.Navigation(
                    icon = Icons.Rounded.Equalizer,
                    title = "Equalizer",
                    summary = eqSummary,
                    enabled = !busy && snapshot.currentEqId != null &&
                        !snapshot.readFailed(HeadsetProtocol.EQ_STATUS_COMMAND),
                    onClick = onOpenEqualizer,
                ),
            )
        }

        if (snapshot.hasDynamicBass) {
            add(
                SoundItem.Navigation(
                    icon = Icons.Rounded.Speaker,
                    title = "Dynamic bass",
                    summary = dynamicBassSummary(dynamicBass, writableBassBands),
                    enabled = !busy,
                    onClick = onOpenDynamicBass,
                ),
            )
        }

        snapshot.switches.firstOrNull { it.id == 27 }?.let { sw ->
            add(
                SoundItem.Toggle(
                    icon = Icons.Rounded.SpatialAudio,
                    title = "Spatial audio",
                    checked = sw.enabled,
                    enabled = !busy,
                    onCheckedChange = { onSwitch(sw.id, it) },
                ),
            )
        }

        snapshot.highVolumeEnabled?.let { enabled ->
            add(
                SoundItem.Toggle(
                    icon = Icons.AutoMirrored.Rounded.VolumeUp,
                    title = "Volume enhancer",
                    checked = enabled,
                    enabled = !busy &&
                        snapshot.capabilities.supportsCommand(HeadsetProtocol.SET_HIGH_VOLUME_COMMAND),
                    onCheckedChange = onVolumeEnhancer,
                ),
            )
        }
    }

    if (items.isEmpty()) return

    val colors = dashboardSegmentedColors()
    val switchColors = dashboardSwitchSegmentedColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        items.forEachIndexed { index, item ->
            val shapes = ListItemDefaults.segmentedShapes(index = index, count = items.size)
            when (item) {
                is SoundItem.Navigation -> SegmentedListItem(
                    onClick = item.onClick,
                    shapes = shapes,
                    colors = colors,
                    enabled = item.enabled,
                    modifier = Modifier.semantics {
                        if (!item.enabled) stateDescription = "Unavailable"
                    },
                    leadingContent = {
                        Icon(
                            item.icon,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                    },
                    supportingContent = {
                        Text(item.summary, style = MaterialTheme.typography.bodySmall)
                    },
                    trailingContent = {
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = "Open ${item.title}",
                        )
                    },
                ) {
                    Text(item.title, style = MaterialTheme.typography.bodyLargeEmphasized)
                }

                is SoundItem.Toggle -> SegmentedListItem(
                    checked = item.checked,
                    onCheckedChange = item.onCheckedChange,
                    shapes = shapes.copy(selectedShape = shapes.shape),
                    colors = switchColors,
                    enabled = item.enabled,
                    modifier = Modifier.semantics {
                        if (!item.enabled) stateDescription = "Unavailable"
                    },
                    leadingContent = {
                        Icon(
                            item.icon,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                    },
                    supportingContent = {
                        Text(
                            if (item.checked) "On" else "Off",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    trailingContent = {
                        ExpressiveSwitch(
                            checked = item.checked,
                            onCheckedChange = null,
                            enabled = item.enabled,
                        )
                    },
                ) {
                    Text(item.title, style = MaterialTheme.typography.bodyLargeEmphasized)
                }
            }
        }
    }
}

private sealed interface SettingsItem {
    data class DualDevice(val sw: SwitchCatalog.LogicalSwitch) : SettingsItem
    data class SwitchItem(
        val icon: ImageVector,
        val title: String,
        val sw: SwitchCatalog.LogicalSwitch,
    ) : SettingsItem

    data object FindNeckband : SettingsItem
    data object ButtonControls : SettingsItem
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
    val items = buildList<SettingsItem> {
        dualSwitch?.let { add(SettingsItem.DualDevice(it)) }
        gameSwitch?.let { add(SettingsItem.SwitchItem(Icons.Rounded.SportsEsports, "Game mode", it)) }
        if (snapshot.capabilities.supportsCommand(HeadsetProtocol.SET_FIND_MODE_COMMAND)) {
            add(SettingsItem.FindNeckband)
        }
        if (snapshot.buttonMappings.isNotEmpty()) {
            add(SettingsItem.ButtonControls)
        }
        autoAnswer?.let { add(SettingsItem.SwitchItem(Icons.Rounded.Headphones, "Auto answer", it)) }
    }

    if (items.isEmpty()) return

    val colors = dashboardSegmentedColors()
    val switchColors = dashboardSwitchSegmentedColors()
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntSize>()
    val effectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spatialSpec),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        items.forEachIndexed { index, item ->
            val shapes = ListItemDefaults.segmentedShapes(index = index, count = items.size)
            when (item) {
                is SettingsItem.DualDevice -> SegmentedListItem(
                    checked = item.sw.enabled,
                    onCheckedChange = { onSwitch(item.sw.id, it) },
                    shapes = shapes.copy(selectedShape = shapes.shape),
                    colors = switchColors,
                    enabled = !busy,
                    leadingContent = {
                        Icon(
                            Icons.Rounded.Devices,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                    },
                    supportingContent = {
                        Column {
                            Text(
                                if (item.sw.enabled) "On" else "Off",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            AnimatedVisibility(
                                visible = item.sw.enabled,
                                enter = fadeIn(effectsSpec) + expandVertically(spatialSpec),
                                exit = fadeOut(effectsSpec) + shrinkVertically(spatialSpec),
                            ) {
                                MultipointDetails(
                                    entries = snapshot.multipoint,
                                    available = !snapshot.readFailed(HeadsetProtocol.MULTI_CONNECT_COMMAND),
                                )
                            }
                        }
                    },
                    trailingContent = {
                        ExpressiveSwitch(
                            checked = item.sw.enabled,
                            onCheckedChange = null,
                            enabled = !busy,
                        )
                    },
                ) {
                    Text("Dual-device", style = MaterialTheme.typography.bodyLargeEmphasized)
                }

                is SettingsItem.SwitchItem -> SegmentedListItem(
                    checked = item.sw.enabled,
                    onCheckedChange = { onSwitch(item.sw.id, it) },
                    shapes = shapes.copy(selectedShape = shapes.shape),
                    colors = switchColors,
                    enabled = !busy,
                    leadingContent = {
                        Icon(
                            item.icon,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                    },
                    supportingContent = {
                        Text(
                            if (item.sw.enabled) "On" else "Off",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    trailingContent = {
                        ExpressiveSwitch(
                            checked = item.sw.enabled,
                            onCheckedChange = null,
                            enabled = !busy,
                        )
                    },
                ) {
                    Text(item.title, style = MaterialTheme.typography.bodyLargeEmphasized)
                }

                SettingsItem.FindNeckband -> SegmentedListItem(
                    onClick = { onFindMode(!snapshot.findModeActive) },
                    shapes = shapes,
                    colors = colors,
                    enabled = !busy,
                    leadingContent = {
                        Icon(
                            Icons.Rounded.RingVolume,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = if (snapshot.findModeActive) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.secondary
                            },
                        )
                    },
                    supportingContent = {
                        Text(
                            if (snapshot.findModeActive) "Playing a tone" else "Ready",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    trailingContent = {
                        FilledTonalToggleButton(
                            checked = snapshot.findModeActive,
                            onCheckedChange = onFindMode,
                            enabled = !busy,
                            colors = FilledTonalToggleButtonDefaults.colors(
                                checkedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                checkedContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            ),
                        ) {
                            Text(
                                if (snapshot.findModeActive) "Stop" else "Find",
                                style = MaterialTheme.typography.labelLargeEmphasized,
                            )
                        }
                    },
                ) {
                    Text("Find neckband", style = MaterialTheme.typography.bodyLargeEmphasized)
                }

                SettingsItem.ButtonControls -> SegmentedListItem(
                    onClick = onOpenButtons,
                    shapes = shapes,
                    colors = colors,
                    enabled = !busy,
                    leadingContent = {
                        Icon(
                            Icons.Rounded.TouchApp,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                    },
                    supportingContent = {
                        Text(
                            buttonSummary(snapshot.buttonMappings),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    trailingContent = {
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = "Open button controls",
                        )
                    },
                ) {
                    Text("Button controls", style = MaterialTheme.typography.bodyLargeEmphasized)
                }
            }
        }
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
            .padding(top = 8.dp),
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
                        Icons.Rounded.Bluetooth,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        entry.name.ifBlank { "Device" },
                        style = MaterialTheme.typography.bodySmallEmphasized,
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
    val segmentedColors = sheetSegmentedColors()

    SheetColumn {
        SheetTitle(title = "Noise control", subtitle = "ANC tuning and button cycling")

        Text(
            "ANC level",
            style = MaterialTheme.typography.titleMediumEmphasized,
            modifier = Modifier
                .padding(top = 4.dp, bottom = 8.dp)
                .semantics { heading() },
        )
        val ancLevels = listOf(
            32 to "Smart",
            8 to "Max",
            16 to "Moderate",
            4 to "Mild",
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            ancLevels.forEachIndexed { index, (value, label) ->
                val selected = noiseValue == value
                SegmentedListItem(
                    selected = selected,
                    onClick = { if (noiseValue != value) onNoiseValue(value) },
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = ancLevels.size),
                    colors = segmentedColors,
                    enabled = enabled,
                    trailingContent = if (selected) {
                        {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = "Selected",
                            )
                        }
                    } else null,
                ) {
                    Text(
                        label,
                        style = if (selected) {
                            MaterialTheme.typography.bodyLargeEmphasized
                        } else {
                            MaterialTheme.typography.bodyLarge
                        },
                    )
                }
            }
        }

        windNoise?.let { sw ->
            Spacer(Modifier.height(16.dp))
            val windShapes = ListItemDefaults.segmentedShapes(index = 0, count = 1)
            SegmentedListItem(
                checked = sw.enabled,
                onCheckedChange = { onToggle(sw.id, it) },
                shapes = windShapes.copy(selectedShape = windShapes.shape),
                colors = sheetSwitchSegmentedColors(),
                enabled = enabled,
                leadingContent = {
                    Icon(
                        Icons.Rounded.Air,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                },
                supportingContent = {
                    Text(
                        if (sw.enabled) "On" else "Off",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                trailingContent = {
                    ExpressiveSwitch(
                        checked = sw.enabled,
                        onCheckedChange = null,
                        enabled = enabled,
                    )
                },
            ) {
                Text("Smart de-wind", style = MaterialTheme.typography.bodyLargeEmphasized)
            }
        }

        if (cycleValue != null) {
            Spacer(Modifier.height(20.dp))
            Text(
                "Button cycling",
                style = MaterialTheme.typography.titleMediumEmphasized,
                modifier = Modifier.semantics { heading() },
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

            val cycleOptions = listOf(
                Triple("ANC", hasAnc) {
                    if (hasAnc && totalActive > 2) {
                        onNoiseCycle(10)
                    } else if (!hasAnc) {
                        onNoiseCycle(11)
                    }
                },
                Triple("Ambient", hasTransparency) {
                    if (hasTransparency && totalActive > 2) {
                        onNoiseCycle(3)
                    } else if (!hasTransparency) {
                        onNoiseCycle(11)
                    }
                },
                Triple("Off", hasOff) {
                    if (hasOff && totalActive > 2) {
                        onNoiseCycle(9)
                    } else if (!hasOff) {
                        onNoiseCycle(11)
                    }
                },
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                modifier = Modifier.fillMaxWidth(),
            ) {
                cycleOptions.forEachIndexed { index, (label, checked, onToggleCycle) ->
                    ToggleButton(
                        checked = checked,
                        onCheckedChange = { onToggleCycle() },
                        enabled = enabled,
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            cycleOptions.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                        colors = ToggleButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (totalActive <= 2) "At least 2 modes must be selected" else "Cycles between selected modes",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            IconButton(
                onClick = it,
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.width(4.dp))
        }
        Column {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmallEmphasized,
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

private data class EqProfileOption(
    val id: Int,
    val label: String,
    val summary: String? = null,
    val selected: Boolean,
    val enabled: Boolean,
)

@Composable
private fun EqualizerSheet(
    snapshot: HeadsetSnapshot,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    onApplyCustomEq: (HeadsetCapabilityDiscovery.EqProfile) -> Unit,
) {
    val custom = snapshot.customEqProfile
    val segmentedColors = sheetSegmentedColors()

    SheetColumn {
        SheetTitle(title = "Equalizer", subtitle = "Sound profiles & 6-band custom")

        Text(
            "Sound profiles",
            style = MaterialTheme.typography.titleMediumEmphasized,
            modifier = Modifier
                .padding(top = 4.dp, bottom = 8.dp)
                .semantics { heading() },
        )

        val eqReadable = !snapshot.readFailed(HeadsetProtocol.EQ_STATUS_COMMAND)
        val profileOptions = buildList {
            listOf(
                0 to "Nature Balance",
                1 to "Bass Boost",
                2 to "Clear Vocals",
                3 to "Clear Bass",
            ).forEach { (id, label) ->
                add(
                    EqProfileOption(
                        id = id,
                        label = label,
                        selected = snapshot.currentEqId == id,
                        enabled = enabled && snapshot.currentEqId != null && eqReadable,
                    ),
                )
            }
            if (snapshot.canEditCustomEq && custom != null) {
                val isCustomSelected = snapshot.currentEqId == custom.id || snapshot.currentEqId == 4
                val matchingSuggestion = EqSuggestions.matchingSuggestion(custom)?.name
                val customSummary = matchingSuggestion ?: "${custom.frequencies.size}-band custom"
                add(
                    EqProfileOption(
                        id = custom.id,
                        label = "Custom",
                        summary = customSummary,
                        selected = isCustomSelected,
                        enabled = enabled && eqReadable,
                    ),
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            profileOptions.forEachIndexed { index, option ->
                SegmentedListItem(
                    selected = option.selected,
                    onClick = { onSelect(option.id) },
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = profileOptions.size),
                    colors = segmentedColors,
                    enabled = option.enabled,
                    supportingContent = option.summary?.let { summary ->
                        { Text(summary, style = MaterialTheme.typography.bodySmall) }
                    },
                    trailingContent = if (option.selected) {
                        {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = "Selected",
                            )
                        }
                    } else null,
                ) {
                    Text(
                        option.label,
                        style = if (option.selected) {
                            MaterialTheme.typography.bodyLargeEmphasized
                        } else {
                            MaterialTheme.typography.bodyLarge
                        },
                    )
                }
            }
        }

        if (snapshot.canEditCustomEq && custom != null) {
            Spacer(Modifier.height(20.dp))

            Text(
                "Custom 6-band EQ",
                style = MaterialTheme.typography.titleMediumEmphasized,
                modifier = Modifier.semantics { heading() },
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
                    val selected = selectedPresetId == suggestion.id
                    FilterChip(
                        selected = selected,
                        onClick = {
                            selectedPresetId = suggestion.id
                            bands = EqSuggestions.apply(custom, suggestion).dbValues
                        },
                        label = { Text(suggestion.name) },
                        shapes = FilterChipDefaults.shapes(),
                        enabled = enabled,
                        leadingIcon = if (selected) {
                            {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.tonalFilterChipColors(),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            custom.frequencies.forEachIndexed { index, frequency ->
                val value = bands.getOrElse(index) { 0 }
                EqBandSlider(
                    frequencyHz = frequency,
                    value = value,
                    minValue = custom.minValue,
                    maxValue = custom.maxValue,
                    enabled = enabled,
                    onValueChange = { newInt ->
                        selectedPresetId = null
                        val updated = bands.toMutableList()
                        if (index < updated.size) {
                            updated[index] = newInt
                            bands = updated
                        }
                    },
                )
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    onApplyCustomEq(custom.copy(dbValues = bands, selected = true))
                },
                shapes = ButtonDefaults.shapes(),
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
private fun EqBandSlider(
    frequencyHz: Int,
    value: Int,
    minValue: Int,
    maxValue: Int,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
) {
    val sliderState = rememberSliderState(
        value = value.toFloat(),
        steps = (maxValue - minValue - 1).coerceAtLeast(0),
        trackRange = minValue.toFloat()..maxValue.toFloat(),
    )
    LaunchedEffect(value) {
        if (sliderState.value.roundToInt() != value) {
            sliderState.value = value.toFloat()
        }
    }
    val displayValue = sliderState.value.roundToInt()
    val dbText = if (displayValue > 0) "+$displayValue dB" else "$displayValue dB"

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            formatFreq(frequencyHz),
            style = MaterialTheme.typography.bodyLargeEmphasized,
        )
        Text(
            dbText,
            style = MaterialTheme.typography.labelLargeEmphasized,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Slider(
        state = sliderState,
        onValueChange = { newValue ->
            sliderState.value = newValue
            onValueChange(newValue.roundToInt())
        },
        enabled = enabled,
    )
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
    val switchColors = sheetSwitchSegmentedColors()

    SheetColumn {
        SheetTitle(title = "Dynamic bass", subtitle = "Low-frequency enhancement")

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            shape = MaterialTheme.shapes.large,
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
            val switchShapes = ListItemDefaults.segmentedShapes(index = 0, count = 1)
            SegmentedListItem(
                checked = sw.enabled,
                onCheckedChange = { onToggle(sw.id, it) },
                shapes = switchShapes.copy(selectedShape = switchShapes.shape),
                colors = switchColors,
                enabled = enabled,
                leadingContent = {
                    Icon(
                        Icons.Rounded.Speaker,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                },
                supportingContent = {
                    Text(
                        if (sw.enabled) "On" else "Off",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                trailingContent = {
                    ExpressiveSwitch(
                        checked = sw.enabled,
                        onCheckedChange = null,
                        enabled = enabled,
                    )
                },
            ) {
                Text("Dynamic bass", style = MaterialTheme.typography.bodyLargeEmphasized)
            }
            if (bands.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
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
    val sliderState = rememberSliderState(
        value = band.currentValue.toFloat(),
        steps = (band.maxValue - band.minValue - 1).coerceAtLeast(0),
        trackRange = band.minValue.toFloat()..band.maxValue.toFloat(),
    )
    LaunchedEffect(band.type, band.currentValue) {
        if (sliderState.value.roundToInt() != band.currentValue) {
            sliderState.value = band.currentValue.toFloat()
        }
    }
    val pendingValue = sliderState.value.roundToInt()
    val (label, description) = when (band.type) {
        1 -> "Low" to "Rumble & sub-bass"
        2 -> "Mid" to "Punch & kick drums"
        3 -> "High" to "Bass guitar & lower warmth"
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
                Text(label, style = MaterialTheme.typography.bodyLargeEmphasized)
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
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            state = sliderState,
            onValueChange = { sliderState.value = it },
            onValueChangeFinished = {
                val targetValue = sliderState.value.roundToInt()
                if (enabled && targetValue != band.currentValue) {
                    onChange(band.copy(currentValue = targetValue))
                }
            },
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
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    val effectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

    AnimatedContent(
        targetState = liveSelection,
        contentKey = { it?.let { m -> "${m.deviceType}:${m.button}:${m.action}" } },
        transitionSpec = {
            if (targetState != null) {
                (slideInHorizontally(spatialSpec) { it / 3 } + fadeIn(effectsSpec)) togetherWith
                    (slideOutHorizontally(spatialSpec) { -it / 3 } + fadeOut(effectsSpec))
            } else {
                (slideInHorizontally(spatialSpec) { -it / 3 } + fadeIn(effectsSpec)) togetherWith
                    (slideOutHorizontally(spatialSpec) { it / 3 } + fadeOut(effectsSpec))
            }
        },
        label = "button_controls_sheet",
    ) { activeMapping ->
        if (activeMapping != null) {
            ButtonFunctionPicker(
                mapping = activeMapping,
                enabled = enabled,
                onBack = onBack,
                onSelect = { choice ->
                    onUpdate(activeMapping.copy(function = choice.function))
                    onBack()
                },
            )
        } else {
            // Only confirmed MFB slots are customizable; ANC cycling has its own controls in Noise adjustment.
            val mappings = snapshot.buttonMappings
                .filter { it.button == 1 && it.action in 1..4 }
                .distinctBy { it.action }
                .sortedBy { it.action }
            val segmentedColors = sheetSegmentedColors()

            SheetColumn {
                SheetTitle(title = "Button controls", subtitle = "Multifunction button")
                Text(
                    "Choose a gesture to change its function.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                ) {
                    mappings.forEachIndexed { index, mapping ->
                        SegmentedListItem(
                            onClick = { onSelectMapping(mapping) },
                            shapes = ListItemDefaults.segmentedShapes(index = index, count = mappings.size),
                            colors = segmentedColors,
                            enabled = enabled,
                            supportingContent = {
                                Text(
                                    buttonFunctionLabel(mapping.function),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                            trailingContent = {
                                Icon(
                                    Icons.Rounded.ChevronRight,
                                    contentDescription = "Choose assignment",
                                )
                            },
                        ) {
                            Text(
                                buttonAssignmentLabel(mapping),
                                style = MaterialTheme.typography.bodyLargeEmphasized,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ButtonFunctionPicker(
    mapping: HeadsetCapabilityDiscovery.KeyMapping,
    enabled: Boolean,
    onBack: () -> Unit,
    onSelect: (ButtonChoice) -> Unit,
) {
    val segmentedColors = sheetSegmentedColors()

    SheetColumn {
        SheetTitle(
            title = "Button controls",
            subtitle = HeadsetSemantics.buttonActionLabel(mapping.action),
            onBack = onBack,
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            buttonChoices.forEachIndexed { index, choice ->
                val selected = mapping.function == choice.function
                val itemEnabled = enabled && choice.supportedByCurrentController
                SegmentedListItem(
                    selected = selected,
                    onClick = { onSelect(choice) },
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = buttonChoices.size),
                    colors = segmentedColors,
                    enabled = itemEnabled,
                    leadingContent = {
                        Icon(
                            choice.icon,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                    },
                    trailingContent = if (selected) {
                        {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = "Selected",
                            )
                        }
                    } else null,
                ) {
                    Text(
                        choice.label,
                        style = if (selected) {
                            MaterialTheme.typography.bodyLargeEmphasized
                        } else {
                            MaterialTheme.typography.bodyLarge
                        },
                    )
                }
            }
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
