package fishmod.features

import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.world.effect.MobEffects

/**
 * Small camera/screen QoL toggles (subset of NoammAddons' Camera). Tick-driven overrides only —
 * the render-overlay-hiding options (fire/portal/water) that need mixins are a follow-up.
 *
 * The original value is captured exactly once when an override first takes effect (via the
 * `applied*` latch) and only re-written when it has actually drifted, so nothing fights the
 * option every tick (that was causing the full-bright flicker).
 */
object CameraTweaks {

    private var appliedFov = false
    private var origFov = 70
    private var appliedGamma = false
    private var origGamma = 0.5

    private const val FULLBRIGHT = 1.0 // vanilla clamps gamma here; true fullbright needs a mixin

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            val opts = mc.options ?: return@register
            val on = FishSettings.cameraTweaksEnabled

            // ── FOV ──
            val wantFov = on && FishSettings.cameraCustomFov
            if (wantFov && !appliedFov) { origFov = opts.fov().get(); appliedFov = true }
            if (appliedFov) {
                val target = if (wantFov) FishSettings.cameraFov else origFov
                if (opts.fov().get() != target) opts.fov().set(target)
                if (!wantFov) appliedFov = false
            }

            // ── Full Bright ──
            val wantGamma = on && FishSettings.cameraFullBright
            if (wantGamma && !appliedGamma) { origGamma = opts.gamma().get(); appliedGamma = true }
            if (appliedGamma) {
                val target = if (wantGamma) FULLBRIGHT else origGamma
                if (opts.gamma().get() != target) opts.gamma().set(target)
                if (!wantGamma) appliedGamma = false
            }

            if (!on) return@register
            val p = mc.player ?: return@register
            if (FishSettings.cameraNoBlindness) p.removeEffect(MobEffects.BLINDNESS)
            if (FishSettings.cameraNoNausea) p.removeEffect(MobEffects.NAUSEA)
        }
    }
}
