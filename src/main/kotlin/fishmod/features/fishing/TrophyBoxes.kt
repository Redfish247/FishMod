package fishmod.features.fishing

import fishmod.compat.speed
import fishmod.utils.SkyblockLocation
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.FishEvents
import fishmod.utils.rendering.FishDraw
import fishmod.utils.rendering.RenderHooks
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import fishmod.features.fishing.FishingWorldSettings as W

// Crimson Isle trophy fishing: Geyser area box and Sulphur Skitter sponge range box (logic from SkyHanni)
object TrophyBoxes {
    @Volatile private var geyser: AABB? = null
    @Volatile private var sponge: AABB? = null
    private var tick = 0

    fun init() {
        FishEvents.particle.register { p ->
            // from SkyHanni: geyser = CLOUD x15, speed 0.05, spread 0.1/0.6/0.1
            if (W.geyserBox && p.particle.type === ParticleTypes.CLOUD && p.count == 15 && p.speed == 0.05f &&
                p.xDist == 0.1f && p.yDist == 0.6f && p.zDist == 0.1f) {
                // from SkyHanni: the geyser surface sits at y 118
                geyser = AABB(p.x - 2, 117.9, p.z - 2, p.x + 2, 117.91, p.z + 2)
            }
            false
        }
        FishEvents.worldChange.register { geyser = null; sponge = null; false }
        ClientTickEvents.END_CLIENT_TICK.register { if (++tick % 20 == 0) FishDiag.guard("TrophyBoxes.1", "sponge scan threw") { scanSponge() } }
        RenderHooks.GIZMO.register { _ ->
            if (!SkyblockLocation.`in`(SkyblockLocation.CRIMSON_ISLE)) return@register
            FishDiag.guard("TrophyBoxes.2", "trophy box render threw") {
                val pl = Minecraft.getInstance().player ?: return@guard
                if (W.geyserBox) geyser?.let { if (pl.position().distanceTo(it.center) < 96) FishDraw.gizmoBox(it, W.geyserColor, W.geyserColor or 0xFF000000.toInt()) }
                if (W.skitterBox) sponge?.let { FishDraw.gizmoBox(it, if (W.skitterFilled) W.skitterColor else 0, W.skitterColor or 0xFF000000.toInt()) }
            }
        }
    }

    // from SkyHanni: Sulphur Skitter needs the bobber within 4 blocks of a sponge
    private fun scanSponge() {
        val mc = Minecraft.getInstance()
        val level = mc.level; val pl = mc.player
        if (!W.skitterBox || level == null || pl == null || !SkyblockLocation.`in`(SkyblockLocation.CRIMSON_ISLE)) { sponge = null; return }
        val c = pl.blockPosition()
        var best: BlockPos? = null; var bd = Double.MAX_VALUE
        for (p in BlockPos.betweenClosed(c.offset(-15, -15, -15), c.offset(15, 15, 15))) {
            if (!level.getBlockState(p).`is`(Blocks.SPONGE)) continue
            val d = p.distSqr(c)
            if (d < bd) { bd = d; best = p.immutable() }
        }
        sponge = best?.let { AABB(it.x - 4.0, it.y - 4.0, it.z - 4.0, it.x + 5.0, it.y + 5.0, it.z + 5.0) }
    }
}
