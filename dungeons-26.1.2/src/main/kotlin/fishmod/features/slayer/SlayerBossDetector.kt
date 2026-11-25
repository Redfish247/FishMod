package fishmod.features.slayer

import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player

object SlayerBossDetector {

    private const val SCAN_INTERVAL_TICKS = 5
    private const val MARK = "☠"
    // Hypixel dropped the skull: boss stands now read "[Lv825]  Inferno Demonlord IV 147M/150M❤" and a sibling stand "<name>'s SLAYER BOSS".
    private val LEVEL_TAG = Regex("\\[Lv\\d+\\]")
    private var scanCounter = 0
    // last time my boss stand was seen, so the spawn-spot box can vanish the moment the boss appears (before the entity is bound)
    @Volatile @JvmField var bossStandSeenAt = 0L

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { mc ->
            try {
                tick(mc)
            } catch (e: Exception) {
                FishDiag.fail("SlayerBossDetector.1", "boss detector tick failed (state=${SlayerManager.state}, type=${SlayerManager.type})", e)
            }
        })
    }

    private fun tick(mc: Minecraft) {
        if (!SlayerManager.anyEnabled() || !SlayerManager.isActiveSlayer()) {
            if (SlayerManager.bossEntity != null) SlayerManager.bossEntity = null
            scanCounter = 0
            return
        }
        val level = mc.level ?: return
        val type = FishDiag.notNull(SlayerManager.type, "SlayerBossDetector.2") { "active slayer quest but type is null (state=${SlayerManager.state})" } ?: return

        val bound = SlayerManager.bossEntity
        if (bound != null && (!bound.isAlive || bound.isRemoved)) SlayerManager.bossEntity = null

        if (scanCounter++ % SCAN_INTERVAL_TICKS != 0) return

        val canHaveBoss = SlayerManager.state == SlayerManager.State.BOSS_SPAWNED ||
            SlayerManager.state == SlayerManager.State.COCOONED ||
            SlayerManager.state == SlayerManager.State.GRINDING
        if (!canHaveBoss) return

        val myName = mc.player?.name?.string ?: ""
        // owner stands ("<name>'s SLAYER BOSS" / "Spawned by: <name>") and whether each one is mine
        val owners = ArrayList<Pair<ArmorStand, Boolean>>()
        for (e in level.entitiesForRendering()) {
            if (e !is ArmorStand || !e.hasCustomName()) continue
            val n = e.customName?.string ?: continue
            if (n.endsWith("SLAYER BOSS") || n.startsWith("Spawned by")) owners.add(e to n.contains(myName, true))
        }

        for (e in level.entitiesForRendering()) {
            if (e !is ArmorStand || !e.hasCustomName()) continue
            val name = e.customName?.string ?: continue

            val bossStand = type.bossNames.any { name.contains(it) } && (name.contains(MARK) || (LEVEL_TAG.containsMatchIn(name) && name.contains("❤")))
            // Only the boss whose nearest owner stand is mine; with no owner stands at all (older formats) accept it.
            val nearOwner = owners.filter { (o, _) -> val dx = o.x - e.x; val dz = o.z - e.z; dx * dx + dz * dz < 12.25 && Math.abs(o.y - e.y) < 5.0 }
                .minByOrNull { (o, _) -> o.distanceToSqr(e) }
            val mine = if (nearOwner != null) nearOwner.second else owners.isEmpty()
            if (bossStand && mine) {
                bossStandSeenAt = System.currentTimeMillis()
                SlayerAlerts.bossSpawned(type)
                if (SlayerManager.bossEntity == null) {
                    val mob = nearestMob(level, e, type.mobClass)
                    if (mob != null) {
                        SlayerManager.bossEntity = mob
                        SlayerTimer.onBossEntityBound()
                    }
                }
            }
        }
    }

    private fun nearestMob(level: ClientLevel, stand: ArmorStand, want: Class<out LivingEntity>?): LivingEntity? {
        val box = stand.boundingBox.inflate(1.5, 4.0, 1.5)
        var best: LivingEntity? = null
        var bestDist = Double.MAX_VALUE
        for (c in level.getEntities(stand, box)) {
            if (c is ArmorStand || c !is LivingEntity || !c.isAlive) continue
            if (c is Player) continue
            if (want != null && !want.isInstance(c)) continue
            val d = c.distanceToSqr(stand)
            if (d < bestDist) { bestDist = d; best = c }
        }
        return best
    }
}
