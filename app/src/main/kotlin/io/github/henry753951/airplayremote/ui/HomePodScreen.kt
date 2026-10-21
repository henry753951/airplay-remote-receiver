package io.github.henry753951.airplayremote.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.henry753951.airplayremote.R
import io.github.henry753951.airplayremote.audiooutput.HomePodOutputService
import io.github.henry753951.airplayremote.audiooutput.SpeakerDiscovery
import io.github.henry753951.airplayremote.audiooutput.protocol.AirplayDevice
import io.github.henry753951.airplayremote.hasLocalNetwork

@Composable
internal fun HomePodScreen(onLocalNetwork: () -> Unit) {
    val context = LocalContext.current
    val output by HomePodOutputService.state.collectAsState()
    var devices by remember { mutableStateOf(emptyList<AirplayDevice>()) }
    var scan by remember { mutableIntStateOf(0) }
    var message by remember { mutableIntStateOf(0) }
    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("7000") }
    var selectedName by rememberSaveable { mutableStateOf("HomePod") }
    var requesting by rememberSaveable { mutableStateOf(false) }
    var localNetwork by remember { mutableStateOf(context.hasLocalNetwork()) }

    val target = remember(host, port, selectedName) {
        runCatching { AirplayDevice(selectedName, host.trim(), port.toIntOrNull() ?: 0) }.getOrNull()
    }
    val projection = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        requesting = false
        if (result.resultCode == Activity.RESULT_OK && result.data != null && target != null) {
            HomePodOutputService.start(context, target, result.data)
        } else message = R.string.speaker_cancelled
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && Build.VERSION.SDK_INT >= 29) {
            try {
                projection.launch(context.getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
            } catch (_: RuntimeException) { requesting = false; message = R.string.speaker_capture_unavailable }
        } else { requesting = false; message = R.string.speaker_permission_denied }
    }

    DisposableEffect(scan, localNetwork) {
        val discovery = SpeakerDiscovery(context,
            onDevices = { devices = it }, onError = { message = R.string.speaker_scan_error }
        )
        if (localNetwork) discovery.start()
        onDispose { discovery.close() }
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.speaker_intro), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.speaker_limit), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(when {
            output.error -> R.string.speaker_failed
            output.streaming && output.tone -> R.string.speaker_sending_tone
            output.streaming -> R.string.speaker_sending_audio
            output.active -> R.string.speaker_connecting
            else -> R.string.speaker_idle
        }, output.target), style = MaterialTheme.typography.titleMedium)

        if (output.active) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { HomePodOutputService.stop(context) }, modifier = Modifier.dpadFocus()) {
                    Text(stringResource(R.string.speaker_stop))
                }
                OutlinedButton(onClick = { HomePodOutputService.volume(context, output.volume - 5) },
                    enabled = output.volume > 0, modifier = Modifier.dpadFocus()) {
                    Text(stringResource(R.string.speaker_quieter))
                }
                Text(stringResource(R.string.speaker_volume, output.volume), Modifier.padding(top = 14.dp))
                OutlinedButton(onClick = { HomePodOutputService.volume(context, output.volume + 5) },
                    enabled = output.volume < 100, modifier = Modifier.dpadFocus()) {
                    Text(stringResource(R.string.speaker_louder))
                }
            }
        } else {
            Button(onClick = {
                localNetwork = context.hasLocalNetwork()
                if (localNetwork) { message = 0; devices = emptyList(); scan++ } else onLocalNetwork()
            }, enabled = !requesting, modifier = Modifier.dpadFocus()) { Text(stringResource(R.string.speaker_scan)) }

            devices.forEach { device ->
                OutlinedButton(onClick = {
                    selectedName = device.name
                    host = device.host
                    port = device.port.toString()
                    message = 0
                }, enabled = !requesting, modifier = Modifier.fillMaxWidth().dpadFocus()) {
                    Text("${device.name} · ${device.host}:${device.port}" + if (target == device) " ✓" else "")
                }
            }
            if (devices.isEmpty()) Text(stringResource(R.string.speaker_no_devices))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvEditGate(Modifier.weight(1f)) { field ->
                    OutlinedTextField(value = host, onValueChange = { host = it.take(64); selectedName = "HomePod" },
                        label = { Text(stringResource(R.string.speaker_ip)) }, singleLine = true, enabled = !requesting,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = field.fillMaxWidth())
                }
                TvEditGate(Modifier.weight(0.4f)) { field ->
                    OutlinedTextField(value = port, onValueChange = { port = it.take(5); selectedName = "HomePod" },
                        label = { Text(stringResource(R.string.speaker_port)) }, singleLine = true, enabled = !requesting,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = field.fillMaxWidth())
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    message = 0
                    if (!context.hasLocalNetwork()) onLocalNetwork()
                    else target?.let { HomePodOutputService.start(context, it) }
                }, enabled = target != null && !requesting, modifier = Modifier.dpadFocus()) {
                    Text(stringResource(R.string.speaker_test))
                }
                Button(onClick = {
                    message = 0
                    if (!context.hasLocalNetwork()) onLocalNetwork()
                    else {
                        requesting = true
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            permission.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            try {
                                projection.launch(context.getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
                            } catch (_: RuntimeException) { requesting = false; message = R.string.speaker_capture_unavailable }
                        }
                    }
                }, enabled = target != null && !requesting && Build.VERSION.SDK_INT >= 29, modifier = Modifier.dpadFocus()) {
                    Text(stringResource(R.string.speaker_capture))
                }
            }
        }
        if (message != 0) Text(stringResource(message), color = MaterialTheme.colorScheme.error)
        Text(stringResource(R.string.speaker_setup), style = MaterialTheme.typography.bodySmall)
    }
}
