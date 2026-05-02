package io.netscan.app.data

import java.io.Serializable

data class DeviceInfo(
    val ipAddress: String,
    val displayName: String,
    val hostName: String?,
    val macAddress: String?,
    val latencyMs: Int?,
    val openPorts: List<Int>,
    val services: List<String>,
    val portSummaries: List<String>,
    val notes: List<String>
) : Serializable
