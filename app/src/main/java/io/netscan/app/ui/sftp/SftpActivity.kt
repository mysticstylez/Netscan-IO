package io.netscan.app.ui.sftp

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import io.netscan.app.data.RemoteFileItem
import io.netscan.app.databinding.ActivitySftpBinding
import io.netscan.app.remote.RemoteRepository
import io.netscan.app.remote.SftpConnection
import kotlinx.coroutines.launch

class SftpActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySftpBinding
    private lateinit var repository: RemoteRepository
    private lateinit var adapter: RemoteFileAdapter
    private var connection: SftpConnection? = null
    private var currentPath: String = "."

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) upload(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySftpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = RemoteRepository(this)
        adapter = RemoteFileAdapter(::handleFileClick)
        binding.fileList.adapter = adapter
        binding.fileList.layoutManager = LinearLayoutManager(this)

        binding.hostInput.setText(intent.getStringExtra(EXTRA_HOST).orEmpty())
        binding.portInput.setText("22")

        binding.connectButton.setOnClickListener { connect() }
        binding.upButton.setOnClickListener { navigateUp() }
        binding.uploadButton.setOnClickListener { openDocument.launch(arrayOf("*/*")) }
    }

    private fun connect() {
        val host = binding.hostInput.text.toString().trim()
        val port = binding.portInput.text.toString().trim().toIntOrNull() ?: 22
        val username = binding.usernameInput.text.toString().trim()
        val password = binding.passwordInput.text.toString()

        lifecycleScope.launch {
            runCatching {
                connection = repository.connectSftp(host, port, username, password)
                currentPath = "."
                refreshListing()
            }.onFailure {
                Toast.makeText(this@SftpActivity, it.message ?: "SFTP failed", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun refreshListing() {
        lifecycleScope.launch {
            runCatching {
                val items = connection?.list(currentPath) ?: error("Not connected.")
                binding.pathValue.text = currentPath
                adapter.submitList(items)
            }.onFailure {
                Toast.makeText(this@SftpActivity, it.message ?: "Listing failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleFileClick(item: RemoteFileItem) {
        if (item.isDirectory) {
            currentPath = item.path
            refreshListing()
        } else {
            lifecycleScope.launch {
                runCatching {
                    connection?.download(item.path)
                }.onSuccess { uri ->
                    Toast.makeText(
                        this@SftpActivity,
                        if (uri != null) "Downloaded to Downloads/Netscan IO" else "Download failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }.onFailure {
                    Toast.makeText(this@SftpActivity, it.message ?: "Download failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun navigateUp() {
        currentPath = if (currentPath == "." || currentPath == "/") {
            "."
        } else {
            currentPath.substringBeforeLast("/", "")
                .ifBlank { "/" }
        }
        refreshListing()
    }

    private fun upload(uri: Uri) {
        lifecycleScope.launch {
            runCatching {
                connection?.upload(uri, currentPath) ?: error("Not connected.")
            }.onSuccess {
                Toast.makeText(this@SftpActivity, "Uploaded to $it", Toast.LENGTH_SHORT).show()
                refreshListing()
            }.onFailure {
                Toast.makeText(this@SftpActivity, it.message ?: "Upload failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        connection?.close()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_HOST = "extra_host"
    }
}
