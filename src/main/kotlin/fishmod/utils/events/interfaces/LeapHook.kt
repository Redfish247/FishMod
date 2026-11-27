package fishmod.utils.events.interfaces

import net.minecraft.network.chat.Component

fun interface LeapHook {
    fun onLeap(message: Component): Boolean
}
