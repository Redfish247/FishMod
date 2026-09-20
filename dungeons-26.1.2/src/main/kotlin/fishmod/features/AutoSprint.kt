package fishmod.features

import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents

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
            if (p.zza <= 0f) return@register
            if (p.isUsingItem || p.isCrouching || p.horizontalCollision) return@register
            if (p.foodData.foodLevel <= 6 && !p.abilities.mayfly) return@register
            p.isSprinting = true
        }
    }
}
