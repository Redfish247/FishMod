package fishmod.features.alerts

import fishmod.features.extras.*

import fishmod.utils.FishUtil
import fishmod.utils.events.FishEvents
import fishmod.utils.events.chatPlain
import net.minecraft.network.chat.Component
import fishmod.features.extras.ExtrasSettings as S

// Auction outbid alert, Reindrake spawn alert
object MiscChatAlerts {
    // from SkyHanni (AuctionOutbidWarning)
    private val OUTBID = Regex("""^\[Auction] (.+) outbid you by ([\d,.]+) coins for (.+?) CLICK""")

    // from SkyHanni (WinterApi reindrake spawn)
    private val REINDRAKE = Regex("""^WOAH! .+ summoned (?:a Reindrake|TWO Reindrakes) from the depths!$""")

    fun init() {
        FishEvents.chat.register { text ->
            if (!S.outbidAlert && !S.reindrakeAlert) return@register false
            val s = text.chatPlain.trim()
            if (S.outbidAlert) OUTBID.find(s)?.let {
                ExtrasSounds.play(S.outbidSound, 100, "fm_outbid")
                FishUtil.forceTitle(Component.literal("§cOutbid!"), Component.literal("§f" + it.groupValues[3] + " §7by §6" + it.groupValues[2]), 2500)
            }
            if (S.reindrakeAlert && REINDRAKE.matches(s)) {
                FishUtil.forceTitle(Component.literal("§cReindrake!"), Component.literal(""), 2500)
                FishUtil.addChatMessage(Component.literal("§cReindrake spawned! ").append(Component.literal("§a[Warp to Winter spawn]")
                    .withStyle { it.withClickEvent(net.minecraft.network.chat.ClickEvent.RunCommand("/warp winter")) }))
            }
            false
        }
    }
}
