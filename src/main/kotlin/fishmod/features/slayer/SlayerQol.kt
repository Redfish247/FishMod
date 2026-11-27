package fishmod.features.slayer

import fishmod.features.mining.MiningHuds
import fishmod.features.slayer.EndAlertSettings as S
import fishmod.utils.config.values.FishSettings
import fishmod.utils.TextStrip
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.FishEvents
import fishmod.utils.rendering.FishDraw
import fishmod.utils.rendering.RenderHooks
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.Blaze
import net.minecraft.world.entity.player.Player

// Slayer QOL: boss-only stats HUD and Inferno Demonlord attunement highlight (boss + split demons).
object SlayerQol {
    private val HP_CUR_MAX = Regex("([\\d,.]+)\\s*([kKmMbB])?\\s*/\\s*([\\d,.]+)\\s*([kKmMbB])?\\s*❤")
    private val HP = Regex("([\\d,.]+)\\s*([kKmMbB])?\\s*❤")
    // attunement word on the boss/demon nametag -> outline colour matching the dagger to use
    // split demons have circled-letter names ("[Lv999] ⓆⓊⒶⓏⒾⒾ 10M/10M❤"); boxed red while invulnerable (no attunement text), then in the attunement colour
    private val DEMON_NAME = Regex("[\u24B6-\u24E9]{4,}")
    private const val DEMON_UNKNOWN = 0xFFFF5555.toInt()
    private val ATTUNE = listOf("ASHEN" to 0xFF555555.toInt(), "AURIC" to 0xFFFFFF55.toInt(), "SPIRIT" to 0xFFFFFFFF.toInt(), "CRYSTAL" to 0xFF55FFFF.toInt())

    @Volatile private var glow: Map<Int, Int> = emptyMap()
    // entity id -> (colour, last time its attunement text was seen); the text vanishes while the boss flies up, the box should not
    private val held = HashMap<Int, Pair<Int, Long>>()
    @Volatile private var lines: List<String> = emptyList()
    private var tick = 0
    private var bossId = -1
    private var maxSeen = 0.0
    private var spawnedAt = 0L

    fun init() {
        FishEvents.worldChange.register { glow = emptyMap(); held.clear(); lines = emptyList(); bossId = -1; false }
        RenderHooks.GIZMO.register { _ -> FishDiag.guard("SlayerQol.2", "attune box render failed") { renderAttuneBoxes() } }
        ClientTickEvents.END_CLIENT_TICK.register { mc -> if (++tick % 5 == 0) FishDiag.guard("SlayerQol.1", "slayer qol scan failed") { scan(mc) } }
        MiningHuds.reg("Slayer Boss", "slayer_boss_hud", 120, 40,
            { S.bossHudX }, { S.bossHudX = it }, { S.bossHudY }, { S.bossHudY = it }, { S.bossHudScale }, { S.bossHudScale = it },
            { FishSettings.slayerQolEnabled && S.bossHud && lines.isNotEmpty() }, { FishSettings.slayerQolEnabled && S.bossHud },
            example = { listOf("§bVoidgloom Seraph IV", "§cHP\t§a12.5M§7/§a30M", "§cDPS\t§e850k", "§bTime\t§f24.1s") }, lines = { lines })
    }

    // Attunement is shown as a box now (depth-tested, so never through walls); the entity outline hook stays as a no-op.
    @JvmStatic
    fun glowColor(e: Entity): Int = EntityRenderState.NO_OUTLINE

    /** True for the Inferno Demonlord boss model when the user chose to hide it. */
    @JvmStatic
    fun hideBoss(e: Entity): Boolean {
        if (!S.hideInfernoBoss || !FishSettings.slayerQolEnabled || SlayerManager.type != SlayerType.INFERNO) return false
        val b = SlayerManager.bossEntity ?: return false
        return b === e
    }

    private fun renderAttuneBoxes() {
        val g = glow
        if (g.isEmpty() || !FishSettings.slayerQolEnabled || !S.attuneHighlight) return
        val level = Minecraft.getInstance().level ?: return
        for ((id, c) in g) {
            val e = level.getEntity(id) ?: continue
            if (!e.isAlive) continue
            // follow the per-frame interpolated position, not the 20 tps tick position (that is what made the box stutter)
            val p = fishmod.utils.data.EntityTools.getLerpedPos(e)
            FishDraw.gizmoBox(e.boundingBox.move(p.x - e.x, p.y - e.y, p.z - e.z).inflate(0.1), (0x30 shl 24) or (c and 0xFFFFFF), c or 0xFF000000.toInt(), false)
        }
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
        if (level == null || me == null || type == null || !FishSettings.slayerQolEnabled || !(S.bossHud || S.attuneHighlight)) { glow = emptyMap(); lines = emptyList(); return }
        val boss = SlayerManager.bossEntity
        val stands = ArrayList<ArmorStand>()
        val mobs = ArrayList<LivingEntity>()
        for (e in level.entitiesForRendering()) {
            if (e === me || e.distanceToSqr(me) > 40.0 * 40.0) continue
            if (e is ArmorStand) { if (e.customName != null) stands.add(e) }
            else if (e is LivingEntity && e.isAlive && (e !is Player || e.uuid.version() != 4)) mobs.add(e) // NPC-style players (the split demons) count, real players do not
        }
        // demon = true: the body of a split demon (wither skeleton, piglin...): never the boss, never a blaze (the arena is full of
        // Smoldering Blazes) and never small helper entities (guardians)
        fun mobUnder(s: ArmorStand, demon: Boolean = false): LivingEntity? {
            // the text floats 2.6-4 blocks above the demon, so look up to 7 blocks away
            var best: LivingEntity? = null; var bd = 49.0
            for (m in mobs) {
                if (demon && (m === boss || m is Blaze || m.bbHeight < 1.5f)) continue
                val dx = m.x - s.x; val dz = m.z - s.z; val dy = s.y - m.y
                if (dx * dx + dz * dz > 2.25 || dy < -1.0 || dy > 5.5) continue
                val d = dx * dx + dz * dz + dy * dy
                if (d < bd) { bd = d; best = m }
            }
            return best
        }
        if (S.attuneHighlight && type == SlayerType.INFERNO) {
            val g = HashMap<Int, Int>()
            // demons by their circled-letter name stand: red box until the attunement text shows
            val demonStands = stands.filter { boss != null && boss.isAlive && DEMON_NAME.containsMatchIn(it.customName!!.string) }
            val demonMob = HashMap<ArmorStand, LivingEntity>()
            for (ds in demonStands) mobUnder(ds, true)?.let { demonMob[ds] = it; g[it.id] = DEMON_UNKNOWN }
            for (s in stands) {
                // only my boss and the demons of its arena (they can stand well away from the boss), not another player's fight
                if (boss == null || !boss.isAlive) continue // demons stand ~47 blocks below a boss that flew up, so no distance limit to the boss (scan range around the player still applies)
                val plain = TextStrip.plain(s.customName!!.string).uppercase()
                val c = ATTUNE.firstOrNull { plain.contains(it.first) }?.second ?: continue
                // text next to my boss is the boss's; otherwise it belongs to the demon whose name stand is beside it (same entity as the red box)
                val tgt = if (s.distanceToSqr(boss) < 16.0) boss else {
                    val ds = demonStands.filter { it.distanceToSqr(s) < 16.0 }.minByOrNull { it.distanceToSqr(s) }
                    (ds?.let { demonMob[it] }) ?: mobUnder(s, true)
                }
                if (tgt != null) g[tgt.id] = c
            }
            val now = System.currentTimeMillis()
            for ((id, c) in g) held[id] = c to now
            held.entries.removeIf { (id, v) -> now - v.second > (if (boss?.id == id) 15_000L else 6_000L) || level.getEntity(id)?.isAlive != true }
            glow = held.mapValues { it.value.first }
            if (tick % 40 == 0) {
                val near = ArrayList<String>()
                for (s in stands) {
                    val pl = TextStrip.plain(s.customName!!.string).uppercase()
                    if (ATTUNE.none { pl.contains(it.first) }) continue
                    val es = level.entitiesForRendering().filter { it !is ArmorStand && it.distanceToSqr(s) < 16.0 }.sortedBy { it.distanceToSqr(s) }.take(3)
                    near.add("'" + TextStrip.plain(s.customName!!.string) + "' dBoss=" + (boss?.let { "%.0f".format(Math.sqrt(s.distanceToSqr(it))) } ?: "-") + " -> " + es.joinToString(",") { it.type.descriptionId.substringAfterLast('.') + "@" + "%.1f".format(Math.sqrt(it.distanceToSqr(s))) + (if (it is Player) "(player v${it.uuid.version()})" else "") })
                }
                for (ds in demonStands) {
                    val es = level.entitiesForRendering().filter { it !is ArmorStand && it.distanceToSqr(ds) < 36.0 }.sortedBy { it.distanceToSqr(ds) }.take(4)
                    near.add("DEMON '" + TextStrip.plain(ds.customName!!.string) + "' -> " + es.joinToString(",") { it.type.descriptionId.substringAfterLast('.') + "@" + "%.1f".format(Math.sqrt(it.distanceToSqr(ds))) + "h" + "%.1f".format(it.bbHeight) } + " picked=" + (demonMob[ds]?.type?.descriptionId?.substringAfterLast('.') ?: "none"))
                }
                fishmod.utils.debug.FishDebug.LOGGER.info("[BlazeDiag] attune boxes=${g.size} stands=${stands.size} mobs=${mobs.size} bossBound=${boss != null} attuneStands=" + near.joinToString(" ; "))
            }
        } else if (glow.isNotEmpty()) { glow = emptyMap(); held.clear() }

        if (!S.bossHud || boss == null || !boss.isAlive) { lines = emptyList(); bossId = -1; return }
        if (boss.id != bossId) { bossId = boss.id; maxSeen = 0.0; spawnedAt = System.currentTimeMillis() }
        var hp = 0.0
        for (s in stands) {
            if (s.distanceToSqr(boss) > 16.0) continue
            val plain = TextStrip.plain(s.customName!!.string)
            val cm = HP_CUR_MAX.find(plain)
            if (cm != null) {
                val c = (cm.groupValues[1].replace(",", "").toDoubleOrNull() ?: continue) * mult(cm.groupValues[2])
                val mx = (cm.groupValues[3].replace(",", "").toDoubleOrNull() ?: continue) * mult(cm.groupValues[4])
                if (mx > maxSeen) maxSeen = mx
                if (c > hp) hp = c
                continue
            }
            val m = HP.find(plain) ?: continue
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
