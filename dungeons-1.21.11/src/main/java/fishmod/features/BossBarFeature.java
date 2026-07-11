package fishmod.features;

import fishmod.mixin.accessors.BossBarHudAccessor;
import fishmod.utils.debug.Debug;
import fishmod.utils.rendering.RenderUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Map;
import java.util.UUID;

public class BossBarFeature {

    /** Called from HudRenderCallback — fires BEFORE vanilla boss bar, so only used for non-boss-bar elements. */
    public static void renderHud(DrawContext ctx) {
        // intentionally empty — boss HP drawn in renderAfterVanilla
    }

    /** Called from FishBossBarHudMixin @Inject(RETURN) — fires after vanilla draws its text. */
    public static void renderAfterVanilla(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;

        BossBarHudAccessor accessor = (BossBarHudAccessor) mc.inGameHud.getBossBarHud();
        Map<UUID, ClientBossBar> bossBars = accessor.getBossBars();
        if (bossBars == null || bossBars.isEmpty()) return;

        int screenWidth = ctx.getScaledWindowWidth();
        int y = 12;

        for (ClientBossBar bar : bossBars.values()) {
            Text customText = buildText(bar);
            if (customText != null) {
                int textWidth = mc.textRenderer.getWidth(customText);
                int textX = screenWidth / 2 - textWidth / 2;
                ctx.drawText(mc.textRenderer, customText, textX, y - 9, 0xFFFFFF, true);
            }
            y += 19;
        }
    }

    private static Text buildText(ClientBossBar bar) {
        try {
            String name = bar.getName().getString().replaceAll("§.", "").trim();
            float pct = bar.getPercent() * 100f;
            String pctStr = pct >= 10 ? String.format("%.1f%%", pct) : String.format("%.2f%%", pct);

            float maxHp;
            if (name.contains("Maxor"))       maxHp = 2.5e8f;
            else if (name.contains("Storm"))  maxHp = 5e8f;
            else if (name.contains("Goldor")) maxHp = 7.5e8f;
            else if (name.contains("Necron")) maxHp = 1e9f;
            else maxHp = -1f;

            if (maxHp < 0) {
                return Text.literal(name + " ").formatted(Formatting.RED)
                        .append(Text.literal(pctStr).formatted(Formatting.GREEN));
            }

            float currHp = maxHp * bar.getPercent();
            return Text.literal(name + " ").formatted(Formatting.RED)
                    .append(Text.literal(RenderUtils.formatNumber(currHp)).formatted(Formatting.GREEN))
                    .append(Text.literal("/").formatted(Formatting.GRAY))
                    .append(Text.literal(RenderUtils.formatNumber(maxHp)).formatted(Formatting.GREEN));
        } catch (Exception e) {
            Debug.LOGGER.error("BossBarFeature buildText error: {}", e.getMessage());
            return null;
        }
    }
}
