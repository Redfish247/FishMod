package fishmod.utils.rendering;

import fishmod.utils.Constants;
import fishmod.utils.config.values.ExtraOptions;
import fishmod.utils.config.values.Floor7;
import fishmod.utils.data.EntityUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import config.practical.hud.HUDComponent;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class RenderUtils {

    private static final float TEXT_SCALE = 0.025f;

    public static float[] toFloats(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        float a = ((argb >> 24) & 0xFF) / 255f;
        return new float[]{r, g, b, a};
    }

    public static int getStatusColor(int minGreen, int minOrange, int value) {
        return value >= minGreen ? Constants.GREEN : value >= minOrange ? Constants.GOLD : Constants.RED;
    }

    public static void drawText(GuiGraphicsExtractor context, HUDComponent component, Component text, int color) {
        Font textRenderer = Minecraft.getInstance().font;
        if (textRenderer == null) return;
        context.text(textRenderer, text, component.getScaledX(), component.getScaledY(), color, true);
    }

    public static void drawCenteredText(GuiGraphicsExtractor context, Font textRenderer, Component text, int x, int y, int maxWidth, int color) {
        if (textRenderer == null) return;
        int centered = (maxWidth - textRenderer.width(text)) / 2;
        context.text(textRenderer, text, x + centered, y, color, true);

    }

    public static void drawCenteredText(GuiGraphicsExtractor context, HUDComponent component, Component text) {
        Font textRenderer = Minecraft.getInstance().font;
        if (textRenderer == null) return;
        drawCenteredText(context, textRenderer, text, component.getScaledX(), component.getScaledY(), component.getWidth(), 0xffffffff);
    }

    public static void drawCenteredText(GuiGraphicsExtractor context, HUDComponent component, Component text, int color) {
        Font textRenderer = Minecraft.getInstance().font;
        if (textRenderer == null) return;
        drawCenteredText(context, textRenderer, text, component.getScaledX(), component.getScaledY(), component.getWidth(), color);
    }

    public static void drawTimer(HUDComponent component, GuiGraphicsExtractor context, int tick, int color) {
        double num = tick * Constants.TICK_DURATION;
        drawTimer(component, context, num, color);
    }

    public static void drawTimer(HUDComponent component, GuiGraphicsExtractor context, double num, int color) {
        int x = component.getScaledX();
        int y = component.getScaledY();

        RenderUtils.drawCenteredText(context, Minecraft.getInstance().font, Component.literal(Constants.DECIMAL_FORMAT.format(num)), x, y, component.getWidth(), color);
    }

    public static void drawPrefixedTimer(HUDComponent component, GuiGraphicsExtractor context, String prefix, int num) {
        drawPrefixedText(component, context, prefix, Constants.DECIMAL_FORMAT.format(num * Constants.TICK_DURATION) + "s");
    }

    public static void drawPrefixedTimer(HUDComponent component, GuiGraphicsExtractor context, String prefix, double num) {
        drawPrefixedText(component, context, prefix, Constants.DECIMAL_FORMAT.format(num) + "s");
    }

    public static void drawPrefixedText(HUDComponent component, GuiGraphicsExtractor context, String prefix, String text) {
        Font textRenderer = Minecraft.getInstance().font;
        if (textRenderer == null) return;

        Component drawnText = Component.literal(prefix + ": ").withColor(ExtraOptions.timerPrefixColor).append(Component.literal(text).withColor(0xffffffff));
        context.text(textRenderer, drawnText, component.getScaledX(), component.getScaledY(), 0xffffffff, true);
    }

    public static void renderFilled(PoseStack matrixStack, VertexConsumer consumer, AABB box, float[] rgba) {
        if (rgba[3] == 0) return;
        drawFilledBox(matrixStack, consumer, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, rgba[0], rgba[1], rgba[2], rgba[3]);
    }

    /**
     * Draws the 12-edge wireframe of an AABB. ShapeRenderer (the old generic VoxelShape outline
     * helper) was removed in 26.2; FishMod only ever outlines plain boxes here, so the edges are
     * emitted directly instead of routing through a VoxelShape.
     */
    public static void renderOutline(PoseStack matrixStack, VertexConsumer consumer, AABB box, float[] rgba) {
        if (rgba[3] == 0) return;
        PoseStack.Pose pose = matrixStack.last();
        float x1 = (float) box.minX, y1 = (float) box.minY, z1 = (float) box.minZ;
        float x2 = (float) box.maxX, y2 = (float) box.maxY, z2 = (float) box.maxZ;
        float r = rgba[0], g = rgba[1], b = rgba[2], a = rgba[3];

        // bottom, top, then the 4 verticals
        edge(consumer, pose, x1, y1, z1, x2, y1, z1, r, g, b, a);
        edge(consumer, pose, x2, y1, z1, x2, y1, z2, r, g, b, a);
        edge(consumer, pose, x2, y1, z2, x1, y1, z2, r, g, b, a);
        edge(consumer, pose, x1, y1, z2, x1, y1, z1, r, g, b, a);

        edge(consumer, pose, x1, y2, z1, x2, y2, z1, r, g, b, a);
        edge(consumer, pose, x2, y2, z1, x2, y2, z2, r, g, b, a);
        edge(consumer, pose, x2, y2, z2, x1, y2, z2, r, g, b, a);
        edge(consumer, pose, x1, y2, z2, x1, y2, z1, r, g, b, a);

        edge(consumer, pose, x1, y1, z1, x1, y2, z1, r, g, b, a);
        edge(consumer, pose, x2, y1, z1, x2, y2, z1, r, g, b, a);
        edge(consumer, pose, x2, y1, z2, x2, y2, z2, r, g, b, a);
        edge(consumer, pose, x1, y1, z2, x1, y2, z2, r, g, b, a);
    }

    /**
     * Like {@link #renderOutline}, but each of the 12 edges is a thin filled box instead of a
     * GL_LINES segment, so its thickness is an actual controllable size in blocks rather than GPU
     * line-width state (which isn't reliably adjustable per-draw through the batched pipeline here).
     * Must be submitted on the same triangle-strip layer as {@link #renderFilled} — see
     * {@link fishmod.utils.rendering.RenderingEvents#NO_DEPTH_FILLED}.
     */
    public static void renderThickOutline(PoseStack matrixStack, VertexConsumer consumer, AABB box, float[] rgba, double lineWidth) {
        if (rgba[3] == 0) return;
        double hw = lineWidth / 2.0;
        double x1 = box.minX, y1 = box.minY, z1 = box.minZ;
        double x2 = box.maxX, y2 = box.maxY, z2 = box.maxZ;

        thickEdge(matrixStack, consumer, x1, y1, z1, x2, y1, z1, hw, rgba);
        thickEdge(matrixStack, consumer, x2, y1, z1, x2, y1, z2, hw, rgba);
        thickEdge(matrixStack, consumer, x2, y1, z2, x1, y1, z2, hw, rgba);
        thickEdge(matrixStack, consumer, x1, y1, z2, x1, y1, z1, hw, rgba);

        thickEdge(matrixStack, consumer, x1, y2, z1, x2, y2, z1, hw, rgba);
        thickEdge(matrixStack, consumer, x2, y2, z1, x2, y2, z2, hw, rgba);
        thickEdge(matrixStack, consumer, x2, y2, z2, x1, y2, z2, hw, rgba);
        thickEdge(matrixStack, consumer, x1, y2, z2, x1, y2, z1, hw, rgba);

        thickEdge(matrixStack, consumer, x1, y1, z1, x1, y2, z1, hw, rgba);
        thickEdge(matrixStack, consumer, x2, y1, z1, x2, y2, z1, hw, rgba);
        thickEdge(matrixStack, consumer, x2, y1, z2, x2, y2, z2, hw, rgba);
        thickEdge(matrixStack, consumer, x1, y1, z2, x1, y2, z2, hw, rgba);
    }

    /** One axis-aligned edge of {@link #renderThickOutline}, expanded to {@code halfWidth} on the two axes it doesn't run along. */
    private static void thickEdge(PoseStack matrixStack, VertexConsumer consumer,
                                   double ax, double ay, double az, double bx, double by, double bz,
                                   double halfWidth, float[] rgba) {
        double minX = Math.min(ax, bx), maxX = Math.max(ax, bx);
        double minY = Math.min(ay, by), maxY = Math.max(ay, by);
        double minZ = Math.min(az, bz), maxZ = Math.max(az, bz);
        if (minX == maxX) { minX -= halfWidth; maxX += halfWidth; }
        if (minY == maxY) { minY -= halfWidth; maxY += halfWidth; }
        if (minZ == maxZ) { minZ -= halfWidth; maxZ += halfWidth; }
        drawFilledBox(matrixStack, consumer, minX, minY, minZ, maxX, maxY, maxZ, rgba[0], rgba[1], rgba[2], rgba[3]);
    }

    /** Draws a single straight line segment between two absolute world points, e.g. to connect route waypoints. */
    public static void renderLine(PoseStack matrixStack, VertexConsumer consumer, Vec3 a, Vec3 b, float[] rgba) {
        if (rgba[3] == 0) return;
        edge(consumer, matrixStack.last(), (float) a.x, (float) a.y, (float) a.z, (float) b.x, (float) b.y, (float) b.z,
                rgba[0], rgba[1], rgba[2], rgba[3]);
    }

    private static void edge(VertexConsumer consumer, PoseStack.Pose pose,
                              float x1, float y1, float z1, float x2, float y2, float z2,
                              float r, float g, float b, float a) {
        Vector3f normal = new Vector3f(x2 - x1, y2 - y1, z2 - z1).normalize();
        consumer.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setNormal(pose, normal.x, normal.y, normal.z);
        consumer.addVertex(pose, x2, y2, z2).setColor(r, g, b, a).setNormal(pose, normal.x, normal.y, normal.z);
    }


    public static void renderText(LevelRenderContext context, PoseStack matrices, Component text, double x, double y, double z, float scale) {
        Minecraft client = Minecraft.getInstance();
        Font textRenderer = client.font;
        LocalPlayer player = client.player;
        if (player == null) return;

        matrices.pushPose();
        matrices.translate(x, y, z);
        matrices.mulPose(context.levelState().cameraRenderState.orientation);
        matrices.scale(TEXT_SCALE * scale, -TEXT_SCALE * scale, TEXT_SCALE * scale);

        float halfWidth = textRenderer.width(text.getString()) / 2f;

        context.submitNodeCollector().submitText(matrices, -halfWidth, 0, text.getVisualOrderText(), true, Font.DisplayMode.SEE_THROUGH, 15728880, 0xffffffff, 0, 0);
        matrices.popPose();
    }

    public static void renderText(LevelRenderContext context, PoseStack matrices, Component text, Vec3 pos, float scale) {
        renderText(context, matrices, text, pos.x, pos.y, pos.z, scale);
    }

    public static void renderLineTo(LevelRenderContext context, PoseStack matrices, VertexConsumer consumer, double x, double y, double z, int color) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        Vec3 playerPos = EntityUtil.getLerpedPos(player);
        double eyeHeight = player.getEyeHeight();
        Vector3f lookat = new Vector3f(0, 0, -1f).rotate(context.levelState().cameraRenderState.orientation);

        Vector3f startPos = playerPos.toVector3f().add(0f, (float)eyeHeight, 0f).add(lookat);
        Vec3 endVec = new Vec3(x, y, z).subtract(playerPos).subtract(lookat.x, lookat.y + eyeHeight, lookat.z);
        Vector3f normal = new Vector3f((float)endVec.x, (float)endVec.y, (float)endVec.z).normalize();
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;
        if (a == 0) a = 1.0f;
        consumer.addVertex(matrices.last(), startPos.x, startPos.y, startPos.z).setColor(r, g, b, a).setNormal(matrices.last(), normal.x, normal.y, normal.z);
        consumer.addVertex(matrices.last(), (float)endVec.x + startPos.x, (float)endVec.y + startPos.y, (float)endVec.z + startPos.z).setColor(r, g, b, a).setNormal(matrices.last(), normal.x, normal.y, normal.z);
    }

    public static void renderLineTo(LevelRenderContext context, PoseStack matrices, VertexConsumer consumer, Vec3 pos, int color) {
        renderLineTo(context, matrices, consumer, pos.x, pos.y, pos.z, color);
    }

    /**
     * Six independent quads (24 vertices) — one per face, each walked around its perimeter
     * (not a Z-order/diagonal split). The two previous attempts here (34- and 14-vertex "triangle
     * strip" layouts) were solving the wrong problem: {@code RenderPipelines.DEBUG_FILLED_BOX}'s
     * snippet actually declares {@code VertexFormat.Mode.QUADS}, not {@code TRIANGLE_STRIP} — every
     * run of 4 vertices is one independent quad, no bridging between faces needed or wanted. Feeding
     * it strip-shaped data (shared vertices, degenerate bridge pairs) is exactly what produced the
     * corrupted "bowtie"/zigzag shapes, since the GPU was grouping 4-vertex chunks of that strip data
     * as unrelated quads instead of walking it as a continuous strip. Verified programmatically:
     * each quad's 2 implied triangles are coplanar and non-degenerate, and all 6 faces are covered
     * exactly once for a total surface area equal to a unit cube's.
     *
     * <p>Corners: A=(x1,y1,z1) B=(x2,y1,z1) C=(x1,y2,z1) D=(x2,y2,z1)
     *             E=(x1,y1,z2) F=(x2,y1,z2) G=(x1,y2,z2) H=(x2,y2,z2)
     */
    private static void drawFilledBox(PoseStack matrices, VertexConsumer consumer,
                                       double x1, double y1, double z1,
                                       double x2, double y2, double z2,
                                       float r, float g, float b, float a) {
        PoseStack.Pose entry = matrices.last();
        float ax1 = (float) x1, ay1 = (float) y1, az1 = (float) z1;
        float ax2 = (float) x2, ay2 = (float) y2, az2 = (float) z2;

        // Bottom: A,B,F,E
        consumer.addVertex(entry, ax1, ay1, az1).setColor(r, g, b, a);
        consumer.addVertex(entry, ax2, ay1, az1).setColor(r, g, b, a);
        consumer.addVertex(entry, ax2, ay1, az2).setColor(r, g, b, a);
        consumer.addVertex(entry, ax1, ay1, az2).setColor(r, g, b, a);
        // Top: C,D,H,G
        consumer.addVertex(entry, ax1, ay2, az1).setColor(r, g, b, a);
        consumer.addVertex(entry, ax2, ay2, az1).setColor(r, g, b, a);
        consumer.addVertex(entry, ax2, ay2, az2).setColor(r, g, b, a);
        consumer.addVertex(entry, ax1, ay2, az2).setColor(r, g, b, a);
        // Front: A,B,D,C
        consumer.addVertex(entry, ax1, ay1, az1).setColor(r, g, b, a);
        consumer.addVertex(entry, ax2, ay1, az1).setColor(r, g, b, a);
        consumer.addVertex(entry, ax2, ay2, az1).setColor(r, g, b, a);
        consumer.addVertex(entry, ax1, ay2, az1).setColor(r, g, b, a);
        // Back: E,F,H,G
        consumer.addVertex(entry, ax1, ay1, az2).setColor(r, g, b, a);
        consumer.addVertex(entry, ax2, ay1, az2).setColor(r, g, b, a);
        consumer.addVertex(entry, ax2, ay2, az2).setColor(r, g, b, a);
        consumer.addVertex(entry, ax1, ay2, az2).setColor(r, g, b, a);
        // Left: A,C,G,E
        consumer.addVertex(entry, ax1, ay1, az1).setColor(r, g, b, a);
        consumer.addVertex(entry, ax1, ay2, az1).setColor(r, g, b, a);
        consumer.addVertex(entry, ax1, ay2, az2).setColor(r, g, b, a);
        consumer.addVertex(entry, ax1, ay1, az2).setColor(r, g, b, a);
        // Right: B,D,H,F
        consumer.addVertex(entry, ax2, ay1, az1).setColor(r, g, b, a);
        consumer.addVertex(entry, ax2, ay2, az1).setColor(r, g, b, a);
        consumer.addVertex(entry, ax2, ay2, az2).setColor(r, g, b, a);
        consumer.addVertex(entry, ax2, ay1, az2).setColor(r, g, b, a);
    }

        public static String formatNumber(float num) {
        if (Floor7.capitalizeHealthNumbers) {
            if (num >= 1e9) return String.format("%.1fB", num / 1e9);
            if (num >= 1e6) return String.format("%.1fM", num / 1e6);
            if (num >= 1e3) return String.format("%.1fK", num / 1e3);
            return num + "";
        } else {
            if (num >= 1e9) return String.format("%.1fb", num / 1e9);
            if (num >= 1e6) return String.format("%.1fm", num / 1e6);
            if (num >= 1e3) return String.format("%.1fk", num / 1e3);
            return num + "";
        }
    }
}
