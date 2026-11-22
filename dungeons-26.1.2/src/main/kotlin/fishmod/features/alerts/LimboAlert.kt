package fishmod.features.alerts

import fishmod.features.extras.*

import fishmod.utils.events.chatPlain
import fishmod.utils.FishUtil
import fishmod.utils.events.FishEvents
import net.minecraft.network.chat.Component
import fishmod.features.extras.ExtrasSettings as S

object LimboAlert {
    private var lastMs = 0L

    fun init() {
        FishEvents.chat.register { text ->
            if (!S.limboAlert && !S.restartAlert) return@register false
            if (S.limboAlert) {
                val s = text.chatPlain.trim()
                if (s.startsWith("You were spawned in Limbo") || s.startsWith("You are AFK. Move around to return from AFK")
                    || s == "You are currently in Limbo") fire()
            }
            // Hypixel's line, seen in Eli's logs; party echoes start with "Party >" so they never match
            if (S.restartAlert && text.chatPlain.trim().startsWith("[Important] This server will restart soon")) restart()
            false
        }
    }

    private var restartMs = 0L

    private fun restart() {
        val now = System.currentTimeMillis()
        if (now - restartMs < 10_000) return
        restartMs = now
        FishUtil.forceTitle(Component.literal(S.restartTitle.replace('&', '§')), Component.literal("§7Warp out before you get moved"), 3000)
        ExtrasSounds.play(S.restartSound, S.restartVolume, "fm_restart")
    }

    private fun fire() {
        val now = System.currentTimeMillis()
        if (now - lastMs < 3000) return
        lastMs = now
        FishUtil.forceTitle(Component.literal(S.limboTitle.replace('&', '§')), Component.empty(), 2500)
        ExtrasSounds.play(S.limboSound, S.limboVolume, "fm_limbo")
    }
}
