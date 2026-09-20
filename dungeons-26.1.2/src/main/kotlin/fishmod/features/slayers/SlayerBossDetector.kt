package fishmod.features.slayers

import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player

object SlayerBossDetector {

    private const val SCAN_INTERVAL_TICKS = 5
    private const val MARK = "☠"
    private var scanCounter = 0

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { mc -> tick(mc) })
    }

    private fun tick(mc: Minecraft) {
        if (!FishSettings.slayerAnyEnabled() || !SlayerManager.isActiveSlayer()) {
            if (SlayerManager.bossEntity != null) SlayerManager.bossEntity = null
            scanCounter = 0
            return
        }
        val level = mc.level ?: return
        val type = SlayerManager.type ?: return

        val bound = SlayerManager.bossEntity
        if (bound != null && (!bound.isAlive || bound.isRemoved)) SlayerManager.bossEntity = null

        if (scanCounter++ % SCAN_INTERVAL_TICKS != 0) return

        val canHaveBoss = SlayerManager.state == SlayerManager.State.BOSS_SPAWNED ||
            SlayerManager.state == SlayerManager.State.COCOONED ||
            SlayerManager.state == SlayerManager.State.GRINDING
        if (!canHaveBoss) return

        for (e in level.entitiesForRendering()) {
            if (e !is ArmorStand || !e.hasCustomName()) continue
            val name = e.customName?.string ?: continue

            if (name.contains(MARK) && type.bossNames.any { name.contains(it) }) {
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
