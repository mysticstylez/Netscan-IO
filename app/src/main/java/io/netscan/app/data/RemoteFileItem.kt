package io.netscan.app.data

data class RemoteFileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long
)
