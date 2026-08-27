package fishmod.features.dungeon.f6

import fishmod.features.dungeon.map.DungeonState
import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Terracotta Timer (ported from Odin's TerracottaTimer). On F6 Sadan's boss, when a terracotta dies
 * Hypixel drops an empty flower pot where it stood and respawns the mob ~15s later (~12s on Master
 * Mode). We watch block-update packets for those pots and float the countdown in-world.
 */
object TerracottaTimer {

    private class Terra(val pos: Vec3, var time: Float)

    private val spawning = CopyOnWriteArrayList<Terra>()

    private fun active(): Boolean =
        FishSettings.terracottaTimerEnabled && DungeonState.isInBoss() && DungeonState.floorNumber() == 6

    @JvmStatic
    fun init() {
        Events.ON_PACKET.register { packet ->
            when (packet) {
                is ClientboundBlockUpdatePacket -> onBlock(packet.pos, packet.blockState)
                is ClientboundSectionBlocksUpdatePacket -> packet.runUpdates(::onBlock)
            }
            false
        }

        Events.ON_SERVER_TICK.register {
            spawning.removeIf { it.time -= 0.05f; it.time <= 0f }
            false
        }

        Events.ON_WORLD_CHANGE.register { spawning.clear(); false }

        // Text via the RenderingEvents (END_MAIN) pass — see PuzzleSolvers note.
        RenderingEvents.NO_DEPTH_LINE.register { ctx, matrices, _ ->
            if (!active() || spawning.isEmpty()) return@register
            for (t in spawning) {
                RenderUtils.renderText(ctx, matrices, Component.literal("${"%.1f".format(t.time)}s"), t.pos, 0.03f)
            }
        }
    }

    private fun onBlock(pos: BlockPos, state: BlockState) {
        if (!state.`is`(Blocks.FLOWER_POT)) return
        if (!active()) return
        val at = Vec3(pos.x + 0.5, pos.y + 1.5, pos.z + 0.5)
        if (spawning.any { it.pos.distanceToSqr(at) < 0.01 }) return
        spawning.add(Terra(at, if (DungeonState.isMasterMode()) 12f else 15f))
        fishmod.utils.Misc.addChatMessage(Component.literal("§6[Terracotta] §7pot at ${pos.x},${pos.y},${pos.z} — ${spawning.last().time}s"))
    }
}
