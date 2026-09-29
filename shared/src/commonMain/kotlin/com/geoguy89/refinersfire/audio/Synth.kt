package com.geoguy89.refinersfire.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** A tiny offline synthesiser. Every sound in the game is generated with it at first launch. */
class Synth(val sr: Int) {
    private val rnd = Random(1234)

    fun buffer(seconds: Float) = FloatArray((seconds * sr).toInt())

    fun midi(note: Double): Double = 440.0 * 2.0.pow((note - 69) / 12.0)

    /** Inharmonic bell/celesta partials with exponential decay. */
    fun bell(buf: FloatArray, start: Float, freq: Double, dur: Float, amp: Float, bright: Float = 1f) {
        val partials = doubleArrayOf(1.0, 2.0, 2.76, 5.4, 8.93)
        val amps = floatArrayOf(1f, 0.35f, 0.5f * bright, 0.22f * bright, 0.1f * bright)
        val s0 = (start * sr).toInt()
        val n = (dur * sr).toInt()
        for (p in partials.indices) {
            val f = freq * partials[p]
            if (f > sr / 2.2) continue
            val decay = 3.2 * (1 + p * 0.9) / dur
            val w = 2 * PI * f / sr
            for (i in 0 until n) {
                val idx = s0 + i
                if (idx >= buf.size) break
                val t = i.toDouble() / sr
                val attack = if (t < 0.003) t / 0.003 else 1.0
                buf[idx] += (amp * amps[p] * attack * exp(-decay * t) * sin(w * i)).toFloat()
            }
        }
    }

    /** Karplus-Strong plucked string: harp, lute and dulcimer tones. */
    fun pluck(buf: FloatArray, start: Float, freq: Double, dur: Float, amp: Float, damping: Float = 0.996f, bright: Float = 0.5f) {
        val period = (sr / freq).toInt().coerceAtLeast(2)
        val line = FloatArray(period) { (rnd.nextFloat() * 2 - 1) }
        // Soften the initial noise burst for a rounder tone.
        var prev = 0f
        for (i in line.indices) { val v = line[i]; line[i] = prev + (v - prev) * bright; prev = line[i] }
        val s0 = (start * sr).toInt()
        val n = (dur * sr).toInt()
        var pos = 0
        for (i in 0 until n) {
            val idx = s0 + i
            if (idx >= buf.size) break
            val cur = line[pos]
            val next = line[(pos + 1) % period]
            line[pos] = (cur + next) * 0.5f * damping
            val fade = if (i > n - 400) (n - i) / 400f else 1f
            buf[idx] += cur * amp * fade
            pos = (pos + 1) % period
        }
    }

    enum class Wave { SINE, TRIANGLE, SAW, SQUARE }

    private fun osc(w: Wave, phase: Double): Double {
        val p = phase - kotlin.math.floor(phase)
        return when (w) {
            Wave.SINE -> sin(2 * PI * p)
            Wave.TRIANGLE -> 4 * abs(p - 0.5) - 1
            Wave.SAW -> 2 * p - 1
            Wave.SQUARE -> if (p < 0.5) 1.0 else -1.0
        }
    }

    /** A tone gliding exponentially from f0 to f1 with an attack/release envelope. */
    fun tone(
        buf: FloatArray, start: Float, f0: Double, f1: Double, dur: Float, amp: Float,
        wave: Wave = Wave.SINE, attack: Float = 0.005f, release: Float = 0.05f, decay: Float = 0f, vibrato: Double = 0.0,
    ) {
        val s0 = (start * sr).toInt()
        val n = (dur * sr).toInt()
        var phase = 0.0
        val glide = f0 != f1
        for (i in 0 until n) {
            val idx = s0 + i
            if (idx >= buf.size) break
            val t = i.toFloat() / sr
            val k = i.toDouble() / n
            val base = if (glide) f0 * (f1 / f0).pow(k) else f0
            val f = if (vibrato != 0.0) base * (1 + vibrato * sin(2 * PI * 5.5 * t)) else base
            phase += f / sr
            var e = 1f
            if (t < attack) e = t / attack
            if (t > dur - release) e *= ((dur - t) / release).coerceAtLeast(0f)
            if (decay > 0f) e *= exp(-t / decay)
            buf[idx] += (amp * e * osc(wave, phase)).toFloat()
        }
    }

    /** Filtered noise with a cutoff that sweeps from c0 to c1 Hz (one-pole low-pass). */
    fun noise(buf: FloatArray, start: Float, dur: Float, amp: Float, c0: Double, c1: Double, attack: Float = 0.002f, decay: Float = 0f, highpass: Boolean = false) {
        val s0 = (start * sr).toInt()
        val n = (dur * sr).toInt()
        var lp = 0.0
        for (i in 0 until n) {
            val idx = s0 + i
            if (idx >= buf.size) break
            val t = i.toFloat() / sr
            val k = i.toDouble() / n
            val cutoff = c0 * (c1 / c0).pow(k)
            val a = 1 - exp(-2 * PI * cutoff / sr)
            val x = rnd.nextDouble() * 2 - 1
            lp += a * (x - lp)
            val v = if (highpass) x - lp else lp
            var e = if (t < attack) t / attack else 1f
            e *= if (decay > 0f) exp(-t / decay) else ((n - i).toFloat() / n)
            buf[idx] += (amp * e * v).toFloat()
        }
    }

    /** Random crackle impulses, like embers popping. */
    fun crackle(buf: FloatArray, start: Float, dur: Float, amp: Float, density: Float) {
        val s0 = (start * sr).toInt()
        val n = (dur * sr).toInt()
        var i = 0
        while (i < n) {
            if (rnd.nextFloat() < density / sr * 40) {
                val len = 30 + rnd.nextInt(120)
                val a = amp * (0.3f + rnd.nextFloat()) * (1f - i.toFloat() / n)
                for (j in 0 until len) {
                    val idx = s0 + i + j
                    if (idx >= buf.size) break
                    buf[idx] += a * (rnd.nextFloat() * 2 - 1) * (1f - j.toFloat() / len)
                }
                i += len
            }
            i += 40
        }
    }

    /** Schroeder reverb: four combs into two all-passes. Returns a new buffer (dry + wet). */
    fun reverb(buf: FloatArray, mix: Float, room: Float = 0.84f, damp: Float = 0.3f): FloatArray {
        val scale = sr / 44100.0
        val combLens = intArrayOf(1557, 1617, 1491, 1422).map { (it * scale).toInt() }
        val apLens = intArrayOf(225, 556).map { (it * scale).toInt() }
        val wet = FloatArray(buf.size)
        for (len in combLens) {
            val line = FloatArray(len)
            var pos = 0
            var store = 0f
            for (i in buf.indices) {
                val out = line[pos]
                store = out * (1 - damp) + store * damp
                line[pos] = buf[i] + store * room
                wet[i] += out
                pos = (pos + 1) % len
            }
        }
        for (len in apLens) {
            val line = FloatArray(len)
            var pos = 0
            for (i in wet.indices) {
                val bufOut = line[pos]
                val input = wet[i]
                line[pos] = input + bufOut * 0.5f
                wet[i] = bufOut - input
                pos = (pos + 1) % len
            }
        }
        return FloatArray(buf.size) { buf[it] * (1 - mix * 0.5f) + wet[it] * mix * 0.25f }
    }

    fun normalize(buf: FloatArray, peak: Float = 0.9f): FloatArray {
        val m = buf.maxOf { abs(it) }
        if (m > 0f) { val g = peak / m; for (i in buf.indices) buf[i] *= g }
        return buf
    }

    fun fadeOut(buf: FloatArray, seconds: Float): FloatArray {
        val n = (seconds * sr).toInt().coerceAtMost(buf.size)
        for (i in 0 until n) buf[buf.size - 1 - i] *= i.toFloat() / n
        return buf
    }

    companion object {
        fun toPcm16(buf: FloatArray): ShortArray = ShortArray(buf.size) {
            (buf[it].coerceIn(-1f, 1f) * 32767).toInt().toShort()
        }

        fun toWav(buf: FloatArray, sr: Int): ByteArray {
            val pcm = toPcm16(buf)
            val out = ByteArray(44 + pcm.size * 2)
            var pos = 0
            fun bytes(str: String) { for (ch in str) out[pos++] = ch.code.toByte() }
            fun int(v: Int) { for (k in 0 until 4) out[pos++] = (v shr (8 * k) and 0xFF).toByte() }
            fun short(v: Int) { out[pos++] = (v and 0xFF).toByte(); out[pos++] = (v shr 8 and 0xFF).toByte() }
            bytes("RIFF"); int(36 + pcm.size * 2); bytes("WAVE")
            bytes("fmt "); int(16); short(1); short(1); int(sr); int(sr * 2); short(2); short(16)
            bytes("data"); int(pcm.size * 2)
            for (s in pcm) short(s.toInt())
            return out
        }
    }
}
