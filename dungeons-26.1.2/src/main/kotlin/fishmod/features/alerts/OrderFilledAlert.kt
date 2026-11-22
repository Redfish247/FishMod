package fishmod.features.alerts

import fishmod.features.extras.*

import fishmod.utils.FishUtil
import fishmod.utils.events.FishEvents
import fishmod.utils.events.chatPlain
import net.minecraft.network.chat.Component
import fishmod.features.extras.ExtrasSettings as S

// Title + sound when a Bazaar order fills ("[Bazaar] Your Sell Offer for 891x Enchanted Gold Block was filled!")
object OrderFilledAlert {
    private val FILLED = Regex("""^\[Bazaar] Your (Buy Order|Sell Offer) for ([\d,]+)x (.+) was filled!$""")

    fun init() {
        FishEvents.chat.register { text ->
            if (S.orderFilledAlert) FILLED.find(text.chatPlain.trim())?.let {
                ExtrasSounds.play(S.orderFilledSound, 100, "fm_order_filled")
                FishUtil.forceTitle(Component.literal("§6" + it.groupValues[1] + " filled"), Component.literal("§a" + it.groupValues[2] + "x §f" + it.groupValues[3]), 2500)
            }
            false
        }
    }
}
