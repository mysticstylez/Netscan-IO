package io.netscan.app.data

data class BluetoothDeviceInfo(
    val address: String,
    val displayName: String,
    val rawName: String?,
    val bondStateLabel: String,
    val typeLabel: String,
    val classLabel: String?,
    val rssi: Int?,
    val isPaired: Boolean
)
