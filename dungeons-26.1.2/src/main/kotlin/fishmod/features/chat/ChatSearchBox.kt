package fishmod.features.chat

import net.minecraft.client.gui.ComponentPath
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.navigation.FocusNavigationEvent
import net.minecraft.network.chat.Component
import org.slf4j.LoggerFactory

// Keyboard navigation (arrows/tab) must never land here; only a click or the toggle key focuses it.
class ChatSearchBox(font: Font, x: Int, y: Int, w: Int, h: Int, msg: Component) : EditBox(font, x, y, w, h, msg) {

    override fun nextFocusPath(event: FocusNavigationEvent): ComponentPath? = null

    override fun setFocused(focused: Boolean) {
        if (focused && !isFocused) LOG.info("[ChatSearch] search box focused", Throwable("focus trace"))
        super.setFocused(focused)
    }

    private companion object {
        val LOG = LoggerFactory.getLogger("fishmod")
    }
}
