package fishmod.features.slayer

import fishmod.features.slayer.EndAlertSettings as S
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.FishEvents
import fishmod.utils.rendering.FishDraw
import fishmod.utils.rendering.RenderHooks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/** Box on the slayer boss (issue #249): live box on the boss mob plus a marker where it first appeared. */
object SlayerBossBox {

    private var spawnPos: Vec3? = null
    private var spawnBoundMs = 0L
    private var lastBoss: Int = -1
    private const val SPAWN_MARK_MS = 8000L
    private var preSpawnPos: Vec3? = null
    private var preSpawnScanAt = 0L

    fun init() {
        RenderHooks.GIZMO.register { _ ->
            try { render() } catch (t: Throwable) { FishDiag.fail("SlayerBossBox.1", "boss box render failed", t) }
        }
        FishEvents.worldChange.register { spawnPos = null; lastBoss = -1; preSpawnPos = null; false }
    }

    private fun render() {
        if (!S.slayerBossBox) return
        val boss = SlayerManager.bossEntity
        val st = SlayerManager.state
        if (boss == null || !boss.isAlive || boss.isRemoved) {
            spawnPos = null; lastBoss = -1
            if (!S.slayerBossBoxSpawnMark || st != SlayerManager.State.BOSS_SPAWNED) { preSpawnPos = null; return }
            // Mark the boss nametag (where the boss is spawning), not the player's own position.
            val now = System.currentTimeMillis()
            if (now - preSpawnScanAt >= 250L) { preSpawnScanAt = now; preSpawnPos = findBossTag() ?: preSpawnPos }
            val pp = preSpawnPos ?: return
            val c = S.slayerBossBoxColor
            FishDraw.gizmoBox(AABB(pp.x - 0.6, pp.y - 2.2, pp.z - 0.6, pp.x + 0.6, pp.y, pp.z + 0.6), (0x20 shl 24) or (c and 0xFFFFFF), c or 0xFF000000.toInt(), true)
            return
        }
        preSpawnPos = null
        if (boss.id != lastBoss) {
            lastBoss = boss.id
            spawnPos = boss.position()
            spawnBoundMs = System.currentTimeMillis()
        }
        val c = S.slayerBossBoxColor
        val fill = (0x30 shl 24) or (c and 0xFFFFFF)
        val live = boss.boundingBox.inflate(0.1)
        FishDraw.gizmoBox(live, fill, c or 0xFF000000.toInt(), true)
        val sp = spawnPos
        if (S.slayerBossBoxSpawnMark && sp != null && System.currentTimeMillis() - spawnBoundMs < SPAWN_MARK_MS) {
            val w = boss.bbWidth.toDouble().coerceAtLeast(1.0)
            val box = AABB(sp.x - w / 2, sp.y, sp.z - w / 2, sp.x + w / 2, sp.y + boss.bbHeight.toDouble().coerceAtLeast(1.0), sp.z + w / 2)
            FishDraw.gizmoBox(box, (0x20 shl 24) or (c and 0xFFFFFF), 0xFFFFFFFF.toInt(), true)
        }
    }

    // Boss nametag stand (skull mark + boss name) nearest the player, before the mob itself is bound.
    private fun findBossTag(): Vec3? {
        val type = SlayerManager.type ?: return null
        val mc = net.minecraft.client.Minecraft.getInstance()
        val level = mc.level ?: return null
        val me = mc.player ?: return null
        var best: Vec3? = null
        var bestD = 48.0 * 48.0
        for (e in level.entitiesForRendering()) {
            if (e !is net.minecraft.world.entity.decoration.ArmorStand || !e.hasCustomName()) continue
            val n = e.customName?.string ?: continue
            if (!n.contains("☠") || type.bossNames.none { n.contains(it) }) continue
            val d = e.distanceToSqr(me)
            if (d < bestD) { bestD = d; best = e.position() }
        }
        return best
    }
}
