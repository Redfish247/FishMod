package fishmod.utils.events.interfaces

fun interface TerminalHook {
    fun onComplete(formattedName: String, action: String, objective: String, current: Int, total: Int): Boolean
}
