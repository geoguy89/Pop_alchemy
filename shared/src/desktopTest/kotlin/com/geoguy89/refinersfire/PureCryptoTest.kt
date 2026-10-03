package com.geoguy89.refinersfire

import com.geoguy89.refinersfire.net.AesGcm
import com.geoguy89.refinersfire.net.JvmChatCrypto
import com.geoguy89.refinersfire.net.PureCrypto
import com.geoguy89.refinersfire.net.Sha256
import com.geoguy89.refinersfire.net.X25519
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The web version's chat crypto, against published test vectors and the phones' (Bouncy Castle) implementation. */
class PureCryptoTest {
    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(2 * it, 2 * it + 2).toInt(16).toByte() }
    private fun ByteArray.hex() = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    @Test
    fun sha256AndHkdfMatchTheirStandards() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Sha256.digest("abc".encodeToByteArray()).hex())
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Sha256.digest(ByteArray(0)).hex())
        // A message longer than one block.
        assertEquals("248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            Sha256.digest("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray()).hex())
        // RFC 5869, test case 1.
        val okm = Sha256.hkdf(ByteArray(22) { 0x0b }, hex("000102030405060708090a0b0c"), hex("f0f1f2f3f4f5f6f7f8f9"), 42)
        assertEquals("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865", okm.hex())
    }

    @Test
    fun x25519MatchesRfc7748() {
        val alice = hex("77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a")
        val bob = hex("5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb")
        assertEquals("8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a", X25519.publicKey(alice).hex())
        assertEquals("de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f", X25519.publicKey(bob).hex())
        val shared = "4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742"
        assertEquals(shared, X25519.scalarMult(alice, X25519.publicKey(bob)).hex())
        assertEquals(shared, X25519.scalarMult(bob, X25519.publicKey(alice)).hex())
    }

    @Test
    fun aesGcmMatchesNist() {
        // The GCM specification's test case 16 (AES-256, 96-bit nonce, with additional data).
        val k = hex("feffe9928665731c6d6a8f9467308308feffe9928665731c6d6a8f9467308308")
        val iv = hex("cafebabefacedbaddecaf888")
        val p = hex("d9313225f88406e5a55909c5aff5269a86a7a9531534f7da2e4c303d8a318a721c3c0c95956809532fcf0e2449a6b525b16aedf5aa0de657ba637b39")
        val a = hex("feedfacedeadbeeffeedfacedeadbeefabaddad2")
        val expected = "522dc1f099567d07f47f37a32a84427d643a8cdcbfe5c0c97598a2bd2555d1aa8cb08e48590dbb3da7b08b1056828838c5f61e6393ba7a0abcc9f662" +
            "76fc6ece0f4e1768cddf8853bb2d551b"
        val sealed = AesGcm.seal(k, iv, a, p)
        assertEquals(expected, sealed.hex())
        assertArrayEquals(p, AesGcm.open(k, iv, a, sealed))
        sealed[3] = (sealed[3].toInt() xor 1).toByte()
        assertNull("a flipped bit is caught", AesGcm.open(k, iv, a, sealed))
        // GCM test case 13: an empty message under a zero key.
        assertEquals("530f8afbc74536b9a963b4f1c4cb738b", AesGcm.seal(ByteArray(32), ByteArray(12), ByteArray(0), ByteArray(0)).hex())
    }

    @Test
    fun webAndPhoneChatInteroperate() {
        val web = PureCrypto.newKeyPair()
        val phone = JvmChatCrypto.newKeyPair()
        // Phone to web.
        val toWeb = JvmChatCrypto.seal(phone.privateKey, web.publicKey, "phone", "web", "Hello from Android! \u2764")
        assertEquals("Hello from Android! \u2764", PureCrypto.open(web.privateKey, phone.publicKey, "phone", "web", toWeb))
        // Web to phone, including a long message spanning many blocks.
        val long = "Refiner's Fire ".repeat(30)
        val toPhone = PureCrypto.seal(web.privateKey, phone.publicKey, "web", "phone", long)
        assertEquals(long, JvmChatCrypto.open(phone.privateKey, web.publicKey, "web", "phone", toPhone))
        // Wrong direction or a stranger's key fails, as it does on the phone.
        assertNull(PureCrypto.open(web.privateKey, phone.publicKey, "web", "phone", toWeb))
        assertNull(PureCrypto.open(PureCrypto.newKeyPair().privateKey, phone.publicKey, "phone", "web", toWeb))
        assertEquals(JvmChatCrypto.safetyCode(web.publicKey, phone.publicKey), PureCrypto.safetyCode(phone.publicKey, web.publicKey))
    }
}
