package fishmod.features.events

import fishmod.features.extras.*

import fishmod.compat.cam
import fishmod.utils.SkyblockLocation
import fishmod.utils.debug.FishDiag
import fishmod.utils.rendering.FishDraw
import fishmod.utils.rendering.RenderHooks
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import fishmod.features.extras.ExtrasSettings as S

object HoppityEggWaypoints {
    private val EGGS = mapOf(
        "a49333d85b8a315d0336eb2df37d8a714ca24c51b8c6074f1b5b927deb516c24" to "§6Breakfast Egg",
        "7ae6d2d31d8167bcaf95293b68a4acd872d66e751db5a34f2cbc6766a0356d0a" to "§9Lunch Egg",
        "e5e36165819fd2850f98552edcd763ff986313119283c126ace0c4cc495e76a8" to "§aDinner Egg",
    )
    @Volatile private var found: List<Pair<Vec3, String>> = emptyList()
    private var tick = 0
    // eggs the player has actually had on screen with line of sight
    private val seen = HashSet<java.util.UUID>()
    private var seenLevel: Level? = null

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (++tick % 10 != 0) return@register
            val level = mc.level
            if (!S.hoppityEggWaypoints || level == null || !SkyblockLocation.inSkyblock()) { found = emptyList(); return@register }
            if (seenLevel !== level) { seen.clear(); seenLevel = level }
            val p = mc.player ?: return@register
            try {
                val out = ArrayList<Pair<Vec3, String>>()
                val eye = p.eyePosition
                val look = p.getViewVector(1f)
                for (e in level.entitiesForRendering()) {
                    if (e !is ArmorStand || !e.isInvisible || e.hasCustomName()) continue
                    val label = label(e) ?: continue
                    val pos = e.position().add(0.0, 1.5, 0.0)
                    if (e.uuid !in seen && !visible(level, p, eye, look, pos)) continue
                    seen += e.uuid
                    out += pos to label
                }
                found = out
            } catch (t: Throwable) {
                FishDiag.fail("HoppityEggWaypoints.1", "egg scan failed", t)
            }
        }
        RenderHooks.NO_DEPTH_FILLED.register { ctx, ps, vc -> if (S.hoppityEggWaypoints && found.isNotEmpty()) render(ctx, ps, vc) }
    }

    // on screen (inside a ~60 degree cone) and no block between the eye and the egg
    private fun visible(level: Level, p: net.minecraft.world.entity.player.Player, eye: Vec3, look: Vec3, pos: Vec3): Boolean {
        val to = pos.subtract(eye)
        val dist = to.length()
        if (dist > 64.0 || (dist > 1.5 && to.normalize().dot(look) < 0.5)) return false
        val hit = level.clip(ClipContext(eye, pos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p))
        return hit.type != HitResult.Type.BLOCK || hit.location.distanceToSqr(eye) >= pos.distanceToSqr(eye) - 1.0
    }

    private fun label(e: ArmorStand): String? {
        val tex = e.getItemBySlot(EquipmentSlot.HEAD).get(DataComponents.PROFILE)?.partialProfile()?.properties()?.get("textures")?.firstOrNull()?.value() ?: return null
        val json = runCatching { String(java.util.Base64.getDecoder().decode(tex)) }.getOrNull() ?: return null
        return EGGS.entries.firstOrNull { json.contains(it.key) }?.value
    }

    private fun render(ctx: net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext, ps: com.mojang.blaze3d.vertex.PoseStack, vc: com.mojang.blaze3d.vertex.VertexConsumer) {
        val eye = Minecraft.getInstance().cam.position()
        val argb = S.hoppityEggColor
        val look = FishDraw.lookDir()
        for ((pos, label) in found) {
            if (FishDraw.behindCamera(pos.x, pos.y, pos.z, eye, look, 2.0)) continue
            val d = pos.distanceTo(eye)
            val (c, k) = FishDraw.pullIn(pos, eye)
            FishDraw.fillBox(ps, vc, AABB.ofSize(c, k * 0.6, k * 0.6, k * 0.6), (0x66 shl 24) or (argb and 0xFFFFFF))
            val (tp, pk) = FishDraw.pullIn(Vec3(pos.x, pos.y + 1.0 + d / 25.0, pos.z), eye)
            FishDraw.renderSeeThroughText(ctx, ps, FishDraw.distLabel(label, d.toInt()), tp, (maxOf(0.035, d * 0.0035) * pk).toFloat(), argb or (0xFF shl 24), true)
        }
    }
}
