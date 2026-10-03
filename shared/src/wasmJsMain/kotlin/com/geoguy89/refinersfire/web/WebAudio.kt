package com.geoguy89.refinersfire.web

import com.geoguy89.refinersfire.ThemeId
import com.geoguy89.refinersfire.audio.AudioPlayer
import com.geoguy89.refinersfire.audio.MusicComposer
import com.geoguy89.refinersfire.audio.Sfx
import com.geoguy89.refinersfire.audio.SoundDesigns
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private fun jsAudioInit(): Unit = js(
    """{
        if (window.__rf) return;
        var Ctx = window.AudioContext || window.webkitAudioContext;
        var ctx = Ctx ? new Ctx() : null;
        var a = { ctx: ctx, buffers: {}, music: null, musicBuffer: null, sfxGain: null, musicGain: null, wantMusic: false };
        if (ctx) {
            a.sfxGain = ctx.createGain(); a.sfxGain.connect(ctx.destination);
            a.musicGain = ctx.createGain(); a.musicGain.connect(ctx.destination);
            // iOS (and most browsers) only let sound start after the player touches the page.
            var unlock = function () {
                if (ctx.state !== 'running') ctx.resume();
                var b = ctx.createBuffer(1, 1, 22050); var s = ctx.createBufferSource(); s.buffer = b; s.connect(ctx.destination); s.start(0);
                if (a.wantMusic && !a.music && a.musicBuffer) window.__rfStartMusic();
            };
            ['pointerdown', 'touchend', 'keydown'].forEach(function (t) { document.addEventListener(t, unlock, true); });
        }
        window.__rfStartMusic = function () {
            if (!a.ctx || !a.musicBuffer || a.music) return;
            var s = a.ctx.createBufferSource(); s.buffer = a.musicBuffer; s.loop = true; s.connect(a.musicGain); s.start(0); a.music = s;
        };
        window.__rf = a;
    }""",
)

/** Decode little-endian 16-bit PCM (as base64) into an AudioBuffer: the music, or a named sound. */
private fun jsLoad(name: String, b64: String, rate: Int): Unit = js(
    """{
        var a = window.__rf; if (!a || !a.ctx) return;
        var bin = atob(b64); var n = bin.length >> 1;
        var buf = a.ctx.createBuffer(1, Math.max(1, n), rate); var ch = buf.getChannelData(0);
        for (var i = 0; i < n; i++) { var v = bin.charCodeAt(2 * i) | (bin.charCodeAt(2 * i + 1) << 8); if (v >= 32768) v -= 65536; ch[i] = v / 32768; }
        if (name === '__music') {
            if (a.music) { try { a.music.stop(); } catch (e) {} a.music = null; }
            a.musicBuffer = buf;
            if (a.wantMusic && a.ctx.state === 'running') window.__rfStartMusic();
        } else a.buffers[name] = buf;
    }""",
)

private fun jsPlay(name: String, volume: Double, rate: Double): Unit = js(
    """{
        var a = window.__rf; if (!a || !a.ctx || a.ctx.state !== 'running') return;
        var b = a.buffers[name]; if (!b) return;
        var s = a.ctx.createBufferSource(); s.buffer = b; s.playbackRate.value = rate;
        var g = a.ctx.createGain(); g.gain.value = volume; s.connect(g); g.connect(a.sfxGain); s.start(0);
    }""",
)

private fun jsMusic(want: Boolean): Unit = js(
    """{
        var a = window.__rf; if (!a) return; a.wantMusic = want;
        if (want) { if (a.ctx && a.ctx.state === 'running') window.__rfStartMusic(); }
        else if (a.music) { try { a.music.stop(); } catch (e) {} a.music = null; }
    }""",
)

private fun jsVolumes(sfx: Double, music: Double): Unit = js("{ var a = window.__rf; if (a && a.sfxGain) { a.sfxGain.gain.value = sfx; a.musicGain.gain.value = music; } }")

private fun jsLater(ms: Int, run: () -> Unit): Unit = js("setTimeout(run, ms)")

/**
 * Sound for the web version. The game's synthesiser renders each theme's effects and soundtrack here (a little at a
 * time, so the page stays responsive), and the browser's Web Audio plays them.
 */
@OptIn(ExperimentalEncodingApi::class)
class WebAudio : AudioPlayer {
    private var theme: ThemeId? = null
    private var generation = 0

    override var sfxVolume = 0.8f
        set(value) { field = value; jsVolumes(value.toDouble(), musicVolume * MUSIC_GAIN) }
    override var musicVolume = 0.5f
        set(value) { field = value; jsVolumes(sfxVolume.toDouble(), value * MUSIC_GAIN) }

    init {
        jsAudioInit()
        jsVolumes(sfxVolume.toDouble(), musicVolume * MUSIC_GAIN)
    }

    private fun pcm(buf: FloatArray): String {
        val out = ByteArray(buf.size * 2)
        for (i in buf.indices) {
            val v = (buf[i].coerceIn(-1f, 1f) * 32767).toInt()
            out[2 * i] = (v and 0xff).toByte()
            out[2 * i + 1] = ((v shr 8) and 0xff).toByte()
        }
        return Base64.Default.encode(out)
    }

    override fun setTheme(theme: ThemeId) {
        if (this.theme == theme) return
        this.theme = theme
        val gen = ++generation
        // Effects first (one per tick), then the soundtrack, which takes longest.
        val sounds = Sfx.entries.toList()
        fun next(i: Int) {
            if (gen != generation) return
            if (i < sounds.size) {
                jsLoad(sounds[i].name, pcm(SoundDesigns.render(sounds[i], theme)), SoundDesigns.SFX_RATE)
                jsLater(0) { next(i + 1) }
            } else {
                jsLater(400) {
                    if (gen == generation) jsLoad("__music", pcm(MusicComposer.compose(theme)), MusicComposer.RATE)
                }
            }
        }
        jsLater(50) { next(0) }
    }

    override fun play(sfx: Sfx, volume: Float, rate: Float) = jsPlay(sfx.name, volume.toDouble(), rate.toDouble())
    override fun startMusic() = jsMusic(true)
    override fun pauseMusic() = jsMusic(false)
    override fun release() = jsMusic(false)

    private companion object {
        const val MUSIC_GAIN = 0.55
    }
}
