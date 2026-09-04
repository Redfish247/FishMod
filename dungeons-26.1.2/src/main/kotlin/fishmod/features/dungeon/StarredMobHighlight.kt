package fishmod.features.dungeon

import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB

/**
 * Outlines dungeon mobs marked as "starred" elites. Hypixel doesn't put the ✯ on the mob's own
 * nametag — it's on a separate invisible armor stand riding/hovering above the mob that also
 * shows its health (e.g. "Zombie Knight ✯300,000/300,000❤"). So detection works by scanning
 * armor-stand nametags for the star + heart markers, then picking the nearest non-armor-stand
 * living entity underneath as the actual mob to outline.
 */
object StarredMobHighlight {

    private const val STAR = "✯"
    private const val HEART = "❤"

    // Which mobs are starred rarely changes frame-to-frame, so the entity/AABB scan runs on a
    // tick interval instead of every rendered frame; the render callback just reads the cache.
    private const val SCAN_INTERVAL_TICKS = 5
    private var scanCounter = 0
    private var cachedStarredMobs: Set<Entity> = emptySet()

    @JvmStatic
    fun init() {
        RenderingEvents.GIZMO.register { _ -> render() }
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { tick() })
    }

    private fun active(): Boolean {
        return FishSettings.enableStarredMobHighlight && Location.inDungeon()
    }

    private fun tick() {
        if (!active()) { cachedStarredMobs = emptySet(); scanCounter = 0; return }
        val level = Minecraft.getInstance().level ?: return
        scanCounter++
        if (scanCounter < SCAN_INTERVAL_TICKS) return
        scanCounter = 0
        cachedStarredMobs = findStarredMobs(level)
    }

    private fun render() {
        if (!active()) return
        val stroke = FishSettings.starredMobHighlightColor
        for (mob in cachedStarredMobs) {
            if (mob is LivingEntity && mob.isAlive) {
                RenderUtils.gizmoBox(mob.boundingBox, 0, stroke)
            }
        }
    }

    private fun findStarredMobs(level: ClientLevel): Set<Entity> {
        val mobs: MutableSet<Entity> = HashSet()
        for (entity in level.entitiesForRendering()) {
            if (entity !is ArmorStand || !entity.hasCustomName()) continue
            val name = entity.customName!!.string
            if (!name.contains(STAR) || !name.contains(HEART)) continue

            val mob = findNearestMob(level, entity)
            if (mob != null) mobs.add(mob)
        }
        return mobs
    }

    private fun findNearestMob(level: ClientLevel, stand: ArmorStand): Entity? {
        val searchBox = stand.boundingBox.inflate(1.0, 3.0, 1.0)
        var nearest: Entity? = null
        var nearestDist = Double.MAX_VALUE

        for (candidate in level.getEntities(stand, searchBox)) {
            if (!isEligibleMob(candidate)) continue
            val dist = candidate.distanceToSqr(stand)
            if (dist < nearestDist) {
                nearestDist = dist
                nearest = candidate
            }
        }
        return nearest
    }

    private fun isEligibleMob(entity: Entity): Boolean {
        if (entity is ArmorStand) return false
        if (entity !is LivingEntity) return false
        if (!entity.isAlive) return false
        if (entity is Player) return !entity.isInvisible
        return true
    }
}
