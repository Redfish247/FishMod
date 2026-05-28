package fishmod.features;

import fishmod.utils.Constants;
import fishmod.utils.config.values.FishSettings;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import fishmod.utils.events.Events;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Trophy Frogs HUD. Baseline counts are read from the in-game "Trophy Frogs" menu (each frog item's
 * lore lists Bronze/Silver/Gold/Diamond counts); after that, catch messages in chat increment the
 * counts live so the HUD stays current without reopening the menu. Persisted across sessions.
 */
public class TrophyFrogTracker {

    private static final int C_NAME    = 0x55FF55;
    private static final int C_TOTAL   = 0xAAAAAA;
    private static final int C_MISSING = 0x808080; // gray "-" for a tier with 0 caught
    private static final int C_SEP     = 0x555555;
    private static final int C_BRONZE  = 0xCD7F32;
    private static final int C_SILVER  = 0xC8C8D0;
    private static final int C_GOLD    = 0xFFD700;
    private static final int C_DIAMOND = 0x5DECF5;
    private static final int C_HEADER  = 0x55FFFF;

    private static final String[] TIERS  = {"bronze", "silver", "gold", "diamond"};
    private static final int[]    COLORS = {C_BRONZE, C_SILVER, C_GOLD, C_DIAMOND};

    // Known frog names so catch-message increments work even before the menu is opened.
    private static final String[] KNOWN_FROGS = {
            "Common Frog", "Exploding Frog", "Leap Frog", "Reality Hopper", "Wetlands Frog",
            "Blessed Frog", "Bullfrog", "Sea Frog", "Cave Frog", "Highlands Frog", "Tree Frog",
            "Puddle Jumper",
    };

    private static final boolean DEBUG = true;
    private static final Path SAVE_FILE = Paths.get("config/fishmod/trophy_frogs.json");
    private static final Path DUMP_FILE = Paths.get("config/fishmod/trophy_menu_dump.txt");

    // name → [bronze, silver, gold, diamond]; LinkedHashMap preserves menu order.
    private static final Map<String, int[]> data = new LinkedHashMap<>();
    private static Screen lastScreen = null;
    // True once chat catches have been added on top of the menu baseline (counts may have drifted).
    private static boolean needsSync = false;
    // De-dupe: a bundled chat packet fires ON_GAME_MESSAGE twice (apply + re-dispatch), so the same
    // catch message arrives back-to-back. Ignore an identical message within this window.
    private static String lastMsg = "";
    private static long lastMsgMs = 0;

    /** Read-only snapshot for the compact tab (name → [bronze, silver, gold, diamond]). */
    public static Map<String, int[]> snapshot() { return new LinkedHashMap<>(data); }
    public static boolean atoll() { return inAtoll(); }

    public static void init() {
        load();
        FishHudEditor.register("Trophy Frogs",
                () -> FishSettings.trophyFrogHudX, v -> FishSettings.trophyFrogHudX = v,
                () -> FishSettings.trophyFrogHudY, v -> FishSettings.trophyFrogHudY = v,
                160, 14 * 6,
                () -> FishSettings.trophyFrogHudScale, v -> FishSettings.trophyFrogHudScale = v,
                () -> FishSettings.trophyFrogEnabled && !data.isEmpty() && inAtoll());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (FishSettings.trophyFrogEnabled) scanMenu(client);
        });

        Events.ON_GAME_MESSAGE.register(message -> {
            if (FishSettings.trophyFrogEnabled) onChat(message.getString().replaceAll("§.", ""));
            return false;
        });
    }

    // ── menu baseline ──────────────────────────────────────────────────────────
    private static void scanMenu(MinecraftClient mc) {
        Screen scr = mc.currentScreen;
        if (!(scr instanceof HandledScreen<?> hs)
                || !(hs.getScreenHandler() instanceof GenericContainerScreenHandler handler)
                || !scr.getTitle().getString().replaceAll("§.", "").trim().equals("Trophy Frogs")) {
            lastScreen = scr; return;
        }

        Map<String, int[]> found = new LinkedHashMap<>();
        StringBuilder dump = DEBUG ? new StringBuilder("MENU: " + scr.getTitle().getString().replaceAll("§.", "") + "\n") : null;

        for (int i = 0; i < handler.slots.size(); i++) {
            ItemStack st;
            try { st = handler.slots.get(i).getStack(); } catch (Exception e) { continue; }
            if (st == null || st.isEmpty()) continue;
            String regId = net.minecraft.registry.Registries.ITEM.getId(st.getItem()).toString();
            if (regId.contains("stained_glass_pane") || regId.contains("barrier")) continue;

            String name = st.getName().getString().replaceAll("§.", "").replaceAll("\\s*x\\s*\\d+\\s*$", "").trim();
            List<String> lore = lore(st);

            int[] counts = new int[4];
            int present = 0;
            for (String line : lore) {
                String low = line.toLowerCase();
                for (int t = 0; t < 4; t++) {
                    if (!low.contains(TIERS[t])) continue;
                    present++;
                    Matcher m = Pattern.compile("\\(([\\d,]+)\\)").matcher(line);
                    counts[t] = m.find() ? toInt(m.group(1)) : 0; // no parens (e.g. Diamond ❌) = 0
                }
            }
            if (present < 2) continue; // not a frog entry
            found.put(name, counts);
            if (DEBUG) dump.append("  ITEM: ").append(name).append("\n    ")
                    .append(String.join("\n    ", lore)).append("\n");
        }

        if (!found.isEmpty()) {
            data.clear();
            data.putAll(found);
            needsSync = false; // menu is authoritative — counts are now correct
            save();
        }
        if (DEBUG && scr != lastScreen) {
            try { Files.createDirectories(DUMP_FILE.getParent()); Files.writeString(DUMP_FILE, dump.toString()); }
            catch (Exception ignored) {}
        }
        lastScreen = scr;
    }

    // Only the LOCAL player's catch: "♔ TROPHY FROG! You caught a Common Frog BRONZE! (9)".
    // Other players read "RIBBIT! [MVP+] Snubbo caught their first DIAMOND Common Frog!" — the
    // "You caught a <frog> <TIER>!" shape excludes those.
    private static final Pattern OWN_CATCH =
            Pattern.compile("(?i)You caught a (.+?) (bronze|silver|gold|diamond)!");

    // ── live chat increments ─────────────────────────────────────────────────────
    private static void onChat(String plain) {
        long now = System.currentTimeMillis();
        if (plain.equals(lastMsg) && now - lastMsgMs < 50) return; // bundled double-dispatch
        lastMsg = plain; lastMsgMs = now;

        Matcher m = OWN_CATCH.matcher(plain);
        if (!m.find()) return;
        // The real game notification has no chat prefix. If another player relays their catch into
        // a channel ("Guild > [MVP] Xeon: ... You caught a ..."), the text before the match contains
        // a ':' or '>' — reject those so we don't credit someone else's catch.
        String prefix = plain.substring(0, m.start());
        if (prefix.indexOf(':') >= 0 || prefix.indexOf('>') >= 0) return;

        String frogName = m.group(1).trim();
        int tier = switch (m.group(2).toLowerCase()) {
            case "bronze" -> 0; case "silver" -> 1; case "gold" -> 2; default -> 3;
        };

        // Resolve to a canonical name: an already-tracked frog, then the known list, else as-typed.
        String matched = null;
        for (String name : data.keySet()) if (name.equalsIgnoreCase(frogName)) { matched = name; break; }
        if (matched == null) for (String name : KNOWN_FROGS) if (name.equalsIgnoreCase(frogName)) { matched = name; break; }
        if (matched == null) matched = frogName;

        data.computeIfAbsent(matched, k -> new int[4])[tier]++;
        if (!needsSync) {
            needsSync = true;
            // One-time reminder when counts first drift from the menu baseline (not a repeated nag).
            // ON_GAME_MESSAGE fires on the network thread — touching the chat HUD off-thread crashes
            // the renderer ("Close the existing render pass"), so marshal onto the main thread.
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.execute(() -> {
                if (mc.inGameHud != null) mc.inGameHud.getChatHud().addMessage(
                        net.minecraft.text.Text.literal("§e[FishMod] §7Frog counts are now estimated — check up with §bResearcher Ribery §7to correct this menu."));
            });
        }
        save();
    }

    private static int toInt(String s) {
        try { return Integer.parseInt(s.replace(",", "")); } catch (NumberFormatException e) { return 0; }
    }

    private static List<String> lore(ItemStack stack) {
        List<String> out = new ArrayList<>();
        LoreComponent lc = stack.get(DataComponentTypes.LORE);
        if (lc == null) return out;
        for (Text line : lc.lines()) out.add(line.getString().replaceAll("§.", ""));
        return out;
    }

    // ── persistence ──────────────────────────────────────────────────────────
    private static synchronized void save() {
        try {
            Files.createDirectories(SAVE_FILE.getParent());
            JsonArray arr = new JsonArray();
            for (var e : data.entrySet()) {
                JsonObject o = new JsonObject();
                o.addProperty("name", e.getKey());
                o.addProperty("bronze", e.getValue()[0]);
                o.addProperty("silver", e.getValue()[1]);
                o.addProperty("gold", e.getValue()[2]);
                o.addProperty("diamond", e.getValue()[3]);
                arr.add(o);
            }
            Files.writeString(SAVE_FILE, arr.toString());
        } catch (Exception ignored) {}
    }

    private static synchronized void load() {
        try {
            if (!Files.exists(SAVE_FILE)) return;
            JsonArray arr = JsonParser.parseString(Files.readString(SAVE_FILE)).getAsJsonArray();
            data.clear();
            for (var el : arr) {
                JsonObject o = el.getAsJsonObject();
                data.put(o.get("name").getAsString(), new int[]{
                        o.get("bronze").getAsInt(), o.get("silver").getAsInt(),
                        o.get("gold").getAsInt(), o.get("diamond").getAsInt()});
            }
        } catch (Exception ignored) {}
    }

    // Sub-areas of the Lotus Atoll island (sidebar area line changes per sub-area). Lowercase
    // substrings — add more here if other atoll areas turn up.
    private static final String[] ATOLL_AREAS = {
            "lotus", "tewtil",
    };

    /** Detect the Lotus Atoll (incl. its sub-areas) from the SkyBlock sidebar area line. Avoids a
     *  Location enum field that blade-addons' duplicate class wouldn't have (crashed before). */
    private static boolean inAtoll() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return false;
        var sb = mc.world.getScoreboard();
        var obj = sb.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (obj == null) return false;
        for (var entry : sb.getScoreboardEntries(obj)) {
            var team = sb.getScoreHolderTeam(entry.owner());
            String raw = team != null
                    ? team.getPrefix().getString() + entry.owner() + team.getSuffix().getString()
                    : entry.name().getString();
            String line = raw.replaceAll("§.", "").toLowerCase();
            for (String a : ATOLL_AREAS) if (line.contains(a)) return true;
        }
        return false;
    }

    // ── rendering ────────────────────────────────────────────────────────────
    public static void renderHud(DrawContext ctx, RenderTickCounter tick) {
        if (!FishSettings.trophyFrogEnabled || data.isEmpty() || !inAtoll()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen)) return;
        draw(ctx, mc);
    }

    public static void renderInScreen(DrawContext ctx) {
        if (!FishSettings.trophyFrogEnabled || data.isEmpty() || !inAtoll()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!(mc.currentScreen instanceof HandledScreen<?>)) return;
        draw(ctx, mc);
    }

    private static void draw(DrawContext ctx, MinecraftClient mc) {
        int x = FishSettings.trophyFrogHudX;
        int y = FishSettings.trophyFrogHudY;
        float sc = (float) FishSettings.trophyFrogHudScale;
        int lh = Constants.TEXT_HEIGHT + 1;
        var tr = mc.textRenderer;

        int nameW = 0;
        int[] amtW = new int[4];
        for (var e : data.entrySet()) {
            int[] v = e.getValue();
            int total = v[0] + v[1] + v[2] + v[3];
            nameW = Math.max(nameW, tr.getWidth(e.getKey() + " (" + total + ")"));
            for (int i = 0; i < 4; i++) amtW[i] = Math.max(amtW[i], tr.getWidth(v[i] == 0 ? "-" : String.valueOf(v[i])));
        }
        int gap = 4;
        int sepW = tr.getWidth("| ");
        // Each tier column is just the (tier-colored) count now — no label words.
        int[] cellX = new int[4];
        int cur = nameW + gap;
        for (int i = 0; i < 4; i++) {
            cellX[i] = cur;
            cur += amtW[i] + gap + sepW;
        }

        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate((float) x, (float) y);
        ctx.getMatrices().scale(sc, sc);

        ctx.drawText(tr, "Trophy Frogs", 0, 0, 0xFF000000 | C_HEADER, true);
        int row = 1;
        for (var e : data.entrySet()) {
            int ty = lh * row;
            int[] t = e.getValue();
            int total = t[0] + t[1] + t[2] + t[3];
            ctx.drawText(tr, e.getKey() + " ", 0, ty, 0xFF000000 | C_NAME, true);
            drawTok(ctx, tr, "(" + total + ")", tr.getWidth(e.getKey() + " "), ty, C_TOTAL);
            for (int i = 0; i < 4; i++) {
                int cx = cellX[i];
                if (i > 0) drawTok(ctx, tr, "| ", cx - sepW, ty, C_SEP);
                String num = t[i] == 0 ? "-" : String.valueOf(t[i]);
                drawTok(ctx, tr, num, cx + (amtW[i] - tr.getWidth(num)), ty, t[i] == 0 ? C_MISSING : COLORS[i]);
            }
            row++;
        }
        ctx.getMatrices().popMatrix();
    }

    private static void drawTok(DrawContext ctx, net.minecraft.client.font.TextRenderer tr,
                                String s, int cx, int ty, int color) {
        ctx.drawText(tr, s, cx, ty, 0xFF000000 | color, true);
    }
}
