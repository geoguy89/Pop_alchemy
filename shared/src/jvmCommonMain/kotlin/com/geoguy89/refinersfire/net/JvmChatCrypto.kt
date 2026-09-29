package com.geoguy89.refinersfire.net

import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-end chat encryption: X25519 key agreement, HKDF-SHA256, AES-256-GCM.
 * Bouncy Castle supplies X25519 because Android only has it built in from API 33.
 */
object JvmChatCrypto : ChatCrypto {
    private val random = SecureRandom()
    private val b64 = Base64.getEncoder()
    private val unb64 = Base64.getDecoder()

    override fun newKeyPair(): ChatKeyPair {
        val priv = X25519PrivateKeyParameters(random)
        return ChatKeyPair(b64.encodeToString(priv.generatePublicKey().encoded), b64.encodeToString(priv.encoded))
    }

    private fun key(myPrivate: String, theirPublic: String, idA: String, idB: String): ByteArray {
        val shared = ByteArray(32)
        X25519Agreement().apply { init(X25519PrivateKeyParameters(unb64.decode(myPrivate), 0)) }
            .calculateAgreement(X25519PublicKeyParameters(unb64.decode(theirPublic), 0), shared, 0)
        // Both sides order the ids the same way, so they derive the same key.
        val info = listOf(idA, idB).sorted().joinToString("|").encodeToByteArray()
        val out = ByteArray(32)
        HKDFBytesGenerator(SHA256Digest()).apply { init(HKDFParameters(shared, "refinersfire-chat-v1".encodeToByteArray(), info)) }
            .generateBytes(out, 0, out.size)
        return out
    }

    override fun seal(myPrivate: String, theirPublic: String, fromId: String, toId: String, text: String): Sealed {
        val nonce = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key(myPrivate, theirPublic, fromId, toId), "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD("$fromId>$toId".encodeToByteArray())
        return Sealed(b64.encodeToString(nonce), b64.encodeToString(cipher.doFinal(text.encodeToByteArray())))
    }

    override fun open(myPrivate: String, theirPublic: String, fromId: String, toId: String, sealed: Sealed): String? = try {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key(myPrivate, theirPublic, fromId, toId), "AES"), GCMParameterSpec(128, unb64.decode(sealed.nonce)))
        cipher.updateAAD("$fromId>$toId".encodeToByteArray())
        cipher.doFinal(unb64.decode(sealed.ct)).decodeToString()
    } catch (e: Exception) {
        null // Tampered with, or sealed to an old key.
    }

    override fun safetyCode(publicA: String, publicB: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest(listOf(publicA, publicB).sorted().joinToString("|").encodeToByteArray())
        // Five groups of five digits, the same on both phones.
        return (0 until 5).joinToString(" ") { g ->
            val v = ((d[g * 3].toInt() and 0xFF) shl 16) or ((d[g * 3 + 1].toInt() and 0xFF) shl 8) or (d[g * 3 + 2].toInt() and 0xFF)
            (v % 100_000).toString().padStart(5, '0')
        }
    }
}
