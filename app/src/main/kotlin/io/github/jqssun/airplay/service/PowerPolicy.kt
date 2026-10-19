package io.github.jqssun.airplay.service

internal object PowerPolicy {
    fun holdCpu(running: Boolean, reachable: Boolean, airplayClients: Int, remoteActive: Boolean) =
        running && (reachable || airplayClients > 0 || remoteActive)
}
