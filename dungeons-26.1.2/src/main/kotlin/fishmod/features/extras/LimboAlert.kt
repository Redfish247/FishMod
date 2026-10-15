package fishmod.features.extras

import fishmod.utils.Misc
import fishmod.utils.events.Events
import net.minecraft.network.chat.Component
import fishmod.features.extras.ExtrasSettings as S

// Title + sound when Hypixel drops you into Limbo
object LimboAlert {
    private var lastMs = 0L

    fun init() {
        Events.ON_GAME_MESSAGE.register { text ->
            if (S.limboAlert) {
                val s = text.string.replace(fishmod.utils.Constants.STRIP_COLOR_REGEX, "").trim()
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
        Misc.forceTitle(Component.literal(S.limboTitle.replace('&', '§')), Component.empty(), 2500)
        ExtrasSounds.play(S.limboSound, S.limboVolume, "fm_limbo")
    }
}
