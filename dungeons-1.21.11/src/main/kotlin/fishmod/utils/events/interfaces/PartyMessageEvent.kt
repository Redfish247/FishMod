package fishmod.utils.events.interfaces

fun interface PartyMessageEvent {
    fun sentMessage(username: String, message: String): Boolean
}
