package fishmod.features.dungeon.f7

import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import fishmod.utils.dungeon.DungeonClass
import fishmod.utils.dungeon.Phase
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import com.mojang.brigadier.Command
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import fishmod.utils.debug.FishDiag

// Storm LB aim: a marker that follows your eye along the class's recorded aim direction.
object StormLbWaypoint {

    private class Spot(val x: Double, val y: Double, val z: Double, yaw: Float, pitch: Float) {
        val dir: Vec3 = Vec3.directionFromRotation(pitch, yaw)
    }

    private val ARCHER = Spot(87.70, 169.00, 75.30, -133.29f, -32.37f)
    private val HEALER = Spot(59.30, 169.00, 64.01, -90.23f, -19.47f)
    private const val AIM_DIST = 12.0

    // Debug override: forces a class's waypoint on anywhere (/fm stormlb archer|healer|off).
    private var testSpot: Spot? = null

    @JvmStatic
    fun init() {
        RenderingEvents.GIZMO.register { _ ->
            if (!FishSettings.stormLbWaypointEnabled && testSpot == null) return@register
            try { render() } catch (e: Exception) { FishDiag.fail("StormLbWaypoint.1", "storm LB waypoint render threw", e) }
        }
    }

    @JvmStatic
    fun command(): LiteralArgumentBuilder<FabricClientCommandSource> {
        fun set(s: Spot?, name: String) = Command<FabricClientCommandSource> {
            testSpot = s
            Minecraft.getInstance().player?.sendSystemMessage(Component.literal("§bStorm LB test: §f$name"))
            1
        }
        return ClientCommands.literal("stormlb")
            .then(ClientCommands.literal("archer").executes(set(ARCHER, "archer")))
            .then(ClientCommands.literal("healer").executes(set(HEALER, "healer")))
            .then(ClientCommands.literal("off").executes(set(null, "off")))
    }

    private fun spot(): Spot? = testSpot ?: when {
        DungeonClass.isClass(DungeonClass.ARCHER) -> ARCHER
        DungeonClass.isClass(DungeonClass.HEALER) -> HEALER
        else -> null
    }

    private fun render() {
        if (testSpot == null && (!Location.inDungeon() || !Phase.inP2() || Phase.stormDead())) return
        val player = Minecraft.getInstance().player ?: return
        val s = spot() ?: return
        val color = FishSettings.stormLbWaypointColor

        RenderUtils.gizmoBox(AABB(s.x - 0.3, s.y, s.z - 0.3, s.x + 0.3, s.y + 0.05, s.z + 0.3), color and 0x40FFFFFF, color, true)

        val aim = player.getEyePosition(Minecraft.getInstance().deltaTracker.getGameTimeDeltaPartialTick(true)).add(s.dir.scale(AIM_DIST))
        RenderUtils.gizmoBox(AABB(aim.x - 0.25, aim.y - 0.25, aim.z - 0.25, aim.x + 0.25, aim.y + 0.25, aim.z + 0.25), color and 0x60FFFFFF, color, true)
        RenderUtils.gizmoText(Component.literal("LB"), aim.add(0.0, 0.5, 0.0), 1f, color, true)
    }
}
