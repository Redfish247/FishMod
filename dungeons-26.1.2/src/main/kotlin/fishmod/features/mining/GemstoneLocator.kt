package fishmod.features.mining

import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import fishmod.features.mining.MiningSettings as S

// Glacite Tunnels: walking paths (SkyHanni tunnel graph) to the nearest spot of each gem your commissions want,
// plus optional boxes on nearby gem blocks, all in the gem's colour
object GemstoneLocator {

    private var targets: List<Pair<Gem, BlockPos>> = emptyList()
    private var paths: List<Pair<Gem, List<Vec3>>> = emptyList()
    private var tick = 0

    fun wanted(): Set<Gem> = Commissions.current.filter { !it.done && it.name.contains("Gemstone") }
        .mapNotNull { c -> Gem.entries.firstOrNull { c.name.contains(it.display) } }.toSet()

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (tick++ % 20 != 0) return@register
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
            paths = if (S.miningGemPaths) findPaths(want, p.position()) else emptyList()
        }
        RenderingEvents.NO_DEPTH_FILLED.register { _, ps, vc ->
            if (!S.miningGemLines || !S.miningGemBoxes || targets.isEmpty() || !Mining.inTunnels()) return@register
            if (S.miningGemBoxes) for ((g, pos) in targets) RenderUtils.fillBox(ps, vc, AABB(pos), Mining.alpha(g.rgb, S.miningGemOpacity))
        }
        RenderingEvents.GIZMO.register { _ ->
            if (!S.miningGemLines || !S.miningGemPaths || paths.isEmpty() || !Mining.inTunnels()) return@register
            val feet = net.minecraft.client.Minecraft.getInstance().player?.position() ?: return@register
            for ((g, pts) in paths) {
                val argb = Mining.alpha(g.rgb, 100)
                var last = feet.add(0.0, 0.1, 0.0)
                for (pt in pts) {
                    // Graph points sit ~2 blocks up; drop them to the floor
                    val next = pt.add(0.0, -1.9, 0.0)
                    RenderUtils.gizmoLine(last, next, argb, S.miningGemLineWidth * 2f, true)
                    last = next
                }
            }
        }
    }

    // One path per wanted gem to its nearest graph spot by walking distance; dropped once you're there
    private fun findPaths(want: Set<Gem>, feet: Vec3): List<Pair<Gem, List<Vec3>>> {
        TunnelGraph.ensureLoaded()
        val start = TunnelGraph.closest(feet) ?: return emptyList()
        val (dist, prev) = TunnelGraph.search(start)
        val out = ArrayList<Pair<Gem, List<Vec3>>>()
        for (g in want) {
            val label = g.display + " Gemstone"
            val goal = TunnelGraph.nodes.filter { it.name == label && it in dist }.minByOrNull { dist.getValue(it) } ?: continue
            if (goal.pos.distanceToSqr(feet) < 4.0 * 4.0) continue
            out += g to TunnelGraph.path(prev, goal).map { it.pos }
        }
        return out
    }
}
