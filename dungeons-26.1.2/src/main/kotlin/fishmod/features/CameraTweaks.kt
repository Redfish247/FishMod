package fishmod.features

import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.world.effect.MobEffects

/**
 * Small screen QoL toggles. Shown in-game as "Visual Effects".
 *
 * - Full Bright: driven by [fishmod.mixin.LightmapMixin] (mutates the lightmap render state — the
 *   only way to true fullbright since vanilla clamps `options.gamma()` to 1.0).
 * - Disable Blindness / Nausea: strips the effect client-side each tick.
 */
object CameraTweaks {

    @JvmField
    var flashFullBright = false
    private var prevFullBright = false

    /** Read by [fishmod.mixin.LightmapMixin]. */
    @JvmStatic
    fun fullBrightActive(): Boolean = FishSettings.cameraTweaksEnabled && FishSettings.cameraFullBright

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            val on = FishSettings.cameraTweaksEnabled

            // Full-bright state change -> tell the lightmap mixin to force a recompute.
            val fb = fullBrightActive()
            if (fb != prevFullBright) { flashFullBright = true; prevFullBright = fb }

            if (!on) return@register
            val p = mc.player ?: return@register
            if (FishSettings.cameraNoBlindness) p.removeEffect(MobEffects.BLINDNESS)
            if (FishSettings.cameraNoNausea) p.removeEffect(MobEffects.NAUSEA)
        }
    }
}
