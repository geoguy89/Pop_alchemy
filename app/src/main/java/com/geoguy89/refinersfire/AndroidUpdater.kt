package com.geoguy89.refinersfire

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import com.geoguy89.refinersfire.net.AppUpdater
import com.geoguy89.refinersfire.net.ReleaseFeed
import com.geoguy89.refinersfire.net.UpdateInfo
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Self-update from GitHub Releases: download the APK, then install it through a PackageInstaller session. On Android
 * 12+ (with UPDATE_PACKAGES_WITHOUT_USER_ACTION) an app updating itself needs no confirmation screen; older versions,
 * or a first install permission, still get Android's prompt. Android only accepts the update when it's signed with
 * the same key as the installed app.
 */
class AndroidUpdater(private val context: Context) : AppUpdater {
    override val supported = true

    override val installedBuild: Int = run {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else @Suppress("DEPRECATION") info.versionCode
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 30_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "RefinersFire-Android")
    }

    override fun latest(done: (UpdateInfo?) -> Unit) {
        thread(isDaemon = true, name = "refinersfire-update-check") {
            val info = try {
                val c = open(ReleaseFeed.LATEST_URL)
                c.setRequestProperty("Accept", "application/vnd.github+json")
                if (c.responseCode == 200) ReleaseFeed.parse(c.inputStream.bufferedReader().use { it.readText() }) else null
            } catch (e: Exception) {
                null
            }
            done(info)
        }
    }

    override fun install(info: UpdateInfo, progress: (Float) -> Unit, done: (String?) -> Unit) {
        thread(isDaemon = true, name = "refinersfire-update") {
            try {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val apk = File(dir, "refinersfire-${info.build}.apk")
                val c = open(info.apkUrl)
                if (c.responseCode != 200) throw IllegalStateException("Download failed (${c.responseCode}).")
                val total = c.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
                c.inputStream.use { input ->
                    apk.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var read = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            read += n
                            if (total > 0) progress((read.toFloat() / total).coerceAtMost(1f))
                        }
                    }
                }
                val installer = context.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(context.packageName)
                    if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
                val id = installer.createSession(params)
                installer.openSession(id).use { session ->
                    session.openWrite("refinersfire.apk", 0, apk.length()).use { out -> apk.inputStream().use { it.copyTo(out) }; session.fsync(out) }
                    val intent = Intent(context, InstallStatusReceiver::class.java).setAction(InstallStatusReceiver.ACTION)
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                    session.commit(PendingIntent.getBroadcast(context, id, intent, flags).intentSender)
                }
                done(null)
            } catch (e: Exception) {
                Log.w("AndroidUpdater", "Update failed", e)
                done(e.message ?: "The update couldn't be installed.")
            }
        }
    }
}

/** Receives the installer's result; when Android needs the user to confirm, it opens that screen. */
class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // The installer closes the game; UpdatedReceiver offers to reopen it.
            else -> Log.w("AndroidUpdater", "Install status: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
        }
    }

    companion object {
        const val ACTION = "com.geoguy89.refinersfire.INSTALL_STATUS"
    }
}

/**
 * The game was just replaced by a newer version (the installer stops the old one). Android doesn't let an app
 * relaunch itself from the background, so it posts a notification to tap instead.
 */
class UpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
        val nm = context.getSystemService(android.app.NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                android.app.NotificationChannel(CHANNEL, "Updates", android.app.NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "When the game has updated itself" },
            )
        }
        val open = PendingIntent.getActivity(
            context, 1, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = androidx.core.app.NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_flame)
            .setContentTitle("Refiner's Fire updated")
            .setContentText(if (version.isNotEmpty()) "Version $version is ready. Tap to play." else "Tap to play.")
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { nm.notify(UPDATED_ID, n) } // Without the notification permission there's simply no notice.
    }

    companion object {
        const val CHANNEL = "updates"
        const val UPDATED_ID = 4242
    }
}
