package fishmod.features;

import fishmod.features.croesus.CroesusPrices;
import fishmod.utils.Constants;
import fishmod.utils.Location;
import fishmod.utils.config.values.FishSettings;
import fishmod.utils.data.ItemUtil;
import fishmod.utils.events.Events;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Farming coin/hr tracker. Listens for "+X <Crop>" chat (Skyblock farming gain messages),
 * multiplies by bazaar prices via CroesusPrices, rolls a 1hr window. Persists across reloads.
 */
public class FarmingTracker {

    /** Skyblock IDs we watch for in inventory (raw + enchanted forms of each crop). */
    private static final String[] WATCHED_IDS = {
        "WHEAT", "ENCHANTED_WHEAT", "ENCHANTED_HAY_BLOCK",
        "CARROT_ITEM", "ENCHANTED_CARROT",
        "POTATO_ITEM", "ENCHANTED_POTATO", "ENCHANTED_BAKED_POTATO",
        "PUMPKIN", "ENCHANTED_PUMPKIN",
        "MELON", "ENCHANTED_MELON", "ENCHANTED_MELON_BLOCK",
        "SUGAR_CANE", "ENCHANTED_SUGAR_CANE",
        "INK_SACK:3", "ENCHANTED_COCOA",
        "CACTUS", "ENCHANTED_CACTUS",
        "NETHER_STALK", "ENCHANTED_NETHER_WART",
        "MUSHROOM_COLLECTION", "RED_MUSHROOM", "ENCHANTED_RED_MUSHROOM", "BROWN_MUSHROOM", "ENCHANTED_BROWN_MUSHROOM"
    };

    private static final Map<String, Long> lastCounts = new HashMap<>();
    private static int scanTick = 0;

    // Pets aren't on the bazaar, so they have no CroesusPrices entry — value Slug pet drops at a
    // fixed price by rarity. Detected directly from inventory (they never hit the sacks).
    private static final long SLUG_EPIC_PRICE      = 500_000L;
    private static final long SLUG_LEGENDARY_PRICE = 5_000_000L;
    private static final Map<String, Long> lastPetCounts = new HashMap<>();
    private static boolean petScanInit = false;
    private static long petRebaselineUntilMs = 0;

    /** Crop display-name (as shown in Sacks hover) → Skyblock item ID. */
    private static final Map<String, String> NAME_TO_ID = new HashMap<>();
    static {
        NAME_TO_ID.put("Wheat", "WHEAT");
        NAME_TO_ID.put("Enchanted Wheat", "ENCHANTED_WHEAT");
        NAME_TO_ID.put("Enchanted Hay Bale", "ENCHANTED_HAY_BLOCK");
        NAME_TO_ID.put("Carrot", "CARROT_ITEM");
        NAME_TO_ID.put("Enchanted Carrot", "ENCHANTED_CARROT");
        NAME_TO_ID.put("Potato", "POTATO_ITEM");
        NAME_TO_ID.put("Enchanted Potato", "ENCHANTED_POTATO");
        NAME_TO_ID.put("Enchanted Baked Potato", "ENCHANTED_BAKED_POTATO");
        NAME_TO_ID.put("Pumpkin", "PUMPKIN");
        NAME_TO_ID.put("Enchanted Pumpkin", "ENCHANTED_PUMPKIN");
        NAME_TO_ID.put("Melon", "MELON");
        NAME_TO_ID.put("Enchanted Melon", "ENCHANTED_MELON");
        NAME_TO_ID.put("Enchanted Melon Block", "ENCHANTED_MELON_BLOCK");
        NAME_TO_ID.put("Sugar Cane", "SUGAR_CANE");
        NAME_TO_ID.put("Enchanted Sugar Cane", "ENCHANTED_SUGAR_CANE");
        NAME_TO_ID.put("Cocoa Beans", "INK_SACK:3");
        NAME_TO_ID.put("Enchanted Cocoa Beans", "ENCHANTED_COCOA");
        NAME_TO_ID.put("Cactus", "CACTUS");
        NAME_TO_ID.put("Enchanted Cactus", "ENCHANTED_CACTUS");
        NAME_TO_ID.put("Nether Wart", "NETHER_STALK");
        NAME_TO_ID.put("Enchanted Nether Wart", "ENCHANTED_NETHER_WART");
        NAME_TO_ID.put("Mutant Nether Wart",    "MUTANT_NETHER_WART");
        NAME_TO_ID.put("Red Mushroom", "RED_MUSHROOM");
        NAME_TO_ID.put("Brown Mushroom", "BROWN_MUSHROOM");
        NAME_TO_ID.put("Enchanted Red Mushroom", "ENCHANTED_RED_MUSHROOM");
        NAME_TO_ID.put("Enchanted Brown Mushroom", "ENCHANTED_BROWN_MUSHROOM");
        // Harvest Feast event — per-crop rare drops
        NAME_TO_ID.put("Cornucopia",             "CORNUCOPIA");           // Wheat
        NAME_TO_ID.put("Carrot Zest",            "CARROT_ZEST");          // Carrot
        NAME_TO_ID.put("Deepfries",              "DEEPFRIES");            // Potato
        NAME_TO_ID.put("Aggourdian",             "AGGOURDIAN");           // Pumpkin
        NAME_TO_ID.put("Cane Knot",              "CANE_KNOT");            // Sugar Cane
        NAME_TO_ID.put("Melon Juice",            "MELON_JUICE");          // Melon
        NAME_TO_ID.put("Cactus Flower",          "CACTUS_FLOWER");        // Cactus
        NAME_TO_ID.put("Designer Coffee Beans",  "DESIGNER_COFFEE_BEANS"); // Cocoa Beans
        NAME_TO_ID.put("Feastfungus",            "FEASTFUNGUS");          // Mushroom
        NAME_TO_ID.put("Botroot",                "BOTROOT");              // Nether Wart
        NAME_TO_ID.put("Salted Sunflower Seeds", "SALTED_SUNFLOWER_SEEDS");
        NAME_TO_ID.put("Crystalized Moonlight",  "CRYSTALIZED_MOONLIGHT");
        NAME_TO_ID.put("Floral Gelatin",         "FLORAL_GELATIN");
        NAME_TO_ID.put("Seasoning",              "SEASONING");
        // Garden plot drops
        NAME_TO_ID.put("Moonflower",             "MOONFLOWER");
        NAME_TO_ID.put("Enchanted Moonflower",   "ENCHANTED_MOONFLOWER");
        NAME_TO_ID.put("Wild Rose",              "WILD_ROSE");
        NAME_TO_ID.put("Enchanted Wild Rose",    "ENCHANTED_WILD_ROSE");
        NAME_TO_ID.put("Moonglade Rose",         "MOONGLADE_ROSE");
        NAME_TO_ID.put("Sunflower",              "SUNFLOWER");
        NAME_TO_ID.put("Enchanted Sunflower",    "ENCHANTED_SUNFLOWER");
    }
    private static final boolean DEBUG_FARMING = false;

    // Matches both "+1,234 Item" (additions) and "-1,234 Item" (withdrawals / compaction).
    private static final java.util.regex.Pattern SACK_HOVER_LINE =
            java.util.regex.Pattern.compile("([+-])\\s*([\\d,]+)\\s+([A-Za-z][A-Za-z ]+?)(?:\\s+\\(.*?\\))?\\s*$");

    // Compaction: each enchanted variant is exactly 160 of its precursor. When the sacks auto-compact,
    // the message pairs a raw removal ("-160 Carrot") with an enchanted addition ("+1 Enchanted Carrot").
    // The raw was already credited when it was first gained, so the enchanted addition is ignored ONLY
    // when it's explained by a matching precursor removal; an unmatched enchanted addition is real profit.
    /** sign|id|count → expiry-ms. Hypixel echoes the same [Sacks] message across multiple
     *  channels (system chat, action overlay, etc.); without a cross-message debounce the
     *  same +N line is parsed and credited 2-3x per real drop, inflating profit by 3x. */
    private static final Map<String, Long> RECENT_LINE_EXPIRY = new HashMap<>();
    private static final long LINE_DEDUP_WINDOW_MS = 2500L;

    private static final long COMPACT_RATIO = 160;
    private static final Map<String, String> ENCHANT_PRECURSOR = new HashMap<>();
    static {
        // Each row: { base, 1x-enchanted, 2x-enchanted-or-null }.
        // base → 1x-enchanted is 160 base; 1x → 2x is 160 1x-enchanted.
        // null in slot 2 means no second-tier compaction exists for that crop.
        String[][] tiers = {
            { "WHEAT",         "ENCHANTED_WHEAT",         "ENCHANTED_HAY_BLOCK"           },
            { "CARROT_ITEM",   "ENCHANTED_CARROT",        "ENCHANTED_GOLDEN_CARROT"       },
            { "POTATO_ITEM",   "ENCHANTED_POTATO",        "ENCHANTED_BAKED_POTATO"        },
            { "PUMPKIN",       "ENCHANTED_PUMPKIN",       "POLISHED_PUMPKIN"              },
            { "MELON",         "ENCHANTED_MELON",         "ENCHANTED_MELON_BLOCK"         },
            // Sugar Cane: 1x = Enchanted Sugar, 2x = Enchanted Sugar Cane.
            { "SUGAR_CANE",    "ENCHANTED_SUGAR",         "ENCHANTED_SUGAR_CANE"          },
            { "INK_SACK:3",    "ENCHANTED_COCOA",          null                           },
            // Cactus: 1x = Enchanted Cactus Green, 2x = Enchanted Cactus.
            { "CACTUS",        "ENCHANTED_CACTUS_GREEN",  "ENCHANTED_CACTUS"              },
            // Internal IDs use _WART (not _STALK) for the enchanted nether-wart tiers.
            { "NETHER_STALK",  "ENCHANTED_NETHER_WART",   "MUTANT_NETHER_WART"            },
            { "RED_MUSHROOM",  "ENCHANTED_RED_MUSHROOM",   "ENCHANTED_RED_MUSHROOM_BLOCK"  },
            { "BROWN_MUSHROOM","ENCHANTED_BROWN_MUSHROOM", "ENCHANTED_BROWN_MUSHROOM_BLOCK"},
            // Garden plot drops — full 3-tier chain: base → Enchanted → Compacted.
            { "MOONFLOWER",    "ENCHANTED_MOONFLOWER",    "COMPACTED_MOONFLOWER"          },
            { "WILD_ROSE",     "ENCHANTED_WILD_ROSE",     "COMPACTED_WILD_ROSE"           },
            { "SUNFLOWER",     "ENCHANTED_SUNFLOWER",     "COMPACTED_SUNFLOWER"           },
        };
        for (String[] row : tiers) {
            ENCHANT_PRECURSOR.put(row[1], row[0]);                       // 1x ← base
            if (row[2] != null) ENCHANT_PRECURSOR.put(row[2], row[1]);   // 2x ← 1x
        }
    }

    private static final long WINDOW_MS = 3_600_000L;

    private static long sessionStartMs = -1;
    private static double sessionCoins = 0;

    private static final Path SAVE_FILE = Paths.get("config/fishmod/farming_tracker.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static int btnX, btnY, btnW, btnH;
    private static boolean btnVisible = false;
    private static int pauseBtnX, pauseBtnY, pauseBtnW, pauseBtnH;
    private static boolean paused = false;
    private static long pauseStartedMs = 0;
    private static long lastActivityMs = 0;
    private static boolean autoPaused = false;

    private static class SaveData {
        long sessionStartMs;
        double sessionCoins;
        boolean paused;
        long pauseStartedMs;
        boolean autoPaused;
        long lastActivityMs;
    }

    private static void noteActivity() {
        long now = System.currentTimeMillis();
        boolean wasPaused = paused && autoPaused;
        if (wasPaused) {
            if (pauseStartedMs > 0 && sessionStartMs > 0) sessionStartMs += (now - pauseStartedMs);
            pauseStartedMs = 0;
            paused = false;
            autoPaused = false;
        }
        lastActivityMs = now;
        // Persist the unpause immediately. A compaction-only sacks message resolves to 0 coins, so
        // the value path's save() is skipped — without this the resume wouldn't survive a reload.
        if (wasPaused) save();
    }

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

    public static void init() {
        load();
        // Make sure bazaar prices are loaded — needed for coins calc.
        CroesusPrices.refreshIfStale();
        FishHudEditor.register("Farming Coins",
                () -> FishSettings.farmingTrackerHudX, v -> FishSettings.farmingTrackerHudX = v,
                () -> FishSettings.farmingTrackerHudY, v -> FishSettings.farmingTrackerHudY = v,
                120, 14 * 4,
                () -> FishSettings.farmingTrackerScale, v -> FishSettings.farmingTrackerScale = v,
                () -> FishSettings.farmingTrackerEnabled && inFarmingArea());

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            autoPause(System.currentTimeMillis());
            petScanInit = false; // re-baseline pets after reconnect so we don't credit the repopulating inventory
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            petScanInit = false;
            petRebaselineUntilMs = System.currentTimeMillis() + 3_000;
            // Fresh idle window on login so a stale lastActivityMs from a previous session
            // doesn't auto-pause us before the first sacks message arrives.
            if (sessionStartMs > 0) lastActivityMs = System.currentTimeMillis();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!FishSettings.farmingTrackerEnabled) return;
            tickAutoPause();
            if (client.player == null) return;
            if (!inFarmingArea()) { lastCounts.clear(); autoPause(System.currentTimeMillis()); return; }
            scanTick++;
            if (scanTick < 20) return;
            scanTick = 0;
            CroesusPrices.refreshIfStale();
            // Inventory-delta scanning intentionally disabled — was double-counting items that
            // briefly touch inventory before being swept into sacks. Sacks chat hover is the
            // single source of truth now. Slug pets are the exception: they go straight to
            // inventory (never the sacks) and have no bazaar price, so scan for them here.
            scanSlugPets(client);
        });

        // Sacks chat messages: "[Sacks] +X items." — real breakdown is in the hover event.
        Events.ON_GAME_MESSAGE.register(message -> {
            if (!FishSettings.farmingTrackerEnabled) return false;
            if (paused && !autoPaused) return false; // manual pause stops tracking; auto-pause resumes on a sacks message
            if (!inFarmingArea()) return false;
            String plain = message.getString().replaceAll("§.", "");
            if (!plain.contains("[Sacks]") && !plain.contains("Added items")) return false;
            noteActivity(); // any sacks message counts as activity: resets the 1-min idle clock (and auto-resumes)
            parseHoverFromText(message);
            return false;
        });
    }

    private static void parseHoverFromText(net.minecraft.text.Text root) {
        if (root == null) return;
        StringBuilder hover = new StringBuilder();
        collectHover(root, hover);
        if (hover.length() == 0) return;
        long now = System.currentTimeMillis();
        String[] lines = hover.toString().split("\\n|\\r");

        // Sweep expired dedup entries first so the map doesn't grow unbounded.
        RECENT_LINE_EXPIRY.entrySet().removeIf(e -> e.getValue() < now);

        // Aggregate additions and removals per item id.
        Map<String, Long> additions = new HashMap<>();
        Map<String, Long> removals = new HashMap<>();
        for (String line : lines) {
            String s = line.replaceAll("§.", "").trim();
            java.util.regex.Matcher m = SACK_HOVER_LINE.matcher(s);
            if (!m.find()) continue;
            boolean neg = m.group(1).equals("-");
            long count;
            try { count = Long.parseLong(m.group(2).replace(",", "")); } catch (NumberFormatException e) { continue; }
            String name = m.group(3).trim();
            // Primary: live Hypixel item list. Fallback: hand-rolled NAME_TO_ID.
            String id = fishmod.utils.SkyblockItems.idFor(name);
            if (id == null) id = NAME_TO_ID.get(name);
            if (id == null) {
                if (DEBUG_FARMING) System.out.println("[FishMod/Farming] unknown item: \"" + name + "\" (count " + (neg ? -count : count) + ")");
                continue;
            }
            // Cross-message dedup: same sign|id|count within window → echo, skip.
            String key = (neg ? "-|" : "+|") + id + "|" + count;
            if (RECENT_LINE_EXPIRY.containsKey(key)) continue;
            RECENT_LINE_EXPIRY.put(key, now + LINE_DEDUP_WINDOW_MS);
            (neg ? removals : additions).merge(id, count, Long::sum);
        }

        // Cancel out compactions: an enchanted addition explained by a matching precursor removal
        // (160:1) is the sack auto-compacting already-counted raw — drop that part. Any enchanted
        // addition NOT covered by a removal is genuine profit and survives.
        for (Map.Entry<String, Long> e : new HashMap<>(additions).entrySet()) {
            String precursor = ENCHANT_PRECURSOR.get(e.getKey());
            if (precursor == null) continue;
            Long rem = removals.get(precursor);
            if (rem == null || rem <= 0) continue;
            long explainedEnch = Math.min(e.getValue(), rem / COMPACT_RATIO);
            if (explainedEnch <= 0) continue;
            long remaining = e.getValue() - explainedEnch;
            if (remaining > 0) additions.put(e.getKey(), remaining); else additions.remove(e.getKey());
            removals.put(precursor, rem - explainedEnch * COMPACT_RATIO);
        }

        // Removals never subtract value (a withdrawal/sell keeps its worth; a compaction's raw was
        // already credited). Only surviving additions add profit.
        double totalCoins = 0;
        for (Map.Entry<String, Long> e : additions.entrySet()) {
            double price = CroesusPrices.price(e.getKey());
            if (price <= 0) {
                if (DEBUG_FARMING) System.out.println("[FishMod/Farming] no price for " + e.getKey());
                continue;
            }
            totalCoins += e.getValue() * price;
        }
        if (totalCoins != 0) {
            if (sessionStartMs < 0 && totalCoins > 0) sessionStartMs = now;
            sessionCoins += totalCoins;
            if (sessionCoins < 0) sessionCoins = 0; // never below zero
            noteActivity();
            save();
        }
    }

    private static void collectHover(net.minecraft.text.Text t, StringBuilder out) {
        java.util.Set<String> seen = new java.util.HashSet<>();
        collectHoverInto(t, out, seen);
    }
    private static void collectHoverInto(net.minecraft.text.Text t, StringBuilder out, java.util.Set<String> seen) {
        if (t.getStyle() != null && t.getStyle().getHoverEvent() instanceof net.minecraft.text.HoverEvent.ShowText st) {
            String s = st.value().getString();
            if (seen.add(s)) out.append(s).append('\n');
        }
        for (net.minecraft.text.Text sib : t.getSiblings()) collectHoverInto(sib, out, seen);
    }

    private static void scanInventoryDelta(MinecraftClient client) {
        PlayerInventory inv = client.player.getInventory();
        Map<String, Long> cur = new HashMap<>();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s == null || s.isEmpty()) continue;
            String id = ItemUtil.getId(s);
            if (id == null) continue;
            for (String w : WATCHED_IDS) {
                if (id.equals(w)) {
                    cur.merge(w, (long) s.getCount(), Long::sum);
                    break;
                }
            }
        }
        long now = System.currentTimeMillis();
        double totalCoins = 0;
        for (Map.Entry<String, Long> e : cur.entrySet()) {
            String id = e.getKey();
            long count = e.getValue();
            Long prev = lastCounts.get(id);
            if (prev != null && count > prev) {
                long delta = count - prev;
                double price = CroesusPrices.price(id);
                if (price > 0) totalCoins += delta * price;
            }
            lastCounts.put(id, count);
        }
        if (totalCoins > 0) {
            if (sessionStartMs < 0) sessionStartMs = now;
            sessionCoins += totalCoins;
            noteActivity();
            save();
        }
    }

    private static boolean inFarmingArea() {
        return Location.in(Location.GARDEN) || Location.in(Location.THE_FARMING_ISLANDS);
    }

    // Counts Slug pets in inventory by rarity and credits the fixed price for any newly gained ones.
    private static void scanSlugPets(MinecraftClient client) {
        if (paused && !autoPaused) return;
        PlayerInventory inv = client.player.getInventory();
        Map<String, Long> cur = new HashMap<>();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s == null || s.isEmpty()) continue;
            if (!"PET".equals(ItemUtil.getId(s))) continue;
            // Read petInfo NBT inline rather than via ItemUtil — blade-addons ships its own
            // fishmod.utils.data.ItemUtil that can win the classload and lacks getNbtString,
            // which crashed here with NoSuchMethodError.
            NbtComponent nbtComp = s.get(DataComponentTypes.CUSTOM_DATA);
            String petInfo = (nbtComp == null) ? null : nbtComp.copyNbt().getString("petInfo", null);
            if (petInfo == null) continue;
            String key = slugPetKey(petInfo);
            if (key != null) cur.merge(key, (long) s.getCount(), Long::sum);
        }

        // Don't credit pets already present at login / during the post-join inventory repopulate.
        if (!petScanInit || System.currentTimeMillis() < petRebaselineUntilMs) {
            lastPetCounts.clear();
            lastPetCounts.putAll(cur);
            petScanInit = true;
            return;
        }

        double credit = 0;
        for (Map.Entry<String, Long> e : cur.entrySet()) {
            long gained = e.getValue() - lastPetCounts.getOrDefault(e.getKey(), 0L);
            if (gained <= 0) continue;
            long price = e.getKey().equals("SLUG_LEGENDARY") ? SLUG_LEGENDARY_PRICE : SLUG_EPIC_PRICE;
            credit += gained * price;
            if (DEBUG_FARMING) System.out.println("[FishMod/Farming] +" + gained + " " + e.getKey() + " = " + (gained * price));
        }
        lastPetCounts.clear();
        lastPetCounts.putAll(cur);

        if (credit > 0) {
            long now = System.currentTimeMillis();
            if (sessionStartMs < 0) sessionStartMs = now;
            sessionCoins += credit;
            noteActivity();
            save();
        }
    }

    /** @return "SLUG_EPIC"/"SLUG_LEGENDARY" if the petInfo is a Slug of that rarity, else null. */
    private static String slugPetKey(String petInfo) {
        try {
            com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(petInfo).getAsJsonObject();
            if (!o.has("type") || !o.has("tier")) return null;
            if (!"SLUG".equalsIgnoreCase(o.get("type").getAsString())) return null;
            String tier = o.get("tier").getAsString().toUpperCase();
            if (tier.equals("EPIC")) return "SLUG_EPIC";
            if (tier.equals("LEGENDARY")) return "SLUG_LEGENDARY";
        } catch (RuntimeException ignored) {}
        return null;
    }

    public static void reset() {
        sessionStartMs = -1;
        sessionCoins = 0;
        lastCounts.clear();
        paused = false;
        pauseStartedMs = 0;
        autoPaused = false;
        lastActivityMs = 0;
        save();
    }

    private static synchronized void load() {
        try {
            if (!Files.exists(SAVE_FILE)) return;
            SaveData d = GSON.fromJson(Files.readString(SAVE_FILE), SaveData.class);
            if (d == null) return;
            sessionStartMs = d.sessionStartMs;
            sessionCoins = d.sessionCoins;
            paused = d.paused;
            pauseStartedMs = d.pauseStartedMs;
            autoPaused = d.autoPaused;
            lastActivityMs = d.lastActivityMs;
        } catch (IOException | RuntimeException ignored) {}
    }

    private static synchronized void save() {
        try {
            Files.createDirectories(SAVE_FILE.getParent());
            SaveData d = new SaveData();
            d.sessionStartMs = sessionStartMs;
            d.sessionCoins = sessionCoins;
            d.paused = paused;
            d.pauseStartedMs = pauseStartedMs;
            d.autoPaused = autoPaused;
            d.lastActivityMs = lastActivityMs;
            Files.writeString(SAVE_FILE, GSON.toJson(d));
        } catch (IOException ignored) {}
    }

    private static double perHour() {
        // Always session coins / elapsed active time, so Coins/hr always reconciles with the
        // displayed Session and Time (no rolling-window divergence at the 1-hour mark).
        if (sessionStartMs < 0) return 0;
        long ref = (paused && pauseStartedMs > 0) ? pauseStartedMs : System.currentTimeMillis();
        long elapsedMs = ref - sessionStartMs; // active time (pauses already excluded)
        if (elapsedMs < 60_000) return 0; // need >= 60s of data
        return sessionCoins * 3_600_000.0 / elapsedMs;
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

    private static String[] buildLines() {
        double hr = perHour();
        return new String[] {
                "§6§lFarming Profit" + (paused ? " §e§l(PAUSED)" : ""),
                "§7Coins/hr: §6" + (hr == 0 ? "§8—" : fmt(hr)),
                "§7Session: §6" + (sessionCoins == 0 ? "§8—" : fmt(sessionCoins)),
                "§7Time: §f"   + elapsedStr()
        };
    }

    public static void renderHud(DrawContext ctx, RenderTickCounter tick) {
        btnVisible = false;
        if (!FishSettings.farmingTrackerEnabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen)) return;
        if (!inFarmingArea()) return;
        int x = FishSettings.farmingTrackerHudX;
        int y = FishSettings.farmingTrackerHudY;
        int lh = Constants.TEXT_HEIGHT + 1;
        String[] lines = buildLines();
        float sc = (float) FishSettings.farmingTrackerScale;
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate((float)x, (float)y);
        ctx.getMatrices().scale(sc, sc);
        for (int i = 0; i < lines.length; i++)
            ctx.drawText(mc.textRenderer, lines[i], 0, lh * i, 0xFFFFFFFF, true);
        ctx.getMatrices().popMatrix();
    }

    public static void renderInScreen(DrawContext ctx, int mouseX, int mouseY) {
        btnVisible = false;
        if (!FishSettings.farmingTrackerEnabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!(mc.currentScreen instanceof HandledScreen<?>)) return;
        if (!inFarmingArea()) return;
        int x = FishSettings.farmingTrackerHudX;
        int y = FishSettings.farmingTrackerHudY;
        int lh = Constants.TEXT_HEIGHT + 1;
        String[] lines = buildLines();
        float sc = (float) FishSettings.farmingTrackerScale;

        String resetLabel = "§l[ Reset ]";
        String pauseLabel = paused ? "§l[ Resume ]" : "§l[ Pause ]";
        int resetW = mc.textRenderer.getWidth(resetLabel);
        int pauseW = mc.textRenderer.getWidth(pauseLabel);
        int padX = 4, padY = 3;
        int localBtnY = lh * lines.length - 2;
        int localResetW = resetW + padX * 2;
        int localPauseW = pauseW + padX * 2;
        int localBtnH = Constants.TEXT_HEIGHT + padY * 2 + 1;
        int gap = 4;
        btnX = x;
        btnY = y + (int)(localBtnY * sc);
        btnW = (int)(localResetW * sc);
        btnH = (int)(localBtnH * sc);
        int localPauseX = localResetW + gap;
        pauseBtnX = x + (int)(localPauseX * sc);
        pauseBtnY = btnY;
        pauseBtnW = (int)(localPauseW * sc);
        pauseBtnH = btnH;
        boolean resetHover = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        boolean pauseHover = mouseX >= pauseBtnX && mouseX <= pauseBtnX + pauseBtnW && mouseY >= pauseBtnY && mouseY <= pauseBtnY + pauseBtnH;
        String shownReset = resetHover ? "§c§l[ Reset ]" : resetLabel;
        String shownPause = pauseHover ? (paused ? "§a§l[ Resume ]" : "§e§l[ Pause ]") : pauseLabel;

        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate((float)x, (float)y);
        ctx.getMatrices().scale(sc, sc);
        for (int i = 0; i < lines.length; i++)
            ctx.drawText(mc.textRenderer, lines[i], 0, lh * i, 0xFFFFFFFF, true);
        ctx.drawText(mc.textRenderer, shownReset, padX, localBtnY + padY, 0xFFFFFFFF, true);
        ctx.drawText(mc.textRenderer, shownPause, localPauseX + padX, localBtnY + padY, 0xFFFFFFFF, true);
        ctx.getMatrices().popMatrix();
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
                if (pauseStartedMs > 0 && sessionStartMs > 0) sessionStartMs += (now - pauseStartedMs);
                pauseStartedMs = 0;
                paused = false;
                autoPaused = false;
                lastActivityMs = now;
            }
            save();
            return true;
        }
        return false;
    }
}
