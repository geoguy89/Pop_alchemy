package com.geoguy89.refinersfire.audio

import com.geoguy89.refinersfire.audio.SoundDesigns.lowpass
import com.geoguy89.refinersfire.audio.Synth.Wave
import kotlin.random.Random

/**
 * Sound effects and soundtracks for the Temple and Future themes. An effect a theme does not redesign returns
 * null and falls back to the Modern version.
 */
object ThemeSounds {
    private val placeNotes = doubleArrayOf(69.0, 72.0, 74.0, 76.0, 79.0)

    fun temple(sfx: Sfx): FloatArray? {
        val s = Synth(SoundDesigns.SFX_RATE)
        return when (sfx) {
            Sfx.PLACE_0, Sfx.PLACE_1, Sfx.PLACE_2, Sfx.PLACE_3, Sfx.PLACE_4 -> {
                // A heavy stone set in its socket, and a lute note.
                val k = sfx.ordinal - Sfx.PLACE_0.ordinal
                val b = s.buffer(1.1f)
                s.tone(b, 0f, 130.0, 48.0, 0.14f, 0.75f, attack = 0.001f, release = 0.08f)
                s.noise(b, 0f, 0.08f, 0.4f, 1500.0, 300.0)
                s.pluck(b, 0.015f, s.midi(placeNotes[k]), 0.9f, 0.35f, damping = 0.994f, bright = 0.25f)
                s.normalize(s.reverb(b, 0.35f, 0.9f), 0.8f)
            }
            Sfx.CLICK -> {
                val b = s.buffer(0.14f)
                s.tone(b, 0f, 320.0, 200.0, 0.05f, 0.6f, Wave.TRIANGLE, attack = 0.0005f, release = 0.03f)
                s.noise(b, 0f, 0.03f, 0.3f, 1200.0, 600.0)
                s.normalize(b, 0.5f)
            }
            Sfx.LINE_CLEAR, Sfx.MULTI_LINE -> {
                val big = sfx == Sfx.MULTI_LINE
                val b = s.buffer(if (big) 2.8f else 2.2f)
                val notes = if (big) doubleArrayOf(62.0, 65.0, 69.0, 74.0, 77.0, 81.0, 86.0) else doubleArrayOf(62.0, 65.0, 69.0, 74.0, 77.0)
                notes.forEachIndexed { i, n ->
                    s.pluck(b, i * 0.09f, s.midi(n), 1.2f, 0.25f, damping = 0.995f, bright = 0.3f)
                    s.bell(b, i * 0.09f, s.midi(n + 12), 1.0f, 0.08f, bright = 0.5f)
                }
                s.bell(b, 0f, s.midi(38.0), 2.0f, 0.3f, bright = 0.4f)
                s.normalize(s.reverb(b, 0.55f, 0.92f), 0.9f)
            }
            else -> null
        }
    }

    fun future(sfx: Sfx): FloatArray? {
        val s = Synth(SoundDesigns.SFX_RATE)
        return when (sfx) {
            Sfx.PLACE_0, Sfx.PLACE_1, Sfx.PLACE_2, Sfx.PLACE_3, Sfx.PLACE_4 -> {
                val k = sfx.ordinal - Sfx.PLACE_0.ordinal
                val b = s.buffer(0.6f)
                val f = s.midi(placeNotes[k] + 12)
                s.tone(b, 0f, f * 0.5, f * 0.5, 0.06f, 0.35f, Wave.SQUARE, attack = 0.001f, release = 0.03f)
                s.tone(b, 0.04f, f, f, 0.14f, 0.3f, Wave.TRIANGLE, attack = 0.001f, release = 0.1f)
                s.tone(b, 0f, 90.0, 40.0, 0.08f, 0.4f, attack = 0.001f, release = 0.04f)
                s.normalize(s.reverb(lowpass(b, 7000.0, s.sr), 0.2f), 0.75f)
            }
            Sfx.CORNERSTONE_PLACE -> {
                val b = s.buffer(1.2f)
                for ((i, n) in listOf(76.0, 83.0, 88.0, 95.0).withIndex()) {
                    s.tone(b, i * 0.04f, s.midi(n), s.midi(n), 0.9f, 0.12f, Wave.SINE, attack = 0.01f, release = 0.6f, vibrato = 0.01)
                }
                s.tone(b, 0f, 120.0, 45.0, 0.2f, 0.5f, attack = 0.002f, release = 0.1f)
                s.normalize(s.reverb(b, 0.4f), 0.8f)
            }
            Sfx.CLICK -> {
                val b = s.buffer(0.08f)
                s.tone(b, 0f, 2200.0, 1500.0, 0.025f, 0.5f, Wave.SINE, attack = 0.0005f, release = 0.015f)
                s.normalize(b, 0.45f)
            }
            Sfx.LINE_CLEAR, Sfx.MULTI_LINE -> {
                val big = sfx == Sfx.MULTI_LINE
                val b = s.buffer(if (big) 2.2f else 1.6f)
                val notes = if (big) doubleArrayOf(64.0, 67.0, 71.0, 76.0, 79.0, 83.0, 88.0, 91.0) else doubleArrayOf(64.0, 67.0, 71.0, 76.0, 79.0, 83.0)
                notes.forEachIndexed { i, n ->
                    s.tone(b, i * 0.045f, s.midi(n), s.midi(n), 0.3f, 0.14f, Wave.SQUARE, attack = 0.002f, release = 0.2f)
                    s.tone(b, i * 0.045f, s.midi(n + 12), s.midi(n + 12), 0.4f, 0.08f, Wave.SINE, attack = 0.002f, release = 0.3f)
                }
                s.noise(b, 0f, 0.8f, 0.25f, 1500.0, 9000.0, attack = 0.2f, decay = 0f, highpass = true)
                s.normalize(s.reverb(lowpass(b, 8000.0, s.sr), 0.4f), 0.85f)
            }
            Sfx.DISCARD -> {
                // A downward zap into the plasma core.
                val b = s.buffer(0.9f)
                s.tone(b, 0f, 900.0, 60.0, 0.5f, 0.4f, Wave.SAW, attack = 0.002f, release = 0.2f)
                s.tone(b, 0f, 450.0, 30.0, 0.5f, 0.3f, Wave.SQUARE, attack = 0.002f, release = 0.2f)
                s.noise(b, 0.05f, 0.6f, 0.3f, 3000.0, 400.0, decay = 0.25f)
                s.normalize(s.reverb(lowpass(b, 5000.0, s.sr), 0.3f), 0.85f)
            }
            else -> null
        }
    }

    /** A slow, cavernous piece in D minor: drone, lute, a distant bell and dripping water. */
    fun templeMusic(): FloatArray {
        val rate = MusicComposer.RATE
        val s = Synth(rate)
        val beat = 60f / 52f
        val bar = beat * 4
        val progression = listOf("Dm", "Dm", "Bb", "A", "Dm", "Gm", "A", "A", "Dm", "C", "Bb", "A", "Gm", "A", "Dm", "Dm")
        val chords = mapOf(
            "Dm" to intArrayOf(50, 53, 57), "Bb" to intArrayOf(46, 50, 53), "A" to intArrayOf(45, 49, 52),
            "Gm" to intArrayOf(43, 46, 50), "C" to intArrayOf(48, 52, 55),
        )
        val loopSeconds = bar * progression.size
        val b = s.buffer(loopSeconds + 6f)
        val rnd = Random(1485)
        progression.forEachIndexed { i, name ->
            val t0 = i * bar
            val ch = chords.getValue(name)
            // Drone on D and A under everything.
            for (n in doubleArrayOf(38.0, 45.0)) {
                s.tone(b, t0, s.midi(n), s.midi(n), bar + 1.2f, 0.05f, Wave.SAW, attack = 1.5f, release = 1.5f)
            }
            s.tone(b, t0, s.midi(ch[0] - 12.0), s.midi(ch[0] - 12.0), bar, 0.14f, attack = 0.2f, release = 1.4f)
            // Low voices.
            for (n in ch) s.tone(b, t0, s.midi(n + 12.0), s.midi(n + 12.0), bar + 1f, 0.02f, Wave.TRIANGLE, attack = 1.6f, release = 1.6f, vibrato = 0.004)
            // Lute in quarter notes.
            val arp = intArrayOf(ch[0] + 12, ch[1] + 12, ch[2] + 12, ch[1] + 12)
            for (k in 0 until 4) {
                if (k > 0 && rnd.nextFloat() < 0.2f) continue
                s.pluck(b, t0 + k * beat, s.midi(arp[k].toDouble()), beat * 2.2f, 0.16f + rnd.nextFloat() * 0.04f, damping = 0.994f, bright = 0.22f)
            }
            if (i % 4 == 0) s.bell(b, t0, s.midi(38.0), 4f, 0.18f, bright = 0.35f)
            // Water dripping somewhere in the dark.
            if (rnd.nextFloat() < 0.6f) {
                val at = t0 + rnd.nextFloat() * bar
                val f = 1300.0 + rnd.nextDouble() * 900.0
                s.tone(b, at, f, f * 1.6, 0.05f, 0.05f, attack = 0.001f, release = 0.04f)
            }
        }
        val wet = s.reverb(lowpass(b, 2800.0, rate), 0.65f, 0.92f, 0.35f)
        return s.normalize(MusicComposer.foldLoop(wet, loopSeconds), 0.7f)
    }

    /** Synthwave in E minor: pulsing bass, drum machine, detuned pads and a square-wave arpeggio. */
    fun futureMusic(): FloatArray {
        val rate = MusicComposer.RATE
        val s = Synth(rate)
        val beat = 60f / 104f
        val bar = beat * 4
        val progression = listOf("Em", "C", "G", "D", "Em", "C", "G", "D", "Am", "C", "Em", "B")
        val chords = mapOf(
            "Em" to intArrayOf(52, 55, 59), "C" to intArrayOf(48, 52, 55), "G" to intArrayOf(55, 59, 62),
            "D" to intArrayOf(50, 54, 57), "Am" to intArrayOf(57, 60, 64), "B" to intArrayOf(47, 51, 54),
        )
        val bars = progression + progression
        val loopSeconds = bar * bars.size
        val tail = 4f
        val drums = s.buffer(loopSeconds + tail)
        val bass = s.buffer(loopSeconds + tail)
        val pads = s.buffer(loopSeconds + tail)
        val arps = s.buffer(loopSeconds + tail)
        val rnd = Random(2077)
        bars.forEachIndexed { i, name ->
            val t0 = i * bar
            val ch = chords.getValue(name)
            for (k in 0 until 4) {
                val bt = t0 + k * beat
                s.tone(drums, bt, 150.0, 42.0, 0.28f, 0.55f, attack = 0.001f, release = 0.12f)
                if (k % 2 == 1) s.noise(drums, bt, 0.2f, 0.28f, 2200.0, 900.0, decay = 0.07f)
                s.noise(drums, bt + beat / 2, 0.04f, 0.1f, 8000.0, 8000.0, decay = 0.015f, highpass = true)
            }
            for (k in 0 until 8) {
                val root = s.midi(ch[0] - 12.0)
                s.tone(bass, t0 + k * beat / 2, root, root, beat / 2 * 0.8f, 0.22f, Wave.SAW, attack = 0.003f, release = 0.05f)
            }
            for (n in ch) for (d in doubleArrayOf(-0.1, 0.1)) {
                s.tone(pads, t0, s.midi(n + d), s.midi(n + d), bar + 0.4f, 0.03f, Wave.SAW, attack = 0.6f, release = 0.8f)
            }
            // The arpeggio sits out the first four bars of each pass.
            if (i % progression.size >= 4) {
                val arp = intArrayOf(ch[0] + 24, ch[1] + 24, ch[2] + 24, ch[1] + 24)
                for (k in 0 until 16) {
                    val n = arp[k % 4] + if (k >= 8 && rnd.nextFloat() < 0.25f) 12 else 0
                    s.tone(arps, t0 + k * beat / 4, s.midi(n.toDouble()), s.midi(n.toDouble()), beat / 4 * 0.7f, 0.05f, Wave.SQUARE, attack = 0.002f, release = 0.04f)
                }
            }
        }
        lowpass(bass, 700.0, rate)
        lowpass(pads, 2200.0, rate)
        lowpass(arps, 4500.0, rate)
        val mix = FloatArray(drums.size) { drums[it] * 0.8f + bass[it] + pads[it] + arps[it] * 0.9f }
        val wet = s.reverb(mix, 0.35f, 0.86f, 0.4f)
        return s.normalize(MusicComposer.foldLoop(wet, loopSeconds), 0.7f)
    }
}
