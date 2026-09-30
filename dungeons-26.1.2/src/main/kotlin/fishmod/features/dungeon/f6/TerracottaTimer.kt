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
import fishmod.utils.debug.FishDiag

object TerracottaTimer {

    private data class Terracotta(val pos: BlockPos, var time: Float)

    private val spawning = CopyOnWriteArrayList<Terracotta>()

    private fun active(): Boolean =
        FishSettings.terracottaTimerEnabled && DungeonState.isInBoss() && DungeonState.floorNumber() == 6

    @JvmStatic
    fun init() {
        Events.ON_PACKET.register { packet ->
            if (!active()) return@register false
            try {
                when (packet) {
                    is ClientboundBlockUpdatePacket -> onBlock(packet.pos, packet.blockState)
                    is ClientboundSectionBlocksUpdatePacket -> packet.runUpdates(::onBlock)
                }
            } catch (e: Exception) { FishDiag.fail("TerracottaTimer.1", "terracotta packet handler threw", e) }
            false
        }

        Events.ON_SERVER_TICK.register {
            spawning.removeIf { it.time -= 0.05f; it.time <= 0f }
            false
        }

        Events.ON_WORLD_CHANGE.register { spawning.clear(); false }

        RenderingEvents.GIZMO.register { _ ->
            if (!active() || spawning.isEmpty()) return@register
            try {
                for (t in spawning) {
                    RenderUtils.gizmoText(
                        Component.literal("§${color(t.time)}%.1fs".format(t.time)),
                        Vec3(t.pos.x + 0.5, t.pos.y + 1.5, t.pos.z + 0.5), 1f, -0x1,
                    )
                }
            } catch (e: Exception) { FishDiag.fail("TerracottaTimer.2", "terracotta render threw (n=${spawning.size})", e) }
        }
    }

    private fun onBlock(pos: BlockPos, state: BlockState) {
        if (!active()) return
        if (state.block !is FlowerPotBlock) return
        if (spawning.any { it.pos == pos }) return
        spawning.add(Terracotta(pos.immutable(), if (DungeonState.isMasterMode()) 12f else 15f))
        FishDiag.check(spawning.size <= 40, "TerracottaTimer.3") { "terracotta list grew to ${spawning.size}" }
    }

    private fun color(time: Float): Char = when {
        time > 5f -> 'a'
        time > 2f -> '6'
        else -> 'c'
    }
}
