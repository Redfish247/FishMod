package fishmod.features.fishing;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fishmod.utils.Constants;
import fishmod.utils.Location;
import fishmod.utils.config.values.FishSettings;
import fishmod.utils.events.Events;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
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
 * Trophy Fish HUD (Crimson Isle). Baseline counts come from the "Trophy Fishing" menu (each fish
 * item's lore lists Bronze/Silver/Gold/Diamond counts); afterwards, catch messages in chat keep the
 * counts live without reopening the menu. Same shape as {@link fishmod.features.TrophyFrogTracker}.
 */
public final class TrophyFishTracker {

    private TrophyFishTracker() {}

    private static final int C_NAME    = 0x55FF55;
    private static final int C_TOTAL   = 0xAAAAAA;
    private static final int C_MISSING = 0x808080;
    private static final int C_SEP     = 0x555555;
    private static final int C_BRONZE  = 0xCD7F32;
    private static final int C_SILVER  = 0xC8C8D0;
    private static final int C_GOLD    = 0xFFD700;
    private static final int C_DIAMOND = 0x5DECF5;
    private static final int C_HEADER  = 0xFF5555;

    private static final String[] TIERS  = {"bronze", "silver", "gold", "diamond"};
    private static final int[]    COLORS = {C_BRONZE, C_SILVER, C_GOLD, C_DIAMOND};

    // Fallback name list so chat increments work before the menu has been opened.
    private static final String[] KNOWN_FISH = {
            "Sulphur Skitter", "Steaming-Hot Flounder", "Gusher", "Blobfish", "Slugfish", "Flyfish",
            "Lavahorse", "Mana Ray", "Volcanic Stonefish", "Vanille", "Skeleton Fish", "Moldfin",
            "Soul Fish", "Karate Fish", "Golden Fish", "Obfuscated 1", "Obfuscated 2", "Obfuscated 3",
    };

    private static final Path SAVE_FILE = Paths.get("config/fishmod/trophy_fish.json");

    private static final Map<String, int[]> data = new LinkedHashMap<>();
    private static Screen lastScreen = null;
    private static boolean needsSync = false;
    private static String lastMsg = "";
    private static long lastMsgMs = 0;

    public static Map<String, int[]> snapshot() { return new LinkedHashMap<>(data); }

    public static void init() {
        load();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (FishSettings.trophyFishEnabled) scanMenu(client);
        });
        Events.ON_GAME_MESSAGE.register(message -> {
            if (FishSettings.trophyFishEnabled) onChat(message.getString().replaceAll("§.", ""));
            return false;
        });
    }

    // ── menu baseline ──────────────────────────────────────────────────────────
    private static void scanMenu(Minecraft mc) {
        Screen scr = mc.screen;
        if (!(scr instanceof AbstractContainerScreen<?> hs)
                || !(hs.getMenu() instanceof ChestMenu handler)
                || !scr.getTitle().getString().replaceAll("§.", "").trim().contains("Trophy Fish")) {
            lastScreen = scr; return;
        }

        Map<String, int[]> found = new LinkedHashMap<>();
        for (int i = 0; i < handler.slots.size(); i++) {
            ItemStack st;
            try { st = handler.slots.get(i).getItem(); } catch (Exception e) { continue; }
            if (st == null || st.isEmpty()) continue;
            String regId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
            if (regId.contains("stained_glass_pane") || regId.contains("barrier")) continue;

            String name = st.getHoverName().getString().replaceAll("§.", "").replaceAll("\\s*x\\s*\\d+\\s*$", "").trim();
            int[] counts = new int[4];
            int present = 0;
            for (String line : lore(st)) {
                String low = line.toLowerCase();
                for (int t = 0; t < 4; t++) {
                    if (!low.contains(TIERS[t])) continue;
                    present++;
                    Matcher m = Pattern.compile("\\(([\\d,]+)\\)").matcher(line);
                    counts[t] = m.find() ? toInt(m.group(1)) : 0;
                }
            }
            if (present < 2) continue;
            found.put(name, counts);
        }

        if (!found.isEmpty()) {
            data.clear();
            data.putAll(found);
            needsSync = false;
            save();
        }
        lastScreen = scr;
    }

    // Local catch line, tier either before or after the name:
    //   "You caught a Gusher BRONZE!"  or  "You caught a Bronze Gusher!"
    private static final Pattern CATCH =
            Pattern.compile("(?i)You caught (?:a |an )?(.+?)!");
    private static final Pattern TIER_TOKEN =
            Pattern.compile("(?i)\\b(bronze|silver|gold|diamond)\\b");

    private static void onChat(String plain) {
        long now = System.currentTimeMillis();
        if (plain.equals(lastMsg) && now - lastMsgMs < 80) return;
        lastMsg = plain; lastMsgMs = now;

        Matcher cm = CATCH.matcher(plain);
        if (!cm.find()) return;
        String prefix = plain.substring(0, cm.start());
        if (prefix.indexOf(':') >= 0 || prefix.indexOf('>') >= 0) return; // someone relayed it

        String body = cm.group(1);
        Matcher tm = TIER_TOKEN.matcher(body);
        if (!tm.find()) return;
        int tier = switch (tm.group(1).toLowerCase()) {
            case "bronze" -> 0; case "silver" -> 1; case "gold" -> 2; default -> 3;
        };
        // The fish name is the body minus the tier word.
        String fishName = TIER_TOKEN.matcher(body).replaceAll("").trim().replaceAll("\\s{2,}", " ");
        if (fishName.isEmpty()) return;

        String matched = null;
        for (String name : data.keySet()) if (name.equalsIgnoreCase(fishName)) { matched = name; break; }
        if (matched == null) for (String name : KNOWN_FISH) if (name.equalsIgnoreCase(fishName)) { matched = name; break; }
        if (matched == null) return; // not a recognised trophy fish — avoid crediting random catches

        data.computeIfAbsent(matched, k -> new int[4])[tier]++;
        if (!needsSync) {
            needsSync = true;
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                if (mc.gui != null) mc.gui.getChat().addClientSystemMessage(Component.literal(
                        "§e[FishMod] §7Trophy fish counts are now estimated — open the §bTrophy Fishing §7menu to correct them."));
            });
        }
        save();
    }

    private static int toInt(String s) {
        try { return Integer.parseInt(s.replace(",", "")); } catch (NumberFormatException e) { return 0; }
    }

    private static List<String> lore(ItemStack stack) {
        List<String> out = new ArrayList<>();
        ItemLore lc = stack.get(DataComponents.LORE);
        if (lc == null) return out;
        for (Component line : lc.lines()) out.add(line.getString().replaceAll("§.", ""));
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

    private static boolean inCrimson() {
        return Location.in(Location.CRIMSON_ISLE);
    }

    // ── rendering ────────────────────────────────────────────────────────────
    public static boolean isVisible() {
        return FishSettings.trophyFishEnabled && !data.isEmpty() && inCrimson();
    }

    public static void renderHud(GuiGraphicsExtractor ctx, DeltaTracker tick) {
        if (!isVisible()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.screen != null && !(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) return;
        draw(ctx, mc);
    }

    public static void renderInScreen(GuiGraphicsExtractor ctx) {
        if (!isVisible()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?>)) return;
        draw(ctx, mc);
    }

    private static void draw(GuiGraphicsExtractor ctx, Minecraft mc) {
        int x = FishSettings.trophyFishHudX;
        int y = FishSettings.trophyFishHudY;
        float sc = (float) FishSettings.trophyFishHudScale;
        int lh = Constants.TEXT_HEIGHT + 1;
        var tr = mc.font;

        int nameW = 0;
        int[] amtW = new int[4];
        for (var e : data.entrySet()) {
            int[] v = e.getValue();
            int total = v[0] + v[1] + v[2] + v[3];
            nameW = Math.max(nameW, tr.width(e.getKey() + " (" + total + ")"));
            for (int i = 0; i < 4; i++) amtW[i] = Math.max(amtW[i], tr.width(v[i] == 0 ? "-" : String.valueOf(v[i])));
        }
        int gap = 4;
        int sepW = tr.width("| ");
        int[] cellX = new int[4];
        int cur = nameW + gap;
        for (int i = 0; i < 4; i++) { cellX[i] = cur; cur += amtW[i] + gap + sepW; }

        ctx.pose().pushMatrix();
        ctx.pose().translate((float) x, (float) y);
        ctx.pose().scale(sc, sc);
        ctx.text(tr, "Trophy Fish", 0, 0, 0xFF000000 | C_HEADER, true);
        int row = 1;
        for (var e : data.entrySet()) {
            int ty = lh * row;
            int[] t = e.getValue();
            int total = t[0] + t[1] + t[2] + t[3];
            ctx.text(tr, e.getKey() + " ", 0, ty, 0xFF000000 | C_NAME, true);
            drawTok(ctx, tr, "(" + total + ")", tr.width(e.getKey() + " "), ty, C_TOTAL);
            for (int i = 0; i < 4; i++) {
                int cx = cellX[i];
                if (i > 0) drawTok(ctx, tr, "| ", cx - sepW, ty, C_SEP);
                String num = t[i] == 0 ? "-" : String.valueOf(t[i]);
                drawTok(ctx, tr, num, cx + (amtW[i] - tr.width(num)), ty, t[i] == 0 ? C_MISSING : COLORS[i]);
            }
            row++;
        }
        ctx.pose().popMatrix();
    }

    private static void drawTok(GuiGraphicsExtractor ctx, net.minecraft.client.gui.Font tr,
                                String s, int cx, int ty, int color) {
        ctx.text(tr, s, cx, ty, 0xFF000000 | color, true);
    }
}
