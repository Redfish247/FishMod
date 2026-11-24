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
    private const val PRE_SPAWN_MS = 8000L
    private var prevState: SlayerManager.State = SlayerManager.State.NONE
    private var preSpawnPos: Vec3? = null
    private var preSpawnAt = 0L

    fun init() {
        RenderHooks.GIZMO.register { _ ->
            try { render() } catch (t: Throwable) { FishDiag.fail("SlayerBossBox.1", "boss box render failed", t) }
        }
        FishEvents.worldChange.register { spawnPos = null; lastBoss = -1; preSpawnPos = null; prevState = SlayerManager.State.NONE; false }
    }

    private fun render() {
        if (!S.slayerBossBox) return
        val boss = SlayerManager.bossEntity
        val st = SlayerManager.state
        if (st != prevState) {
            // Boss spawns on the player when the quest bar fills, so the player spot at that moment is the pre-spawn spot.
            if (st == SlayerManager.State.BOSS_SPAWNED && prevState == SlayerManager.State.GRINDING) {
                preSpawnPos = net.minecraft.client.Minecraft.getInstance().player?.position()
                preSpawnAt = System.currentTimeMillis()
            }
            prevState = st
        }
        if (boss == null || !boss.isAlive || boss.isRemoved) {
            spawnPos = null; lastBoss = -1
            val pp = preSpawnPos
            if (pp != null && S.slayerBossBoxSpawnMark && st == SlayerManager.State.BOSS_SPAWNED && System.currentTimeMillis() - preSpawnAt < PRE_SPAWN_MS) {
                val c = S.slayerBossBoxColor
                FishDraw.gizmoBox(AABB(pp.x - 0.6, pp.y, pp.z - 0.6, pp.x + 0.6, pp.y + 2.2, pp.z + 0.6), (0x20 shl 24) or (c and 0xFFFFFF), c or 0xFF000000.toInt(), true)
            } else if (pp != null) preSpawnPos = null
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
}
