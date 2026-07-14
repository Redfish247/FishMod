package fishmod.features;

import com.google.gson.JsonObject;
import fishmod.utils.HypixelApi;
import fishmod.utils.Location;
import fishmod.utils.config.values.FishSettings;
import fishmod.utils.data.ItemUtil;
import fishmod.utils.rendering.DrawEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Hypixel's own Catacombs/class level-up menu items just show "MAX LEVEL" once you pass
 * level 50, with no indication of overflow progress. This draws the real (SkyHanni-style)
 * overflow level on top of those items, computed from the player's own dungeons API data.
 */
public class CatacombsOverflowOverlay {

    private static final Pattern COLOR_STRIP = Pattern.compile("§.");

    private static final Map<String, String> CLASS_KEYS = Map.of(
            "healer", "healer",
            "mage", "mage",
            "berserk", "berserk",
            "berserker", "berserk",
            "archer", "archer",
            "tank", "tank"
    );

    private static long selfCataXp = -1;
    private static final Map<String, Long> selfClassXp = new HashMap<>();
    private static long lastFetchAt = 0;
    private static boolean fetchInFlight = false;
    private static final long REFRESH_MS = 60_000L;

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!FishSettings.catacombsOverflowEnabled || client.player == null || !Location.inSkyblock()) return;
            long now = System.currentTimeMillis();
            if (fetchInFlight || now - lastFetchAt < REFRESH_MS) return;
            lastFetchAt = now;
            fetchInFlight = true;
            HypixelApi.getLocalMember(client, CatacombsOverflowOverlay::applySelfMember);
        });

        DrawEvents.INVENTORY_SLOT_AFTER.register((ctx, stack, x, y) -> {
            if (!FishSettings.catacombsOverflowEnabled) return;
            draw(ctx, stack, x, y);
        });
    }

    private static void applySelfMember(JsonObject member) {
        fetchInFlight = false;
        if (member == null || !member.has("dungeons")) return;
        try {
            JsonObject dungeons = member.getAsJsonObject("dungeons");
            if (dungeons.has("dungeon_types")) {
                JsonObject types = dungeons.getAsJsonObject("dungeon_types");
                if (types.has("catacombs")) {
                    JsonObject cata = types.getAsJsonObject("catacombs");
                    if (cata.has("experience")) selfCataXp = cata.get("experience").getAsLong();
                }
            }
            if (dungeons.has("player_classes")) {
                JsonObject classes = dungeons.getAsJsonObject("player_classes");
                for (String cls : new String[]{"healer", "mage", "berserk", "archer", "tank"}) {
                    if (classes.has(cls)) {
                        JsonObject c = classes.getAsJsonObject(cls);
                        if (c.has("experience")) selfClassXp.put(cls, c.get("experience").getAsLong());
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private static void draw(DrawContext ctx, ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty()) return;
        String name = COLOR_STRIP.matcher(stack.getName().getString()).replaceAll("").trim();

        long xp;
        if (name.equalsIgnoreCase("Catacombs")) {
            xp = selfCataXp;
        } else {
            String key = CLASS_KEYS.get(name.toLowerCase());
            if (key == null) return;
            Long boxed = selfClassXp.get(key);
            xp = boxed != null ? boxed : -1;
        }
        // Only decorate once actually past the level-50 cap — below that Hypixel's own progress display is fine.
        if (xp <= HypixelApi.XP_FOR_50) return;
        if (!ItemUtil.containsLore(stack, "MAX LEVEL") && !ItemUtil.containsLore(stack, "Progress to Level")) return;

        String levelStr = HypixelApi.formatLevel(xp);
        MinecraftClient mc = MinecraftClient.getInstance();
        String text = "§6" + levelStr + "§e✦";
        float scale = 0.6f;
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(x - 1f, y + 9f);
        ctx.getMatrices().scale(scale, scale);
        ctx.drawText(mc.textRenderer, text, 0, 0, 0xFFFFFFFF, true);
        ctx.getMatrices().popMatrix();
    }
}
