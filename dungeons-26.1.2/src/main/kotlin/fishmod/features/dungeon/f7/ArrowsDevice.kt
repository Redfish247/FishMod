package fishmod.features.dungeon.f7

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
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import java.util.regex.Pattern

/**
 * Arrows / "Sharp Shooter" device solver (F7 P3), ported from Odin's `ArrowsDevice` — distinct from
 * Arrow Align. The 3x3 grid of blocks sits at fixed world coords (P3 orientation is constant): an
 * emerald block is a live target to shoot, a terracotta block is one already hit. Boxes the targets
 * (purple) and the hit blocks (aqua), and titles when the device completes.
 *
 * Odin's optimal aim-position maths is intentionally left out (off by default there too).
 */
object ArrowsDevice {

    private val POSITIONS = listOf(
        BlockPos(68, 130, 50), BlockPos(66, 130, 50), BlockPos(64, 130, 50),
        BlockPos(68, 128, 50), BlockPos(66, 128, 50), BlockPos(64, 128, 50),
        BlockPos(68, 126, 50), BlockPos(66, 126, 50), BlockPos(64, 126, 50),
    )
    private val CENTER = net.minecraft.world.phys.Vec3(66.0, 128.0, 50.0)
    private val COMPLETE = Pattern.compile("^(.{1,16}) completed a device! \\((\\d)/(\\d)\\)")

    @Volatile private var targets: List<BlockPos> = emptyList()
    @Volatile private var marked: List<BlockPos> = emptyList()
    private var announced = false

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc -> tick(mc) }

        Events.ON_GAME_MESSAGE.register { text ->
            if (!FishSettings.arrowsDeviceEnabled || !FishSettings.arrowsDeviceCompleteAlert) return@register false
            val m = COMPLETE.matcher(text.string.replace(Regex("§."), ""))
            if (m.find() && m.group(1) == mc().player?.gameProfile?.name && !announced && Phase.inP3()) {
                announced = true
                Misc.forceTitle(Component.literal("§aDevice Complete"), Component.empty())
            }
            false
        }
        Events.ON_WORLD_CHANGE.register { announced = false; targets = emptyList(); marked = emptyList(); false }

        RenderingEvents.FILLED_BLOCK.register { _, m, vc -> if (!FishSettings.arrowsDeviceDepth) render(m, vc, fill = true) }
        RenderingEvents.LINE.register { _, m, vc -> if (!FishSettings.arrowsDeviceDepth) render(m, vc, fill = false) }
        RenderingEvents.NO_DEPTH_FILLED.register { _, m, vc -> if (FishSettings.arrowsDeviceDepth) render(m, vc, fill = true) }
        RenderingEvents.NO_DEPTH_LINE.register { _, m, vc -> if (FishSettings.arrowsDeviceDepth) render(m, vc, fill = false) }
    }

    private fun mc() = Minecraft.getInstance()

    private fun tick(mc: Minecraft) {
        if (!FishSettings.arrowsDeviceEnabled || !Phase.inP3()) {
            targets = emptyList(); marked = emptyList(); return
        }
        val level = mc.level ?: return
        val p = mc.player ?: return
        if (p.position().distanceToSqr(CENTER) > 40.0 * 40.0) {
            targets = emptyList(); marked = emptyList(); return
        }
        val states = POSITIONS.map { it to level.getBlockState(it) }
        // Grid not rendered yet if any slot is still air — wait for a full 3x3.
        if (states.any { it.second.isAir }) { targets = emptyList(); marked = emptyList(); return }
        targets = states.filter { it.second.`is`(Blocks.EMERALD_BLOCK) }.map { it.first }
        marked = states.filter { !it.second.`is`(Blocks.EMERALD_BLOCK) }.map { it.first }
    }

    private fun render(matrices: com.mojang.blaze3d.vertex.PoseStack, vc: com.mojang.blaze3d.vertex.VertexConsumer, fill: Boolean) {
        if (!FishSettings.arrowsDeviceEnabled) return
        if (targets.isEmpty() && marked.isEmpty()) return
        drawAll(matrices, vc, fill, marked, FishSettings.arrowsDeviceMarkedColor)
        drawAll(matrices, vc, fill, targets, FishSettings.arrowsDeviceTargetColor)
    }

    private fun drawAll(
        matrices: com.mojang.blaze3d.vertex.PoseStack, vc: com.mojang.blaze3d.vertex.VertexConsumer,
        fill: Boolean, list: List<BlockPos>, color: Int,
    ) {
        if (list.isEmpty()) return
        val rgba = RenderUtils.toFloats(color)
        for (pos in list) {
            val box = AABB(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(), pos.x + 1.0, pos.y + 1.0, pos.z + 1.0).inflate(0.003)
            if (fill) RenderUtils.renderFilled(matrices, vc, box, floatArrayOf(rgba[0], rgba[1], rgba[2], rgba[3] * 0.4f))
            else RenderUtils.renderOutline(matrices, vc, box, floatArrayOf(rgba[0], rgba[1], rgba[2], 1f))
        }
    }
}
