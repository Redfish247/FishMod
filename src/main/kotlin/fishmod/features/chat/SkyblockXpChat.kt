package fishmod.features.chat

import fishmod.utils.events.chatPlain
import fishmod.features.extras.*

import fishmod.utils.FishUtil
import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.network.chat.Component
import fishmod.features.extras.ExtrasSettings as S

object SkyblockXpChat {
    private val XP = Regex("\\+(\\d+) SkyBlock XP \\(([^)]+)\\) \\((\\d+)/100\\)")
    private var last = ""
    private var lastMs = 0L

    fun init() {
        ClientReceiveMessageEvents.GAME.register { msg, overlay ->
            if (!overlay || !S.sbXpChat) return@register
            try {
                val m = XP.find(msg.chatPlain) ?: return@register
                val now = System.currentTimeMillis()
                if (m.value == last && now - lastMs < 5000) return@register
                last = m.value; lastMs = now
                FishUtil.addChatMessage(Component.literal("§b+${m.groupValues[1]} SkyBlock XP §7(${m.groupValues[2]}) §b(${m.groupValues[3]}/100)"))
            } catch (t: Throwable) { FishDiag.fail("SkyblockXpChat.1", "skyblock xp chat failed", t) }
        }
    }
}
