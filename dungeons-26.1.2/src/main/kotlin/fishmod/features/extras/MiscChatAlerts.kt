package fishmod.features.extras

import fishmod.utils.FishUtil
import fishmod.utils.events.FishEvents
import fishmod.utils.events.chatPlain
import net.minecraft.network.chat.Component
import fishmod.features.extras.ExtrasSettings as S

// Hide Sky Mall / Lottery daily buff lines, and Auction outbid alert
object MiscChatAlerts {
    // from SkyHanni (ChatFilter skymall/lottery lists)
    private val BUFF_LINES = setOf(
        "New day! Your Sky Mall buff changed!",
        "You can disable this messaging by toggling Sky Mall in your /hotm!",
        "New day! Your Lottery buff changed!",
        "You can disable this messaging by toggling Lottery in your /hotf!",
    )
    // from SkyHanni (AuctionOutbidWarning)
    private val OUTBID = Regex("""^\[Auction] (.+) outbid you by ([\d,.]+) coins for (.+?) CLICK""")

    fun init() {
        FishEvents.chat.register { text ->
            val s = text.chatPlain.trim()
            if (S.hideBuffMessages && s in BUFF_LINES) return@register true
            if (S.outbidAlert) OUTBID.find(s)?.let {
                ExtrasSounds.play(S.outbidSound, 100, "fm_outbid")
                FishUtil.forceTitle(Component.literal("§cOutbid!"), Component.literal("§f" + it.groupValues[3] + " §7by §6" + it.groupValues[2]), 2500)
            }
            false
        }
    }
}
