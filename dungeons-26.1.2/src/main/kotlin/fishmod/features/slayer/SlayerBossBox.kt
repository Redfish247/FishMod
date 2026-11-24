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

    fun init() {
        RenderHooks.GIZMO.register { _ ->
            try { render() } catch (t: Throwable) { FishDiag.fail("SlayerBossBox.1", "boss box render failed", t) }
        }
        FishEvents.worldChange.register { spawnPos = null; lastBoss = -1; false }
    }

    private fun render() {
        if (!S.slayerBossBox) return
        val boss = SlayerManager.bossEntity
        if (boss == null || !boss.isAlive || boss.isRemoved) { spawnPos = null; lastBoss = -1; return }
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
