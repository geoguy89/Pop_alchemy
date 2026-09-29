package com.geoguy89.refinersfire.audio

import com.geoguy89.refinersfire.ThemeId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.FloatControl
import kotlin.math.log10

/**
 * Java Sound backend. Each effect gets a few pre-loaded clips so overlapping plays work; the soundtrack is one looping
 * clip, which is gapless. Everything is synthesised on a background thread when a theme is selected.
 */
class DesktopAudio : AudioPlayer {
    private val clips = ConcurrentHashMap<Sfx, List<Clip>>()
    private val next = ConcurrentHashMap<Sfx, Int>()
    @Volatile private var music: Clip? = null
    @Volatile private var musicWanted = false
    @Volatile private var available = true
    @Volatile private var theme: ThemeId? = null
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "refinersfire-audio").apply { isDaemon = true } }

    override var sfxVolume = 0.8f
    override var musicVolume = 0.5f
        set(value) {
            field = value
            music?.let { setGain(it, value * MUSIC_GAIN) }
        }

    override fun setTheme(theme: ThemeId) {
        if (this.theme == theme) return
        this.theme = theme
        worker.execute {
            if (this.theme != theme || !available) return@execute
            loadMusic(theme)
            loadSfx(theme)
        }
    }

    private fun loadMusic(theme: ThemeId) {
        try {
            music?.let { old -> music = null; old.stop(); old.close() }
            val pcm = toBytes(MusicComposer.compose(theme))
            val clip = AudioSystem.getClip()
            clip.open(AudioFormat(MusicComposer.RATE.toFloat(), 16, 1, true, false), pcm, 0, pcm.size)
            setGain(clip, musicVolume * MUSIC_GAIN)
            music = clip
            if (musicWanted) clip.loop(Clip.LOOP_CONTINUOUSLY)
        } catch (_: Exception) {
        }
    }

    private fun loadSfx(theme: ThemeId) {
        for (sfx in Sfx.entries) {
            if (!available) break
            try {
                val pcm = toBytes(SoundDesigns.render(sfx, theme))
                val fmt = AudioFormat(SoundDesigns.SFX_RATE.toFloat(), 16, 1, true, false)
                val old = clips.put(sfx, List(VOICES) { AudioSystem.getClip().apply { open(fmt, pcm, 0, pcm.size) } })
                old?.forEach { it.close() }
            } catch (e: Exception) {
                // No audio device (or a headless machine): play silently.
                available = false
            }
        }
    }

    private fun toBytes(buf: FloatArray): ByteArray {
        val pcm = Synth.toPcm16(buf)
        val out = ByteArray(pcm.size * 2)
        for (i in pcm.indices) {
            out[i * 2] = (pcm[i].toInt() and 0xFF).toByte()
            out[i * 2 + 1] = (pcm[i].toInt() shr 8).toByte()
        }
        return out
    }

    private fun setGain(clip: Clip, linear: Float) {
        if (!clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) return
        val c = clip.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
        val db = if (linear <= 0.0001f) c.minimum else (20f * log10(linear)).coerceIn(c.minimum, c.maximum)
        c.value = db
    }

    override fun play(sfx: Sfx, volume: Float, rate: Float) {
        if (sfxVolume <= 0f) return
        val voices = clips[sfx] ?: return
        val i = (next[sfx] ?: 0)
        next[sfx] = (i + 1) % voices.size
        val clip = voices[i]
        try {
            clip.stop()
            clip.framePosition = 0
            setGain(clip, (sfxVolume * volume).coerceIn(0f, 1f))
            clip.start()
        } catch (_: Exception) {
        }
    }

    override fun startMusic() {
        musicWanted = true
        music?.let { if (!it.isRunning) it.loop(Clip.LOOP_CONTINUOUSLY) }
    }

    override fun pauseMusic() {
        musicWanted = false
        music?.stop()
    }

    override fun release() {
        worker.shutdownNow()
        clips.values.flatten().forEach { it.close() }
        music?.close()
    }

    private companion object {
        const val VOICES = 3
        const val MUSIC_GAIN = 0.6f
    }
}
