package fishmod.features.dungeon.f7

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import fishmod.utils.config.values.FishSettings
import fishmod.utils.dungeon.Phase
import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.AABB

/**
 * F7 P3 Simon Says device solver, ported from Odin's `SimonSays` ("optimized solution" path).
 *
 * The demo lights sea-lanterns behind the buttons (x=111) one at a time — that flash order is the
 * click order. Each button press (x=110, stone button powered) advances [clickNeeded]. Boxes the
 * buttons still to press: green next, gold the one after, red the rest. Resets when the sequence is
 * exhausted or the start button (110,121,91) is pressed.
 */
object SimonSaysSolver {

    private val START = BlockPos(110, 121, 91)
    private val clickInOrder = ArrayList<BlockPos>()
    private var clickNeeded = 0
    private val prev = HashMap<Long, Block>()

    private fun enabled(): Boolean = FishSettings.simonSolverEnabled && safeInP3()
    private fun safeInP3(): Boolean = try { Phase.inP3() } catch (t: Throwable) { false }

    private fun reset() { clickInOrder.clear(); clickNeeded = 0 }

    @JvmStatic
    fun init() {
        Events.ON_PACKET.register { packet ->
            when (packet) {
                is ClientboundBlockUpdatePacket -> onBlock(packet.pos, packet.blockState)
                is ClientboundSectionBlocksUpdatePacket -> packet.runUpdates(::onBlock)
            }
            false
        }
        Events.ON_WORLD_CHANGE.register { reset(); prev.clear(); false }

        RenderingEvents.FILLED_BLOCK.register { _, m, vc -> if (!FishSettings.simonSolverDepth) render(m, vc, fill = true) }
        RenderingEvents.LINE.register { _, m, vc -> if (!FishSettings.simonSolverDepth) render(m, vc, fill = false) }
        RenderingEvents.NO_DEPTH_FILLED.register { _, m, vc -> if (FishSettings.simonSolverDepth) render(m, vc, fill = true) }
        RenderingEvents.NO_DEPTH_LINE.register { _, m, vc -> if (FishSettings.simonSolverDepth) render(m, vc, fill = false) }
    }

    private fun powered(state: BlockState): Boolean =
        state.hasProperty(BlockStateProperties.POWERED) && state.getValue(BlockStateProperties.POWERED)

    private fun onBlock(pos: BlockPos, state: BlockState) {
        if (!enabled()) return
        val key = pos.asLong()
        val old = prev.put(key, state.block)

        if (pos.x == START.x && pos.y == START.y && pos.z == START.z) {
            if (state.`is`(Blocks.STONE_BUTTON) && powered(state)) reset()
            return
        }
        if (pos.y !in 120..123 || pos.z !in 92..95) return

        when (pos.x) {
            111 -> {
                // Demo flash: obsidian → lit lantern. That position, in order, is a click.
                if (state.`is`(Blocks.SEA_LANTERN) && old == Blocks.OBSIDIAN) {
                    val p = pos.immutable()
                    if (p !in clickInOrder) clickInOrder.add(p)
                }
            }
            110 -> {
                if (old == Blocks.STONE_BUTTON && state.`is`(Blocks.STONE_BUTTON) && powered(state)) {
                    val idx = clickInOrder.indexOf(pos.immutable().east())
                    if (idx >= 0) {
                        clickNeeded = idx + 1
                        if (clickNeeded >= clickInOrder.size) reset()
                    }
                }
            }
        }
    }

    private fun render(matrices: PoseStack, vc: VertexConsumer, fill: Boolean) {
        if (!enabled() || clickNeeded >= clickInOrder.size) return
        for (i in clickNeeded until clickInOrder.size) {
            val p = clickInOrder[i]
            val box = AABB(
                p.x - 0.15, p.y + 0.37, p.z + 0.3,
                p.x + 0.05, p.y + 0.63, p.z + 0.7,
            )
            val color = when (i) {
                clickNeeded -> FishSettings.simonSolverColor1
                clickNeeded + 1 -> FishSettings.simonSolverColor2
                else -> FishSettings.simonSolverColor3
            }
            val rgba = RenderUtils.toFloats(color)
            if (fill) RenderUtils.renderFilled(matrices, vc, box, floatArrayOf(rgba[0], rgba[1], rgba[2], rgba[3] * 0.5f))
            else RenderUtils.renderOutline(matrices, vc, box, floatArrayOf(rgba[0], rgba[1], rgba[2], 1f))
        }
    }
}
