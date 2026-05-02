package io.netscan.app.network

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.RouteInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import io.netscan.app.data.DeviceInfo
import io.netscan.app.data.GatewayInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

class NetworkScanner(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun loadGatewayInfo(): GatewayInfo = withContext(ioDispatcher) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
            ?: return@withContext emptyGatewayInfo("No active network")
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        val linkProperties = connectivityManager.getLinkProperties(activeNetwork)
            ?: return@withContext emptyGatewayInfo("Missing link properties")
        val ipv4Address = linkProperties.linkAddresses
            .firstOrNull { it.address is Inet4Address }
        val localAddress = ipv4Address?.address?.hostAddress.orEmpty()
        val prefixLength = ipv4Address?.prefixLength ?: 24
        val gateway = linkProperties.routes
            .firstOrNull(RouteInfo::isDefaultRoute)
            ?.gateway
            ?.hostAddress
            .orEmpty()
        val dnsServers = linkProperties.dnsServers.mapNotNull { it.hostAddress }
        val networkName = when {
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cellular"
            else -> "Unknown"
        }
        val routeSummary = linkProperties.routes.map { route ->
            buildString {
                append(route.destination?.toString() ?: "default")
                append(" -> ")
                append(route.gateway?.hostAddress ?: "direct")
            }
        }
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val canReadWifi = canReadWifiDetails()
        val wifiInfo = if (canReadWifi) wifiManager?.connectionInfo else null
        val ssid = wifiInfo?.ssid?.trim('"')?.takeUnless { it == "<unknown ssid>" }
        val bssid = wifiInfo?.bssid?.takeUnless { it == "02:00:00:00:00:00" }
        GatewayInfo(
            networkName = networkName,
            interfaceName = linkProperties.interfaceName.orEmpty(),
            localAddress = localAddress,
            subnetPrefix = prefixLength,
            gatewayAddress = gateway,
            dnsServers = dnsServers,
            ssid = ssid,
            bssid = bssid,
            metered = connectivityManager.isActiveNetworkMetered,
            scanRangeLabel = scanRange(localAddress, prefixLength),
            routeSummary = routeSummary
        )
    }

    suspend fun scanCurrentNetwork(): List<DeviceInfo> = withContext(ioDispatcher) {
        val gatewayInfo = loadGatewayInfo()
        val baseIp = gatewayInfo.localAddress
        if (baseIp.isBlank()) return@withContext emptyList()

        val octets = baseIp.split(".")
        if (octets.size != 4) return@withContext emptyList()

        val subnetBase = octets.take(3).joinToString(".")
        val semaphore = Semaphore(48)
        coroutineScope {
            (1..254).map { host ->
                val ip = "$subnetBase.$host"
                async {
                    semaphore.withPermit {
                        probeDevice(
                            ip = ip,
                            localAddress = gatewayInfo.localAddress,
                            gatewayAddress = gatewayInfo.gatewayAddress
                        )
                    }
                }
            }.awaitAll().filterNotNull().sortedBy { ipToSortableLong(it.ipAddress) }
        }
    }

    private suspend fun probeDevice(
        ip: String,
        localAddress: String,
        gatewayAddress: String
    ): DeviceInfo? = withContext(ioDispatcher) {
        val commonPorts = listOf(22, 23, 80, 139, 443, 445, 8080)
        val start = System.currentTimeMillis()
        val openPorts = commonPorts.filter { isPortOpen(ip, it) }
        val reachable = openPorts.isNotEmpty() || isReachable(ip)
        if (!reachable) return@withContext null

        val latency = (System.currentTimeMillis() - start).toInt()
        val resolvedName = resolveHostName(ip)

        val macAddress = readArpMac(ip)
        val services = openPorts.mapNotNull { SERVICE_LABELS[it] }
        val portSummaries = openPorts.map { port ->
            SERVICE_LABELS[port]?.let { "$port - $it" } ?: "$port - Unknown"
        }
        val displayName = resolvedName
            ?: classifyDeviceName(
                ip = ip,
                localAddress = localAddress,
                gatewayAddress = gatewayAddress,
                services = services
            )
        val notes = buildList {
            if (openPorts.isEmpty()) add("Host responded without common ports open.")
            if (macAddress == null) add("MAC address not exposed by Android on this device.")
            if (resolvedName == null) add("No hostname announced by this device on the current network.")
        }

        DeviceInfo(
            ipAddress = ip,
            displayName = displayName,
            hostName = resolvedName,
            macAddress = macAddress,
            latencyMs = latency,
            openPorts = openPorts,
            services = services,
            portSummaries = portSummaries,
            notes = notes
        )
    }

    private fun resolveHostName(ip: String): String? {
        val address = runCatching { InetAddress.getByName(ip) }.getOrNull() ?: return null
        val candidates = listOf(address.hostName, address.canonicalHostName)
            .mapNotNull { candidate ->
                candidate
                    ?.trim()
                    ?.takeUnless { it.isBlank() || it == ip || it.equals("localhost", ignoreCase = true) }
                    ?.substringBefore(".localdomain")
            }
        return candidates.firstOrNull()
    }

    private fun isReachable(ip: String): Boolean {
        return runCatching {
            InetAddress.getByName(ip).isReachable(300)
        }.getOrDefault(false)
    }

    private fun isPortOpen(ip: String, port: Int): Boolean {
        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), 180)
                true
            }
        }.getOrDefault(false)
    }

    private fun readArpMac(ip: String): String? {
        return runCatching {
            File("/proc/net/arp").useLines { lines ->
                lines.drop(1)
                    .firstOrNull { it.trim().startsWith(ip) }
                    ?.split(Regex("\\s+"))
                    ?.getOrNull(3)
                    ?.takeUnless { it == "00:00:00:00:00:00" }
            }
        }.getOrNull()
    }

    private fun canReadWifiDetails(): Boolean {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val nearbyGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return fineLocationGranted && nearbyGranted
    }

    private fun scanRange(ip: String, prefixLength: Int): String {
        val octets = ip.split(".")
        return if (octets.size == 4) {
            "${octets[0]}.${octets[1]}.${octets[2]}.1-254 (/24 sweep, active network /$prefixLength)"
        } else {
            "Unable to determine range"
        }
    }

    private fun emptyGatewayInfo(reason: String): GatewayInfo = GatewayInfo(
        networkName = reason,
        interfaceName = "",
        localAddress = "",
        subnetPrefix = 0,
        gatewayAddress = "",
        dnsServers = emptyList(),
        ssid = null,
        bssid = null,
        metered = false,
        scanRangeLabel = "",
        routeSummary = emptyList()
    )

    private fun ipToSortableLong(ip: String): Long {
        return ip.split(".")
            .map { it.toLongOrNull() ?: 0L }
            .fold(0L) { acc, value -> (acc shl 8) + value }
    }

    private fun classifyDeviceName(
        ip: String,
        localAddress: String,
        gatewayAddress: String,
        services: List<String>
    ): String {
        return when {
            ip == localAddress -> "This Android device"
            gatewayAddress.isNotBlank() && ip == gatewayAddress -> "Gateway / router"
            services.isNotEmpty() -> "${services.joinToString(" / ")} device"
            else -> "LAN device"
        }
    }

    companion object {
        private val SERVICE_LABELS = mapOf(
            22 to "SSH",
            23 to "Telnet",
            80 to "HTTP",
            139 to "NetBIOS",
            443 to "HTTPS",
            445 to "SMB",
            8080 to "HTTP Alt"
        )
    }
}
