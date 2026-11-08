package fishmod.features.fishing

import fishmod.features.fishing.FishingWorldSettings as W
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.FishingHook

// Called from FishEntityRenderMixin.shouldRender (only when FishingWorld.anyOn). Own hook is never hidden.
object HideHooks {
    @JvmStatic
    fun shouldHide(e: Entity): Boolean {
        val mc = Minecraft.getInstance()
        val me = mc.player ?: return false
        if (e === me) return false
        if (e.id == HookTimer.hiddenStandId) return true
        if (W.hideOtherHooks && e is FishingHook && e.playerOwner !== me) return true
        if (W.hidePlayersNearBobber && e is Player) {
            val hook = me.fishing ?: return false
            val r = W.hidePlayersNearBobberRadius
            return e.distanceToSqr(hook) <= r * r
        }
        return false
    }
}
