package fishmod.features.mining

import fishmod.utils.rendering.FishDraw
import fishmod.utils.rendering.RenderHooks
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import fishmod.features.mining.MiningSettings as S

// Glacite Tunnels: walking paths (SkyHanni tunnel graph) to the nearest spot of each gem your commissions want,
// plus optional boxes on nearby gem blocks, all in the gem's colour
object GemstoneLocator {

    private var targets: List<Pair<Gem, BlockPos>> = emptyList()
    private var paths: List<PathData> = emptyList()
    private var tick = 0

    fun wanted(): Set<Gem> = Commissions.current.filter { !it.done && it.name.contains("Gemstone") }
        .mapNotNull { c -> Gem.entries.firstOrNull { c.name.contains(it.display) } }.toSet()

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (tick++ % 5 != 0) return@register
            val p = mc.player; val level = mc.level
            if (!S.miningGemLines || p == null || level == null || !Mining.inTunnels()) { targets = emptyList(); paths = emptyList(); return@register }
            val want = wanted()
            if (want.isEmpty()) { targets = emptyList(); paths = emptyList(); return@register }
            val r = S.miningGemRadius.coerceIn(4, 48)
            val c = p.blockPosition()
            val found = HashMap<Gem, ArrayList<BlockPos>>()
            val m = BlockPos.MutableBlockPos()
            for (dx in -r..r) for (dy in -r / 2..r / 2) for (dz in -r..r) {
                m.set(c.x + dx, c.y + dy, c.z + dz)
                val gem = Gem.of(level.getBlockState(m)) ?: continue
                if (gem in want) found.getOrPut(gem) { ArrayList() }.add(m.immutable())
            }
            val eye = p.eyePosition
            targets = found.flatMap { (g, l) ->
                l.sortedBy { Vec3.atCenterOf(it).distanceToSqr(eye) }.take(S.miningGemMax.coerceAtLeast(1)).map { g to it }
            }
            val here = found.filterValues { l -> l.any { Vec3.atCenterOf(it).distanceToSqr(eye) < 8.0 * 8.0 } }.keys
            paths = if (S.miningGemPaths) findPaths(want - here, p.position()) else emptyList()
        }
        RenderHooks.NO_DEPTH_FILLED.register { _, ps, vc ->
            if (!S.miningGemLines || !S.miningGemBoxes || targets.isEmpty() || !Mining.inTunnels()) return@register
            if (S.miningGemBoxes) for ((g, pos) in targets) FishDraw.fillBox(ps, vc, AABB(pos), Mining.alpha(g.rgb, S.miningGemOpacity))
        }
        RenderHooks.GIZMO.register { _ ->
            if (!S.miningGemLines || !S.miningGemPaths || paths.isEmpty() || !Mining.inTunnels()) return@register
            val feet = net.minecraft.client.Minecraft.getInstance().player?.position() ?: return@register
            for (pd in paths) {
                val argb = Mining.alpha(pd.gem.rgb, 100)
                val floor = pd.floor
                val dropped = floor.size > 1 && floor[1].distanceToSqr(feet) < floor[0].distanceToSqr(floor[1])
                val curve = pd.curve(feet.add(0.0, 0.1, 0.0), dropped)
                FishDraw.gizmoRibbon(curve, S.miningGemLineWidth * 0.05, argb) { it.distanceToSqr(feet) < 20.0 * 20.0 }
            }
        }
    }

    private class PathData(val gem: Gem, pts: List<Vec3>) {
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
            if (goal.pos.distanceToSqr(feet) < 12.0 * 12.0) continue
            out += PathData(g, TunnelGraph.path(prev, goal).map { it.pos })
        }
        return out
    }
}
