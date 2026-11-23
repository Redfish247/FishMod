package fishmod.features.slayer

import fishmod.features.mining.MiningHuds
import fishmod.features.slayer.EndAlertSettings as S
import fishmod.utils.TextStrip
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.FishEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player

// Slayer QOL: boss-only stats HUD and Inferno Demonlord attunement highlight (boss + split demons).
object SlayerQol {
    private val HP = Regex("([\d,.]+)\s*([kKmMbB])?\s*❤")
    // attunement word on the boss/demon nametag -> outline colour matching the dagger to use
    private val ATTUNE = listOf("ASHEN" to 0xFF555555.toInt(), "AURIC" to 0xFFFFFF55.toInt(), "SPIRIT" to 0xFFFFFFFF.toInt(), "CRYSTAL" to 0xFF55FFFF.toInt())

    @Volatile private var glow: Map<Int, Int> = emptyMap()
    @Volatile private var lines: List<String> = emptyList()
    private var tick = 0
    private var bossId = -1
    private var maxSeen = 0.0
    private var spawnedAt = 0L

    fun init() {
        FishEvents.worldChange.register { glow = emptyMap(); lines = emptyList(); bossId = -1; false }
        ClientTickEvents.END_CLIENT_TICK.register { mc -> if (++tick % 5 == 0) FishDiag.guard("SlayerQol.1", "slayer qol scan failed") { scan(mc) } }
        MiningHuds.reg("Slayer Boss", "slayer_boss_hud", 120, 40,
            { S.bossHudX }, { S.bossHudX = it }, { S.bossHudY }, { S.bossHudY = it }, { S.bossHudScale }, { S.bossHudScale = it },
            { S.bossHud && lines.isNotEmpty() }, { S.bossHud },
            example = { listOf("§bVoidgloom Seraph IV", "§cHP\t§a12.5M§7/§a30M", "§cDPS\t§e850k", "§bTime\t§f24.1s") }, lines = { lines })
    }

    @JvmStatic
    fun glowColor(e: Entity): Int {
        val g = glow
        if (g.isEmpty() || !S.attuneHighlight) return EntityRenderState.NO_OUTLINE
        return g[e.id] ?: EntityRenderState.NO_OUTLINE
    }

    private fun mult(u: String) = when (u.lowercase()) { "k" -> 1e3; "m" -> 1e6; "b" -> 1e9; else -> 1.0 }
    private fun short(v: Double): String = when {
        v >= 1e9 -> "%.2fB".format(v / 1e9)
        v >= 1e6 -> "%.2fM".format(v / 1e6)
        v >= 1e4 -> "%.1fk".format(v / 1e3)
        else -> v.toLong().toString()
    }

    private fun scan(mc: Minecraft) {
        val level = mc.level; val me = mc.player; val type = SlayerManager.type
        if (level == null || me == null || type == null || !(S.bossHud || S.attuneHighlight)) { glow = emptyMap(); lines = emptyList(); return }
        val boss = SlayerManager.bossEntity
        val stands = ArrayList<ArmorStand>()
        val mobs = ArrayList<LivingEntity>()
        for (e in level.entitiesForRendering()) {
            if (e === me || e.distanceToSqr(me) > 40.0 * 40.0) continue
            if (e is ArmorStand) { if (e.customName != null) stands.add(e) }
            else if (e is LivingEntity && e !is Player && e.isAlive) mobs.add(e)
        }
        fun mobUnder(s: ArmorStand): LivingEntity? {
            var best: LivingEntity? = null; var bd = 4.0
            for (m in mobs) {
                val dx = m.x - s.x; val dz = m.z - s.z; val dy = s.y - m.y
                if (dx * dx + dz * dz > 2.25 || dy < -1.0 || dy > 4.0) continue
                val d = dx * dx + dz * dz + dy * dy
                if (d < bd) { bd = d; best = m }
            }
            return best
        }
        if (S.attuneHighlight && type == SlayerType.INFERNO) {
            val g = HashMap<Int, Int>()
            for (s in stands) {
                val plain = TextStrip.plain(s.customName!!.string).uppercase()
                val c = ATTUNE.firstOrNull { plain.contains(it.first) }?.second ?: continue
                mobUnder(s)?.let { g[it.id] = c }
            }
            glow = g
        } else if (glow.isNotEmpty()) glow = emptyMap()

        if (!S.bossHud || boss == null || !boss.isAlive) { lines = emptyList(); bossId = -1; return }
        if (boss.id != bossId) { bossId = boss.id; maxSeen = 0.0; spawnedAt = System.currentTimeMillis() }
        var hp = 0.0
        for (s in stands) {
            if (s.distanceToSqr(boss) > 16.0) continue
            val m = HP.find(TextStrip.plain(s.customName!!.string)) ?: continue
            val v = (m.groupValues[1].replace(",", "").toDoubleOrNull() ?: continue) * mult(m.groupValues[2])
            if (v > hp) hp = v
        }
        if (hp > maxSeen) maxSeen = hp
        val secs = (System.currentTimeMillis() - spawnedAt) / 1000.0
        val out = ArrayList<String>()
        out.add("§b" + type.displayName + " " + SlayerManager.tier)
        if (hp > 0.0) {
            val f = if (maxSeen > 0) hp / maxSeen else 1.0
            val c = if (f > 0.5) "§a" else if (f > 0.25) "§e" else "§c"
            out.add("§cHP\t$c" + short(hp) + "§7/§a" + short(maxSeen) + " §7(" + (f * 100).toInt() + "%)")
            if (secs > 1 && maxSeen > hp) out.add("§cDPS\t§e" + short((maxSeen - hp) / secs))
        }
        out.add("§bTime\t§f" + "%.1f".format(secs) + "s")
        lines = out
    }
}
