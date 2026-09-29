package com.geoguy89.refinersfire.audio

import com.geoguy89.refinersfire.ThemeId

/** Plays the synthesised sounds; each platform supplies its own backend. */
interface AudioPlayer {
    var sfxVolume: Float
    var musicVolume: Float
    fun play(sfx: Sfx, volume: Float = 1f, rate: Float = 1f)
    fun startMusic()
    fun pauseMusic()
    /** Switch the sound set and soundtrack. Sounds are prepared in the background; the first call starts loading. */
    fun setTheme(theme: ThemeId)
    fun release()
}

/** Used in previews and tests. */
object SilentAudio : AudioPlayer {
    override var sfxVolume = 0f
    override var musicVolume = 0f
    override fun play(sfx: Sfx, volume: Float, rate: Float) = Unit
    override fun startMusic() = Unit
    override fun pauseMusic() = Unit
    override fun setTheme(theme: ThemeId) = Unit
    override fun release() = Unit
}
