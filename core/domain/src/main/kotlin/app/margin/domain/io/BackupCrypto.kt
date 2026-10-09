package app.margin.domain.io

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Passphrase-encrypted backups (docs/BACKUP_AND_RECOVERY.md):
 *  - Key: PBKDF2-HMAC-SHA256, random 16-byte salt, [DEFAULT_ITERATIONS] iterations → 256-bit AES key.
 *  - Data: AES-256-GCM in 1 MiB segments (STREAM construction). Each segment has its own nonce
 *    (random 7-byte prefix ‖ 4-byte big-endian segment counter ‖ 1-byte last-segment flag) and is
 *    authenticated with the file header as AAD, so truncation, reordering or header tampering
 *    fails decryption. Memory use is bounded regardless of backup size.
 *  - No custom primitives; only JCA AES/GCM and PBKDF2, available on Android and the JVM.
 * There is no recovery without the passphrase — by design.
 */
object BackupCrypto {
    private val MAGIC = "MRGNENC1".toByteArray(Charsets.US_ASCII)
    const val DEFAULT_ITERATIONS = 210_000
    private const val SEGMENT = 1 shl 20
    private const val TAG_BITS = 128
    private const val MIN_ITERATIONS = 10_000
    private const val MAX_ITERATIONS = 10_000_000

    class WrongPassphraseOrCorrupt : Exception("Wrong passphrase, or the backup is damaged")
    class NotEncrypted : Exception("Not an encrypted Margin backup")

    fun isEncrypted(head: ByteArray): Boolean = head.size >= MAGIC.size && head.copyOf(MAGIC.size).contentEquals(MAGIC)

    private fun deriveKey(pass: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(pass, salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun nonce(prefix: ByteArray, counter: Int, last: Boolean): ByteArray =
        ByteBuffer.allocate(12).put(prefix).putInt(counter).put(if (last) 1 else 0).array()

    /** Encrypts [input] to [output]. Does not close either stream. */
    fun encrypt(input: InputStream, output: OutputStream, passphrase: CharArray, iterations: Int = DEFAULT_ITERATIONS, random: SecureRandom = SecureRandom()) {
        require(passphrase.isNotEmpty()) { "empty passphrase" }
        val salt = ByteArray(16).also(random::nextBytes)
        val prefix = ByteArray(7).also(random::nextBytes)
        val header = ByteBuffer.allocate(MAGIC.size + 4 + 16 + 7).put(MAGIC).putInt(iterations).put(salt).put(prefix).array()
        output.write(header)
        val key = deriveKey(passphrase, salt, iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        var counter = 0
        var current = readFully(input, SEGMENT)
        while (true) {
            val next = if (current.size == SEGMENT) readFully(input, SEGMENT) else ByteArray(0)
            val last = next.isEmpty()
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce(prefix, counter, last)))
            cipher.updateAAD(header)
            val ct = cipher.doFinal(current)
            output.write(ByteBuffer.allocate(4).putInt(ct.size).array())
            output.write(ct)
            if (last) break
            counter++
            if (counter == Int.MAX_VALUE) throw GeneralSecurityException("backup too large")
            current = next
        }
        output.flush()
    }

    /** Decrypts to [output]. Throws [WrongPassphraseOrCorrupt] on any authentication failure. */
    fun decrypt(input: InputStream, output: OutputStream, passphrase: CharArray) {
        val pin = PushbackInputStream(input, 1)
        val din = DataInputStream(pin)
        val header = ByteArray(MAGIC.size + 4 + 16 + 7)
        try { din.readFully(header) } catch (e: EOFException) { throw NotEncrypted() }
        if (!isEncrypted(header)) throw NotEncrypted()
        val bb = ByteBuffer.wrap(header, MAGIC.size, header.size - MAGIC.size)
        val iterations = bb.int
        if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) throw WrongPassphraseOrCorrupt()
        val salt = ByteArray(16).also { bb.get(it) }
        val prefix = ByteArray(7).also { bb.get(it) }
        val key = deriveKey(passphrase, salt, iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        var counter = 0
        while (true) {
            val len = try { din.readInt() } catch (e: EOFException) { throw WrongPassphraseOrCorrupt() } // truncated
            if (len < 16 || len > SEGMENT + 16) throw WrongPassphraseOrCorrupt()
            val ct = ByteArray(len)
            try { din.readFully(ct) } catch (e: EOFException) { throw WrongPassphraseOrCorrupt() }
            // The final segment is the one followed by end-of-stream; its nonce flag must match.
            val peek = pin.read()
            val last = peek < 0
            if (!last) pin.unread(peek)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce(prefix, counter, last)))
            cipher.updateAAD(header)
            val pt = try { cipher.doFinal(ct) } catch (e: GeneralSecurityException) { throw WrongPassphraseOrCorrupt() }
            output.write(pt)
            if (last) break
            counter++
        }
        output.flush()
    }

    private fun readFully(input: InputStream, max: Int): ByteArray {
        val buf = ByteArrayOutputStream(minOf(max, 64 * 1024))
        val chunk = ByteArray(64 * 1024)
        var total = 0
        while (total < max) {
            val n = input.read(chunk, 0, minOf(chunk.size, max - total))
            if (n < 0) break
            buf.write(chunk, 0, n); total += n
        }
        return buf.toByteArray()
    }
}
