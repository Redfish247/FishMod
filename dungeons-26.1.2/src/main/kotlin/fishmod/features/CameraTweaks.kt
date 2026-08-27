package fishmod.features

import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.world.effect.MobEffects

/**
 * Small camera/screen QoL toggles (subset of NoammAddons' Camera). Tick-driven overrides only —
 * the render-overlay-hiding options (fire/portal/water) that need mixins are a follow-up.
 *
 * - Custom FOV: forces `options.fov`.
 * - Full Bright: forces gamma to max.
 * - Disable Blindness / Nausea: strips the effect client-side each tick.
 */
object CameraTweaks {

    private var savedFov = -1
    private var savedGamma = -1.0

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (!FishSettings.cameraTweaksEnabled) {
                if (savedFov >= 0) { mc.options?.fov()?.set(savedFov); savedFov = -1 }
                if (savedGamma >= 0) { mc.options?.gamma()?.set(savedGamma); savedGamma = -1.0 }
                return@register
            }
            val opts = mc.options ?: return@register

            if (FishSettings.cameraCustomFov) {
                if (savedFov < 0) savedFov = opts.fov().get()
                opts.fov().set(FishSettings.cameraFov)
            } else if (savedFov >= 0) {
                opts.fov().set(savedFov); savedFov = -1
            }

            if (FishSettings.cameraFullBright) {
                if (savedGamma < 0) savedGamma = opts.gamma().get()
                opts.gamma().set(1.0) // vanilla clamps gamma to 1.0; true fullbright needs a mixin
            } else if (savedGamma >= 0) {
                opts.gamma().set(savedGamma); savedGamma = -1.0
            }

            val p = mc.player ?: return@register
            if (FishSettings.cameraNoBlindness) p.removeEffect(MobEffects.BLINDNESS)
            if (FishSettings.cameraNoNausea) p.removeEffect(MobEffects.NAUSEA)
        }
    }
}
