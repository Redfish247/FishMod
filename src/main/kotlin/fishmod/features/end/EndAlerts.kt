package fishmod.features.end

import fishmod.features.slayer.EndAlertSettings as S
import fishmod.utils.FishUtil
import fishmod.utils.TextStrip
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.FishEvents
import fishmod.utils.events.chatPlain
import net.minecraft.network.chat.Component

object EndAlerts {

    private const val SUPERIOR = "☬ The Superior Dragon has spawned!"
    private const val PROTECTOR = "BEWARE - An Endstone Protector has risen!"
    private const val PROTECTOR_RISING = "The ground begins to shake as an End Stone Protector rises from below!"

    @JvmStatic
    fun init() {
        FishEvents.chat.register { text ->
            if (S.superiorDragonAlert || S.endstoneProtectorAlert) {
                val s = TextStrip.plain(text.chatPlain).trim()
                if (S.superiorDragonAlert && s == SUPERIOR) title("§6§lSUPERIOR DRAGON!", "§7Spawned")
                else if (S.endstoneProtectorAlert && s == PROTECTOR_RISING) title("§5§lENDSTONE PROTECTOR!", "§7Rising now")
                else if (S.endstoneProtectorAlert && s == PROTECTOR) title("§5§lENDSTONE PROTECTOR!", "§7Has risen")
            }
            false
        }
    }

    private fun title(main: String, sub: String) {
        try {
            FishUtil.forceTitle(Component.literal(main), Component.literal(sub), S.endAlertDurationMs.coerceIn(250, 10_000))
        } catch (e: Exception) {
            FishDiag.fail("EndAlerts.1", "failed to show end title '$main'", e)
        }
    }
}
