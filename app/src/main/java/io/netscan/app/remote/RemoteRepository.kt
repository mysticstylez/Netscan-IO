package io.netscan.app.remote

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import io.netscan.app.data.RemoteFileItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.telnet.TelnetClient
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.Vector

class RemoteRepository(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun openSshShell(
        host: String,
        port: Int,
        username: String,
        password: String,
        onText: (String) -> Unit
    ): RemoteTerminalSession = withContext(ioDispatcher) {
        val jsch = JSch()
        val session = jsch.getSession(username, host, port).apply {
            setPassword(password)
            setConfig("StrictHostKeyChecking", "no")
            timeout = 10_000
            connect(10_000)
        }
        val shell = session.openChannel("shell") as com.jcraft.jsch.ChannelShell
        shell.setPty(true)
        shell.connect(10_000)
        val reader = BufferedReader(InputStreamReader(shell.inputStream))
        val writer = OutputStreamWriter(shell.outputStream)
        RemoteTerminalSession(
            writer = writer,
            closeAction = {
                shell.disconnect()
                session.disconnect()
            },
            readLoop = {
                val buffer = CharArray(1024)
                while (shell.isConnected) {
                    val read = reader.read(buffer)
                    if (read <= 0) break
                    onText(String(buffer, 0, read))
                }
            }
        )
    }

    suspend fun openTelnetShell(
        host: String,
        port: Int,
        onText: (String) -> Unit
    ): RemoteTerminalSession = withContext(ioDispatcher) {
        val client = TelnetClient().apply {
            connect(host, port)
        }
        val reader = BufferedReader(InputStreamReader(client.inputStream))
        val writer = OutputStreamWriter(client.outputStream)
        RemoteTerminalSession(
            writer = writer,
            closeAction = { client.disconnect() },
            readLoop = {
                val buffer = CharArray(1024)
                while (client.isConnected) {
                    val read = reader.read(buffer)
                    if (read <= 0) break
                    onText(String(buffer, 0, read))
                }
            }
        )
    }

    suspend fun connectSftp(
        host: String,
        port: Int,
        username: String,
        password: String
    ): SftpConnection = withContext(ioDispatcher) {
        val jsch = JSch()
        val session = jsch.getSession(username, host, port).apply {
            setPassword(password)
            setConfig("StrictHostKeyChecking", "no")
            timeout = 10_000
            connect(10_000)
        }
        val channel = session.openChannel("sftp") as ChannelSftp
        channel.connect(10_000)
        SftpConnection(context, session, channel)
    }
}

class RemoteTerminalSession(
    private val writer: OutputStreamWriter,
    private val closeAction: () -> Unit,
    val readLoop: () -> Unit
) {
    suspend fun sendLine(line: String) = withContext(Dispatchers.IO) {
        writer.write(line)
        writer.write("\n")
        writer.flush()
    }

    fun close() {
        closeAction()
    }
}

class SftpConnection(
    private val context: Context,
    private val session: Session,
    private val channel: ChannelSftp
) {
    suspend fun list(path: String): List<RemoteFileItem> = withContext(Dispatchers.IO) {
        @Suppress("UNCHECKED_CAST")
        val entries = channel.ls(path) as Vector<ChannelSftp.LsEntry>
        entries
            .filter { it.filename != "." }
            .map { entry ->
                val fullPath = if (path.endsWith("/")) "$path${entry.filename}" else "$path/${entry.filename}"
                RemoteFileItem(
                    name = entry.filename,
                    path = fullPath,
                    isDirectory = entry.attrs.isDir,
                    sizeBytes = entry.attrs.size.toLong()
                )
            }
            .sortedWith(compareByDescending<RemoteFileItem> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    suspend fun download(remotePath: String): Uri? = withContext(Dispatchers.IO) {
        val fileName = remotePath.substringAfterLast("/")
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Netscan IO")
        }
        val resolver = context.contentResolver
        val targetUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        targetUri?.let { uri ->
            resolver.openOutputStream(uri)?.use { output ->
                channel.get(remotePath, output)
            }
        }
        targetUri
    }

    suspend fun upload(sourceUri: Uri, remoteDirectory: String): String = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val fileName = queryDisplayName(resolver, sourceUri) ?: "upload.bin"
        resolver.openInputStream(sourceUri)?.use { input ->
            val remotePath = if (remoteDirectory.endsWith("/")) {
                "$remoteDirectory$fileName"
            } else {
                "$remoteDirectory/$fileName"
            }
            channel.put(input, remotePath)
            remotePath
        } ?: error("Unable to open source file.")
    }

    fun close() {
        channel.disconnect()
        session.disconnect()
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? {
        val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
        return resolver.query(uri, projection, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            if (index != -1 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }
}
