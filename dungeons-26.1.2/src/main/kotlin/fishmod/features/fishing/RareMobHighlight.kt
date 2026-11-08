package fishmod.features.fishing

import fishmod.features.fishing.FishingWorldSettings as W
import fishmod.utils.TextStrip
import fishmod.utils.debug.FishDiag
import fishmod.utils.rendering.FishDraw
import fishmod.utils.rendering.RenderHooks
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3

// Rare sea creature glow, HP and spawn-immunity text. Pattern: diana/RareMobs.kt (5-tick scan).
// Nametag shape `Lord Jawbus 1M/2M❤` and 5 s immunity are UNVERIFIED in game (from Feesh).
object RareMobHighlight {
    private class Label(val pos: Vec3, val text: Component)

    private val RARE = SeaCreatures.ALL.filter { it.rare }
    private val HP = Regex("([0-9.]+[kKmMbB]?)/([0-9.]+[kKmMbB]?)❤")
    private const val RANGE_SQ = 30.0 * 30.0

    // Read by the render mixin; swapped whole every scan
    @Volatile private var glow: Map<Int, Int> = emptyMap()
    @Volatile private var labels: List<Label> = emptyList()
    private var tick = 0

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { onTick() }
        RenderHooks.NO_DEPTH_LINE.register { ctx, ps, _ ->
            val ls = labels
            if (ls.isEmpty()) return@register
            FishDiag.guard("RareMobHighlight.1", "rare creature text render failed") {
                for (l in ls) FishDraw.renderText(ctx, ps, l.text, l.pos, 1.2f)
            }
        }
    }

    @JvmStatic
    fun glowColor(e: Entity): Int {
        val g = glow
        if (g.isEmpty() || !W.rareHighlight) return EntityRenderState.NO_OUTLINE
        return g[e.id] ?: EntityRenderState.NO_OUTLINE
    }

    private fun clear() {
        if (glow.isNotEmpty()) glow = emptyMap()
        if (labels.isNotEmpty()) labels = emptyList()
    }

    private fun onTick() {
        if (!FishingWorld.anyRare) { clear(); return }
        if (++tick % 5 != 0) return
        val mc = Minecraft.getInstance()
        val level = mc.level
        val player = mc.player
        if (level == null || player == null || !Fishing.active()) { clear(); return }
        try {
            val stands = ArrayList<ArmorStand>()
            val mobs = ArrayList<LivingEntity>()
            val newGlow = HashMap<Int, Int>()
            val newLabels = ArrayList<Label>()
            val color = W.rareHighlightColor or 0xFF000000.toInt()

            for (e in level.entitiesForRendering()) {
                if (e === player) continue
                if (e is ArmorStand) {
                    val c = e.customName ?: continue
                    if (c.string.indexOf('❤') >= 0) stands.add(e)
                } else if (e is LivingEntity && e !is Player && e.isAlive) mobs.add(e)
            }
            for (s in stands) {
                val plain = TextStrip.plain(s.customName!!.string)
                val sc = RARE.firstOrNull { plain.contains(it.name) } ?: continue
                if (sc.name != "Reindrake" && s.distanceToSqr(player) > RANGE_SQ) continue
                val m = HP.find(plain)
                if (m != null && parse(m.groupValues[1]) <= 0.0) continue

                if (W.rareHighlight) {
                    // the real mob stands just below its nametag stand
                    var best: LivingEntity? = null
                    var bd = 4.0
                    for (mob in mobs) {
                        val dx = mob.x - s.x; val dz = mob.z - s.z; val dy = s.y - mob.y
                        if (dx * dx + dz * dz > 2.25 || dy < -1.0 || dy > 4.0) continue
                        val d = dx * dx + dz * dz + dy * dy
                        if (d < bd) { bd = d; best = mob }
                    }
                    if (best != null) newGlow[best.id] = color
                }
                val sb = StringBuilder()
                if (W.rareHp && m != null) sb.append("§c").append(m.groupValues[1]).append("§7/§a").append(m.groupValues[2]).append("§c❤")
                if (W.rareImmunity && sc.immunity) {
                    val left = 100 - s.tickCount
                    if (left > 0) { if (sb.isNotEmpty()) sb.append(" "); sb.append("§eImmune ").append(fishmod.utils.Fmt.f1(left / 20.0)).append("s") }
                }
                if (sb.isNotEmpty()) newLabels.add(Label(Vec3(s.x, s.y + 0.5, s.z), Component.literal(sb.toString())))
            }
            glow = newGlow
            labels = newLabels
        } catch (t: Throwable) {
            FishDiag.fail("RareMobHighlight.2", "rare creature scan failed", t)
        }
    }

    private fun parse(s: String): Double {
        val mult = when (s.last()) { 'k', 'K' -> 1e3; 'm', 'M' -> 1e6; 'b', 'B' -> 1e9; else -> 1.0 }
        val num = if (mult == 1.0) s else s.dropLast(1)
        return num.toDoubleOrNull()?.times(mult) ?: 1.0
    }
}
