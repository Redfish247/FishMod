package fishmod.features.slayers

import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player

/**
 * Resolves the concrete boss entity behind Hypixel's nametag.
 *
 * Like [fishmod.features.dungeon.StarredMobHighlight], the ☠ + health text sits on a separate
 * invisible ArmorStand hovering over the real mob, so detection is: scan armor-stand nametags for
 * the boss name, then take the nearest matching living mob under that stand.
 *
 * Miniboss alerts are NOT handled here — they fire off the `SLAYER MINI-BOSS <name> has spawned!`
 * chat line in [SlayerManager]. This class only binds the main boss entity (for the "Fully Spawned"
 * timer mode) and doubles as a nametag-based backup for the boss-spawn alert.
 *
 * Performance
 * -----------
 *  - The scan runs on a 5-tick cadence, never per frame.
 *  - It only runs at all while [SlayerManager.isActiveSlayer] — i.e. a live quest on the right
 *    island. Outside that it does nothing and drops its caches.
 *  - The boss scan is further limited to states where a boss can exist.
 *  - Once the boss entity is bound it's reused every tick until it dies/unloads (no re-scan).
 */
object SlayerBossDetector {

    private const val SCAN_INTERVAL_TICKS = 5
    private const val MARK = "☠" // ☠
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

        // keep / drop the already-bound boss without a rescan
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

            // main boss
            if (name.contains(MARK) && type.bossNames.any { name.contains(it) }) {
                // fire the spawn alert straight off the nametag too — independent of the scoreboard
                // progress line, which can lag or flicker (SlayerAlerts latches so it's still once)
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
