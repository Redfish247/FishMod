package fishmod.features.fishing

import fishmod.features.fishing.FishingWorldSettings as W
import fishmod.utils.TextStrip
import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player

// Hides non-rare sea creatures (mob + nametag stand) while fishing; names matched like RareMobHighlight.
object CommonSeaCreatureHider {
    private val BY_LEN = SeaCreatures.ALL.sortedByDescending { it.name.length }
    @Volatile private var hidden: Set<Int> = emptySet()
    private var tick = 0

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { onTick() }
    }

    @JvmStatic
    fun shouldHide(e: Entity): Boolean {
        val h = hidden
        return h.isNotEmpty() && W.hideCommonCreatures && e.id in h
    }

    private fun onTick() {
        if (!FishingWorld.anyOn || !W.hideCommonCreatures) { if (hidden.isNotEmpty()) hidden = emptySet(); return }
        if (++tick % 5 != 0) return
        val mc = Minecraft.getInstance()
        val level = mc.level
        val player = mc.player
        if (level == null || player == null || !Fishing.active()) { hidden = emptySet(); return }
        try {
            val stands = ArrayList<ArmorStand>()
            val mobs = ArrayList<LivingEntity>()
            for (e in level.entitiesForRendering()) {
                if (e === player) continue
                if (e is ArmorStand) {
                    val c = e.customName ?: continue
                    if (c.string.indexOf('❤') >= 0) stands.add(e)
                } else if (e is LivingEntity && e !is Player) mobs.add(e)
            }
            val out = HashSet<Int>()
            for (s in stands) {
                val plain = TextStrip.plain(s.customName!!.string)
                val sc = BY_LEN.firstOrNull { plain.contains(it.name) } ?: continue
                if (sc.rare) continue
                out.add(s.id)
                var best: LivingEntity? = null
                var bd = 4.0
                for (mob in mobs) {
                    val dx = mob.x - s.x; val dz = mob.z - s.z; val dy = s.y - mob.y
                    if (dx * dx + dz * dz > 2.25 || dy < -1.0 || dy > 4.0) continue
                    val d = dx * dx + dz * dz + dy * dy
                    if (d < bd) { bd = d; best = mob }
                }
                if (best != null) out.add(best.id)
            }
            hidden = out
        } catch (t: Throwable) {
            FishDiag.fail("CommonSeaCreatureHider.1", "common creature scan failed", t)
        }
    }
}
