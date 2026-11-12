package fishmod.features
import fishmod.compat.hud

import fishmod.utils.debug.FishDiag
import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.FishEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import java.util.regex.Pattern

object LoadoutTitle {

    private val PATTERN: Pattern = Pattern.compile("^You equipped (.+)!$")

    @JvmStatic
    fun init() {
        FishEvents.chat.register { text -> onMessage(text) }
    }

    private fun onMessage(text: Component?): Boolean {
        if (!FishSettings.loadoutTitleEnabled || text == null) return false
        val s = text.string ?: return false

        val m = PATTERN.matcher(s.trim())
        if (!m.matches()) return false

        val title = Component.literal(m.group(1))
        val subtitle = Component.literal("§7Loadout equipped")

        try {
            fishmod.utils.FishUtil.forceTitle(title, subtitle, 1250)
        } catch (t: Throwable) {
            FishDiag.fail("LoadoutTitle.1", "loadout title display failed for '${title.string}'", t)
        }
        return false
    }
}
