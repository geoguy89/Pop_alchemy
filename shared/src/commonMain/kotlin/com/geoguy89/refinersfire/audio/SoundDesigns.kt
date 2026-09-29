package com.geoguy89.refinersfire.audio

import com.geoguy89.refinersfire.ThemeId
import com.geoguy89.refinersfire.audio.Synth.Wave
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

enum class Sfx {
    PLACE_0, PLACE_1, PLACE_2, PLACE_3, PLACE_4,
    CORNERSTONE_PLACE, INVALID, DISCARD, FORGE_COOL, LINE_CLEAR, MULTI_LINE, BOARD_CLEAR,
    HAMMER_USE, HAMMER_APPEAR, CORNERSTONE_APPEAR, GAME_OVER, CLICK, TICK, FORGE_WARNING,
    HINT, STREAK, SYMBOL_LINE, PERFECT_LINE;

    companion object {
        fun place(neighbors: Int) = entries[PLACE_0.ordinal + neighbors.coerceIn(0, 4)]
    }
}

/** The recipes for every sound effect. Pure functions of the synthesiser, so they are testable off-device. */
object SoundDesigns {
    const val SFX_RATE = 44100

    fun render(sfx: Sfx, theme: ThemeId = ThemeId.MODERN): FloatArray {
        when (theme) {
            ThemeId.TEMPLE -> ThemeSounds.temple(sfx)?.let { return it }
            ThemeId.FUTURE -> ThemeSounds.future(sfx)?.let { return it }
            ThemeId.MODERN -> Unit
        }
        val s = Synth(SFX_RATE)
        return when (sfx) {
            Sfx.PLACE_0, Sfx.PLACE_1, Sfx.PLACE_2, Sfx.PLACE_3, Sfx.PLACE_4 -> {
                // A stone set down, then a chime that climbs the pentatonic scale with each extra neighbour.
                val k = sfx.ordinal - Sfx.PLACE_0.ordinal
                val notes = doubleArrayOf(69.0, 72.0, 74.0, 76.0, 79.0)
                val b = s.buffer(0.9f)
                s.tone(b, 0f, 180.0, 70.0, 0.09f, 0.55f, attack = 0.001f, release = 0.05f)
                s.noise(b, 0f, 0.05f, 0.35f, 3500.0, 900.0)
                s.bell(b, 0.012f, s.midi(notes[k] + 12), 0.7f, 0.22f)
                s.bell(b, 0.012f, s.midi(notes[k] + 19), 0.4f, 0.06f)
                s.normalize(s.reverb(b, 0.25f, 0.7f), 0.8f)
            }
            Sfx.CORNERSTONE_PLACE -> {
                val b = s.buffer(1.2f)
                s.tone(b, 0f, 120.0, 45.0, 0.25f, 0.6f, attack = 0.002f, release = 0.1f)
                s.noise(b, 0f, 0.3f, 0.35f, 600.0, 120.0)
                s.bell(b, 0.03f, s.midi(81.0), 0.9f, 0.18f)
                s.bell(b, 0.06f, s.midi(88.0), 0.8f, 0.1f)
                s.normalize(s.reverb(b, 0.35f), 0.85f)
            }
            Sfx.INVALID -> {
                val b = s.buffer(0.3f)
                s.tone(b, 0f, 140.0, 120.0, 0.09f, 0.4f, Wave.SQUARE, release = 0.02f)
                s.tone(b, 0.12f, 120.0, 100.0, 0.11f, 0.4f, Wave.SQUARE, release = 0.03f)
                lowpass(s.normalize(b, 0.55f), 1800.0, s.sr)
            }
            Sfx.DISCARD -> {
                // Whoosh into the forge and a flare of crackling flame.
                val b = s.buffer(1.3f)
                s.noise(b, 0f, 0.35f, 0.6f, 400.0, 3500.0, attack = 0.15f, decay = 0f)
                s.noise(b, 0.25f, 0.9f, 0.5f, 900.0, 250.0, attack = 0.01f, decay = 0.35f)
                s.crackle(b, 0.3f, 0.9f, 0.35f, 0.6f)
                s.tone(b, 0.25f, 90.0, 55.0, 0.5f, 0.4f, release = 0.3f)
                s.normalize(s.reverb(b, 0.2f), 0.85f)
            }
            Sfx.FORGE_COOL -> {
                val b = s.buffer(1.1f)
                s.noise(b, 0f, 1.0f, 0.5f, 5000.0, 2000.0, attack = 0.05f, decay = 0.4f, highpass = true)
                s.bell(b, 0.05f, s.midi(84.0), 0.6f, 0.08f)
                s.normalize(b, 0.6f)
            }
            Sfx.LINE_CLEAR, Sfx.MULTI_LINE -> {
                // A shimmering rising arpeggio: lead turning to gold.
                val big = sfx == Sfx.MULTI_LINE
                val b = s.buffer(if (big) 2.4f else 1.8f)
                val notes = if (big) doubleArrayOf(72.0, 76.0, 79.0, 84.0, 88.0, 91.0, 96.0) else doubleArrayOf(72.0, 76.0, 79.0, 84.0, 88.0)
                notes.forEachIndexed { i, n ->
                    s.bell(b, i * 0.07f, s.midi(n), 1.1f, 0.22f)
                    s.pluck(b, i * 0.07f, s.midi(n - 12), 0.9f, 0.18f)
                }
                s.noise(b, 0f, 0.9f, 0.12f, 6000.0, 9000.0, attack = 0.2f, highpass = true)
                s.tone(b, 0f, s.midi(48.0), s.midi(48.0), 1.2f, 0.18f, Wave.TRIANGLE, attack = 0.02f, release = 0.6f)
                s.normalize(s.reverb(b, 0.45f, 0.86f), 0.85f)
            }
            Sfx.BOARD_CLEAR -> {
                // A triumphant cadence: I - IV - V - I with bells on top.
                val b = s.buffer(4.2f)
                val chords = listOf(
                    doubleArrayOf(60.0, 64.0, 67.0, 72.0), doubleArrayOf(65.0, 69.0, 72.0, 77.0),
                    doubleArrayOf(67.0, 71.0, 74.0, 79.0), doubleArrayOf(72.0, 76.0, 79.0, 84.0),
                )
                val times = floatArrayOf(0f, 0.45f, 0.9f, 1.4f)
                chords.forEachIndexed { ci, ch ->
                    val dur = if (ci == 3) 2.6f else 0.6f
                    for (n in ch) {
                        s.tone(b, times[ci], s.midi(n - 12), s.midi(n - 12), dur, 0.07f, Wave.SAW, attack = 0.03f, release = 0.3f, vibrato = 0.003)
                        s.pluck(b, times[ci] + (n - ch[0]).toFloat() * 0.006f, s.midi(n), dur, 0.16f)
                    }
                    s.bell(b, times[ci], s.midi(ch.last() + 12), dur + 0.5f, 0.14f)
                }
                listOf(84.0, 88.0, 91.0, 96.0, 100.0).forEachIndexed { i, n -> s.bell(b, 1.5f + i * 0.09f, s.midi(n), 1.5f, 0.1f) }
                s.normalize(s.reverb(lowpass(b, 5000.0, s.sr), 0.5f, 0.88f), 0.9f)
            }
            Sfx.HAMMER_USE -> {
                // The Refiner's Hammer: a heavy strike, a metallic ring and a spray of sparks.
                val b = s.buffer(1.4f)
                s.tone(b, 0f, 160.0, 55.0, 0.18f, 0.8f, attack = 0.001f, release = 0.1f)
                s.noise(b, 0f, 0.08f, 0.7f, 6000.0, 1500.0)
                s.bell(b, 0.005f, s.midi(81.0), 1.1f, 0.25f, bright = 0.9f)
                s.bell(b, 0.005f, s.midi(88.3), 0.8f, 0.12f, bright = 0.9f)
                s.crackle(b, 0.02f, 0.5f, 0.35f, 0.8f)
                s.normalize(s.reverb(b, 0.35f), 0.85f)
            }
            Sfx.HAMMER_APPEAR -> {
                // The hammer arrives: a bright ring on the anvil.
                val b = s.buffer(1.8f)
                s.bell(b, 0f, s.midi(76.0), 1.6f, 0.4f, bright = 0.9f)
                s.bell(b, 0f, s.midi(83.2), 1.2f, 0.18f, bright = 0.9f)
                s.tone(b, 0f, 220.0, 120.0, 0.05f, 0.3f, attack = 0.001f, release = 0.03f)
                s.normalize(s.reverb(b, 0.4f, 0.86f), 0.7f)
            }
            Sfx.CORNERSTONE_APPEAR -> {
                val b = s.buffer(1.4f)
                listOf(79.0, 83.0, 86.0, 91.0).forEachIndexed { i, n -> s.bell(b, i * 0.05f, s.midi(n), 1.0f, 0.15f) }
                s.normalize(s.reverb(b, 0.45f), 0.6f)
            }
            Sfx.GAME_OVER -> {
                val b = s.buffer(4.5f)
                listOf(69.0, 65.0, 62.0, 57.0).forEachIndexed { i, n ->
                    s.pluck(b, i * 0.45f, s.midi(n), 1.6f, 0.3f, damping = 0.997f)
                    s.bell(b, i * 0.45f, s.midi(n), 1.4f, 0.1f, bright = 0.5f)
                }
                s.tone(b, 1.35f, s.midi(33.0), s.midi(33.0), 2.8f, 0.3f, Wave.TRIANGLE, attack = 0.1f, release = 1.6f)
                s.tone(b, 1.35f, s.midi(45.0), s.midi(45.0), 2.8f, 0.12f, Wave.SAW, attack = 0.3f, release = 1.6f)
                s.normalize(s.reverb(lowpass(b, 3000.0, s.sr), 0.5f, 0.88f), 0.85f)
            }
            Sfx.CLICK -> {
                val b = s.buffer(0.12f)
                s.tone(b, 0f, 900.0, 500.0, 0.03f, 0.5f, Wave.TRIANGLE, attack = 0.0005f, release = 0.02f)
                s.noise(b, 0f, 0.02f, 0.3f, 4000.0, 2000.0)
                s.normalize(b, 0.5f)
            }
            Sfx.TICK -> {
                val b = s.buffer(0.1f)
                s.tone(b, 0f, 1800.0, 1600.0, 0.025f, 0.5f, attack = 0.0005f, release = 0.015f)
                s.normalize(b, 0.45f)
            }
            Sfx.HINT -> {
                val b = s.buffer(1.2f)
                s.bell(b, 0f, s.midi(76.0), 0.9f, 0.3f, bright = 0.6f)
                s.bell(b, 0.12f, s.midi(83.0), 1.0f, 0.3f, bright = 0.6f)
                s.normalize(s.reverb(b, 0.45f), 0.55f)
            }
            Sfx.STREAK -> {
                val b = s.buffer(1.6f)
                listOf(72.0, 76.0, 79.0, 84.0).forEachIndexed { i, n ->
                    s.pluck(b, i * 0.08f, s.midi(n), 1.0f, 0.25f)
                    s.bell(b, i * 0.08f, s.midi(n + 12), 0.9f, 0.1f)
                }
                s.normalize(s.reverb(b, 0.4f), 0.75f)
            }
            Sfx.SYMBOL_LINE -> {
                val b = s.buffer(2.2f)
                listOf(79.0, 83.0, 86.0, 91.0, 95.0, 98.0).forEachIndexed { i, n -> s.bell(b, i * 0.05f, s.midi(n), 1.3f, 0.2f) }
                s.noise(b, 0f, 0.6f, 0.2f, 6000.0, 9000.0, attack = 0.05f, decay = 0.3f, highpass = true)
                s.normalize(s.reverb(b, 0.5f, 0.88f), 0.85f)
            }
            Sfx.PERFECT_LINE -> {
                // The boom: a sub drop, a blast of noise and crackle, then a long rising fanfare.
                val b = s.buffer(4.2f)
                s.tone(b, 0f, 110.0, 28.0, 1.4f, 0.9f, attack = 0.002f, release = 0.8f)
                s.noise(b, 0f, 1.2f, 0.7f, 4000.0, 150.0, attack = 0.002f, decay = 0.45f)
                s.crackle(b, 0.05f, 1.4f, 0.4f, 1f)
                listOf(60.0, 64.0, 67.0, 72.0, 76.0, 79.0, 84.0, 88.0, 91.0, 96.0).forEachIndexed { i, n ->
                    s.bell(b, 0.35f + i * 0.06f, s.midi(n), 1.8f, 0.2f)
                    s.pluck(b, 0.35f + i * 0.06f, s.midi(n - 12), 1.4f, 0.14f)
                }
                for (n in listOf(60.0, 64.0, 67.0, 72.0)) {
                    s.tone(b, 0.9f, s.midi(n - 12), s.midi(n - 12), 2.6f, 0.07f, Wave.SAW, attack = 0.05f, release = 1.2f, vibrato = 0.003)
                }
                s.normalize(s.reverb(lowpass(b, 6000.0, s.sr), 0.5f, 0.9f), 0.95f)
            }
            Sfx.FORGE_WARNING -> {
                val b = s.buffer(0.9f)
                s.tone(b, 0f, 330.0, 330.0, 0.18f, 0.3f, Wave.TRIANGLE, release = 0.06f)
                s.tone(b, 0.22f, 311.0, 311.0, 0.35f, 0.3f, Wave.TRIANGLE, release = 0.2f)
                s.normalize(s.reverb(b, 0.3f), 0.6f)
            }
        }
    }

    fun lowpass(buf: FloatArray, cutoff: Double, sr: Int): FloatArray {
        val a = (1 - kotlin.math.exp(-2 * PI * cutoff / sr)).toFloat()
        var y = 0f
        for (i in buf.indices) { y += a * (buf[i] - y); buf[i] = y }
        return buf
    }
}

/**
 * The background music: a slow, mysterious piece for harp, strings and celesta in A minor. It is generated once
 * as a seamless loop.
 */
object MusicComposer {
    const val RATE = 22050
    private const val BPM = 66.0

    private val progression = listOf(
        "Am", "Am", "F", "G", "Am", "Dm", "E", "E",
        "F", "G", "Am", "Em", "Dm", "E", "Am", "Am",
    )
    private val chordNotes = mapOf(
        "Am" to intArrayOf(57, 60, 64), "F" to intArrayOf(53, 57, 60), "G" to intArrayOf(55, 59, 62),
        "Dm" to intArrayOf(50, 53, 57), "E" to intArrayOf(52, 56, 59), "Em" to intArrayOf(52, 55, 59),
    )

    fun compose(theme: ThemeId = ThemeId.MODERN): FloatArray {
        when (theme) {
            ThemeId.TEMPLE -> return ThemeSounds.templeMusic()
            ThemeId.FUTURE -> return ThemeSounds.futureMusic()
            ThemeId.MODERN -> Unit
        }
        val s = Synth(RATE)
        val beat = (60.0 / BPM).toFloat()
        val bar = beat * 4
        val loopSeconds = bar * progression.size
        val tail = 5f
        val b = s.buffer(loopSeconds + tail)
        val rnd = Random(2026)

        progression.forEachIndexed { i, name ->
            val t0 = i * bar
            val ch = chordNotes.getValue(name)
            // Bass: a soft root on beat one.
            s.tone(b, t0, s.midi(ch[0] - 12.0), s.midi(ch[0] - 12.0), bar * 1.02f, 0.16f, attack = 0.08f, release = 1.2f)
            // String pad: slightly detuned voices with slow swells.
            for (n in ch) for (d in doubleArrayOf(-0.07, 0.0, 0.08)) {
                s.tone(b, t0, s.midi(n + d), s.midi(n + d), bar + 0.8f, 0.022f, Synth.Wave.TRIANGLE, attack = 1.2f, release = 1.4f, vibrato = 0.002)
            }
            // Harp arpeggio in eighth notes, with the odd note left out to breathe.
            val arp = intArrayOf(ch[0], ch[1], ch[2], ch[0] + 12, ch[2], ch[1] + 12, ch[2] + 12, ch[1] + 12)
            for (k in 0 until 8) {
                if (k > 0 && rnd.nextFloat() < 0.15f) continue
                val vel = 0.12f + rnd.nextFloat() * 0.05f - (if (k % 2 == 1) 0.03f else 0f)
                s.pluck(b, t0 + k * beat / 2, s.midi(arp[k].toDouble()), beat * 2.5f, vel, damping = 0.9975f, bright = 0.35f)
            }
            // Celesta sparkles every other bar.
            if (i % 2 == 1) {
                val n = ch[rnd.nextInt(3)] + 24
                s.bell(b, t0 + beat * (1 + rnd.nextInt(3)), s.midi(n.toDouble()), 2.5f, 0.05f, bright = 0.6f)
            }
        }
        // A gentle tremolo on the whole mix, then a big hall.
        for (i in b.indices) b[i] *= (0.92f + 0.08f * sin(2 * PI * 0.12 * i / RATE).toFloat())
        val wet = s.reverb(SoundDesigns.lowpass(b, 4200.0, RATE), 0.55f, 0.88f, 0.4f)
        return s.normalize(foldLoop(wet, loopSeconds), 0.7f)
    }

    /** Fold the tail back onto the start so the loop is seamless. */
    fun foldLoop(wet: FloatArray, loopSeconds: Float): FloatArray {
        val loopLen = (loopSeconds * RATE).toInt()
        val out = FloatArray(loopLen) { wet[it] }
        for (i in 0 until wet.size - loopLen) out[i % loopLen] += wet[loopLen + i]
        return out
    }
}
