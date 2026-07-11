package fishmod.features;

import fishmod.utils.HypixelApi;
import fishmod.utils.Location;
import fishmod.utils.config.values.FishSettings;
import fishmod.utils.data.ItemUtil;
import fishmod.utils.events.Events;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PetHud {

    private static final Pattern COLOR_STRIP = Pattern.compile("§.");

    // Tab patterns for 2-line layout
    private static final Pattern TAB_NAME_LINE = Pattern.compile("\\[Lvl\\s*(\\d+)\\]\\s+(.+)");
    private static final Pattern TAB_XP_LINE = Pattern.compile("([\\d.,]+[KMB]?)\\s*/\\s*([\\d.,]+[KMB]?)\\s*XP");

    // Chat detection — the authoritative source for the active pet.
    // "Autopet equipped your [Lvl 100] Griffin! VIEW RULE"
    private static final Pattern AUTOPET_PAT = Pattern.compile("Autopet equipped your \\[Lvl\\s*(\\d+)\\]\\s*(.+?)!");
    // "You summoned your Golden Dragon ✦!"
    private static final Pattern SUMMON_PAT  = Pattern.compile("You summoned your\\s+(.+?)!");
    // "You despawned your Golden Dragon ✦!" / "Autopet despawned your ..."
    private static final Pattern DESPAWN_PAT = Pattern.compile("(?:You|Autopet) despawned your\\s+(.+?)!");

    private static final Pattern PET_ITEM_NAME = Pattern.compile("\\[Lvl\\s*(\\d+)\\]\\s*(.+)");
    private static final Pattern PROGRESS_PAT = Pattern.compile("Progress to Level \\d+:\\s*([\\d.]+)%");
    private static final Pattern PROGRESS_XP_PAT = Pattern.compile("([\\d.,]+)\\s*/\\s*([\\d.,]+[KMB]?)");

    public static boolean debugDumpPetLines = false;

    private static final Pattern TAB_OVERFLOW_XP = Pattern.compile("\\+([\\d.,]+[KMB]?)\\s*XP");

    private static String petName = null;
    private static int petLevel = -1;
    private static int petOverflowLevel = -1;
    private static boolean petMaxed = false;
    private static int forceScanTicks = 0; // after equip/summon, scan tab every tick briefly
    private static double pendingXp = 0;
    private static long lastXpAt = 0;
    private static int tickCount = 0;

    private static double xpCurrent = -1;
    private static double xpNext    = -1;
    private static float  xpPct     = -1;

    // Prevents /pets menu from overwriting Tab list data
    private static long lastTabUpdate = 0;

    // API is the authoritative source for the active pet's level/XP (tab format broke and dungeons
    // have no pet tab entry). Refresh periodically and immediately after a pet change.
    private static final long API_REFRESH_MS = 60_000L;
    private static long lastApiFetchAt = 0;
    private static boolean apiFetchInFlight = false;

    // Hypixel wiki: a pet earns 1 Pet XP per 1 skill XP gained in its matching skill.
    // Non-matching skills give 0. Source: https://wiki.hypixel.net/Pets#Pet_XP
    private static final Pattern SKILL_XP_BAR = Pattern.compile(
            "\\+\\s*([\\d,.]+)\\s+(Farming|Mining|Combat|Foraging|Fishing|Enchanting|Alchemy|Carpentry|Runecrafting|Taming)\\b");
    private static final java.util.Map<String, String> PET_SKILL = java.util.Map.ofEntries(
        java.util.Map.entry("Elephant",        "Farming"),
        java.util.Map.entry("Rabbit",          "Farming"),
        java.util.Map.entry("Bee",             "Farming"),
        java.util.Map.entry("Mooshroom Cow",   "Farming"),
        java.util.Map.entry("Slug",            "Farming"),
        java.util.Map.entry("Hedgehog",        "Farming"),
        java.util.Map.entry("Chicken",         "Farming"),
        java.util.Map.entry("Squid",           "Fishing"),
        java.util.Map.entry("Megalodon",       "Fishing"),
        java.util.Map.entry("Blue Whale",      "Fishing"),
        java.util.Map.entry("Dolphin",         "Fishing"),
        java.util.Map.entry("Flying Fish",     "Fishing"),
        java.util.Map.entry("Endermite",       "Mining"),
        java.util.Map.entry("Silverfish",      "Mining"),
        java.util.Map.entry("Rock",            "Mining"),
        java.util.Map.entry("Mole",            "Mining"),
        java.util.Map.entry("Bal",             "Mining"),
        java.util.Map.entry("Tiger",           "Combat"),
        java.util.Map.entry("Wolf",            "Combat"),
        java.util.Map.entry("Skeleton",        "Combat"),
        java.util.Map.entry("Spider",          "Combat"),
        java.util.Map.entry("Enderman",        "Combat"),
        java.util.Map.entry("Black Cat",       "Combat"),
        java.util.Map.entry("Lion",            "Foraging"),
        java.util.Map.entry("Monkey",          "Foraging"),
        java.util.Map.entry("Giraffe",         "Foraging"),
        java.util.Map.entry("Ocelot",          "Foraging"),
        java.util.Map.entry("Treasure Hunter", "Foraging")
    );

    public static void init() {
        FishHudEditor.register("Pet",
                () -> FishSettings.petHudX, v -> FishSettings.petHudX = v,
                () -> FishSettings.petHudY, v -> FishSettings.petHudY = v,
                120, 10,
                () -> FishSettings.petHudScale, v -> FishSettings.petHudScale = v);

        // Chat listener: the active pet is announced on summon / autopet-rule equip.
        // This is the authoritative source (tab/menu scraping is a fallback).
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
            if (overlay || !FishSettings.petHudEnabled) return;
            String s = COLOR_STRIP.matcher(msg.getString()).replaceAll("").trim();

            Matcher a = AUTOPET_PAT.matcher(s);
            if (a.find()) {
                petLevel = safeInt(a.group(1), -1);
                petName  = cleanPetName(a.group(2));
                petMaxed = false; // tab burst-scan re-confirms
                xpCurrent = -1; xpNext = -1; pendingXp = 0; // reset XP for the newly-equipped pet
                lastTabUpdate = System.currentTimeMillis();
                lastApiFetchAt = 0; // force an immediate API refetch for the new pet
                forceScanTicks = 10; // immediately pull level/xp/overflow from tab
                if (debugDumpPetLines) fishmod.utils.Misc.addChatMessage(net.minecraft.network.chat.Component.literal("§d[pet] autopet → [" + petLevel + "] " + petName));
                return;
            }
            Matcher su = SUMMON_PAT.matcher(s);
            if (su.find()) {
                String n = cleanPetName(su.group(1));
                // Ignore non-pet "summoned your" lines (e.g. mounts) by keeping it simple — set name.
                petName = n;
                petMaxed = false; // tab burst-scan re-confirms
                xpCurrent = -1; xpNext = -1; pendingXp = 0;
                lastTabUpdate = System.currentTimeMillis();
                lastApiFetchAt = 0; // force an immediate API refetch for the new pet
                forceScanTicks = 10; // immediately pull level/xp/overflow from tab
                if (debugDumpPetLines) fishmod.utils.Misc.addChatMessage(net.minecraft.network.chat.Component.literal("§d[pet] summon → " + petName));
            }
        });

        // Action-bar listener: Hypixel emits "+X.X <Skill> (current/next)" each gain.
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
            if (!FishSettings.petHudEnabled || !overlay || petName == null) return;
            String s = COLOR_STRIP.matcher(msg.getString()).replaceAll("");
            String matchSkill = PET_SKILL.get(petName);
            if (matchSkill == null) return;
            Matcher m = SKILL_XP_BAR.matcher(s);
            while (m.find()) {
                if (!m.group(2).equalsIgnoreCase(matchSkill)) continue;
                try {
                    double rawSkillXp = Double.parseDouble(m.group(1).replace(",", ""));
                    // Apply Hypixel pet-XP multipliers (wiki).
                    double mult = 1.0
                            * (1 + FishSettings.petXpTamingLevel * 0.01)
                            * (1 + FishSettings.petXpBeastmasterBonus / 100.0)
                            * (1 + FishSettings.petXpPetItemBonus / 100.0)
                            * (FishSettings.petXpBoosterCookie ? 1.20 : 1.0);
                    double gain = rawSkillXp * mult;
                    pendingXp += gain;
                    lastXpAt = System.currentTimeMillis();
                    if (xpCurrent >= 0 && xpNext > 0) xpCurrent = Math.min(xpCurrent + gain, xpNext);
                } catch (NumberFormatException ignored) {}
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!FishSettings.petHudEnabled || client.getConnection() == null) return;
            if (!Location.inSkyblock()) {
                reset();
                return;
            }

            // Authoritative pet level/XP + multipliers from the API.
            long nowMs = System.currentTimeMillis();
            if (!apiFetchInFlight && nowMs - lastApiFetchAt >= API_REFRESH_MS) {
                lastApiFetchAt = nowMs;
                apiFetchInFlight = true;
                HypixelApi.getActivePet(client, PetHud::applyApiPet);
            }

            scanPetsMenuIfOpen(client.screen);

            // After an equip/summon, scan every tick for a short burst (tab can lag the chat msg).
            if (forceScanTicks > 0) {
                forceScanTicks--;
                scanTabList(client.getConnection());
            }

            tickCount++;
            if (tickCount >= 5) {
                tickCount = 0;
                scanTabList(client.getConnection());
            }
        });
    }

    private static void scanTabList(ClientPacketListener handler) {
        // The equipped pet shows in the tab list as "[Lvl N] Name" (under the "Pet:" header).
        // The "[Lvl " prefix is unique to the pet line — player names use "[519]"/"[MVP+]" —
        // so we just match it directly. (The old code required a separate "x/y XP" line that
        // the tab never has, so it never committed; the pet maxed shows "MAX LEVEL" instead.)
        String tempName = null;
        int    tempLevel = -1;
        boolean maxed = false;
        double overflowXp = -1;

        for (PlayerInfo entry : handler.getOnlinePlayers()) {
            if (entry.getTabListDisplayName() == null) continue;
            String text = COLOR_STRIP.matcher(entry.getTabListDisplayName().getString()).replaceAll("").trim();
            Matcher nameMatch = TAB_NAME_LINE.matcher(text);
            if (nameMatch.find()) {
                tempLevel = safeInt(nameMatch.group(1), -1);
                tempName  = nameMatch.group(2).replace("✦", "").trim();
            } else if (text.equalsIgnoreCase("MAX LEVEL")) {
                maxed = true;
            } else {
                Matcher ov = TAB_OVERFLOW_XP.matcher(text); // "+1,234 XP" overflow line under Pet:
                if (ov.find()) overflowXp = parseAbbrev(ov.group(1));
            }
        }

        if (tempName != null) {
            petName = tempName;
            petLevel = tempLevel;
            petMaxed = maxed;
            if (maxed) {
                xpNext = -1; // forces the HUD's MAXED display
                // Overflow level: total XP = overflow shown + XP to reach the max level.
                if (overflowXp >= 0 && tempLevel > 0) {
                    OverflowPetLevels.Rarity rar = OverflowPetLevels.Rarity.LEGENDARY;
                    double total = overflowXp + OverflowPetLevels.getCalculativeXpForLevel(tempLevel, rar);
                    petOverflowLevel = OverflowPetLevels.calcLevel(total, rar);
                } else {
                    petOverflowLevel = tempLevel;
                }
            } else {
                petOverflowLevel = -1;
            }
            lastTabUpdate = System.currentTimeMillis(); // authority mark
        }
    }

    /** Applies the API-fetched active pet as the authoritative baseline (runs on the client thread). */
    private static void applyApiPet(HypixelApi.PetInfo info) {
        apiFetchInFlight = false;
        if (info == null || !info.ok) return;
        petName = info.name;
        petLevel = info.level;
        petMaxed = info.maxed;
        petOverflowLevel = info.maxed ? info.overflowLevel : -1;
        if (info.maxed) {
            xpNext = -1; // HUD shows MAXED
        } else {
            xpCurrent = info.xpIntoLevel;
            xpNext = info.xpForNext;
            xpPct = info.pct;
        }
        pendingXp = 0; // baseline re-synced; clear accumulated live estimate
        lastTabUpdate = System.currentTimeMillis(); // treat API as authority over the menu scraper
    }

    /** Overflow level for a maxed pet (e.g. 142 for a Lvl 100 pet past max), or -1 if not maxed/unknown. */
    public static int getOverflowLevel() { return petOverflowLevel; }

    private static void scanPetsMenuIfOpen(Screen current) {
        // Stop flopping: If Tab updated in last 2 seconds, don't use menu data
        if (System.currentTimeMillis() - lastTabUpdate < 2000) return;

        if (!(current instanceof ContainerScreen gc)) return;
        String title = COLOR_STRIP.matcher(gc.getTitle().getString()).replaceAll("").trim();
        if (!title.startsWith("Pets")) return;

        AbstractContainerMenu handler = gc.getMenu();
        for (Slot slot : handler.slots) {
            ItemStack stack = slot.getItem();
            if (stack == null || stack.isEmpty() || !ItemUtil.containsLore(stack, "Click to despawn")) continue;

            String displayName = COLOR_STRIP.matcher(stack.getHoverName().getString()).replaceAll("").trim();
            Matcher m = PET_ITEM_NAME.matcher(displayName);
            if (m.find()) {
                petLevel = safeInt(m.group(1), petLevel);
                petName = m.group(2).trim();
            }
            scanProgressFromLore(stack);
            return;
        }
    }

    private static void scanProgressFromLore(ItemStack stack) {
        var lore = stack.get(net.minecraft.core.component.DataComponents.LORE);
        if (lore == null) return;
        java.util.List<net.minecraft.network.chat.Component> lines = lore.lines();
        for (int i = 0; i < lines.size(); i++) {
            String s = COLOR_STRIP.matcher(lines.get(i).getString()).replaceAll("").trim();
            Matcher pm = PROGRESS_PAT.matcher(s);
            if (!pm.find()) continue;
            try { xpPct = Float.parseFloat(pm.group(1)); } catch (NumberFormatException ignored) {}
            if (i + 1 < lines.size()) {
                String s2 = COLOR_STRIP.matcher(lines.get(i + 1).getString()).replaceAll("").trim();
                Matcher xm = PROGRESS_XP_PAT.matcher(s2);
                if (xm.find()) {
                    xpCurrent = parseAbbrev(xm.group(1));
                    xpNext    = parseAbbrev(xm.group(2));
                }
            }
            return;
        }
    }

    private static double parseAbbrev(String s) {
        if (s == null) return -1;
        s = s.replace(",", "").trim().toUpperCase();
        double mult = 1;
        if (s.endsWith("K")) { mult = 1_000; s = s.substring(0, s.length() - 1); }
        else if (s.endsWith("M")) { mult = 1_000_000; s = s.substring(0, s.length() - 1); }
        else if (s.endsWith("B")) { mult = 1_000_000_000; s = s.substring(0, s.length() - 1); }
        try { return Double.parseDouble(s) * mult; } catch (NumberFormatException e) { return -1; }
    }

    private static void reset() {
        petName = null;
        petLevel = -1;
        xpCurrent = -1;
    }

    /** Strips rarity star, trailing punctuation, and whitespace from a pet name. */
    private static String cleanPetName(String s) {
        if (s == null) return null;
        return s.replace("✦", "").replaceAll("[!.]+$", "").trim();
    }

    private static int safeInt(String s, int fallback) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return fallback; }
    }

    public static String debugState() {
        return String.format("petHudEnabled=%s | name=%s | level=%d | currentXP=%.0f | nextXP=%.0f",
                FishSettings.petHudEnabled, petName, petLevel, xpCurrent, xpNext);
    }

    public static void renderHud(GuiGraphicsExtractor ctx, DeltaTracker tickCounter) {
        if (!FishSettings.petHudEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !Location.inSkyblock() || petName == null) return;

        if (FishSettings.petHudFadeIdle && pendingXp > 0 && (System.currentTimeMillis() - lastXpAt) > FishSettings.petHudFadeMs) {
            pendingXp = 0;
        }

        StringBuilder text = new StringBuilder();
        if (FishSettings.petHudShowLevel && petLevel >= 0) text.append("§7[Lvl ").append(petLevel).append("] ");
        text.append("§6").append(petName);

        // Detect max-level pet (Golden Dragon = 200, all others = 100). No progress line in lore at max.
        int maxLvl = "Golden Dragon".equalsIgnoreCase(petName) ? 200 : 100;
        boolean maxed = petLevel >= maxLvl || petMaxed;

        if (maxed) {
            text.append(" §a§lMAXED");
        } else {
            if (pendingXp > 0) text.append(" §a+").append(formatXp(pendingXp));
            if (xpCurrent >= 0 && xpNext > 0) {
                text.append(" §7(").append(formatXp(xpCurrent)).append("/").append(formatXp(xpNext))
                        .append(" ").append(String.format("%.1f%%", xpPct)).append(")");
            }
        }

        float sc = (float) FishSettings.petHudScale;
        ctx.pose().pushMatrix();
        ctx.pose().translate((float) FishSettings.petHudX, (float) FishSettings.petHudY);
        ctx.pose().scale(sc, sc);
        ctx.text(mc.font, text.toString(), 0, 0, -1, true);
        ctx.pose().popMatrix();
    }

    private static String formatXp(double v) {
        if (v >= 1_000_000) return String.format("%.1fM", v / 1_000_000);
        if (v >= 1_000)     return String.format("%.1fk", v / 1_000);
        return String.format("%.0f", v);
    }
}