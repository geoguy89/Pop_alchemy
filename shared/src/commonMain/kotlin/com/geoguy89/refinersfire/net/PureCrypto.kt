package com.geoguy89.refinersfire.net

import com.geoguy89.refinersfire.secureRandomBytes
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * The same end-to-end chat encryption as the Android and desktop apps (X25519, HKDF-SHA256, AES-256-GCM), written in
 * plain Kotlin for platforms without a synchronous crypto library (the web version). Byte-for-byte compatible: a
 * message sealed here opens on a phone and the other way round. Every primitive is checked against its published test
 * vectors and against the JVM implementation in the tests.
 */
@OptIn(ExperimentalEncodingApi::class)
object PureCrypto : ChatCrypto {
    private fun b64(b: ByteArray) = Base64.Default.encode(b)
    private fun unb64(s: String) = Base64.Default.decode(s)

    override fun newKeyPair(): ChatKeyPair {
        val priv = secureRandomBytes(32)
        return ChatKeyPair(b64(X25519.publicKey(priv)), b64(priv))
    }

    private fun key(myPrivate: String, theirPublic: String, idA: String, idB: String): ByteArray {
        val shared = X25519.scalarMult(unb64(myPrivate), unb64(theirPublic))
        val info = listOf(idA, idB).sorted().joinToString("|").encodeToByteArray()
        return Sha256.hkdf(shared, "refinersfire-chat-v1".encodeToByteArray(), info, 32)
    }

    override fun seal(myPrivate: String, theirPublic: String, fromId: String, toId: String, text: String): Sealed {
        val nonce = secureRandomBytes(12)
        val ct = AesGcm.seal(key(myPrivate, theirPublic, fromId, toId), nonce, "$fromId>$toId".encodeToByteArray(), text.encodeToByteArray())
        return Sealed(b64(nonce), b64(ct))
    }

    override fun open(myPrivate: String, theirPublic: String, fromId: String, toId: String, sealed: Sealed): String? = try {
        AesGcm.open(key(myPrivate, theirPublic, fromId, toId), unb64(sealed.nonce), "$fromId>$toId".encodeToByteArray(), unb64(sealed.ct))?.decodeToString()
    } catch (e: Exception) {
        null
    }

    override fun safetyCode(publicA: String, publicB: String): String {
        val d = Sha256.digest(listOf(publicA, publicB).sorted().joinToString("|").encodeToByteArray())
        return (0 until 5).joinToString(" ") { g ->
            val v = ((d[g * 3].toInt() and 0xFF) shl 16) or ((d[g * 3 + 1].toInt() and 0xFF) shl 8) or (d[g * 3 + 2].toInt() and 0xFF)
            (v % 100_000).toString().padStart(5, '0')
        }
    }
}

/** SHA-256 (FIPS 180-4), HMAC (RFC 2104) and HKDF (RFC 5869). */
internal object Sha256 {
    private val K = intArrayOf(
        0x428a2f98, 0x71374491, -0x4a3f0431, -0x164a245b, 0x3956c25b, 0x59f111f1, -0x6dc07d5c, -0x54e3a12b,
        -0x27f85568, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, -0x7f214e02, -0x6423f959, -0x3e640e8c,
        -0x1b64963f, -0x1041b87a, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        -0x67c1aeae, -0x57ce3993, -0x4ffcd838, -0x40a68039, -0x391ff40d, -0x2a586eb9, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, -0x7e3d36d2, -0x6d8dd37b,
        -0x5d40175f, -0x57e599b5, -0x3db47490, -0x3893ae5d, -0x2e6d17e7, -0x2966f9dc, -0xbf1ca7b, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, -0x7b3787ec, -0x7338fdf8, -0x6f410006, -0x5baf9315, -0x41065c09, -0x398e870e,
    )

    fun digest(msg: ByteArray): ByteArray {
        val h = intArrayOf(0x6a09e667, -0x4498517b, 0x3c6ef372, -0x5ab00ac6, 0x510e527f, -0x64fa9774, 0x1f83d9ab, 0x5be0cd19)
        val bitLen = msg.size.toLong() * 8
        val padded = ByteArray(((msg.size + 9 + 63) / 64) * 64)
        msg.copyInto(padded)
        padded[msg.size] = 0x80.toByte()
        for (i in 0 until 8) padded[padded.size - 1 - i] = (bitLen ushr (8 * i)).toByte()
        val w = IntArray(64)
        for (block in 0 until padded.size / 64) {
            for (t in 0 until 16) {
                val o = block * 64 + t * 4
                w[t] = ((padded[o].toInt() and 0xFF) shl 24) or ((padded[o + 1].toInt() and 0xFF) shl 16) or
                    ((padded[o + 2].toInt() and 0xFF) shl 8) or (padded[o + 3].toInt() and 0xFF)
            }
            for (t in 16 until 64) {
                val s0 = w[t - 15].rotateRight(7) xor w[t - 15].rotateRight(18) xor (w[t - 15] ushr 3)
                val s1 = w[t - 2].rotateRight(17) xor w[t - 2].rotateRight(19) xor (w[t - 2] ushr 10)
                w[t] = w[t - 16] + s0 + w[t - 7] + s1
            }
            var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]; var e = h[4]; var f = h[5]; var g = h[6]; var hh = h[7]
            for (t in 0 until 64) {
                val t1 = hh + (e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)) + ((e and f) xor (e.inv() and g)) + K[t] + w[t]
                val t2 = (a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)) + ((a and b) xor (a and c) xor (b and c))
                hh = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2
            }
            h[0] += a; h[1] += b; h[2] += c; h[3] += d; h[4] += e; h[5] += f; h[6] += g; h[7] += hh
        }
        return ByteArray(32) { (h[it / 4] ushr (24 - 8 * (it % 4))).toByte() }
    }

    fun hmac(key: ByteArray, msg: ByteArray): ByteArray {
        val k = (if (key.size > 64) digest(key) else key).copyOf(64)
        val inner = ByteArray(64) { (k[it].toInt() xor 0x36).toByte() } + msg
        val outer = ByteArray(64) { (k[it].toInt() xor 0x5c).toByte() }
        return digest(outer + digest(inner))
    }

    fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val prk = hmac(if (salt.isEmpty()) ByteArray(32) else salt, ikm)
        val out = ArrayList<Byte>()
        var t = ByteArray(0)
        var n = 1
        while (out.size < length) {
            t = hmac(prk, t + info + byteArrayOf(n.toByte()))
            out.addAll(t.toList())
            n++
        }
        return out.take(length).toByteArray()
    }
}

/** X25519 (RFC 7748), ported from TweetNaCl: field elements are 16 limbs of 16 bits. */
internal object X25519 {
    private val C121665 = longArrayOf(0xDB41, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)

    private fun gf() = LongArray(16)

    private fun car(o: LongArray) {
        for (i in 0 until 16) {
            o[i] += 1L shl 16
            val c = o[i] shr 16
            if (i < 15) o[i + 1] += c - 1 else o[0] += c - 1 + 37 * (c - 1)
            o[i] -= c shl 16
        }
    }

    private fun sel(p: LongArray, q: LongArray, b: Int) {
        val c = (b - 1).toLong().inv()
        for (i in 0 until 16) {
            val t = c and (p[i] xor q[i])
            p[i] = p[i] xor t
            q[i] = q[i] xor t
        }
    }

    private fun pack(n: LongArray): ByteArray {
        val m = gf()
        val t = n.copyOf()
        car(t); car(t); car(t)
        for (j in 0 until 2) {
            m[0] = t[0] - 0xffed
            for (i in 1 until 15) {
                m[i] = t[i] - 0xffff - ((m[i - 1] shr 16) and 1)
                m[i - 1] = m[i - 1] and 0xffff
            }
            m[15] = t[15] - 0x7fff - ((m[14] shr 16) and 1)
            val b = ((m[15] shr 16) and 1).toInt()
            m[14] = m[14] and 0xffff
            sel(t, m, 1 - b)
        }
        return ByteArray(32) { if (it % 2 == 0) (t[it / 2] and 0xff).toByte() else (t[it / 2] shr 8).toByte() }
    }

    private fun unpack(n: ByteArray): LongArray {
        val o = gf()
        for (i in 0 until 16) o[i] = (n[2 * i].toLong() and 0xff) + ((n[2 * i + 1].toLong() and 0xff) shl 8)
        o[15] = o[15] and 0x7fff
        return o
    }

    private fun add(o: LongArray, a: LongArray, b: LongArray) { for (i in 0 until 16) o[i] = a[i] + b[i] }
    private fun sub(o: LongArray, a: LongArray, b: LongArray) { for (i in 0 until 16) o[i] = a[i] - b[i] }

    private fun mul(o: LongArray, a: LongArray, b: LongArray) {
        val t = LongArray(31)
        for (i in 0 until 16) for (j in 0 until 16) t[i + j] += a[i] * b[j]
        for (i in 0 until 15) t[i] += 38 * t[i + 16]
        for (i in 0 until 16) o[i] = t[i]
        car(o); car(o)
    }

    private fun sq(o: LongArray, a: LongArray) = mul(o, a, a)

    private fun inv(o: LongArray, i: LongArray) {
        val c = i.copyOf()
        for (a in 253 downTo 0) {
            sq(c, c)
            if (a != 2 && a != 4) mul(c, c, i)
        }
        c.copyInto(o)
    }

    fun scalarMult(n: ByteArray, p: ByteArray): ByteArray {
        require(n.size == 32 && p.size == 32)
        val z = n.copyOf()
        z[31] = ((n[31].toInt() and 127) or 64).toByte()
        z[0] = (z[0].toInt() and 248).toByte()
        val x = unpack(p)
        val a = gf(); val b = x.copyOf(); val c = gf(); val d = gf(); val e = gf(); val f = gf()
        a[0] = 1; d[0] = 1
        for (i in 254 downTo 0) {
            val r = ((z[i shr 3].toInt() and 0xff) shr (i and 7)) and 1
            sel(a, b, r); sel(c, d, r)
            add(e, a, c); sub(a, a, c); add(c, b, d); sub(b, b, d)
            sq(d, e); sq(f, a); mul(a, c, a); mul(c, b, e)
            add(e, a, c); sub(a, a, c); sq(b, a); sub(c, d, f)
            mul(a, c, C121665); add(a, a, d); mul(c, c, a); mul(a, d, f)
            mul(d, b, x); sq(b, e)
            sel(a, b, r); sel(c, d, r)
        }
        inv(c, c)
        mul(a, a, c)
        return pack(a)
    }

    private val BASE = ByteArray(32).also { it[0] = 9 }

    fun publicKey(priv: ByteArray): ByteArray = scalarMult(priv, BASE)
}

/** AES-256 (FIPS 197) in GCM (NIST SP 800-38D) with a 96-bit nonce and a 128-bit tag appended to the ciphertext. */
internal object AesGcm {
    private val SBOX = IntArray(256)

    init {
        // Build the S-box from the multiplicative inverse in GF(2^8) and the affine transform.
        var p = 1
        var q = 1
        do {
            p = p xor ((p shl 1) and 0xff) xor (if (p and 0x80 != 0) 0x1b else 0)
            q = q xor (q shl 1)
            q = q xor (q shl 2)
            q = q xor (q shl 4)
            q = q and 0xff
            if (q and 0x80 != 0) q = q xor 0x09
            val x = q xor (q shl 1 or (q ushr 7)) xor (q shl 2 or (q ushr 6)) xor (q shl 3 or (q ushr 5)) xor (q shl 4 or (q ushr 4))
            SBOX[p] = (x xor 0x63) and 0xff
        } while (p != 1)
        SBOX[0] = 0x63
    }

    private fun xtime(b: Int) = ((b shl 1) xor (if (b and 0x80 != 0) 0x1b else 0)) and 0xff

    private fun expandKey(key: ByteArray): IntArray {
        require(key.size == 32)
        val w = IntArray(60)
        for (i in 0 until 8) {
            w[i] = ((key[4 * i].toInt() and 0xff) shl 24) or ((key[4 * i + 1].toInt() and 0xff) shl 16) or
                ((key[4 * i + 2].toInt() and 0xff) shl 8) or (key[4 * i + 3].toInt() and 0xff)
        }
        var rcon = 1
        for (i in 8 until 60) {
            var t = w[i - 1]
            if (i % 8 == 0) {
                t = (t shl 8) or (t ushr 24)
                t = subWord(t) xor (rcon shl 24)
                rcon = xtime(rcon)
            } else if (i % 8 == 4) {
                t = subWord(t)
            }
            w[i] = w[i - 8] xor t
        }
        return w
    }

    private fun subWord(w: Int) =
        (SBOX[(w ushr 24) and 0xff] shl 24) or (SBOX[(w ushr 16) and 0xff] shl 16) or (SBOX[(w ushr 8) and 0xff] shl 8) or SBOX[w and 0xff]

    private fun encryptBlock(w: IntArray, input: ByteArray): ByteArray {
        val s = IntArray(16) { input[it].toInt() and 0xff }
        fun addRound(r: Int) {
            for (c in 0 until 4) {
                val k = w[r * 4 + c]
                s[4 * c] = s[4 * c] xor ((k ushr 24) and 0xff)
                s[4 * c + 1] = s[4 * c + 1] xor ((k ushr 16) and 0xff)
                s[4 * c + 2] = s[4 * c + 2] xor ((k ushr 8) and 0xff)
                s[4 * c + 3] = s[4 * c + 3] xor (k and 0xff)
            }
        }
        addRound(0)
        for (round in 1..14) {
            for (i in 0 until 16) s[i] = SBOX[s[i]]
            // Shift rows (state is column-major: s[4c + r]).
            val t = s.copyOf()
            for (c in 0 until 4) for (r in 0 until 4) s[4 * c + r] = t[4 * ((c + r) % 4) + r]
            if (round != 14) {
                for (c in 0 until 4) {
                    val a0 = s[4 * c]; val a1 = s[4 * c + 1]; val a2 = s[4 * c + 2]; val a3 = s[4 * c + 3]
                    val all = a0 xor a1 xor a2 xor a3
                    s[4 * c] = a0 xor all xor xtime(a0 xor a1)
                    s[4 * c + 1] = a1 xor all xor xtime(a1 xor a2)
                    s[4 * c + 2] = a2 xor all xor xtime(a2 xor a3)
                    s[4 * c + 3] = a3 xor all xor xtime(a3 xor a0)
                }
            }
            addRound(round)
        }
        return ByteArray(16) { s[it].toByte() }
    }

    /** Multiply in GF(2^128) as GCM defines it (bits taken most significant first). */
    private fun gmul(x: ByteArray, y: ByteArray): ByteArray {
        var zh = 0L; var zl = 0L
        var vh = toLong(y, 0); var vl = toLong(y, 8)
        for (i in 0 until 128) {
            if ((x[i / 8].toInt() shr (7 - i % 8)) and 1 == 1) { zh = zh xor vh; zl = zl xor vl }
            val lsb = vl and 1L
            vl = (vl ushr 1) or (vh shl 63)
            vh = vh ushr 1
            if (lsb == 1L) vh = vh xor (0xE1L shl 56)
        }
        return fromLongs(zh, zl)
    }

    private fun toLong(b: ByteArray, o: Int): Long {
        var v = 0L
        for (i in 0 until 8) v = (v shl 8) or (b[o + i].toLong() and 0xff)
        return v
    }

    private fun fromLongs(h: Long, l: Long) = ByteArray(16) { if (it < 8) (h ushr (56 - 8 * it)).toByte() else (l ushr (56 - 8 * (it - 8))).toByte() }

    private fun ghash(h: ByteArray, aad: ByteArray, ct: ByteArray): ByteArray {
        var x = ByteArray(16)
        fun absorb(data: ByteArray) {
            var o = 0
            while (o < data.size) {
                val block = ByteArray(16)
                data.copyInto(block, 0, o, minOf(o + 16, data.size))
                for (i in 0 until 16) block[i] = (block[i].toInt() xor x[i].toInt()).toByte()
                x = gmul(block, h)
                o += 16
            }
        }
        absorb(aad)
        absorb(ct)
        val lens = fromLongs(aad.size.toLong() * 8, ct.size.toLong() * 8)
        for (i in 0 until 16) lens[i] = (lens[i].toInt() xor x[i].toInt()).toByte()
        return gmul(lens, h)
    }

    private fun ctr(w: IntArray, j0: ByteArray, data: ByteArray): ByteArray {
        val out = ByteArray(data.size)
        val counter = j0.copyOf()
        var o = 0
        while (o < data.size) {
            // inc32 on the last four bytes.
            var c = ((counter[12].toInt() and 0xff) shl 24) or ((counter[13].toInt() and 0xff) shl 16) or
                ((counter[14].toInt() and 0xff) shl 8) or (counter[15].toInt() and 0xff)
            c++
            counter[12] = (c ushr 24).toByte(); counter[13] = (c ushr 16).toByte(); counter[14] = (c ushr 8).toByte(); counter[15] = c.toByte()
            val ks = encryptBlock(w, counter)
            for (i in 0 until minOf(16, data.size - o)) out[o + i] = (data[o + i].toInt() xor ks[i].toInt()).toByte()
            o += 16
        }
        return out
    }

    fun seal(key: ByteArray, nonce: ByteArray, aad: ByteArray, plain: ByteArray): ByteArray {
        require(nonce.size == 12)
        val w = expandKey(key)
        val h = encryptBlock(w, ByteArray(16))
        val j0 = nonce.copyOf(16).also { it[15] = 1 }
        val ct = ctr(w, j0, plain)
        val s = ghash(h, aad, ct)
        val ek = encryptBlock(w, j0)
        return ct + ByteArray(16) { (s[it].toInt() xor ek[it].toInt()).toByte() }
    }

    /** Null when the tag doesn't match (tampered, or the wrong key). */
    fun open(key: ByteArray, nonce: ByteArray, aad: ByteArray, sealed: ByteArray): ByteArray? {
        if (nonce.size != 12 || sealed.size < 16) return null
        val w = expandKey(key)
        val h = encryptBlock(w, ByteArray(16))
        val j0 = nonce.copyOf(16).also { it[15] = 1 }
        val ct = sealed.copyOfRange(0, sealed.size - 16)
        val tag = sealed.copyOfRange(sealed.size - 16, sealed.size)
        val s = ghash(h, aad, ct)
        val ek = encryptBlock(w, j0)
        var diff = 0
        for (i in 0 until 16) diff = diff or ((s[i].toInt() xor ek[i].toInt()) xor tag[i].toInt())
        if (diff and 0xff != 0) return null
        return ctr(w, j0, ct)
    }
}
