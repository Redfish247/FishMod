package fishmod.utils.events.interfaces

import net.minecraft.network.chat.Component

fun interface LeapEvent {
    fun onLeap(message: Component): Boolean
}
