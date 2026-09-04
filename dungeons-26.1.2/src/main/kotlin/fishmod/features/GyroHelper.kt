package fishmod.features

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import fishmod.utils.config.values.FishSettings
import fishmod.utils.data.ItemUtil
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.minecraft.client.Minecraft
import net.minecraft.tags.BlockTags
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

/**
 * Gyrokinetic Wand landing helper. While holding the wand, raytraces up to 25 blocks and, if it
 * lands on a valid surface, draws a box on the target block +
 * a 10-block-radius "sucking range" ring.
 */
object GyroHelper {

    private const val SEGMENTS = 48

    @JvmStatic
    fun init() {
        RenderingEvents.NO_DEPTH_LINE.register { _, m, vc -> render(m, vc) }
    }

    private fun render(matrices: PoseStack, vc: VertexConsumer) {
        if (!FishSettings.gyroHelperEnabled) return
        val mc = Minecraft.getInstance()
        val p = mc.player ?: return
        val level = mc.level ?: return
        if (ItemUtil.getId(p.mainHandItem) != "GYROKINETIC_WAND") return

        val hit = p.pick(25.0, 0f, false) as? BlockHitResult ?: return
        if (hit.type != HitResult.Type.BLOCK) return
        val pos = hit.blockPos
        val here = level.getBlockState(pos)
        val above = level.getBlockState(pos.above())
        if (here.isAir) return
        if (!above.isAir && !above.`is`(BlockTags.WOOL_CARPETS)) return

        val cx = pos.x + 0.5
        val cz = pos.z + 0.5
        val boxColor = RenderUtils.toFloats(FishSettings.gyroBoxColor)
        val ringColor = RenderUtils.toFloats(FishSettings.gyroRingColor)

        RenderUtils.renderOutline(matrices, vc,
            AABB(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(), pos.x + 1.0, pos.y + 1.0, pos.z + 1.0),
            boxColor)

        val ry = pos.y + 2.05
        var prev: Vec3? = null
        for (i in 0..SEGMENTS) {
            val a = i.toDouble() / SEGMENTS * Math.PI * 2
            val cur = Vec3(cx + cos(a) * 10.0, ry, cz + sin(a) * 10.0)
            if (prev != null) RenderUtils.renderLine(matrices, vc, prev, cur, ringColor)
            prev = cur
        }
    }
}
