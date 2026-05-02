package io.netscan.app.ui.device

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.netscan.app.data.DeviceInfo
import io.netscan.app.databinding.ActivityDeviceDetailBinding
import io.netscan.app.remote.Protocol
import io.netscan.app.ui.packet.PacketToolActivity
import io.netscan.app.ui.sftp.SftpActivity
import io.netscan.app.ui.terminal.TerminalActivity

class DeviceDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDeviceDetailBinding
    private lateinit var deviceInfo: DeviceInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        deviceInfo = intent.getSerializableExtra(EXTRA_DEVICE) as? DeviceInfo
            ?: error("Missing device details.")

        supportActionBar?.title = deviceInfo.displayName
        bindDetails()
        bindActions()
    }

    private fun bindDetails() {
        binding.ipValue.text = deviceInfo.ipAddress
        binding.deviceNameValue.text = deviceInfo.displayName
        binding.hostnameValue.text = deviceInfo.hostName ?: "Not announced"
        binding.macValue.text = deviceInfo.macAddress ?: "Unavailable"
        binding.latencyValue.text = deviceInfo.latencyMs?.let { "$it ms" } ?: "Unavailable"
        binding.portsValue.text = deviceInfo.portSummaries.joinToString("\n").ifBlank { "None detected" }
        binding.servicesValue.text = deviceInfo.services.joinToString(", ").ifBlank { "No mapped services detected" }
        binding.notesValue.text = deviceInfo.notes.joinToString("\n").ifBlank { "No extra notes" }
    }

    private fun bindActions() {
        binding.sshButton.setOnClickListener {
            openTerminal(Protocol.SSH)
        }
        binding.telnetButton.setOnClickListener {
            openTerminal(Protocol.TELNET)
        }
        binding.sftpButton.setOnClickListener {
            startActivity(
                Intent(this, SftpActivity::class.java)
                    .putExtra(SftpActivity.EXTRA_HOST, deviceInfo.ipAddress)
            )
        }
        binding.packetButton.setOnClickListener {
            startActivity(
                Intent(this, PacketToolActivity::class.java)
                    .putExtra(PacketToolActivity.EXTRA_HOST, deviceInfo.ipAddress)
            )
        }
    }

    private fun openTerminal(protocol: Protocol) {
        startActivity(
            Intent(this, TerminalActivity::class.java)
                .putExtra(TerminalActivity.EXTRA_HOST, deviceInfo.ipAddress)
                .putExtra(TerminalActivity.EXTRA_PROTOCOL, protocol.name)
        )
    }

    companion object {
        const val EXTRA_DEVICE = "extra_device"
    }
}
