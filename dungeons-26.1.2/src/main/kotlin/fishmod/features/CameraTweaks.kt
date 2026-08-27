package fishmod.features

import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.world.effect.MobEffects

/**
 * Small camera/screen QoL toggles (subset of NoammAddons' Camera).
 *
 * - Full Bright: driven by [fishmod.mixin.LightmapMixin] (mutates the lightmap render state — the
 *   only way to true fullbright since vanilla clamps `options.gamma()` to 1.0).
 * - Custom FOV: forces `options.fov`, captured once and only re-written on drift.
 * - Disable Blindness / Nausea: strips the effect client-side each tick.
 */
object CameraTweaks {

    private var appliedFov = false
    private var origFov = 70

    @JvmField
    var flashFullBright = false
    private var prevFullBright = false

    /** Read by [fishmod.mixin.LightmapMixin]. */
    @JvmStatic
    fun fullBrightActive(): Boolean = FishSettings.cameraTweaksEnabled && FishSettings.cameraFullBright

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            val opts = mc.options ?: return@register
            val on = FishSettings.cameraTweaksEnabled

            // Full-bright state change -> tell the lightmap mixin to force a recompute.
            val fb = fullBrightActive()
            if (fb != prevFullBright) { flashFullBright = true; prevFullBright = fb }

            // ── FOV ──
            val wantFov = on && FishSettings.cameraCustomFov
            if (wantFov && !appliedFov) { origFov = opts.fov().get(); appliedFov = true }
            if (appliedFov) {
                val target = if (wantFov) FishSettings.cameraFov else origFov
                if (opts.fov().get() != target) opts.fov().set(target)
                if (!wantFov) appliedFov = false
            }

            if (!on) return@register
            val p = mc.player ?: return@register
            if (FishSettings.cameraNoBlindness) p.removeEffect(MobEffects.BLINDNESS)
            if (FishSettings.cameraNoNausea) p.removeEffect(MobEffects.NAUSEA)
        }
    }
}
