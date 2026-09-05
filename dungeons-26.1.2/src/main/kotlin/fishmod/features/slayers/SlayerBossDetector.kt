package fishmod.features.slayers

import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player

/**
 * Resolves the concrete boss / miniboss entities behind Hypixel's nametags.
 *
 * Like [fishmod.features.dungeon.StarredMobHighlight], the ☠ + health text sits on a separate
 * invisible ArmorStand hovering over the real mob, so detection is: scan armor-stand nametags for a
 * boss/miniboss name, then take the nearest matching living mob under that stand.
 *
 * Performance
 * -----------
 *  - The scan runs on a 5-tick cadence, never per frame.
 *  - It only runs at all while [SlayerManager.isActiveSlayer] — i.e. a live quest on the right
 *    island. Outside that it does nothing and drops its caches.
 *  - The boss scan is further limited to states where a boss can exist.
 *  - Once the boss entity is bound it's reused every tick until it dies/unloads (no re-scan).
 *  - Miniboss alerts are de-duplicated by entity id, so a stand seen for 100 ticks fires once.
 */
object SlayerBossDetector {

    private const val SCAN_INTERVAL_TICKS = 5
    private const val MARK = "☠" // ☠
    private var scanCounter = 0

    // entity ids we've already fired a miniboss alert for this quest
    private val seenMiniBosses = HashSet<Int>()

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { mc -> tick(mc) })
    }

    private fun tick(mc: Minecraft) {
        if (!FishSettings.slayerAnyEnabled() || !SlayerManager.isActiveSlayer()) {
            if (SlayerManager.bossEntity != null) SlayerManager.bossEntity = null
            if (seenMiniBosses.isNotEmpty()) seenMiniBosses.clear()
            scanCounter = 0
            return
        }
        val level = mc.level ?: return
        val type = SlayerManager.type ?: return

        // keep / drop the already-bound boss without a rescan
        val bound = SlayerManager.bossEntity
        if (bound != null && (!bound.isAlive || bound.isRemoved)) SlayerManager.bossEntity = null

        if (scanCounter++ % SCAN_INTERVAL_TICKS != 0) return

        // prune stale miniboss ids so the set can't grow unbounded across a long quest
        if (seenMiniBosses.size > 64) {
            seenMiniBosses.retainAll(level.entitiesForRendering().map { it.id }.toSet())
        }

        val canHaveBoss = SlayerManager.state == SlayerManager.State.BOSS_SPAWNED ||
            SlayerManager.state == SlayerManager.State.COCOONED ||
            SlayerManager.state == SlayerManager.State.GRINDING

        for (e in level.entitiesForRendering()) {
            if (e !is ArmorStand || !e.hasCustomName()) continue
            val name = e.customName?.string ?: continue

            // miniboss — key the de-dupe on the health-nametag armour stand's own id, which is
            // stable for that miniboss's whole life, so the alert fires once at first sight (never
            // on a later re-scan / near death) and never latches onto a random nearby trash mob.
            if (FishSettings.slayerSpawnAlertEnabled && FishSettings.slayerMiniBossAlert) {
                if (SlayerType.miniBossTypeFor(name) == type) {
                    if (seenMiniBosses.add(e.id)) SlayerAlerts.miniBoss(miniBossLabel(name, type))
                    continue
                }
            }

            // main boss
            if (canHaveBoss && name.contains(MARK) && type.bossNames.any { name.contains(it) }) {
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

    /** Extract the shown miniboss name from a nametag like "Revenant Champion 8k❤". */
    private fun miniBossLabel(name: String, type: SlayerType): String =
        type.miniBosses.firstOrNull { name.contains(it) } ?: name

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

    /** Called by SlayerManager on a fresh quest so a new boss's adds alert again. */
    @JvmStatic
    fun clearSeenMiniBosses() = seenMiniBosses.clear()
}
