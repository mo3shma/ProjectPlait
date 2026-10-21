package app.plait

import app.plait.data.NoteJson
import app.plait.data.NoteLine
import app.plait.data.SavedNote
import app.plait.data.update.UpdateInfo
import app.plait.data.update.Updater
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket

class NoteJsonSafetyTest {
    private fun note(id: String) = SavedNote(id, "e", "Budget", 1L, 5000, "$id.wav", listOf(NoteLine(0, "hello")), emptyList())

    @Test fun oneDamagedNoteDoesNotHideTheOthers() {
        val root = JSONObject(NoteJson.encode(listOf(note("a"), note("b"), note("c"))))
        root.getJSONArray("notes").getJSONObject(1).remove("id")
        assertEquals(listOf("a", "c"), NoteJson.decode(root.toString()).map { it.id })
    }
}

class UpdaterFetchTest {
    private val bytes = ByteArray(300_000) { (it % 251).toByte() }

    /** A tiny one-request-at-a-time HTTP server that understands "Range: bytes=N-". */
    private fun serve(): ServerSocket = ServerSocket(0, 5, InetAddress.getByName("127.0.0.1")).also { server ->
        Thread {
            while (!server.isClosed) {
                try {
                    server.accept().use { c ->
                        val reader = c.getInputStream().bufferedReader()
                        var range: String? = null
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                            if (line.startsWith("Range:", ignoreCase = true)) range = line.substringAfter(':').trim()
                        }
                        val from = range?.removePrefix("bytes=")?.removeSuffix("-")?.toInt() ?: 0
                        val body = bytes.copyOfRange(from, bytes.size)
                        val status = if (range != null) "HTTP/1.1 206 Partial Content" else "HTTP/1.1 200 OK"
                        val head = status + "\r\nContent-Length: " + body.size + "\r\nConnection: close\r\n\r\n"
                        c.getOutputStream().apply { write(head.toByteArray()); write(body); flush() }
                    }
                } catch (_: Exception) { }
            }
        }.apply { isDaemon = true; start() }
    }

    private fun download(partFirst: Int?): File {
        val server = serve()
        try {
            val dir = File.createTempFile("upd", "").apply { delete(); mkdirs(); deleteOnExit() }
            val file = File(dir, "plait-9.apk")
            if (partFirst != null) File(file.path + ".part").writeBytes(bytes.copyOf(partFirst))
            val info = UpdateInfo("9", "", "http://127.0.0.1:${server.localPort}/plait.apk", bytes.size.toLong())
            runBlocking { Updater.fetch(info, file) }
            return file
        } finally { server.close() }
    }

    @Test fun downloadsTheWholeFile() = assertArrayEquals(bytes, download(null).readBytes())

    @Test fun carriesOnFromAPartialDownload() = assertArrayEquals(bytes, download(123_456).readBytes())
}
