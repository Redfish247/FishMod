package fishmod.features.mining

import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import fishmod.features.mining.MiningSettings as S

// Glacite Tunnels: lines to gemstone blocks wanted by active gemstone commissions, in the gem's colour
object GemstoneLocator {

    private var targets: List<Pair<Gem, BlockPos>> = emptyList()
    private var tick = 0

    fun wanted(): Set<Gem> = Commissions.current.filter { !it.done && it.name.contains("Gemstone") }
        .mapNotNull { c -> Gem.entries.firstOrNull { c.name.contains(it.display) } }.toSet()

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (tick++ % 20 != 0) return@register
            val p = mc.player; val level = mc.level
            if (!S.miningGemLines || p == null || level == null || !Mining.inTunnels()) { targets = emptyList(); return@register }
            val want = wanted()
            if (want.isEmpty()) { targets = emptyList(); return@register }
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
        }
        RenderingEvents.NO_DEPTH_FILLED.register { _, ps, vc ->
            if (!S.miningGemLines || targets.isEmpty() || !Mining.inTunnels()) return@register
            val start = Mining.lineStart()
            for ((g, pos) in targets) {
                if (S.miningGemBoxes) RenderUtils.fillBox(ps, vc, AABB(pos), Mining.alpha(g.rgb, S.miningGemOpacity))
                RenderUtils.screenLine(ps, vc, start, Vec3.atCenterOf(pos), Mining.alpha(g.rgb, 100), S.miningGemLineWidth.toFloat())
            }
        }
    }
}
