package io.github.henry753951.airplayremote.audiooutput.protocol

data class AirplayDevice(val name: String, val host: String, val port: Int) {
    init {
        require(name.isNotBlank() && name.length <= 256)
        require(validIpv4(host)) { "An IPv4 address is required" }
        require(port in 1..65535) { "Port must be between 1 and 65535" }
    }
}

// shortcut: the sender selects its local route using IPv4; add IPv6 with protocol tests.
internal fun validIpv4(host: String): Boolean {
    val parts = host.split('.')
    return parts.size == 4 && parts.all {
        it.isNotEmpty() && it.length <= 3 && it.all { c -> c in '0'..'9' } &&
            (it.length == 1 || it[0] != '0') && it.toInt() in 0..255
    } && parts[0].toInt() in 1..223 && parts[0] != "127" && host != "0.0.0.0"
}
