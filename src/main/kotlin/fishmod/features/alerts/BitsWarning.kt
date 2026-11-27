package fishmod.features.alerts

import fishmod.compat.curScreen
import fishmod.features.extras.*

import fishmod.features.garden.Garden
import fishmod.utils.FishUtil
import fishmod.utils.SidebarCache
import fishmod.utils.TextStrip
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.FishEvents
import fishmod.utils.events.chatPlain
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import fishmod.features.extras.ExtrasSettings as S

object BitsWarning {
    // from SkyHanni (BitsApi availablemenu / scoreboard / boostercookieate)
    private val AVAILABLE = Regex("""^Bits Available: ([\d,]+)""")
    private val SCOREBOARD = Regex("""^Bits: ([\d,.]+)""")
    private const val COOKIE_ATE = "You consumed a Booster Cookie!"

    private var available = -1L
    private var lastBits = -1L
    private var tick = 0

    fun init() {
        FishEvents.chat.register { text ->
            if (S.bitsWarning && text.chatPlain.trim().startsWith(COOKIE_ATE)) available = -1
            false
        }
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (!S.bitsWarning || ++tick % 10 != 0) return@register
            try {
                (mc.curScreen as? AbstractContainerScreen<*>)?.let { scan(it) }
                val bits = SidebarCache.entries.firstNotNullOfOrNull { SCOREBOARD.find(it.plain.trim())?.groupValues?.get(1) }
                    ?.replace(",", "")?.substringBefore('.')?.toLongOrNull() ?: return@register
                if (lastBits in 0 until bits && available > 0) {
                    available -= bits - lastBits
                    if (available <= 0) warn()
                }
                lastBits = bits
            } catch (t: Throwable) {
                FishDiag.fail("BitsWarning.1", "bits tick failed", t)
            }
        }
    }

    private fun scan(screen: AbstractContainerScreen<*>) {
        for (slot in screen.menu.slots) {
            for (line in Garden.lore(slot.item)) {
                val m = AVAILABLE.find(TextStrip.plain(line).trim()) ?: continue
                available = m.groupValues[1].replace(",", "").toLongOrNull() ?: continue
                return
            }
        }
    }

    private fun warn() {
        available = 0
        // from SkyHanni (NoBitsWarning)
        val msg = Component.literal(fishmod.utils.FishMsg.prefix() + "§bNo Bits Available! §eClick to buy booster cookies on the bazaar.")
            .withStyle { it.withClickEvent(net.minecraft.network.chat.ClickEvent.RunCommand("/bz booster cookie")) }
        FishUtil.addChatMessage(msg)
        FishUtil.forceTitle(Component.literal("§bNo Bits Available"), Component.literal(""), 2500)
    }
}
