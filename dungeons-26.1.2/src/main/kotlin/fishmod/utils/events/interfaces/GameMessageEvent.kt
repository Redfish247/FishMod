package fishmod.utils.events.interfaces

import net.minecraft.network.chat.Component

fun interface GameMessageEvent {
    fun onGameMessage(text: Component): Boolean
}
