package fishmod.utils.events.interfaces

import net.minecraft.text.Text

fun interface LeapEvent {
    fun onLeap(message: Text): Boolean
}
