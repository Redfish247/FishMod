package fishmod.features.mining

import fishmod.utils.FishUtil
import fishmod.utils.rendering.FishDraw
import fishmod.utils.rendering.RenderHooks
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import fishmod.features.mining.MiningSettings as S

object OrderedWaypoints {
    var index = 0

    fun points(): List<BlockPos> = S.routePoints.split(';').mapNotNull { p ->
        val c = p.split(',').mapNotNull { it.trim().toIntOrNull() }
        if (c.size == 3) BlockPos(c[0], c[1], c[2]) else null
    }

    fun addHere() {
        val p = Minecraft.getInstance().player ?: return
        val b = p.blockPosition().below()
        S.routePoints = (points() + b).joinToString(";") { "${it.x},${it.y},${it.z}" }
        msg("Added point ${points().size}: ${b.x} ${b.y} ${b.z}")
    }

    fun removeLast() { S.routePoints = points().dropLast(1).joinToString(";") { "${it.x},${it.y},${it.z}" }; index = 0 }
    fun clear() { S.routePoints = ""; index = 0 }
    private fun msg(s: String) = Minecraft.getInstance().player?.sendSystemMessage(Component.literal("§b[Route] §f$s"))

    fun command(): com.mojang.brigadier.builder.LiteralArgumentBuilder<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> {
        fun run(f: () -> Unit) = com.mojang.brigadier.Command<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> { f(); 1 }
        fun c(n: String): com.mojang.brigadier.builder.LiteralArgumentBuilder<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> = net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal(n)
        return c("routes")
            .executes(run { msg("${points().size} points, at #${index + 1}. /fm routes add|remove|restart|clear|toggle") })
            .then(c("add").executes(run(::addHere)))
            .then(c("remove").executes(run(::removeLast)))
            .then(c("restart").executes(run { index = 0 }))
            .then(c("clear").executes(run(::clear)))
            .then(c("toggle").executes(run { S.route = !S.route; msg(if (S.route) "On" else "Off") }))
    }

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (!S.route) return@register
            val p = mc.player ?: return@register
            val pts = points()
            if (pts.isEmpty()) return@register
            if (index >= pts.size) index = 0
            if (p.position().distanceToSqr(Vec3.atCenterOf(pts[index])) <= S.routeRadius * S.routeRadius) {
                index = (index + 1) % pts.size
                FishUtil.sendSound2D(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, 1.5f)
            }
        }
        RenderHooks.NO_DEPTH_FILLED.register { _, ps, vc ->
            if (!S.route) return@register
            val pts = points()
            if (pts.isEmpty()) return@register
            val i = index.coerceIn(0, pts.size - 1)
            val cur = pts[i]
            val col = Mining.alpha(S.routeColor, 100)
            Mining.outline(ps, vc, AABB(cur), col, 3f)
            FishDraw.fillBox(ps, vc, AABB(cur), Mining.alpha(S.routeColor, 30))
            FishDraw.screenLine(ps, vc, Mining.lineStart(), Vec3.atCenterOf(cur), col, 2f)
            if (S.routeShowNext && pts.size > 1) Mining.outline(ps, vc, AABB(pts[(i + 1) % pts.size]), Mining.alpha(S.routeColor, 40), 1.5f)
        }
    }
}
