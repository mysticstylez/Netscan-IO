package io.netscan.app.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import io.netscan.app.data.BluetoothDeviceInfo
import java.util.concurrent.ConcurrentHashMap

class BluetoothScanner(private val context: Context) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? get() = bluetoothManager.adapter
    private val devices = ConcurrentHashMap<String, BluetoothDeviceInfo>()
    private var receiverRegistered = false
    private var callback: ((List<BluetoothDeviceInfo>) -> Unit)? = null

    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                    device?.let { upsert(it, if (rssi == Short.MIN_VALUE) null else rssi.toInt()) }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> publish()
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let { upsert(it, null) }
                }
            }
        }
    }

    fun isBluetoothAvailable(): Boolean = adapter != null

    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    fun hasRequiredPermissions(): Boolean {
        val scanPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val connectPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val fineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return scanPermission && connectPermission && fineLocation
    }

    fun requiredPermissions(): Array<String> {
        return buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }.toTypedArray()
    }

    fun start(onDevicesUpdated: (List<BluetoothDeviceInfo>) -> Unit): Boolean {
        if (!hasRequiredPermissions()) return false
        val bluetoothAdapter = adapter ?: return false
        callback = onDevicesUpdated
        registerReceiverIfNeeded()
        devices.clear()
        loadBondedDevices()
        if (bluetoothAdapter.isDiscovering) {
            bluetoothAdapter.cancelDiscovery()
        }
        val started = bluetoothAdapter.startDiscovery()
        publish()
        return started
    }

    fun stop() {
        adapter?.takeIf { it.isDiscovering }?.cancelDiscovery()
        callback = null
    }

    fun currentDevices(): List<BluetoothDeviceInfo> = devices.values.sortedWith(
        compareByDescending<BluetoothDeviceInfo> { it.isPaired }
            .thenBy { it.displayName.lowercase() }
            .thenBy { it.address }
    )

    fun release() {
        stop()
        if (receiverRegistered) {
            runCatching { context.unregisterReceiver(discoveryReceiver) }
            receiverRegistered = false
        }
    }

    private fun loadBondedDevices() {
        val bonded = runCatching { adapter?.bondedDevices.orEmpty() }.getOrDefault(emptySet())
        bonded.forEach { upsert(it, null) }
    }

    private fun upsert(device: BluetoothDevice, rssi: Int?) {
        val info = BluetoothDeviceInfo(
            address = device.address.orEmpty(),
            displayName = device.name?.takeIf { it.isNotBlank() } ?: "Unnamed Bluetooth device",
            rawName = device.name,
            bondStateLabel = bondStateLabel(device.bondState),
            typeLabel = typeLabel(device.type),
            classLabel = classLabel(device.bluetoothClass),
            rssi = rssi,
            isPaired = device.bondState == BluetoothDevice.BOND_BONDED
        )
        devices[info.address] = info
        publish()
    }

    private fun publish() {
        callback?.invoke(currentDevices())
    }

    private fun registerReceiverIfNeeded() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        context.registerReceiver(discoveryReceiver, filter)
        receiverRegistered = true
    }

    private fun bondStateLabel(state: Int): String = when (state) {
        BluetoothDevice.BOND_BONDED -> "Paired"
        BluetoothDevice.BOND_BONDING -> "Pairing"
        else -> "Unpaired"
    }

    private fun typeLabel(type: Int): String = when (type) {
        BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Classic"
        BluetoothDevice.DEVICE_TYPE_LE -> "BLE"
        BluetoothDevice.DEVICE_TYPE_DUAL -> "Dual"
        else -> "Unknown"
    }

    private fun classLabel(btClass: BluetoothClass?): String? {
        val deviceClass = btClass?.deviceClass ?: return null
        return when (deviceClass) {
            BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES -> "Headphones"
            BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER -> "Speaker"
            BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE -> "Hands-free"
            BluetoothClass.Device.COMPUTER_LAPTOP -> "Laptop"
            BluetoothClass.Device.COMPUTER_DESKTOP -> "Desktop computer"
            BluetoothClass.Device.PHONE_SMART -> "Smartphone"
            BluetoothClass.Device.PHONE_CELLULAR -> "Phone"
            BluetoothClass.Device.PERIPHERAL_KEYBOARD -> "Keyboard"
            BluetoothClass.Device.WEARABLE_WRIST_WATCH -> "Watch"
            BluetoothClass.Device.HEALTH_PULSE_RATE -> "Health sensor"
            else -> "Class $deviceClass"
        }
    }
}
