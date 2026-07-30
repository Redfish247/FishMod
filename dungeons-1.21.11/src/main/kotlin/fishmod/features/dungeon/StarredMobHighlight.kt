package fishmod.features.dungeon

import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.client.world.ClientWorld
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.decoration.ArmorStandEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.Box

/** Outlines dungeon mobs marked as "starred" elites. */
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

    private fun render(context: WorldRenderContext, matrices: MatrixStack, consumer: VertexConsumer) {
        if (!active()) return
        val mc = MinecraftClient.getInstance()
        val world = mc.world ?: return

        val rgba = RenderUtils.toFloats(FishSettings.starredMobHighlightColor)

        for (mob in findStarredMobs(world)) {
            if (mob is LivingEntity && mob.isAlive) {
                RenderUtils.renderOutline(matrices, consumer, mob.boundingBox, rgba)
            }
        }
    }

    private fun findStarredMobs(world: ClientWorld): Set<Entity> {
        val mobs: MutableSet<Entity> = HashSet()
        for (entity in world.entities) {
            if (entity !is ArmorStandEntity || !entity.hasCustomName()) continue
            val name = entity.customName!!.string
            if (!name.contains(STAR) || !name.contains(HEART)) continue

            val mob = findNearestMob(world, entity)
            if (mob != null) mobs.add(mob)
        }
        return mobs
    }

    private fun findNearestMob(world: ClientWorld, stand: ArmorStandEntity): Entity? {
        val searchBox = stand.boundingBox.expand(1.0, 3.0, 1.0)
        var nearest: Entity? = null
        var nearestDist = Double.MAX_VALUE

        for (candidate in world.getOtherEntities(stand, searchBox)) {
            if (!isEligibleMob(candidate)) continue
            val dist = candidate.squaredDistanceTo(stand)
            if (dist < nearestDist) {
                nearestDist = dist
                nearest = candidate
            }
        }
        return nearest
    }

    private fun isEligibleMob(entity: Entity): Boolean {
        if (entity is ArmorStandEntity) return false
        if (entity !is LivingEntity) return false
        if (!entity.isAlive) return false
        if (entity is PlayerEntity) return !entity.isInvisible
        return true
    }
}
