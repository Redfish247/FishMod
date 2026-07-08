package fishmod.features;

import fishmod.utils.Constants;
import fishmod.utils.Location;
import fishmod.utils.config.values.FishSettings;
import fishmod.utils.events.Events;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
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
    /** Rare crop drop message — fired even when the seasoning is auto-donated, so this is
     *  the only reliable signal for Seasoning counts (sacks hover never sees them).
     *  The "+N☀" in the message is sun XP, NOT a seasoning quantity, so we just count +1
     *  per match (one drop event = one seasoning). */
    private static final Pattern SEASONING_DROP =
            Pattern.compile("RARE CROP!\\s+Seasoning\\b");

    private static final Map<String, Long> counts = new LinkedHashMap<>();
    private static long sessionStartMs = -1;

    /** item-line signature → expiry-ms. Hypixel sometimes fires the same [Sacks] hover via
     *  multiple sibling chat components (and occasionally separate chat messages) — without
     *  this debounce the same drop is parsed N times. Keyed by "sign|name|count". */
    private static final Map<String, Long> recentLineExpiry = new java.util.HashMap<>();
    private static final long DEDUP_WINDOW_MS = 2500L;

    /** Last accepted "RARE CROP! Seasoning" timestamp — used to dedupe multi-channel echoes. */
    private static long lastSeasoningMs = 0L;
    private static final long SEASONING_DEDUP_MS = 1500L;

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
            // Auto-donated Seasoning never hits the sacks hover — catch it here.
            // One match = one seasoning drop, regardless of the "+N☀" sun-XP number.
            // The same RARE CROP line is delivered through several channels (system chat,
            // overlay, etc.) so the handler fires multiple times per drop — debounce a
            // short window to keep one drop = one count.
            if (SEASONING_DROP.matcher(plain).find()) {
                long now = System.currentTimeMillis();
                if (now - lastSeasoningMs < SEASONING_DEDUP_MS) return false;
                lastSeasoningMs = now;
                counts.merge("Seasoning", 1L, Long::sum);
                if (sessionStartMs < 0) sessionStartMs = now;
                save();
                return false;
            }
            // Only parse the canonical [Sacks] chat line. Hypixel also sends an "Added items:"
            // summary message with its own hover containing the same items — including both
            // double/triple-counted every drop, since per-call hover-text dedup can't dedupe
            // across separate ON_GAME_MESSAGE invocations.
            if (!plain.contains("[Sacks]")) return false;
            parseHover(message);
            return false;
        });
    }

    private static void parseHover(net.minecraft.network.chat.Component root) {
        StringBuilder hover = new StringBuilder();
        collectHover(root, hover, new java.util.HashSet<>());
        if (hover.length() == 0) return;
        long now = System.currentTimeMillis();
        // Sweep expired dedup entries first so the map doesn't grow unbounded.
        recentLineExpiry.entrySet().removeIf(e -> e.getValue() < now);
        boolean any = false;
        for (String line : hover.toString().split("\\n|\\r")) {
            String s = COLOR_STRIP.matcher(line).replaceAll("").trim();
            Matcher m = SACK_HOVER_LINE.matcher(s);
            if (!m.find()) continue;
            boolean removal = m.group(1).equals("-");
            // Ignore removals entirely: a "-160 Cornucopia" almost always means the sack
            // auto-compacted 160 raws (already credited as drops) into an enchanted form.
            // Subtracting would undo a genuine drop. Compaction events therefore net to zero.
            if (removal) continue;
            String name = m.group(3).trim();
            if (!ITEMS.containsKey(name)) continue;
            long count;
            try { count = Long.parseLong(m.group(2).replace(",", "")); } catch (NumberFormatException e) { continue; }
            // Skip if we've seen this exact line within the dedup window.
            String key = "+|" + name + "|" + count;
            if (recentLineExpiry.containsKey(key)) continue;
            recentLineExpiry.put(key, now + DEDUP_WINDOW_MS);
            counts.merge(name, count, Long::sum);
            any = true;
        }
        if (any) {
            if (sessionStartMs < 0) sessionStartMs = now;
            save();
        }
    }

    private static void collectHover(net.minecraft.network.chat.Component t, StringBuilder out, java.util.Set<String> seen) {
        // A single chat line is split into many sibling components that all share the SAME hover
        // tooltip. Without de-duping, each identical hover gets parsed once per sibling, which
        // multiplied every drop count (≈6x). Only append each distinct hover text once.
        if (t.getStyle() != null && t.getStyle().getHoverEvent() instanceof net.minecraft.network.chat.HoverEvent.ShowText st) {
            String text = st.value().getString();
            if (seen.add(text)) out.append(text).append('\n');
        }
        for (net.minecraft.network.chat.Component sib : t.getSiblings()) collectHover(sib, out, seen);
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
        // Seasoning is rendered as a dedicated bottom line (always shown), so skip it here.
        java.util.List<String> lines = new java.util.ArrayList<>();
        long total = 0;
        for (Map.Entry<String, String> e : ITEMS.entrySet()) {
            if ("Seasoning".equals(e.getKey())) continue;
            Long c = counts.get(e.getKey());
            if (c == null || c == 0) continue;
            total += c;
            lines.add("§7" + e.getValue() + ": §a" + fmt(c));
        }
        if (lines.isEmpty()) lines.add("§7No drops yet");
        else lines.add(0, "§6§lHarvest Feast §7(" + fmt(total) + ")");
        // Dedicated Seasoning line (dark green label, gray number) — always shown so the
        // player can see how many they've banked even before the first drop.
        Long seasoning = counts.get("Seasoning");
        long sc = seasoning == null ? 0 : seasoning;
        lines.add("§2Seasoning: §7" + fmt(sc));
        return lines.toArray(new String[0]);
    }

    public static void renderHud(GuiGraphicsExtractor ctx, DeltaTracker tick) {
        btnVisible = false;
        if (!FishSettings.harvestFeastEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.screen != null && !(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) return;
        if (!inFarmingArea()) return;

        int x = FishSettings.harvestFeastHudX;
        int y = FishSettings.harvestFeastHudY;
        int lh = Constants.TEXT_HEIGHT + 1;
        String[] lines = buildLines();
        float sc = (float) FishSettings.harvestFeastScale;
        ctx.pose().pushMatrix();
        ctx.pose().translate((float) x, (float) y);
        ctx.pose().scale(sc, sc);
        for (int i = 0; i < lines.length; i++)
            ctx.text(mc.font, lines[i], 0, lh * i, 0xFFFFFFFF, true);
        ctx.pose().popMatrix();
    }

    public static void renderInScreen(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        btnVisible = false;
        if (!FishSettings.harvestFeastEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?>)) return;
        if (!inFarmingArea()) return;

        int x = FishSettings.harvestFeastHudX;
        int y = FishSettings.harvestFeastHudY;
        int lh = Constants.TEXT_HEIGHT + 1;
        String[] lines = buildLines();
        float sc = (float) FishSettings.harvestFeastScale;

        String resetLabel = "§l[ Reset ]";
        int resetW = mc.font.width(resetLabel);
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

        ctx.pose().pushMatrix();
        ctx.pose().translate((float) x, (float) y);
        ctx.pose().scale(sc, sc);
        for (int i = 0; i < lines.length; i++)
            ctx.text(mc.font, lines[i], 0, lh * i, 0xFFFFFFFF, true);
        ctx.text(mc.font, shownReset, padX, localBtnY + padY, 0xFFFFFFFF, true);
        ctx.pose().popMatrix();
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
