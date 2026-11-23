package fishmod.features.events

import fishmod.features.extras.*

import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import fishmod.features.extras.ExtrasSettings as S

object JerryHighlight {
    // from SkyHanni (HighlightJerries / MobFilter): "[Lv1] <player>'s Green Jerry 7 Hits"
    private val JERRY = Regex("""(\w+)'s (Green|Blue|Purple|Golden) Jerry""")
    private val COLORS = mapOf("Green" to 0xFF55FF55.toInt(), "Blue" to 0xFF5555FF.toInt(), "Purple" to 0xFFAA00AA.toInt(), "Golden" to 0xFFFFAA00.toInt())
    @Volatile private var glow: Map<Int, Int> = emptyMap()
    private var tick = 0

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc -> onTick(mc) }
    }

    @JvmStatic
    fun glowColor(e: Entity): Int {
        val g = glow
        if (g.isEmpty() || !S.jerryHighlight) return EntityRenderState.NO_OUTLINE
        return g[e.id] ?: EntityRenderState.NO_OUTLINE
    }

    private fun onTick(mc: Minecraft) {
        if (++tick % 10 != 0) return
        val level = mc.level
        val player = mc.player
        if (!S.jerryHighlight || level == null || player == null) { if (glow.isNotEmpty()) glow = emptyMap(); return }
        try {
            val me = player.gameProfile.name
            val stands = ArrayList<Pair<ArmorStand, Int>>()
            val mobs = ArrayList<LivingEntity>()
            for (e in level.entitiesForRendering()) {
                if (e === player || e.distanceToSqr(player) > 48.0 * 48.0) continue
                if (e is ArmorStand) {
                    val m = JERRY.find(e.customName?.string ?: continue) ?: continue
                    if (m.groupValues[1].equals(me, true)) stands += e to COLORS.getValue(m.groupValues[2])
                } else if (e is LivingEntity && e !is Player && e.isAlive) mobs += e
            }
            val out = HashMap<Int, Int>()
            for ((s, color) in stands) {
                val best = mobs.filter { val dx = it.x - s.x; val dz = it.z - s.z; dx * dx + dz * dz < 2.25 && s.y - it.y in -1.0..4.0 }
                    .minByOrNull { it.distanceToSqr(s) } ?: continue
                out[best.id] = color
            }
            glow = out
        } catch (t: Throwable) {
            FishDiag.fail("JerryHighlight.1", "jerry scan failed", t)
        }
    }
}
