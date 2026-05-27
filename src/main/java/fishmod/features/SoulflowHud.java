package fishmod.features;

import fishmod.utils.Constants;
import fishmod.utils.Location;
import fishmod.utils.config.values.FishSettings;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderTickCounter;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SoulflowHud {

    private static final Pattern SF_PATTERN = Pattern.compile("Soulflow:\\s*([\\d,]+)");
    private static final Pattern COLOR_STRIP = Pattern.compile("§.");

    private static int soulflow = -1;
    private static int tickCount = 0;

    private static int missCount = 0;
    private static boolean warnedThisSession = false;
    private static final int MISS_SCANS_BEFORE_WARN = 5; // ~5s at 10-tick scan interval

    public static void init() {
        FishHudEditor.register("Soulflow",
                () -> FishSettings.soulflowHudX, v -> FishSettings.soulflowHudX = v,
                () -> FishSettings.soulflowHudY, v -> FishSettings.soulflowHudY = v,
                145, 14,
                () -> FishSettings.soulflowHudScale, v -> FishSettings.soulflowHudScale = v);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!FishSettings.soulflowHudEnabled) return;
            if (client.player == null || client.getNetworkHandler() == null) {
                soulflow = -1;
                return;
            }
            if (!Location.inSkyblock()) {
                soulflow = -1;
                return;
            }
            tickCount++;
            if (tickCount < 10) return;
            tickCount = 0;
            scanTabList(client.getNetworkHandler());
        });
    }

    private static void scanTabList(ClientPlayNetworkHandler handler) {
        Collection<PlayerListEntry> entries = handler.getPlayerList();
        for (PlayerListEntry entry : entries) {
            if (entry.getDisplayName() == null) continue;
            String text = COLOR_STRIP.matcher(entry.getDisplayName().getString()).replaceAll("").trim();
            Matcher m = SF_PATTERN.matcher(text);
            if (m.find()) {
                String numStr = m.group(1).replace(",", "");
                try {
                    soulflow = Integer.parseInt(numStr);
                } catch (NumberFormatException ignored) {
                    soulflow = -1;
                }
                missCount = 0;
                warnedThisSession = false;
                return;
            }
        }
        soulflow = -1;
        missCount++;
        if (FishSettings.soulflowMissingNotifier
                && !warnedThisSession
                && missCount >= MISS_SCANS_BEFORE_WARN) {
            warnedThisSession = true;
            fishmod.utils.Misc.addChatMessage(net.minecraft.text.Text.literal(
                "§3[FishMod] §eSoulflow not visible in /tab. Run §b/tab §e→ click §bProfile §e→ enable §bSoulflow§e."
            ));
        }
    }

    public static void renderHud(DrawContext ctx, RenderTickCounter tickCounter) {
        if (!FishSettings.soulflowHudEnabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        if (!Location.inSkyblock()) return;
        if (soulflow < 0) return;

        // Check for the warning threshold
        boolean warn = FishSettings.soulflowWarningThreshold > 0
                && soulflow < FishSettings.soulflowWarningThreshold;

        // Force the format: \u00A7e is Yellow (&e), \u00A7
        // If warning, we can keep it Red or add the warning symbol
        String label;
        if (warn) {
            label = "§3Soulflow: " + String.format("%,d", soulflow) + " ⚠";
        } else {
            label = "§3Soulflow: §f" + String.format("%,d", soulflow);
        }

        float sc = (float) FishSettings.soulflowHudScale;
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate((float) FishSettings.soulflowHudX, (float) FishSettings.soulflowHudY);
        ctx.getMatrices().scale(sc, sc);
        ctx.drawText(mc.textRenderer, label, 0, 0, -1, true);
        ctx.getMatrices().popMatrix();
    }
}