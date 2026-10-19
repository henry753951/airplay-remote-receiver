package io.github.jqssun.airplay.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.corvofeng.fakeatv.AtvAccessibilityService
import com.corvofeng.fakeatv.LocalAdbDriver
import com.corvofeng.fakeatv.RemoteState
import io.github.jqssun.airplay.Prefs
import io.github.jqssun.airplay.R
import io.github.jqssun.airplay.service.AirPlayService.ServerState
import io.github.jqssun.airplay.viewmodel.LocalNetworkAccess
import io.github.jqssun.airplay.viewmodel.MainViewModel
import kotlinx.coroutines.delay

// TV receiver look: near-black greys, white type, one white highlight for focus
internal val TvBg = Color(0xFF0E0E10)
internal val TvText = Color(0xFFF5F5F7)
private val TvMuted = Color(0xFF9C9CA3)
private val TvDim = Color(0xFF5A5A60)
private val TvTile = Color(0xFF1D1D20)
private val TvInk = Color(0xFF111113)
private val TvError = Color(0xFFFF7A6E)
private val TvBackdrop = Brush.verticalGradient(listOf(Color(0xFF1B1B1E), TvBg))

// short and quick: motion confirms a key press, it never stands in for one
private const val FOCUS_MS = 110

// every page knows where Back goes; parents are declared before children
internal enum class TvPage(val parent: TvPage?) {
    HOME(null),
    SETTINGS(HOME),
    GENERAL(SETTINGS), VIDEO(SETTINGS), AUDIO(SETTINGS), REMOTE(SETTINGS), POWER(SETTINGS), DIAGNOSTICS(SETTINGS),
    GRAYSCALE(VIDEO),
    LOGS(DIAGNOSTICS), ADVANCED(DIAGNOSTICS)
}

@Composable
internal fun TvRouter(
    viewModel: MainViewModel,
    page: TvPage,
    onPage: (TvPage) -> Unit,
    idle: Boolean,
    onLocalNetwork: () -> Unit,
    overview: @Composable (onSettings: () -> Unit, focusSettings: Boolean) -> Unit
) {
    // focused row per page, so Back lands where the user left
    val focusMemory = remember { mutableStateMapOf<TvPage, Int>() }
    var cameFrom by remember { mutableStateOf<TvPage?>(null) }
    val go: (TvPage) -> Unit = { cameFrom = page; onPage(it) }
    val back: () -> Unit = { page.parent?.let(go) }
    val memo = { p: TvPage -> focusMemory[p] ?: 0 }
    val saveFocus = { p: TvPage -> { i: Int -> focusMemory[p] = i } }

    key(page) {
        when (page) {
            TvPage.HOME -> if (idle) {
                TvIdleScreen(viewModel, onLocalNetwork, onSettings = { go(TvPage.SETTINGS) },
                    focusSettings = cameFrom == TvPage.SETTINGS)
            } else {
                overview({ go(TvPage.SETTINGS) }, cameFrom == TvPage.SETTINGS)
            }
            TvPage.SETTINGS -> TvRootSettings(viewModel, memo(page), saveFocus(page), back, go)
            TvPage.GENERAL, TvPage.VIDEO, TvPage.AUDIO, TvPage.REMOTE, TvPage.POWER, TvPage.DIAGNOSTICS ->
                TvCategory(viewModel, page, memo(page), saveFocus(page), back, go)
            TvPage.GRAYSCALE -> TvGrayscaleScreen(back)
            TvPage.ADVANCED -> TvSubPage(stringResource(R.string.tv_advanced), back) { SettingsScreen(viewModel) }
            TvPage.LOGS -> TvSubPage(stringResource(R.string.tab_logs), back) { LogsScreen(viewModel) }
        }
    }
}

@Composable
private fun AirPlayMark(height: Dp, tint: Color) {
    // drawable is 96x80
    Icon(painterResource(R.drawable.airplay_mark), null, Modifier.size(height * 1.2f, height), tint = tint)
}

@Composable
private fun TvPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var focused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(if (focused) TvText else Color(0x1FFFFFFF), tween(FOCUS_MS), label = "pillBg")
    val fg by animateColorAsState(if (focused) TvInk else TvText, tween(FOCUS_MS), label = "pillFg")
    Box(
        modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(CircleShape)
            .background(bg)
            .clickable(
                remember { MutableInteractionSource() }, indication = null, enabled = enabled,
                role = Role.Button, onClick = onClick
            )
            .heightIn(min = 48.dp)
            .padding(horizontal = 30.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, color = fg.copy(alpha = if (enabled) 1f else 0.4f),
            fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1
        )
    }
}

// ---- service status ----

private enum class TvHealth { READY, BUSY, OFF, ERROR }

private class TvStatus(val health: TvHealth, @StringRes val label: Int)

@Composable
private fun airPlayStatus(viewModel: MainViewModel): TvStatus {
    val state by viewModel.serverState.collectAsState()
    val localNetwork by viewModel.localNetwork.collectAsState()
    val connections by viewModel.connectionCount.collectAsState()
    val mirroring by viewModel.mirroringActive.collectAsState()
    val audio by viewModel.audioOnly.collectAsState()
    val video by viewModel.videoPlaybackActive.collectAsState()
    return when {
        state != ServerState.RUNNING && localNetwork != LocalNetworkAccess.GRANTED ->
            TvStatus(TvHealth.ERROR, R.string.tv_state_needs_permission)
        state == ServerState.ERROR -> TvStatus(TvHealth.ERROR, R.string.tv_state_error)
        state == ServerState.STOPPED -> TvStatus(TvHealth.OFF, R.string.tv_state_stopped)
        connections > 0 && (mirroring || audio || video) -> TvStatus(TvHealth.READY, R.string.tv_state_connected)
        connections > 0 -> TvStatus(TvHealth.BUSY, R.string.tv_state_connecting)
        else -> TvStatus(TvHealth.READY, R.string.tv_state_ready)
    }
}

@Composable
private fun remoteStatus(viewModel: MainViewModel): TvStatus {
    val enabled by viewModel.pref(Prefs.REMOTE_ENABLED).collectAsState()
    val status by RemoteState.status.collectAsState()
    val connected by RemoteState.connected.collectAsState()
    return when {
        !enabled -> TvStatus(TvHealth.OFF, R.string.tv_state_off)
        // connected tracks a live remote; paired is only the one-off pairing signal
        status == "running" && connected -> TvStatus(TvHealth.READY, R.string.tv_state_connected)
        status == "running" -> TvStatus(TvHealth.READY, R.string.tv_state_ready)
        status == "starting" -> TvStatus(TvHealth.BUSY, R.string.tv_state_starting)
        status == "waiting" -> TvStatus(TvHealth.BUSY, R.string.tv_state_waiting_network)
        status == "error" -> TvStatus(TvHealth.ERROR, R.string.tv_state_error)
        else -> TvStatus(TvHealth.OFF, R.string.tv_state_stopped)
    }
}

@Composable
private fun StatusDot(health: TvHealth) {
    val mod = Modifier.size(8.dp).clip(CircleShape)
    when (health) {
        TvHealth.READY -> Box(mod.background(TvText))
        TvHealth.BUSY -> Box(mod.background(TvMuted))
        TvHealth.ERROR -> Box(mod.background(TvError))
        TvHealth.OFF -> Box(mod.border(1.5.dp, TvDim, CircleShape))
    }
}

@Composable
private fun StatusItem(service: String, status: TvStatus) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(status.health)
        Spacer(Modifier.width(10.dp))
        Text(service, color = TvText, fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(status.label), color = TvMuted, fontSize = 16.sp)
    }
}

// ---- home ----

@Composable
internal fun TvIdleScreen(
    viewModel: MainViewModel,
    onLocalNetwork: () -> Unit,
    onSettings: () -> Unit,
    focusSettings: Boolean
) {
    val state by viewModel.serverState.collectAsState()
    val localNetwork by viewModel.localNetwork.collectAsState()
    val connections by viewModel.connectionCount.collectAsState()
    val serverName by viewModel.pref(Prefs.SERVER_NAME).collectAsState()
    val grant = state != ServerState.RUNNING && localNetwork != LocalNetworkAccess.GRANTED
    val needsStart = grant || state != ServerState.RUNNING
    val activity = LocalContext.current as? Activity
    val airPlay = airPlayStatus(viewModel)
    val remote = remoteStatus(viewModel)

    val startFocus = remember { FocusRequester() }
    val settingsFocus = remember { FocusRequester() }
    val homeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        when {
            needsStart -> startFocus
            focusSettings -> settingsFocus
            else -> homeFocus
        }.requestFocusUntilLanded()
    }

    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val reveal by animateFloatAsState(if (shown) 1f else 0f, tween(320, easing = FastOutSlowInEasing), label = "reveal")

    Box(Modifier.fillMaxSize().background(TvBackdrop).padding(horizontal = 56.dp, vertical = 40.dp)) {
        // quiet status bar: the TV's name on the left, each service on its own on the right
        Row(
            Modifier.fillMaxWidth().align(Alignment.TopStart).graphicsLayer { alpha = reveal },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                serverName, color = TvMuted, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            StatusItem(stringResource(R.string.tv_service_airplay), airPlay)
            Spacer(Modifier.width(32.dp))
            StatusItem(stringResource(R.string.tv_service_remote), remote)
        }
        Column(
            Modifier.align(Alignment.Center).graphicsLayer {
                alpha = reveal
                translationY = (1f - reveal) * 14.dp.toPx()
            },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AirPlayMark(56.dp, TvText)
            Spacer(Modifier.height(28.dp))
            Text(
                stringResource(
                    when {
                        grant -> R.string.local_network_desc
                        state == ServerState.RUNNING && connections > 0 -> R.string.waiting_for_playback
                        state == ServerState.RUNNING -> R.string.tv_status_ready
                        state == ServerState.ERROR -> R.string.error_starting_server
                        else -> R.string.server_stopped
                    }
                ),
                color = TvText, fontSize = 28.sp, fontWeight = FontWeight.Light, textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 720.dp)
            )
        }
        Row(
            Modifier.align(Alignment.BottomCenter).graphicsLayer { alpha = reveal },
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (needsStart) {
                TvPill(
                    stringResource(if (grant) R.string.btn_allow else R.string.btn_start),
                    onClick = if (grant) onLocalNetwork else viewModel::startServer,
                    modifier = Modifier.focusRequester(startFocus)
                )
            }
            TvPill(stringResource(R.string.tv_settings), onSettings, Modifier.focusRequester(settingsFocus))
            TvPill(
                stringResource(R.string.tv_home),
                onClick = { activity?.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)) },
                modifier = Modifier.focusRequester(homeFocus)
            )
        }
    }
}

// ---- settings building blocks ----

internal class TvItem(
    val title: String,
    val description: String,
    val checked: Boolean? = null,
    val value: String? = null,
    val link: Boolean = false,
    // a list of (key, label); selecting the row opens a chooser
    val choices: List<Pair<String, String>>? = null,
    val selected: String? = null,
    val onPick: ((String) -> Unit)? = null,
    val onClick: (() -> Unit)? = null
)

@Composable
private fun TvSettingsList(
    title: String,
    crumb: String?,
    items: List<TvItem>,
    focusIndex: Int,
    onFocusIndex: (Int) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    var choosing by remember { mutableStateOf<TvItem?>(null) }
    val requesters = remember(items.size) { List(items.size) { FocusRequester() } }
    val current = focusIndex.coerceIn(0, items.lastIndex)
    LaunchedEffect(Unit) { requesters[current].requestFocusUntilLanded() }

    Row(Modifier.fillMaxSize().background(TvBackdrop).padding(start = 64.dp, end = 56.dp)) {
        Column(Modifier.weight(0.4f).fillMaxHeight().padding(top = 56.dp, bottom = 56.dp, end = 48.dp)) {
            AirPlayMark(28.dp, TvMuted)
            Spacer(Modifier.height(18.dp))
            if (crumb != null) {
                Text(crumb, color = TvMuted, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
            }
            Text(title, color = TvText, fontSize = 32.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.weight(1f))
            // the focused row explains itself here, so rows never change height
            Crossfade(targetState = current, animationSpec = tween(140), label = "desc") { i ->
                Text(items[i].description, color = TvMuted, fontSize = 17.sp, lineHeight = 26.sp)
            }
        }
        Column(
            Modifier
                .weight(0.6f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 48.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items.forEachIndexed { i, item ->
                val click = when {
                    item.choices != null -> ({ choosing = item })
                    else -> item.onClick
                }
                TvSettingRow(item, click, Modifier.focusRequester(requesters[i])) { onFocusIndex(i) }
            }
        }
    }

    choosing?.let { item ->
        TvChoiceDialog(
            title = item.title,
            options = item.choices.orEmpty(),
            selected = item.selected,
            onPick = { key ->
                item.onPick?.invoke(key)
                choosing = null
            },
            onDismiss = { choosing = null }
        )
    }
}

@Composable
private fun TvSettingRow(item: TvItem, onClick: (() -> Unit)?, modifier: Modifier, onFocused: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(if (focused) TvText else Color.Transparent, tween(FOCUS_MS), label = "rowBg")
    val fg by animateColorAsState(if (focused) TvInk else TvText, tween(FOCUS_MS), label = "rowFg")
    val sub by animateColorAsState(if (focused) TvInk.copy(alpha = 0.6f) else TvMuted, tween(FOCUS_MS), label = "rowSub")
    val base = modifier
        .fillMaxWidth()
        .onFocusChanged {
            focused = it.isFocused
            if (it.isFocused) onFocused()
        }
        .clip(RoundedCornerShape(14.dp))
        .background(bg)
    val interactive = when {
        onClick == null -> base.focusable()
        item.checked != null -> base.clickable(
            remember { MutableInteractionSource() }, indication = null, role = Role.Switch, onClick = onClick
        )
        else -> base.clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick)
    }
    Row(
        interactive.semantics { contentDescription = item.title }.heightIn(min = 58.dp).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            item.title, color = fg, fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        val value = item.value ?: item.choices?.firstOrNull { it.first == item.selected }?.second ?: item.selected
        if (value != null) {
            Spacer(Modifier.width(20.dp))
            Text(
                value, color = sub, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 280.dp)
            )
        }
        when {
            item.checked != null -> {
                Spacer(Modifier.width(20.dp))
                TvSwitch(item.checked, focused)
            }
            item.link || item.choices != null -> {
                Spacer(Modifier.width(14.dp))
                Text("›", color = sub, fontSize = 22.sp)
            }
        }
    }
}

@Composable
private fun TvSwitch(checked: Boolean, onLightRow: Boolean) {
    val knobX by animateDpAsState(if (checked) 20.dp else 2.dp, tween(150), label = "knob")
    val onTrack = if (onLightRow) TvInk else TvText
    val offTrack = if (onLightRow) Color(0x33000000) else Color(0x33FFFFFF)
    val track by animateColorAsState(if (checked) onTrack else offTrack, tween(FOCUS_MS), label = "track")
    val knob by animateColorAsState(
        when {
            checked && onLightRow -> TvText
            checked -> TvInk
            onLightRow -> Color(0xFF8A8A90)
            else -> Color(0xFFB4B4BA)
        }, tween(FOCUS_MS), label = "knobColor"
    )
    Box(Modifier.size(42.dp, 24.dp).clip(CircleShape).background(track)) {
        Box(Modifier.offset(x = knobX, y = 2.dp).size(20.dp).clip(CircleShape).background(knob))
    }
}

@Composable
private fun TvChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val requesters = remember(options.size) { List(options.size) { FocusRequester() } }
    val start = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    LaunchedEffect(Unit) { if (requesters.isNotEmpty()) requesters[start].requestFocusUntilLanded() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(min = 420.dp, max = 560.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(TvTile)
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            Text(
                title, color = TvText, fontSize = 22.sp, fontWeight = FontWeight.Light,
                modifier = Modifier.padding(start = 24.dp, bottom = 12.dp)
            )
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                options.forEachIndexed { i, option ->
                    TvSettingRow(
                        TvItem(option.second, "", value = if (option.first == selected) "✓" else null),
                        onClick = { onPick(option.first) },
                        modifier = Modifier.focusRequester(requesters[i]),
                        onFocused = {}
                    )
                }
            }
        }
    }
}

// ---- pages ----

@Composable
private fun TvRootSettings(
    viewModel: MainViewModel,
    focusIndex: Int,
    onFocusIndex: (Int) -> Unit,
    onBack: () -> Unit,
    go: (TvPage) -> Unit
) {
    val remote = remoteStatus(viewModel)
    val items = listOf(
        TvItem(stringResource(R.string.tv_cat_general), stringResource(R.string.tv_cat_general_desc), link = true,
            onClick = { go(TvPage.GENERAL) }),
        TvItem(stringResource(R.string.tv_cat_video), stringResource(R.string.tv_cat_video_desc), link = true,
            onClick = { go(TvPage.VIDEO) }),
        TvItem(stringResource(R.string.tv_cat_audio), stringResource(R.string.tv_cat_audio_desc), link = true,
            onClick = { go(TvPage.AUDIO) }),
        TvItem(stringResource(R.string.tv_cat_remote), stringResource(R.string.tv_cat_remote_desc), link = true,
            value = stringResource(remote.label), onClick = { go(TvPage.REMOTE) }),
        TvItem(stringResource(R.string.tv_cat_power), stringResource(R.string.tv_cat_power_desc), link = true,
            onClick = { go(TvPage.POWER) }),
        TvItem(stringResource(R.string.tv_cat_diagnostics), stringResource(R.string.tv_cat_diagnostics_desc), link = true,
            onClick = { go(TvPage.DIAGNOSTICS) }),
    )
    TvSettingsList(stringResource(R.string.tv_settings), null, items, focusIndex, onFocusIndex, onBack)
}

@Composable
private fun TvCategory(
    viewModel: MainViewModel,
    page: TvPage,
    focusIndex: Int,
    onFocusIndex: (Int) -> Unit,
    onBack: () -> Unit,
    go: (TvPage) -> Unit
) {
    var renaming by remember { mutableStateOf(false) }
    val items = when (page) {
        TvPage.GENERAL -> generalItems(viewModel) { renaming = true }
        TvPage.VIDEO -> videoItems(viewModel, go)
        TvPage.AUDIO -> audioItems(viewModel)
        TvPage.REMOTE -> remoteItems(viewModel)
        TvPage.POWER -> powerItems(viewModel)
        else -> diagnosticsItems(viewModel, go)
    }
    val title = stringResource(
        when (page) {
            TvPage.GENERAL -> R.string.tv_cat_general
            TvPage.VIDEO -> R.string.tv_cat_video
            TvPage.AUDIO -> R.string.tv_cat_audio
            TvPage.REMOTE -> R.string.tv_cat_remote
            TvPage.POWER -> R.string.tv_cat_power
            else -> R.string.tv_cat_diagnostics
        }
    )
    TvSettingsList(title, stringResource(R.string.tv_settings), items, focusIndex, onFocusIndex, onBack)

    if (renaming) {
        val serverName by viewModel.pref(Prefs.SERVER_NAME).collectAsState()
        TvRenameDialog(
            initial = serverName,
            onSave = { viewModel.set(Prefs.SERVER_NAME, it); renaming = false },
            onDismiss = { renaming = false }
        )
    }
}

@Composable
private fun toggle(viewModel: MainViewModel, pref: io.github.jqssun.airplay.BoolPref, title: Int, desc: Int): TvItem {
    val on by viewModel.pref(pref).collectAsState()
    return TvItem(stringResource(title), stringResource(desc), checked = on, onClick = { viewModel.set(pref, !on) })
}

@Composable
private fun generalItems(viewModel: MainViewModel, onRename: () -> Unit): List<TvItem> {
    val serverName by viewModel.pref(Prefs.SERVER_NAME).collectAsState()
    val launchOnConnect by viewModel.pref(Prefs.LAUNCH_ON_CONNECT).collectAsState()
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasOverlayPermission by remember { mutableStateOf(canAutoLaunch(ctx)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hasOverlayPermission = canAutoLaunch(ctx)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val autoLaunch = launchOnConnect && hasOverlayPermission
    return listOf(
        TvItem(stringResource(R.string.tv_name), stringResource(R.string.tv_name_desc), value = serverName, onClick = onRename),
        toggle(viewModel, Prefs.REQUIRE_PIN, R.string.tv_require_pin, R.string.tv_require_pin_desc),
        TvItem(
            stringResource(R.string.setting_launch_on_connect),
            stringResource(
                if (launchOnConnect && !hasOverlayPermission) R.string.setting_launch_on_connect_no_permission
                else R.string.setting_launch_on_connect_desc
            ),
            checked = autoLaunch,
            onClick = {
                viewModel.set(Prefs.LAUNCH_ON_CONNECT, !autoLaunch)
                if (!autoLaunch && !canAutoLaunch(ctx)) openOverlaySettings(ctx)
            }
        ),
        toggle(viewModel, Prefs.ALLOW_NEW_CONN, R.string.setting_allow_new_conn, R.string.setting_allow_new_conn_desc),
    )
}

@Composable
private fun videoItems(viewModel: MainViewModel, go: (TvPage) -> Unit): List<TvItem> {
    val resolution by viewModel.pref(Prefs.RESOLUTION).collectAsState()
    val maxFps by viewModel.pref(Prefs.MAX_FPS).collectAsState()
    val colorRange by viewModel.pref(Prefs.COLOR_RANGE).collectAsState()
    val h265 by viewModel.pref(Prefs.H265).collectAsState()
    val auto = stringResource(R.string.chip_auto)
    // keep a custom value from the advanced page visible and selectable
    fun withCurrent(base: List<Pair<String, String>>, current: String) =
        if (base.any { it.first == current }) base else base + (current to current)
    val resolutions = withCurrent(
        listOf(
            Prefs.AUTO to auto, "1280x720" to "1280×720", "1920x1080" to "1920×1080", "3840x2160" to "3840×2160",
            Prefs.LANDSCAPE to stringResource(R.string.chip_device_landscape),
            Prefs.PORTRAIT to stringResource(R.string.chip_device_portrait),
        ), resolution
    )
    val fpsOptions = withCurrent(listOf("24", "30", "60").map { it to it }, maxFps.toString())
    return listOf(
        toggle(viewModel, Prefs.AUTO_FULLSCREEN, R.string.setting_auto_fullscreen, R.string.setting_auto_fullscreen_desc),
        TvItem(
            stringResource(R.string.setting_resolution), stringResource(R.string.setting_resolution_desc),
            choices = resolutions, selected = resolution, onPick = { viewModel.set(Prefs.RESOLUTION, it) }
        ),
        TvItem(
            stringResource(R.string.setting_max_fps), stringResource(R.string.setting_max_fps_desc),
            choices = fpsOptions, selected = maxFps.toString(),
            onPick = { v -> v.toIntOrNull()?.let { viewModel.set(Prefs.MAX_FPS, it) } }
        ),
        TvItem(
            stringResource(R.string.tv_color_range), stringResource(R.string.tv_color_range_desc),
            choices = listOf(
                Prefs.AUTO to auto,
                "full" to stringResource(R.string.tv_color_range_full),
                "limited" to stringResource(R.string.tv_color_range_limited),
            ),
            selected = colorRange, onPick = { viewModel.set(Prefs.COLOR_RANGE, it) }
        ),
        TvItem(
            stringResource(R.string.setting_h265), stringResource(R.string.setting_h265_desc),
            choices = listOf(
                Prefs.AUTO to auto,
                Prefs.ON to stringResource(R.string.chip_on),
                Prefs.OFF to stringResource(R.string.chip_off),
            ),
            selected = h265, onPick = { viewModel.set(Prefs.H265, it) }
        ),
        toggle(viewModel, Prefs.OVERSCANNED, R.string.setting_overscanned, R.string.setting_overscanned_desc),
        TvItem(
            stringResource(R.string.tv_grayscale), stringResource(R.string.tv_grayscale_desc),
            link = true, onClick = { go(TvPage.GRAYSCALE) }
        ),
    )
}

@Composable
private fun audioItems(viewModel: MainViewModel): List<TvItem> {
    val latency by viewModel.pref(Prefs.AUDIO_LATENCY_MS).collectAsState()
    val off = stringResource(R.string.chip_off)
    val steps = listOf(100, 200, 300, 500)
    val latencies = listOf("-1" to off) +
        (steps + latency.takeIf { it >= 0 && it !in steps }).filterNotNull().sorted()
            .map { it.toString() to stringResource(R.string.tv_ms, it) }
    return listOf(
        toggle(viewModel, Prefs.SYNC_CLIENT_VOLUME, R.string.setting_sync_client_volume, R.string.setting_sync_client_volume_desc),
        TvItem(
            stringResource(R.string.tv_audio_delay), stringResource(R.string.tv_audio_delay_desc),
            choices = latencies, selected = latency.toString(),
            onPick = { v -> v.toIntOrNull()?.let { viewModel.set(Prefs.AUDIO_LATENCY_MS, it) } }
        ),
        toggle(viewModel, Prefs.AUDIO_AUTO_BUFFER, R.string.setting_audio_auto_buffer, R.string.tv_audio_auto_buffer_desc),
    )
}

@Composable
private fun remoteItems(viewModel: MainViewModel): List<TvItem> {
    val ctx = LocalContext.current
    val status = remoteStatus(viewModel)
    // neither flag is observable; poll only while this page is on screen
    var adb by remember { mutableStateOf(LocalAdbDriver.isConnected.get()) }
    var access by remember { mutableStateOf(AtvAccessibilityService.isServiceRunning) }
    LaunchedEffect(Unit) {
        while (true) {
            adb = LocalAdbDriver.isConnected.get()
            access = AtvAccessibilityService.isServiceRunning
            delay(2000)
        }
    }
    val on = stringResource(R.string.tv_state_on)
    val off = stringResource(R.string.tv_state_off)
    return listOf(
        toggle(viewModel, Prefs.REMOTE_ENABLED, R.string.tv_remote_enable, R.string.tv_remote_enable_desc),
        TvItem(
            stringResource(R.string.tv_remote_status), stringResource(R.string.tv_remote_status_desc),
            value = stringResource(status.label)
        ),
        TvItem(
            stringResource(R.string.tv_remote_access), stringResource(R.string.tv_remote_access_desc),
            value = if (access) on else off, link = true,
            onClick = {
                try {
                    ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: ActivityNotFoundException) {
                }
            }
        ),
        TvItem(
            stringResource(R.string.tv_remote_adb), stringResource(R.string.tv_remote_adb_desc),
            value = stringResource(if (adb) R.string.tv_state_connected else R.string.tv_state_disconnected)
        ),
        TvItem(stringResource(R.string.tv_remote_dpad), stringResource(R.string.tv_remote_dpad_desc)),
    )
}

@Composable
private fun powerItems(viewModel: MainViewModel): List<TvItem> = listOf(
    toggle(viewModel, Prefs.RUN_IN_BACKGROUND, R.string.setting_run_in_background, R.string.setting_run_in_background_desc),
    toggle(viewModel, Prefs.BOOT_AUTO_START, R.string.setting_boot_auto_start, R.string.setting_boot_auto_start_desc),
    toggle(viewModel, Prefs.STANDBY_REACHABLE, R.string.tv_standby, R.string.tv_standby_desc),
    toggle(viewModel, Prefs.KEEP_SCREEN_ON, R.string.setting_keep_screen_on, R.string.setting_keep_screen_on_desc),
    toggle(viewModel, Prefs.AUTO_START, R.string.setting_auto_start, R.string.setting_auto_start_desc),
)

@Composable
private fun diagnosticsItems(viewModel: MainViewModel, go: (TvPage) -> Unit): List<TvItem> = listOf(
    TvItem(stringResource(R.string.tab_logs), stringResource(R.string.tv_logs_desc), link = true,
        onClick = { go(TvPage.LOGS) }),
    toggle(viewModel, Prefs.DEBUG_ENABLED, R.string.setting_debug_overlay, R.string.setting_debug_overlay_desc),
    TvItem(stringResource(R.string.tv_advanced), stringResource(R.string.tv_advanced_desc), link = true,
        onClick = { go(TvPage.ADVANCED) }),
)

// same checks the pref applies on write, so the dialog never accepts what would be dropped
internal enum class TvNameError { EMPTY, TOO_LONG, CONTROL }

internal fun tvNameError(name: String): TvNameError? = when {
    name.isBlank() -> TvNameError.EMPTY
    name.any(Char::isISOControl) -> TvNameError.CONTROL
    name.toByteArray(Charsets.UTF_8).size > 63 -> TvNameError.TOO_LONG
    else -> null
}

@Composable
private fun TvRenameDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val fieldFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { fieldFocus.requestFocusUntilLanded() }
    val trimmed = text.trim()
    val error = tvNameError(trimmed)
    val bytes = trimmed.toByteArray(Charsets.UTF_8).size
    val save = { if (error == null) onSave(trimmed) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(min = 460.dp, max = 580.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(TvTile)
                .padding(32.dp)
        ) {
            Text(stringResource(R.string.tv_name), color = TvText, fontSize = 24.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = text,
                // a little headroom past the limit so the error can be shown instead of silently cutting
                onValueChange = { text = it.take(80) },
                singleLine = true,
                isError = error != null,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 20.sp, color = TvText),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TvText, unfocusedBorderColor = Color(0x55FFFFFF), cursorColor = TvText,
                    errorBorderColor = TvError
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(fieldFocus)
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(
                    when (error) {
                        TvNameError.EMPTY -> stringResource(R.string.tv_name_error_empty)
                        TvNameError.TOO_LONG -> stringResource(R.string.tv_name_error_long)
                        TvNameError.CONTROL -> stringResource(R.string.tv_name_error_control)
                        null -> stringResource(R.string.tv_name_shared)
                    },
                    color = if (error != null) TvError else TvMuted, fontSize = 15.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    stringResource(R.string.tv_name_bytes, bytes),
                    color = if (bytes > 63) TvError else TvMuted, fontSize = 15.sp
                )
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvPill(stringResource(R.string.tv_cancel), onDismiss)
                TvPill(stringResource(R.string.tv_save), { save() }, enabled = error == null)
            }
        }
    }
}

@Composable
internal fun TvSubPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(TvBg).padding(horizontal = 48.dp, vertical = 32.dp)) {
        Text(title, color = TvText, fontSize = 28.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
    }
}

// near-black and near-white steps drawn by this TV, to compare against the same pattern sent over AirPlay
private val GRAY_BLACK = listOf(0, 4, 8, 12, 16, 20, 24, 32)
private val GRAY_WHITE = listOf(223, 231, 235, 239, 243, 247, 251, 255)

@Composable
private fun TvGrayscaleScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        GrayRow(GRAY_BLACK, Color.Black, Modifier.weight(1f))
        GrayRow(GRAY_WHITE, Color.White, Modifier.weight(1f))
        Text(
            stringResource(R.string.tv_grayscale_note), color = TvMuted, fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().background(TvBg).padding(horizontal = 48.dp, vertical = 14.dp)
        )
    }
}

@Composable
private fun GrayRow(levels: List<Int>, surround: Color, modifier: Modifier) {
    val label = if (surround == Color.Black) Color(0xFF8E8E93) else Color(0xFF3A3A3C)
    Row(
        modifier.fillMaxWidth().background(surround).padding(horizontal = 48.dp, vertical = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        levels.forEach { v ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().weight(1f).background(Color(v, v, v)))
                Spacer(Modifier.height(10.dp))
                Text(v.toString(), color = label, fontSize = 16.sp)
            }
        }
    }
}

// ---- pairing ----

internal enum class TvPairing { AIRPLAY, REMOTE }

// a second request waits for the first screen to finish, so two dialogs never fight over focus
@Composable
internal fun TvPinOverlays(
    airPlayPin: String?,
    remotePin: String?,
    airPlaySucceeded: () -> Boolean,
    remoteSucceeded: () -> Boolean,
    onDismissAirPlay: () -> Unit,
    onDismissRemote: () -> Unit
) {
    var owner by remember { mutableStateOf<TvPairing?>(null) }
    LaunchedEffect(owner, airPlayPin, remotePin) {
        if (owner == null) {
            owner = when {
                airPlayPin != null -> TvPairing.AIRPLAY
                remotePin != null -> TvPairing.REMOTE
                else -> null
            }
        }
    }
    when (owner) {
        TvPairing.AIRPLAY -> key(TvPairing.AIRPLAY) {
            TvPinOverlay(TvPairing.AIRPLAY, airPlayPin, airPlaySucceeded, onDismissAirPlay) { owner = null }
        }
        TvPairing.REMOTE -> key(TvPairing.REMOTE) {
            TvPinOverlay(TvPairing.REMOTE, remotePin, remoteSucceeded, onDismissRemote) { owner = null }
        }
        null -> {}
    }
}

// keeps the last code up through a short "done" beat, only when that service reports success
@Composable
private fun TvPinOverlay(
    kind: TvPairing,
    pin: String?,
    succeeded: () -> Boolean,
    onDismiss: () -> Unit,
    onFinished: () -> Unit
) {
    var shownPin by remember { mutableStateOf<String?>(null) }
    var paired by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }
    val success by rememberUpdatedState(succeeded)
    val fade = remember { Animatable(1f) }
    LaunchedEffect(pin) {
        if (pin != null) {
            dismissed = false
            paired = false
            fade.snapTo(1f)
            shownPin = pin
        } else if (shownPin != null) {
            if (!dismissed && success()) {
                paired = true
                delay(650)
            }
            fade.animateTo(0f, tween(180))
            shownPin = null
            paired = false
            onFinished()
        } else {
            // the request ended before this screen ever showed it
            onFinished()
        }
    }
    val code = shownPin ?: return
    Dialog(
        onDismissRequest = {
            dismissed = true
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false, dismissOnClickOutside = false, decorFitsSystemWindows = false
        )
    ) {
        Box(
            Modifier.fillMaxSize().background(TvBackdrop).graphicsLayer { alpha = fade.value },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // which service is asking matters: the two codes are entered in different places
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (kind == TvPairing.AIRPLAY) {
                        AirPlayMark(24.dp, TvMuted)
                    } else {
                        Icon(Icons.Default.SettingsRemote, null, Modifier.size(26.dp), tint = TvMuted)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(if (kind == TvPairing.AIRPLAY) R.string.tv_service_airplay else R.string.tv_remote_enable),
                        color = TvMuted, fontSize = 18.sp
                    )
                }
                Spacer(Modifier.height(24.dp))
                Crossfade(targetState = paired, animationSpec = tween(160), label = "pairTitle") { done ->
                    Text(
                        stringResource(
                            when {
                                !done -> R.string.tv_pair_title
                                kind == TvPairing.AIRPLAY -> R.string.tv_paired
                                else -> R.string.tv_remote_paired
                            }
                        ),
                        color = TvText, fontSize = 28.sp, fontWeight = FontWeight.Light
                    )
                }
                Spacer(Modifier.height(40.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.semantics { contentDescription = code.toList().joinToString(" ") }
                ) {
                    code.forEachIndexed { i, c -> PinDigit(c, i, paired) }
                }
                Spacer(Modifier.height(36.dp))
                val hintAlpha by animateFloatAsState(if (paired) 0f else 1f, tween(160), label = "hint")
                Text(
                    stringResource(if (kind == TvPairing.AIRPLAY) R.string.tv_pair_hint else R.string.tv_remote_pair_hint),
                    color = TvMuted, fontSize = 18.sp,
                    modifier = Modifier.graphicsLayer { alpha = hintAlpha }
                )
            }
        }
    }
}

@Composable
private fun PinDigit(digit: Char, index: Int, paired: Boolean) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(70L * index)
        appear.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
    }
    // completion runs left to right as a quick wave of white tiles
    val fill by animateFloatAsState(
        if (paired) 1f else 0f, tween(200, delayMillis = if (paired) 55 * index else 0), label = "fill"
    )
    Box(
        Modifier
            .graphicsLayer {
                alpha = appear.value
                translationY = (1f - appear.value) * 16.dp.toPx()
            }
            .size(88.dp, 112.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(lerp(TvTile, TvText, fill))
            .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            digit.toString(), color = lerp(TvText, TvInk, fill),
            fontSize = 60.sp, fontWeight = FontWeight.Light
        )
    }
}
