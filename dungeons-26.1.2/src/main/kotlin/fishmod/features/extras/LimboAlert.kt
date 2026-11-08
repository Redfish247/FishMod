package fishmod.features.extras

import fishmod.utils.events.chatPlain
import fishmod.utils.FishUtil
import fishmod.utils.events.FishEvents
import net.minecraft.network.chat.Component
import fishmod.features.extras.ExtrasSettings as S

// Title + sound when Hypixel drops you into Limbo
object LimboAlert {
    private var lastMs = 0L

    fun init() {
        FishEvents.chat.register { text ->
            if (S.limboAlert) {
                val s = text.chatPlain.trim()
                if (s.startsWith("You were spawned in Limbo") || s.startsWith("You are AFK. Move around to return from AFK")
                    || s == "You are currently in Limbo") fire()
            }
            false
        }
    }

    private fun fire() {
        val now = System.currentTimeMillis()
        if (now - lastMs < 3000) return
        lastMs = now
        FishUtil.forceTitle(Component.literal(S.limboTitle.replace('&', '§')), Component.empty(), 2500)
        ExtrasSounds.play(S.limboSound, S.limboVolume, "fm_limbo")
    }
}
