package fishmod.utils.events.interfaces

fun interface TerminalEvent {
    fun onComplete(formattedName: String, action: String, objective: String, current: Int, total: Int): Boolean
}
