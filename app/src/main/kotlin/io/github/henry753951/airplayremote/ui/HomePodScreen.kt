package io.github.henry753951.airplayremote.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.henry753951.airplayremote.R
import io.github.henry753951.airplayremote.audiooutput.HomePodOutputService
import io.github.henry753951.airplayremote.audiooutput.SpeakerDiscovery
import io.github.henry753951.airplayremote.audiooutput.SpeakerOutputState
import io.github.henry753951.airplayremote.audiooutput.protocol.AirplayDevice
import io.github.henry753951.airplayremote.hasLocalNetwork
import kotlinx.coroutines.delay

// how long an empty list counts as "still searching" before it turns into "nothing found"
private const val SEARCH_GRACE_MS = 8_000L
private const val VOLUME_STEP = 5

/** Speakers are found with the system's mDNS (NSD) and shown by name only; addresses never reach the screen. */
@Composable
internal fun HomePodScreen(onLocalNetwork: () -> Unit) {
    val context = LocalContext.current
    val tv = isTv()
    val output by HomePodOutputService.state.collectAsState()
    var devices by remember { mutableStateOf(emptyList<AirplayDevice>()) }
    var scan by remember { mutableIntStateOf(0) }
    var searching by remember { mutableStateOf(true) }
    var scanFailed by remember { mutableStateOf(false) }
    var message by rememberSaveable { mutableIntStateOf(0) }   // a string resource, 0 for none
    var selectedName by rememberSaveable { mutableStateOf("") }
    var localNetwork by remember { mutableStateOf(context.hasLocalNetwork()) }
    // the grant happens in another screen; pick it up when this one comes back
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) localNetwork = context.hasLocalNetwork()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // The permission flow keeps its own copy of the speaker it was started for, so picking another
    // speaker while Android's dialogs are up cannot change where the audio goes.
    var requesting by rememberSaveable { mutableStateOf(false) }
    var pendingName by rememberSaveable { mutableStateOf("") }
    var pendingHost by rememberSaveable { mutableStateOf("") }
    var pendingPort by rememberSaveable { mutableIntStateOf(0) }
    fun pending(): AirplayDevice? = runCatching { AirplayDevice(pendingName, pendingHost, pendingPort) }.getOrNull()
    fun clearPending() {
        requesting = false
        pendingName = ""; pendingHost = ""; pendingPort = 0
    }

    val selected = devices.firstOrNull { it.name == selectedName }
    val captureSupported = Build.VERSION.SDK_INT >= 29

    val projection = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val target = pending()
        clearPending()
        if (result.resultCode == Activity.RESULT_OK && result.data != null && target != null) {
            if (!HomePodOutputService.start(context, target, result.data)) message = R.string.hp_failed_body
        } else message = R.string.hp_cancelled
    }
    fun launchProjection() {
        try {
            projection.launch(context.getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
        } catch (_: RuntimeException) {
            clearPending()
            message = R.string.hp_capture_unavailable
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && captureSupported) launchProjection()
        else {
            clearPending()
            message = R.string.hp_permission_denied
        }
    }

    DisposableEffect(scan, localNetwork) {
        scanFailed = false
        val discovery = SpeakerDiscovery(context,
            onDevices = { devices = it }, onError = { scanFailed = true }
        )
        if (localNetwork) discovery.start()
        onDispose { discovery.close() }
    }
    LaunchedEffect(scan, localNetwork) {
        searching = true
        delay(SEARCH_GRACE_MS)
        searching = false
    }

    fun rescan() {
        localNetwork = context.hasLocalNetwork()
        if (!localNetwork) {
            onLocalNetwork()
            return
        }
        message = 0
        devices = emptyList()
        scan++
    }
    fun playTone() {
        val device = selected ?: return
        if (requesting) return
        message = 0
        if (!context.hasLocalNetwork()) onLocalNetwork()
        else if (!HomePodOutputService.start(context, device)) message = R.string.hp_failed_body
    }
    fun sendAppAudio() {
        val device = selected ?: return
        if (requesting || !captureSupported) return
        message = 0
        if (!context.hasLocalNetwork()) {
            onLocalNetwork()
            return
        }
        requesting = true
        pendingName = device.name; pendingHost = device.host; pendingPort = device.port
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.RECORD_AUDIO)
        } else launchProjection()
    }

    val colors = homePodColors(tv)
    val listState = ListState(
        localNetwork = localNetwork, scanFailed = scanFailed, searching = searching,
        devices = devices, selectedName = selectedName, activeTarget = output.target.takeIf { output.active }
    )
    val panel = PanelState(
        output = output, selected = selected, selectedName = selectedName, requesting = requesting,
        captureSupported = captureSupported, message = message
    )
    val actions = PanelActions(
        start = ::sendAppAudio, tone = ::playTone,
        stop = { HomePodOutputService.stop(context) },
        volume = { HomePodOutputService.volume(context, it) }
    )

    // Focus on a TV: land somewhere useful when the page opens, and again whenever the focused control
    // goes away (a speaker disappears, a session starts or ends), but never pull focus from the viewer.
    var pageHasFocus by remember { mutableStateOf(false) }
    val refreshFocus = remember { FocusRequester() }
    val firstRowFocus = remember { FocusRequester() }
    val primaryFocus = remember { FocusRequester() }
    val canAct = output.active || selected != null
    LaunchedEffect(devices.isEmpty(), localNetwork, output.active, canAct, pageHasFocus) {
        if (!tv || pageHasFocus) return@LaunchedEffect
        when {
            canAct -> primaryFocus
            devices.isNotEmpty() -> firstRowFocus
            else -> refreshFocus
        }.requestFocusUntilLanded()
    }

    BoxWithConstraints(Modifier.fillMaxSize().onFocusChanged { pageHasFocus = it.hasFocus }.focusGroup()) {
        if (maxWidth >= 640.dp) {
            // TV and tablets: pick a speaker on the left, act on it on the right
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(0.42f).fillMaxHeight()) {
                    SpeakerListHeader(colors, ::rescan, onLocalNetwork, listState, refreshFocus)
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        SpeakerList(colors, listState, lazy = true, firstRowFocus) { selectedName = it; message = 0 }
                    }
                }
                Spacer(Modifier.width(36.dp))
                Column(
                    Modifier.weight(0.58f).fillMaxHeight().verticalScroll(rememberScrollState())
                ) {
                    ControlPanel(colors, panel, actions, primaryFocus)
                }
            }
        } else {
            // phones: one scrolling column, list first
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                SpeakerListHeader(colors, ::rescan, onLocalNetwork, listState, refreshFocus)
                Spacer(Modifier.height(8.dp))
                SpeakerList(colors, listState, lazy = false, firstRowFocus) { selectedName = it; message = 0 }
                Spacer(Modifier.height(24.dp))
                ControlPanel(colors, panel, actions, primaryFocus)
            }
        }
    }
}

// ---- state carried into the two halves ----

private class ListState(
    val localNetwork: Boolean,
    val scanFailed: Boolean,
    val searching: Boolean,
    val devices: List<AirplayDevice>,
    val selectedName: String,
    val activeTarget: String?
)

private class PanelState(
    val output: SpeakerOutputState,
    val selected: AirplayDevice?,
    val selectedName: String,
    val requesting: Boolean,
    val captureSupported: Boolean,
    @StringRes val message: Int
)

private class PanelActions(
    val start: () -> Unit,
    val tone: () -> Unit,
    val stop: () -> Unit,
    val volume: (Int) -> Unit
)

// ---- colours: the TV's grey and white on a television, the system theme on a phone ----

private class HomePodColors(
    val text: Color,
    val muted: Color,
    val tile: Color,
    val quiet: Color,
    val focusBg: Color,
    val focusFg: Color,
    val error: Color,
    val accent: Color
)

@Composable
private fun homePodColors(tv: Boolean): HomePodColors {
    if (tv) {
        return HomePodColors(
            text = TvText, muted = TvMuted, tile = TvTile, quiet = Color(0x14FFFFFF),
            focusBg = TvText, focusFg = TvInk, error = Color(0xFFFF7A6E), accent = Color(0xFF8FE3A8)
        )
    }
    val c = MaterialTheme.colorScheme
    return HomePodColors(
        text = c.onSurface, muted = c.onSurfaceVariant, tile = c.surfaceVariant, quiet = c.surfaceVariant,
        focusBg = c.primary, focusFg = c.onPrimary, error = c.error, accent = c.primary
    )
}

// ---- left: finding and picking a speaker ----

@Composable
private fun SpeakerListHeader(
    colors: HomePodColors,
    onRescan: () -> Unit,
    onLocalNetwork: () -> Unit,
    state: ListState,
    refreshFocus: FocusRequester
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.hp_speakers), color = colors.muted, fontSize = 16.sp,
            modifier = Modifier.weight(1f)
        )
        HpButton(
            stringResource(if (state.localNetwork) R.string.hp_refresh else R.string.hp_permission_action),
            colors, primary = false, compact = true,
            onClick = if (state.localNetwork) onRescan else onLocalNetwork,
            modifier = Modifier.focusRequester(refreshFocus)
        )
    }
}

@Composable
private fun SpeakerList(
    colors: HomePodColors,
    state: ListState,
    lazy: Boolean,
    firstRowFocus: FocusRequester,
    onSelect: (String) -> Unit
) {
    when {
        !state.localNetwork -> InfoBlock(colors, R.string.hp_permission_title, listOf(R.string.hp_permission_body))
        state.scanFailed -> InfoBlock(colors, R.string.hp_scan_error_title, listOf(R.string.hp_scan_error_body))
        state.devices.isEmpty() && state.searching -> SearchingLine(colors)
        state.devices.isEmpty() -> InfoBlock(
            colors, R.string.hp_none_title,
            listOf(R.string.hp_none_power, R.string.hp_none_network, R.string.hp_none_home_app)
        )
        lazy -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(state.devices, key = { it.name }) { device ->
                val first = device == state.devices.first()
                SpeakerRow(colors, device.name, state, if (first) Modifier.focusRequester(firstRowFocus) else Modifier) {
                    onSelect(device.name)
                }
            }
            if (state.searching) item { SearchingLine(colors, more = true) }
        }
        else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            state.devices.forEachIndexed { i, device ->
                SpeakerRow(colors, device.name, state, if (i == 0) Modifier.focusRequester(firstRowFocus) else Modifier) {
                    onSelect(device.name)
                }
            }
            if (state.searching) SearchingLine(colors, more = true)
        }
    }
}

@Composable
private fun SpeakerRow(colors: HomePodColors, name: String, state: ListState, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val chosen = name == state.selectedName
    val sending = name == state.activeTarget
    val bg by animateColorAsState(if (focused) colors.focusBg else if (chosen) colors.quiet else Color.Transparent, tween(FOCUS_MS), label = "rowBg")
    val fg by animateColorAsState(if (focused) colors.focusFg else colors.text, tween(FOCUS_MS), label = "rowFg")
    val sub = if (focused) colors.focusFg.copy(alpha = 0.65f) else colors.muted
    val status = when {
        sending -> stringResource(R.string.hp_row_sending)
        chosen -> stringResource(R.string.hp_row_selected)
        else -> null
    }
    Row(
        modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = if (status != null) "$name, $status" else name }
            .heightIn(min = 60.dp)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, color = fg, fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (status != null) Text(status, color = if (sending && !focused) colors.accent else sub, fontSize = 14.sp, maxLines = 1)
        }
        if (chosen) Text("✓", color = fg, fontSize = 20.sp, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun SearchingLine(colors: HomePodColors, more: Boolean = false) {
    Text(
        stringResource(if (more) R.string.hp_still_searching else R.string.hp_searching),
        color = colors.muted, fontSize = 16.sp, lineHeight = 24.sp,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
    )
}

@Composable
private fun InfoBlock(colors: HomePodColors, @StringRes title: Int, lines: List<Int>) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.quiet)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(stringResource(title), color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        lines.forEach { line ->
            Spacer(Modifier.height(8.dp))
            Row {
                if (lines.size > 1) Text("•", color = colors.muted, fontSize = 15.sp, modifier = Modifier.width(16.dp))
                Text(stringResource(line), color = colors.muted, fontSize = 15.sp, lineHeight = 22.sp)
            }
        }
    }
}

// ---- right: what is selected, what it is doing, and the controls ----

@Composable
private fun ControlPanel(colors: HomePodColors, state: PanelState, actions: PanelActions, primaryFocus: FocusRequester) {
    val output = state.output
    Column {
        when {
            output.active -> {
                Text(stringResource(R.string.hp_sending_to), color = colors.muted, fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    output.target.ifEmpty { state.selectedName }, color = colors.text, fontSize = 30.sp,
                    fontWeight = FontWeight.Light, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
            state.selected != null -> {
                Text(stringResource(R.string.hp_row_selected), color = colors.muted, fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    state.selected.name, color = colors.text, fontSize = 30.sp,
                    fontWeight = FontWeight.Light, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
            state.selectedName.isNotEmpty() -> {
                Text(state.selectedName, color = colors.text, fontSize = 26.sp, fontWeight = FontWeight.Light,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.hp_unavailable), color = colors.muted, fontSize = 16.sp, lineHeight = 24.sp)
            }
            else -> Text(stringResource(R.string.hp_pick_prompt), color = colors.muted, fontSize = 22.sp,
                fontWeight = FontWeight.Light, lineHeight = 30.sp)
        }
        Spacer(Modifier.height(14.dp))
        StatusLine(colors, state)
        Spacer(Modifier.height(22.dp))

        if (output.active) {
            HpButton(stringResource(R.string.hp_stop), colors, primary = true, onClick = actions.stop,
                modifier = Modifier.focusRequester(primaryFocus))
            Spacer(Modifier.height(22.dp))
            VolumeRow(colors, output.volume, actions.volume)
        } else if (state.selected != null) {
            if (state.captureSupported) {
                // Android's own dialogs say "record audio"; say plainly what that means here, before they appear
                Text(stringResource(R.string.hp_start_note), color = colors.muted, fontSize = 15.sp, lineHeight = 22.sp)
                Spacer(Modifier.height(14.dp))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.captureSupported) {
                    HpButton(stringResource(R.string.hp_start), colors, primary = true, onClick = actions.start,
                        modifier = Modifier.focusRequester(primaryFocus))
                    HpButton(stringResource(R.string.hp_test), colors, primary = false, onClick = actions.tone)
                } else {
                    HpButton(stringResource(R.string.hp_test), colors, primary = false, onClick = actions.tone,
                        modifier = Modifier.focusRequester(primaryFocus))
                }
            }
            if (!state.captureSupported) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.hp_needs_android10), color = colors.muted, fontSize = 15.sp, lineHeight = 22.sp)
            }
        }

        if (state.message != 0) {
            Spacer(Modifier.height(16.dp))
            Text(stringResource(state.message), color = colors.error, fontSize = 15.sp, lineHeight = 22.sp)
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.hp_limits), color = colors.muted, fontSize = 14.sp, lineHeight = 21.sp)
    }
}

@Composable
private fun StatusLine(colors: HomePodColors, state: PanelState) {
    val output = state.output
    val line = when {
        state.requesting -> R.string.hp_status_waiting to colors.muted
        output.error -> R.string.hp_failed_body to colors.error
        output.streaming && output.tone -> R.string.hp_status_tone to colors.accent
        output.streaming -> R.string.hp_status_audio to colors.accent
        output.active -> R.string.hp_status_connecting to colors.muted
        state.selected != null -> R.string.hp_status_ready to colors.muted
        else -> 0 to colors.muted
    }
    val text = line.first
    val color = line.second
    if (text == 0) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Text(stringResource(text), color = if (color == colors.error) colors.error else colors.text, fontSize = 17.sp, lineHeight = 24.sp)
    }
}

@Composable
private fun VolumeRow(colors: HomePodColors, volume: Int, onVolume: (Int) -> Unit) {
    Text(stringResource(R.string.hp_volume), color = colors.muted, fontSize = 15.sp)
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        HpButton("−", colors, primary = false, compact = true, enabled = volume > 0,
            description = stringResource(R.string.hp_quieter),
            onClick = { onVolume((volume - VOLUME_STEP).coerceAtLeast(0)) })
        Spacer(Modifier.width(14.dp))
        Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(colors.quiet)) {
            Box(Modifier.fillMaxWidth(volume.coerceIn(0, 100) / 100f).fillMaxHeight().clip(CircleShape).background(colors.text))
        }
        Spacer(Modifier.width(14.dp))
        Text(stringResource(R.string.hp_volume_value, volume), color = colors.text, fontSize = 17.sp,
            modifier = Modifier.widthIn(min = 52.dp))
        Spacer(Modifier.width(8.dp))
        HpButton("+", colors, primary = false, compact = true, enabled = volume < 100,
            description = stringResource(R.string.hp_louder),
            onClick = { onVolume((volume + VOLUME_STEP).coerceAtMost(100)) })
    }
}

// one button shape for the whole page: white when focused, as everywhere else on the TV
@Composable
private fun HpButton(
    text: String,
    colors: HomePodColors,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    enabled: Boolean = true,
    description: String? = null
) {
    var focused by remember { mutableStateOf(false) }
    val rest = if (primary) colors.text.copy(alpha = 0.18f) else colors.quiet
    val bg by animateColorAsState(if (focused) colors.focusBg else rest, tween(FOCUS_MS), label = "btnBg")
    val fg by animateColorAsState(if (focused) colors.focusFg else colors.text, tween(FOCUS_MS), label = "btnFg")
    Box(
        modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(CircleShape)
            .background(bg)
            // stays focusable when it has nothing to do, so the focus never vanishes from under the viewer
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                if (enabled) onClick()
            }
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .heightIn(min = if (compact) 44.dp else 52.dp)
            .widthIn(min = if (compact) 44.dp else 0.dp)
            .padding(horizontal = if (compact) 16.dp else 28.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, color = fg.copy(alpha = if (enabled) 1f else 0.4f),
            fontSize = if (compact) 16.sp else 18.sp, fontWeight = FontWeight.Medium, maxLines = 1
        )
    }
}
