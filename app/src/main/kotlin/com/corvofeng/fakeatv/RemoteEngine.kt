package com.corvofeng.fakeatv

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import io.github.jqssun.airplay.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-local UI state; pairing secrets never leave app storage or the pairing screen. */
object RemoteState {
    internal val mutableStatus = MutableStateFlow("stopped")
    val status = mutableStatus.asStateFlow()
    internal val mutablePin = MutableStateFlow<String?>(null)
    val pin = mutablePin.asStateFlow()
    internal val mutablePaired = MutableStateFlow(false)
    val paired = mutablePaired.asStateFlow()
    internal val mutableConnected = MutableStateFlow(false)
    val connected = mutableConnected.asStateFlow()
    fun dismissPin() { mutablePin.value = null }
}

/** Shares the AirPlay foreground service and power policy; native lifecycle is serialized. */
class RemoteEngine(context: Context, private val onActivity: (Boolean) -> Unit) : ContextWrapper(context), AtvNative.Callback {
    private val thread = HandlerThread("RemoteLifecycle").apply { start() }
    private val worker = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())
    private val cm = getSystemService(ConnectivityManager::class.java)
    @Volatile private var enabled = false
    @Volatile private var closed = false
    private var advertised: Pair<String, String>? = null
    private var multicast: WifiManager.MulticastLock? = null
    private var clients = 0
    private var attempts = 0
    private val retry = Runnable { refresh() }
    private val clearPairing = Runnable { RemoteState.mutablePin.value = null }
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { scheduleRefresh() }
        override fun onLost(network: Network) { scheduleRefresh() }
        override fun onLinkPropertiesChanged(network: Network, props: LinkProperties) { scheduleRefresh() }
    }
    init {
        try { cm.registerDefaultNetworkCallback(callback) }
        catch (e: RuntimeException) { Log.w("RemoteEngine", "Network monitoring unavailable", e) }
    }
    private fun scheduleRefresh() {
        if (!closed) worker.post { attempts = 0; refresh() }
    }
    fun start() {
        if (closed) return
        enabled = true
        scheduleRefresh()
    }
    fun stop() {
        enabled = false
        worker.post { stopNative() }
    }
    private fun refresh() {
        worker.removeCallbacks(retry)
        if (!enabled || closed) return
        val ip = DeviceInfo.getLocalIpAddress(this)
        if (ip == null) {
            stopNative()
            RemoteState.mutableStatus.value = "waiting"
            return // Connectivity callbacks resume startup when the network is ready.
        }
        val identity = DeviceInfo.getDeviceName(this) to ip
        if (identity == advertised && AtvNative.nativeIsRunning()) return
        stopNative()
        RemoteState.mutableStatus.value = "starting"
        try {
            val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicast = wifi.createMulticastLock("ReceiverRemoteDiscovery").apply {
                setReferenceCounted(false); acquire()
            }
            val id = DeviceInfo.getDeviceId(this)
            val ok = AtvNative.nativeStartServer(identity.first, DeviceInfo.getPairingPin(this), id,
                DeviceInfo.getServerIdentifier(this, id), ip, this, DeviceInfo.getPrivateKey(this),
                createDeviceProtectedStorageContext().filesDir.absolutePath)
            if (!ok) error("Native receiver startup failed")
            advertised = identity
            attempts = 0
            KeyDispatcher.init(this)
            RemoteState.mutableStatus.value = "running"
        } catch (e: Exception) {
            Log.e("RemoteEngine", "Unable to start remote receiver", e)
            stopNative()
            RemoteState.mutableStatus.value = "error"
            if (++attempts <= 3) worker.postDelayed(retry, attempts * 5000L)
        }
    }
    private fun stopNative() {
        worker.removeCallbacks(retry)
        AtvNative.nativeStopServer()
        advertised = null
        LocalAdbDriver.shutdown()
        multicast?.let { if (it.isHeld) it.release() }
        multicast = null
        clients = 0
        RemoteState.mutableConnected.value = false
        main.post { onActivity(false) }
        main.removeCallbacks(clearPairing)
        RemoteState.mutablePin.value = null
        RemoteState.mutablePaired.value = false
        RemoteState.mutableStatus.value = "stopped"
    }
    override fun onButton(name: String) { if (enabled && !closed) KeyDispatcher.dispatchButton(name) }
    override fun onTouch(dx: Double, dy: Double, phase: Int) {
        AtvAccessibilityService.instance?.handleTouch(dx, dy, phase)
    }
    override fun onAudio(volume: Double, muted: Boolean) = Unit
    override fun onEvent(kind: String, detail: String) {
        if (!enabled || closed) return
        when (kind) {
            "ClientConnected", "ClientDisconnected" -> worker.post {
                clients = (clients + if (kind == "ClientConnected") 1 else -1).coerceAtLeast(0)
                RemoteState.mutableConnected.value = clients > 0
                if (kind == "ClientConnected") KeyDispatcher.init(this)
                main.post { onActivity(clients > 0) }
            }
            "PairingRequested" -> {
                RemoteState.mutablePaired.value = false
                RemoteState.mutablePin.value = DeviceInfo.getPairingPin(this).toString()
                main.removeCallbacks(clearPairing)
                main.postDelayed(clearPairing, 120_000)
                main.post {
                    if (!enabled || closed) return@post
                    try {
                        startActivity(Intent(this, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                    } catch (e: RuntimeException) { Log.w("RemoteEngine", "Open app to show pairing code", e) }
                }
            }
            "Paired", "Verified" -> {
                RemoteState.mutablePaired.value = true
                RemoteState.mutablePin.value = null
            }
            "MouseClick" -> AtvAccessibilityService.instance?.handleMouseClick()
        }
    }
    fun close() {
        closed = true
        enabled = false
        try { cm.unregisterNetworkCallback(callback) } catch (_: IllegalArgumentException) {}
        worker.post { stopNative(); thread.quitSafely() }
    }
}
