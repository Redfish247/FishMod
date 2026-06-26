package fishmod.features.fishing;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fishmod.utils.Constants;
import fishmod.utils.config.values.FishSettings;
import fishmod.utils.events.Events;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sea Creature Tracker. Counts the sea creatures you reel up (per type + total + creatures/hr) and,
 * optionally, pops a title + ping when a rare one surfaces.
 *
 * <p>Detection is by the creature's name appearing in a non-player server line while you've been
 * fishing recently. The {@link #CREATURES} table is ordered most-specific-first so e.g. a
 * "Night Squid" line isn't credited to "Squid". If a creature isn't being counted, run
 * {@code /fmseadump} and read the exact spawn line, then add/adjust an entry here.
 */
public final class SeaCreatureTracker {

    /** A sea creature: the name substring to look for in its spawn line, and whether it's rare. */
    private record Creature(String name, boolean rare) {}

    // Ordered specific → general (longer names first) so substring matching credits the right one.
    // Verify/extend in-game with /fmseadump — Hypixel occasionally tweaks the flavor text.
    private static final Creature[] CREATURES = {
            // ── Water ──
            new Creature("Night Squid", false),
            new Creature("Sea Emperor", true),
            new Creature("Sea Guardian", false),
            new Creature("Sea Archer", false),
            new Creature("Sea Walker", false),
            new Creature("Sea Witch", false),
            new Creature("Sea Leech", false),
            new Creature("Rider of the Deep", false),
            new Creature("Deep Sea Protector", true),
            new Creature("Guardian Defender", false),
            new Creature("Water Hydra", true),
            new Creature("Carrot King", true),
            new Creature("Catfish", false),
            new Creature("Squid", false),
            // ── Lava (Crimson Isle) ──
            new Creature("Lord Jawbus", true),
            new Creature("Plhlegblast", true),
            new Creature("Fire Eel", true),
            new Creature("Taurus", true),
            new Creature("Magma Slug", false),
            new Creature("Moogma", false),
            new Creature("Lava Blaze", false),
            new Creature("Lava Pigman", false),
            new Creature("Lava Flame", false),
            new Creature("Flaming Worm", false),
            new Creature("Blaze", false),
            new Creature("Thunder", true),
            // ── Spooky / Winter / Festival ──
            new Creature("Reindrake", true),
            new Creature("Nutcracker", false),
            new Creature("Frozen Steve", false),
            new Creature("Grinch", true),
            new Creature("Yeti", true),
            new Creature("Frosty", false),
            new Creature("Grim Reaper", true),
            new Creature("Phantom Fisher", true),
            new Creature("Werewolf", false),
            new Creature("Nightmare", false),
            new Creature("Scarecrow", false),
    };

    private static final Path SAVE_FILE = Paths.get("config/fishmod/sea_creatures.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long WINDOW_MS = 3_600_000L;

    // name → count, insertion order (first-caught first).
    private static final Map<String, Integer> counts = new LinkedHashMap<>();
    private static final Deque<Long> catchTimes = new ArrayDeque<>();

    private static long lastFishingMs = 0; // last time the player's bobber was out
    private static String lastMsg = "";
    private static long lastMsgMs = 0;

    /** Toggle with /fmseadump — logs server lines while fishing so spawn text can be captured. */
    public static boolean debugDump = false;

    private static int btnX, btnY, btnW, btnH;
    private static boolean btnVisible = false;

    private static class SaveData { String[] names; int[] counts; }

    public static void init() {
        load();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!FishSettings.seaCreatureEnabled) return;
            if (client.player == null || client.world == null) return;
            for (Entity e : client.world.getEntities()) {
                if (e instanceof FishingBobberEntity b && b.getPlayerOwner() == client.player) {
                    lastFishingMs = System.currentTimeMillis();
                    break;
                }
            }
        });

        Events.ON_GAME_MESSAGE.register(text -> {
            if (!FishSettings.seaCreatureEnabled || text == null) return false;
            onChat(text.getString());
            return false;
        });
    }

    private static void onChat(String raw) {
        String plain = raw.replaceAll("§.", "").trim();
        long now = System.currentTimeMillis();
        if (plain.equals(lastMsg) && now - lastMsgMs < 80) return; // bundled double-dispatch
        lastMsg = plain; lastMsgMs = now;

        // Only consider real server lines while actively fishing (no player-chat prefix).
        if (now - lastFishingMs > 15_000L) { dump(plain); return; }
        if (plain.indexOf(':') >= 0 || plain.indexOf('>') >= 0) { dump(plain); return; }

        for (Creature c : CREATURES) {
            if (plain.contains(c.name())) {
                record(c, now);
                return;
            }
        }
        dump(plain);
    }

    private static void dump(String plain) {
        if (!debugDump || plain.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> {
            if (mc.inGameHud != null)
                mc.inGameHud.getChatHud().addMessage(Text.literal("§8[seadump] §7" + plain));
        });
    }

    private static void record(Creature c, long now) {
        counts.merge(c.name(), 1, Integer::sum);
        catchTimes.addLast(now);
        long cutoff = now - WINDOW_MS;
        while (!catchTimes.isEmpty() && catchTimes.peekFirst() < cutoff) catchTimes.pollFirst();
        save();

        if (c.rare() && FishSettings.seaCreatureRareAlert) {
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.execute(() -> {
                InGameHud hud = mc.inGameHud;
                if (hud == null) return;
                hud.setTitleTicks(0, 30, 10);
                hud.setTitle(Text.literal("§6§l✦ RARE CATCH ✦"));
                hud.setSubtitle(Text.literal("§e" + c.name()));
                if (mc.player != null)
                    mc.player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            });
        }
    }

    public static void reset() {
        counts.clear();
        catchTimes.clear();
        save();
    }

    private static double creaturesPerHour() {
        long now = System.currentTimeMillis();
        long cutoff = now - WINDOW_MS;
        while (!catchTimes.isEmpty() && catchTimes.peekFirst() < cutoff) catchTimes.pollFirst();
        if (catchTimes.size() < 2) return 0;
        long windowMs = now - catchTimes.peekFirst();
        if (windowMs < 1000) return 0;
        return catchTimes.size() * 3_600_000.0 / windowMs;
    }

    private static int total() {
        int t = 0;
        for (int v : counts.values()) t += v;
        return t;
    }

    // ── persistence ──────────────────────────────────────────────────────────
    private static synchronized void save() {
        try {
            Files.createDirectories(SAVE_FILE.getParent());
            SaveData d = new SaveData();
            d.names = counts.keySet().toArray(new String[0]);
            d.counts = counts.values().stream().mapToInt(Integer::intValue).toArray();
            Files.writeString(SAVE_FILE, GSON.toJson(d));
        } catch (Exception ignored) {}
    }

    private static synchronized void load() {
        try {
            if (!Files.exists(SAVE_FILE)) return;
            SaveData d = GSON.fromJson(Files.readString(SAVE_FILE), SaveData.class);
            if (d == null || d.names == null || d.counts == null) return;
            int n = Math.min(d.names.length, d.counts.length);
            for (int i = 0; i < n; i++) counts.put(d.names[i], d.counts[i]);
        } catch (Exception ignored) {}
    }

    // ── rendering ────────────────────────────────────────────────────────────
    public static boolean isVisible() {
        return FishSettings.seaCreatureEnabled && !counts.isEmpty();
    }

    private static String[] buildLines() {
        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add("§b§lSea Creatures §7(" + total() + ")");
        for (var e : counts.entrySet())
            lines.add("§7" + e.getKey() + ": §f" + e.getValue());
        double rate = creaturesPerHour();
        lines.add("§7Per hr: §a" + (rate == 0 ? "§8—" : String.format("%.0f", rate)));
        return lines.toArray(new String[0]);
    }

    public static void renderHud(DrawContext ctx, RenderTickCounter tick) {
        btnVisible = false;
        if (!isVisible()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen)) return;
        draw(ctx, mc, buildLines());
    }

    public static void renderInScreen(DrawContext ctx, int mouseX, int mouseY) {
        btnVisible = false;
        if (!isVisible()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!(mc.currentScreen instanceof HandledScreen<?>)) return;

        String[] lines = buildLines();
        int x = FishSettings.seaCreatureHudX, y = FishSettings.seaCreatureHudY;
        float sc = (float) FishSettings.seaCreatureScale;
        int lh = Constants.TEXT_HEIGHT + 1;
        draw(ctx, mc, lines);

        String label = "§l[ Reset ]";
        int padX = 4, padY = 3;
        int localY = lh * lines.length - 2;
        boolean hover;
        btnX = x;
        btnY = y + (int) (localY * sc);
        btnW = (int) ((mc.textRenderer.getWidth(label) + padX * 2) * sc);
        btnH = (int) ((Constants.TEXT_HEIGHT + padY * 2 + 1) * sc);
        hover = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
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
        int x = FishSettings.seaCreatureHudX, y = FishSettings.seaCreatureHudY;
        float sc = (float) FishSettings.seaCreatureScale;
        int lh = Constants.TEXT_HEIGHT + 1;
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate((float) x, (float) y);
        ctx.getMatrices().scale(sc, sc);
        for (int i = 0; i < lines.length; i++)
            ctx.drawText(mc.textRenderer, lines[i], 0, lh * i, 0xFFFFFFFF, true);
        ctx.getMatrices().popMatrix();
    }
}
