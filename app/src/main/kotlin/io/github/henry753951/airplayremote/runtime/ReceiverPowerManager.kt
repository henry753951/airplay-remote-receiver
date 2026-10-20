package io.github.henry753951.airplayremote.runtime

import android.content.Context
import android.os.PowerManager

/** One lock shared by the two listeners; releases CPU residency according to the existing policy. */
internal class ReceiverPowerManager(context: Context) {
    private val manager = context.getSystemService(PowerManager::class.java)
    private var lock: PowerManager.WakeLock? = null

    fun update(running: Boolean, reachable: Boolean, airplayClients: Int, remoteActive: Boolean) {
        if (!PowerPolicy.holdCpu(running, reachable, airplayClients, remoteActive)) {
            release()
        } else if (lock?.isHeld != true) {
            lock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Receiver:Available")
                .apply { acquire() }
        }
    }

    fun release() {
        lock?.let { if (it.isHeld) it.release() }
        lock = null
    }
}
