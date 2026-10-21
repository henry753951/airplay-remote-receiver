package io.github.henry753951.airplayremote.audiooutput

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import io.github.henry753951.airplayremote.audiooutput.protocol.AirplayDevice
import io.github.henry753951.airplayremote.audiooutput.protocol.validIpv4
import java.net.Inet4Address
import java.net.NetworkInterface

/** Uses the system mDNS client; it does not compete with the receiver for UDP 5353. */
@Suppress("DEPRECATION")
internal class SpeakerDiscovery(
    context: Context,
    private val onDevices: (List<AirplayDevice>) -> Unit,
    private val onError: () -> Unit
) : NsdManager.DiscoveryListener {
    private val manager = context.getSystemService(NsdManager::class.java)
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val devices = linkedMapOf<String, AirplayDevice>()
    private val pending = ArrayDeque<NsdServiceInfo>()
    private val present = mutableSetOf<String>()
    private var lock: WifiManager.MulticastLock? = null
    private var running = false
    private var resolving = false

    fun start() {
        if (running) return
        running = true
        try {
            lock = wifi?.createMulticastLock("airplay-hub-speakers")?.apply {
                setReferenceCounted(false)
                acquire()
            }
            manager.discoverServices("_airplay._tcp.", NsdManager.PROTOCOL_DNS_SD, this)
        } catch (_: RuntimeException) {
            close()
            onError()
        }
    }

    fun close() {
        running = false
        pending.clear()
        present.clear()
        try { manager.stopServiceDiscovery(this) } catch (_: RuntimeException) { }
        lock?.let { if (it.isHeld) it.release() }
        lock = null
    }

    override fun onServiceFound(info: NsdServiceInfo) { main.post {
        if (running && present.add(info.serviceName)) {
            pending.addLast(info)
            resolveNext()
        }
    } }

    override fun onServiceLost(info: NsdServiceInfo) { main.post {
        present.remove(info.serviceName)
        devices.remove(info.serviceName)
        if (running) onDevices(devices.values.distinctBy { it.host to it.port })
    } }

    private fun resolveNext() {
        if (!running || resolving || pending.isEmpty()) return
        val next = pending.removeFirst()
        resolving = true
        try {
            manager.resolveService(next, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, code: Int) { main.post {
                    resolving = false
                    present.remove(info.serviceName)
                    resolveNext()
                } }
                override fun onServiceResolved(info: NsdServiceInfo) { main.post {
                    resolving = false
                    val address = info.host as? Inet4Address
                    val local = address?.let { runCatching { NetworkInterface.getByInetAddress(it) != null }.getOrDefault(true) } ?: true
                    val host = address?.hostAddress
                    if (running && !local && info.serviceName.isNotBlank() && info.serviceName in present && host != null && validIpv4(host) && info.port in 1..65535) {
                        devices[info.serviceName] = AirplayDevice(info.serviceName.take(256), host, info.port)
                        onDevices(devices.values.distinctBy { it.host to it.port })
                    }
                    resolveNext()
                } }
            })
        } catch (_: RuntimeException) {
            resolving = false
            present.remove(next.serviceName)
            resolveNext()
        }
    }

    override fun onStartDiscoveryFailed(type: String, code: Int) { main.post { close(); onError() } }
    override fun onStopDiscoveryFailed(type: String, code: Int) = Unit
    override fun onDiscoveryStarted(type: String) = Unit
    override fun onDiscoveryStopped(type: String) = Unit
}
