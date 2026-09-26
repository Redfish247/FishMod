package fishmod.features

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import fishmod.utils.data.ItemUtil
import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import fishmod.utils.sound.SoundManager
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.ARGB
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

object EtherwarpHelper {

    private val ITEMS = setOf("ASPECT_OF_THE_VOID", "ASPECT_OF_THE_END", "ETHERWARP_CONDUIT")

    private const val ETHERWARP_PITCH = 0.53968257f

    @Volatile private var target: BlockPos? = null
    @Volatile private var valid = false
    private var lastCue = 0L

    private fun holdingEtherItem(): Boolean {
        val p = Minecraft.getInstance().player ?: return false
        return ItemUtil.getId(p.mainHandItem) in ITEMS
    }

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            target = null
            if (!FishSettings.etherwarpHelperEnabled || !FishSettings.etherwarpShowGuess) return@register
            if (!Location.inSkyblock()) return@register
            val p = mc.player ?: return@register
            if (!p.isShiftKeyDown || !holdingEtherItem()) return@register
            raycast(mc)
        }

        Events.ON_SOUND.register { event, volume, pitch ->
            if (!FishSettings.etherwarpHelperEnabled || !FishSettings.etherwarpSoundEnabled) return@register false
            if (volume != 1f || pitch != ETHERWARP_PITCH || !Location.inSkyblock()) return@register false
            val looksRight = event == SoundEvents.ENDER_DRAGON_HURT ||
                event.location.path.let { it.contains("dragon") && (it.contains("hurt") || it.contains("hit")) } ||
                holdingEtherItem()
            if (!looksRight) return@register false
            val now = System.currentTimeMillis()
            if (now - lastCue < 150L) return@register true
            lastCue = now
            val vol = FishSettings.etherwarpSoundVolume.coerceIn(0, 500) / 100f
            val pit = FishSettings.etherwarpSoundPitch.toFloat().coerceIn(0.5f, 2f)
            fishmod.utils.Misc.sendSound(SoundManager.preset(FishSettings.etherwarpSoundName), vol, pit)
            true
        }

        RenderingEvents.GIZMO.register { _ -> if (!FishSettings.etherwarpDepth) renderGizmo() }
        RenderingEvents.NO_DEPTH_FILLED.register { _, m, vc -> if (FishSettings.etherwarpDepth) render(m, vc, fill = true) }
        RenderingEvents.NO_DEPTH_LINE.register { _, m, vc -> if (FishSettings.etherwarpDepth) render(m, vc, fill = false) }
    }

    private fun raycast(mc: Minecraft) {
        val p = mc.player ?: return
        val level = mc.level ?: return
        val start: Vec3 = p.getEyePosition(1f)
        val dir: Vec3 = p.getViewVector(1f).normalize()
        val max = FishSettings.etherwarpRange.coerceIn(1, 61).toDouble()

        var x = Math.floor(start.x).toInt()
        var y = Math.floor(start.y).toInt()
        var z = Math.floor(start.z).toInt()
        val stepX = if (dir.x > 0) 1 else -1
        val stepY = if (dir.y > 0) 1 else -1
        val stepZ = if (dir.z > 0) 1 else -1
        val deltaX = if (dir.x == 0.0) Double.MAX_VALUE else Math.abs(1.0 / dir.x)
        val deltaY = if (dir.y == 0.0) Double.MAX_VALUE else Math.abs(1.0 / dir.y)
        val deltaZ = if (dir.z == 0.0) Double.MAX_VALUE else Math.abs(1.0 / dir.z)
        var tMaxX = if (dir.x == 0.0) Double.MAX_VALUE else (if (stepX > 0) x + 1 - start.x else start.x - x) * deltaX
        var tMaxY = if (dir.y == 0.0) Double.MAX_VALUE else (if (stepY > 0) y + 1 - start.y else start.y - y) * deltaY
        var tMaxZ = if (dir.z == 0.0) Double.MAX_VALUE else (if (stepZ > 0) z + 1 - start.z else start.z - z) * deltaZ
        val pos = BlockPos.MutableBlockPos()
        var t = 0.0
        while (t <= max) {
            pos.set(x, y, z)
            val state = level.getBlockState(pos)
            if (!state.isAir && !state.getCollisionShape(level, pos).isEmpty) {
                val bp = pos.immutable()
                target = bp
                valid = level.getBlockState(bp.above()).getCollisionShape(level, bp.above()).isEmpty &&
                    level.getBlockState(bp.above(2)).getCollisionShape(level, bp.above(2)).isEmpty
                return
            }
            if (tMaxX < tMaxY && tMaxX < tMaxZ) { x += stepX; t = tMaxX; tMaxX += deltaX }
            else if (tMaxY < tMaxZ) { y += stepY; t = tMaxY; tMaxY += deltaY }
            else { z += stepZ; t = tMaxZ; tMaxZ += deltaZ }
        }
    }

    private val FULL = AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)

    private fun guessBoxes(): List<AABB>? {
        if (!FishSettings.etherwarpHelperEnabled || !FishSettings.etherwarpShowGuess) return null
        val bp = target ?: return null
        if (!valid && !FishSettings.etherwarpShowFail) return null
        val lvl = Minecraft.getInstance().level ?: return null

        val local: List<AABB> = if (FishSettings.etherwarpFullBlock) {
            listOf(FULL)
        } else {
            val st = lvl.getBlockState(bp)
            var shape = st.getShape(lvl, bp)
            if (shape.isEmpty) shape = st.getCollisionShape(lvl, bp)
            if (shape.isEmpty) listOf(FULL) else shape.toAabbs()
        }
        return local.map { it.move(bp.x.toDouble(), bp.y.toDouble(), bp.z.toDouble()).inflate(0.002) }
    }

    private fun guessColor(): Int = if (valid) FishSettings.etherwarpColor else FishSettings.etherwarpFailColor

    private fun renderGizmo() {
        val boxes = guessBoxes() ?: return
        val color = guessColor()
        val fillArgb = ARGB.multiplyAlpha(color, 0.4f)
        val lineArgb = ARGB.opaque(color)
        for (box in boxes) RenderUtils.gizmoBox(box, fillArgb, lineArgb)
    }

    private fun render(matrices: PoseStack, vc: VertexConsumer, fill: Boolean) {
        val boxes = guessBoxes() ?: return
        val rgba = RenderUtils.toFloats(guessColor())
        val fillRgba = floatArrayOf(rgba[0], rgba[1], rgba[2], rgba[3] * 0.4f)
        val lineRgba = floatArrayOf(rgba[0], rgba[1], rgba[2], 1f)
        for (box in boxes) {
            if (fill) RenderUtils.renderFilled(matrices, vc, box, fillRgba)
            else RenderUtils.renderOutline(matrices, vc, box, lineRgba)
        }
    }
}
