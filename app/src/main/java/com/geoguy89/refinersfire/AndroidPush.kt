package com.geoguy89.refinersfire

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.geoguy89.refinersfire.net.PushRegistrar
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/** Sets up notifications as the app starts, so they can arrive even when the game isn't running. */
class GameApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidPush.init(this)
    }
}

object AndroidPush {
    /** True while the game is on screen: notifications are then left to the game itself. */
    @Volatile var visible = false
    /** Set by the activity: asks for the Android 13+ notification permission. */
    var askPermission: (() -> Unit)? = null

    const val CHANNEL = "messages"

    /** The Firebase settings come in at build time; a build without them simply has no notifications. */
    val configured: Boolean get() = BuildConfig.FIREBASE_APP_ID.isNotEmpty()

    fun init(context: Context) {
        if (!configured || FirebaseApp.getApps(context).isNotEmpty()) return
        FirebaseApp.initializeApp(
            context,
            FirebaseOptions.Builder()
                .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                .build(),
        )
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Messages and challenges", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Chats, challenges and friend requests from other players"
            })
        }
    }

    fun registrar(context: Context): PushRegistrar = object : PushRegistrar {
        override val supported: Boolean get() = configured
        override fun register(onToken: (String) -> Unit) {
            if (!configured) return
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                askPermission?.invoke()
            }
            FirebaseMessaging.getInstance().token.addOnSuccessListener { onToken(it) }
        }
    }
}

/** Receives notifications from the game server. The message never contains chat text, only who it's from. */
class PushService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        if (AndroidPush.visible) return // The game shows it itself.
        val text = message.data["text"] ?: return
        val kind = message.data["kind"] ?: "chat"
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(this, AndroidPush.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_flame)
            .setContentTitle("Refiner's Fire")
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (kind == "chat") NotificationCompat.CATEGORY_MESSAGE else NotificationCompat.CATEGORY_SOCIAL)
            .setContentIntent(open)
            .build()
        val nm = getSystemService(NotificationManager::class.java)
        // One notification per sender and kind, updated rather than stacked.
        nm.notify("$kind:${message.data["sender"]}".hashCode(), n)
    }

    override fun onNewToken(token: String) {
        // The game hands the current token to the server each time it starts.
    }
}
