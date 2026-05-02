package io.netscan.app.remote

enum class Protocol(val label: String, val defaultPort: Int) {
    SSH("SSH", 22),
    TELNET("Telnet", 23),
    SFTP("SFTP", 22)
}
