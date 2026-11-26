package fishmod.features.mining

import fishmod.utils.rendering.FishDraw
import fishmod.utils.rendering.RenderHooks
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import fishmod.features.mining.MiningSettings as S

// Glacite Tunnels: walking paths (SkyHanni tunnel graph) to the nearest spot of each gem your commissions want,
// in the gem's colour
object GemstoneLocator {

    private var paths: List<PathData> = emptyList()
    private var spots: List<Pair<Gem, Vec3>> = emptyList()
    private var tick = 0

    fun wanted(): Set<Gem> = Commissions.current.filter { !it.done && it.name.contains("Gemstone") }
        .mapNotNull { c -> Gem.entries.firstOrNull { c.name.contains(it.display) } }.toSet()

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (tick++ % 5 != 0) return@register
            val p = mc.player
            if (!S.miningGemLines || (!S.miningGemPaths && !S.miningGemWaypoints) || p == null || !Mining.inTunnels()) { paths = emptyList(); spots = emptyList(); return@register }
            val want = wanted()
            paths = if (want.isEmpty() || !S.miningGemPaths) emptyList() else findPaths(want, p.position())
            spots = if (want.isEmpty() || !S.miningGemWaypoints) emptyList() else {
                TunnelGraph.ensureLoaded()
                val labels = want.associateBy { it.display + " Gemstone" }
                TunnelGraph.nodes.mapNotNull { n -> labels[n.name]?.let { it to n.pos } }
            }
        }
        RenderHooks.GIZMO.register { _ ->
            if (!S.miningGemLines || (paths.isEmpty() && spots.isEmpty()) || !Mining.inTunnels()) return@register
            val feet = net.minecraft.client.Minecraft.getInstance().player?.position() ?: return@register
            if (S.miningGemWaypoints) for ((gem, goal) in spots) {
                val d = goal.distanceTo(feet)
                if (d < 4.0) continue
                val spot = goal.add(0.0, -1.9, 0.0)
                FishDraw.gizmoBox(AABB(spot.add(-0.4, 0.0, -0.4), spot.add(0.4, 1.0, 0.4)), Mining.alpha(gem.rgb, 60), Mining.alpha(gem.rgb, 255), true)
                FishDraw.gizmoText(FishDraw.distLabel(gem.display + " Gemstone", d.toInt()), spot.add(0.0, 1.6, 0.0), 1.0f, Mining.alpha(gem.rgb, 255), true)
            }
            if (S.miningGemPaths) for (pd in paths) {
                if (pd.goal.distanceToSqr(feet) < 12.0 * 12.0) continue
                val argb = Mining.alpha(pd.gem.rgb, 100)
                val floor = pd.floor
                val dropped = floor.size > 1 && floor[1].distanceToSqr(feet) < floor[0].distanceToSqr(floor[1])
                val curve = pd.curve(feet.add(0.0, 0.1, 0.0), dropped)
                FishDraw.gizmoRibbon(curve, S.miningGemLineWidth * 0.05, argb) { it.distanceToSqr(feet) < 20.0 * 20.0 }
            }
        }
    }

    private class PathData(val gem: Gem, pts: List<Vec3>) {
        val goal: Vec3 = pts.last()
        val floor: List<Vec3> = pts.map { it.add(0.0, -1.9, 0.0) }
        private val dropFloor: List<Vec3> = floor.drop(1)
        private val tail: List<Vec3> = tailOf(floor)
        private val dropTail: List<Vec3> = tailOf(dropFloor)

        fun curve(start: Vec3, dropped: Boolean): List<Vec3> {
            val v = if (dropped) dropFloor else floor
            if (v.size < 2) return smooth(listOf(start) + v)
            val out = ArrayList<Vec3>(16 + (if (dropped) dropTail else tail).size)
            segment(start, start, v[0], v[1], 8, out)
            segment(start, v[0], v[1], v[minOf(2, v.size - 1)], 8, out)
            out.addAll(if (dropped) dropTail else tail)
            return out
        }

        private fun tailOf(v: List<Vec3>): List<Vec3> =
            if (v.size >= 3) smooth(v).drop(8) else if (v.isEmpty()) emptyList() else listOf(v.last())
    }

    private fun smooth(p: List<Vec3>, steps: Int = 8): List<Vec3> {
        if (p.size < 3) return p
        val out = ArrayList<Vec3>()
        for (i in 0 until p.size - 1) segment(p[maxOf(i - 1, 0)], p[i], p[i + 1], p[minOf(i + 2, p.size - 1)], steps, out)
        out += p.last()
        return out
    }

    private fun segment(p0: Vec3, p1: Vec3, p2: Vec3, p3: Vec3, steps: Int, out: MutableList<Vec3>) {
        for (s in 0 until steps) {
            val t = s.toDouble() / steps; val t2 = t * t; val t3 = t2 * t
            fun c(a: Double, b: Double, c: Double, d: Double) =
                0.5 * (2 * b + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t2 + (-a + 3 * b - 3 * c + d) * t3)
            out += Vec3(c(p0.x, p1.x, p2.x, p3.x), c(p0.y, p1.y, p2.y, p3.y), c(p0.z, p1.z, p2.z, p3.z))
        }
    }

    private fun findPaths(want: Set<Gem>, feet: Vec3): List<PathData> {
        if (want.isEmpty()) return emptyList()
        TunnelGraph.ensureLoaded()
        val start = TunnelGraph.closest(feet) ?: return emptyList()
        val (dist, prev) = TunnelGraph.search(start)
        val out = ArrayList<PathData>()
        for (g in want) {
            val label = g.display + " Gemstone"
            val goal = TunnelGraph.nodes.filter { it.name == label && it in dist }.minByOrNull { dist.getValue(it) } ?: continue
            out += PathData(g, TunnelGraph.path(prev, goal).map { it.pos })
        }
        return out
    }
}
