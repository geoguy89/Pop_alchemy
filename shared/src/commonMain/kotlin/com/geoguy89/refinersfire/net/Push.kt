package com.geoguy89.refinersfire.net

/**
 * Notifications when the game isn't open on screen: chats, challenges and friend requests. On Android this is
 * Firebase Cloud Messaging; elsewhere it isn't available (the desktop shows its own while the window is unfocused).
 */
interface PushRegistrar {
    val supported: Boolean
    /** Ask for permission if needed and get this device's token. [onToken] may be called on any thread. */
    fun register(onToken: (String) -> Unit)
}

object NoPush : PushRegistrar {
    override val supported = false
    override fun register(onToken: (String) -> Unit) = Unit
}
