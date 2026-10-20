package app.plait.data.crypto

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Passphrase protection for backup files. The key comes from the passphrase with PBKDF2-HMAC-SHA256 (a random salt,
 * 210,000 rounds); the data is cut into 1 MB chunks, each sealed with AES-256-GCM under its own nonce and bound to its
 * position and to whether it is the last, so chunks cannot be reordered, dropped or cut off without it being noticed.
 * There is no recovery: a forgotten passphrase means the file cannot be opened.
 */
object Crypt {
    val MAGIC = "PLAITENC1".toByteArray(Charsets.US_ASCII)
    private const val SALT = 16
    private const val NONCE = 12
    private const val TAG_BITS = 128
    private const val ROUNDS = 210_000
    private const val FINAL_FLAG = 0x80000000.toInt()

    class BadPassphrase : Exception("Wrong passphrase, or the file is damaged.")

    fun isEncrypted(head: ByteArray): Boolean = head.size >= MAGIC.size && head.copyOf(MAGIC.size).contentEquals(MAGIC)

    private fun key(pass: CharArray, salt: ByteArray, rounds: Int): SecretKey {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(f.generateSecret(PBEKeySpec(pass, salt, rounds, 256)).encoded, "AES")
    }

    private fun nonceFor(base: ByteArray, index: Long): ByteArray {
        val n = base.copyOf()
        for (i in 0 until 8) n[NONCE - 1 - i] = (n[NONCE - 1 - i].toInt() xor ((index shr (8 * i)) and 0xFF).toInt()).toByte()
        return n
    }

    private fun aad(index: Long, last: Boolean): ByteArray =
        ByteArray(9) { i -> if (i < 8) ((index shr (8 * (7 - i))) and 0xFF).toByte() else (if (last) 1 else 0) }

    /** Wraps [out]: everything written is sealed. Close it to write the final chunk. */
    fun encrypt(out: OutputStream, passphrase: CharArray, chunk: Int = 1 shl 20, rounds: Int = ROUNDS): OutputStream {
        val rnd = SecureRandom()
        val salt = ByteArray(SALT).also { rnd.nextBytes(it) }
        val base = ByteArray(NONCE).also { rnd.nextBytes(it) }
        val k = key(passphrase, salt, rounds)
        out.write(MAGIC); out.write(salt); out.write(base)
        out.write(byteArrayOf((rounds shr 24).toByte(), (rounds shr 16).toByte(), (rounds shr 8).toByte(), rounds.toByte()))
        return object : OutputStream() {
            private val buf = ByteArray(chunk)
            private var n = 0
            private var index = 0L
            private var closed = false

            private fun seal(last: Boolean) {
                val c = Cipher.getInstance("AES/GCM/NoPadding")
                c.init(Cipher.ENCRYPT_MODE, k, GCMParameterSpec(TAG_BITS, nonceFor(base, index)))
                c.updateAAD(aad(index, last))
                val sealed = c.doFinal(buf, 0, n)
                val len = sealed.size or (if (last) FINAL_FLAG else 0)
                out.write(byteArrayOf((len shr 24).toByte(), (len shr 16).toByte(), (len shr 8).toByte(), len.toByte()))
                out.write(sealed)
                index++; n = 0
            }

            override fun write(b: Int) { write(byteArrayOf(b.toByte()), 0, 1) }

            override fun write(b: ByteArray, off: Int, len: Int) {
                var o = off; var left = len
                while (left > 0) {
                    if (n == chunk) seal(last = false) // more data is coming, so this chunk is not the last
                    val take = minOf(left, chunk - n)
                    System.arraycopy(b, o, buf, n, take)
                    n += take; o += take; left -= take
                }
            }

            override fun flush() = out.flush()

            override fun close() {
                if (closed) return
                closed = true
                seal(last = true)
                out.flush(); out.close()
            }
        }
    }

    /** Reads what [encrypt] wrote. The magic bytes must already have been confirmed with [isEncrypted] and consumed. */
    fun decrypt(input: InputStream, passphrase: CharArray): InputStream {
        val salt = ByteArray(SALT).also { readFully(input, it) }
        val base = ByteArray(NONCE).also { readFully(input, it) }
        val r = ByteArray(4).also { readFully(input, it) }
        val rounds = ((r[0].toInt() and 0xFF) shl 24) or ((r[1].toInt() and 0xFF) shl 16) or ((r[2].toInt() and 0xFF) shl 8) or (r[3].toInt() and 0xFF)
        if (rounds !in 1_000..5_000_000) throw BadPassphrase()
        val k = key(passphrase, salt, rounds)
        return object : InputStream() {
            private var current = ByteArray(0)
            private var pos = 0
            private var index = 0L
            private var done = false

            private fun next(): Boolean {
                if (done) return false
                val h = ByteArray(4)
                try { readFully(input, h) } catch (e: EOFException) { throw BadPassphrase() } // cut off before the last chunk
                val raw = ((h[0].toInt() and 0xFF) shl 24) or ((h[1].toInt() and 0xFF) shl 16) or ((h[2].toInt() and 0xFF) shl 8) or (h[3].toInt() and 0xFF)
                val last = raw and FINAL_FLAG != 0
                val len = raw and FINAL_FLAG.inv()
                if (len < TAG_BITS / 8 || len > (1 shl 20) + 64) throw BadPassphrase()
                val sealed = ByteArray(len)
                try { readFully(input, sealed) } catch (e: EOFException) { throw BadPassphrase() }
                try {
                    val c = Cipher.getInstance("AES/GCM/NoPadding")
                    c.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(TAG_BITS, nonceFor(base, index)))
                    c.updateAAD(aad(index, last))
                    current = c.doFinal(sealed)
                } catch (e: GeneralSecurityException) {
                    throw BadPassphrase()
                }
                pos = 0; index++; done = last
                return true
            }

            override fun read(): Int {
                while (pos >= current.size) { if (!next()) return -1 }
                return current[pos++].toInt() and 0xFF
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (len == 0) return 0
                while (pos >= current.size) { if (!next()) return -1 }
                val take = minOf(len, current.size - pos)
                System.arraycopy(current, pos, b, off, take)
                pos += take
                return take
            }
        }
    }

    private fun readFully(input: InputStream, b: ByteArray) {
        var o = 0
        while (o < b.size) {
            val n = input.read(b, o, b.size - o)
            if (n < 0) throw EOFException()
            o += n
        }
    }

    fun encryptBytes(data: ByteArray, pass: CharArray, rounds: Int = ROUNDS): ByteArray =
        ByteArrayOutputStream().also { bo -> encrypt(bo, pass, rounds = rounds).use { it.write(data) } }.toByteArray()

    fun decryptBytes(data: ByteArray, pass: CharArray): ByteArray {
        val ins = data.inputStream()
        val head = ByteArray(MAGIC.size).also { readFully(ins, it) }
        if (!isEncrypted(head)) throw BadPassphrase()
        return decrypt(ins, pass).readBytes()
    }
}
