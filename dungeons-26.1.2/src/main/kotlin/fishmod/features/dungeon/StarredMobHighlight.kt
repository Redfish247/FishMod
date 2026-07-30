package fishmod.features.dungeon

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
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
 * living entity underneath as the actual mob to outline (same approach NoammAddons uses).
 */
object StarredMobHighlight {

    private const val STAR = "✯"
    private const val HEART = "❤"

    @JvmStatic
    fun init() {
        RenderingEvents.OUTLINE_ENTITY.register { context, matrices, consumer -> render(context, matrices, consumer) }
    }

    private fun active(): Boolean {
        return FishSettings.enableStarredMobHighlight && Location.inDungeon()
    }

    private fun render(context: LevelRenderContext, matrices: PoseStack, consumer: VertexConsumer) {
        if (!active()) return
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return

        val rgba = RenderUtils.toFloats(FishSettings.starredMobHighlightColor)

        for (mob in findStarredMobs(level)) {
            if (mob is LivingEntity && mob.isAlive) {
                RenderUtils.renderOutline(matrices, consumer, mob.boundingBox, rgba)
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
