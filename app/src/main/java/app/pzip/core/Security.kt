package app.pzip.core

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

object Security {
    private const val ITERATIONS = 120_000

    /** Salted PBKDF2 hash of a PIN: "salt:hash" in Base64. */
    fun hashPin(pin: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return b64(salt) + ":" + b64(derive(pin, salt))
    }

    fun verifyPin(pin: String, stored: String): Boolean {
        val parts = stored.split(":")
        if (parts.size != 2) return false
        val salt = Base64.decode(parts[0], Base64.NO_WRAP)
        return MessageDigest.isEqual(derive(pin, salt), Base64.decode(parts[1], Base64.NO_WRAP))
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded.also { spec.clearPassword() }
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)

    fun passwordStrength(pw: CharArray): Int {
        if (pw.isEmpty()) return 0
        var score = 0
        if (pw.size >= 8) score++
        if (pw.size >= 12) score++
        if (pw.any { it.isLowerCase() } && pw.any { it.isUpperCase() }) score++
        if (pw.any { it.isDigit() }) score++
        if (pw.any { !it.isLetterOrDigit() }) score++
        return score.coerceAtMost(4)
    }

    fun generatePassword(length: Int = 16): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#\$%^&*-_+="
        val rnd = SecureRandom()
        return (1..length).map { chars[rnd.nextInt(chars.length)] }.joinToString("")
    }

    // ---------- Hardware-backed key for the Safe folder ----------

    private const val KEY_ALIAS = "pzip_safe_v1"

    fun safeKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    /**
     * Streams data as independently authenticated AES-256-GCM chunks, so files of any size
     * can be encrypted without loading them into memory. Chunk index and a final-chunk flag
     * are bound as associated data, which blocks reordering and truncation.
     */
    object Chunked {
        private val MAGIC = byteArrayOf('P'.code.toByte(), 'Z'.code.toByte(), 'S'.code.toByte(), '1'.code.toByte())
        private const val CHUNK = 256 * 1024

        private fun aad(index: Long, last: Boolean) = ByteBuffer.allocate(9).putLong(index).put(if (last) 1 else 0).array()

        fun encrypt(key: SecretKey, input: InputStream, output: OutputStream, onBytes: (Int) -> Unit = {}) {
            val out = DataOutputStream(output)
            out.write(MAGIC)
            val buf = ByteArray(CHUNK)
            var index = 0L
            var pending = readFully(input, buf)
            while (true) {
                val next = ByteArray(CHUNK)
                val nextLen = if (pending == CHUNK) readFully(input, next) else 0
                val last = nextLen <= 0
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, key)
                cipher.updateAAD(aad(index, last))
                val ct = cipher.doFinal(buf, 0, maxOf(pending, 0))
                out.write(cipher.iv)
                out.writeInt(ct.size)
                out.write(ct)
                onBytes(maxOf(pending, 0))
                if (last) break
                System.arraycopy(next, 0, buf, 0, nextLen)
                pending = nextLen
                index++
            }
            out.flush()
        }

        fun decrypt(key: SecretKey, input: InputStream, output: OutputStream, onBytes: (Int) -> Unit = {}) {
            val inp = DataInputStream(input)
            val magic = ByteArray(4)
            inp.readFully(magic)
            if (!magic.contentEquals(MAGIC)) throw IOException("Not a pzip safe file")
            var index = 0L
            while (true) {
                val iv = ByteArray(12)
                try {
                    inp.readFully(iv)
                } catch (e: EOFException) {
                    throw IOException("File is truncated")
                }
                val len = inp.readInt()
                if (len < 16 || len > CHUNK + 16) throw IOException("Damaged safe file")
                val ct = ByteArray(len)
                inp.readFully(ct)
                val atEnd = inp.available() == 0 && input.read().also { if (it >= 0) throw IOException("Unexpected data") } < 0
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
                cipher.updateAAD(aad(index, atEnd))
                val pt = cipher.doFinal(ct)
                output.write(pt)
                onBytes(pt.size)
                if (atEnd) break
                index++
            }
            output.flush()
        }

        private fun readFully(input: InputStream, buf: ByteArray): Int {
            var total = 0
            while (total < buf.size) {
                val n = input.read(buf, total, buf.size - total)
                if (n < 0) break
                total += n
            }
            return total
        }
    }
}
