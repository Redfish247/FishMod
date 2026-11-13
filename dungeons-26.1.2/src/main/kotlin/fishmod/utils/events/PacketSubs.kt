package fishmod.utils.events

import fishmod.utils.events.interfaces.PacketHook

fun registerPacket(vararg types: Class<*>, gate: (() -> Boolean)? = null, hook: PacketHook) {
    PacketFilter.register(arrayOf(*types), gate?.let { g -> java.util.function.BooleanSupplier { g() } }, hook)
}
