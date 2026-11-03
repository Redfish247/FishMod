package fishmod.utils.events.interfaces

fun interface ServerTickHook {
    fun onServerTick(): Boolean
}
