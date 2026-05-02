package io.netscan.app.data

data class GatewayInfo(
    val networkName: String,
    val interfaceName: String,
    val localAddress: String,
    val subnetPrefix: Int,
    val gatewayAddress: String,
    val dnsServers: List<String>,
    val ssid: String?,
    val bssid: String?,
    val metered: Boolean,
    val scanRangeLabel: String,
    val routeSummary: List<String>
)
