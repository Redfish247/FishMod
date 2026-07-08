package fishmod.features;

import fishmod.utils.config.values.FishSettings;
import fishmod.utils.data.ItemUtil;
import fishmod.utils.events.Events;
import fishmod.utils.rendering.DrawEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import fishmod.utils.data.ScoreboardUtil;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Per-item ability cooldown overlay. Detects cooldown start via the Hypixel
 * "ability cooldown" sound (Enderman teleport at pitch 0 / volume 8) and renders
 * a countdown bar + number on the held item's slot until the cooldown expires.
 *
 * Hotbar and inventory-GUI slots both render the overlay.
 */
public class CooldownOverlay {

    /**
     * Known item-id → cooldown duration (ms). Values pulled from the Hypixel Skyblock wiki:
     * https://hypixelskyblock.minecraft.wiki/
     */
    private static final Map<String, Long> COOLDOWNS = new HashMap<>();
    static {
        COOLDOWNS.put("HYPERION",      5_000L);
        COOLDOWNS.put("SCYLLA",        5_000L);
        COOLDOWNS.put("VALKYRIE",      5_000L);
        COOLDOWNS.put("ASTRAEA",       5_000L);
        COOLDOWNS.put("SHADOW_FURY", 15_000L);
        COOLDOWNS.put("INFINITE_SUPERBOOM_TNT", 20_000L);
        COOLDOWNS.put("GIANTS_SWORD",      30_000L); // Giant's Slam
        COOLDOWNS.put("ATOMSPLIT_KATANA",   4_000L); // Soulcry
        COOLDOWNS.put("ICE_SPRAY_WAND",     5_000L);
        COOLDOWNS.put("FIRE_FREEZE_STAFF", 10_000L);
        COOLDOWNS.put("GYROKINETIC_WAND",  30_000L); // Gravity Storm
        COOLDOWNS.put("RAGNAROCK_AXE",     20_000L);
        COOLDOWNS.put("TACTICAL_INSERTION", 20_000L);
        COOLDOWNS.put("ROGUE_SWORD",        5_000L);
        COOLDOWNS.put("WITHER_CLOAK",      10_000L);
        COOLDOWNS.put("DEIFIC_SPADE",       1_000L);
        COOLDOWNS.put("WIERDER_TUBA",      20_000L);
        COOLDOWNS.put("WIERD_TUBA",        20_000L);
        COOLDOWNS.put("FIRE_FREEZE_STAFF", 10_000L);
    }

    /** itemId → wall-clock millisecond at which the cooldown ends. */
    private static final Map<String, Long> active = new HashMap<>();

    public  static boolean debugDumpSound = false;

    // Hypixel mana-cost chat line, e.g. "-300 Mana (Wither Impact)".
    private static final Pattern MANA_LINE = Pattern.compile("-\\s*[\\d,]+\\s*Mana\\s*\\(([^)]+)\\)");
    private static final Pattern COLOR_STRIP = Pattern.compile("§.");
    // Hypixel "[Mage] Cooldown Reduction 49% -> 74%" — exact live CDR from the game.
    private static final Pattern MAGE_CDR_LINE = Pattern.compile("\\[Mage\\] Cooldown Reduction \\d+% -> (\\d+)%");
    private static volatile int liveMageCdrPercent = -1;

    // Action-bar mana, e.g. "590/770✎". Used to confirm an ability actually fired (mana spent).
    private static final Pattern MANA_BAR = Pattern.compile("([\\d,]+)/[\\d,]+✎");
    private static volatile int  lastMana       = -1;
    private static volatile String pendingId    = null;  // right-clicked ability awaiting mana-drop confirmation
    private static volatile long   pendingAt    = 0;
    private static volatile int    pendingManaBefore = -1;

    public static void init() {
        // Primary trigger: cooldown sound (Enderman teleport, pitch=0, volume=8).
        Events.ON_SOUND.register((SoundEvent event, float volume, float pitch) -> {
            if (!FishSettings.cooldownOverlayEnabled) return false;
            if (pitch == 0.0f && volume == 8.0f && event == SoundEvents.ENDERMAN_TELEPORT) {
                if (debugDumpSound) {
                    fishmod.utils.Misc.addChatMessage(net.minecraft.network.chat.Component.literal("§d[fmcd] cooldown sound detected"));
                }
                onAbilityFired();
            }
            return false;
        });

        // Fallback trigger: mana-cost chat line. Some Hypixel abilities suppress the cooldown sound.
        Events.ON_GAME_MESSAGE.register(text -> {
            if (!FishSettings.cooldownOverlayEnabled) return false;
            String s = COLOR_STRIP.matcher(text.getString()).replaceAll("");
            if (debugDumpSound && s.toLowerCase().contains("mana")) {
                fishmod.utils.Misc.addChatMessage(net.minecraft.network.chat.Component.literal("§d[fmcd] chat: §7" + s));
            }
            java.util.regex.Matcher cdrM = MAGE_CDR_LINE.matcher(s);
            if (cdrM.find()) {
                try { liveMageCdrPercent = Integer.parseInt(cdrM.group(1)); } catch (NumberFormatException ignored) {}
                return false;
            }
            java.util.regex.Matcher m = MANA_LINE.matcher(s);
            if (m.find()) {
                if (debugDumpSound) {
                    fishmod.utils.Misc.addChatMessage(net.minecraft.network.chat.Component.literal("§d[fmcd] mana line matched: " + m.group(1)));
                }
                onAbilityFired();
            }
            return false;
        });

        // Action-bar mana tracker. A right-click only *arms* a pending ability; the cooldown is
        // registered only once the action-bar mana actually DROPS shortly after. If no mana was
        // spent (off cooldown but you didn't have/use mana, missed cast, etc.) it never registers.
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
            if (!overlay || !FishSettings.cooldownOverlayEnabled) return;
            String s = COLOR_STRIP.matcher(msg.getString()).replaceAll("");
            java.util.regex.Matcher m = MANA_BAR.matcher(s);
            if (!m.find()) return;
            int mana;
            try { mana = Integer.parseInt(m.group(1).replace(",", "")); } catch (NumberFormatException e) { return; }
            if (pendingId != null && System.currentTimeMillis() - pendingAt < 2000
                    && pendingManaBefore >= 0 && mana < pendingManaBefore) {
                if (debugDumpSound) fishmod.utils.Misc.addChatMessage(net.minecraft.network.chat.Component.literal(
                        "§d[fmcd] mana " + pendingManaBefore + "→" + mana + " confirms " + pendingId));
                pendingId = null;
                onAbilityFired();
            }
            lastMana = mana;
        });

        // Right-click trigger — ARMS the pending confirmation only. Cooldown is registered by the
        // mana tracker above when mana actually drops, so a no-mana right-click won't start it.
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!FishSettings.cooldownOverlayEnabled) return InteractionResult.PASS;
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            ItemStack stack = player.getItemInHand(hand);
            if (stack != null && !stack.isEmpty()) {
                String id = ItemUtil.getId(stack);
                if (id != null && COOLDOWNS.containsKey(id)) {
                    pendingId = id;
                    pendingAt = System.currentTimeMillis();
                    pendingManaBefore = lastMana;
                    if (debugDumpSound) {
                        fishmod.utils.Misc.addChatMessage(net.minecraft.network.chat.Component.literal("§d[fmcd] armed " + id + " (mana=" + lastMana + ")"));
                    }
                }
            }
            return InteractionResult.PASS;
        });

        // Inventory-GUI slot overlay (chest GUIs, player inventory open, etc.)
        DrawEvents.INVENTORY_SLOT_AFTER.register((ctx, stack, x, y) -> {
            if (!FishSettings.cooldownOverlayEnabled) return;
            drawOverlay(ctx, stack, x, y);
        });
    }

    private static void onAbilityFired() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.getConnection() == null) return;

        ItemStack held = p.getMainHandItem();
        if (held == null || held.isEmpty()) return;

        String id = ItemUtil.getId(held);
        if (id == null) return;

        Long baseCdRaw = COOLDOWNS.get(id);
        if (baseCdRaw == null) return;

        // --- DECLARE VARIABLES ---
        int mageLvl = 0;
        boolean isMage = false;
        double baseCd = baseCdRaw.doubleValue();
        double finalCdResult;
        boolean inDungeon = fishmod.utils.Location.inDungeon();

        // --- TAB LIST SCANNING (Mage detection) ---
        for (net.minecraft.client.multiplayer.PlayerInfo entry : mc.getConnection().getOnlinePlayers()) {
            if (entry.getTabListDisplayName() != null) {
                String line = entry.getTabListDisplayName().getString();

                if (line.contains(p.getName().getString()) && line.contains("Mage")) {
                    isMage = true;
                    try {
                        String roman = line.split("Mage ")[1].split("\\)")[0].trim();
                        mageLvl = decodeRoman(roman);
                    } catch (Exception e) {
                        mageLvl = ScoreboardUtil.getCurrentClassLevel();
                    }
                }
            }
        }

        // --- HYPERION OVERRIDE ---
        // Hyperion ability cooldown is hardcoded to 5s by Hypixel regardless of CDR.
        if (id.equals("HYPERION")) {
            finalCdResult = baseCd;
        }
        // --- MAGE REDUCTION CALCULATION ---
        else if (isMage && inDungeon) {
            double classReduction;
            if (liveMageCdrPercent > 0) {
                // Game told us exact CDR via "[Mage] Cooldown Reduction X% -> Y%" chat — use it.
                classReduction = liveMageCdrPercent / 100.0;
            } else {
                // Fallback: level-based estimate (25% -> 70% across Mage 1..50).
                int level = (mageLvl > 0) ? Math.min(mageLvl, 50) : 50;
                classReduction = 0.25 + (level - 1) * (0.45 / 49.0);
            }
            double multiplier = 1.0 - Math.min(classReduction, 0.75);
            finalCdResult = baseCd * multiplier;
        } else {
            finalCdResult = baseCd;
        }

        // --- RAGNAROCK AXE OUTSIDE BUFFER ---
        // If not in a dungeon and item is Ragnarock Axe, add 3000ms (3s)
        if (!inDungeon && id.equals("RAGNAROCK_AXE")) {
            finalCdResult += 3000.0;
        }
        // Non-Mage in dungeons: Ragnarock Axe needs a 3s buffer.
        if (inDungeon && !isMage && id.equals("RAGNAROCK_AXE")) {
            finalCdResult += 3000;
        }

        if (debugDumpSound) {
            fishmod.utils.Misc.addChatMessage(net.minecraft.network.chat.Component.literal(
                    "§b[fmcd] Final CD: " + String.format("%.1fs", finalCdResult / 1000.0) + (inDungeon ? " (Dungeon)" : " (Outside)")
            ));
        }

        long finalCd = (long) finalCdResult;
        long now = System.currentTimeMillis();
        Long existing = active.get(id);

        if (existing != null && existing > now) return;

        active.put(id, now + finalCd);
    }

    // Helper to extract level if ScoreboardUtil isn't doing it
    private static int parseLevelFromTab(String line) {
        if (line.contains("XLIX")) return 49;
        if (line.contains("L")) return 50;
        // You could add a full Roman Numeral parser here if needed,
        // but checking the top levels is usually enough for testing.
        return 0;
    }

    // (Ensure there is only ONE getLevelFromXp method below this)

    /**
     * Standard Skyblock Dungeon Level XP Requirements.
     * You can expand this array to include all 50 levels.
     */
    private static int getLevelFromXp(long xp) {
        long[] levelXp = {
                0, 50, 125, 235, 395, 625, 955, 1425, 2095, 3045,
                4385, 6275, 8940, 12700, 17960, 25340, 35640, 50040, 70040, 97640,
                135640, 187640, 258640, 356640, 488640, 668640, 911640, 1239640, 1677640, 2262640,
                3037640, 4057640, 5407640, 7157640, 9457640, 12457640, 16357640, 21357640, 27857640, 36357640,
                47357640, 61357640, 79357640, 102357640, 131357640, 168357640, 215357640, 275357640, 351357640, 448357640, 569857640
        };

        for (int i = levelXp.length - 1; i >= 0; i--) {
            if (xp >= levelXp[i]) return i;
        }
        return 0;
    }

    /** Hotbar render hook (called from FishModInit HudRenderCallback). */
    public static void renderHotbar(GuiGraphicsExtractor ctx, DeltaTracker tickCounter) {
        if (!FishSettings.cooldownOverlayEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        if (mc.options.hideGui) return;
        if (mc.getDebugOverlay() != null && mc.getDebugOverlay().showDebugScreen()) return;

        // Sweep expired entries before drawing.
        pruneExpired();
        if (active.isEmpty()) return;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int hbX = (sw - 182) / 2;
        int hbY = sh - 22;

        Inventory inv = p.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.isEmpty()) continue;
            int x = hbX + 3 + i * 20;
            int y = hbY + 3;
            drawOverlay(ctx, stack, x, y);
        }
    }

    /** Debug dump for /fmpet command. */
    public static String debugState() {
        StringBuilder sb = new StringBuilder();
        sb.append("cooldownOverlayEnabled=").append(FishSettings.cooldownOverlayEnabled).append(" | active=");
        if (active.isEmpty()) sb.append("(none)");
        else {
            long now = System.currentTimeMillis();
            for (Map.Entry<String, Long> e : active.entrySet())
                sb.append(e.getKey()).append("(").append(Math.max(0, e.getValue() - now)).append("ms) ");
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            ItemStack held = mc.player.getMainHandItem();
            sb.append(" | heldId=").append(held == null || held.isEmpty() ? "none" : ItemUtil.getId(held));
        }
        return sb.toString();
    }

    private static void pruneExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> it = active.entrySet().iterator();
        while (it.hasNext()) if (it.next().getValue() <= now) it.remove();
    }

    private static void drawOverlay(GuiGraphicsExtractor ctx, ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty()) return;
        String id = ItemUtil.getId(stack);
        if (id == null) return;
        Long endAt = active.get(id);
        if (endAt == null) return;
        long now = System.currentTimeMillis();
        long remaining = endAt - now;
        if (remaining <= 0) return;
        Long total = COOLDOWNS.get(id);
        if (total == null || total <= 0) return;

        // "Only show when < 3s left" option — gate text + bar visibility.
        boolean inFocusWindow = remaining < 3_000L;
        if (FishSettings.cooldownOnlyUnder3s && !inFocusWindow) return;

        double secs = remaining / 1000.0;
        String text = secs >= 10 ? String.valueOf((int) Math.ceil(secs))
                                 : String.format("%.1f", secs);


        if (FishSettings.cooldownShowText) {
            Minecraft mc = Minecraft.getInstance();
            int tx = x + 16 - mc.font.width(text);
            int ty = y + 8 - mc.font.lineHeight / 2 + 1;
            // Draw text on top of items (use 200 z-offset to clear item shading).
            ctx.pose().pushMatrix();
            ctx.pose().translate(0f, 0f);
            ctx.text(mc.font, text, tx, ty, 0xFFFFFFFF, true);
            ctx.pose().popMatrix();
        }
    }
    private static int decodeRoman(String roman) {
        if (roman == null) return 0;
        String r = roman.toUpperCase();
        if (r.equalsIgnoreCase("L")) return 50;
        if (r.equalsIgnoreCase("XLIX")) return 49;
        if (r.equalsIgnoreCase("XLVIII")) return 48;
        if (r.equalsIgnoreCase("XLVII")) return 47;
        if (r.equalsIgnoreCase("XLVI")) return 46;
        if (r.equalsIgnoreCase("XLV")) return 45;

        // Fallback for numeric strings if it's already a number
        try {
            return Integer.parseInt(r.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }
}
