package io.netscan.app.ui.packet

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.netscan.app.databinding.ActivityPacketToolBinding
import io.netscan.app.packet.PacketProbeConfig
import io.netscan.app.packet.PacketProtocol
import io.netscan.app.packet.PacketTester
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PacketToolActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPacketToolBinding
    private val packetTester = PacketTester()
    private var runningJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPacketToolBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "Packet Tool"
        binding.hostInput.setText(intent.getStringExtra(EXTRA_HOST).orEmpty())
        binding.portInput.setText("22")
        binding.packetCountInput.setText("10")
        binding.payloadSizeInput.setText("64")
        binding.intervalInput.setText("250")
        binding.protocolSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            PacketProtocol.entries.map { it.name }
        )

        binding.startButton.setOnClickListener { startTest() }
        binding.clearButton.setOnClickListener {
            binding.logOutput.text = ""
            binding.resultSummary.text = getString(io.netscan.app.R.string.packet_summary_idle)
        }
    }

    private fun startTest() {
        if (runningJob?.isActive == true) return

        val host = binding.hostInput.text.toString().trim()
        val port = binding.portInput.text.toString().trim().toIntOrNull()
        val packetCount = binding.packetCountInput.text.toString().trim().toIntOrNull()
        val payloadSize = binding.payloadSizeInput.text.toString().trim().toIntOrNull()
        val intervalMs = binding.intervalInput.text.toString().trim().toLongOrNull()
        val protocol = PacketProtocol.valueOf(binding.protocolSpinner.selectedItem.toString())

        if (host.isBlank() || port == null || packetCount == null || payloadSize == null || intervalMs == null) {
            Toast.makeText(this, "Fill in host, port, count, payload, and interval.", Toast.LENGTH_SHORT).show()
            return
        }

        binding.startButton.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE
        binding.logOutput.text = ""
        binding.resultSummary.text = "Running ${protocol.name} packet test..."

        runningJob = lifecycleScope.launch {
            runCatching {
                packetTester.run(
                    PacketProbeConfig(
                        host = host,
                        port = port,
                        packetCount = packetCount,
                        payloadSize = payloadSize,
                        intervalMs = intervalMs,
                        protocol = protocol
                    )
                ) { line ->
                    runOnUiThread {
                        binding.logOutput.append(line)
                        binding.logOutput.append("\n")
                        binding.logScroll.post { binding.logScroll.fullScroll(View.FOCUS_DOWN) }
                    }
                }
            }.onSuccess { result ->
                binding.resultSummary.text = buildString {
                    append("Sent ${result.sent} ${protocol.name} packets")
                    append(" | success ${result.success}")
                    append(" | failed ${result.failed}")
                    if (result.success > 0) {
                        append(" | avg ${"%.1f".format(result.averageLatencyMs)}ms")
                        append(" | min ${result.minLatencyMs}ms")
                        append(" | max ${result.maxLatencyMs}ms")
                    }
                }
            }.onFailure {
                binding.resultSummary.text = it.message ?: "Packet test failed."
            }

            binding.startButton.isEnabled = true
            binding.progressBar.visibility = View.GONE
        }
    }

    companion object {
        const val EXTRA_HOST = "extra_host"
    }
}
