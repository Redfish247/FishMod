package fishmod.features.fishing

import fishmod.features.fishing.FishingWorldSettings as W
import fishmod.utils.debug.FishDiag
import fishmod.utils.rendering.FishDraw
import fishmod.utils.rendering.RenderHooks
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.Vec3

// Shows how long the current cast has been out, above the bobber.
object RodTimer {
    private var hookId = -1
    private var castAt = 0L
    @Volatile private var pos: Vec3? = null

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            val hook = if (W.rodTimer) mc.player?.fishing else null
            if (hook == null) { hookId = -1; pos = null; return@register }
            if (hook.id != hookId) { hookId = hook.id; castAt = System.currentTimeMillis() }
            pos = Vec3(hook.x, hook.y + 0.9, hook.z)
        }
        RenderHooks.NO_DEPTH_LINE.register { ctx, ps, _ ->
            val p = pos ?: return@register
            FishDiag.guard("RodTimer.1", "rod timer render failed") {
                val s = (System.currentTimeMillis() - castAt) / 1000.0
                FishDraw.renderText(ctx, ps, Component.literal("§b" + String.format("%.1f", s) + "s"), p, 1f)
            }
        }
    }
}
