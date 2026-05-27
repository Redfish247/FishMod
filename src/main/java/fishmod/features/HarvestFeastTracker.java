package fishmod.features;

import fishmod.utils.Constants;
import fishmod.utils.Location;
import fishmod.utils.config.values.FishSettings;
import fishmod.utils.events.Events;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.RenderTickCounter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Counts all Harvest Feast event drops (raw counts, not coin value).
 * Reads from the same [Sacks] chat hover that FarmingTracker uses.
 */
public class HarvestFeastTracker {

    /** Display-name → friendly short name (for compact HUD). */
    private static final LinkedHashMap<String, String> ITEMS = new LinkedHashMap<>();
    static {
        ITEMS.put("Cornucopia",             "Wheat");
        ITEMS.put("Carrot Zest",            "Carrot");
        ITEMS.put("Deepfries",              "Potato");
        ITEMS.put("Aggourdian",             "Pumpkin");
        ITEMS.put("Cane Knot",              "Cane");
        ITEMS.put("Melon Juice",            "Melon");
        ITEMS.put("Cactus Flower",          "Cactus");
        ITEMS.put("Designer Coffee Beans",  "Cocoa");
        ITEMS.put("Feastfungus",            "Mush");
        ITEMS.put("Botroot",                "Wart");
        ITEMS.put("Salted Sunflower Seeds", "Sunflower");
        ITEMS.put("Crystalized Moonlight",  "Moonlight");
        ITEMS.put("Floral Gelatin",         "Gelatin");
        ITEMS.put("Seasoning",              "Seasoning");
    }

    private static final Pattern SACK_HOVER_LINE =
            Pattern.compile("([+-])\\s*([\\d,]+)\\s+([A-Za-z][A-Za-z ]+?)(?:\\s+\\(.*?\\))?\\s*$");
    private static final Pattern COLOR_STRIP = Pattern.compile("§.");

    private static final Map<String, Long> counts = new LinkedHashMap<>();
    private static long sessionStartMs = -1;

    private static final Path SAVE_FILE = Paths.get("config/fishmod/harvest_feast_tracker.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static int btnX, btnY, btnW, btnH;
    private static boolean btnVisible = false;

    private static class SaveData {
        Map<String, Long> counts;
        long sessionStartMs;
    }

    public static void init() {
        load();
        FishHudEditor.register("Harvest Feast",
                () -> FishSettings.harvestFeastHudX, v -> FishSettings.harvestFeastHudX = v,
                () -> FishSettings.harvestFeastHudY, v -> FishSettings.harvestFeastHudY = v,
                160, 14 * 6,
                () -> FishSettings.harvestFeastScale, v -> FishSettings.harvestFeastScale = v,
                () -> FishSettings.harvestFeastEnabled && inFarmingArea());

        Events.ON_GAME_MESSAGE.register(message -> {
            if (!FishSettings.harvestFeastEnabled) return false;
            String plain = COLOR_STRIP.matcher(message.getString()).replaceAll("");
            if (!plain.contains("[Sacks]") && !plain.contains("Added items")) return false;
            parseHover(message);
            return false;
        });
    }

    private static void parseHover(net.minecraft.text.Text root) {
        StringBuilder hover = new StringBuilder();
        collectHover(root, hover, new java.util.HashSet<>());
        if (hover.length() == 0) return;
        long now = System.currentTimeMillis();
        boolean any = false;
        for (String line : hover.toString().split("\\n|\\r")) {
            String s = COLOR_STRIP.matcher(line).replaceAll("").trim();
            Matcher m = SACK_HOVER_LINE.matcher(s);
            if (!m.find()) continue;
            int sign = m.group(1).equals("-") ? -1 : 1;
            String name = m.group(3).trim();
            if (!ITEMS.containsKey(name)) continue;
            long count;
            try { count = Long.parseLong(m.group(2).replace(",", "")); } catch (NumberFormatException e) { continue; }
            counts.merge(name, sign * count, Long::sum);
            if (counts.get(name) < 0) counts.put(name, 0L);
            any = true;
        }
        if (any) {
            if (sessionStartMs < 0) sessionStartMs = now;
            save();
        }
    }

    private static void collectHover(net.minecraft.text.Text t, StringBuilder out, java.util.Set<String> seen) {
        // A single chat line is split into many sibling components that all share the SAME hover
        // tooltip. Without de-duping, each identical hover gets parsed once per sibling, which
        // multiplied every drop count (≈6x). Only append each distinct hover text once.
        if (t.getStyle() != null && t.getStyle().getHoverEvent() instanceof net.minecraft.text.HoverEvent.ShowText st) {
            String text = st.value().getString();
            if (seen.add(text)) out.append(text).append('\n');
        }
        for (net.minecraft.text.Text sib : t.getSiblings()) collectHover(sib, out, seen);
    }

    private static boolean inFarmingArea() {
        return Location.in(Location.GARDEN) || Location.in(Location.THE_FARMING_ISLANDS);
    }

    public static void reset() {
        counts.clear();
        sessionStartMs = -1;
        save();
    }

    private static synchronized void load() {
        try {
            if (!Files.exists(SAVE_FILE)) return;
            SaveData d = GSON.fromJson(Files.readString(SAVE_FILE), SaveData.class);
            if (d == null) return;
            sessionStartMs = d.sessionStartMs;
            if (d.counts != null) counts.putAll(d.counts);
        } catch (IOException | RuntimeException ignored) {}
    }

    private static synchronized void save() {
        try {
            Files.createDirectories(SAVE_FILE.getParent());
            SaveData d = new SaveData();
            d.counts = new LinkedHashMap<>(counts);
            d.sessionStartMs = sessionStartMs;
            Files.writeString(SAVE_FILE, GSON.toJson(d));
        } catch (IOException ignored) {}
    }

    private static String fmt(long n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000)     return String.format("%.1fk", n / 1_000.0);
        return Long.toString(n);
    }

    private static String[] buildLines() {
        // Only show items the player has collected at least one of (compact display).
        java.util.List<String> lines = new java.util.ArrayList<>();
        long total = 0;
        for (Map.Entry<String, String> e : ITEMS.entrySet()) {
            Long c = counts.get(e.getKey());
            if (c == null || c == 0) continue;
            total += c;
            lines.add("§7" + e.getValue() + ": §a" + fmt(c));
        }
        if (lines.isEmpty()) lines.add("§7No drops yet");
        else lines.add(0, "§6§lHarvest Feast §7(" + fmt(total) + ")");
        return lines.toArray(new String[0]);
    }

    public static void renderHud(DrawContext ctx, RenderTickCounter tick) {
        btnVisible = false;
        if (!FishSettings.harvestFeastEnabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen)) return;
        if (!inFarmingArea()) return;

        int x = FishSettings.harvestFeastHudX;
        int y = FishSettings.harvestFeastHudY;
        int lh = Constants.TEXT_HEIGHT + 1;
        String[] lines = buildLines();
        float sc = (float) FishSettings.harvestFeastScale;
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate((float) x, (float) y);
        ctx.getMatrices().scale(sc, sc);
        for (int i = 0; i < lines.length; i++)
            ctx.drawText(mc.textRenderer, lines[i], 0, lh * i, 0xFFFFFFFF, true);
        ctx.getMatrices().popMatrix();
    }

    public static void renderInScreen(DrawContext ctx, int mouseX, int mouseY) {
        btnVisible = false;
        if (!FishSettings.harvestFeastEnabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!(mc.currentScreen instanceof HandledScreen<?>)) return;
        if (!inFarmingArea()) return;

        int x = FishSettings.harvestFeastHudX;
        int y = FishSettings.harvestFeastHudY;
        int lh = Constants.TEXT_HEIGHT + 1;
        String[] lines = buildLines();
        float sc = (float) FishSettings.harvestFeastScale;

        String resetLabel = "§l[ Reset ]";
        int resetW = mc.textRenderer.getWidth(resetLabel);
        int padX = 4, padY = 3;
        int localBtnY = lh * lines.length - 2;
        int localResetW = resetW + padX * 2;
        int localBtnH = Constants.TEXT_HEIGHT + padY * 2 + 1;
        btnX = x;
        btnY = y + (int)(localBtnY * sc);
        btnW = (int)(localResetW * sc);
        btnH = (int)(localBtnH * sc);
        boolean resetHover = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        String shownReset = resetHover ? "§c§l[ Reset ]" : resetLabel;

        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate((float) x, (float) y);
        ctx.getMatrices().scale(sc, sc);
        for (int i = 0; i < lines.length; i++)
            ctx.drawText(mc.textRenderer, lines[i], 0, lh * i, 0xFFFFFFFF, true);
        ctx.drawText(mc.textRenderer, shownReset, padX, localBtnY + padY, 0xFFFFFFFF, true);
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
}
