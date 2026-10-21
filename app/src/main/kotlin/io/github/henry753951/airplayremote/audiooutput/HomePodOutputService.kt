package io.github.henry753951.airplayremote.audiooutput

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.henry753951.airplayremote.MainActivity
import io.github.henry753951.airplayremote.R
import io.github.henry753951.airplayremote.audiooutput.protocol.AirplayDevice
import io.github.henry753951.airplayremote.audiooutput.protocol.RaopSender
import io.github.henry753951.airplayremote.hasLocalNetwork
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors
import java.net.InetAddress
import java.net.NetworkInterface

data class SpeakerOutputState(
    val active: Boolean = false,
    val streaming: Boolean = false,
    val target: String = "",
    val tone: Boolean = false,
    val volume: Int = 25,
    val error: Boolean = false
)

/** A user-started session, independent of receiver boot/start/stop preferences. */
class HomePodOutputService : Service() {
    companion object {
        private const val CHANNEL = "speaker-output"
        private const val NOTIFICATION = 42
        private const val START = "speaker.START"
        private const val STOP = "speaker.STOP"
        private const val VOLUME = "speaker.VOLUME"
        private val mutableState = MutableStateFlow(SpeakerOutputState())
        val state = mutableState.asStateFlow()

        fun start(context: Context, device: AirplayDevice, consent: Intent? = null): Boolean = try {
            ContextCompat.startForegroundService(context, Intent(context, HomePodOutputService::class.java).apply {
                action = START
                putExtra("name", device.name)
                putExtra("host", device.host)
                putExtra("port", device.port)
                putExtra("consent", consent)
            })
            true
        } catch (_: RuntimeException) {
            mutableState.value = SpeakerOutputState(error = true)
            false
        }

        fun stop(context: Context) { context.stopService(Intent(context, HomePodOutputService::class.java)) }
        fun volume(context: Context, value: Int) {
            if (state.value.active) context.startService(Intent(context, HomePodOutputService::class.java).apply {
                action = VOLUME
                putExtra("volume", value.coerceIn(0, 100))
            })
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private val cleanup = Executors.newSingleThreadExecutor()
    private var sender: RaopSender? = null
    private var capture: PlaybackCapture? = null
    private var projection: MediaProjection? = null
    private var toneThread: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var generation = 0
    private val connectionTimeout = Runnable { fail() }
    private var projectionCallback: MediaProjection.Callback? = null

    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            STOP -> finishSession(false)
            VOLUME -> if (sender != null) {
                val volume = intent.getIntExtra("volume", 25).coerceIn(0, 100)
                sender?.setVolume(volume.toDouble())
                mutableState.value = state.value.copy(volume = volume)
            } else stopSelf()
            START -> if (sender == null) {
                try {
                    require(hasLocalNetwork())
                    val device = AirplayDevice(
                        intent.getStringExtra("name") ?: "HomePod",
                        intent.getStringExtra("host") ?: "",
                        intent.getIntExtra("port", 0)
                    )
                    // Sending captured output back into our own receiver would create feedback.
                    require(NetworkInterface.getByInetAddress(InetAddress.getByName(device.host)) == null)
                    val consent = intent.getParcelableExtra<Intent>("consent")
                    if (consent != null) {
                        require(Build.VERSION.SDK_INT >= 29)
                        require(ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                    }
                    showNotification(consent != null)
                    mutableState.value = SpeakerOutputState(active = true, target = device.name, tone = consent == null)
                    if (consent != null) {
                        val token = requireNotNull(getSystemService(MediaProjectionManager::class.java)
                            .getMediaProjection(Activity.RESULT_OK, consent))
                        projection = token
                        val callback = object : MediaProjection.Callback() {
                            override fun onStop() { if (projection === token) finishSession(false) }
                        }
                        projectionCallback = callback
                        token.registerCallback(callback, main)
                    }
                    val power = getSystemService(PowerManager::class.java)
                    @Suppress("WakelockTimeout") // Released with this user-visible foreground session.
                    val lock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "airplay-hub:speaker-output").apply { acquire() }
                    wakeLock = lock
                    connect(device)
                } catch (_: Exception) { fail() }
            }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun connect(device: AirplayDevice) {
        val session = ++generation
        val output = RaopSender(device, object : RaopSender.Listener {
            override fun onState(state: RaopSender.State) = Unit
            // The protocol can log session payloads; they are deliberately kept out of app logs.
            override fun onLog(message: String) = Unit
            override fun onStarted() { main.post {
                if (generation != session || sender == null) return@post
                main.removeCallbacks(connectionTimeout)
                mutableState.value = state.value.copy(streaming = true)
                val current = sender ?: return@post
                current.setVolume(state.value.volume.toDouble())
                if (projection != null && Build.VERSION.SDK_INT >= 29) {
                    val recorder = PlaybackCapture(requireNotNull(projection),
                        onPcm = current::pushPcm,
                        onError = { main.post { if (sender === current) fail() } }
                    )
                    capture = recorder
                    recorder.onRate = current::onCaptureRate
                    if (!runCatching { recorder.start() }.getOrDefault(false)) fail()
                } else {
                    toneThread = Thread({
                        val tone = TestTone()
                        val start = SystemClock.elapsedRealtimeNanos()
                        var blocks = 0L
                        try {
                            while (!Thread.currentThread().isInterrupted) {
                                current.pushPcm(tone.next(), 44100)
                                blocks++
                                val wait = start + blocks * 10_000_000 - SystemClock.elapsedRealtimeNanos()
                                if (wait > 0) Thread.sleep(wait / 1_000_000, (wait % 1_000_000).toInt())
                            }
                        } catch (_: InterruptedException) { }
                    }, "homepod-test-tone").also { it.start() }
                }
            } }
            override fun onStopped(reason: String?) { main.post {
                if (generation == session && sender != null) finishSession(reason != null)
            } }
        })
        sender = output
        main.postDelayed(connectionTimeout, 60_000)
        output.start()
    }

    private fun showNotification(capturing: Boolean) {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.speaker_title), NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(this, 42, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 43, Intent(this, HomePodOutputService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.airplay_mark)
            .setContentTitle(getString(R.string.speaker_title))
            .setContentText(getString(R.string.speaker_notification))
            .setContentIntent(open).setOngoing(true)
            .addAction(0, getString(R.string.speaker_stop), stop).build()
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
            (if (capturing) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0) else 0
        ServiceCompat.startForeground(this, NOTIFICATION, notification, type)
    }

    private fun fail() = finishSession(true)

    private fun finishSession(error: Boolean) {
        mutableState.value = state.value.copy(active = false, streaming = false, error = error)
        releaseSession()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseSession() {
        generation++
        main.removeCallbacks(connectionTimeout)
        val oldSender = sender.also { sender = null }
        val oldCapture = capture.also { capture = null }
        val oldProjection = projection.also { projection = null }
        val oldCallback = projectionCallback.also { projectionCallback = null }
        val oldTone = toneThread.also { toneThread = null }
        val oldWakeLock = wakeLock.also { wakeLock = null }
        oldTone?.interrupt()
        if (oldCallback != null) {
            try { oldProjection?.unregisterCallback(oldCallback) } catch (_: RuntimeException) { }
        }
        if (oldSender != null || oldCapture != null || oldProjection != null) cleanup.execute {
            try { if (Build.VERSION.SDK_INT >= 29) oldCapture?.stop() } finally {
                try { oldSender?.stop() } finally {
                    try { oldProjection?.stop() } finally {
                        oldWakeLock?.let { if (it.isHeld) it.release() }
                    }
                }
            }
        } else oldWakeLock?.let { if (it.isHeld) it.release() }
    }

    override fun onDestroy() {
        releaseSession()
        mutableState.value = state.value.copy(active = false, streaming = false)
        cleanup.shutdown()
        super.onDestroy()
    }
}
