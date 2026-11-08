package fishmod.utils.events

import fishmod.utils.events.interfaces.PacketHook

/** Typed packet listener; the netty thread skips the main-thread hop when no gated-on listener wants the type. */
fun registerPacket(vararg types: Class<*>, gate: (() -> Boolean)? = null, hook: PacketHook) {
    PacketFilter.register(arrayOf(*types), gate?.let { g -> java.util.function.BooleanSupplier { g() } }, hook)
}
