package fishmod.features.rift

import fishmod.utils.Misc
import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import fishmod.features.rift.RiftSettings as S

// Mirrorverse parkours, Blood Effigy spots and Colosseum kill-zone warning; course data from SkyHanni-Repo
object RiftParkour {

    private class Course(val pts: List<IntArray>, val shortcuts: List<IntArray>) {
        var idx = -1
        private val minX = pts.minOf { it[0] } - 6.0; private val maxX = pts.maxOf { it[0] } + 7.0
        private val minY = pts.minOf { it[1] } - 6.0; private val maxY = pts.maxOf { it[1] } + 8.0
        private val minZ = pts.minOf { it[2] } - 6.0; private val maxZ = pts.maxOf { it[2] } + 7.0
        fun inRoom(x: Double, y: Double, z: Double) = x in minX..maxX && y in minY..maxY && z in minZ..maxZ
    }

    private fun c(vararg v: Int) = v.toList().chunked(3).map { it.toIntArray() }

    private val LAVA = Course(c(-83,51,-112, -86,51,-112, -86,51,-115, -88,51,-115, -88,51,-110, -86,51,-110, -86,51,-109, -84,51,-109, -84,51,-107, -90,51,-107, -90,51,-108, -93,51,-108, -93,51,-110, -90,51,-110, -90,51,-112, -92,51,-112, -92,51,-114, -90,51,-114, -90,51,-116, -94,51,-116, -94,51,-114, -96,51,-114, -96,51,-116, -98,51,-116, -98,51,-112, -96,51,-112, -96,51,-111, -95,51,-111, -95,51,-108, -98,51,-108, -98,51,-110, -100,51,-110, -100,51,-108, -102,51,-108, -102,51,-112, -100,51,-112, -100,51,-116, -102,51,-116, -102,51,-114, -104,51,-114, -104,51,-112, -105,51,-112),
        listOf(intArrayOf(0, 7), intArrayOf(11, 28), intArrayOf(29, 32), intArrayOf(34, 40)))
    private val UPSIDE = Course(c(-121,39,-108, -125,39,-108, -129,39,-108, -133,40,-108, -137,39,-104, -133,40,-100, -137,41,-96, -141,42,-100, -145,40,-104, -145,40,-108, -141,39,-112, -137,40,-116, -141,41,-120, -145,42,-116, -149,43,-112, -153,42,-108, -157,41,-104, -153,40,-100, -157,40,-96, -161,39,-92, -165,40,-96, -165,41,-100, -165,42,-104, -165,43,-108, -161,42,-112, -157,39,-116, -161,40,-120, -165,41,-124, -169,41,-120, -169,42,-116, -173,43,-112, -173,43,-108, -177,40,-104, -173,41,-100, -173,41,-96, -177,39,-92, -181,40,-96, -185,41,-100, -181,42,-104, -185,41,-108, -181,40,-112, -185,41,-116, -181,40,-120, -185,41,-124, -189,42,-120, -189,43,-116, -189,43,-112, -193,41,-108, -193,42,-104, -189,43,-100, -193,42,-96, -197,39,-92, -201,40,-96, -205,41,-100, -209,42,-104, -205,43,-108, -201,43,-108, -197,43,-112, -201,40,-116, -205,41,-116, -205,42,-120, -209,43,-120, -213,41,-116, -213,41,-112, -217,42,-108, -221,43,-104),
        listOf(intArrayOf(3, 10), intArrayOf(14, 25), intArrayOf(30, 40), intArrayOf(41, 44), intArrayOf(50, 52), intArrayOf(55, 63)))
    private val TUBE = Course(c(-302,0,-107, -304,2,-107, -306,4,-103, -308,6,-105, -304,8,-109, -300,10,-111, -304,12,-107, -308,14,-103, -306,16,-107, -302,18,-111, -300,20,-107, -304,22,-111, -306,24,-109, -302,26,-111, -300,28,-107, -304,30,-103, -306,32,-105, -302,34,-107, -300,36,-109, -302,38,-105, -304,40,-107, -306,42,-111, -302,44,-107, -300,46,-103, -304,48,-107, -308,50,-105, -304,52,-109, -306,54,-111, -306,55,-112, -309,55,-112, -309,55,-102, -304,55,-102, -304,55,-99), emptyList())
    private val COURSES = listOf(LAVA, UPSIDE, TUBE)
    private val EFFIGIES = c(150,73,95, 193,87,119, 235,104,147, 293,90,134, 262,93,94, 240,123,118)

    private fun box(p: IntArray) = AABB(p[0] - 1.0, p[1] - 1.0, p[2] - 1.0, p[0] + 1.0, p[1].toDouble(), p[2] + 1.0)

    fun init() {
        Events.ON_WORLD_CHANGE.register { COURSES.forEach { it.idx = -1 }; false }
        Events.ON_GAME_MESSAGE.register { text ->
            val m = Rift.strip(text.string)
            if (m.contains("THE LAVA OOFED YOU BACK TO THE START")) COURSES.forEach { it.idx = -1 }
            if (S.riftKillZone && Rift.inRift() && m.contains("Get back in the arena or you will DIE"))
                Misc.forceTitle(Component.literal("§c§lGet back in the arena!"), Component.empty(), 1000)
            false
        }
        RenderingEvents.GIZMO.register { _ -> render() }
    }

    private fun render() {
        if (!Rift.inRift()) return
        val me = Minecraft.getInstance().player ?: return
        if (S.riftParkour) for (co in COURSES) {
            // Only inside the course's own room (bounds + small margin), never through walls from outside
            if (!co.inRoom(me.x, me.y, me.z)) { co.idx = -1; continue }
            val near = co.pts.indices.minByOrNull { me.distanceToSqr(co.pts[it][0] + 0.0, co.pts[it][1] + 0.0, co.pts[it][2] + 0.0) } ?: continue
            val n = co.pts[near]
            val dx = me.x - n[0]; val dz = me.z - n[2]; val dy = me.y - n[1]
            if (dx * dx + dz * dz < 2.6 * 2.6 && dy > -1.0 && dy < 3.0 && near > co.idx) co.idx = near
            val from = co.idx.coerceAtLeast(0)
            val c = S.riftParkourColor
            for (i in from until minOf(co.pts.size, from + S.riftParkourAhead + 1)) {
                val a = if (i == from) 0x20 else (0x50 - (i - from) * 0x10).coerceAtLeast(0x18)
                RenderUtils.gizmoBox(box(co.pts[i]), (a shl 24) or (c and 0xFFFFFF), c, true)
            }
            co.shortcuts.filter { it[0] in from..(from + S.riftParkourAhead) }.forEach {
                RenderUtils.gizmoBox(box(co.pts[it[1]]), 0x4055FF55, 0xFF55FF55.toInt(), true)
            }
        }
        if (S.riftEffigies && Rift.inArea("Stillgore", "Oubliette", "Chateau", "Château")) {
            for ((i, e) in EFFIGIES.withIndex()) {
                val b = AABB(e[0].toDouble(), e[1].toDouble(), e[2].toDouble(), e[0] + 1.0, e[1] + 1.0, e[2] + 1.0)
                RenderUtils.gizmoBox(b, 0x40FF5555, 0xFFFF5555.toInt(), true)
                RenderUtils.gizmoText(Component.literal("§cEffigy ${i + 1}"), Vec3(e[0] + 0.5, e[1] + 1.6, e[2] + 0.5), 1.5f, -1, true)
            }
        }
    }
}
