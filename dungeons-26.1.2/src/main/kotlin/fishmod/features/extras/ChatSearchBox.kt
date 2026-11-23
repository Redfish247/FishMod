package fishmod.features.extras

import fishmod.features.chat.ChatSearch

import net.minecraft.client.gui.ComponentPath
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.navigation.FocusNavigationEvent
import net.minecraft.network.chat.Component

class ChatSearchBox(font: Font, x: Int, y: Int, w: Int, h: Int, msg: Component) : EditBox(font, x, y, w, h, msg) {

    override fun nextFocusPath(event: FocusNavigationEvent): ComponentPath? = null
}
