package fishmod.features.dungeon.f6

import fishmod.features.dungeon.map.DungeonState
import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.world.level.block.FlowerPotBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Terracotta Timer. On F6 Sadan's boss a terracotta dying makes Hypixel plant a flower-pot block
 * where it stood; the mob respawns 15s later (12s on Master Mode). Match `state.block is FlowerPotBlock`
 * — Hypixel can use a *potted* variant, not just the empty `Blocks.FLOWER_POT`, which the old
 * behaviour missed.
 */
object TerracottaTimer {

    private data class Terracotta(val pos: BlockPos, var time: Float)

    private val spawning = CopyOnWriteArrayList<Terracotta>()

    private fun active(): Boolean =
        FishSettings.terracottaTimerEnabled && DungeonState.isInBoss() && DungeonState.floorNumber() == 6

    @JvmStatic
    fun init() {
        Events.ON_PACKET.register { packet ->
            when (packet) {
                is ClientboundBlockUpdatePacket ->
                    Minecraft.getInstance().execute { onBlock(packet.pos, packet.blockState) }
                is ClientboundSectionBlocksUpdatePacket ->
                    Minecraft.getInstance().execute { packet.runUpdates(::onBlock) }
            }
            false
        }

        Events.ON_SERVER_TICK.register {
            spawning.removeIf { it.time -= 0.05f; it.time <= 0f }
            false
        }

        Events.ON_WORLD_CHANGE.register { spawning.clear(); false }

        RenderingEvents.GIZMO.register { _ ->
            if (!active() || spawning.isEmpty()) return@register
            for (t in spawning) {
                RenderUtils.gizmoText(
                    Component.literal("§${color(t.time)}%.1fs".format(t.time)),
                    Vec3(t.pos.x + 0.5, t.pos.y + 1.5, t.pos.z + 0.5), 1f, -0x1,
                )
            }
        }
    }

    private fun onBlock(pos: BlockPos, state: BlockState) {
        if (!active()) return
        if (state.block !is FlowerPotBlock) return
        if (spawning.any { it.pos == pos }) return
        spawning.add(Terracotta(pos.immutable(), if (DungeonState.isMasterMode()) 12f else 15f))
    }

    private fun color(time: Float): Char = when {
        time > 5f -> 'a'
        time > 2f -> '6'
        else -> 'c'
    }
}
