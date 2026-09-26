package fishmod.utils

import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

object ScreenTitle {
    private var last: Component? = null
    private var lastPlain = ""

    @JvmStatic
    fun plain(screen: Screen): String {
        val title = screen.title
        if (title !== last) {
            last = title
            lastPlain = Constants.STRIP_COLOR_REGEX.replace(title.string, "")
        }
        return lastPlain
    }
}
