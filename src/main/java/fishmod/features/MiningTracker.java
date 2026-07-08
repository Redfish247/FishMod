package fishmod.features;

import fishmod.features.croesus.CroesusPrices;
import fishmod.utils.Constants;
import fishmod.utils.Location;
import fishmod.utils.config.values.FishSettings;
import fishmod.utils.events.Events;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import fishmod.utils.data.ItemUtil;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Coin/hr tracker for mining drops. Same Sacks-hover model as FarmingTracker. */
public class MiningTracker {

    /** Sacks hover display-name → Skyblock bazaar item ID. */
    private static final Map<String, String> NAME_TO_ID = new HashMap<>();
    static {
        // Base ores + enchanted
        NAME_TO_ID.put("Coal",                "COAL");
        NAME_TO_ID.put("Enchanted Coal",      "ENCHANTED_COAL");
        NAME_TO_ID.put("Enchanted Charcoal",  "ENCHANTED_CHARCOAL");
        NAME_TO_ID.put("Enchanted Block of Coal", "ENCHANTED_COAL_BLOCK");
        NAME_TO_ID.put("Iron Ingot",          "IRON_INGOT");
        NAME_TO_ID.put("Enchanted Iron",      "ENCHANTED_IRON");
        NAME_TO_ID.put("Enchanted Iron Block","ENCHANTED_IRON_BLOCK");
        NAME_TO_ID.put("Gold Ingot",          "GOLD_INGOT");
        NAME_TO_ID.put("Enchanted Gold",      "ENCHANTED_GOLD");
        NAME_TO_ID.put("Enchanted Gold Block","ENCHANTED_GOLD_BLOCK");
        NAME_TO_ID.put("Diamond",             "DIAMOND");
        NAME_TO_ID.put("Enchanted Diamond",   "ENCHANTED_DIAMOND");
        NAME_TO_ID.put("Enchanted Diamond Block","ENCHANTED_DIAMOND_BLOCK");
        NAME_TO_ID.put("Emerald",             "EMERALD");
        NAME_TO_ID.put("Enchanted Emerald",   "ENCHANTED_EMERALD");
        NAME_TO_ID.put("Enchanted Emerald Block","ENCHANTED_EMERALD_BLOCK");
        NAME_TO_ID.put("Lapis Lazuli",        "INK_SACK:4");
        NAME_TO_ID.put("Enchanted Lapis Lazuli","ENCHANTED_LAPIS_LAZULI");
        NAME_TO_ID.put("Enchanted Lapis Lazuli Block","ENCHANTED_LAPIS_BLOCK");
        NAME_TO_ID.put("Redstone",            "REDSTONE");
        NAME_TO_ID.put("Enchanted Redstone",  "ENCHANTED_REDSTONE");
        NAME_TO_ID.put("Enchanted Redstone Block","ENCHANTED_REDSTONE_BLOCK");

        // Dwarven Mines
        NAME_TO_ID.put("Mithril",             "MITHRIL_ORE");
        NAME_TO_ID.put("Enchanted Mithril",   "ENCHANTED_MITHRIL");
        NAME_TO_ID.put("Titanium",            "TITANIUM_ORE");
        NAME_TO_ID.put("Enchanted Titanium",  "ENCHANTED_TITANIUM");
        NAME_TO_ID.put("Refined Mithril",     "REFINED_MITHRIL");
        NAME_TO_ID.put("Refined Titanium",    "REFINED_TITANIUM");
        NAME_TO_ID.put("Hard Stone",          "HARD_STONE");
        NAME_TO_ID.put("Enchanted Hard Stone","ENCHANTED_HARD_STONE");
        NAME_TO_ID.put("Sulphur",             "SULPHUR_ORE");
        NAME_TO_ID.put("Sulphur Ore",         "SULPHUR_ORE");
        NAME_TO_ID.put("Enchanted Sulphur",   "ENCHANTED_SULPHUR");
        NAME_TO_ID.put("Glacite Powder",      "GLACITE_POWDER");
        NAME_TO_ID.put("Glacite",             "GLACITE");
        NAME_TO_ID.put("Enchanted Glacite",   "ENCHANTED_GLACITE");

        // Gemstones — Rough, Flawed, Fine, Flawless, Perfect for each color
        String[] gems = {"Ruby","Amber","Amethyst","Jade","Sapphire","Topaz","Jasper","Opal","Aquamarine","Citrine","Onyx","Peridot"};
        String[] gemIds = {"RUBY","AMBER","AMETHYST","JADE","SAPPHIRE","TOPAZ","JASPER","OPAL","AQUAMARINE","CITRINE","ONYX","PERIDOT"};
        String[] grades = {"Rough","Flawed","Fine","Flawless"};
        for (int i = 0; i < gems.length; i++) {
            for (String g : grades) {
                NAME_TO_ID.put(g + " " + gems[i] + " Gemstone", g.toUpperCase() + "_" + gemIds[i] + "_GEM");
            }
            NAME_TO_ID.put("Perfect " + gems[i] + " Gemstone", "PERFECT_" + gemIds[i] + "_GEM");
        }

        // Corpse / Mineshaft drops
        NAME_TO_ID.put("Suspicious Scrap",    "SUSPICIOUS_SCRAP");
        NAME_TO_ID.put("Lapis Corpse",        "LAPIS_CORPSE");
        NAME_TO_ID.put("Tungsten Corpse",     "TUNGSTEN_CORPSE");
        NAME_TO_ID.put("Umber Corpse",        "UMBER_CORPSE");
        NAME_TO_ID.put("Vanguard Corpse",     "VANGUARD_CORPSE");
        NAME_TO_ID.put("Lapis Key",           "LAPIS_KEY");
        NAME_TO_ID.put("Tungsten Key",        "TUNGSTEN_KEY");
        NAME_TO_ID.put("Umber Key",           "UMBER_KEY");
        NAME_TO_ID.put("Vanguard Key",        "VANGUARD_KEY");
        NAME_TO_ID.put("Skeleton Skull",      "SKELETON_SKULL");
        NAME_TO_ID.put("Goblin Egg",          "GOBLIN_EGG");
        NAME_TO_ID.put("Green Goblin Egg",    "GREEN_GOBLIN_EGG");
        NAME_TO_ID.put("Red Goblin Egg",      "RED_GOBLIN_EGG");
        NAME_TO_ID.put("Yellow Goblin Egg",   "YELLOW_GOBLIN_EGG");
        NAME_TO_ID.put("Blue Goblin Egg",     "BLUE_GOBLIN_EGG");
        NAME_TO_ID.put("Treasurite",          "TREASURITE");
        NAME_TO_ID.put("Glacite Jewel",       "GLACITE_JEWEL");
        NAME_TO_ID.put("Mineral",             "MINERAL");
        NAME_TO_ID.put("Pickonimbus 2000",    "PICKONIMBUS");
        NAME_TO_ID.put("Bejeweled Handle",    "BEJEWELED_HANDLE");
        NAME_TO_ID.put("Robotron Reflector",  "ROBOTRON_REFLECTOR");
        NAME_TO_ID.put("Control Switch",      "CONTROL_SWITCH");
        NAME_TO_ID.put("Synthetic Heart",     "SYNTHETIC_HEART");
        NAME_TO_ID.put("Electron Transmitter","ELECTRON_TRANSMITTER");
        NAME_TO_ID.put("FTX 3070",            "FTX_3070");
        NAME_TO_ID.put("Superlite Motor",     "SUPERLITE_MOTOR");
        NAME_TO_ID.put("Flawless Cut Onyx",   "FLAWLESS_ONYX_GEM");
        NAME_TO_ID.put("Hard Stone Block",    "ENCHANTED_HARD_STONE");
        NAME_TO_ID.put("Sludge Juice",        "SLUDGE_JUICE");
        NAME_TO_ID.put("Glacite Amalgamation","GLACITE_AMALGAMATION");
        NAME_TO_ID.put("Dwarven O's Metallic Minis","DWARVEN_OS_METALLIC_MINIS");
        NAME_TO_ID.put("Wishing Compass",     "WISHING_COMPASS");
        NAME_TO_ID.put("Ascension Rope",      "ASCENSION_ROPE");
        NAME_TO_ID.put("Yog Egg",             "YOG_EGG");
        NAME_TO_ID.put("Goblin Omelette",     "GOBLIN_OMELETTE");
        NAME_TO_ID.put("Diamonite",           "DIAMONITE");
        NAME_TO_ID.put("Umber",               "UMBER");
        NAME_TO_ID.put("Enchanted Umber",     "ENCHANTED_UMBER");
        NAME_TO_ID.put("Refined Umber",       "REFINED_UMBER");
        NAME_TO_ID.put("Enchanted Tungsten",  "ENCHANTED_TUNGSTEN");
        NAME_TO_ID.put("Tungsten",            "TUNGSTEN");
        NAME_TO_ID.put("Tungsten Ore",        "TUNGSTEN_ORE");
        NAME_TO_ID.put("Umber Ore",           "UMBER_ORE");
    }

    // Persisted last-known bazaar prices — survives restarts so first corpse-open of a session prices correctly.
    private static final Map<String, Double> LAST_PRICE = new HashMap<>();

    private static double priceOf(String id) {
        double p = CroesusPrices.price(id);
        if (p > 0) {
            LAST_PRICE.put(id, p);
            return p;
        }
        Double cached = LAST_PRICE.get(id);
        return cached == null ? 0 : cached;
    }

    // Corpses opened while their key price was 0 — retried later once a price is known.
    private record DeferredOpen(String corpseKeyType, int times, double lootValue) {}
    private static final List<DeferredOpen> deferredOpens = new ArrayList<>();

    // Inventory diff tracking — catches items that go straight to inventory (sack full, no sack, etc.)
    private static final Map<String, Long> lastInvCounts = new HashMap<>();
    private static boolean invInitialized = false;
    private static int invScanCooldown = 0;
    private static long rebaselineUntilMs = 0; // during this window, just track baseline without crediting
    // Pending-credit cancellations: items the player dropped or bought (any later pickup of these doesn't count).
    private static final Map<String, Long> dropReservoir = new HashMap<>();      // id -> count blocked from credit
    private static final Map<String, Long> dropReservoirTime = new HashMap<>();  // id -> last touched ms
    private static final Map<String, Long> purchaseReservoir = new HashMap<>();
    private static final Map<String, Long> purchaseReservoirTime = new HashMap<>();
    // Corpse-received items: rewards already counted in the corpse net, must NOT be credited again when they hit sack or inv.
    private static final Map<String, Long> corpseReservoir = new HashMap<>();
    private static final Map<String, Long> corpseReservoirTime = new HashMap<>();
    private static final long RESERVOIR_TTL_MS = 60_000;

    // Auto-compactor rules: sourceId → (targetId, sourceCount needed to produce 1 target).
    // When sourceLost >= ratio * targetGained in the same scan, treat it as a compact, not a gain.
    private record Compact(String target, int ratio) {}
    private static final Map<String, Compact> COMPACTS = new HashMap<>();
    static {
        // Base ore → enchanted
        COMPACTS.put("COAL",        new Compact("ENCHANTED_COAL",    160));
        COMPACTS.put("IRON_INGOT",  new Compact("ENCHANTED_IRON",    160));
        COMPACTS.put("GOLD_INGOT",  new Compact("ENCHANTED_GOLD",    160));
        COMPACTS.put("DIAMOND",     new Compact("ENCHANTED_DIAMOND", 160));
        COMPACTS.put("EMERALD",     new Compact("ENCHANTED_EMERALD", 160));
        COMPACTS.put("INK_SACK:4",  new Compact("ENCHANTED_LAPIS_LAZULI", 160));
        COMPACTS.put("REDSTONE",    new Compact("ENCHANTED_REDSTONE", 160));
        COMPACTS.put("MITHRIL_ORE", new Compact("ENCHANTED_MITHRIL", 160));
        COMPACTS.put("TITANIUM_ORE",new Compact("ENCHANTED_TITANIUM",160));
        COMPACTS.put("HARD_STONE",  new Compact("ENCHANTED_HARD_STONE",160));
        COMPACTS.put("SULPHUR_ORE", new Compact("ENCHANTED_SULPHUR", 160));
        COMPACTS.put("GLACITE",     new Compact("ENCHANTED_GLACITE", 160));
        // Enchanted → block
        COMPACTS.put("ENCHANTED_COAL",    new Compact("ENCHANTED_COAL_BLOCK",    160));
        COMPACTS.put("ENCHANTED_IRON",    new Compact("ENCHANTED_IRON_BLOCK",    160));
        COMPACTS.put("ENCHANTED_GOLD",    new Compact("ENCHANTED_GOLD_BLOCK",    160));
        COMPACTS.put("ENCHANTED_DIAMOND", new Compact("ENCHANTED_DIAMOND_BLOCK", 160));
        COMPACTS.put("ENCHANTED_EMERALD", new Compact("ENCHANTED_EMERALD_BLOCK", 160));
        COMPACTS.put("ENCHANTED_LAPIS_LAZULI", new Compact("ENCHANTED_LAPIS_BLOCK", 160));
        COMPACTS.put("ENCHANTED_REDSTONE",new Compact("ENCHANTED_REDSTONE_BLOCK",160));
        COMPACTS.put("ENCHANTED_MITHRIL", new Compact("REFINED_MITHRIL", 160));
        COMPACTS.put("ENCHANTED_TITANIUM",new Compact("REFINED_TITANIUM",160));
        // Gemstones: Rough(4)→Flawed, Flawed(5)→Fine, Fine(5)→Flawless, Flawless(5)→Perfect
        String[] gems = {"RUBY","AMBER","AMETHYST","JADE","SAPPHIRE","TOPAZ","JASPER","OPAL","AQUAMARINE","CITRINE","ONYX","PERIDOT"};
        for (String g : gems) {
            COMPACTS.put("ROUGH_" + g + "_GEM",    new Compact("FLAWED_" + g + "_GEM",   4));
            COMPACTS.put("FLAWED_" + g + "_GEM",   new Compact("FINE_" + g + "_GEM",     5));
            COMPACTS.put("FINE_" + g + "_GEM",     new Compact("FLAWLESS_" + g + "_GEM", 5));
            COMPACTS.put("FLAWLESS_" + g + "_GEM", new Compact("PERFECT_" + g + "_GEM",  5));
        }
    }

    private static void expireReservoir(Map<String, Long> res, Map<String, Long> time) {
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<String, Long>> it = time.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, Long> e = it.next();
            if (now - e.getValue() > RESERVOIR_TTL_MS) { res.remove(e.getKey()); it.remove(); }
        }
    }
    private static long cancelFromReservoir(Map<String, Long> res, Map<String, Long> time, String id, long want) {
        Long have = res.get(id);
        if (have == null || have <= 0) return 0;
        long take = Math.min(have, want);
        long left = have - take;
        if (left <= 0) { res.remove(id); time.remove(id); }
        else { res.put(id, left); time.put(id, System.currentTimeMillis()); }
        return take;
    }

    // Accept either "+1,234 [icon] Name" or "Name +1,234". Hypixel inserts symbols like ☠/❁ between count and gemstone names.
    private static final Pattern SACK_HOVER_LINE =
            Pattern.compile("(?:([+-])\\s*([\\d,]+)\\s+(?:[^A-Za-z0-9\\s]\\s+)?([A-Za-z][A-Za-z0-9 '\\-]+?)|([A-Za-z][A-Za-z0-9 '\\-]+?)\\s*[:\\s]\\s*([+-])\\s*([\\d,]+))(?:\\s+\\(.*?\\))?\\s*$");
    private static final boolean DEBUG_SACKS = false; // logs unknown sack lines/items to console
    private static final Pattern CORPSE_LOOT_LINE =
            Pattern.compile("^\\s*(LAPIS|TUNGSTEN|UMBER|VANGUARD) CORPSE LOOT!\\s*(?:\\((\\d+)\\))?\\s*$");
    private static final Pattern SACK_LAST_SECONDS =
            Pattern.compile("\\(Last\\s+(\\d+)s\\.?\\)");
    // Bazaar / AH purchase patterns: capture quantity (default 1) and item display name
    private static final Pattern BAZAAR_BUY =
            Pattern.compile("Bought\\s+([\\d,]+)x?\\s+([A-Za-z][A-Za-z0-9 '\\-]+?)\\s+for\\s+[\\d,.]+ coins");
    private static final Pattern AH_BUY =
            Pattern.compile("You purchased\\s+([A-Za-z][A-Za-z0-9 '\\-]+?)\\s+for\\s+[\\d,.]+ coins");
    // Reward line under "  X CORPSE LOOT!" — e.g. "    ☘ Fine Peridot Gemstone" or "    Glacite Powder x5,992"
    private static final Pattern CORPSE_REWARD_LINE =
            Pattern.compile("^\\s{2,}(?:[^A-Za-z0-9\\s]+\\s+)?([A-Za-z][A-Za-z0-9 '\\-]+?)(?:\\s+x([\\d,]+))?\\s*$");

    // Reward-collection state (active immediately after a "X CORPSE LOOT!" line)
    private static String pendingCorpseType = null;     // "LAPIS_KEY" etc.
    private static int pendingCorpseTimes = 0;
    private static double pendingLootValue = 0;
    private static long pendingCorpseSetMs = 0;
    private static final Pattern COLOR_STRIP = Pattern.compile("§.");

    private static final long WINDOW_MS = 3_600_000L;

    private record Entry(long timeMs, double coins) {}
    private static final Deque<Entry> entries = new ArrayDeque<>();
    private static long sessionStartMs = -1;
    private static double sessionCoins = 0;

    // Corpse session tracking (signed net counts) + signed total coin value
    private static long corpseLapis = 0, corpseTungsten = 0, corpseUmber = 0, corpseVanguard = 0;
    private static double corpseCoins = 0;
    private static long keyLapis = 0, keyTungsten = 0, keyUmber = 0, keyVanguard = 0;
    private static double keyCoins = 0;
    // Denominator for coins/hr: first "(Last Ns.)" seeds it; afterwards we add real elapsed seconds between sack messages.
    private static long sackSeconds = 0;
    private static long lastSackMs = 0;
    private static boolean paused = false;
    private static long pauseStartedMs = 0;
    // Auto-pause: idle >60s / disconnect / area-leave pauses the timer; next activity auto-resumes.
    private static long lastActivityMs = 0;
    private static boolean autoPaused = false;

    private static final Path SAVE_FILE = Paths.get("config/fishmod/mining_tracker.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static int btnX, btnY, btnW, btnH;
    private static boolean btnVisible = false;
    private static int pauseBtnX, pauseBtnY, pauseBtnW, pauseBtnH;

    private static class SaveData {
        long[] times; double[] coins;
        long sessionStartMs;
        double sessionCoins;
        long corpseLapis, corpseTungsten, corpseUmber, corpseVanguard;
        double corpseCoins;
        long keyLapis, keyTungsten, keyUmber, keyVanguard;
        double keyCoins;
        long sackSeconds;
        long lastSackMs;
        Map<String, Double> lastPrice;
        // serialize deferred opens as parallel arrays
        String[] deferredKeyTypes;
        int[] deferredTimes;
        double[] deferredLoot;
        boolean paused;
        long pauseStartedMs;
        boolean autoPaused;
        long lastActivityMs;
    }

    public static void init() {
        load();
        CroesusPrices.refreshIfStale();
        FishHudEditor.register("Mining Coins",
                () -> FishSettings.miningTrackerHudX, v -> FishSettings.miningTrackerHudX = v,
                () -> FishSettings.miningTrackerHudY, v -> FishSettings.miningTrackerHudY = v,
                160, 14 * 5,
                () -> FishSettings.miningTrackerScale, v -> FishSettings.miningTrackerScale = v,
                () -> FishSettings.miningTrackerEnabled && inMiningArea());

        // Lobby/world swap: drop baseline and stale reservoirs so post-swap inv repopulation isn't credited.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            invInitialized = false;
            lastInvCounts.clear();
            dropReservoir.clear(); dropReservoirTime.clear();
            purchaseReservoir.clear(); purchaseReservoirTime.clear();
            corpseReservoir.clear(); corpseReservoirTime.clear();
            rebaselineUntilMs = System.currentTimeMillis() + 3_000; // ride out the inv repopulate
            if (sessionStartMs > 0) lastActivityMs = System.currentTimeMillis(); // fresh idle window on login
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            invInitialized = false;
            lastInvCounts.clear();
            rebaselineUntilMs = System.currentTimeMillis() + 3_000;
            autoPause(System.currentTimeMillis());
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!FishSettings.miningTrackerEnabled) return;
            tickAutoPause();
            drainDeferred();
            if (--invScanCooldown <= 0) {
                invScanCooldown = 5; // ~4 scans/sec
                scanInventoryDelta(client);
            }
            if (!inMiningArea()) { autoPause(System.currentTimeMillis()); return; }
            CroesusPrices.refreshIfStale();
        });

        Events.ON_GAME_MESSAGE.register(message -> {
            if (!FishSettings.miningTrackerEnabled) return false;
            String plain = COLOR_STRIP.matcher(message.getString()).replaceAll("");
            // Bazaar/AH purchases — register so the next inventory increase doesn't credit
            Matcher bb = BAZAAR_BUY.matcher(plain);
            if (bb.find()) {
                try {
                    long qty = Long.parseLong(bb.group(1).replace(",", ""));
                    String nm = bb.group(2).trim();
                    String id = fishmod.utils.SkyblockItems.idFor(nm);
                    if (id == null) id = NAME_TO_ID.get(nm);
                    if (id != null) {
                        purchaseReservoir.merge(id, qty, Long::sum);
                        purchaseReservoirTime.put(id, System.currentTimeMillis());
                        if (DEBUG_SACKS) System.out.println("[FishMod/Inv] purchase reservoir +" + qty + " " + id);
                    }
                } catch (NumberFormatException ignored) {}
            }
            Matcher ab = AH_BUY.matcher(plain);
            if (ab.find()) {
                String nm2 = ab.group(1).trim();
                String id = fishmod.utils.SkyblockItems.idFor(nm2);
                if (id == null) id = NAME_TO_ID.get(nm2);
                if (id != null) {
                    purchaseReservoir.merge(id, 1L, Long::sum);
                    purchaseReservoirTime.put(id, System.currentTimeMillis());
                    if (DEBUG_SACKS) System.out.println("[FishMod/Inv] AH reservoir +1 " + id);
                }
            }
            if (paused && !autoPaused) return false; // manual pause stops tracking; auto-pause resumes on activity
            long nowMs = System.currentTimeMillis();
            // Timeout-finalize any pending corpse-reward collection
            if (pendingCorpseType != null && (nowMs - pendingCorpseSetMs) > 5_000) {
                finalizePendingCorpse();
            }
            // Corpse-open: Hypixel sends "  LAPIS CORPSE LOOT!" (optionally with " (N)" for batched opens).
            Matcher cm = CORPSE_LOOT_LINE.matcher(plain);
            if (cm.find()) {
                if (pendingCorpseType != null) finalizePendingCorpse();
                String type = cm.group(1);
                int times = 1;
                if (cm.group(2) != null) { try { times = Integer.parseInt(cm.group(2)); } catch (NumberFormatException ignored) {} }
                pendingCorpseType = type + "_KEY";
                pendingCorpseTimes = times;
                pendingLootValue = 0;
                pendingCorpseSetMs = nowMs;
                if (DEBUG_SACKS) System.out.println("[FishMod/Keys] LOOT start " + times + "x " + pendingCorpseType);
                return false;
            }
            // While collecting rewards: parse each line; finalize on separator
            if (pendingCorpseType != null) {
                if (plain.contains("▬▬▬") || plain.startsWith("[Sacks]")) {
                    finalizePendingCorpse();
                } else {
                    Matcher rm = CORPSE_REWARD_LINE.matcher(plain);
                    if (rm.find()) {
                        String name = rm.group(1).trim();
                        if (name.equalsIgnoreCase("REWARDS")) { return false; } // section header, skip
                        long qty = 1;
                        if (rm.group(2) != null) {
                            try { qty = Long.parseLong(rm.group(2).replace(",", "")); } catch (NumberFormatException ignored) {}
                        }
                        String id = fishmod.utils.SkyblockItems.idFor(name);
                        if (id == null) id = NAME_TO_ID.get(name);
                        if (id != null) {
                            double price = priceOf(id);
                            if (price > 0) {
                                // Only reservoir when we successfully priced it (counted in corpse net).
                                // If price was 0, let it flow through [Sacks]/inv so it credits once bazaar populates.
                                corpseReservoir.merge(id, qty, Long::sum);
                                corpseReservoirTime.put(id, System.currentTimeMillis());
                                pendingLootValue += price * qty;
                                if (DEBUG_SACKS) System.out.println("[FishMod/Loot] +" + qty + " " + name + " = " + (long)(price*qty));
                            } else if (DEBUG_SACKS) {
                                System.out.println("[FishMod/Loot] no price for " + name + " (" + id + ") — falling through to sack/inv");
                            }
                        } else if (DEBUG_SACKS && name.length() >= 3) {
                            System.out.println("[FishMod/Loot] unknown reward: \"" + name + "\" x" + qty);
                        }
                    }
                }
            }
            if (!plain.contains("[Sacks]") && !plain.contains("Added items")) return false;
            long sackNow = System.currentTimeMillis();
            if (paused) { lastSackMs = sackNow; return false; }
            if (sackSeconds == 0) {
                // First sack message of the session — seed from "(Last Ns.)", capped at 60s
                Matcher sm = SACK_LAST_SECONDS.matcher(plain);
                if (sm.find()) {
                    try { sackSeconds = Math.min(60, Long.parseLong(sm.group(1))); } catch (NumberFormatException ignored) {}
                }
            } else if (lastSackMs > 0) {
                // Subsequent — cap elapsed at 60s so AFK gaps don't inflate the denominator
                long elapsedSec = Math.max(0, (sackNow - lastSackMs) / 1000);
                sackSeconds += Math.min(60, elapsedSec);
            }
            lastSackMs = sackNow;
            parseHover(message);
            return false;
        });
    }

    private static boolean inMiningArea() {
        return Location.in(Location.DWARVEN_MINES)
            || Location.in(Location.CRYSTAL_HOLLOWS)
            || Location.in(Location.MINESHAFT);
    }

    private static void parseHover(net.minecraft.network.chat.Component root) {
        if (paused && !autoPaused) return;
        StringBuilder hover = new StringBuilder();
        collectHover(root, hover);
        if (hover.length() == 0) return;
        double totalCoins = 0;
        long now = System.currentTimeMillis();
        for (String line : hover.toString().split("\\n|\\r")) {
            String s = COLOR_STRIP.matcher(line).replaceAll("").trim();
            if (s.isEmpty()) continue;
            Matcher m = SACK_HOVER_LINE.matcher(s);
            if (!m.find()) {
                if (DEBUG_SACKS && (s.matches(".*\\d.*"))) System.out.println("[FishMod/Sacks] no-match line: \"" + s + "\"");
                continue;
            }
            String signStr; String countStr; String name;
            if (m.group(1) != null) { // form A: "+N Name"
                signStr = m.group(1); countStr = m.group(2); name = m.group(3).trim();
            } else { // form B: "Name +N"
                name = m.group(4).trim(); signStr = m.group(5); countStr = m.group(6);
            }
            int sign = signStr.equals("-") ? -1 : 1;
            long count;
            try { count = Long.parseLong(countStr.replace(",", "")); } catch (NumberFormatException e) { continue; }
            String id = fishmod.utils.SkyblockItems.idFor(name);
            if (id == null) id = NAME_TO_ID.get(name);
            if (id == null) {
                if (DEBUG_SACKS) System.out.println("[FishMod/Sacks] unknown item: \"" + name + "\" (count " + sign*count + ")");
                continue;
            }
            double price = CroesusPrices.price(id);
            if (price <= 0) {
                if (DEBUG_SACKS) System.out.println("[FishMod/Sacks] no price for " + name + " (" + id + ")");
                continue;
            }
            long effectiveCount = count;
            if (sign > 0) {
                long absorbed = cancelFromReservoir(corpseReservoir, corpseReservoirTime, id, count);
                if (absorbed > 0 && DEBUG_SACKS) System.out.println("[FishMod/Sacks] +" + count + " " + name + " (skipped corpse=" + absorbed + ")");
                effectiveCount = count - absorbed;
                if (effectiveCount == 0) continue;
            }
            double delta = sign * effectiveCount * price;
            totalCoins += delta;
            switch (id) {
                case "LAPIS_CORPSE":    corpseLapis    += sign * count; corpseCoins += delta; break;
                case "TUNGSTEN_CORPSE": corpseTungsten += sign * count; corpseCoins += delta; break;
                case "UMBER_CORPSE":    corpseUmber    += sign * count; corpseCoins += delta; break;
                case "VANGUARD_CORPSE": corpseVanguard += sign * count; corpseCoins += delta; break;
                // Keys are tracked via corpse-open chat message instead (catches inventory keys too)
            }
        }
        if (totalCoins != 0) {
if (sessionStartMs < 0 && totalCoins > 0) sessionStartMs = now;
            entries.addLast(new Entry(now, totalCoins));
            sessionCoins += totalCoins;
            if (sessionCoins < 0) sessionCoins = 0;
            noteActivity();
            save();
        }
    }

    private static void collectHover(net.minecraft.network.chat.Component t, StringBuilder out) {
        java.util.Set<String> seen = new java.util.HashSet<>();
        collectHoverInto(t, out, seen);
    }
    private static void collectHoverInto(net.minecraft.network.chat.Component t, StringBuilder out, java.util.Set<String> seen) {
        if (t.getStyle() != null && t.getStyle().getHoverEvent() instanceof net.minecraft.network.chat.HoverEvent.ShowText st) {
            String s = st.value().getString();
            if (seen.add(s)) out.append(s).append('\n');
        }
        for (net.minecraft.network.chat.Component sib : t.getSiblings()) collectHoverInto(sib, out, seen);
    }

    private static void finalizePendingCorpse() {
        if (pendingCorpseType == null) return;
        String keyType = pendingCorpseType;
        int times = pendingCorpseTimes;
        double loot = pendingLootValue;
        pendingCorpseType = null; pendingCorpseTimes = 0; pendingLootValue = 0;

        // Always count the corpse immediately, even if price is 0 (will be applied later)
        switch (keyType) {
            case "LAPIS_KEY":    keyLapis    += times; break;
            case "TUNGSTEN_KEY": keyTungsten += times; break;
            case "UMBER_KEY":    keyUmber    += times; break;
            case "VANGUARD_KEY": keyVanguard += times; break;
        }
        applyOpenOrDefer(keyType, times, loot);
    }

    private static void applyOpenOrDefer(String keyType, int times, double loot) {
        // Always credit the loot side immediately — it's independent of key price.
        long now = System.currentTimeMillis();
        if (loot != 0) {
            keyCoins += loot;
            if (sessionStartMs < 0) sessionStartMs = now;
            entries.addLast(new Entry(now, loot));
            sessionCoins += loot;
            noteActivity();
        }
        // Lapis corpses are free — no key required.
        if (keyType.equals("LAPIS_KEY")) {
            save();
            if (DEBUG_SACKS) System.out.println("[FishMod/Keys] applied " + times + "x LAPIS (free) loot=" + (long)loot);
            return;
        }
        // Vanguard corpses are opened with a Skeleton Key, not a Vanguard Key.
        String costKey = keyType.equals("VANGUARD_KEY") ? "SKELETON_KEY" : keyType;
        double unitCost = priceOf(costKey);
        if (unitCost <= 0) {
            // Defer only the cost subtraction; loot is already in sessionCoins.
            deferredOpens.add(new DeferredOpen(keyType, times, 0));
            CroesusPrices.price(costKey);
            if (DEBUG_SACKS) System.out.println("[FishMod/Keys] credited loot " + (long)loot + ", deferred " + times + "x " + keyType + " cost (no price for " + costKey + ")");
            save();
            return;
        }
        double keyCost = unitCost * times;
        keyCoins -= keyCost;
        entries.addLast(new Entry(now, -keyCost));
        sessionCoins -= keyCost;
        if (sessionCoins < 0) sessionCoins = 0;
        save();
        if (DEBUG_SACKS) System.out.println("[FishMod/Keys] applied " + times + "x " + keyType
                + " loot=" + (long)loot + " cost=" + (long)keyCost + " net=" + (long)(loot-keyCost));
    }

    private static void scanInventoryDelta(Minecraft client) {
        if (paused && !autoPaused) return;
        if (client.player == null) return;
        // Skip while a Forge / Anvil / Crafting GUI is open — items appearing in inv from those
        // are not drops and shouldn't be credited. Re-baseline silently while open so we don't
        // pick up the post-close diff either.
        if (client.gui.screen() instanceof AbstractContainerScreen<?>) {
            String title = client.gui.screen().getTitle().getString();
            if (title.contains("Forge") || title.contains("The Forge") || title.contains("Anvil")
                    || title.contains("Crafting") || title.contains("Reforge") || title.contains("Recipe Book")) {
                rebaselineUntilMs = System.currentTimeMillis() + 3_000;
                return;
            }
        }
        Inventory inv = client.player.getInventory();
        // Build current count map, filtered to tracked mining IDs only (avoids buy-from-bazaar false positives)
        Map<String, Long> cur = new HashMap<>();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s == null || s.isEmpty()) continue;
            String id = ItemUtil.getId(s);
            if (id == null) continue;
            if (!NAME_TO_ID.containsValue(id)) continue;
            cur.merge(id, (long) s.getCount(), Long::sum);
        }
        if (!invInitialized || System.currentTimeMillis() < rebaselineUntilMs) {
            // Inventory is in transition (post-join/disconnect) — keep updating baseline silently.
            lastInvCounts.clear();
            lastInvCounts.putAll(cur);
            invInitialized = true;
            return;
        }
        expireReservoir(dropReservoir, dropReservoirTime);
        expireReservoir(purchaseReservoir, purchaseReservoirTime);
        expireReservoir(corpseReservoir, corpseReservoirTime);
        // Compute deltas
        Set<String> allIds = new HashSet<>(cur.keySet());
        allIds.addAll(lastInvCounts.keySet());
        Map<String, Long> delta = new HashMap<>();
        for (String id : allIds) {
            long d = cur.getOrDefault(id, 0L) - lastInvCounts.getOrDefault(id, 0L);
            if (d != 0) delta.put(id, d);
        }
        // Pair compacts: if a source went down and its target went up in the same scan, cancel both sides.
        for (Map.Entry<String, Compact> ce : COMPACTS.entrySet()) {
            String src = ce.getKey(); Compact c = ce.getValue();
            Long sd = delta.get(src); Long td = delta.get(c.target);
            if (sd == null || td == null || sd >= 0 || td <= 0) continue;
            long lost = -sd;
            long gained = td;
            long ops = Math.min(lost / c.ratio, gained); // # of compact operations
            if (ops <= 0) continue;
            long consumed = ops * c.ratio;
            // Reduce magnitudes; remove entries that hit zero
            long newSd = sd + consumed; // less negative (closer to 0)
            long newTd = td - ops;
            if (newSd == 0) delta.remove(src); else delta.put(src, newSd);
            if (newTd == 0) delta.remove(c.target); else delta.put(c.target, newTd);
            if (DEBUG_SACKS) System.out.println("[FishMod/Inv] compact " + consumed + " " + src + " -> " + ops + " " + c.target);
        }
        double credit = 0;
        for (Map.Entry<String, Long> en : delta.entrySet()) {
            String id = en.getKey();
            long d = en.getValue();
            if (d < 0) {
                // Item left inventory — record as droppable so future re-pickup doesn't credit.
                // (Sack deposits also subtract here, but [Sacks] hover handles those separately, so it's harmless.)
                dropReservoir.merge(id, -d, Long::sum);
                dropReservoirTime.put(id, System.currentTimeMillis());
                if (DEBUG_SACKS) System.out.println("[FishMod/Inv] -" + (-d) + " " + id + " (reservoir+)");
            } else if (d > 0) {
                long remaining = d;
                long usedCorpse = cancelFromReservoir(corpseReservoir, corpseReservoirTime, id, remaining);
                remaining -= usedCorpse;
                long usedPurchase = cancelFromReservoir(purchaseReservoir, purchaseReservoirTime, id, remaining);
                remaining -= usedPurchase;
                long usedDrop = cancelFromReservoir(dropReservoir, dropReservoirTime, id, remaining);
                remaining -= usedDrop;
                if ((usedPurchase | usedDrop | usedCorpse) != 0 && DEBUG_SACKS) {
                    System.out.println("[FishMod/Inv] +" + d + " " + id + " (skipped corpse=" + usedCorpse + " purchase=" + usedPurchase + " drop=" + usedDrop + ")");
                }
                if (remaining > 0) {
                    double price = priceOf(id);
                    if (price > 0) {
                        credit += remaining * price;
                        if (DEBUG_SACKS) System.out.println("[FishMod/Inv] +" + remaining + " " + id + " = " + (long)(remaining*price));
                    }
                }
            }
        }
        // Update baseline to current
        lastInvCounts.clear();
        lastInvCounts.putAll(cur);
        if (credit > 0) {
long now = System.currentTimeMillis();
            if (sessionStartMs < 0) sessionStartMs = now;
            entries.addLast(new Entry(now, credit));
            sessionCoins += credit;
            noteActivity();
            save();
        }
    }

    private static void drainDeferred() {
        if (deferredOpens.isEmpty()) return;
        for (Iterator<DeferredOpen> it = deferredOpens.iterator(); it.hasNext();) {
            DeferredOpen o = it.next();
            // Lapis corpses are free — drop any stale deferred entries.
            if (o.corpseKeyType.equals("LAPIS_KEY")) { it.remove(); continue; }
            String costKey = o.corpseKeyType.equals("VANGUARD_KEY") ? "SKELETON_KEY" : o.corpseKeyType;
            double unitCost = priceOf(costKey);
            if (unitCost <= 0) continue;
            it.remove();
            double keyCost = unitCost * o.times;
            double net = o.lootValue - keyCost;
            keyCoins += net;
            if (sessionStartMs < 0) sessionStartMs = System.currentTimeMillis();
            entries.addLast(new Entry(System.currentTimeMillis(), net));
            sessionCoins += net;
            if (sessionCoins < 0) sessionCoins = 0;
            noteActivity();
            if (DEBUG_SACKS) System.out.println("[FishMod/Keys] drained " + o.times + "x " + o.corpseKeyType
                    + " loot=" + (long)o.lootValue + " cost=" + (long)keyCost + " net=" + (long)net);
        }
        save();
    }

    // Public accessors for party commands / external readers
    public static long getKeyLapis()    { return keyLapis; }
    public static long getKeyTungsten() { return keyTungsten; }
    public static long getKeyUmber()    { return keyUmber; }
    public static long getKeyVanguard() { return keyVanguard; }
    public static long getCorpseTotal() { return keyLapis + keyTungsten + keyUmber + keyVanguard; }

    // Call at every profit event: auto-resumes if idle-paused, then refreshes the idle clock.
    private static void noteActivity() {
        long now = System.currentTimeMillis();
        if (paused && autoPaused) {
            if (pauseStartedMs > 0 && sessionStartMs > 0) sessionStartMs += (now - pauseStartedMs);
            pauseStartedMs = 0;
            paused = false;
            autoPaused = false;
        }
        lastActivityMs = now;
    }

    // Freeze the timer at freezeAtMs (idle uses last activity; disconnect/area-leave uses now).
    private static void autoPause(long freezeAtMs) {
        if (paused) return;
        paused = true;
        autoPaused = true;
        pauseStartedMs = (freezeAtMs > 0 ? freezeAtMs : System.currentTimeMillis());
    }

    private static void tickAutoPause() {
        if (paused || sessionStartMs <= 0 || lastActivityMs <= 0) return;
        if (System.currentTimeMillis() - lastActivityMs >= 60_000L) autoPause(lastActivityMs);
    }

    public static void reset() {
        entries.clear();
        sessionStartMs = -1;
        lastActivityMs = 0;
        autoPaused = false;
        sessionCoins = 0;
        corpseLapis = corpseTungsten = corpseUmber = corpseVanguard = 0;
        corpseCoins = 0;
        keyLapis = keyTungsten = keyUmber = keyVanguard = 0;
        keyCoins = 0;
        sackSeconds = 0;
        lastSackMs = 0;
        deferredOpens.clear();
        paused = false;
        pauseStartedMs = 0;
        save();
    }

    private static synchronized void load() {
        try {
            if (!Files.exists(SAVE_FILE)) return;
            SaveData d = GSON.fromJson(Files.readString(SAVE_FILE), SaveData.class);
            if (d == null) return;
            sessionStartMs = d.sessionStartMs;
            sessionCoins = d.sessionCoins;
            corpseLapis = d.corpseLapis;
            corpseTungsten = d.corpseTungsten;
            corpseUmber = d.corpseUmber;
            corpseVanguard = d.corpseVanguard;
            corpseCoins = d.corpseCoins;
            keyLapis = d.keyLapis;
            keyTungsten = d.keyTungsten;
            keyUmber = d.keyUmber;
            keyVanguard = d.keyVanguard;
            keyCoins = d.keyCoins;
            sackSeconds = d.sackSeconds;
            lastSackMs = d.lastSackMs;
            paused = d.paused;
            pauseStartedMs = d.pauseStartedMs;
            autoPaused = d.autoPaused;
            lastActivityMs = d.lastActivityMs;
            if (d.lastPrice != null) LAST_PRICE.putAll(d.lastPrice);
            if (d.deferredKeyTypes != null && d.deferredTimes != null && d.deferredLoot != null) {
                int n = Math.min(d.deferredKeyTypes.length, Math.min(d.deferredTimes.length, d.deferredLoot.length));
                for (int i = 0; i < n; i++) deferredOpens.add(new DeferredOpen(d.deferredKeyTypes[i], d.deferredTimes[i], d.deferredLoot[i]));
            }
            if (d.times != null && d.coins != null) {
                int n = Math.min(d.times.length, d.coins.length);
                for (int i = 0; i < n; i++) entries.addLast(new Entry(d.times[i], d.coins[i]));
            }
        } catch (IOException | RuntimeException ignored) {}
    }

    private static synchronized void save() {
        try {
            Files.createDirectories(SAVE_FILE.getParent());
            SaveData d = new SaveData();
            d.times = entries.stream().mapToLong(Entry::timeMs).toArray();
            d.coins = entries.stream().mapToDouble(Entry::coins).toArray();
            d.sessionStartMs = sessionStartMs;
            d.sessionCoins = sessionCoins;
            d.corpseLapis = corpseLapis;
            d.corpseTungsten = corpseTungsten;
            d.corpseUmber = corpseUmber;
            d.corpseVanguard = corpseVanguard;
            d.corpseCoins = corpseCoins;
            d.keyLapis = keyLapis;
            d.keyTungsten = keyTungsten;
            d.keyUmber = keyUmber;
            d.keyVanguard = keyVanguard;
            d.keyCoins = keyCoins;
            d.sackSeconds = sackSeconds;
            d.lastSackMs = lastSackMs;
            d.paused = paused;
            d.pauseStartedMs = pauseStartedMs;
            d.autoPaused = autoPaused;
            d.lastActivityMs = lastActivityMs;
            d.lastPrice = new HashMap<>(LAST_PRICE);
            int n = deferredOpens.size();
            d.deferredKeyTypes = new String[n];
            d.deferredTimes = new int[n];
            d.deferredLoot = new double[n];
            for (int i = 0; i < n; i++) {
                DeferredOpen o = deferredOpens.get(i);
                d.deferredKeyTypes[i] = o.corpseKeyType;
                d.deferredTimes[i] = o.times;
                d.deferredLoot[i] = o.lootValue;
            }
            Files.writeString(SAVE_FILE, GSON.toJson(d));
        } catch (IOException ignored) {}
    }

    private static double perHour() {
        // Prefer the accurate denominator: sum of "(Last Ns.)" reported by Hypixel.
        if (sackSeconds >= 10) {
            return sessionCoins * 3600.0 / sackSeconds;
        }
        if (sessionStartMs < 0) return 0;
        long elapsedMs = System.currentTimeMillis() - sessionStartMs;
        if (elapsedMs < 60_000) return 0;
        long windowMs = Math.min(elapsedMs, WINDOW_MS);
        long now = System.currentTimeMillis();
        long cutoff = now - windowMs;
        while (!entries.isEmpty() && entries.peekFirst().timeMs() < cutoff) entries.pollFirst();
        double total = 0;
        for (Entry e : entries) total += e.coins();
        return total * 3_600_000.0 / windowMs;
    }

    private static String fmt(double n) {
        if (n >= 1_000_000_000) return String.format("%.2fB", n / 1_000_000_000.0);
        if (n >= 1_000_000)     return String.format("%.2fM", n / 1_000_000.0);
        if (n >= 1_000)         return String.format("%.1fk", n / 1_000.0);
        return String.format("%.0f", n);
    }

    private static String elapsedStr() {
        if (sessionStartMs < 0) return "—";
        long ref = (paused && pauseStartedMs > 0) ? pauseStartedMs : System.currentTimeMillis();
        long s = (ref - sessionStartMs) / 1000;
        long m = s / 60; s %= 60;
        long h = m / 60; m %= 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }

    private static String corpseLine() {
        // Counts come from corpse-open chat messages (1 key = 1 corpse opened); value = cost of keys spent
        long l = Math.abs(keyLapis), t = Math.abs(keyTungsten), u = Math.abs(keyUmber), v = Math.abs(keyVanguard);
        if ((l|t|u|v) == 0 && keyCoins == 0) return "§7Corpses: §8—";
        String val;
        if (keyCoins == 0) val = "§80";
        else if (keyCoins < 0) val = "§c-" + fmt(-keyCoins);
        else val = "§a+" + fmt(keyCoins);
        return "§7Corpses: §b" + l + "L§7, §b" + t + "T§7, §b" + u + "U§7, §b" + v + "V§7: " + val;
    }

    private static String[] buildLines() {
        double hr = perHour();
        return new String[] {
                "§b§lMining Profit" + (paused ? " §e§l(PAUSED)" : ""),
                "§7Coins/hr: §b" + (hr == 0 ? "§8—" : fmt(hr)),
                corpseLine(),
                "§7Session: §b" + (sessionCoins == 0 ? "§8—" : fmt(sessionCoins)),
                "§7Time: §f"   + elapsedStr()
        };
    }

    public static void renderHud(GuiGraphicsExtractor ctx, DeltaTracker tick) {
        btnVisible = false;
        if (!FishSettings.miningTrackerEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.gui.screen() != null && !(mc.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen)) return;
        if (!inMiningArea()) return;
        int x = FishSettings.miningTrackerHudX;
        int y = FishSettings.miningTrackerHudY;
        int lh = Constants.TEXT_HEIGHT + 1;
        String[] lines = buildLines();
        float sc = (float) FishSettings.miningTrackerScale;
        ctx.pose().pushMatrix();
        ctx.pose().translate((float) x, (float) y);
        ctx.pose().scale(sc, sc);
        for (int i = 0; i < lines.length; i++)
            ctx.text(mc.font, lines[i], 0, lh * i, 0xFFFFFFFF, true);
        ctx.pose().popMatrix();
    }

    public static void renderInScreen(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        btnVisible = false;
        if (!FishSettings.miningTrackerEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?>)) return;
        if (!inMiningArea()) return;
        int x = FishSettings.miningTrackerHudX;
        int y = FishSettings.miningTrackerHudY;
        int lh = Constants.TEXT_HEIGHT + 1;
        String[] lines = buildLines();
        float sc = (float) FishSettings.miningTrackerScale;
        String resetLabel = "§l[ Reset ]";
        String pauseLabel = paused ? "§l[ Resume ]" : "§l[ Pause ]";
        int resetW = mc.font.width(resetLabel);
        int pauseW = mc.font.width(pauseLabel);
        int padX = 4, padY = 3;
        int localBtnY = lh * lines.length - 2;
        int localResetW = resetW + padX * 2;
        int localPauseW = pauseW + padX * 2;
        int localBtnH = Constants.TEXT_HEIGHT + padY * 2 + 1;
        int gap = 4;
        // Reset button
        btnX = x;
        btnY = y + (int)(localBtnY * sc);
        btnW = (int)(localResetW * sc);
        btnH = (int)(localBtnH * sc);
        // Pause button (to the right of Reset)
        int localPauseX = localResetW + gap;
        pauseBtnX = x + (int)(localPauseX * sc);
        pauseBtnY = btnY;
        pauseBtnW = (int)(localPauseW * sc);
        pauseBtnH = btnH;
        boolean resetHover = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        boolean pauseHover = mouseX >= pauseBtnX && mouseX <= pauseBtnX + pauseBtnW && mouseY >= pauseBtnY && mouseY <= pauseBtnY + pauseBtnH;
        String shownReset = resetHover ? "§c§l[ Reset ]" : resetLabel;
        String shownPause = pauseHover ? (paused ? "§a§l[ Resume ]" : "§e§l[ Pause ]") : pauseLabel;
        ctx.pose().pushMatrix();
        ctx.pose().translate((float) x, (float) y);
        ctx.pose().scale(sc, sc);
        for (int i = 0; i < lines.length; i++)
            ctx.text(mc.font, lines[i], 0, lh * i, 0xFFFFFFFF, true);
        ctx.text(mc.font, shownReset, padX, localBtnY + padY, 0xFFFFFFFF, true);
        ctx.text(mc.font, shownPause, localPauseX + padX, localBtnY + padY, 0xFFFFFFFF, true);
        ctx.pose().popMatrix();
        btnVisible = true;
    }

    public static boolean handleScreenClick(double mx, double my) {
        if (!btnVisible) return false;
        if (mx >= btnX && mx <= btnX + btnW && my >= btnY && my <= btnY + btnH) {
            reset();
            return true;
        }
        if (mx >= pauseBtnX && mx <= pauseBtnX + pauseBtnW && my >= pauseBtnY && my <= pauseBtnY + pauseBtnH) {
            long now = System.currentTimeMillis();
            if (!paused) {
                paused = true;
                autoPaused = false;
                pauseStartedMs = now;
            } else {
                // Shift sessionStartMs forward by the paused duration so elapsed time stays accurate
                if (pauseStartedMs > 0 && sessionStartMs > 0) sessionStartMs += (now - pauseStartedMs);
                pauseStartedMs = 0;
                paused = false;
                autoPaused = false;
                lastActivityMs = now;
                lastSackMs = now;
            }
            save();
            return true;
        }
        return false;
    }
}
