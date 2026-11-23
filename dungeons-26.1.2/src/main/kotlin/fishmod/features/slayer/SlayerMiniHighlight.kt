package fishmod.features.slayer

import fishmod.features.slayer.EndAlertSettings as S
import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player

// Outlines the mob under a slayer miniboss nametag (names from SlayerType.miniBosses)
object SlayerMiniHighlight {

    @Volatile private var glow: Map<Int, Int> = emptyMap()
    private var tick = 0
    // mob id -> scans left; keeps the outline through brief nametag/mob desyncs
    private val hold = HashMap<Int, Int>()
    private const val HOLD_SCANS = 8

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc -> onTick(mc) }
    }

    @JvmStatic
    fun glowColor(e: Entity): Int {
        val g = glow
        if (g.isEmpty() || !S.slayerMiniHighlight) return EntityRenderState.NO_OUTLINE
        return g[e.id] ?: EntityRenderState.NO_OUTLINE
    }

    private fun onTick(mc: Minecraft) {
        if (++tick % 5 != 0) return
        val level = mc.level
        val player = mc.player
        val type = SlayerManager.type
        if (!S.slayerMiniHighlight || level == null || player == null || type == null) {
            if (glow.isNotEmpty()) glow = emptyMap()
            hold.clear()
            return
        }
        try {
            val stands = ArrayList<ArmorStand>()
            val mobs = ArrayList<LivingEntity>()
            for (e in level.entitiesForRendering()) {
                if (e === player || e.distanceToSqr(player) > 40.0 * 40.0) continue
                if (e is ArmorStand) {
                    val raw = e.customName?.string ?: continue
                    if (type.miniBosses.any { raw.contains(it) }) stands.add(e)
                } else if (e is LivingEntity && e !is Player && e.isAlive) mobs.add(e)
            }
            val color = S.slayerMiniHighlightColor or 0xFF000000.toInt()
            val matched = HashSet<Int>()
            for (s in stands) {
                var best: LivingEntity? = null
                var bd = 4.0
                for (m in mobs) {
                    val dx = m.x - s.x; val dz = m.z - s.z; val dy = s.y - m.y
                    if (dx * dx + dz * dz > 2.25 || dy < -1.0 || dy > 4.0) continue
                    val d = dx * dx + dz * dz + dy * dy
                    if (d < bd) { bd = d; best = m }
                }
                if (best != null) matched.add(best.id)
            }
            val alive = mobs.mapTo(HashSet()) { it.id }
            hold.entries.removeIf { (id, left) -> id !in alive || left <= 1 }
            hold.replaceAll { _, left -> left - 1 }
            for (id in matched) hold[id] = HOLD_SCANS
            glow = hold.keys.associateWith { color }
        } catch (t: Throwable) {
            FishDiag.fail("SlayerMiniHighlight.1", "miniboss highlight scan failed", t)
        }
    }
}
