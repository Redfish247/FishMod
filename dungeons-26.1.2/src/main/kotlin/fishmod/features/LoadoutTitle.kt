package fishmod.features

import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.Events
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import java.util.regex.Pattern

/**
 * Parses the item-customizer "You equipped <Name>!" chat line and flashes the loadout name as an
 * on-screen title, since the chat line alone is easy to miss mid-fight. Never cancels the chat line.
 */
object LoadoutTitle {

    private val PATTERN: Pattern = Pattern.compile("^You equipped (.+)!$")

    @JvmStatic
    fun init() {
        Events.ON_GAME_MESSAGE.register { text -> onMessage(text) }
    }

    private fun onMessage(text: Component?): Boolean {
        if (!FishSettings.loadoutTitleEnabled || text == null) return false
        val s = text.string ?: return false

        val m = PATTERN.matcher(s.trim())
        if (!m.matches()) return false

        val title = Component.literal(m.group(1))
        val subtitle = Component.literal("§7Loadout equipped")

        // ON_GAME_MESSAGE fires on the network thread — touch the HUD only on the client thread.
        val mc = Minecraft.getInstance()
        mc.execute {
            val hud = mc.gui
            hud.setTimes(0, 25, 8)
            hud.setTitle(title)
            hud.setSubtitle(subtitle)
        }
        return false // keep the original chat line
    }
}
