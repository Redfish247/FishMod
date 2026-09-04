package fishmod.features

import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents

/**
 * Keeps the player sprinting while they hold forward. Only ever *sets* the sprint flag — it never
 * clears it — so a manual stop (release forward, open a GUI, run out of hunger) still works: the
 * next tick the guards below simply don't re-enable it.
 */
object AutoSprint {

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (!FishSettings.autoSprintEnabled) return@register
            val p = mc.player ?: return@register
            if (mc.screen != null) return@register
            if (!Location.inSkyblock()) return@register
            if (FishSettings.autoSprintDungeonOnly && !Location.inDungeon()) return@register
            if (p.isSprinting || p.isSpectator || p.isPassenger) return@register
            // zza is the per-tick forward-movement input (>0 only while holding forward).
            if (p.zza <= 0f) return@register
            if (p.isUsingItem || p.isCrouching || p.horizontalCollision) return@register
            // Vanilla blocks sprint start under ~6 hunger unless flight is available.
            if (p.foodData.foodLevel <= 6 && !p.abilities.mayfly) return@register
            p.isSprinting = true
        }
    }
}
