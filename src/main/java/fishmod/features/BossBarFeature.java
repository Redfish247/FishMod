package fishmod.features;

import fishmod.mixin.accessors.BossBarHudAccessor;
import fishmod.utils.debug.Debug;
import fishmod.utils.rendering.RenderUtils;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;

public class BossBarFeature {

    /** Called from HudRenderCallback — fires BEFORE vanilla boss bar, so only used for non-boss-bar elements. */
    public static void renderHud(GuiGraphicsExtractor ctx) {
        // intentionally empty — boss HP drawn in renderAfterVanilla
    }

    /** Called from FishBossBarHudMixin @Inject(RETURN) — fires after vanilla draws its text. */
    public static void renderAfterVanilla(GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        BossBarHudAccessor accessor = (BossBarHudAccessor) mc.gui.getBossOverlay();
        Map<UUID, LerpingBossEvent> bossBars = accessor.getBossBars();
        if (bossBars == null || bossBars.isEmpty()) return;

        int screenWidth = ctx.guiWidth();
        int y = 12;

        for (LerpingBossEvent bar : bossBars.values()) {
            Component customText = buildText(bar);
            if (customText != null) {
                int textWidth = mc.font.width(customText);
                int textX = screenWidth / 2 - textWidth / 2;
                ctx.text(mc.font, customText, textX, y - 9, 0xFFFFFF, true);
            }
            y += 19;
        }
    }

    private static Component buildText(LerpingBossEvent bar) {
        try {
            String name = bar.getName().getString().replaceAll("§.", "").trim();
            float pct = bar.getProgress() * 100f;
            String pctStr = pct >= 10 ? String.format("%.1f%%", pct) : String.format("%.2f%%", pct);

            float maxHp;
            if (name.contains("Maxor"))       maxHp = 2.5e8f;
            else if (name.contains("Storm"))  maxHp = 5e8f;
            else if (name.contains("Goldor")) maxHp = 7.5e8f;
            else if (name.contains("Necron")) maxHp = 1e9f;
            else maxHp = -1f;

            if (maxHp < 0) {
                return Component.literal(name + " ").withStyle(ChatFormatting.RED)
                        .append(Component.literal(pctStr).withStyle(ChatFormatting.GREEN));
            }

            float currHp = maxHp * bar.getProgress();
            return Component.literal(name + " ").withStyle(ChatFormatting.RED)
                    .append(Component.literal(RenderUtils.formatNumber(currHp)).withStyle(ChatFormatting.GREEN))
                    .append(Component.literal("/").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(RenderUtils.formatNumber(maxHp)).withStyle(ChatFormatting.GREEN));
        } catch (Exception e) {
            Debug.LOGGER.error("BossBarFeature buildText error: {}", e.getMessage());
            return null;
        }
    }
}
