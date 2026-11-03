package fishmod.utils.events.interfaces

fun interface PartyChatHook {
    fun sentMessage(username: String, message: String): Boolean
}
