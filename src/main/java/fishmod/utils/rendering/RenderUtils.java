package fishmod.utils.rendering;

import fishmod.utils.Constants;
import fishmod.utils.config.values.ExtraOptions;
import fishmod.utils.config.values.Floor7;
import fishmod.utils.data.EntityUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import config.practical.hud.HUDComponent;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
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

    public static void drawText(GuiGraphics context, HUDComponent component, Component text, int color) {
        Font textRenderer = Minecraft.getInstance().font;
        if (textRenderer == null) return;
        context.drawString(textRenderer, text, component.getScaledX(), component.getScaledY(), color, true);
    }

    public static void drawCenteredText(GuiGraphics context, Font textRenderer, Component text, int x, int y, int maxWidth, int color) {
        if (textRenderer == null) return;
        int centered = (maxWidth - textRenderer.width(text)) / 2;
        context.drawString(textRenderer, text, x + centered, y, color, true);

    }

    public static void drawCenteredText(GuiGraphics context, HUDComponent component, Component text) {
        Font textRenderer = Minecraft.getInstance().font;
        if (textRenderer == null) return;
        drawCenteredText(context, textRenderer, text, component.getScaledX(), component.getScaledY(), component.getWidth(), 0xffffffff);
    }

    public static void drawCenteredText(GuiGraphics context, HUDComponent component, Component text, int color) {
        Font textRenderer = Minecraft.getInstance().font;
        if (textRenderer == null) return;
        drawCenteredText(context, textRenderer, text, component.getScaledX(), component.getScaledY(), component.getWidth(), color);
    }

    public static void drawTimer(HUDComponent component, GuiGraphics context, int tick, int color) {
        double num = tick * Constants.TICK_DURATION;
        drawTimer(component, context, num, color);
    }

    public static void drawTimer(HUDComponent component, GuiGraphics context, double num, int color) {
        int x = component.getScaledX();
        int y = component.getScaledY();

        RenderUtils.drawCenteredText(context, Minecraft.getInstance().font, Component.literal(Constants.DECIMAL_FORMAT.format(num)), x, y, component.getWidth(), color);
    }

    public static void drawPrefixedTimer(HUDComponent component, GuiGraphics context, String prefix, int num) {
        drawPrefixedText(component, context, prefix, Constants.DECIMAL_FORMAT.format(num * Constants.TICK_DURATION) + "s");
    }

    public static void drawPrefixedTimer(HUDComponent component, GuiGraphics context, String prefix, double num) {
        drawPrefixedText(component, context, prefix, Constants.DECIMAL_FORMAT.format(num) + "s");
    }

    public static void drawPrefixedText(HUDComponent component, GuiGraphics context, String prefix, String text) {
        Font textRenderer = Minecraft.getInstance().font;
        if (textRenderer == null) return;

        Component drawnText = Component.literal(prefix + ": ").withColor(ExtraOptions.timerPrefixColor).append(Component.literal(text).withColor(0xffffffff));
        context.drawString(textRenderer, drawnText, component.getScaledX(), component.getScaledY(), 0xffffffff, true);
    }

    public static void renderFilled(PoseStack matrixStack, VertexConsumer consumer, AABB box, float[] rgba) {
        if (rgba[3] == 0) return;
        drawFilledBox(matrixStack, consumer, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, rgba[0], rgba[1], rgba[2], rgba[3]);
    }

    public static void renderOutline(PoseStack matrixStack, VertexConsumer consumer, AABB box, float[] rgba) {
        if (rgba[3] == 0) return;
        int argb = ((int)(rgba[3] * 255) << 24) | ((int)(rgba[0] * 255) << 16) | ((int)(rgba[1] * 255) << 8) | (int)(rgba[2] * 255);
        ShapeRenderer.renderShape(matrixStack, consumer, Shapes.create(box), 0.0, 0.0, 0.0, argb, 1.0f);
    }


    public static void renderText(WorldRenderContext context, PoseStack matrices, Component text, double x, double y, double z, float scale) {
        Minecraft client = Minecraft.getInstance();
        Font textRenderer = client.font;
        LocalPlayer player = client.player;
        if (player == null) return;

        matrices.pushPose();
        matrices.translate(x, y, z);
        matrices.mulPose(context.worldState().cameraRenderState.orientation);
        matrices.scale(TEXT_SCALE * scale, -TEXT_SCALE * scale, TEXT_SCALE * scale);

        float halfWidth = textRenderer.width(text.getString()) / 2f;

        context.commandQueue().submitText(matrices, -halfWidth, 0, text.getVisualOrderText(), true, Font.DisplayMode.SEE_THROUGH, 15728880, 0xffffffff, 0, 0);
        matrices.popPose();
    }

    public static void renderText(WorldRenderContext context, PoseStack matrices, Component text, Vec3 pos, float scale) {
        renderText(context, matrices, text, pos.x, pos.y, pos.z, scale);
    }

    public static void renderLineTo(WorldRenderContext context, PoseStack matrices, VertexConsumer consumer, double x, double y, double z, int color) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        Vec3 playerPos = EntityUtil.getLerpedPos(player);
        double eyeHeight = player.getEyeHeight();
        Vector3f lookat = new Vector3f(0, 0, -1f).rotate(context.worldState().cameraRenderState.orientation);

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

    public static void renderLineTo(WorldRenderContext context, PoseStack matrices, VertexConsumer consumer, Vec3 pos, int color) {
        renderLineTo(context, matrices, consumer, pos.x, pos.y, pos.z, color);
    }

    private static void drawFilledBox(PoseStack matrices, VertexConsumer consumer,
                                       double x1, double y1, double z1,
                                       double x2, double y2, double z2,
                                       float r, float g, float b, float a) {
        PoseStack.Pose entry = matrices.last();
        consumer.addVertex(entry, (float)x1, (float)y1, (float)z1).setColor(r, g, b, a);
        consumer.addVertex(entry, (float)x2, (float)y1, (float)z1).setColor(r, g, b, a);
        consumer.addVertex(entry, (float)x1, (float)y1, (float)z2).setColor(r, g, b, a);
        consumer.addVertex(entry, (float)x2, (float)y1, (float)z2).setColor(r, g, b, a);
        consumer.addVertex(entry, (float)x2, (float)y1, (float)z2).setColor(r, g, b, a);
        consumer.addVertex(entry, (float)x1, (float)y2, (float)z1).setColor(r, g, b, a);
        consumer.addVertex(entry, (float)x1, (float)y2, (float)z1).setColor(r, g, b, a);
        consumer.addVertex(entry, (float)x2, (float)y2, (float)z1).setColor(r, g, b, a);
        consumer.addVertex(entry, (float)x1, (float)y2, (float)z2).setColor(r, g, b, a);
        consumer.addVertex(entry, (float)x2, (float)y2, (float)z2).setColor(r, g, b, a);
        consumer.addVertex(entry, (float)x2, (float)y2, (float)z2).setColor(r, g, b, a);
        consumer.addVertex(entry, (float)x1, (float)y1, (float)z2).setColor(r, g, b, a);
        consumer.addVertex(entry, (float)x1, (float)y1, (float)z2).setColor(r, g, b, a);
        consumer.addVertex(entry, (float)x2, (float)y1, (float)z2).setColor(r, g, b, a);
        consumer.addVertex(entry, (float)x1, (float)y2, (float)z2).setColor(r, g, b, a);
        consumer.addVertex(entry, (float)x2, (float)y2, (float)z2).setColor(r, g, b, a);
        consumer.addVertex(entry, (float)x2, (float)y2, (float)z2).setColor(r, g, b, a);
        consumer.addVertex(entry, (float)x1, (float)y1, (float)z1).setColor(r, g, b, a);
        consumer.addVertex(entry, (float)x1, (float)y1, (float)z1).setColor(r, g, b, a);
        consumer.addVertex(entry, (float)x2, (float)y1, (float)z1).setColor(r, g, b, a);
        consumer.addVertex(entry, (float)x1, (float)y2, (float)z1).setColor(r, g, b, a);
        consumer.addVertex(entry, (float)x2, (float)y2, (float)z1).setColor(r, g, b, a);
        consumer.addVertex(entry, (float)x2, (float)y2, (float)z1).setColor(r, g, b, a);
        consumer.addVertex(entry, (float)x1, (float)y1, (float)z1).setColor(r, g, b, a);
        consumer.addVertex(entry, (float)x1, (float)y1, (float)z1).setColor(r, g, b, a);
        consumer.addVertex(entry, (float)x1, (float)y1, (float)z2).setColor(r, g, b, a);
        consumer.addVertex(entry, (float)x1, (float)y2, (float)z1).setColor(r, g, b, a);
        consumer.addVertex(entry, (float)x1, (float)y2, (float)z2).setColor(r, g, b, a);
        consumer.addVertex(entry, (float)x1, (float)y2, (float)z2).setColor(r, g, b, a);
        consumer.addVertex(entry, (float)x2, (float)y1, (float)z1).setColor(r, g, b, a);
        consumer.addVertex(entry, (float)x2, (float)y1, (float)z1).setColor(r, g, b, a);
        consumer.addVertex(entry, (float)x2, (float)y1, (float)z2).setColor(r, g, b, a);
        consumer.addVertex(entry, (float)x2, (float)y2, (float)z1).setColor(r, g, b, a);
        consumer.addVertex(entry, (float)x2, (float)y2, (float)z2).setColor(r, g, b, a);
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
