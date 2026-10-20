package io.github.henry753951.airplayremote.runtime

internal object PowerPolicy {
    fun holdCpu(running: Boolean, reachable: Boolean, airplayClients: Int, remoteActive: Boolean) =
        running && (reachable || airplayClients > 0 || remoteActive)
}
