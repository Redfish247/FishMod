package fishmod.utils.events.interfaces

fun interface ServerTickEvent {
    fun onServerTick(): Boolean
}
