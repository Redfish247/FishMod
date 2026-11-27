package fishmod.features.events

import fishmod.compat.curScreen
import fishmod.features.extras.*

import fishmod.utils.FishUtil
import fishmod.utils.TextStrip
import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import fishmod.features.extras.ExtrasSettings as S

object StrayRabbitAlert {
    // from SkyHanni (CFDataLoader clickMe patterns)
    private const val CLICK_ME = "CLICK ME!"
    private val GOLDEN = Regex("""^Golden Rabbit - (.+)$""")
    private var shown = false
    private var tick = 0

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (!S.strayRabbitAlert || ++tick % 5 != 0) return@register
            val screen = mc.curScreen as? AbstractContainerScreen<*> ?: run { shown = false; return@register }
            try {
                var golden: String? = null
                val any = screen.menu.slots.any { slot ->
                    val name = TextStrip.plain(slot.item.hoverName.string).trim()
                    GOLDEN.find(name)?.let { golden = it.groupValues[1] }
                    name == CLICK_ME || golden != null
                }
                if (any && !shown) {
                    ExtrasSounds.play(S.strayRabbitSound, 100, "fm_stray_rabbit")
                    FishUtil.forceTitle(Component.literal(if (golden != null) "§6Golden Rabbit!" else "§eStray Rabbit!"), Component.literal(golden?.let { "§a$it" } ?: ""), 1500)
                }
                shown = any
            } catch (t: Throwable) {
                FishDiag.fail("StrayRabbitAlert.1", "stray rabbit scan failed", t)
            }
        }
    }
}
