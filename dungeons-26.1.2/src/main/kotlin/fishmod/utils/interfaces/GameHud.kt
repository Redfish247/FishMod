package fishmod.utils.interfaces

import net.minecraft.network.chat.Component

fun interface GameHud {
    fun `blade_addons$forceTitle`(title: Component, subtitle: Component)
}
