package fishmod.features.slayer

import fishmod.features.slayer.EndAlertSettings as S
import fishmod.utils.data.EntityTools
import fishmod.utils.debug.FishDiag
import fishmod.utils.rendering.FishDraw
import fishmod.utils.rendering.RenderHooks
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import fishmod.utils.events.FishEvents
import net.minecraft.world.phys.AABB

/** Box on the slayer boss (issue #249): live box on the boss mob, plus a box on the spot where the last slayer-type mob died (the kill that starts the boss spawn), gone once the boss exists. */
object SlayerBossBox {

    private class Seen(val mob: LivingEntity, var box: AABB)

    private val seen = HashMap<Int, Seen>()
    private var killBox: AABB? = null
    private var killAt = 0L
    private var spawnBox: AABB? = null
    private var prevState: SlayerManager.State = SlayerManager.State.NONE
    private var windowAt = 0L
    private var lastScanAt = 0L
    private const val SPAWN_WINDOW_MS = 15_000L
    private const val KILL_MAX_AGE_MS = 5_000L
    private const val SCAN_RANGE = 24.0
    private const val SCAN_MS = 100L

    fun init() {
        RenderHooks.GIZMO.register { _ ->
            try { render() } catch (t: Throwable) { FishDiag.fail("SlayerBossBox.1", "boss box render failed", t) }
        }
        FishEvents.worldChange.register { seen.clear(); killBox = null; spawnBox = null; prevState = SlayerManager.State.NONE; windowAt = 0L; false }
    }

    private fun windowOpen(): Boolean {
        if (!S.slayerBossBox || !S.slayerBossBoxSpawnMark || SlayerManager.state != SlayerManager.State.BOSS_SPAWNED) return false
        val boss = SlayerManager.bossEntity
        if (boss != null && boss.isAlive && !boss.isRemoved) return false
        if (SlayerBossDetector.bossStandSeenAt >= windowAt) return false
        return System.currentTimeMillis() - windowAt < SPAWN_WINDOW_MS
    }

    private fun trackMobs() {
        val now = System.currentTimeMillis()
        if (now - lastScanAt < SCAN_MS) return
        lastScanAt = now
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val me = mc.player ?: return
        val type = SlayerManager.type ?: return
        val it = seen.entries.iterator()
        while (it.hasNext()) {
            val s = it.next().value
            if (!s.mob.isAlive || s.mob.isRemoved) {
                if (!s.mob.isAlive) { killBox = s.box; killAt = now }
                it.remove()
            } else s.box = s.mob.boundingBox
        }
        for (e in level.entitiesForRendering()) {
            if (e !is LivingEntity || e is ArmorStand || e is Player || !e.isAlive) continue
            if (!type.mobClass.isInstance(e) || e.distanceToSqr(me) > SCAN_RANGE * SCAN_RANGE) continue
            val s = seen[e.id]
            if (s == null) seen[e.id] = Seen(e, e.boundingBox)
        }
    }

    private fun playerBox(k: AABB): AABB {
        val cx = (k.minX + k.maxX) / 2; val cz = (k.minZ + k.maxZ) / 2
        return AABB(cx - 0.3, k.minY, cz - 0.3, cx + 0.3, k.minY + 1.8, cz + 0.3)
    }

    private fun render() {
        if (!S.slayerBossBox) return
        if (SlayerManager.isActiveSlayer()) trackMobs() else if (seen.isNotEmpty()) seen.clear()
        val boss = SlayerManager.bossEntity
        val st = SlayerManager.state
        if (st != prevState) {
            if (st == SlayerManager.State.BOSS_SPAWNED) {
                windowAt = System.currentTimeMillis()
                spawnBox = killBox?.takeIf { windowAt - killAt <= KILL_MAX_AGE_MS }?.let { playerBox(it) }
            }
            prevState = st
        }
        val c = S.slayerBossBoxColor
        if (boss == null || !boss.isAlive || boss.isRemoved) {
            if (!windowOpen()) return
            spawnBox?.let { FishDraw.gizmoBox(it, (0x20 shl 24) or (c and 0xFFFFFF), c or 0xFF000000.toInt(), false) }
            return
        }
        if (SlayerManager.type == SlayerType.INFERNO) return
        val fill = (0x30 shl 24) or (c and 0xFFFFFF)
        val lp = EntityTools.getLerpedPos(boss)
        val live = boss.boundingBox.move(lp.x - boss.x, lp.y - boss.y, lp.z - boss.z).inflate(0.1)
        FishDraw.gizmoBox(live, fill, c or 0xFF000000.toInt(), false)
    }
}
