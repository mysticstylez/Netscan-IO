package io.netscan.app.ui.terminal

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.netscan.app.databinding.ActivityTerminalBinding
import io.netscan.app.remote.Protocol
import io.netscan.app.remote.RemoteRepository
import io.netscan.app.remote.RemoteTerminalSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TerminalActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTerminalBinding
    private lateinit var protocol: Protocol
    private lateinit var repository: RemoteRepository
    private var session: RemoteTerminalSession? = null
    private var readJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTerminalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = RemoteRepository(this)
        protocol = Protocol.valueOf(intent.getStringExtra(EXTRA_PROTOCOL) ?: Protocol.SSH.name)
        supportActionBar?.title = "${protocol.label} Session"

        binding.hostInput.setText(intent.getStringExtra(EXTRA_HOST).orEmpty())
        binding.portInput.setText(protocol.defaultPort.toString())

        binding.connectButton.setOnClickListener { connect() }
        binding.sendButton.setOnClickListener { sendCommand() }
        binding.disconnectButton.setOnClickListener { disconnect() }
    }

    private fun connect() {
        val host = binding.hostInput.text.toString().trim()
        val port = binding.portInput.text.toString().trim().toIntOrNull() ?: protocol.defaultPort
        val username = binding.usernameInput.text.toString().trim()
        val password = binding.passwordInput.text.toString()

        lifecycleScope.launch {
            runCatching {
                session = when (protocol) {
                    Protocol.SSH -> repository.openSshShell(host, port, username, password, ::appendOutput)
                    Protocol.TELNET -> repository.openTelnetShell(host, port, ::appendOutput)
                    Protocol.SFTP -> error("SFTP does not use the terminal screen.")
                }
                readJob = launch(Dispatchers.IO) {
                    session?.readLoop?.invoke()
                }
            }.onSuccess {
                appendOutput("[connected to $host:${port} via ${protocol.label.lowercase()}]\n")
            }.onFailure {
                Toast.makeText(this@TerminalActivity, it.message ?: "Connection failed", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun sendCommand() {
        val line = binding.commandInput.text.toString()
        if (line.isBlank()) return
        lifecycleScope.launch {
            runCatching {
                session?.sendLine(line) ?: error("Not connected.")
            }.onSuccess {
                binding.commandInput.text?.clear()
            }.onFailure {
                Toast.makeText(this@TerminalActivity, it.message ?: "Send failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun disconnect() {
        readJob?.cancel()
        session?.close()
        session = null
        appendOutput("\n[disconnected]\n")
    }

    private fun appendOutput(chunk: String) {
        runOnUiThread {
            binding.outputView.append(chunk)
            binding.outputScroll.post {
                binding.outputScroll.fullScroll(android.view.View.FOCUS_DOWN)
            }
        }
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_HOST = "extra_host"
        const val EXTRA_PROTOCOL = "extra_protocol"
    }
}
