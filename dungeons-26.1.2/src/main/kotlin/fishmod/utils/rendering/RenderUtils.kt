package fishmod.utils.rendering

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import fishmod.shaded.practicalconfig.hud.HUDComponent
import fishmod.utils.Constants
import fishmod.utils.config.values.ExtraOptions
import fishmod.utils.config.values.Floor7
import fishmod.utils.data.EntityUtil
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.gizmos.Gizmos
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.TextGizmo
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.joml.Vector3f

object RenderUtils {

    private const val TEXT_SCALE = 0.025f

    @JvmStatic
    fun toFloats(argb: Int): FloatArray {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val a = ((argb shr 24) and 0xFF) / 255f
        return floatArrayOf(r, g, b, a)
    }

    // Tracer start from the real camera (follows the smooth crouch height, unlike player.eyeHeight).
    @JvmStatic
    fun cameraLineStart(ahead: Double): Vec3 {
        val cam = Minecraft.getInstance().gameRenderer.mainCamera
        return cam.position().add(Vec3.directionFromRotation(cam.xRot(), cam.yRot()).scale(ahead))
    }

    @JvmStatic
    fun gizmoBox(box: AABB, fillArgb: Int, strokeArgb: Int) = gizmoBox(box, fillArgb, strokeArgb, false)

    @JvmStatic
    fun gizmoBox(box: AABB, fillArgb: Int, strokeArgb: Int, throughWalls: Boolean) {
        if ((fillArgb ushr 24) != 0) Gizmos.cuboid(box, GizmoStyle.fill(fillArgb)).also { if (throughWalls) it.setAlwaysOnTop() }
        if ((strokeArgb ushr 24) != 0) Gizmos.cuboid(box, GizmoStyle.stroke(strokeArgb)).also { if (throughWalls) it.setAlwaysOnTop() }
    }

    @JvmStatic
    fun gizmoThickOutline(box: AABB, argb: Int, lineWidth: Double) {
        if ((argb ushr 24) == 0) return
        val hw = lineWidth / 2.0
        val x1 = box.minX; val y1 = box.minY; val z1 = box.minZ
        val x2 = box.maxX; val y2 = box.maxY; val z2 = box.maxZ

        gizmoThickEdge(x1, y1, z1, x2, y1, z1, hw, argb)
        gizmoThickEdge(x2, y1, z1, x2, y1, z2, hw, argb)
        gizmoThickEdge(x2, y1, z2, x1, y1, z2, hw, argb)
        gizmoThickEdge(x1, y1, z2, x1, y1, z1, hw, argb)

        gizmoThickEdge(x1, y2, z1, x2, y2, z1, hw, argb)
        gizmoThickEdge(x2, y2, z1, x2, y2, z2, hw, argb)
        gizmoThickEdge(x2, y2, z2, x1, y2, z2, hw, argb)
        gizmoThickEdge(x1, y2, z2, x1, y2, z1, hw, argb)

        gizmoThickEdge(x1, y1, z1, x1, y2, z1, hw, argb)
        gizmoThickEdge(x2, y1, z1, x2, y2, z1, hw, argb)
        gizmoThickEdge(x2, y1, z2, x2, y2, z2, hw, argb)
        gizmoThickEdge(x1, y1, z2, x1, y2, z2, hw, argb)
    }

    @JvmStatic
    fun gizmoThickEdge(a: Vec3, b: Vec3, halfWidth: Double, argb: Int) {
        gizmoThickEdge(a.x, a.y, a.z, b.x, b.y, b.z, halfWidth, argb)
    }

    private fun gizmoThickEdge(
        ax: Double, ay: Double, az: Double, bx: Double, by: Double, bz: Double,
        halfWidth: Double, argb: Int
    ) {
        var minX = minOf(ax, bx); var maxX = maxOf(ax, bx)
        var minY = minOf(ay, by); var maxY = maxOf(ay, by)
        var minZ = minOf(az, bz); var maxZ = maxOf(az, bz)
        if (minX == maxX) { minX -= halfWidth; maxX += halfWidth }
        if (minY == maxY) { minY -= halfWidth; maxY += halfWidth }
        if (minZ == maxZ) { minZ -= halfWidth; maxZ += halfWidth }
        Gizmos.cuboid(AABB(minX, minY, minZ, maxX, maxY, maxZ), GizmoStyle.fill(argb))
    }

    @JvmStatic
    fun gizmoQuad(corners: Array<Vec3>, fillArgb: Int, strokeArgb: Int) {
        if (corners.size < 4) return
        Gizmos.rect(corners[0], corners[1], corners[2], corners[3], GizmoStyle.strokeAndFill(strokeArgb, 2f, fillArgb))
    }

    @JvmStatic
    fun gizmoLine(a: Vec3, b: Vec3, argb: Int) {
        Gizmos.line(a, b, if ((argb ushr 24) == 0) argb or (0xFF shl 24) else argb)
    }

    private fun crossQuads(a: Vec3, b: Vec3, halfWidth: Double): Pair<Array<Vec3>, Array<Vec3>>? {
        val dir = b.subtract(a)
        if (dir.lengthSqr() < 1.0e-9) return null
        val d = dir.normalize()
        val ref = if (kotlin.math.abs(d.y) < 0.99) Vec3(0.0, 1.0, 0.0) else Vec3(1.0, 0.0, 0.0)
        val right = d.cross(ref).normalize().scale(halfWidth)
        val up = d.cross(right).normalize().scale(halfWidth)
        return Pair(
            arrayOf(a.subtract(right), a.add(right), b.add(right), b.subtract(right)),
            arrayOf(a.subtract(up), a.add(up), b.add(up), b.subtract(up)),
        )
    }

    @JvmStatic
    fun gizmoThickLine(a: Vec3, b: Vec3, halfWidth: Double, argb: Int) {
        if ((argb ushr 24) == 0) return
        val (quad1, quad2) = crossQuads(a, b, halfWidth) ?: return
        gizmoQuad(quad1, argb, 0)
        gizmoQuad(quad2, argb, 0)
    }

    @JvmStatic
    fun gizmoText(text: Component, pos: Vec3, scale: Float, argb: Int) {
        Gizmos.billboardText(text.string, pos, TextGizmo.Style.forColorAndCentered(argb).withScale(scale))
    }

    @JvmStatic
    fun drawCenteredText(context: GuiGraphicsExtractor, textRenderer: Font?, text: Component, x: Int, y: Int, maxWidth: Int, color: Int) {
        if (textRenderer == null) return
        val centered = (maxWidth - textRenderer.width(text)) / 2
        context.text(textRenderer, text, x + centered, y, color, true)
    }

    @JvmStatic
    fun drawCenteredText(context: GuiGraphicsExtractor, component: HUDComponent, text: Component) {
        val textRenderer = Minecraft.getInstance().font ?: return
        drawCenteredText(context, textRenderer, text, component.scaledX, component.scaledY, component.width, -0x1)
    }

    @JvmStatic
    fun drawCenteredText(context: GuiGraphicsExtractor, component: HUDComponent, text: Component, color: Int) {
        val textRenderer = Minecraft.getInstance().font ?: return
        drawCenteredText(context, textRenderer, text, component.scaledX, component.scaledY, component.width, color)
    }

    @JvmStatic
    fun drawTimer(component: HUDComponent, context: GuiGraphicsExtractor, tick: Int, color: Int) {
        val num = tick * Constants.TICK_DURATION
        drawTimer(component, context, num, color)
    }

    @JvmStatic
    fun drawTimer(component: HUDComponent, context: GuiGraphicsExtractor, num: Double, color: Int) {
        val x = component.scaledX
        val y = component.scaledY

        drawCenteredText(context, Minecraft.getInstance().font, Component.literal(Constants.DECIMAL_FORMAT.format(num)), x, y, component.width, color)
    }

    @JvmStatic
    fun drawPrefixedText(component: HUDComponent, context: GuiGraphicsExtractor, prefix: String, text: String) {
        val textRenderer = Minecraft.getInstance().font ?: return

        val drawnText = Component.literal("$prefix: ").withColor(ExtraOptions.timerPrefixColor).append(Component.literal(text).withColor(-0x1))
        context.text(textRenderer, drawnText, component.scaledX, component.scaledY, -0x1, true)
    }

    @JvmStatic
    fun renderFilled(matrixStack: PoseStack, consumer: VertexConsumer, box: AABB, rgba: FloatArray) {
        if (rgba[3] == 0f) return
        drawFilledBox(matrixStack, consumer, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, rgba[0], rgba[1], rgba[2], rgba[3])
    }

    @JvmStatic
    fun renderFilledQuad(matrixStack: PoseStack, consumer: VertexConsumer, corners: Array<Vec3>, rgba: FloatArray) {
        if (rgba[3] == 0f) return
        val pose = matrixStack.last()
        for (c in corners) {
            consumer.addVertex(pose, c.x.toFloat(), c.y.toFloat(), c.z.toFloat()).setColor(rgba[0], rgba[1], rgba[2], rgba[3])
        }
    }

    @JvmStatic
    fun renderQuadOutline(matrixStack: PoseStack, consumer: VertexConsumer, corners: Array<Vec3>, rgba: FloatArray) {
        if (rgba[3] == 0f) return
        val pose = matrixStack.last()
        for (i in corners.indices) {
            val a = corners[i]
            val b = corners[(i + 1) % corners.size]
            edge(consumer, pose, a.x.toFloat(), a.y.toFloat(), a.z.toFloat(), b.x.toFloat(), b.y.toFloat(), b.z.toFloat(), rgba[0], rgba[1], rgba[2], rgba[3])
        }
    }

    @JvmStatic
    fun renderOutline(matrixStack: PoseStack, consumer: VertexConsumer, box: AABB, rgba: FloatArray) {
        if (rgba[3] == 0f) return
        val pose = matrixStack.last()
        val x1 = box.minX.toFloat(); val y1 = box.minY.toFloat(); val z1 = box.minZ.toFloat()
        val x2 = box.maxX.toFloat(); val y2 = box.maxY.toFloat(); val z2 = box.maxZ.toFloat()
        val r = rgba[0]; val g = rgba[1]; val b = rgba[2]; val a = rgba[3]

        edge(consumer, pose, x1, y1, z1, x2, y1, z1, r, g, b, a)
        edge(consumer, pose, x2, y1, z1, x2, y1, z2, r, g, b, a)
        edge(consumer, pose, x2, y1, z2, x1, y1, z2, r, g, b, a)
        edge(consumer, pose, x1, y1, z2, x1, y1, z1, r, g, b, a)

        edge(consumer, pose, x1, y2, z1, x2, y2, z1, r, g, b, a)
        edge(consumer, pose, x2, y2, z1, x2, y2, z2, r, g, b, a)
        edge(consumer, pose, x2, y2, z2, x1, y2, z2, r, g, b, a)
        edge(consumer, pose, x1, y2, z2, x1, y2, z1, r, g, b, a)

        edge(consumer, pose, x1, y1, z1, x1, y2, z1, r, g, b, a)
        edge(consumer, pose, x2, y1, z1, x2, y2, z1, r, g, b, a)
        edge(consumer, pose, x2, y1, z2, x2, y2, z2, r, g, b, a)
        edge(consumer, pose, x1, y1, z2, x1, y2, z2, r, g, b, a)
    }

    @JvmStatic
    fun renderThickOutline(matrixStack: PoseStack, consumer: VertexConsumer, box: AABB, rgba: FloatArray, lineWidth: Double) {
        if (rgba[3] == 0f) return
        val hw = lineWidth / 2.0
        val x1 = box.minX; val y1 = box.minY; val z1 = box.minZ
        val x2 = box.maxX; val y2 = box.maxY; val z2 = box.maxZ

        thickEdge(matrixStack, consumer, x1, y1, z1, x2, y1, z1, hw, rgba)
        thickEdge(matrixStack, consumer, x2, y1, z1, x2, y1, z2, hw, rgba)
        thickEdge(matrixStack, consumer, x2, y1, z2, x1, y1, z2, hw, rgba)
        thickEdge(matrixStack, consumer, x1, y1, z2, x1, y1, z1, hw, rgba)

        thickEdge(matrixStack, consumer, x1, y2, z1, x2, y2, z1, hw, rgba)
        thickEdge(matrixStack, consumer, x2, y2, z1, x2, y2, z2, hw, rgba)
        thickEdge(matrixStack, consumer, x2, y2, z2, x1, y2, z2, hw, rgba)
        thickEdge(matrixStack, consumer, x1, y2, z2, x1, y2, z1, hw, rgba)

        thickEdge(matrixStack, consumer, x1, y1, z1, x1, y2, z1, hw, rgba)
        thickEdge(matrixStack, consumer, x2, y1, z1, x2, y2, z1, hw, rgba)
        thickEdge(matrixStack, consumer, x2, y1, z2, x2, y2, z2, hw, rgba)
        thickEdge(matrixStack, consumer, x1, y1, z2, x1, y2, z2, hw, rgba)
    }

    @JvmStatic
    fun renderThickEdge(matrixStack: PoseStack, consumer: VertexConsumer, a: Vec3, b: Vec3, halfWidth: Double, rgba: FloatArray) {
        thickEdge(matrixStack, consumer, a.x, a.y, a.z, b.x, b.y, b.z, halfWidth, rgba)
    }

    private fun thickEdge(
        matrixStack: PoseStack, consumer: VertexConsumer,
        ax: Double, ay: Double, az: Double, bx: Double, by: Double, bz: Double,
        halfWidth: Double, rgba: FloatArray
    ) {
        var minX = minOf(ax, bx); var maxX = maxOf(ax, bx)
        var minY = minOf(ay, by); var maxY = maxOf(ay, by)
        var minZ = minOf(az, bz); var maxZ = maxOf(az, bz)
        if (minX == maxX) { minX -= halfWidth; maxX += halfWidth }
        if (minY == maxY) { minY -= halfWidth; maxY += halfWidth }
        if (minZ == maxZ) { minZ -= halfWidth; maxZ += halfWidth }
        drawFilledBox(matrixStack, consumer, minX, minY, minZ, maxX, maxY, maxZ, rgba[0], rgba[1], rgba[2], rgba[3])
    }

    @JvmStatic
    fun renderLine(matrixStack: PoseStack, consumer: VertexConsumer, a: Vec3, b: Vec3, rgba: FloatArray) {
        if (rgba[3] == 0f) return
        edge(
            consumer, matrixStack.last(), a.x.toFloat(), a.y.toFloat(), a.z.toFloat(), b.x.toFloat(), b.y.toFloat(), b.z.toFloat(),
            rgba[0], rgba[1], rgba[2], rgba[3]
        )
    }

    @JvmStatic
    fun renderThickLine(matrixStack: PoseStack, consumer: VertexConsumer, a: Vec3, b: Vec3, halfWidth: Double, rgba: FloatArray) {
        if (rgba[3] == 0f) return
        val (quad1, quad2) = crossQuads(a, b, halfWidth) ?: return
        renderFilledQuad(matrixStack, consumer, quad1, rgba)
        renderFilledQuad(matrixStack, consumer, quad2, rgba)
    }

    private fun edge(
        consumer: VertexConsumer, pose: PoseStack.Pose,
        x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float,
        r: Float, g: Float, b: Float, a: Float
    ) {
        consumer.addVertex(pose, x1, y1, z1).setColor(r, g, b, a)
        consumer.addVertex(pose, x2, y2, z2).setColor(r, g, b, a)
    }

    @JvmStatic
    fun renderText(context: LevelRenderContext, matrices: PoseStack, text: Component, x: Double, y: Double, z: Double, scale: Float) {
        val client = Minecraft.getInstance()
        val textRenderer = client.font
        client.player ?: return

        val cam = client.gameRenderer.mainCamera.position()
        val viewPos = Vector3f(
            (x - cam.x).toFloat(), (y - cam.y).toFloat(), (z - cam.z).toFloat(),
        )
        Quaternionf(context.levelState().cameraRenderState.orientation).conjugate().transform(viewPos)

        matrices.pushPose()
        matrices.translate(viewPos.x + cam.x, viewPos.y + cam.y, viewPos.z + cam.z)
        matrices.scale(TEXT_SCALE * scale, -TEXT_SCALE * scale, TEXT_SCALE * scale)

        val halfWidth = textRenderer.width(text) / 2f
        context.submitNodeCollector().submitText(matrices, -halfWidth, 0f, text.visualOrderText, true, Font.DisplayMode.SEE_THROUGH, 15728880, -0x1, 0, 0)
        matrices.popPose()
    }

    @JvmStatic
    fun renderText(context: LevelRenderContext, matrices: PoseStack, text: Component, pos: Vec3, scale: Float) {
        renderText(context, matrices, text, pos.x, pos.y, pos.z, scale)
    }

    @JvmStatic
    fun renderLineTo(context: LevelRenderContext, matrices: PoseStack, consumer: VertexConsumer, x: Double, y: Double, z: Double, color: Int) {
        val player = Minecraft.getInstance().player ?: return

        val playerPos = EntityUtil.getLerpedPos(player)
        val eyeHeight = player.eyeHeight
        val lookat = Vector3f(0f, 0f, -1f).rotate(context.levelState().cameraRenderState.orientation)

        val startPos = playerPos.toVector3f().add(0f, eyeHeight.toFloat(), 0f).add(lookat)
        val endVec = Vec3(x, y, z).subtract(playerPos).subtract(lookat.x.toDouble(), (lookat.y + eyeHeight).toDouble(), lookat.z.toDouble())
        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        var a = ((color shr 24) and 0xFF) / 255f
        if (a == 0f) a = 1.0f
        consumer.addVertex(matrices.last(), startPos.x, startPos.y, startPos.z).setColor(r, g, b, a)
        consumer.addVertex(matrices.last(), endVec.x.toFloat() + startPos.x, endVec.y.toFloat() + startPos.y, endVec.z.toFloat() + startPos.z).setColor(r, g, b, a)
    }

    @JvmStatic
    fun renderLineTo(context: LevelRenderContext, matrices: PoseStack, consumer: VertexConsumer, pos: Vec3, color: Int) {
        renderLineTo(context, matrices, consumer, pos.x, pos.y, pos.z, color)
    }

    private fun drawFilledBox(
        matrices: PoseStack, consumer: VertexConsumer,
        x1: Double, y1: Double, z1: Double,
        x2: Double, y2: Double, z2: Double,
        r: Float, g: Float, b: Float, a: Float
    ) {
        val entry = matrices.last()
        val ax1 = x1.toFloat(); val ay1 = y1.toFloat(); val az1 = z1.toFloat()
        val ax2 = x2.toFloat(); val ay2 = y2.toFloat(); val az2 = z2.toFloat()

        consumer.addVertex(entry, ax1, ay1, az1).setColor(r, g, b, a)
        consumer.addVertex(entry, ax2, ay1, az1).setColor(r, g, b, a)
        consumer.addVertex(entry, ax2, ay1, az2).setColor(r, g, b, a)
        consumer.addVertex(entry, ax1, ay1, az2).setColor(r, g, b, a)
        consumer.addVertex(entry, ax1, ay2, az1).setColor(r, g, b, a)
        consumer.addVertex(entry, ax2, ay2, az1).setColor(r, g, b, a)
        consumer.addVertex(entry, ax2, ay2, az2).setColor(r, g, b, a)
        consumer.addVertex(entry, ax1, ay2, az2).setColor(r, g, b, a)
        consumer.addVertex(entry, ax1, ay1, az1).setColor(r, g, b, a)
        consumer.addVertex(entry, ax2, ay1, az1).setColor(r, g, b, a)
        consumer.addVertex(entry, ax2, ay2, az1).setColor(r, g, b, a)
        consumer.addVertex(entry, ax1, ay2, az1).setColor(r, g, b, a)
        consumer.addVertex(entry, ax1, ay1, az2).setColor(r, g, b, a)
        consumer.addVertex(entry, ax2, ay1, az2).setColor(r, g, b, a)
        consumer.addVertex(entry, ax2, ay2, az2).setColor(r, g, b, a)
        consumer.addVertex(entry, ax1, ay2, az2).setColor(r, g, b, a)
        consumer.addVertex(entry, ax1, ay1, az1).setColor(r, g, b, a)
        consumer.addVertex(entry, ax1, ay2, az1).setColor(r, g, b, a)
        consumer.addVertex(entry, ax1, ay2, az2).setColor(r, g, b, a)
        consumer.addVertex(entry, ax1, ay1, az2).setColor(r, g, b, a)
        consumer.addVertex(entry, ax2, ay1, az1).setColor(r, g, b, a)
        consumer.addVertex(entry, ax2, ay2, az1).setColor(r, g, b, a)
        consumer.addVertex(entry, ax2, ay2, az2).setColor(r, g, b, a)
        consumer.addVertex(entry, ax2, ay1, az2).setColor(r, g, b, a)
    }

    @JvmStatic
    fun formatNumber(num: Float): String {
        return if (Floor7.capitalizeHealthNumbers) {
            if (num >= 1e9) String.format(java.util.Locale.ROOT, "%.1fB", num / 1e9f)
            else if (num >= 1e6) String.format(java.util.Locale.ROOT, "%.1fM", num / 1e6f)
            else if (num >= 1e3) String.format(java.util.Locale.ROOT, "%.1fK", num / 1e3f)
            else "$num"
        } else {
            if (num >= 1e9) String.format(java.util.Locale.ROOT, "%.1fb", num / 1e9f)
            else if (num >= 1e6) String.format(java.util.Locale.ROOT, "%.1fm", num / 1e6f)
            else if (num >= 1e3) String.format(java.util.Locale.ROOT, "%.1fk", num / 1e3f)
            else "$num"
        }
    }
}
