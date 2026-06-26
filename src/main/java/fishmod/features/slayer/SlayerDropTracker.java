package fishmod.features.slayer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fishmod.utils.Constants;
import fishmod.utils.config.values.FishSettings;
import fishmod.utils.events.Events;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.RenderTickCounter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Slayer Drop Tracker — a small session HUD counting RARE / VERY RARE / CRAZY RARE / INSANE drops
 * (and "PRAISE RNGESUS" pulls) while a slayer quest is active. Persisted; reset from the panel button
 * when an inventory/menu screen is open.
 */
public final class SlayerDropTracker {

    private SlayerDropTracker() {}

    private static final Path SAVE_FILE = Paths.get("config/fishmod/slayer_drops.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // counts: [rare, veryRare, crazyRare, insane, praise]
    private static final int RARE = 0, VERY = 1, CRAZY = 2, INSANE = 3, PRAISE = 4;
    private static final long[] counts = new long[5];

    private static boolean questActive = false;
    private static long graceUntilMs = 0; // keep counting briefly after completion for boss drops
    private static String lastMsg = "";
    private static long lastMsgMs = 0;

    private static int btnX, btnY, btnW, btnH;
    private static boolean btnVisible = false;

    private static class SaveData { long[] counts; }

    public static void init() {
        load();
        Events.ON_GAME_MESSAGE.register(text -> {
            if (!FishSettings.slayerDropsEnabled || text == null) return false;
            onChat(text.getString().replaceAll("§.", "").trim());
            return false;
        });
    }

    private static void onChat(String plain) {
        long now = System.currentTimeMillis();
        if (plain.equals(lastMsg) && now - lastMsgMs < 80) return;
        lastMsg = plain; lastMsgMs = now;

        if (plain.contains("SLAYER QUEST STARTED")) { questActive = true; return; }
        if (plain.contains("SLAYER QUEST COMPLETE") || plain.contains("SLAYER QUEST FAILED")) {
            graceUntilMs = now + 4000;
            questActive = false;
            return;
        }

        if (!questActive && now > graceUntilMs) return;

        String up = plain.toUpperCase();
        boolean hit = false;
        if (up.contains("PRAISE RNGESUS"))           { counts[PRAISE]++; hit = true; }
        else if (up.contains("INSANE DROP"))          { counts[INSANE]++; hit = true; }
        else if (up.contains("CRAZY RARE DROP"))      { counts[CRAZY]++;  hit = true; }
        else if (up.contains("VERY RARE DROP"))       { counts[VERY]++;   hit = true; }
        else if (up.contains("RARE DROP"))            { counts[RARE]++;   hit = true; }
        if (hit) save();
    }

    public static void reset() {
        for (int i = 0; i < counts.length; i++) counts[i] = 0;
        save();
    }

    private static boolean any() {
        for (long c : counts) if (c > 0) return true;
        return false;
    }

    private static String[] buildLines() {
        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add("§5§lSlayer Drops");
        if (counts[RARE]   > 0) lines.add("§9Rare: §f"        + counts[RARE]);
        if (counts[VERY]   > 0) lines.add("§5Very Rare: §f"   + counts[VERY]);
        if (counts[CRAZY]  > 0) lines.add("§dCrazy Rare: §f"  + counts[CRAZY]);
        if (counts[INSANE] > 0) lines.add("§cInsane: §f"      + counts[INSANE]);
        if (counts[PRAISE] > 0) lines.add("§6Praise: §f"      + counts[PRAISE]);
        if (lines.size() == 1) lines.add("§8(no drops yet)");
        return lines.toArray(new String[0]);
    }

    // ── persistence ──────────────────────────────────────────────────────────
    private static synchronized void save() {
        try {
            Files.createDirectories(SAVE_FILE.getParent());
            SaveData d = new SaveData();
            d.counts = counts.clone();
            Files.writeString(SAVE_FILE, GSON.toJson(d));
        } catch (Exception ignored) {}
    }

    private static synchronized void load() {
        try {
            if (!Files.exists(SAVE_FILE)) return;
            SaveData d = GSON.fromJson(Files.readString(SAVE_FILE), SaveData.class);
            if (d == null || d.counts == null) return;
            for (int i = 0; i < Math.min(counts.length, d.counts.length); i++) counts[i] = d.counts[i];
        } catch (Exception ignored) {}
    }

    // ── rendering ────────────────────────────────────────────────────────────
    public static boolean isVisible() { return FishSettings.slayerDropsEnabled; }

    public static void renderHud(DrawContext ctx, RenderTickCounter tick) {
        btnVisible = false;
        if (!FishSettings.slayerDropsEnabled || !any()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen)) return;
        draw(ctx, mc, buildLines());
    }

    public static void renderInScreen(DrawContext ctx, int mouseX, int mouseY) {
        btnVisible = false;
        if (!FishSettings.slayerDropsEnabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!(mc.currentScreen instanceof HandledScreen<?>)) return;

        String[] lines = buildLines();
        int x = FishSettings.slayerDropsHudX, y = FishSettings.slayerDropsHudY;
        float sc = (float) FishSettings.slayerDropsScale;
        int lh = Constants.TEXT_HEIGHT + 1;
        draw(ctx, mc, lines);

        String label = "§l[ Reset ]";
        int padX = 4, padY = 3;
        int localY = lh * lines.length - 2;
        btnX = x;
        btnY = y + (int) (localY * sc);
        btnW = (int) ((mc.textRenderer.getWidth(label) + padX * 2) * sc);
        btnH = (int) ((Constants.TEXT_HEIGHT + padY * 2 + 1) * sc);
        boolean hover = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate((float) x, (float) y);
        ctx.getMatrices().scale(sc, sc);
        ctx.drawText(mc.textRenderer, hover ? "§c§l[ Reset ]" : label, padX, localY + padY, 0xFFFFFFFF, true);
        ctx.getMatrices().popMatrix();
        btnVisible = true;
    }

    public static boolean handleScreenClick(double mx, double my) {
        if (!btnVisible) return false;
        if (mx >= btnX && mx <= btnX + btnW && my >= btnY && my <= btnY + btnH) {
            reset();
            return true;
        }
        return false;
    }

    private static void draw(DrawContext ctx, MinecraftClient mc, String[] lines) {
        int x = FishSettings.slayerDropsHudX, y = FishSettings.slayerDropsHudY;
        float sc = (float) FishSettings.slayerDropsScale;
        int lh = Constants.TEXT_HEIGHT + 1;
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate((float) x, (float) y);
        ctx.getMatrices().scale(sc, sc);
        for (int i = 0; i < lines.length; i++)
            ctx.drawText(mc.textRenderer, lines[i], 0, lh * i, 0xFFFFFFFF, true);
        ctx.getMatrices().popMatrix();
    }
}
