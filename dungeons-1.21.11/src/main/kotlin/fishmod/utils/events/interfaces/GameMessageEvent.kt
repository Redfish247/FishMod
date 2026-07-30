package fishmod.utils.events.interfaces

import net.minecraft.text.Text

fun interface GameMessageEvent {
    fun onGameMessage(text: Text): Boolean
}
