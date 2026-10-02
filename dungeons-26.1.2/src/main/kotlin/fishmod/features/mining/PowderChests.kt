package fishmod.features.mining

import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.concurrent.ConcurrentHashMap
import fishmod.features.mining.MiningSettings as S

// Crystal Hollows treasure (powder) chests: box + line to each one spawned near you
object PowderChests {

    private const val MAX_DIST_SQ = 15.0 * 15.0
    private const val TTL_MS = 60_000L
    private val chests = ConcurrentHashMap<BlockPos, Long>()

    fun init() {
        Events.ON_PACKET.register { packet ->
            if (packet is ClientboundBlockUpdatePacket && S.miningChests && Mining.inHollows()) onBlock(packet.pos, packet.blockState)
            false
        }
        Events.ON_WORLD_CHANGE.register { chests.clear(); false }
        RenderingEvents.NO_DEPTH_FILLED.register { _, ps, vc ->
            if (!S.miningChests || chests.isEmpty()) return@register
            val now = System.currentTimeMillis()
            chests.entries.removeIf { now - it.value > TTL_MS }
            val start = Mining.lineStart()
            for (p in chests.keys) {
                val b = AABB(p.x + 0.0625, p.y.toDouble(), p.z + 0.0625, p.x + 0.9375, p.y + 0.875, p.z + 0.9375)
                RenderUtils.fillBox(ps, vc, b, Mining.alpha(S.miningChestColor, S.miningChestOpacity))
                Mining.outline(ps, vc, b, Mining.alpha(S.miningChestColor, 100), 2f)
                if (S.miningChestLine) RenderUtils.screenLine(ps, vc, start, Vec3.atCenterOf(p), Mining.alpha(S.miningChestColor, 100), S.miningChestLineWidth.toFloat())
            }
        }
    }

    private fun onBlock(pos: BlockPos, st: BlockState) {
        val p = Minecraft.getInstance().player ?: return
        if (st.`is`(Blocks.CHEST)) {
            if (p.position().distanceToSqr(Vec3.atCenterOf(pos)) <= MAX_DIST_SQ) chests[pos.immutable()] = System.currentTimeMillis()
        } else chests.remove(pos)
    }
}
