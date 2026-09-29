package com.geoguy89.refinersfire.net

import kotlinx.serialization.Serializable

@Serializable
data class ChatKeyPair(val publicKey: String, val privateKey: String)

/** An encrypted message as it travels: the server only ever sees these two fields. */
@Serializable
data class Sealed(val nonce: String, val ct: String)

/** Platform crypto for end-to-end encrypted chat. */
interface ChatCrypto {
    fun newKeyPair(): ChatKeyPair
    fun seal(myPrivate: String, theirPublic: String, fromId: String, toId: String, text: String): Sealed
    /** Null when the message can't be authenticated (tampered with, or sealed to a key we no longer have). */
    fun open(myPrivate: String, theirPublic: String, fromId: String, toId: String, sealed: Sealed): String?
    /** A short number both friends can compare to confirm nobody swapped their keys. */
    fun safetyCode(publicA: String, publicB: String): String
}

object NoChatCrypto : ChatCrypto {
    override fun newKeyPair() = ChatKeyPair("", "")
    override fun seal(myPrivate: String, theirPublic: String, fromId: String, toId: String, text: String) = Sealed("", "")
    override fun open(myPrivate: String, theirPublic: String, fromId: String, toId: String, sealed: Sealed): String? = null
    override fun safetyCode(publicA: String, publicB: String) = ""
}

/** One line of a conversation, as kept on this device. */
@Serializable
data class ChatLine(val fromMe: Boolean, val text: String, val t: Long, val failed: Boolean = false)

/** A message waiting on the server for us. */
@Serializable
data class InboundMessage(val id: String, val from: String, val nonce: String, val ct: String, val t: Long)

const val CHAT_MAX_CHARS = 500
