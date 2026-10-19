package io.github.jqssun.airplay.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
private val TvTile = Color(0xFF1D1D20)
private val TvInk = Color(0xFF111113)
private val TvError = Color(0xFFFF7A6E)
private val TvBackdrop = Brush.verticalGradient(listOf(Color(0xFF1B1B1E), TvBg))

// short and quick: motion confirms a key press, it never stands in for one
private const val FOCUS_MS = 110

internal enum class TvPage { HOME, SETTINGS, ADVANCED, LOGS }

@Composable
private fun AirPlayMark(height: androidx.compose.ui.unit.Dp, tint: Color) {
    // drawable is 96x80
    Icon(painterResource(R.drawable.airplay_mark), null, Modifier.size(height * 1.2f, height), tint = tint)
}

@Composable
private fun TvPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(if (focused) TvText else Color(0x1FFFFFFF), tween(FOCUS_MS), label = "pillBg")
    val fg by animateColorAsState(if (focused) TvInk else TvText, tween(FOCUS_MS), label = "pillFg")
    Box(
        modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(CircleShape)
            .background(bg)
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 30.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = fg, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

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
    val reveal by animateFloatAsState(if (shown) 1f else 0f, tween(380, easing = FastOutSlowInEasing), label = "reveal")

    Column(
        Modifier.fillMaxSize().background(TvBackdrop).padding(horizontal = 64.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        Column(
            Modifier.graphicsLayer {
                alpha = reveal
                translationY = (1f - reveal) * 18.dp.toPx()
            },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AirPlayMark(52.dp, TvText)
            Spacer(Modifier.height(30.dp))
            Text(
                serverName, color = TvText, fontSize = 50.sp, fontWeight = FontWeight.Light,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(18.dp))
            val error = grant || state == ServerState.ERROR
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(8.dp).clip(CircleShape).background(
                        when {
                            error -> TvError
                            state == ServerState.RUNNING -> TvText
                            else -> TvMuted.copy(alpha = 0.5f)
                        }
                    )
                )
                Spacer(Modifier.width(12.dp))
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
                    color = TvMuted, fontSize = 18.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 620.dp)
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.graphicsLayer { alpha = reveal },
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

private class TvSetting(
    val title: String,
    val description: String,
    val checked: Boolean? = null,
    val value: String? = null,
    val onClick: () -> Unit
)

@Composable
internal fun TvSettingsScreen(
    viewModel: MainViewModel,
    focusIndex: Int,
    onFocusIndex: (Int) -> Unit,
    onBack: () -> Unit,
    onAdvanced: () -> Unit,
    onLogs: () -> Unit
) {
    BackHandler(onBack = onBack)
    val serverName by viewModel.pref(Prefs.SERVER_NAME).collectAsState()
    val requirePin by viewModel.pref(Prefs.REQUIRE_PIN).collectAsState()
    val allowNewConn by viewModel.pref(Prefs.ALLOW_NEW_CONN).collectAsState()
    val bootAutoStart by viewModel.pref(Prefs.BOOT_AUTO_START).collectAsState()
    val runInBackground by viewModel.pref(Prefs.RUN_IN_BACKGROUND).collectAsState()
    val launchOnConnect by viewModel.pref(Prefs.LAUNCH_ON_CONNECT).collectAsState()
    val autoFullscreen by viewModel.pref(Prefs.AUTO_FULLSCREEN).collectAsState()

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
    var renaming by remember { mutableStateOf(false) }

    val items = listOf(
        TvSetting(
            stringResource(R.string.setting_server_name), stringResource(R.string.tv_rename_desc),
            value = serverName, onClick = { renaming = true }
        ),
        TvSetting(
            stringResource(R.string.tv_require_pin), stringResource(R.string.setting_require_pin_desc),
            checked = requirePin, onClick = { viewModel.set(Prefs.REQUIRE_PIN, !requirePin) }
        ),
        TvSetting(
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
        TvSetting(
            stringResource(R.string.setting_auto_fullscreen), stringResource(R.string.setting_auto_fullscreen_desc),
            checked = autoFullscreen, onClick = { viewModel.set(Prefs.AUTO_FULLSCREEN, !autoFullscreen) }
        ),
        TvSetting(
            stringResource(R.string.setting_run_in_background), stringResource(R.string.setting_run_in_background_desc),
            checked = runInBackground, onClick = { viewModel.set(Prefs.RUN_IN_BACKGROUND, !runInBackground) }
        ),
        TvSetting(
            stringResource(R.string.setting_boot_auto_start), stringResource(R.string.setting_boot_auto_start_desc),
            checked = bootAutoStart, onClick = { viewModel.set(Prefs.BOOT_AUTO_START, !bootAutoStart) }
        ),
        TvSetting(
            stringResource(R.string.setting_allow_new_conn), stringResource(R.string.setting_allow_new_conn_desc),
            checked = allowNewConn, onClick = { viewModel.set(Prefs.ALLOW_NEW_CONN, !allowNewConn) }
        ),
        TvSetting(
            stringResource(R.string.tv_advanced), stringResource(R.string.tv_advanced_desc),
            value = "›", onClick = onAdvanced
        ),
        TvSetting(
            stringResource(R.string.tab_logs), stringResource(R.string.tv_logs_desc),
            value = "›", onClick = onLogs
        ),
    )
    val requesters = remember { List(items.size) { FocusRequester() } }
    val current = focusIndex.coerceIn(0, items.lastIndex)
    LaunchedEffect(Unit) { requesters[current].requestFocusUntilLanded() }

    Row(Modifier.fillMaxSize().background(TvBackdrop).padding(start = 64.dp, end = 56.dp)) {
        Column(Modifier.weight(0.38f).fillMaxHeight().padding(top = 56.dp, bottom = 56.dp, end = 40.dp)) {
            AirPlayMark(32.dp, TvMuted)
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.tv_settings_title), color = TvText, fontSize = 32.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.weight(1f))
            // the focused row explains itself here, so rows never change height
            Crossfade(targetState = current, animationSpec = tween(140), label = "desc") { i ->
                Text(items[i].description, color = TvMuted, fontSize = 17.sp, lineHeight = 26.sp)
            }
        }
        Column(
            Modifier
                .weight(0.62f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 48.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items.forEachIndexed { i, item ->
                TvSettingRow(item, Modifier.focusRequester(requesters[i])) { onFocusIndex(i) }
            }
        }
    }

    if (renaming) {
        TvRenameDialog(
            initial = serverName,
            onSave = { viewModel.set(Prefs.SERVER_NAME, it); renaming = false },
            onDismiss = { renaming = false }
        )
    }
}

@Composable
private fun TvSettingRow(item: TvSetting, modifier: Modifier, onFocused: () -> Unit) {
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
    val clickable = if (item.checked != null) {
        base.clickable(
            remember { MutableInteractionSource() }, indication = null, role = Role.Switch, onClick = item.onClick
        ).semantics { contentDescription = item.title }
    } else {
        base.clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = item.onClick)
    }
    Row(
        clickable.heightIn(min = 58.dp).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            item.title, color = fg, fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(20.dp))
        when {
            item.checked != null -> TvSwitch(item.checked, focused)
            item.value != null -> Text(
                item.value, color = sub, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 260.dp)
            )
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
private fun TvRenameDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val fieldFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { fieldFocus.requestFocusUntilLanded() }
    val valid = text.isNotBlank()
    val save = { if (valid) onSave(text.trim()) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(min = 460.dp, max = 560.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(TvTile)
                .padding(32.dp)
        ) {
            Text(stringResource(R.string.setting_server_name), color = TvText, fontSize = 24.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(40) },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 20.sp, color = TvText),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TvText, unfocusedBorderColor = Color(0x55FFFFFF), cursorColor = TvText
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(fieldFocus)
            )
            Spacer(Modifier.height(24.dp))
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvPill(stringResource(R.string.tv_cancel), onDismiss)
                TvPill(stringResource(R.string.tv_save), { save() })
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

// keeps the last code up through a short "connected" beat after the sender pairs
@Composable
internal fun TvPinOverlay(pin: String?, connections: Int, onDismiss: () -> Unit) {
    var shownPin by remember { mutableStateOf<String?>(null) }
    var paired by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }
    val connected by rememberUpdatedState(connections > 0)
    val fade = remember { Animatable(1f) }
    LaunchedEffect(pin) {
        if (pin != null) {
            dismissed = false
            paired = false
            fade.snapTo(1f)
            shownPin = pin
        } else if (shownPin != null) {
            if (!dismissed && connected) {
                paired = true
                delay(650)
            }
            fade.animateTo(0f, tween(180))
            shownPin = null
            paired = false
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
                AirPlayMark(40.dp, TvMuted)
                Spacer(Modifier.height(28.dp))
                Crossfade(targetState = paired, animationSpec = tween(160), label = "pairTitle") { done ->
                    Text(
                        stringResource(if (done) R.string.tv_paired else R.string.tv_pair_title),
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
                    stringResource(R.string.tv_pair_hint), color = TvMuted, fontSize = 18.sp,
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
