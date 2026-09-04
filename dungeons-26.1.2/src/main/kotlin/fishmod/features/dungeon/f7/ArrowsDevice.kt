package fishmod.features.dungeon.f7

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import fishmod.utils.Location
import fishmod.utils.Misc
import fishmod.utils.config.values.FishSettings
import fishmod.utils.dungeon.Phase
import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

/**
 * Arrows / "Sharp Shooter" device solver (F7 P3).
 *
 * Event-driven off block updates in the 3x3 device grid: EMERALD_BLOCK -> BLUE_TERRACOTTA means that
 * block was just hit (added to [markedPositions]); BLUE_TERRACOTTA -> EMERALD_BLOCK means it reset
 * and is now the live [targetPosition]. Optional "Show Aim Positions" runs an adjacent-pair
 * midpoint optimiser to suggest where to stand/aim so one shot covers two blocks.
 */
object ArrowsDevice {

    private val devicePositions = listOf(
        BlockPos(68, 130, 50), BlockPos(66, 130, 50), BlockPos(64, 130, 50),
        BlockPos(68, 128, 50), BlockPos(66, 128, 50), BlockPos(64, 128, 50),
        BlockPos(68, 126, 50), BlockPos(66, 126, 50), BlockPos(64, 126, 50),
    )
    private val roomBoundingBox = AABB(20.0, 100.0, 30.0, 89.0, 151.0, 51.0)
    private val deviceCompleteRegex = Regex("^(.{1,16}) completed a device! \\((\\d)/(\\d)\\)$")

    private val markedPositions = mutableSetOf<BlockPos>()
    private var targetPosition: BlockPos? = null
    private var isDeviceComplete = false
    private var optimalAimPositions: List<AimPosition> = emptyList()
    // reconstructs the old->new transition; concurrent (written from the packet thread)
    private val prev = java.util.concurrent.ConcurrentHashMap<Long, Block>()

    private data class AimPosition(val position: Vec3, val coveredBlocks: Set<BlockPos>, val distance: Double)

    private val adjacentPairs: List<Pair<BlockPos, BlockPos>> by lazy {
        devicePositions.flatMapIndexed { i, b1 ->
            devicePositions.drop(i + 1).mapNotNull { b2 ->
                if (abs(b1.x - b2.x) == 2 && b1.y == b2.y && b1.z == b2.z) b1 to b2 else null
            }
        }
    }

    private fun inP3(): Boolean =
        (try { Phase.inP3() } catch (t: Throwable) { false }) ||
            (Location.inDungeon() && Minecraft.getInstance().player?.let { it.y in 100.0..156.0 } == true)

    private val isPlayerInRoom: Boolean
        get() = Minecraft.getInstance().player?.let { roomBoundingBox.contains(it.position()) } == true

    private fun resetSolver() {
        markedPositions.clear()
        targetPosition = null
        optimalAimPositions = emptyList()
    }

    @JvmStatic
    fun init() {
        Events.ON_PACKET.register { packet ->
            when (packet) {
                is ClientboundBlockUpdatePacket ->
                    Minecraft.getInstance().execute { onBlock(packet.pos, packet.blockState) }
                is ClientboundSectionBlocksUpdatePacket ->
                    Minecraft.getInstance().execute { packet.runUpdates(::onBlock) }
                is ClientboundSetEntityDataPacket -> onEntityData(packet)
            }
            false
        }

        Events.ON_WORLD_CHANGE.register {
            resetSolver(); isDeviceComplete = false; prev.clear(); false
        }

        Events.ON_GAME_MESSAGE.register { text ->
            if (!FishSettings.arrowsDeviceEnabled || !inP3() || !isPlayerInRoom || isDeviceComplete) return@register false
            val name = deviceCompleteRegex.find(text.string.replace(fishmod.utils.Constants.STRIP_COLOR_REGEX, ""))?.groupValues?.get(1)
            if (name == Minecraft.getInstance().player?.gameProfile?.name) onComplete("Chat")
            false
        }

        // Occluded (default) vs through-walls, per arrowsDeviceDepth.
        RenderingEvents.GIZMO.register { _ -> if (!FishSettings.arrowsDeviceDepth) drawGizmo() }
        RenderingEvents.NO_DEPTH_FILLED.register { _, m, vc -> if (FishSettings.arrowsDeviceDepth) draw(m, vc) }

        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (!FishSettings.arrowsDeviceShowAim) optimalAimPositions = emptyList()
            // seed [prev] with every device block's live state so the first transition after P3 isn't dropped
            if (FishSettings.arrowsDeviceEnabled && inP3()) {
                val level = mc.level ?: return@register
                for (p in devicePositions) {
                    val k = p.asLong()
                    if (!prev.containsKey(k)) prev[k] = level.getBlockState(p).block
                }
            }
        }
    }

    private fun onBlock(pos: BlockPos, state: BlockState) {
        if (!FishSettings.arrowsDeviceEnabled || !inP3() || pos !in devicePositions) {
            if (pos in devicePositions) prev[pos.asLong()] = state.block
            return
        }
        val old = prev.put(pos.asLong(), state.block)

        if (old == Blocks.EMERALD_BLOCK && state.`is`(Blocks.BLUE_TERRACOTTA)) {
            markedPositions.add(pos.immutable())
            if (targetPosition == pos) targetPosition = null
            if (FishSettings.arrowsDeviceShowAim) optimalAimPositions = calculateOptimalAimPositions(pos)
        } else if (old == Blocks.BLUE_TERRACOTTA && state.`is`(Blocks.EMERALD_BLOCK)) {
            markedPositions.remove(pos)
            targetPosition = pos.immutable()
            if (FishSettings.arrowsDeviceShowAim) optimalAimPositions = calculateOptimalAimPositions(pos)
        }
    }

    private fun onEntityData(packet: ClientboundSetEntityDataPacket) {
        if (!FishSettings.arrowsDeviceEnabled || !inP3() || !isPlayerInRoom || isDeviceComplete) return
        if (Minecraft.getInstance().level?.getEntity(packet.id)?.name?.string == "Active") onComplete("Entity")
    }

    private fun onComplete(method: String) {
        isDeviceComplete = true
        if (FishSettings.arrowsDeviceCompleteAlert) {
            Misc.addChatMessage(Component.literal("§aSharp shooter device complete §7($method)"))
            Misc.forceTitle(Component.literal("§aDevice Complete"), Component.empty())
        }
        resetSolver()
    }

    private fun aimColors() = listOf(
        FishSettings.arrowsDeviceAim1Color, FishSettings.arrowsDeviceAim2Color, FishSettings.arrowsDeviceAim3Color,
    )

    private fun drawGizmo() {
        if (!FishSettings.arrowsDeviceEnabled || !inP3()) return
        for (p in markedPositions) RenderUtils.gizmoBox(AABB(p), FishSettings.arrowsDeviceMarkedColor, 0)
        targetPosition?.let { RenderUtils.gizmoBox(AABB(it), FishSettings.arrowsDeviceTargetColor, 0) }
        if (FishSettings.arrowsDeviceShowAim) {
            val cols = aimColors()
            optimalAimPositions.take(3).forEachIndexed { i, aim ->
                RenderUtils.gizmoBox(AABB.unitCubeFromLowerCorner(aim.position.add(-0.5, -0.5, -0.1)), cols[i], 0)
            }
        }
    }

    private fun draw(matrices: PoseStack, vc: VertexConsumer) {
        if (!FishSettings.arrowsDeviceEnabled || !inP3()) return
        for (p in markedPositions) fill(matrices, vc, AABB(p), FishSettings.arrowsDeviceMarkedColor)
        targetPosition?.let { fill(matrices, vc, AABB(it), FishSettings.arrowsDeviceTargetColor) }
        if (FishSettings.arrowsDeviceShowAim) {
            val cols = aimColors()
            optimalAimPositions.take(3).forEachIndexed { i, aim ->
                fill(matrices, vc, AABB.unitCubeFromLowerCorner(aim.position.add(-0.5, -0.5, -0.1)), cols[i])
            }
        }
    }

    private fun fill(matrices: PoseStack, vc: VertexConsumer, box: AABB, color: Int) {
        val rgba = RenderUtils.toFloats(color)
        RenderUtils.renderFilled(matrices, vc, box, rgba)
    }

    private fun calculateOptimalAimPositions(target: BlockPos): List<AimPosition> {
        val unmarked = devicePositions.filterNot { it in markedPositions }

        fun createAim(b1: BlockPos, b2: BlockPos): AimPosition? =
            listOf(b1, b2).filter { it in unmarked }.takeIf { it.isNotEmpty() }?.let {
                AimPosition(b1.midpoint(b2), it.toSet(), 0.0)
            }

        val greenAim = adjacentPairs
            .filter { (b1, b2) -> target == b1 || target == b2 }
            .mapNotNull { (b1, b2) -> createAim(b1, b2) }
            .maxByOrNull { it.coveredBlocks.size } ?: return emptyList()

        val remaining = adjacentPairs
            .filterNot { (b1, b2) -> target == b1 || target == b2 }
            .mapNotNull { (b1, b2) -> createAim(b1, b2) }

        return findBestCombination(greenAim, remaining)
    }

    private fun findBestCombination(greenAim: AimPosition, aimPositions: List<AimPosition>): List<AimPosition> {
        val result = mutableListOf(greenAim)
        val covered = greenAim.coveredBlocks.toMutableSet()

        repeat(2) {
            val best = aimPositions.filterNot { it in result }
                .maxWithOrNull(compareBy(
                    { it.coveredBlocks.count { b -> b !in covered } },
                    { it.coveredBlocks.size },
                    { -(result.last().position.distanceTo(it.position)) },
                )) ?: return@repeat
            result.add(best)
            covered.addAll(best.coveredBlocks)
        }

        return result.mapIndexed { index, aim ->
            AimPosition(aim.position, aim.coveredBlocks,
                if (index == 0) 0.0 else aim.position.distanceTo(result[index - 1].position))
        }
    }

    private fun BlockPos.midpoint(other: BlockPos): Vec3 =
        Vec3((x + other.x) / 2.0 + 0.5, y.toDouble() + 0.5, z.toDouble() + 0.5)
}
