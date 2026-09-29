package com.geoguy89.refinersfire

import com.geoguy89.refinersfire.audio.MusicComposer
import com.geoguy89.refinersfire.audio.SoundDesigns
import com.geoguy89.refinersfire.audio.Sfx
import com.geoguy89.refinersfire.audio.Synth
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

class AudioSynthTest {
    @Test
    fun everySoundRendersCleanly() {
        val dir = File("build/sounds").apply { mkdirs() }
        for (theme in ThemeId.entries) for (sfx in Sfx.entries) {
            val buf = SoundDesigns.render(sfx, theme)
            assertTrue("$theme $sfx is empty", buf.isNotEmpty())
            assertTrue("$theme $sfx has NaN", buf.none { it.isNaN() })
            val peak = buf.maxOf { abs(it) }
            assertTrue("$theme $sfx peak $peak", peak in 0.1f..1f)
            File(dir, "${theme.name.lowercase()}_${sfx.name.lowercase()}.wav").writeBytes(Synth.toWav(buf, SoundDesigns.SFX_RATE))
        }
    }

    @Test
    fun musicLoopIsLongAndClean() {
        for (theme in ThemeId.entries) {
            val start = System.nanoTime()
            val music = MusicComposer.compose(theme)
            val seconds = music.size / MusicComposer.RATE.toFloat()
            println("Music $theme: %.1fs generated in %d ms".format(seconds, (System.nanoTime() - start) / 1_000_000))
            assertTrue(seconds > 45f)
            assertTrue(music.none { it.isNaN() })
            // Seamless loop: the join shouldn't jump.
            assertTrue("$theme loop join", abs(music.last() - music.first()) < 0.2f)
            File("build/sounds").mkdirs()
            File("build/sounds/music_${theme.name.lowercase()}.wav").writeBytes(Synth.toWav(music, MusicComposer.RATE))
        }
    }
}
