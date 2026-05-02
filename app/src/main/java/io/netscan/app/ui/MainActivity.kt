package io.netscan.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import io.netscan.app.data.DeviceInfo
import io.netscan.app.databinding.ActivityMainBinding
import io.netscan.app.network.NetworkScanner
import io.netscan.app.remote.Protocol
import io.netscan.app.ui.bluetooth.BluetoothScanActivity
import io.netscan.app.ui.device.DeviceAdapter
import io.netscan.app.ui.device.DeviceDetailActivity
import io.netscan.app.ui.packet.PacketToolActivity
import io.netscan.app.ui.sftp.SftpActivity
import io.netscan.app.ui.terminal.TerminalActivity
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var networkScanner: NetworkScanner
    private lateinit var deviceAdapter: DeviceAdapter

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        loadGatewayDetails()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        networkScanner = NetworkScanner(this)
        deviceAdapter = DeviceAdapter(
            onOpenDetails = ::openDevice,
            onOpenSsh = { openTerminal(it, Protocol.SSH) },
            onOpenTelnet = { openTerminal(it, Protocol.TELNET) },
            onOpenSftp = ::openSftp,
            onOpenPacketTool = ::openPacketTool
        )
        binding.deviceList.adapter = deviceAdapter
        binding.deviceList.layoutManager = LinearLayoutManager(this)
        binding.appToolbar.title = getString(io.netscan.app.R.string.app_name)

        binding.scanButton.setOnClickListener {
            runNetworkScan()
        }
        binding.refreshGatewayButton.setOnClickListener {
            loadGatewayDetails()
        }
        binding.quickSshButton.setOnClickListener {
            openManualTerminal(Protocol.SSH)
        }
        binding.quickTelnetButton.setOnClickListener {
            openManualTerminal(Protocol.TELNET)
        }
        binding.quickSftpButton.setOnClickListener {
            startActivity(Intent(this, SftpActivity::class.java))
        }
        binding.quickPacketButton.setOnClickListener {
            startActivity(Intent(this, PacketToolActivity::class.java))
        }
        binding.quickBluetoothButton.setOnClickListener {
            startActivity(Intent(this, BluetoothScanActivity::class.java))
        }

        requestHelpfulPermissions()
        loadGatewayDetails()
        runNetworkScan()
    }

    private fun requestHelpfulPermissions() {
        val needed = buildList {
            if (ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) add(Manifest.permission.ACCESS_FINE_LOCATION)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.NEARBY_WIFI_DEVICES
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.BLUETOOTH_SCAN)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }

    private fun loadGatewayDetails() {
        lifecycleScope.launch {
            val gateway = networkScanner.loadGatewayInfo()
            binding.networkTypeValue.text = gateway.networkName
            binding.localIpValue.text = if (gateway.localAddress.isBlank()) "Unavailable" else gateway.localAddress
            binding.gatewayValue.text = if (gateway.gatewayAddress.isBlank()) "Unavailable" else gateway.gatewayAddress
            binding.rangeValue.text = gateway.scanRangeLabel.ifBlank { "Unavailable" }
            binding.dnsValue.text = gateway.dnsServers.ifEmpty { listOf("Unavailable") }.joinToString("\n")
            binding.interfaceValue.text = gateway.interfaceName.ifBlank { "Unavailable" }
            binding.ssidValue.text = gateway.ssid ?: "Permission required or not on Wi-Fi"
            binding.meteredValue.text = if (gateway.metered) "Yes" else "No"
            binding.routesValue.text = gateway.routeSummary.ifEmpty { listOf("Unavailable") }.joinToString("\n")
        }
    }

    private fun runNetworkScan() {
        binding.scanButton.isEnabled = false
        binding.scanStatus.text = getString(io.netscan.app.R.string.scan_running)
        binding.emptyState.visibility = View.GONE
        lifecycleScope.launch {
            val devices = networkScanner.scanCurrentNetwork()
            deviceAdapter.submitList(devices)
            binding.scanButton.isEnabled = true
            binding.scanStatus.text = getString(
                io.netscan.app.R.string.scan_complete,
                devices.size
            )
            binding.deviceCountValue.text = devices.size.toString()
            binding.emptyState.visibility = if (devices.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun openDevice(deviceInfo: DeviceInfo) {
        startActivity(
            Intent(this, DeviceDetailActivity::class.java)
                .putExtra(DeviceDetailActivity.EXTRA_DEVICE, deviceInfo)
        )
    }

    private fun openTerminal(deviceInfo: DeviceInfo, protocol: Protocol) {
        startActivity(
            Intent(this, TerminalActivity::class.java)
                .putExtra(TerminalActivity.EXTRA_HOST, deviceInfo.ipAddress)
                .putExtra(TerminalActivity.EXTRA_PROTOCOL, protocol.name)
        )
    }

    private fun openManualTerminal(protocol: Protocol) {
        startActivity(
            Intent(this, TerminalActivity::class.java)
                .putExtra(TerminalActivity.EXTRA_PROTOCOL, protocol.name)
        )
    }

    private fun openSftp(deviceInfo: DeviceInfo) {
        startActivity(
            Intent(this, SftpActivity::class.java)
                .putExtra(SftpActivity.EXTRA_HOST, deviceInfo.ipAddress)
        )
    }

    private fun openPacketTool(deviceInfo: DeviceInfo) {
        startActivity(
            Intent(this, PacketToolActivity::class.java)
                .putExtra(PacketToolActivity.EXTRA_HOST, deviceInfo.ipAddress)
        )
    }
}
