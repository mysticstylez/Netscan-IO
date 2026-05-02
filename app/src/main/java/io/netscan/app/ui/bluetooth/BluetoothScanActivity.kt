package io.netscan.app.ui.bluetooth

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import io.netscan.app.bluetooth.BluetoothScanner
import io.netscan.app.databinding.ActivityBluetoothScanBinding

class BluetoothScanActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBluetoothScanBinding
    private lateinit var bluetoothScanner: BluetoothScanner
    private lateinit var adapter: BluetoothDeviceAdapter

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        startDiscovery()
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startDiscovery()
        } else {
            Toast.makeText(this, "Bluetooth needs to be enabled for scanning.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBluetoothScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "Bluetooth Scan"
        bluetoothScanner = BluetoothScanner(this)
        adapter = BluetoothDeviceAdapter()
        binding.deviceList.adapter = adapter
        binding.deviceList.layoutManager = LinearLayoutManager(this)

        binding.startScanButton.setOnClickListener { startDiscovery() }
        binding.stopScanButton.setOnClickListener {
            bluetoothScanner.stop()
            binding.statusValue.text = "Bluetooth scan stopped."
            binding.progressBar.visibility = View.GONE
        }

        updateCapabilityStatus()
    }

    private fun updateCapabilityStatus() {
        binding.statusValue.text = when {
            !bluetoothScanner.isBluetoothAvailable() -> "Bluetooth is not available on this device."
            !bluetoothScanner.isBluetoothEnabled() -> "Bluetooth is off. Turn it on to scan nearby devices."
            else -> "Ready to scan nearby Bluetooth devices."
        }
    }

    private fun startDiscovery() {
        if (!bluetoothScanner.isBluetoothAvailable()) {
            updateCapabilityStatus()
            return
        }
        if (!bluetoothScanner.hasRequiredPermissions()) {
            permissionLauncher.launch(bluetoothScanner.requiredPermissions())
            return
        }
        if (!bluetoothScanner.isBluetoothEnabled()) {
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.statusValue.text = "Scanning for nearby Bluetooth devices..."
        binding.emptyState.visibility = View.GONE

        var discoveryStarted = false
        discoveryStarted = bluetoothScanner.start { devices ->
            runOnUiThread {
                adapter.submitList(devices)
                binding.deviceCountValue.text = devices.size.toString()
                binding.emptyState.visibility = if (devices.isEmpty()) View.VISIBLE else View.GONE
                binding.statusValue.text = if (discoveryStarted) {
                    "Scanning... found ${devices.size} device(s)."
                } else {
                    "Bluetooth scan could not start."
                }
            }
        }

        if (!discoveryStarted) {
            binding.progressBar.visibility = View.GONE
            binding.statusValue.text = "Bluetooth scan could not start."
        }
    }

    override fun onDestroy() {
        bluetoothScanner.release()
        super.onDestroy()
    }
}
