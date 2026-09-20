package fishmod.features

import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.world.effect.MobEffects

object CameraTweaks {

    @JvmField
    var flashFullBright = false
    private var prevFullBright = false

    @JvmStatic
    fun fullBrightActive(): Boolean = FishSettings.cameraTweaksEnabled && FishSettings.cameraFullBright

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            val on = FishSettings.cameraTweaksEnabled

            val fb = fullBrightActive()
            if (fb != prevFullBright) { flashFullBright = true; prevFullBright = fb }

            if (!on) return@register
            val p = mc.player ?: return@register
            if (FishSettings.cameraNoBlindness) p.removeEffect(MobEffects.BLINDNESS)
            if (FishSettings.cameraNoNausea) p.removeEffect(MobEffects.NAUSEA)
        }
    }
}
