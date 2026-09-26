package fishmod.features.dungeon.f7

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import fishmod.utils.Misc
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.Debug
import fishmod.utils.dungeon.Phase
import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.util.ARGB
import net.minecraft.world.InteractionResult
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.AABB

// F7 P3 Simon Says device solver — 1:1 port of Odin's SimonSays module.
object SimonSaysSolver {

    private val startButton = BlockPos(110, 121, 91)
    private val grid: Set<BlockPos> = buildSet {
        for (y in 120..123) for (z in 92..95) add(BlockPos(110, y, z))
    }

    private val clickInOrder = ArrayList<BlockPos>()
    private var clickNeeded = 0
    private var firstPhase = true
    private var lastLanternTick = -1
    private val prev = HashMap<Long, Block>()

    private fun resetSolution() {
        clickInOrder.clear()
        clickNeeded = 0
        lastLanternTick = -1
    }

    private fun inP3(): Boolean = FishSettings.simonSolverEnabled && try { Phase.inP3() } catch (t: Throwable) { false }

    private fun trackingActive(): Boolean = try { Phase.inP3() } catch (t: Throwable) { false }

    @JvmField var lastRoundCompleteMs: Long = 0L

    private fun dbg(msg: String) {
        if (Debug.ssDebug) Misc.addChatMessage(Component.literal("§7[SS] §f$msg"))
    }

    private fun powered(state: BlockState): Boolean =
        state.hasProperty(BlockStateProperties.POWERED) && state.getValue(BlockStateProperties.POWERED)

    @JvmStatic
    fun init() {
        Events.ON_WORLD_CHANGE.register {
            Minecraft.getInstance().execute {
                resetSolution()
                firstPhase = true
                prev.clear()
                lastRoundCompleteMs = 0L
            }
            false
        }

        Events.ON_PACKET.register { packet ->
            when (packet) {
                is ClientboundBlockUpdatePacket -> queueBlock(packet.pos, packet.blockState)
                is ClientboundSectionBlocksUpdatePacket -> packet.runUpdates(::queueBlock)
            }
            false
        }

        Events.ON_SERVER_TICK.register {
            tick()
            false
        }

        UseBlockCallback.EVENT.register(UseBlockCallback { _, _, _, hit ->
            val pos = hit.blockPos
            if (!inP3()) return@UseBlockCallback InteractionResult.PASS

            if (pos == startButton) {
                Minecraft.getInstance().execute { resetSolution(); firstPhase = true }
                return@UseBlockCallback InteractionResult.PASS
            }

            if (pos.x != 110 || pos.y !in 120..123 || pos.z !in 92..95)
                return@UseBlockCallback InteractionResult.PASS
            val lantern = pos.east()

            if (FishSettings.simonSolverBlockWrong &&
                Minecraft.getInstance().player?.isShiftKeyDown == false &&
                lantern != clickInOrder.getOrNull(clickNeeded)
            ) return@UseBlockCallback InteractionResult.FAIL

            val idx = clickInOrder.indexOf(lantern)
            if (idx >= 0) {
                clickNeeded = idx + 1
                dbg("click ${pos.y}:${pos.z} -> clickNeeded=$clickNeeded")
                if (clickNeeded >= clickInOrder.size) {
                    lastRoundCompleteMs = System.currentTimeMillis()
                    resetSolution(); firstPhase = false
                }
            }
            InteractionResult.PASS
        })

        RenderingEvents.GIZMO.register { _ -> if (!FishSettings.simonSolverDepth) renderGizmo() }
        RenderingEvents.NO_DEPTH_FILLED.register { _, m, vc -> if (FishSettings.simonSolverDepth) render(m, vc, fill = true) }
        RenderingEvents.NO_DEPTH_LINE.register { _, m, vc -> if (FishSettings.simonSolverDepth) render(m, vc, fill = false) }
    }

    private fun queueBlock(pos: BlockPos, state: BlockState) {
        onBlock(pos.immutable(), state)
    }

    private fun tick() {
        if (!trackingActive() || !firstPhase) return
        lastLanternTick++
        if (lastLanternTick > 10 && grid.count { Minecraft.getInstance().level?.getBlockState(it)?.block === Blocks.STONE_BUTTON } > 8) {
            dbg("grid reset detected (${clickInOrder.size})")
            firstPhase = false
        }
    }

    private fun onBlock(pos: BlockPos, updated: BlockState) {
        if (!trackingActive()) return

        val old = prev.put(pos.asLong(), updated.block)

        if (pos == startButton && updated.block === Blocks.STONE_BUTTON && powered(updated)) {
            resetSolution()
            firstPhase = true
            return
        }

        if (pos.y !in 120..123 || pos.z !in 92..95) return

        when (pos.x) {
            111 ->
                if (updated.block === Blocks.OBSIDIAN && old === Blocks.SEA_LANTERN && pos !in clickInOrder) {
                    clickInOrder.add(pos.immutable())
                    lastLanternTick = 0
                    dbg("lantern ${pos.y}:${pos.z} added (${clickInOrder.size})")
                    if (!firstPhase) return
                    when (clickInOrder.size) {
                        2 -> { clickInOrder.reverse(); dbg("first-phase correction: size 2 -> reverse") }
                        3 -> { clickInOrder.removeAt(clickInOrder.lastIndex - 1); dbg("first-phase correction: size 3 -> drop middle") }
                    }
                }

            110 ->
                if (updated.block !== Blocks.AIR && old === Blocks.STONE_BUTTON && powered(updated)) {
                    clickNeeded = clickInOrder.indexOf(pos.east()) + 1
                    dbg("click ${pos.y}:${pos.z} -> clickNeeded=$clickNeeded")
                    if (clickNeeded >= clickInOrder.size) {
                        lastRoundCompleteMs = System.currentTimeMillis()
                        resetSolution(); firstPhase = false
                    }
                }
        }
    }

    private data class Btn(val box: AABB, val color: Int)

    private fun buttons(): List<Btn> {
        if (!inP3() || clickNeeded >= clickInOrder.size) return emptyList()
        return (clickNeeded until clickInOrder.size).map { index ->
            val p = clickInOrder[index]
            val box = AABB(
                p.x + 0.05, p.y + 0.37, p.z + 0.3,
                p.x - 0.15, p.y + 0.63, p.z + 0.7
            )
            val color = when (index) {
                clickNeeded -> FishSettings.simonSolverColor1
                clickNeeded + 1 -> FishSettings.simonSolverColor2
                else -> FishSettings.simonSolverColor3
            }
            Btn(box, color)
        }
    }

    private fun renderGizmo() {
        for ((box, color) in buttons()) RenderUtils.gizmoBox(box, ARGB.multiplyAlpha(color, 0.5f), ARGB.opaque(color))
    }

    private fun render(matrices: PoseStack, vc: VertexConsumer, fill: Boolean) {
        for ((box, color) in buttons()) {
            val rgba = RenderUtils.toFloats(color)
            if (fill) RenderUtils.renderFilled(matrices, vc, box, floatArrayOf(rgba[0], rgba[1], rgba[2], rgba[3] * 0.5f))
            else RenderUtils.renderOutline(matrices, vc, box, floatArrayOf(rgba[0], rgba[1], rgba[2], 1f))
        }
    }
}
