package fishmod.features.extras
import fishmod.utils.events.chatPlain
import fishmod.compat.cam

import fishmod.utils.events.FishEvents
import fishmod.utils.rendering.FishDraw
import fishmod.utils.rendering.RenderHooks
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.concurrent.CopyOnWriteArrayList
import fishmod.features.extras.ExtrasSettings as S

// Waypoints from Patcher-style "x: 1, y: 2, z: 3" coords in chat
object PatcherWaypoints {
    private class Wp(val label: String, val pos: Vec3, val expires: Long)

    private val COORDS = Regex("""x: ?(-?\d+)(?:\.\d+)?, ?y: ?(-?\d+)(?:\.\d+)?, ?z: ?(-?\d+)(?:\.\d+)?""", RegexOption.IGNORE_CASE)
    private val SENDER = Regex("""(\w{1,16})(?: [♲Ⓑ⚒ቾ])?: """)
    private val list = CopyOnWriteArrayList<Wp>()

    fun init() {
        FishEvents.chat.register { text ->
            if (S.patcherWaypoints) onChat(text.chatPlain)
            false
        }
        FishEvents.worldChange.register { list.clear(); false }
        ClientTickEvents.END_CLIENT_TICK.register { tick() }
        RenderHooks.NO_DEPTH_FILLED.register { ctx, ps, vc -> if (S.patcherWaypoints && list.isNotEmpty()) render(ctx, ps, vc) }
    }

    private fun onChat(s: String) {
        val m = COORDS.find(s) ?: return
        val x = m.groupValues[1].toIntOrNull() ?: return
        val y = m.groupValues[2].toIntOrNull() ?: return
        val z = m.groupValues[3].toIntOrNull() ?: return
        val before = s.substring(0, m.range.first)
        val label = SENDER.findAll(before).lastOrNull()?.groupValues?.get(1) ?: "Waypoint"
        val pos = Vec3(x + 0.5, y.toDouble(), z + 0.5)
        list.removeIf { it.label == label || it.pos == pos }
        if (list.size >= 32) list.removeAt(0)
        list += Wp(label, pos, System.currentTimeMillis() + S.patcherWaypointSeconds.coerceIn(5, 3600) * 1000L)
    }

    fun clear() = list.clear()

    private fun tick() {
        if (list.isEmpty()) return
        val now = System.currentTimeMillis()
        val p = Minecraft.getInstance().player
        list.removeIf { now > it.expires || (S.patcherWaypointRemoveNear && p != null && p.position().distanceTo(it.pos) < 3.0) }
    }

    private fun render(ctx: net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext, ps: com.mojang.blaze3d.vertex.PoseStack, vc: com.mojang.blaze3d.vertex.VertexConsumer) {
        val eye = Minecraft.getInstance().cam.position()
        val argb = S.patcherWaypointColor
        val rgb = argb and 0xFFFFFF
        for (w in list) {
            val center = w.pos.add(0.0, 0.5, 0.0)
            val d = center.distanceTo(eye)
            val (c, k) = FishDraw.pullIn(center, eye)
            FishDraw.fillBox(ps, vc, AABB.ofSize(c, k, k, k), (0x66 shl 24) or rgb)
            if (S.patcherWaypointBeam) FishDraw.fillBox(ps, vc, AABB.ofSize(c.add(0.0, k * 100.5, 0.0), k * 0.4, k * 200, k * 0.4), (0x40 shl 24) or rgb)
            val (tp, pk) = FishDraw.pullIn(Vec3(w.pos.x, w.pos.y + 1.5 + d / 25.0, w.pos.z), eye)
            val px = (S.patcherWaypointTextScale * maxOf(0.035, d * 0.0035) * pk).toFloat()
            FishDraw.renderSeeThroughText(ctx, ps, Component.literal("${w.label} §7(${d.toInt()}m)"), tp, px, argb or (0xFF shl 24), true)
        }
    }
}
