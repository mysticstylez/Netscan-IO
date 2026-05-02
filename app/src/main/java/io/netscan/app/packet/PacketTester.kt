package io.netscan.app.packet

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.max

enum class PacketProtocol {
    TCP,
    UDP
}

data class PacketProbeConfig(
    val host: String,
    val port: Int,
    val packetCount: Int,
    val payloadSize: Int,
    val intervalMs: Long,
    val protocol: PacketProtocol
)

data class PacketProbeResult(
    val sent: Int,
    val success: Int,
    val failed: Int,
    val averageLatencyMs: Double,
    val minLatencyMs: Long?,
    val maxLatencyMs: Long?,
    val logLines: List<String>
)

class PacketTester(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun run(
        config: PacketProbeConfig,
        onProgress: (String) -> Unit
    ): PacketProbeResult = withContext(ioDispatcher) {
        val count = max(1, config.packetCount)
        val payload = ByteArray(max(1, config.payloadSize)) { index -> (index % 127).toByte() }
        val latencies = mutableListOf<Long>()
        val log = mutableListOf<String>()
        var success = 0
        var failed = 0

        repeat(count) { index ->
            val attempt = index + 1
            val startedAt = System.nanoTime()
            val ok = when (config.protocol) {
                PacketProtocol.TCP -> sendTcp(config.host, config.port, payload)
                PacketProtocol.UDP -> sendUdp(config.host, config.port, payload)
            }
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
            if (ok) {
                success += 1
                latencies += elapsedMs
                val line = "#$attempt ${config.protocol.name} sent to ${config.host}:${config.port} in ${elapsedMs}ms"
                log += line
                onProgress(line)
            } else {
                failed += 1
                val line = "#$attempt ${config.protocol.name} failed to ${config.host}:${config.port}"
                log += line
                onProgress(line)
            }
            if (attempt < count && config.intervalMs > 0) {
                delay(config.intervalMs)
            }
        }

        PacketProbeResult(
            sent = count,
            success = success,
            failed = failed,
            averageLatencyMs = if (latencies.isEmpty()) 0.0 else latencies.average(),
            minLatencyMs = latencies.minOrNull(),
            maxLatencyMs = latencies.maxOrNull(),
            logLines = log
        )
    }

    private fun sendTcp(host: String, port: Int, payload: ByteArray): Boolean {
        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 1500)
                socket.getOutputStream().use { output ->
                    output.write(payload)
                    output.flush()
                }
            }
            true
        }.getOrDefault(false)
    }

    private fun sendUdp(host: String, port: Int, payload: ByteArray): Boolean {
        return runCatching {
            DatagramSocket().use { socket ->
                val packet = DatagramPacket(payload, payload.size, InetSocketAddress(host, port))
                socket.send(packet)
            }
            true
        }.getOrDefault(false)
    }
}
