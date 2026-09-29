package com.geoguy89.refinersfire.net

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A newer release on GitHub. [build] is the CI run number, which is also the APK's versionCode. */
data class UpdateInfo(val version: String, val build: Int, val apkUrl: String, val sizeBytes: Long, val pageUrl: String)

/**
 * Checks for and installs new releases. Android downloads the APK and hands it to the system installer;
 * other platforms don't self-update.
 */
interface AppUpdater {
    val supported: Boolean
    /** The installed build (Android versionCode). */
    val installedBuild: Int
    /** [done] gets the latest release (or null when it can't be reached), on any thread. */
    fun latest(done: (UpdateInfo?) -> Unit)
    /** Download and open the installer. [progress] 0..1; [done] gets null on success or an error message. Any thread. */
    fun install(info: UpdateInfo, progress: (Float) -> Unit, done: (String?) -> Unit)
}

object NoUpdater : AppUpdater {
    override val supported = false
    override val installedBuild = 0
    override fun latest(done: (UpdateInfo?) -> Unit) = done(null)
    override fun install(info: UpdateInfo, progress: (Float) -> Unit, done: (String?) -> Unit) = done("Updates aren't supported here.")
}

@Serializable private data class GhAsset(val name: String, val browser_download_url: String, val size: Long = 0)
@Serializable private data class GhRelease(val tag_name: String, val html_url: String = "", val draft: Boolean = false, val prerelease: Boolean = false, val assets: List<GhAsset> = emptyList())

object ReleaseFeed {
    const val LATEST_URL = "https://api.github.com/repos/geoguy89/refiners-fire/releases/latest"
    private val json = Json { ignoreUnknownKeys = true }
    private val tag = Regex("""^v(\d+\.\d+\.\d+)-build\.(\d+)$""")

    /** Reads GitHub's "latest release" JSON. Tags look like v1.3.0-build.12; the APK is the asset ending in -android.apk. */
    fun parse(body: String): UpdateInfo? {
        val r = runCatching { json.decodeFromString(GhRelease.serializer(), body) }.getOrNull() ?: return null
        if (r.draft || r.prerelease) return null
        val m = tag.matchEntire(r.tag_name) ?: return null
        val apk = r.assets.firstOrNull { it.name.endsWith("-android.apk") } ?: return null
        return UpdateInfo(m.groupValues[1], m.groupValues[2].toInt(), apk.browser_download_url, apk.size, r.html_url)
    }
}
