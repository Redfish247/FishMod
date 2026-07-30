package fishmod.utils.rendering

import config.practical.hud.HUDComponent
import fishmod.utils.Constants
import fishmod.utils.config.values.ExtraOptions
import fishmod.utils.config.values.Floor7
import fishmod.utils.data.EntityUtil
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.render.VertexRendering
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.text.Text
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShapes
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

    @JvmStatic
    fun getStatusColor(minGreen: Int, minOrange: Int, value: Int): Int {
        return if (value >= minGreen) Constants.GREEN else if (value >= minOrange) Constants.GOLD else Constants.RED
    }

    @JvmStatic
    fun drawText(context: DrawContext, component: HUDComponent, text: Text, color: Int) {
        val textRenderer = MinecraftClient.getInstance().textRenderer ?: return
        context.drawText(textRenderer, text, component.scaledX, component.scaledY, color, true)
    }

    @JvmStatic
    fun drawCenteredText(context: DrawContext, textRenderer: TextRenderer?, text: Text, x: Int, y: Int, maxWidth: Int, color: Int) {
        if (textRenderer == null) return
        val centered = (maxWidth - textRenderer.getWidth(text)) / 2
        context.drawText(textRenderer, text, x + centered, y, color, true)
    }

    @JvmStatic
    fun drawCenteredText(context: DrawContext, component: HUDComponent, text: Text) {
        val textRenderer = MinecraftClient.getInstance().textRenderer ?: return
        drawCenteredText(context, textRenderer, text, component.scaledX, component.scaledY, component.width, -0x1)
    }

    @JvmStatic
    fun drawCenteredText(context: DrawContext, component: HUDComponent, text: Text, color: Int) {
        val textRenderer = MinecraftClient.getInstance().textRenderer ?: return
        drawCenteredText(context, textRenderer, text, component.scaledX, component.scaledY, component.width, color)
    }

    @JvmStatic
    fun drawTimer(component: HUDComponent, context: DrawContext, tick: Int, color: Int) {
        val num = tick * Constants.TICK_DURATION
        drawTimer(component, context, num, color)
    }

    @JvmStatic
    fun drawTimer(component: HUDComponent, context: DrawContext, num: Double, color: Int) {
        val x = component.scaledX
        val y = component.scaledY

        drawCenteredText(context, MinecraftClient.getInstance().textRenderer, Text.literal(Constants.DECIMAL_FORMAT.format(num)), x, y, component.width, color)
    }

    @JvmStatic
    fun drawPrefixedTimer(component: HUDComponent, context: DrawContext, prefix: String, num: Int) {
        drawPrefixedText(component, context, prefix, Constants.DECIMAL_FORMAT.format(num * Constants.TICK_DURATION) + "s")
    }

    @JvmStatic
    fun drawPrefixedTimer(component: HUDComponent, context: DrawContext, prefix: String, num: Double) {
        drawPrefixedText(component, context, prefix, Constants.DECIMAL_FORMAT.format(num) + "s")
    }

    @JvmStatic
    fun drawPrefixedText(component: HUDComponent, context: DrawContext, prefix: String, text: String) {
        val textRenderer = MinecraftClient.getInstance().textRenderer ?: return

        val drawnText = Text.literal("$prefix: ").withColor(ExtraOptions.timerPrefixColor).append(Text.literal(text).withColor(-0x1))
        context.drawText(textRenderer, drawnText, component.scaledX, component.scaledY, -0x1, true)
    }

    @JvmStatic
    fun renderFilled(matrixStack: MatrixStack, consumer: VertexConsumer, box: Box, rgba: FloatArray) {
        if (rgba[3] == 0f) return
        drawFilledBox(matrixStack, consumer, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, rgba[0], rgba[1], rgba[2], rgba[3])
    }

    @JvmStatic
    @JvmOverloads
    fun renderOutline(matrixStack: MatrixStack, consumer: VertexConsumer, box: Box, rgba: FloatArray, lineWidth: Float = 1.0f) {
        if (rgba[3] == 0f) return
        val argb = ((rgba[3] * 255).toInt() shl 24) or ((rgba[0] * 255).toInt() shl 16) or ((rgba[1] * 255).toInt() shl 8) or (rgba[2] * 255).toInt()
        VertexRendering.drawOutline(matrixStack, consumer, VoxelShapes.cuboid(box), 0.0, 0.0, 0.0, argb, lineWidth)
    }

    /** Draws a single straight line segment between two absolute world points, e.g. to connect route waypoints. */
    @JvmStatic
    fun renderLine(matrixStack: MatrixStack, consumer: VertexConsumer, a: Vec3d, b: Vec3d, rgba: FloatArray) {
        if (rgba[3] == 0f) return
        val pose = matrixStack.peek()
        val normal = Vector3f((b.x - a.x).toFloat(), (b.y - a.y).toFloat(), (b.z - a.z).toFloat()).normalize()
        val r = rgba[0]
        val g = rgba[1]
        val bl = rgba[2]
        val al = rgba[3]
        consumer.vertex(pose, a.x.toFloat(), a.y.toFloat(), a.z.toFloat()).color(r, g, bl, al).normal(pose, normal.x, normal.y, normal.z)
        consumer.vertex(pose, b.x.toFloat(), b.y.toFloat(), b.z.toFloat()).color(r, g, bl, al).normal(pose, normal.x, normal.y, normal.z)
    }

    @JvmStatic
    fun renderText(context: WorldRenderContext, matrices: MatrixStack, text: Text, x: Double, y: Double, z: Double, scale: Float) {
        val client = MinecraftClient.getInstance()
        val textRenderer = client.textRenderer
        val player: ClientPlayerEntity = client.player ?: return

        matrices.push()
        matrices.translate(x, y, z)
        matrices.multiply(context.worldState().cameraRenderState.orientation)
        matrices.scale(TEXT_SCALE * scale, -TEXT_SCALE * scale, TEXT_SCALE * scale)

        val halfWidth = textRenderer.getWidth(text.string) / 2f

        context.commandQueue().submitText(matrices, -halfWidth, 0f, text.asOrderedText(), true, TextRenderer.TextLayerType.SEE_THROUGH, 15728880, -0x1, 0, 0)
        matrices.pop()
    }

    @JvmStatic
    fun renderText(context: WorldRenderContext, matrices: MatrixStack, text: Text, pos: Vec3d, scale: Float) {
        renderText(context, matrices, text, pos.x, pos.y, pos.z, scale)
    }

    @JvmStatic
    fun renderLineTo(context: WorldRenderContext, matrices: MatrixStack, consumer: VertexConsumer, x: Double, y: Double, z: Double, color: Int) {
        val player = MinecraftClient.getInstance().player ?: return

        val playerPos = EntityUtil.getLerpedPos(player)
        val eyeHeight = player.standingEyeHeight
        val lookat = Vector3f(0f, 0f, -1f).rotate(context.worldState().cameraRenderState.orientation)

        val startPos = playerPos.toVector3f().add(0f, eyeHeight.toFloat(), 0f).add(lookat)
        val endVec = Vec3d(x, y, z).subtract(playerPos).subtract(lookat.x.toDouble(), (lookat.y + eyeHeight).toDouble(), lookat.z.toDouble())
        val normal = Vector3f(endVec.x.toFloat(), endVec.y.toFloat(), endVec.z.toFloat()).normalize()
        var r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        var a = ((color shr 24) and 0xFF) / 255f
        if (a == 0f) a = 1.0f
        consumer.vertex(matrices.peek(), startPos.x, startPos.y, startPos.z).color(r, g, b, a).normal(matrices.peek(), normal.x, normal.y, normal.z)
        consumer.vertex(matrices.peek(), endVec.x.toFloat() + startPos.x, endVec.y.toFloat() + startPos.y, endVec.z.toFloat() + startPos.z).color(r, g, b, a).normal(matrices.peek(), normal.x, normal.y, normal.z)
    }

    @JvmStatic
    fun renderLineTo(context: WorldRenderContext, matrices: MatrixStack, consumer: VertexConsumer, pos: Vec3d, color: Int) {
        renderLineTo(context, matrices, consumer, pos.x, pos.y, pos.z, color)
    }

    /** Six independent quads (24 vertices), one per face — DEBUG_FILLED_BOX's pipeline is VertexFormat.Mode.QUADS, not TRIANGLE_STRIP, so strip-shaped data produced corrupted bowtie shapes. */
    private fun drawFilledBox(
        matrices: MatrixStack, consumer: VertexConsumer,
        x1: Double, y1: Double, z1: Double,
        x2: Double, y2: Double, z2: Double,
        r: Float, g: Float, b: Float, a: Float
    ) {
        val entry = matrices.peek()
        val ax1 = x1.toFloat()
        val ay1 = y1.toFloat()
        val az1 = z1.toFloat()
        val ax2 = x2.toFloat()
        val ay2 = y2.toFloat()
        val az2 = z2.toFloat()

        // Bottom: A,B,F,E
        consumer.vertex(entry, ax1, ay1, az1).color(r, g, b, a)
        consumer.vertex(entry, ax2, ay1, az1).color(r, g, b, a)
        consumer.vertex(entry, ax2, ay1, az2).color(r, g, b, a)
        consumer.vertex(entry, ax1, ay1, az2).color(r, g, b, a)
        // Top: C,D,H,G
        consumer.vertex(entry, ax1, ay2, az1).color(r, g, b, a)
        consumer.vertex(entry, ax2, ay2, az1).color(r, g, b, a)
        consumer.vertex(entry, ax2, ay2, az2).color(r, g, b, a)
        consumer.vertex(entry, ax1, ay2, az2).color(r, g, b, a)
        // Front: A,B,D,C
        consumer.vertex(entry, ax1, ay1, az1).color(r, g, b, a)
        consumer.vertex(entry, ax2, ay1, az1).color(r, g, b, a)
        consumer.vertex(entry, ax2, ay2, az1).color(r, g, b, a)
        consumer.vertex(entry, ax1, ay2, az1).color(r, g, b, a)
        // Back: E,F,H,G
        consumer.vertex(entry, ax1, ay1, az2).color(r, g, b, a)
        consumer.vertex(entry, ax2, ay1, az2).color(r, g, b, a)
        consumer.vertex(entry, ax2, ay2, az2).color(r, g, b, a)
        consumer.vertex(entry, ax1, ay2, az2).color(r, g, b, a)
        // Left: A,C,G,E
        consumer.vertex(entry, ax1, ay1, az1).color(r, g, b, a)
        consumer.vertex(entry, ax1, ay2, az1).color(r, g, b, a)
        consumer.vertex(entry, ax1, ay2, az2).color(r, g, b, a)
        consumer.vertex(entry, ax1, ay1, az2).color(r, g, b, a)
        // Right: B,D,H,F
        consumer.vertex(entry, ax2, ay1, az1).color(r, g, b, a)
        consumer.vertex(entry, ax2, ay2, az1).color(r, g, b, a)
        consumer.vertex(entry, ax2, ay2, az2).color(r, g, b, a)
        consumer.vertex(entry, ax2, ay1, az2).color(r, g, b, a)
    }

    @JvmStatic
    fun formatNumber(num: Float): String {
        return if (Floor7.capitalizeHealthNumbers) {
            if (num >= 1e9) String.format("%.1fB", num / 1e9f)
            else if (num >= 1e6) String.format("%.1fM", num / 1e6f)
            else if (num >= 1e3) String.format("%.1fK", num / 1e3f)
            else "$num"
        } else {
            if (num >= 1e9) String.format("%.1fb", num / 1e9f)
            else if (num >= 1e6) String.format("%.1fm", num / 1e6f)
            else if (num >= 1e3) String.format("%.1fk", num / 1e3f)
            else "$num"
        }
    }
}
