package fishmod.features

import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.Events
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text
import java.util.regex.Pattern

/**
 * Parses the item-customizer "You equipped &lt;Name&gt;!" chat line and flashes the loadout name
 * as an on-screen title, since the chat line alone is easy to miss mid-fight.
 */
object LoadoutTitle {

    private val PATTERN: Pattern = Pattern.compile("^You equipped (.+)!$")

    @JvmStatic
    fun init() {
        Events.ON_GAME_MESSAGE.register(::onMessage)
    }

    private fun onMessage(text: Text?): Boolean {
        if (!FishSettings.loadoutTitleEnabled || text == null) return false
        val s = text.string ?: return false

        val m = PATTERN.matcher(s.trim())
        if (!m.matches()) return false

        val name = m.group(1)
        val title = Text.literal(name)
        val subtitle = Text.literal("§7Loadout equipped")

        // ON_GAME_MESSAGE fires on the network thread — touch the HUD only on the client thread.
        val mc = MinecraftClient.getInstance()
        mc.execute {
            val hud = mc.inGameHud
            hud.setTitleTicks(0, 25, 8)
            hud.setTitle(title)
            hud.setSubtitle(subtitle)
        }
        return false // keep the original chat line
    }
}
