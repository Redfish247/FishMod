package fishmod.utils;

import fishmod.utils.config.values.FishSettings;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.text.Text;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HypixelApi {

    /** Cumulative XP required for each catacombs / class level (index = level 0-50) */
    public static final long[] CATA_XP_TABLE = {
        0L, 50L, 125L, 235L, 395L, 625L, 955L, 1_425L, 2_095L, 3_045L,
        4_385L, 6_275L, 8_940L, 12_700L, 17_960L, 25_340L, 35_640L, 50_040L, 70_040L, 97_640L,
        135_640L, 188_140L, 259_640L, 356_640L, 488_640L, 668_640L, 911_640L, 1_239_640L, 1_684_640L, 2_284_640L,
        3_084_640L, 4_149_640L, 5_559_640L, 7_459_640L, 9_959_640L, 13_259_640L, 17_559_640L, 23_159_640L, 30_359_640L, 39_559_640L,
        51_559_640L, 66_559_640L, 85_559_640L, 109_559_640L, 139_559_640L, 177_559_640L, 225_559_640L, 285_559_640L, 360_559_640L, 453_559_640L,
        569_809_640L
    };

    public static final long XP_FOR_50 = CATA_XP_TABLE[50];

    // ─── proxy config ─────────────────────────────────────────────────────────
    private static final String PROXY_URL = "https://fishmod.redfish2471.workers.dev";
    private static final String MOD_TOKEN = "fishmod123";

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    // ─── persistent cache ──────────────────────────────────────────────────────
    private static final long CACHE_TTL_MS = 30 * 60 * 1000L; // 30 minutes
    /** lower-cased player name → UUID without dashes. Backed by an on-disk cache (see below). */
    public  static final Map<String, String> uuidByName    = new ConcurrentHashMap<>();
    /** player name → epoch-ms when DungeonData was last fetched */
    public  static final Map<String, Long>   dataTimestamp = new ConcurrentHashMap<>();

    // ─── persistent name→UUID cache ────────────────────────────────────────────
    // A UUID never changes for an account and players rename only rarely, so name→UUID is cheap to keep
    // on disk and saves a Mojang round-trip on every repeat lookup (party members, friends, etc.). Each
    // entry carries the time it was written and expires after UUID_CACHE_TTL_MS, so a rename / recycle
    // self-corrects within a bounded window — and the resolve that refreshes it is Mojang-authoritative
    // (see resolveUuid), so we never re-introduce the stale-mirror bug. This does NOT touch the worker:
    // name resolution hits Mojang, the worker is only used for the by-UUID stats fetch.
    private static final long UUID_CACHE_TTL_MS = 24 * 60 * 60 * 1000L; // 24h
    private static final Map<String, Long> uuidCachedAt = new ConcurrentHashMap<>();
    private static volatile boolean uuidCacheLoaded = false;
    private static final java.util.concurrent.atomic.AtomicBoolean uuidSavePending = new java.util.concurrent.atomic.AtomicBoolean(false);

    private static String nameKey(String name) {
        return name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
    }

    /** Cached UUID for a name if present and unexpired, else null. Lazily loads the on-disk cache once. */
    public static String getCachedUuid(String name) {
        ensureUuidCacheLoaded();
        String k = nameKey(name);
        Long ts = uuidCachedAt.get(k);
        if (ts == null) return null;
        if (System.currentTimeMillis() - ts > UUID_CACHE_TTL_MS) { // expired — drop it
            uuidByName.remove(k);
            uuidCachedAt.remove(k);
            return null;
        }
        return uuidByName.get(k);
    }

    /** Records a fresh name→UUID mapping (from an authoritative resolve) and schedules a debounced save. */
    public static void putUuid(String name, String uuid) {
        if (name == null || uuid == null || uuid.isEmpty()) return;
        ensureUuidCacheLoaded();
        String k = nameKey(name);
        uuidByName.put(k, uuid);
        uuidCachedAt.put(k, System.currentTimeMillis());
        scheduleUuidSave();
    }

    private static synchronized void ensureUuidCacheLoaded() {
        if (uuidCacheLoaded) return;
        try {
            var file = FabricLoader.getInstance().getConfigDir().resolve("fishmod_uuid_cache.json");
            if (Files.exists(file)) {
                JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                JsonObject entries = root.has("entries") ? root.getAsJsonObject("entries") : new JsonObject();
                long now = System.currentTimeMillis();
                for (Map.Entry<String, JsonElement> e : entries.entrySet()) {
                    try {
                        JsonObject o = e.getValue().getAsJsonObject();
                        long ts = o.get("ts").getAsLong();
                        if (now - ts > UUID_CACHE_TTL_MS) continue; // stale — skip
                        String uuid = o.get("uuid").getAsString();
                        if (uuid == null || uuid.isEmpty()) continue;
                        uuidByName.put(e.getKey(), uuid);
                        uuidCachedAt.put(e.getKey(), ts);
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
        // Flush on shutdown so the most recent lookups survive even a hard close.
        try { Runtime.getRuntime().addShutdownHook(new Thread(HypixelApi::saveUuidCacheNow, "fishmod-uuid-cache-flush")); }
        catch (Exception ignored) {}
        uuidCacheLoaded = true;
    }

    private static void scheduleUuidSave() {
        if (!uuidSavePending.compareAndSet(false, true)) return; // a flush is already queued
        CompletableFuture.delayedExecutor(5, java.util.concurrent.TimeUnit.SECONDS)
            .execute(() -> { uuidSavePending.set(false); saveUuidCacheNow(); });
    }

    private static synchronized void saveUuidCacheNow() {
        try {
            var file = FabricLoader.getInstance().getConfigDir().resolve("fishmod_uuid_cache.json");
            JsonObject entries = new JsonObject();
            long now = System.currentTimeMillis();
            for (Map.Entry<String, Long> e : uuidCachedAt.entrySet()) {
                if (now - e.getValue() > UUID_CACHE_TTL_MS) continue;
                String uuid = uuidByName.get(e.getKey());
                if (uuid == null) continue;
                JsonObject o = new JsonObject();
                o.addProperty("uuid", uuid);
                o.addProperty("ts", e.getValue());
                entries.add(e.getKey(), o);
            }
            JsonObject root = new JsonObject();
            root.addProperty("version", 1);
            root.add("entries", entries);
            Files.writeString(file, root.toString());
        } catch (Exception ignored) {}
    }

    public static void loadPfCache(Map<String, DungeonData> liveCache) {
        try {
            var file = FabricLoader.getInstance().getConfigDir().resolve("fishmod_pf_cache.json");
            if (!Files.exists(file)) return;
            JsonObject root    = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            JsonObject entries = root.getAsJsonObject("entries");
            long now = System.currentTimeMillis();
            for (Map.Entry<String, JsonElement> e : entries.entrySet()) {
                String     name = e.getKey();
                JsonObject obj  = e.getValue().getAsJsonObject();
                long ts = obj.get("timestamp").getAsLong();
                if (now - ts > CACHE_TTL_MS) continue; // stale — skip
                if (obj.has("uuid")) uuidByName.put(name, obj.get("uuid").getAsString());
                dataTimestamp.put(name, ts);
                DungeonData d = new DungeonData();
                if (obj.has("cataXp"))       d.cataXp       = obj.get("cataXp").getAsLong();
                if (obj.has("cataLevel"))    d.cataLevel    = obj.get("cataLevel").getAsInt();
                if (obj.has("totalSecrets")) d.totalSecrets = obj.get("totalSecrets").getAsLong();
                if (obj.has("totalRuns"))    d.totalRuns    = obj.get("totalRuns").getAsLong();
                if (obj.has("secretAverage") && !obj.get("secretAverage").isJsonNull())
                    d.secretAverage = obj.get("secretAverage").getAsString();
                if (obj.has("cataPbs")) {
                    JsonArray arr = obj.getAsJsonArray("cataPbs");
                    for (int i = 0; i < Math.min(arr.size(), 8); i++)
                        d.cataPbs[i] = arr.get(i).isJsonNull() ? null : arr.get(i).getAsString();
                }
                if (obj.has("masterPbs")) {
                    JsonArray arr = obj.getAsJsonArray("masterPbs");
                    for (int i = 0; i < Math.min(arr.size(), 8); i++)
                        d.masterPbs[i] = arr.get(i).isJsonNull() ? null : arr.get(i).getAsString();
                }
                if (obj.has("cataTimes")) {
                    JsonArray arr = obj.getAsJsonArray("cataTimes");
                    for (int i = 0; i < Math.min(arr.size(), 8); i++)
                        d.cataTimes[i] = arr.get(i).getAsLong();
                }
                if (obj.has("masterTimes")) {
                    JsonArray arr = obj.getAsJsonArray("masterTimes");
                    for (int i = 0; i < Math.min(arr.size(), 8); i++)
                        d.masterTimes[i] = arr.get(i).getAsLong();
                }
                if (obj.has("ragnarockChimera")) d.ragnarockChimera = obj.get("ragnarockChimera").getAsInt();
                if (obj.has("magicalPower"))    d.magicalPower     = obj.get("magicalPower").getAsInt();
                if (obj.has("termUltimate") && !obj.get("termUltimate").isJsonNull()) d.termUltimate = obj.get("termUltimate").getAsString();
                if (obj.has("armorStars")) {
                    JsonArray a = obj.getAsJsonArray("armorStars");
                    if (a.size() == 4) d.armorStars = new int[]{a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt(), a.get(3).getAsInt()};
                }
                if (obj.has("equipStars")) {
                    JsonArray a = obj.getAsJsonArray("equipStars");
                    if (a.size() == 4) d.equipStars = new int[]{a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt(), a.get(3).getAsInt()};
                }
                liveCache.put(name, d);
            }
        } catch (Exception ignored) {}
    }

    public static void savePfCacheAsync(Map<String, DungeonData> liveCache) {
        CompletableFuture.runAsync(() -> {
            try {
                var file = FabricLoader.getInstance().getConfigDir().resolve("fishmod_pf_cache.json");
                JsonObject root    = new JsonObject();
                JsonObject entries = new JsonObject();
                for (Map.Entry<String, DungeonData> e : liveCache.entrySet()) {
                    String     name = e.getKey();
                    DungeonData d   = e.getValue();
                    Long ts = dataTimestamp.get(name);
                    if (ts == null) continue;
                    JsonObject obj = new JsonObject();
                    String uuid = uuidByName.get(name);
                    if (uuid != null) obj.addProperty("uuid", uuid);
                    obj.addProperty("timestamp",    ts);
                    obj.addProperty("cataXp",       d.cataXp);
                    obj.addProperty("cataLevel",    d.cataLevel);
                    obj.addProperty("totalSecrets", d.totalSecrets);
                    obj.addProperty("totalRuns",    d.totalRuns);
                    if (d.secretAverage != null) obj.addProperty("secretAverage", d.secretAverage);
                    else obj.add("secretAverage", JsonNull.INSTANCE);
                    JsonArray cataPbs = new JsonArray();
                    for (String pb : d.cataPbs)   { if (pb != null) cataPbs.add(pb); else cataPbs.add(JsonNull.INSTANCE); }
                    obj.add("cataPbs", cataPbs);
                    JsonArray masterPbs = new JsonArray();
                    for (String pb : d.masterPbs) { if (pb != null) masterPbs.add(pb); else masterPbs.add(JsonNull.INSTANCE); }
                    obj.add("masterPbs", masterPbs);
                    JsonArray cataTimes = new JsonArray();
                    for (long t : d.cataTimes)   cataTimes.add(t);
                    obj.add("cataTimes", cataTimes);
                    JsonArray masterTimes = new JsonArray();
                    for (long t : d.masterTimes) masterTimes.add(t);
                    obj.add("masterTimes", masterTimes);
                    obj.addProperty("ragnarockChimera", d.ragnarockChimera);
                    obj.addProperty("magicalPower",     d.magicalPower);
                    if (d.termUltimate != null) obj.addProperty("termUltimate", d.termUltimate);
                    else obj.add("termUltimate", JsonNull.INSTANCE);
                    if (d.armorStars != null) {
                        JsonArray a = new JsonArray(); for (int s : d.armorStars) a.add(s); obj.add("armorStars", a);
                    }
                    if (d.equipStars != null) {
                        JsonArray a = new JsonArray(); for (int s : d.equipStars) a.add(s); obj.add("equipStars", a);
                    }
                    entries.add(name, obj);
                }
                root.addProperty("version", 1);
                root.add("entries", entries);
                Files.writeString(file, root.toString());
            } catch (Exception ignored) {}
        });
    }

    public static class DungeonData {
        public long cataXp;
        public int  cataLevel;       // computed from cataXp
        public long totalSecrets;
        public long totalRuns;
        public String secretAverage; // "9.5", null if no runs
        public Map<String, Long> classXp = new HashMap<>();
        // Index 0-7: 0=Entrance/E, 1-7=F1-F7 for cata; 1-7=M1-M7 for master. null = no PB.
        public String[] cataPbs    = new String[8];
        public String[] masterPbs  = new String[8];
        // Per-floor run counts: index 0-7 (0=entrance for cata, 1-7=floors)
        public long[]   cataTimes   = new long[8];
        public long[]   masterTimes = new long[8];
        // Inventory-derived fields (null/–1 when API is off or item not found)
        public int      ragnarockChimera = -1;  // Chimera enchant level on RAGNAROCK_AXE, –1 = none
        public String   termUltimate     = null; // Ultimate enchant on Terminator(s), null = none/no term
        public int[]    armorStars       = null; // [H, C, L, B] dungeon stars; null = inventory API off
        public int[]    equipStars       = null; // [N, CL, B, G] dungeon stars; null = inventory API off
        public int      magicalPower     = -1;   // accessory_bag_storage.magical_power, –1 = unknown
    }

    // ─── inventory / NBT helpers ──────────────────────────────────────────────

    private static final Pattern STRIP_COLOR    = Pattern.compile("§.");
    private static final Pattern ULTIMATE_PAT   = Pattern.compile("Ultimate ([A-Za-z ]+?) ([IVX]+)$");

    private static void parseInventoryData(JsonObject member, DungeonData result) {
        try {
            if (!member.has("inventory")) return;
            JsonObject inv = member.getAsJsonObject("inventory");

            // Search main inv + echest for Ragnarock Axe / Terminator
            List<NbtCompound> mainItems  = parseSlots(inv, "inv_contents");
            List<NbtCompound> echestItems = parseSlots(inv, "ender_chest_contents");
            List<NbtCompound> allItems = new ArrayList<>(mainItems.size() + echestItems.size());
            for (NbtCompound c : mainItems)   if (c != null) allItems.add(c);
            for (NbtCompound c : echestItems) if (c != null) allItems.add(c);

            for (NbtCompound item : allItems) {
                String id = getItemId(item);
                if (result.ragnarockChimera < 0 && "RAGNAROCK_AXE".equals(id))
                    result.ragnarockChimera = getEnchantLevel(item, "chimera");
                if (result.termUltimate == null && id != null && id.contains("TERMINATOR"))
                    result.termUltimate = getUltimateEnchant(item);
            }

            // Armor stars: inv_armor slots [0=boots, 1=legs, 2=chest, 3=head]
            List<NbtCompound> armorSlots = parseSlots(inv, "inv_armor");
            if (!armorSlots.isEmpty()) {
                result.armorStars = new int[]{
                    getStarCount(armorSlots.size() > 3 ? armorSlots.get(3) : null), // H
                    getStarCount(armorSlots.size() > 2 ? armorSlots.get(2) : null), // C
                    getStarCount(armorSlots.size() > 1 ? armorSlots.get(1) : null), // L
                    getStarCount(armorSlots.size() > 0 ? armorSlots.get(0) : null)  // B
                };
            }

            // Equipment stars: equipment_contents [0=necklace, 1=cloak, 2=belt, 3=gloves]
            List<NbtCompound> equipSlots = parseSlots(inv, "equipment_contents");
            if (!equipSlots.isEmpty()) {
                result.equipStars = new int[]{
                    getStarCount(equipSlots.size() > 0 ? equipSlots.get(0) : null), // N
                    getStarCount(equipSlots.size() > 1 ? equipSlots.get(1) : null), // CL
                    getStarCount(equipSlots.size() > 2 ? equipSlots.get(2) : null), // B (belt)
                    getStarCount(equipSlots.size() > 3 ? equipSlots.get(3) : null)  // G
                };
            }
        } catch (Exception ignored) {}
    }

    private static List<NbtCompound> parseSlots(JsonObject inventory, String key) {
        try {
            if (!inventory.has(key)) return Collections.emptyList();
            JsonObject slot = inventory.getAsJsonObject(key);
            if (!slot.has("data")) return Collections.emptyList();
            String b64 = slot.get("data").getAsString();
            if (b64.isEmpty()) return Collections.emptyList();
            byte[] bytes = java.util.Base64.getDecoder().decode(b64);
            NbtCompound root = NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtSizeTracker.ofUnlimitedBytes());
            Optional<NbtList> listOpt = root.getList("i");
            NbtList items = listOpt.orElse(null);
            if (items == null) return Collections.emptyList();
            List<NbtCompound> out = new ArrayList<>(items.size());
            for (int i = 0; i < items.size(); i++) {
                NbtCompound c = items.getCompound(i).orElse(null);
                out.add(c != null && !c.isEmpty() ? c : null);
            }
            return out;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static NbtCompound getTag(NbtCompound item) {
        if (item == null) return null;
        try {
            NbtElement el = item.get("tag");
            if (el == null) return null;
            return el.asCompound().orElse(null);
        } catch (Exception e) { return null; }
    }

    private static NbtCompound getExtras(NbtCompound item) {
        NbtCompound tag = getTag(item);
        if (tag == null) return null;
        try {
            NbtElement el = tag.get("ExtraAttributes");
            if (el == null) return null;
            return el.asCompound().orElse(null);
        } catch (Exception e) { return null; }
    }

    private static String getItemId(NbtCompound item) {
        NbtCompound extras = getExtras(item);
        if (extras == null) return null;
        try {
            NbtElement el = extras.get("id");
            if (el == null) return null;
            return el.asString().orElse(null);
        } catch (Exception e) { return null; }
    }

    private static int getEnchantLevel(NbtCompound item, String enchantName) {
        NbtCompound extras = getExtras(item);
        if (extras == null) return -1;
        try {
            NbtElement encEl = extras.get("enchantments");
            if (encEl == null) return -1;
            NbtCompound enchants = encEl.asCompound().orElse(null);
            if (enchants == null) return -1;
            return enchants.getInt(enchantName, -1);
        } catch (Exception e) { return -1; }
    }

    private static String getUltimateEnchant(NbtCompound item) {
        NbtCompound extras = getExtras(item);
        if (extras == null) return null;
        try {
            // Check enchantments map for "ultimate_" prefixed keys
            NbtElement encEl = extras.get("enchantments");
            if (encEl != null) {
                NbtCompound enchants = encEl.asCompound().orElse(null);
                if (enchants != null) {
                    for (String k : enchants.getKeys()) {
                        if (!k.startsWith("ultimate_")) continue;
                        int lvl = enchants.getInt(k, 0);
                        String name = k.substring("ultimate_".length()).replace("_", " ");
                        name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
                        return name + " " + toRoman(lvl);
                    }
                }
            }
            // Fallback: scan lore for "Ultimate <Name> <Level>"
            NbtCompound tag = getTag(item);
            if (tag == null) return null;
            NbtElement displayEl = tag.get("display");
            if (displayEl == null) return null;
            NbtCompound display = displayEl.asCompound().orElse(null);
            if (display == null) return null;
            NbtList lore = display.getList("Lore").orElse(null);
            if (lore == null || lore.isEmpty()) return null;
            for (int i = 0; i < lore.size(); i++) {
                String line = STRIP_COLOR.matcher(lore.getString(i).orElse("")).replaceAll("").trim();
                Matcher m = ULTIMATE_PAT.matcher(line);
                if (m.find()) return m.group(1).trim() + " " + m.group(2);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static int getStarCount(NbtCompound item) {
        NbtCompound extras = getExtras(item);
        if (extras != null) {
            try {
                int lvl = extras.getInt("dungeon_item_level", -1);
                if (lvl >= 0) return lvl;
            } catch (Exception ignored) {}
        }
        // Fallback: count ✪ in display name
        try {
            NbtCompound tag = getTag(item);
            if (tag == null) return 0;
            NbtElement displayEl = tag.get("display");
            if (displayEl == null) return 0;
            NbtCompound display = displayEl.asCompound().orElse(null);
            if (display == null) return 0;
            NbtElement nameEl = display.get("Name");
            if (nameEl == null) return 0;
            String name = STRIP_COLOR.matcher(nameEl.asString().orElse("")).replaceAll("");
            return (int) name.chars().filter(c -> c == '\u272A').count();
        } catch (Exception e) { return 0; }
    }

    private static int computeMagicalPower(JsonObject member) {
        try {
            if (member.has("accessory_bag_storage")) {
                JsonObject abs = member.getAsJsonObject("accessory_bag_storage");
                if (abs.has("highest_magical_power"))
                    return (int) abs.get("highest_magical_power").getAsDouble();
                if (abs.has("magical_power"))
                    return (int) abs.get("magical_power").getAsDouble();
            }
        } catch (Exception ignored) {}

        // Compute from bag NBT: parse each accessory's rarity → sum MP values
        try {
            if (!member.has("accessory_bag_storage")) return -1;
            JsonObject abs = member.getAsJsonObject("accessory_bag_storage");
            List<NbtCompound> items = parseSlots(abs, "bag_storage");
            if (items.isEmpty()) return -1;

            java.util.Set<String> seen = new java.util.HashSet<>();
            int total = 0;
            for (NbtCompound item : items) {
                if (item == null) continue;
                // Deduplicate by item ID — only count each accessory once
                String id = getItemId(item);
                if (id != null && !seen.add(id)) continue;

                NbtCompound tag = getTag(item);
                if (tag == null) continue;
                NbtElement displayEl = tag.get("display");
                if (displayEl == null) continue;
                NbtCompound display = displayEl.asCompound().orElse(null);
                if (display == null) continue;
                NbtList lore = display.getList("Lore").orElse(null);
                if (lore == null || lore.isEmpty()) continue;

                // Last non-empty lore line = "§X§lRARITY TYPE"
                for (int i = lore.size() - 1; i >= 0; i--) {
                    String line = STRIP_COLOR.matcher(lore.getString(i).orElse("")).replaceAll("").trim();
                    if (!line.isEmpty()) { total += mpForRarity(line); break; }
                }
            }
            return total;
        } catch (Exception e) { return -1; }
    }

    private static int mpForRarity(String rarityLine) {
        if (rarityLine.startsWith("MYTHIC"))       return 22;
        if (rarityLine.startsWith("LEGENDARY"))    return 16;
        if (rarityLine.startsWith("EPIC"))         return 12;
        if (rarityLine.startsWith("RARE"))         return 8;
        if (rarityLine.startsWith("UNCOMMON"))     return 5;
        if (rarityLine.startsWith("VERY SPECIAL")) return 5;
        if (rarityLine.startsWith("COMMON"))       return 3;
        if (rarityLine.startsWith("SPECIAL"))      return 3;
        return 0;
    }

    private static String toRoman(int n) {
        if (n <= 0) return String.valueOf(n);
        String[] v = {"M","CM","D","CD","C","XC","L","XL","X","IX","V","IV","I"};
        int[]    r = {1000,900,500,400,100, 90, 50, 40, 10,  9,  5,  4,  1};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < r.length; i++) while (n >= r[i]) { sb.append(v[i]); n -= r[i]; }
        return sb.toString();
    }

    public interface DungeonDataCallback {
        void onData(DungeonData data);
    }

    // ─── entry points ─────────────────────────────────────────────────────────

    /**
     * Silent Party Finder lookup — requires API key.
     * Flow: Ashcon (UUID, fast/cached) → Hypixel profiles (stats).
     * Falls back to Mojang for UUID if Ashcon fails.
     * Always calls callback so pending is never stuck.
     */
    public static void getByNameSilent(String ign, DungeonDataCallback callback) {
        // Fast path: UUID already known (in-session or on-disk cache) — skip the name→UUID lookup.
        String cachedUuid = getCachedUuid(ign);
        if (cachedUuid != null) { fetchProfilesSilent(cachedUuid, callback); return; }
        // Mojang-authoritative resolve (see resolveUuid) so recycled/changed names hit the right account.
        resolveUuid(ign, 0, uuid -> {
            if (uuid == null) { callback.onData(new DungeonData()); return; }
            fetchProfilesSilent(uuid, callback);
        });
    }

    private static void fetchProfilesSilent(String uuidStr, DungeonDataCallback callback) {
        HttpRequest req;
        try {
            req = HttpRequest.newBuilder()
                .uri(URI.create(PROXY_URL + "/skyblock/profiles?uuid=" + uuidStr))
                .header("X-FishMod-Token", MOD_TOKEN)
                .header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        } catch (Exception e) { callback.onData(new DungeonData()); return; }
        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
            .thenAccept(resp -> {
                if (friendlyProxyError(resp) != null) { callback.onData(new DungeonData()); return; }
                try {
                    JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
                    if (!root.get("success").getAsBoolean()) { callback.onData(new DungeonData()); return; }
                    for (JsonElement profileEl : root.getAsJsonArray("profiles")) {
                        JsonObject profile = profileEl.getAsJsonObject();
                        if (!profile.has("selected") || !profile.get("selected").getAsBoolean()) continue;
                        JsonObject members = profile.getAsJsonObject("members");
                        if (!members.has(uuidStr)) continue;
                        JsonObject member = members.getAsJsonObject(uuidStr);
                        if (!member.has("dungeons")) continue;
                        callback.onData(parseDungeonData(uuidStr, member));
                        return;
                    }
                } catch (Exception ignored) {}
                callback.onData(new DungeonData());
            })
            .exceptionally(e -> { callback.onData(new DungeonData()); return null; });
    }

    /** Look up by IGN: Mojang UUID lookup → Hypixel profiles. */
    public static void getByName(MinecraftClient mc, String ign, DungeonDataCallback callback) {
        if (!checkKey(mc)) return;
        mc.send(() -> Misc.addChatMessage(Text.literal("§7Looking up " + ign + "...")));
        resolveUuid(ign, 0, uuid -> {
            if (uuid == null) {
                mc.send(() -> Misc.addChatMessage(Text.literal("§cPlayer not found: " + ign)));
                return;
            }
            fetchProfiles(mc, uuid, callback);
        });
    }

    /**
     * Resolves an IGN to a dash-less UUID, preferring Mojang's authoritative endpoint so name changes
     * and recycled names resolve to whoever CURRENTLY owns the name. Ashcon / playerdb mirror Mojang
     * but cache aggressively and can lag real name changes by days — so they're used only as a fallback
     * for when Mojang itself can't answer (rate-limit / outage), never to override a Mojang answer.
     *
     * attempt: 0 = Mojang (authoritative) → 1 = Ashcon → 2 = playerdb. A definitive not-found from
     * Mojang means "no account currently holds this name" — we stop there instead of asking the stale
     * mirrors, which would happily hand back a recycled/old owner (the bug this ordering fixes).
     */
    private static void resolveUuid(String ign, int attempt, java.util.function.Consumer<String> cb) {
        if (attempt == 0) {
            String cached = getCachedUuid(ign);
            if (cached != null) { cb.accept(cached); return; }
        }
        String url; final int next;
        switch (attempt) {
            case 0 -> { url = "https://api.mojang.com/users/profiles/minecraft/" + ign; next = 1; }
            case 1 -> { url = "https://api.ashcon.app/mojang/v2/user/" + ign;           next = 2; }
            case 2 -> { url = "https://playerdb.co/api/player/minecraft/" + ign;        next = 3; }
            default -> { cb.accept(null); return; }
        }
        HttpRequest req;
        try {
            req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "FishMod/1.0")
                    .GET()
                    .build();
        } catch (Exception e) { resolveUuid(ign, next, cb); return; }

        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenAccept(resp -> {
                    int code = resp.statusCode();
                    String uuid = (code >= 200 && code < 300) ? parseUuid(resp.body()) : null;
                    if (uuid != null) { putUuid(ign, uuid); cb.accept(uuid); return; }
                    // Only fall back to the mirrors when Mojang couldn't actually answer. A definitive
                    // not-found (404/400/empty-2xx) is authoritative — don't let a stale mirror resolve
                    // a recycled name to the wrong account.
                    boolean transientErr = code == 429 || code == 408 || code >= 500;
                    if (attempt == 0 && !transientErr) { cb.accept(null); return; }
                    resolveUuid(ign, next, cb);
                })
                .exceptionally(e -> { resolveUuid(ign, next, cb); return null; });
    }

    /** Public async name→UUID resolver (Mojang-authoritative, mirror fallback). UUID is dash-less. */
    public static void resolveUuidAsync(String ign, java.util.function.Consumer<String> cb) {
        resolveUuid(ign, 0, cb);
    }

    /** Extracts a dash-less UUID from a Mojang / Ashcon / playerdb name-lookup body, or null. */
    private static String parseUuid(String body) {
        try {
            JsonObject obj = JsonParser.parseString(body).getAsJsonObject();
            String uuid = null;
            if (obj.has("id"))        uuid = obj.get("id").getAsString();   // mojang
            else if (obj.has("uuid")) uuid = obj.get("uuid").getAsString(); // ashcon
            else if (obj.has("data")) {                                     // playerdb
                JsonObject player = obj.getAsJsonObject("data").getAsJsonObject("player");
                if (player.has("id")) uuid = player.get("id").getAsString();
            }
            return (uuid != null && !uuid.isEmpty()) ? uuid.replace("-", "") : null;
        } catch (Exception e) { return null; }
    }

    /** Look up by local player's own UUID (no Mojang step needed). */
    public static void getPlayerDungeonData(MinecraftClient mc, DungeonDataCallback callback) {
        if (!checkKey(mc)) return;
        if (mc.player == null) return;
        String uuid = mc.player.getUuid().toString().replace("-", "");
        mc.send(() -> Misc.addChatMessage(Text.literal("§7Fetching Hypixel data...")));
        fetchProfiles(mc, uuid, callback);
    }

    // ─── internal ─────────────────────────────────────────────────────────────

    private static DungeonData parseDungeonData(String uuidStr, JsonObject member) {
        DungeonData result = new DungeonData();
        if (!member.has("dungeons")) return result;
        JsonObject dungeons = member.getAsJsonObject("dungeons");

        if (dungeons.has("dungeon_types")) {
            JsonObject types = dungeons.getAsJsonObject("dungeon_types");
            if (types.has("catacombs")) {
                JsonObject cata = types.getAsJsonObject("catacombs");
                if (cata.has("experience"))
                    result.cataXp = cata.get("experience").getAsLong();
                for (int f = 0; f <= 7; f++)
                    result.cataPbs[f] = extractFloorPb(cata, f);
            }

            // Per-floor run counts + totalRuns
            // Hypixel API: tier_completions[floor] = completions; times_played[floor] = attempts (incl. fails).
            // master_catacombs typically only populates tier_completions, not times_played.
            long totalRuns = 0;
            if (types.has("catacombs")) {
                JsonObject dt = types.getAsJsonObject("catacombs");
                String countField = dt.has("tier_completions") ? "tier_completions" : "times_played";
                if (dt.has(countField)) {
                    for (Map.Entry<String, JsonElement> e : dt.getAsJsonObject(countField).entrySet()) {
                        int f = parseFloorKey(e.getKey());
                        if (f < 0) continue;
                        long v = e.getValue().getAsLong();
                        if (f <= 7) result.cataTimes[f] = v;
                        totalRuns += v;
                    }
                }
            }
            if (types.has("master_catacombs")) {
                JsonObject dt = types.getAsJsonObject("master_catacombs");
                String countField = dt.has("tier_completions") ? "tier_completions" : "times_played";
                if (dt.has(countField)) {
                    for (Map.Entry<String, JsonElement> e : dt.getAsJsonObject(countField).entrySet()) {
                        int f = parseFloorKey(e.getKey());
                        if (f < 0) continue;
                        long v = e.getValue().getAsLong();
                        if (f >= 1 && f <= 7) result.masterTimes[f] = v;
                        totalRuns += v;
                    }
                }
            }
            long totalSecrets = dungeons.has("secrets") ? dungeons.get("secrets").getAsLong() : 0;
            result.totalSecrets = totalSecrets;
            result.totalRuns    = totalRuns;
            if (totalRuns > 0) {
                result.secretAverage = String.format("%.1f", (double) totalSecrets / totalRuns);
            }
            result.cataLevel = calcCataLevel(result.cataXp);

            if (types.has("master_catacombs")) {
                JsonObject mc = types.getAsJsonObject("master_catacombs");
                for (int f = 1; f <= 7; f++)
                    result.masterPbs[f] = extractFloorPb(mc, f);
            }
        }

        if (dungeons.has("player_classes")) {
            JsonObject classes = dungeons.getAsJsonObject("player_classes");
            for (String cls : new String[]{"healer", "mage", "berserk", "archer", "tank"}) {
                if (classes.has(cls)) {
                    JsonObject c = classes.getAsJsonObject(cls);
                    if (c.has("experience"))
                        result.classXp.put(cls, c.get("experience").getAsLong());
                }
            }
        }

        parseInventoryData(member, result);

        result.magicalPower = computeMagicalPower(member);

        return result;
    }

    public static int calcCataLevel(long xp) {
        for (int i = CATA_XP_TABLE.length - 1; i >= 0; i--) {
            if (xp >= CATA_XP_TABLE[i]) {
                if (i == CATA_XP_TABLE.length - 1)
                    return (int)(i + (xp - CATA_XP_TABLE[i]) / 200_000_000L);
                return i;
            }
        }
        return 0;
    }

    private static String extractFloorPb(JsonObject dungeonType, int floor) {
        String floorKey = String.valueOf(floor); // Hypixel API uses "7" not "floor_7"
        for (String[] pair : new String[][]{{"fastest_time_s_plus", "S+"}, {"fastest_time_s", "S"}}) {
            if (dungeonType.has(pair[0])) {
                JsonObject times = dungeonType.getAsJsonObject(pair[0]);
                if (times.has(floorKey)) {
                    long ms = times.get(floorKey).getAsLong();
                    long s = ms / 1000;
                    return String.format("%d:%02d %s", s / 60, s % 60, pair[1]);
                }
            }
        }
        return null;
    }

    /** Parses a Hypixel floor key like "7" or "floor_7" → floor number, or -1 if unrecognised. */
    public static int parseFloorKey(String key) {
        try {
            if (key.matches("\\d+"))        return Integer.parseInt(key);
            if (key.startsWith("floor_"))   return Integer.parseInt(key.substring(6));
        } catch (NumberFormatException ignored) {}
        return -1;
    }

    private static boolean checkKey(MinecraftClient mc) {
        return true; // API key no longer needed — requests go through proxy
    }

    private static void fetchProfiles(MinecraftClient mc, String uuidStr, DungeonDataCallback callback) {
        HttpRequest req;
        try {
            req = HttpRequest.newBuilder()
                .uri(URI.create(PROXY_URL + "/skyblock/profiles?uuid=" + uuidStr))
                .header("X-FishMod-Token", MOD_TOKEN)
                .header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        } catch (Exception e) { return; }

        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
            .thenAccept(resp -> {
                String friendly = friendlyProxyError(resp);
                if (friendly != null) {
                    mc.send(() -> Misc.addChatMessage(Text.literal(friendly)));
                    return;
                }
                try {
                    JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
                    if (!root.get("success").getAsBoolean()) {
                        mc.send(() -> Misc.addChatMessage(Text.literal("§cAPI error — proxy rejected request.")));
                        return;
                    }
                    for (JsonElement profileEl : root.getAsJsonArray("profiles")) {
                        JsonObject profile = profileEl.getAsJsonObject();
                        if (!profile.has("selected") || !profile.get("selected").getAsBoolean()) continue;
                        JsonObject members = profile.getAsJsonObject("members");
                        if (!members.has(uuidStr)) continue;
                        JsonObject member = members.getAsJsonObject(uuidStr);
                        if (!member.has("dungeons")) continue;
                        callback.onData(parseDungeonData(uuidStr, member));
                        return;
                    }
                    mc.send(() -> Misc.addChatMessage(Text.literal("§cNo active Skyblock profile found.")));
                } catch (Exception e) {
                    mc.send(() -> Misc.addChatMessage(Text.literal("§cAPI parse error: " + e.getMessage())));
                }
            })
            .exceptionally(e -> { mc.send(() -> Misc.addChatMessage(Text.literal("§cAPI request failed."))); return null; });
    }

    /**
     * Returns a user-friendly chat message if the proxy response is not parseable JSON
     * (rate limits, Cloudflare error pages, etc.), or null if the body looks like JSON.
     */
    private static String friendlyProxyError(HttpResponse<String> resp) {
        int code = resp.statusCode();
        String body = resp.body();
        String trimmed = body == null ? "" : body.trim();
        boolean looksJson = trimmed.startsWith("{") || trimmed.startsWith("[");
        if (code >= 200 && code < 300 && looksJson) return null;
        if (code == 429 || trimmed.contains("error code: 1015") || trimmed.contains("error code: 1027"))
            return "§cHypixel proxy is rate-limited (429) — try again in a bit.";
        if (code == 502 || code == 503 || code == 504)
            return "§cHypixel proxy unreachable (" + code + ") — try again shortly.";
        if (code >= 400) return "§cHypixel proxy error (HTTP " + code + ").";
        if (!looksJson) return "§cHypixel proxy returned a non-JSON response (HTTP " + code + ").";
        return null;
    }

    /** Dumps all top-level keys of the member object to chat — used to find which fields the proxy returns. */
    public static void dumpMemberKeys(MinecraftClient mc, String ign) {
        mc.send(() -> Misc.addChatMessage(Text.literal("§7Looking up " + ign + " (raw)...")));
        HttpRequest uuidReq;
        try {
            uuidReq = HttpRequest.newBuilder()
                .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + ign))
                .timeout(Duration.ofSeconds(10)).GET().build();
        } catch (Exception e) { return; }
        HTTP.sendAsync(uuidReq, HttpResponse.BodyHandlers.ofString()).thenAccept(ur -> {
            try {
                String uuid = JsonParser.parseString(ur.body()).getAsJsonObject().get("id").getAsString();
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(PROXY_URL + "/skyblock/profiles?uuid=" + uuid))
                    .header("X-FishMod-Token", MOD_TOKEN).header("User-Agent", "Mozilla/5.0")
                    .timeout(Duration.ofSeconds(10)).GET().build();
                HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept(r -> {
                    try {
                        JsonObject root = JsonParser.parseString(r.body()).getAsJsonObject();
                        for (JsonElement profileEl : root.getAsJsonArray("profiles")) {
                            JsonObject profile = profileEl.getAsJsonObject();
                            if (!profile.has("selected") || !profile.get("selected").getAsBoolean()) continue;
                            JsonObject member = profile.getAsJsonObject("members").getAsJsonObject(uuid);
                            mc.send(() -> {
                                Misc.addChatMessage(Text.literal("§b--- accessory_bag_storage keys ---"));
                                if (member.has("accessory_bag_storage")) {
                                    JsonObject abs = member.getAsJsonObject("accessory_bag_storage");
                                    for (String key : abs.keySet())
                                        Misc.addChatMessage(Text.literal("§7" + key + " = " + abs.get(key).toString().substring(0, Math.min(60, abs.get(key).toString().length()))));
                                } else {
                                    Misc.addChatMessage(Text.literal("§cmissing"));
                                }
                                Misc.addChatMessage(Text.literal("§b--- End ---"));
                            });
                            return;
                        }
                    } catch (Exception e) {
                        mc.send(() -> Misc.addChatMessage(Text.literal("§cParse error: " + e.getMessage())));
                    }
                });
            } catch (Exception e) {
                mc.send(() -> Misc.addChatMessage(Text.literal("§cUUID error: " + e.getMessage())));
            }
        });
    }

    public interface NetworthCallback { void onData(double networth, String profileName); }

    // SkyHelper public price list (item/pet/modifier prices). Cached.
    private static final Map<String, Double> NW_PRICES = new ConcurrentHashMap<>();
    private static volatile long nwPricesAt = 0;

    private static final String[] NW_STORAGES = {
        "inv_contents","inv_armor","ender_chest_contents","equipment_contents",
        "personal_vault_contents","talisman_bag","wardrobe_contents","fishing_bag","potion_bag","quiver","candy_inventory_contents"
    };

    /**
     * Computes networth from the Hypixel profile (fetched via the proxy, which holds the API key)
     * using the SkyHelper price list. Values liquid + items (base, recomb, enchants, hot-potato,
     * master stars) + pets. An estimate — close to in-game, no extra hosting / SkyCrypt needed.
     */
    public static void getNetworth(MinecraftClient mc, String ign, NetworthCallback cb) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            String uuid = resolveUuidBlocking(ign);
            if (uuid == null) { cb.onData(-1, null); return; }
            getNetworthLocal(uuid, cb);
        });
    }

    /** Client-side networth estimate using the SkyHelper price list. */
    private static void getNetworthLocal(String uuid, NetworthCallback cb) {
            try {
                fishmod.utils.networth.ItemsDb.ensureLoaded();
                Map<String, Double> prices = nwPrices();

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(PROXY_URL + "/skyblock/profiles?uuid=" + uuid))
                        .header("X-FishMod-Token", MOD_TOKEN).header("User-Agent", "Mozilla/5.0")
                        .timeout(Duration.ofSeconds(12)).GET().build();
                HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                JsonObject root = JsonParser.parseString(r.body()).getAsJsonObject();
                if (!root.has("success") || !root.get("success").getAsBoolean() || !root.has("profiles")) {
                    cb.onData(-1, null); return;
                }
                JsonObject chosen = null;
                for (JsonElement pe : root.getAsJsonArray("profiles")) {
                    JsonObject p = pe.getAsJsonObject();
                    if (p.has("selected") && p.get("selected").getAsBoolean()) { chosen = p; break; }
                    if (chosen == null) chosen = p;
                }
                if (chosen == null) { cb.onData(-1, null); return; }
                String pname = chosen.has("cute_name") ? chosen.get("cute_name").getAsString() : null;
                JsonObject member = chosen.getAsJsonObject("members").getAsJsonObject(uuid);

                double total = 0;
                if (chosen.has("banking") && chosen.getAsJsonObject("banking").has("balance"))
                    total += chosen.getAsJsonObject("banking").get("balance").getAsDouble();
                if (member.has("currencies") && member.getAsJsonObject("currencies").has("coin_purse"))
                    total += member.getAsJsonObject("currencies").get("coin_purse").getAsDouble();
                else if (member.has("coin_purse")) total += member.get("coin_purse").getAsDouble();

                if (member.has("inventory")) {
                    JsonObject inv = member.getAsJsonObject("inventory");
                    for (String k : NW_STORAGES) total += sumStorageNw(inv, k, prices);
                    if (inv.has("backpack_contents") && inv.get("backpack_contents").isJsonObject()) {
                        JsonObject bp = inv.getAsJsonObject("backpack_contents");
                        for (String k : bp.keySet()) total += sumStorageNw(bp, k, prices);
                    }
                    // Accessory bag, fishing bag, potion bag, quiver, sacks bag live nested under
                    // bag_contents — NOT at the inventory top level. The accessory (talisman) bag
                    // is often several billion, so missing it badly undercounted networth.
                    if (inv.has("bag_contents") && inv.get("bag_contents").isJsonObject()) {
                        JsonObject bags = inv.getAsJsonObject("bag_contents");
                        for (String k : bags.keySet()) total += sumStorageNw(bags, k, prices);
                    }
                }
                total += petsValueNw(member, prices);
                total += sacksValueNw(member, prices);
                total += essenceValueNw(member, prices);
                total += shardsValueNw(member, prices);

                cb.onData(total, pname);
            } catch (Exception ex) {
                fishmod.utils.debug.Debug.LOGGER.warn("[Networth] error: {}", ex.toString());
                cb.onData(-1, null);
            }
    }

    private static synchronized Map<String, Double> nwPrices() {
        if (System.currentTimeMillis() - nwPricesAt < 10 * 60 * 1000L && !NW_PRICES.isEmpty()) return NW_PRICES;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://raw.githubusercontent.com/SkyHelperBot/Prices/main/pricesV2.json"))
                    .timeout(Duration.ofSeconds(20)).GET().build();
            HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() == 200) {
                JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
                NW_PRICES.clear();
                for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                    try { NW_PRICES.put(e.getKey(), e.getValue().getAsDouble()); } catch (Exception ignored) {}
                }
                nwPricesAt = System.currentTimeMillis();
            }
        } catch (Exception e) { fishmod.utils.debug.Debug.LOGGER.warn("[Networth] prices fetch: {}", e.getMessage()); }
        return NW_PRICES;
    }

    private static double price(Map<String, Double> p, String key) {
        Double v = p.get(key);
        return v != null ? v : 0;
    }

    private static double sumStorageNw(JsonObject inventory, String key, Map<String, Double> prices) {
        double total = 0;
        for (NbtCompound item : parseSlots(inventory, key)) {
            if (item != null) total += itemValueNw(item, prices);
        }
        return total;
    }

    /**
     * Per-item modifier valuation, ported from SkyHelper-Networth's handler pipeline (non-cosmetic).
     * Each modifier is wrapped in try/catch so one bad field never zeroes the whole item.
     */
    private static double itemValueNw(NbtCompound item, Map<String, Double> prices) {
        NbtCompound ex = getExtras(item);
        if (ex == null) return 0;
        String id = getItemId(item);
        if (id == null) return 0;

        int count = 1;
        try { count = item.getInt("Count", 1); if (count <= 0) count = 1; } catch (Exception ignored) {}
        // Some lists store Count as byte; fall back gracefully.

        // Item metadata (category / gemstone_slots / upgrade_costs / prestige) for handlers that need it.
        com.google.gson.JsonObject meta = fishmod.utils.networth.ItemsDb.get(id);
        String category = "";
        try { if (meta != null && meta.has("category")) category = meta.get("category").getAsString(); } catch (Exception ignored) {}

        // ---- Base price (ported getItemId): skin / shiny / starred / cake / rune variants ----
        String priceId = id;
        try {
            String skin = ex.getString("skin", "");
            if (!skin.isEmpty()) {
                String skinned = id + "_SKINNED_" + skin;
                if (price(prices, skinned) > price(prices, id)) priceId = skinned;
            }
            // Rune item -> RUNE_<type>_<tier>
            if ("RUNE".equals(id) || "UNIQUE_RUNE".equals(id)) {
                NbtCompound runes = compound(ex, "runes");
                if (runes != null) for (String rn : runes.getKeys()) {
                    priceId = ("RUNE_" + rn + "_" + runes.getInt(rn, 0)).toUpperCase();
                    break;
                }
            }
            if ("NEW_YEAR_CAKE".equals(id)) {
                int cake = ex.getInt("new_years_cake", 0);
                priceId = "NEW_YEAR_CAKE_" + cake;
            }
            // Shiny variant
            if (ex.getInt("is_shiny", 0) > 0 && price(prices, id + "_SHINY") > 0) priceId = id + "_SHINY";
            // Fragged: STARRED_ fallback to base
            if (id.startsWith("STARRED_") && price(prices, id) == 0 && price(prices, id.replace("STARRED_", "")) > 0)
                priceId = id.replace("STARRED_", "");
        } catch (Exception ignored) {}

        double base = price(prices, priceId) * count;
        double v = base;

        // ---- Crown of Avarice: collected coins interpolate base between 0 and 1B price ----
        try {
            if ("CROWN_OF_AVARICE".equals(id)) {
                long cc = 0;
                try { cc = ex.getLong("collected_coins", 0L); }
                catch (Exception e) { cc = (long) ex.getDouble("collected_coins", 0); }
                if (cc > 0) {
                    double zero = price(prices, "CROWN_OF_AVARICE");
                    double bil  = price(prices, "CROWN_OF_AVARICE_1B");
                    double coins = Math.min(cc, 1_000_000_000.0);
                    double newBase = zero + (bil - zero) * (coins / 1_000_000_000.0);
                    v += (newBase - base); // SkyHelper replaces the base price with the interpolated value
                }
            }
        } catch (Exception ignored) {}

        // ---- Recombobulator x0.8 ----
        try {
            boolean isRecomb = ex.getInt("rarity_upgrades", 0) > 0 && ex.getInt("item_tier", -1) < 0
                    && !ex.contains("item_tier");
            if (isRecomb) {
                boolean hasEnch = compound(ex, "enchantments") != null;
                boolean allows = fishmod.utils.networth.NwConstants.ALLOWED_RECOMBOBULATED_CATEGORIES.contains(category)
                        || fishmod.utils.networth.NwConstants.ALLOWED_RECOMBOBULATED_IDS.contains(id);
                if (hasEnch || allows) {
                    double w = "BONE_BOOMERANG".equals(id)
                            ? fishmod.utils.networth.NwConstants.RECOMBOBULATOR * 0.5
                            : fishmod.utils.networth.NwConstants.RECOMBOBULATOR;
                    v += price(prices, "RECOMBOBULATOR_3000") * w;
                }
            }
        } catch (Exception ignored) {}

        // ---- Potato books ----
        try {
            int hpc = ex.getInt("hot_potato_count", 0);
            if (hpc > 0) {
                v += price(prices, "HOT_POTATO_BOOK") * Math.min(hpc, 10) * fishmod.utils.networth.NwConstants.HOT_POTATO_BOOK;
                if (hpc > 10) v += price(prices, "FUMING_POTATO_BOOK") * (hpc - 10) * fishmod.utils.networth.NwConstants.FUMING_POTATO_BOOK;
            }
        } catch (Exception ignored) {}

        // ---- Enchantments (EnchantedBook items valued differently) ----
        try {
            NbtCompound enc = compound(ex, "enchantments");
            if (enc != null && !enc.getKeys().isEmpty()) {
                if ("ENCHANTED_BOOK".equals(id)) {
                    boolean single = enc.getKeys().size() == 1;
                    double bookPrice = 0;
                    for (String name : enc.getKeys()) {
                        int lvl = enc.getInt(name, 0);
                        double p = price(prices, "ENCHANTMENT_" + name.toUpperCase() + "_" + lvl);
                        if (p == 0) continue;
                        bookPrice += p * (single ? 1 : fishmod.utils.networth.NwConstants.ENCHANTMENTS);
                    }
                    if (bookPrice > 0) v += bookPrice; // replaces basePrice (which is ~0 for the book item)
                } else {
                    v += enchantmentsValueNw(id, enc, prices);
                }
            }
        } catch (Exception ignored) {}

        // ---- Gemstones (gems themselves + unlock slot costs for Divan/Crimson armor) ----
        try { v += gemsValueNw(id, ex, meta, prices); } catch (Exception ignored) {}

        // ---- Master Stars (stars 6-10) ----
        try {
            int up = upgradeLevel(ex);
            if (meta != null && meta.has("upgrade_costs") && up > 5) {
                int starsUsed = Math.min(up - 5, 5);
                if (meta.getAsJsonArray("upgrade_costs").size() <= 5) {
                    for (int s = 0; s < starsUsed; s++)
                        v += price(prices, fishmod.utils.networth.NwConstants.MASTER_STARS[s]) * fishmod.utils.networth.NwConstants.MASTER_STAR;
                }
            }
        } catch (Exception ignored) {}

        // ---- Essence Stars ----
        try {
            int up = upgradeLevel(ex);
            if (meta != null && meta.has("upgrade_costs") && up > 0) {
                v += starCostsNw(meta.getAsJsonArray("upgrade_costs"), up, prices, false);
            }
        } catch (Exception ignored) {}

        // ---- Prestige ----
        try {
            String[] chain = fishmod.utils.networth.NwConstants.PRESTIGES.get(id);
            if (chain != null && price(prices, id) == 0) {
                for (String pItem : chain) {
                    com.google.gson.JsonObject pMeta = fishmod.utils.networth.ItemsDb.get(pItem);
                    if (pMeta != null && pMeta.has("upgrade_costs"))
                        v += starCostsNw(pMeta.getAsJsonArray("upgrade_costs"), pMeta.getAsJsonArray("upgrade_costs").size(), prices, true);
                    if (pMeta != null && pMeta.has("prestige") && pMeta.getAsJsonObject("prestige").has("costs"))
                        v += starCostsNw(pMeta.getAsJsonObject("prestige").getAsJsonArray("costs"),
                                pMeta.getAsJsonObject("prestige").getAsJsonArray("costs").size(), prices, true);
                    if (price(prices, pItem) > 0) { v += price(prices, pItem); break; }
                }
            }
        } catch (Exception ignored) {}

        // ---- Reforge x1 (not for accessories) ----
        try {
            String modifier = ex.getString("modifier", "");
            if (!modifier.isEmpty() && !"ACCESSORY".equals(category)) {
                String stone = fishmod.utils.networth.NwConstants.REFORGES.get(modifier);
                if (stone != null) v += price(prices, stone) * fishmod.utils.networth.NwConstants.REFORGE;
            }
        } catch (Exception ignored) {}

        // ---- Art of War x0.6 ----
        try { int c = ex.getInt("art_of_war_count", 0); if (c > 0) v += price(prices, "THE_ART_OF_WAR") * c * fishmod.utils.networth.NwConstants.ART_OF_WAR; } catch (Exception ignored) {}
        // ---- Art of Peace x0.8 ----
        try { int c = ex.getInt("artOfPeaceApplied", 0); if (c > 0) v += price(prices, "THE_ART_OF_PEACE") * c * fishmod.utils.networth.NwConstants.ART_OF_PEACE; } catch (Exception ignored) {}
        // ---- Necron-blade ability scrolls x1 ----
        try {
            NbtList scrolls = ex.getList("ability_scroll").orElse(null);
            if (scrolls != null) for (int i = 0; i < scrolls.size(); i++)
                v += price(prices, scrolls.getString(i).orElse("").toUpperCase()) * fishmod.utils.networth.NwConstants.NECRON_BLADE_SCROLL;
        } catch (Exception ignored) {}
        // ---- Gemstone power scroll x0.5 ----
        try { String ps = ex.getString("power_ability_scroll", ""); if (!ps.isEmpty()) v += price(prices, ps) * fishmod.utils.networth.NwConstants.GEMSTONE_POWER_SCROLL; } catch (Exception ignored) {}
        // ---- Drill parts x1 ----
        try {
            for (String part : new String[]{"drill_part_upgrade_module","drill_part_fuel_tank","drill_part_engine"}) {
                String pid = ex.getString(part, "");
                if (!pid.isEmpty()) v += price(prices, pid.toUpperCase()) * fishmod.utils.networth.NwConstants.DRILL_PART;
            }
        } catch (Exception ignored) {}
        // ---- Rod parts x1 (line/hook/sinker -> compound with `part`) ----
        try {
            for (String part : new String[]{"line","hook","sinker"}) {
                NbtCompound pc = compound(ex, part);
                if (pc != null) {
                    String pp = pc.getString("part", "");
                    if (!pp.isEmpty()) v += price(prices, pp.toUpperCase()) * fishmod.utils.networth.NwConstants.ROD_PART;
                }
            }
        } catch (Exception ignored) {}
        // ---- Etherwarp conduit x1 ----
        try { if (ex.getInt("ethermerge", 0) > 0) v += price(prices, "ETHERWARP_CONDUIT") * fishmod.utils.networth.NwConstants.ETHERWARP; } catch (Exception ignored) {}
        // ---- Transmission tuner x0.7 ----
        try { int tt = ex.getInt("tuned_transmission", 0); if (tt > 0) v += price(prices, "TRANSMISSION_TUNER") * tt * fishmod.utils.networth.NwConstants.TUNED_TRANSMISSION; } catch (Exception ignored) {}
        // ---- Wood singularity x0.5 ----
        try { int c = ex.getInt("wood_singularity_count", 0); if (c > 0) v += price(prices, "WOOD_SINGULARITY") * c * fishmod.utils.networth.NwConstants.WOOD_SINGULARITY; } catch (Exception ignored) {}
        // ---- Jalapeno book x0.8 ----
        try { int c = ex.getInt("jalapeno_count", 0); if (c > 0) v += price(prices, "JALAPENO_BOOK") * c * fishmod.utils.networth.NwConstants.JALAPENO_BOOK; } catch (Exception ignored) {}
        // ---- Mana disintegrator x0.8 ----
        try { int c = ex.getInt("mana_disintegrator_count", 0); if (c > 0) v += price(prices, "MANA_DISINTEGRATOR") * c * fishmod.utils.networth.NwConstants.MANA_DISINTEGRATOR; } catch (Exception ignored) {}
        // ---- Farming for dummies x0.5 ----
        try { int c = ex.getInt("farming_for_dummies_count", 0); if (c > 0) v += price(prices, "FARMING_FOR_DUMMIES") * c * fishmod.utils.networth.NwConstants.FARMING_FOR_DUMMIES; } catch (Exception ignored) {}
        // ---- Overclocker 3000 x0.9 ----
        try { int c = ex.getInt("levelable_overclocks", 0); if (c > 0) v += price(prices, "OVERCLOCKER_3000") * c * fishmod.utils.networth.NwConstants.OVERCLOCKER_3000; } catch (Exception ignored) {}
        // ---- Polarvoid book x1 ----
        try { int c = ex.getInt("polarvoid", 0); if (c > 0) v += price(prices, "POLARVOID_BOOK") * c * fishmod.utils.networth.NwConstants.POLARVOID_BOOK; } catch (Exception ignored) {}
        // ---- Pocket sack-in-a-sack x0.7 ----
        try { int c = ex.getInt("sack_pss", 0); if (c > 0) v += price(prices, "POCKET_SACK_IN_A_SACK") * c * fishmod.utils.networth.NwConstants.POCKET_SACK_IN_A_SACK; } catch (Exception ignored) {}
        // ---- Divan powder coating x0.8 ----
        try { int c = ex.getInt("divan_powder_coating", 0); if (c > 0) v += price(prices, "DIVAN_POWDER_COATING") * fishmod.utils.networth.NwConstants.DIVAN_POWDER_COATING; } catch (Exception ignored) {}
        // ---- Dye x0.9 ----
        try { String dye = ex.getString("dye_item", ""); if (!dye.isEmpty()) v += price(prices, dye.toUpperCase()) * fishmod.utils.networth.NwConstants.DYE; } catch (Exception ignored) {}
        // ---- Runes x0.6 (only on non-rune items) ----
        try {
            NbtCompound runes = compound(ex, "runes");
            if (runes != null && !id.startsWith("RUNE")) for (String rn : runes.getKeys()) {
                String runeId = "RUNE_" + rn + "_" + runes.getInt(rn, 0);
                v += price(prices, runeId.toUpperCase()) * fishmod.utils.networth.NwConstants.RUNES;
                break;
            }
        } catch (Exception ignored) {}
        // ---- Enrichment x0.5 (cheapest enrichment) ----
        try {
            String enr = ex.getString("talisman_enrichment", "");
            if (!enr.isEmpty()) {
                double cheapest = Double.POSITIVE_INFINITY;
                for (String e : fishmod.utils.networth.NwConstants.ENRICHMENTS) {
                    double p = price(prices, e);
                    if (p > 0) cheapest = Math.min(cheapest, p);
                }
                if (cheapest != Double.POSITIVE_INFINITY) v += cheapest * fishmod.utils.networth.NwConstants.ENRICHMENT;
            }
        } catch (Exception ignored) {}
        // ---- Boosters x0.8 ----
        try {
            NbtList boosters = ex.getList("boosters").orElse(null);
            if (boosters != null) for (int i = 0; i < boosters.size(); i++) {
                String b = boosters.getString(i).orElse("");
                if (!b.isEmpty()) v += price(prices, b.toUpperCase() + "_BOOSTER") * fishmod.utils.networth.NwConstants.BOOSTER;
            }
        } catch (Exception ignored) {}
        // ---- New Year Cake Bag (sum of contained cakes, x1) ----
        try {
            NbtList years = ex.getList("new_year_cake_bag_years").orElse(null);
            if (years != null) for (int i = 0; i < years.size(); i++)
                v += price(prices, "NEW_YEAR_CAKE_" + years.getInt(i).orElse(0));
        } catch (Exception ignored) {}
        // ---- Shen's Auction (price paid x0.85, replaces base if higher) ----
        try {
            if (ex.contains("price") && ex.contains("auction") && ex.contains("bid")) {
                double pricePaid = ex.getDouble("price", 0) * fishmod.utils.networth.NwConstants.SHENS_AUCTION_PRICE;
                if (pricePaid > base) v += (pricePaid - base);
            }
        } catch (Exception ignored) {}
        // ---- Midas weapon (max-bid variant replaces base) ----
        try {
            Object[] midas = fishmod.utils.networth.NwConstants.MIDAS_SWORDS.get(id);
            if (midas != null) {
                long maxBid = (Long) midas[0];
                String type = (String) midas[1];
                double winning = ex.getDouble("winning_bid", 0);
                double additional = ex.getDouble("additional_coins", 0);
                if (winning + additional >= maxBid && price(prices, type) > 0)
                    v += (price(prices, type) - base);
            }
        } catch (Exception ignored) {}
        // ---- Pickonimbus (durability reduces base) ----
        try {
            if ("PICKONIMBUS".equals(id)) {
                int dur = ex.getInt("pickonimbus_durability", 5000);
                if (dur < 5000) v += base * ((dur / 5000.0) - 1);
            }
        } catch (Exception ignored) {}

        // ---- BONUS (not in SkyHelper): item attributes -> ATTRIBUTE_SHARD_<NAME> x 2^(level-1) ----
        try {
            NbtCompound att = compound(ex, "attributes");
            if (att != null) for (String an : att.getKeys()) {
                int lvl = att.getInt(an, 0);
                if (lvl > 0) {
                    double sp = price(prices, "ATTRIBUTE_SHARD_" + an.toUpperCase());
                    if (sp > 0) v += sp * Math.pow(2, lvl - 1);
                }
            }
        } catch (Exception ignored) {}

        return v;
    }

    private static NbtCompound compound(NbtCompound parent, String key) {
        try {
            NbtElement el = parent.get(key);
            if (el == null) return null;
            return el.asCompound().orElse(null);
        } catch (Exception e) { return null; }
    }

    /** dungeon_item_level / upgrade_level, stripped of non-digits, max of the two. */
    private static int upgradeLevel(NbtCompound ex) {
        int dil = digits(strOrInt(ex, "dungeon_item_level"));
        int ul = digits(strOrInt(ex, "upgrade_level"));
        return Math.max(dil, ul);
    }
    private static String strOrInt(NbtCompound ex, String key) {
        try {
            NbtElement el = ex.get(key);
            if (el == null) return "0";
            String s = el.asString().orElse(null);
            if (s != null) return s;
            return String.valueOf(ex.getInt(key, 0));
        } catch (Exception e) { return "0"; }
    }
    private static int digits(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) if (Character.isDigit(c)) b.append(c);
        try { return b.length() == 0 ? 0 : Integer.parseInt(b.toString()); } catch (Exception e) { return 0; }
    }

    /** Ported helper/essenceStars.js starCosts. Sums slice(0, level) of an upgrade_costs array. */
    private static double starCostsNw(JsonArray upgrades, int level, Map<String, Double> prices, boolean prestigeItem) {
        double price = 0;
        int limit = Math.min(level, upgrades.size());
        for (int i = 0; i < limit; i++) {
            JsonElement up = upgrades.get(i);
            if (up.isJsonArray()) {
                for (JsonElement cost : up.getAsJsonArray()) price += starCostOne(cost.getAsJsonObject(), prices);
            } else if (up.isJsonObject()) {
                price += starCostOne(up.getAsJsonObject(), prices);
            }
        }
        return price;
    }
    private static double starCostOne(JsonObject up, Map<String, Double> prices) {
        try {
            double amount = up.has("amount") ? up.get("amount").getAsDouble() : 0;
            if (up.has("essence_type")) {
                String et = up.get("essence_type").getAsString();
                return amount * price(prices, "ESSENCE_" + et) * fishmod.utils.networth.NwConstants.ESSENCE;
            } else if (up.has("item_id")) {
                String iid = up.get("item_id").getAsString();
                return amount * price(prices, iid);
            }
        } catch (Exception ignored) {}
        return 0;
    }

    /** Ported ItemEnchantments.js: per-enchant value with overrides, silex, upgrades. */
    private static double enchantmentsValueNw(String id, NbtCompound enc, Map<String, Double> prices) {
        double v = 0;
        java.util.Set<String> blocked = fishmod.utils.networth.NwConstants.BLOCKED_ENCHANTMENTS.get(id);
        for (String rawName : enc.getKeys()) {
            try {
                String name = rawName.toUpperCase();
                int value = enc.getInt(rawName, 0);
                if (blocked != null && blocked.contains(name)) continue;
                Integer ign = fishmod.utils.networth.NwConstants.IGNORED_ENCHANTMENTS.get(name);
                if (ign != null && ign == value) continue;
                if (fishmod.utils.networth.NwConstants.STACKING_ENCHANTMENTS.contains(name)) value = 1;

                // Silex
                if ("EFFICIENCY".equals(name) && value >= 6 && !fishmod.utils.networth.NwConstants.IGNORE_SILEX.contains(id)) {
                    int eff = value - ("STONK_PICKAXE".equals(id) ? 6 : 5);
                    if (eff > 0) v += price(prices, "SIL_EX") * eff * fishmod.utils.networth.NwConstants.SILEX;
                }
                // Enchantment upgrades
                Integer tierReq = fishmod.utils.networth.NwConstants.ENCHANTMENT_UPGRADE_TIER.containsKey(name)
                        ? fishmod.utils.networth.NwConstants.ENCHANTMENT_UPGRADE_TIER.get(name)[0] : null;
                if (tierReq != null && value >= tierReq) {
                    String up = fishmod.utils.networth.NwConstants.ENCHANTMENT_UPGRADE_ITEM.get(name);
                    v += price(prices, up) * fishmod.utils.networth.NwConstants.ENCHANTMENT_UPGRADES;
                }
                // Base enchantment value
                double mult = fishmod.utils.networth.NwConstants.ENCHANTMENTS_WORTH.containsKey(name)
                        ? fishmod.utils.networth.NwConstants.ENCHANTMENTS_WORTH.get(name)
                        : fishmod.utils.networth.NwConstants.ENCHANTMENTS;
                v += price(prices, "ENCHANTMENT_" + name + "_" + value) * mult;
            } catch (Exception ignored) {}
        }
        return v;
    }

    private static final java.util.Set<String> GEM_TYPES = java.util.Set.of(
        "RUBY","AMBER","SAPPHIRE","JADE","AMETHYST","TOPAZ","JASPER","OPAL","AQUAMARINE","CITRINE","ONYX","PERIDOT");

    /**
     * Values applied gemstones in an item's `gems` compound (x1) PLUS gemstone-slot UNLOCK costs
     * for Divan armor (x0.9 gemstoneChambers) and Crimson-family armor (x0.6 gemstoneSlots),
     * replicating Gemstones.js.
     */
    private static double gemsValueNw(String id, NbtCompound ex, com.google.gson.JsonObject meta, Map<String, Double> prices) {
        NbtElement gemsEl = ex.get("gems");
        if (gemsEl == null) return 0;
        NbtCompound gems = gemsEl.asCompound().orElse(null);
        if (gems == null) return 0;
        double total = 0;

        // ---- Gemstone slot unlock costs (Divan / Crimson family armor) ----
        try {
            boolean isDivan = id != null && id.matches("DIVAN_(HELMET|CHESTPLATE|LEGGINGS|BOOTS)");
            boolean isCrimson = id != null && id.matches("(HOT_|FIERY_|BURNING_|INFERNAL_)?(AURORA|CRIMSON|TERROR|HOLLOW|FERVOR)(_HELMET|_CHESTPLATE|_LEGGINGS|_BOOTS)");
            if ((isDivan || isCrimson) && meta != null && meta.has("gemstone_slots") && meta.get("gemstone_slots").isJsonArray()) {
                double application = isDivan
                        ? fishmod.utils.networth.NwConstants.GEMSTONE_CHAMBERS
                        : fishmod.utils.networth.NwConstants.GEMSTONE_SLOTS;
                NbtList unlocked = gems.getList("unlocked_slots").orElse(null);
                if (unlocked != null) {
                    com.google.gson.JsonArray slots = meta.getAsJsonArray("gemstone_slots");
                    for (int u = 0; u < unlocked.size(); u++) {
                        String slotName = unlocked.getString(u).orElse("");
                        // slot entries look like COMBAT_0 -> match by slot_type prefix
                        String slotType = slotName.contains("_") ? slotName.substring(0, slotName.lastIndexOf('_')) : slotName;
                        for (com.google.gson.JsonElement se : slots) {
                            if (!se.isJsonObject()) continue;
                            com.google.gson.JsonObject so = se.getAsJsonObject();
                            if (so.has("slot_type") && so.get("slot_type").getAsString().equals(slotType) && so.has("costs")) {
                                double t = 0;
                                for (com.google.gson.JsonElement ce : so.getAsJsonArray("costs")) {
                                    com.google.gson.JsonObject co = ce.getAsJsonObject();
                                    String ctype = co.has("type") ? co.get("type").getAsString() : "";
                                    if ("COINS".equals(ctype) && co.has("coins")) t += co.get("coins").getAsDouble();
                                    else if ("ITEM".equals(ctype) && co.has("item_id"))
                                        t += price(prices, co.get("item_id").getAsString().toUpperCase()) * (co.has("amount") ? co.get("amount").getAsDouble() : 0);
                                }
                                total += t * application;
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        for (String slot : gems.getKeys()) {
            if (slot.equals("unlocked_slots") || slot.endsWith("_gem")) continue;
            // Tier: either a bare string ("PERFECT") or a compound with "quality".
            String tier = gems.getString(slot, "");
            if (tier.isEmpty()) {
                NbtCompound c = gems.get(slot) != null ? gems.get(slot).asCompound().orElse(null) : null;
                if (c != null) tier = c.getString("quality", "");
            }
            if (tier.isEmpty()) continue;
            // Type: from slot name (e.g. "JASPER_0") or the companion "<slot>_gem" entry for universal slots.
            String type = slot.split("_")[0];
            if (!GEM_TYPES.contains(type)) {
                String named = gems.getString(slot + "_gem", "");
                if (!named.isEmpty()) type = named;
            }
            if (!GEM_TYPES.contains(type)) continue;
            total += price(prices, tier + "_" + type + "_GEM");
        }
        return total;
    }

    private static double petsValueNw(JsonObject member, Map<String, Double> prices) {
        double total = 0;
        try {
            JsonArray pets = null;
            if (member.has("pets_data") && member.getAsJsonObject("pets_data").has("pets"))
                pets = member.getAsJsonObject("pets_data").getAsJsonArray("pets");
            else if (member.has("pets")) pets = member.getAsJsonArray("pets");
            if (pets == null) return 0;
            for (JsonElement pe : pets) {
                JsonObject pet = pe.getAsJsonObject();
                String type = pet.has("type") ? pet.get("type").getAsString() : null;
                String tier = pet.has("tier") ? pet.get("tier").getAsString() : null;
                if (type == null || tier == null) continue;
                double exp = pet.has("exp") ? pet.get("exp").getAsDouble() : 0;
                int maxLevel = "GOLDEN_DRAGON".equals(type) ? 200 : 100;
                int level = Math.min(maxLevel, fishmod.features.OverflowPetLevels.calcLevel(exp, petRarity(tier)));
                String skin = pet.has("skin") && !pet.get("skin").isJsonNull() ? pet.get("skin").getAsString() : null;
                String basePetId = tier + "_" + type;
                String petId = skin != null ? basePetId + "_SKINNED_" + skin : basePetId;
                // pet skin uses max(skinned, base) at each level point (non-cosmetic falls back to base)
                double p1 = Math.max(price(prices, "LVL_1_" + petId), price(prices, "LVL_1_" + basePetId));
                double pMax = Math.max(price(prices, "LVL_" + maxLevel + "_" + petId), price(prices, "LVL_" + maxLevel + "_" + basePetId));
                double frac = maxLevel <= 1 ? 1 : (double) (level - 1) / (maxLevel - 1);
                double base = p1 + (pMax - p1) * frac;
                if (base <= 0) base = pMax > 0 ? pMax : p1;

                double extra = 0;
                String heldItem = pet.has("heldItem") && !pet.get("heldItem").isJsonNull() ? pet.get("heldItem").getAsString() : null;
                // held pet item x1
                if (heldItem != null) extra += price(prices, heldItem) * fishmod.utils.networth.NwConstants.PET_ITEM;
                // pet skin x0.8
                if (skin != null) extra += price(prices, "PET_SKIN_" + skin) * fishmod.utils.networth.NwConstants.SOULBOUND_PET_SKINS;

                // pet candy reduction (PetCandy.js): reduces the candy-added value portion
                try {
                    int candyUsed = pet.has("candyUsed") && !pet.get("candyUsed").isJsonNull() ? pet.get("candyUsed").getAsInt() : 0;
                    if (candyUsed > 0) {
                        double reduceValue = base * (1 - fishmod.utils.networth.NwConstants.PET_CANDY);
                        double maxReduction = level == 100 ? 5_000_000 : 2_500_000;
                        reduceValue = Math.min(reduceValue, maxReduction);
                        extra -= reduceValue;
                    }
                } catch (Exception ignored) {}

                total += base + extra;
            }
        } catch (Exception ignored) {}
        return total;
    }

    /** Values everything stored in sacks (enchanted resources, gemstones, etc.). */
    private static double sacksValueNw(JsonObject member, Map<String, Double> prices) {
        JsonObject sacks = null;
        if (member.has("inventory") && member.getAsJsonObject("inventory").has("sacks_counts")
                && member.getAsJsonObject("inventory").get("sacks_counts").isJsonObject())
            sacks = member.getAsJsonObject("inventory").getAsJsonObject("sacks_counts");
        else if (member.has("sacks_counts") && member.get("sacks_counts").isJsonObject())
            sacks = member.getAsJsonObject("sacks_counts");
        if (sacks == null) return 0;
        double total = 0;
        for (Map.Entry<String, JsonElement> e : sacks.entrySet()) {
            try {
                double cnt = e.getValue().getAsDouble();
                if (cnt > 0) total += price(prices, e.getKey()) * cnt;
            } catch (Exception ignored) {}
        }
        return total;
    }

    /** Values stored essence (wither/crimson/dragon/etc.) from the currencies block. */
    private static double essenceValueNw(JsonObject member, Map<String, Double> prices) {
        try {
            if (!member.has("currencies")) return 0;
            JsonObject cur = member.getAsJsonObject("currencies");
            if (!cur.has("essence") || !cur.get("essence").isJsonObject()) return 0;
            JsonObject ess = cur.getAsJsonObject("essence");
            double total = 0;
            for (Map.Entry<String, JsonElement> e : ess.entrySet()) {
                double amt = 0;
                if (e.getValue().isJsonObject()) {
                    JsonObject o = e.getValue().getAsJsonObject();
                    if (o.has("current")) amt = o.get("current").getAsDouble();
                } else {
                    try { amt = e.getValue().getAsDouble(); } catch (Exception ignored) {}
                }
                if (amt > 0) total += price(prices, "ESSENCE_" + e.getKey().toUpperCase()) * amt;
            }
            return total;
        } catch (Exception ignored) { return 0; }
    }

    /**
     * Values the Galatea/Foraging Attribute Shard system: loose captured shards
     * ({@code member.shards.owned} → {@code SHARD_<TYPE>}) plus fused attribute stacks
     * ({@code member.attributes.stacks} → {@code ATTRIBUTE_SHARD_<NAME>}). SkyHelper does not
     * value this, so this is a market-resale estimate (shard count × current shard price).
     */
    private static double shardsValueNw(JsonObject member, Map<String, Double> prices) {
        double total = 0;
        try {
            JsonObject shards = member.has("shards") && member.get("shards").isJsonObject()
                    ? member.getAsJsonObject("shards") : null;
            if (shards != null && shards.has("owned") && shards.get("owned").isJsonArray()) {
                for (JsonElement e : shards.getAsJsonArray("owned")) {
                    try {
                        JsonObject o = e.getAsJsonObject();
                        String type = o.has("type") ? o.get("type").getAsString() : null;
                        double amt = o.has("amount_owned") ? o.get("amount_owned").getAsDouble() : 0;
                        if (type != null && amt > 0) total += price(prices, "SHARD_" + type.toUpperCase()) * amt;
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
        try {
            JsonObject attrs = member.has("attributes") && member.get("attributes").isJsonObject()
                    ? member.getAsJsonObject("attributes") : null;
            if (attrs != null && attrs.has("stacks") && attrs.get("stacks").isJsonObject()) {
                JsonObject stacks = attrs.getAsJsonObject("stacks");
                for (Map.Entry<String, JsonElement> e : stacks.entrySet()) {
                    try {
                        double cnt = e.getValue().getAsDouble();
                        if (cnt > 0) total += price(prices, "ATTRIBUTE_SHARD_" + e.getKey().toUpperCase()) * cnt;
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
        return total;
    }

    private static fishmod.features.OverflowPetLevels.Rarity petRarity(String tier) {
        try { return fishmod.features.OverflowPetLevels.Rarity.valueOf(tier); }
        catch (Exception e) { return fishmod.features.OverflowPetLevels.Rarity.LEGENDARY; }
    }

    /** Blocking UUID resolve (Mojang → Ashcon), with on-disk cache. */
    private static String resolveUuidBlocking(String ign) {
        String cached = getCachedUuid(ign);
        if (cached != null) return cached;
        // Mojang is authoritative for the CURRENT owner of a name. Only fall back to the (cache-laggy)
        // Ashcon mirror when Mojang can't answer (rate-limit / outage) — never on a clean not-found,
        // which would let a stale mirror resolve a recycled name to the wrong account.
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + ign))
                    .timeout(Duration.ofSeconds(8)).header("User-Agent", "FishMod").GET().build();
            HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            int code = r.statusCode();
            if (code >= 200 && code < 300) {
                String uuid = parseUuid(r.body());
                if (uuid != null) { putUuid(ign, uuid); return uuid; }
            }
            boolean transientErr = code == 429 || code == 408 || code >= 500;
            if (!transientErr) return null; // authoritative not-found — don't ask the stale mirror
        } catch (Exception ignored) {}
        // Mojang unreachable / rate-limited — fall back to Ashcon.
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.ashcon.app/mojang/v2/user/" + ign))
                    .timeout(Duration.ofSeconds(8)).header("User-Agent", "FishMod").GET().build();
            HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() == 200) {
                String uuid = parseUuid(r.body());
                if (uuid != null) { putUuid(ign, uuid); return uuid; }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public interface EconomyCallback { void onData(double bank, double purse, String corpses); }

    public static final class PowderData {
        public long mithril  = -1;
        public long gemstone = -1;
        public long glacite  = -1;

        public boolean hasData() {
            return mithril >= 0 || gemstone >= 0 || glacite >= 0;
        }
    }

    public interface PowderCallback { void onData(PowderData data); }

    /** Fetches mithril/gemstone/glacite powder from the player's selected SkyBlock profile. */
    public static void getPowderByName(MinecraftClient mc, String ign, PowderCallback cb) {
        if (!checkKey(mc)) return;
        mc.send(() -> Misc.addChatMessage(Text.literal("§7Looking up " + ign + "'s powder...")));
        resolveUuid(ign, 0, uuid -> {
            if (uuid == null) {
                mc.send(() -> Misc.addChatMessage(Text.literal("§cPlayer not found: " + ign)));
                mc.execute(() -> cb.onData(new PowderData()));
                return;
            }
            fetchPowder(mc, uuid, cb);
        });
    }

    private static void fetchPowder(MinecraftClient mc, String uuidStr, PowderCallback cb) {
        HttpRequest req;
        try {
            req = HttpRequest.newBuilder()
                .uri(URI.create(PROXY_URL + "/skyblock/profiles?uuid=" + uuidStr))
                .header("X-FishMod-Token", MOD_TOKEN)
                .header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        } catch (Exception e) {
            mc.execute(() -> cb.onData(new PowderData()));
            return;
        }
        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
            .thenAccept(resp -> {
                PowderData result = new PowderData();
                try {
                    JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
                    if (!root.get("success").getAsBoolean()) {
                        mc.send(() -> Misc.addChatMessage(Text.literal("§cAPI error — proxy rejected request.")));
                        mc.execute(() -> cb.onData(new PowderData()));
                        return;
                    }
                    JsonObject member = findSelectedMember(root, uuidStr);
                    if (member != null) result = parsePowder(member);
                } catch (Exception e) {
                    mc.send(() -> Misc.addChatMessage(Text.literal("§cAPI parse error: " + e.getMessage())));
                }
                PowderData finalResult = result;
                mc.execute(() -> cb.onData(finalResult));
            })
            .exceptionally(e -> {
                mc.send(() -> Misc.addChatMessage(Text.literal("§cAPI request failed.")));
                mc.execute(() -> cb.onData(new PowderData()));
                return null;
            });
    }

    private static JsonObject findSelectedMember(JsonObject root, String uuidStr) {
        if (!root.has("profiles") || root.get("profiles").isJsonNull()) return null;
        JsonObject chosen = null;
        for (JsonElement pe : root.getAsJsonArray("profiles")) {
            JsonObject p = pe.getAsJsonObject();
            if (p.has("selected") && p.get("selected").getAsBoolean()) { chosen = p; break; }
            if (chosen == null) chosen = p;
        }
        if (chosen == null || !chosen.has("members")) return null;
        JsonObject members = chosen.getAsJsonObject("members");
        return members.has(uuidStr) ? members.getAsJsonObject(uuidStr) : null;
    }

    private static PowderData parsePowder(JsonObject member) {
        PowderData d = new PowderData();
        if (!member.has("mining_core")) return d;
        JsonObject core = member.getAsJsonObject("mining_core");
        // powder_* = unspent balance; powder_spent = lifetime spent — total = both
        d.mithril  = totalPowder(core, "mithril");
        d.gemstone = totalPowder(core, "gemstone");
        d.glacite  = totalPowder(core, "glacite");
        return d;
    }

    private static long totalPowder(JsonObject core, String type) {
        long current = readPowderField(core, "powder_" + type);
        long spent   = readPowderSpent(core, type);
        if (current < 0 && spent < 0) return -1;
        return Math.max(0, current) + Math.max(0, spent);
    }

    private static long readPowderSpent(JsonObject core, String type) {
        if (core.has("powder_spent") && !core.get("powder_spent").isJsonNull()) {
            JsonElement spentEl = core.get("powder_spent");
            if (spentEl.isJsonObject()) {
                long v = readPowderField(spentEl.getAsJsonObject(), type);
                if (v >= 0) return v;
            }
        }
        return readPowderField(core, "powder_spent_" + type);
    }

    private static long readPowderField(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) return -1;
        return (long) obj.get(key).getAsDouble();
    }

    /** Fetches bank balance, purse, and glacite corpses for the local player's selected profile. */
    public static void getEconomy(MinecraftClient mc, EconomyCallback cb) {
        if (mc.player == null) { cb.onData(-1, -1, null); return; }
        String uuid = mc.player.getUuid().toString().replace("-", "");
        HttpRequest req;
        try {
            req = HttpRequest.newBuilder()
                .uri(URI.create(PROXY_URL + "/skyblock/profiles?uuid=" + uuid))
                .header("X-FishMod-Token", MOD_TOKEN).header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(10)).GET().build();
        } catch (Exception e) { cb.onData(-1, -1, null); return; }
        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept(r -> {
            double bank = -1, purse = -1; String corpses = null;
            try {
                JsonObject root = JsonParser.parseString(r.body()).getAsJsonObject();
                for (JsonElement pe : root.getAsJsonArray("profiles")) {
                    JsonObject profile = pe.getAsJsonObject();
                    if (!profile.has("selected") || !profile.get("selected").getAsBoolean()) continue;
                    if (profile.has("banking") && profile.getAsJsonObject("banking").has("balance"))
                        bank = profile.getAsJsonObject("banking").get("balance").getAsDouble();
                    JsonObject member = profile.getAsJsonObject("members").getAsJsonObject(uuid);
                    if (member.has("currencies") && member.getAsJsonObject("currencies").has("coin_purse"))
                        purse = member.getAsJsonObject("currencies").get("coin_purse").getAsDouble();
                    else if (member.has("coin_purse")) purse = member.get("coin_purse").getAsDouble();
                    if (member.has("glacite_player_data")) {
                        JsonElement cl = member.getAsJsonObject("glacite_player_data").get("corpses_looted");
                        corpses = formatCorpses(cl);
                    }
                    break;
                }
            } catch (Exception ignored) {}
            cb.onData(bank, purse, corpses);
        }).exceptionally(t -> { cb.onData(-1, -1, null); return null; });
    }

    /** Uploads the local player's cosmetic nick (empty/null clears it) so other mod users can see it. */
    public static void uploadNick(String uuidNoDashes, String nick) {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("uuid", uuidNoDashes);
            o.addProperty("nick", nick == null ? "" : nick);
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(PROXY_URL + "/nick"))
                .header("X-FishMod-Token", MOD_TOKEN)
                .header("Content-Type", "application/json")
                .header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(o.toString()))
                .build();
            HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception ignored) {}
    }

    /** A shared location ping published by a FishMod user. */
    public record PingData(String uuid, String name, double x, double y, double z, String dim, long ts) {}

    /** Publishes (or refreshes) the local player's current location ping to the shared store. */
    public static void uploadPing(String uuidNoDashes, String name, double x, double y, double z, String dim) {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("uuid", uuidNoDashes);
            o.addProperty("name", name == null ? "" : name);
            o.addProperty("x", x);
            o.addProperty("y", y);
            o.addProperty("z", z);
            o.addProperty("dim", dim == null ? "" : dim);
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(PROXY_URL + "/ping"))
                .header("X-FishMod-Token", MOD_TOKEN)
                .header("Content-Type", "application/json")
                .header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(o.toString()))
                .build();
            HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception ignored) {}
    }

    /** Fetches live pings for the given UUIDs newer than {@code since} (ms). Empty list on any failure. */
    public static void fetchPings(java.util.Collection<String> uuidsNoDashes, long since,
                                  java.util.function.Consumer<java.util.List<PingData>> cb) {
        if (uuidsNoDashes == null || uuidsNoDashes.isEmpty()) { cb.accept(java.util.List.of()); return; }
        try {
            String q = String.join(",", uuidsNoDashes);
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(PROXY_URL + "/pings?uuids=" + q + "&since=" + since))
                .header("X-FishMod-Token", MOD_TOKEN)
                .header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(10))
                .GET().build();
            HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept(r -> {
                java.util.List<PingData> out = new java.util.ArrayList<>();
                try {
                    JsonObject root = JsonParser.parseString(r.body()).getAsJsonObject();
                    if (root.has("pings") && root.get("pings").isJsonObject()) {
                        for (var e : root.getAsJsonObject("pings").entrySet()) {
                            if (e.getValue() == null || !e.getValue().isJsonObject()) continue;
                            JsonObject pr = e.getValue().getAsJsonObject();
                            out.add(new PingData(
                                e.getKey(),
                                pr.has("name") ? pr.get("name").getAsString() : "",
                                pr.get("x").getAsDouble(), pr.get("y").getAsDouble(), pr.get("z").getAsDouble(),
                                pr.has("dim") ? pr.get("dim").getAsString() : "",
                                pr.has("ts") ? pr.get("ts").getAsLong() : 0L));
                        }
                    }
                } catch (Exception ignored) {}
                cb.accept(out);
            }).exceptionally(t -> { cb.accept(java.util.List.of()); return null; });
        } catch (Exception e) { cb.accept(java.util.List.of()); }
    }

    /** Aggregate reputation for a player: crowd-sourced up/down vote counts. */
    public record RepData(String name, int up, int down) {}

    /** Casts the local player's reputation vote on a target. vote ∈ "up" | "down" | "none" (clears). */
    public static void voteRep(String voterUuid, String targetUuid, String targetName, String vote,
                               java.util.function.BiConsumer<Integer, Integer> cb) {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("voter", voterUuid);
            o.addProperty("target", targetUuid);
            o.addProperty("name", targetName == null ? "" : targetName);
            o.addProperty("vote", vote);
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(PROXY_URL + "/rep"))
                .header("X-FishMod-Token", MOD_TOKEN)
                .header("Content-Type", "application/json")
                .header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(o.toString()))
                .build();
            HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept(r -> {
                int up = -1, down = -1;
                try {
                    JsonObject root = JsonParser.parseString(r.body()).getAsJsonObject();
                    if (root.has("success") && root.get("success").getAsBoolean()) {
                        up = root.has("up") ? root.get("up").getAsInt() : 0;
                        down = root.has("down") ? root.get("down").getAsInt() : 0;
                    }
                } catch (Exception ignored) {}
                if (cb != null) cb.accept(up, down);
            }).exceptionally(t -> { if (cb != null) cb.accept(-1, -1); return null; });
        } catch (Exception e) { if (cb != null) cb.accept(-1, -1); }
    }

    /** Batch-fetches reputation for the given UUIDs → map of uuid(no dashes) → RepData. */
    public static void fetchReps(java.util.Collection<String> uuidsNoDashes,
                                 java.util.function.Consumer<Map<String, RepData>> cb) {
        if (uuidsNoDashes == null || uuidsNoDashes.isEmpty()) { cb.accept(java.util.Map.of()); return; }
        try {
            String q = String.join(",", uuidsNoDashes);
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(PROXY_URL + "/rep?uuids=" + q))
                .header("X-FishMod-Token", MOD_TOKEN)
                .header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(10))
                .GET().build();
            HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept(r -> {
                Map<String, RepData> out = new HashMap<>();
                try {
                    JsonObject root = JsonParser.parseString(r.body()).getAsJsonObject();
                    if (root.has("reps") && root.get("reps").isJsonObject()) {
                        for (var e : root.getAsJsonObject("reps").entrySet()) {
                            if (e.getValue() == null || !e.getValue().isJsonObject()) continue;
                            JsonObject rr = e.getValue().getAsJsonObject();
                            out.put(e.getKey(), new RepData(
                                rr.has("name") ? rr.get("name").getAsString() : "",
                                rr.has("up") ? rr.get("up").getAsInt() : 0,
                                rr.has("down") ? rr.get("down").getAsInt() : 0));
                        }
                    }
                } catch (Exception ignored) {}
                cb.accept(out);
            }).exceptionally(t -> { cb.accept(java.util.Map.of()); return null; });
        } catch (Exception e) { cb.accept(java.util.Map.of()); }
    }

    /** Batch-fetches cosmetic nicks for the given UUIDs → map of uuid(no dashes) → raw nick. */
    public static void fetchNicks(java.util.Collection<String> uuidsNoDashes,
                                  java.util.function.Consumer<Map<String, String>> cb) {
        if (uuidsNoDashes == null || uuidsNoDashes.isEmpty()) { cb.accept(java.util.Map.of()); return; }
        try {
            String q = String.join(",", uuidsNoDashes);
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(PROXY_URL + "/nicks?uuids=" + q))
                .header("X-FishMod-Token", MOD_TOKEN)
                .header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(10))
                .GET().build();
            HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept(r -> {
                Map<String, String> out = new HashMap<>();
                try {
                    JsonObject root = JsonParser.parseString(r.body()).getAsJsonObject();
                    if (root.has("nicks") && root.get("nicks").isJsonObject())
                        for (var e : root.getAsJsonObject("nicks").entrySet())
                            if (e.getValue() != null && !e.getValue().isJsonNull())
                                out.put(e.getKey(), e.getValue().getAsString());
                } catch (Exception ignored) {}
                cb.accept(out);
            }).exceptionally(t -> { cb.accept(java.util.Map.of()); return null; });
        } catch (Exception e) { cb.accept(java.util.Map.of()); }
    }

    /** Publishes the local player's render size as "x,y,z" (all 1.0 = none/clear). */
    public static void uploadScale(String uuidNoDashes, float x, float y, float z) {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("uuid", uuidNoDashes);
            o.addProperty("scale", x + "," + y + "," + z);
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(PROXY_URL + "/scale"))
                .header("X-FishMod-Token", MOD_TOKEN)
                .header("Content-Type", "application/json")
                .header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(o.toString()))
                .build();
            HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception ignored) {}
    }

    /** Result of a /sync poll: the server version, and (only when changed) the nick/item/scale maps. */
    public interface SyncCallback {
        /** @param version current server version; @param nicks/items/scales null when nothing changed. */
        void onData(long version, Map<String, String> nicks, Map<String, String> items, Map<String, String> scales);
    }

    /**
     * Combined, version-gated poll. Sends the last-seen {@code version}; the worker returns just the
     * version (nicks/items null) when nothing has changed, or both maps filtered to {@code uuids}
     * when it has. Replaces the separate {@link #fetchNicks}/{@link #fetchItems} polls so the mod can
     * refresh often while reading the full tables server-side only when something actually changed.
     */
    public static void fetchSync(java.util.Collection<String> uuidsNoDashes, long version, SyncCallback cb) {
        if (uuidsNoDashes == null || uuidsNoDashes.isEmpty()) { cb.onData(version, null, null, null); return; }
        try {
            String q = String.join(",", uuidsNoDashes);
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(PROXY_URL + "/sync?since=" + version + "&uuids=" + q))
                .header("X-FishMod-Token", MOD_TOKEN)
                .header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(10))
                .GET().build();
            HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept(r -> {
                try {
                    JsonObject root = JsonParser.parseString(r.body()).getAsJsonObject();
                    if (!root.has("success") || !root.get("success").getAsBoolean()) { cb.onData(version, null, null, null); return; }
                    long ver = root.has("version") ? root.get("version").getAsLong() : version;
                    if (!root.has("changed") || !root.get("changed").getAsBoolean()) { cb.onData(ver, null, null, null); return; }
                    cb.onData(ver, parseStringMap(root, "nicks"), parseStringMap(root, "items"), parseStringMap(root, "scales"));
                } catch (Exception ignored) { cb.onData(version, null, null, null); }
            }).exceptionally(t -> { cb.onData(version, null, null, null); return null; });
        } catch (Exception e) { cb.onData(version, null, null, null); }
    }

    private static Map<String, String> parseStringMap(JsonObject root, String key) {
        Map<String, String> out = new HashMap<>();
        if (root.has(key) && root.get(key).isJsonObject())
            for (var e : root.getAsJsonObject(key).entrySet())
                if (e.getValue() != null && !e.getValue().isJsonNull())
                    out.put(e.getKey(), e.getValue().getAsString());
        return out;
    }

    /** Uploads the local player's item customizations (JSON array; empty array clears them). */
    public static void uploadItems(String uuidNoDashes, String itemsJson) {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("uuid", uuidNoDashes);
            o.addProperty("items", itemsJson == null ? "" : itemsJson);
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(PROXY_URL + "/items"))
                .header("X-FishMod-Token", MOD_TOKEN)
                .header("Content-Type", "application/json")
                .header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(o.toString()))
                .build();
            HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception ignored) {}
    }

    /** Batch-fetches item customizations for the given UUIDs → map of uuid(no dashes) → JSON payload. */
    public static void fetchItems(java.util.Collection<String> uuidsNoDashes,
                                  java.util.function.Consumer<Map<String, String>> cb) {
        if (uuidsNoDashes == null || uuidsNoDashes.isEmpty()) { cb.accept(java.util.Map.of()); return; }
        try {
            String q = String.join(",", uuidsNoDashes);
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(PROXY_URL + "/items?uuids=" + q))
                .header("X-FishMod-Token", MOD_TOKEN)
                .header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(10))
                .GET().build();
            HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept(r -> {
                Map<String, String> out = new HashMap<>();
                try {
                    JsonObject root = JsonParser.parseString(r.body()).getAsJsonObject();
                    if (root.has("items") && root.get("items").isJsonObject())
                        for (var e : root.getAsJsonObject("items").entrySet())
                            if (e.getValue() != null && !e.getValue().isJsonNull())
                                out.put(e.getKey(), e.getValue().getAsString());
                } catch (Exception ignored) {}
                cb.accept(out);
            }).exceptionally(t -> { cb.accept(java.util.Map.of()); return null; });
        } catch (Exception e) { cb.accept(java.util.Map.of()); }
    }

    /** Fetches the local player's selected-profile member object (key held proxy-side). */
    public static void getLocalMember(MinecraftClient mc, java.util.function.Consumer<JsonObject> cb) {
        if (mc.player == null) { cb.accept(null); return; }
        String uuid = mc.player.getUuid().toString().replace("-", "");
        HttpRequest req;
        try {
            req = HttpRequest.newBuilder()
                .uri(URI.create(PROXY_URL + "/skyblock/profiles?uuid=" + uuid))
                .header("X-FishMod-Token", MOD_TOKEN).header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(10)).GET().build();
        } catch (Exception e) { cb.accept(null); return; }
        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept(r -> {
            JsonObject member = null;
            try {
                JsonObject root = JsonParser.parseString(r.body()).getAsJsonObject();
                if (root.has("success") && root.get("success").getAsBoolean())
                    member = findSelectedMember(root, uuid);
            } catch (Exception ignored) {}
            JsonObject fm = member;
            mc.execute(() -> cb.accept(fm));
        }).exceptionally(t -> { mc.execute(() -> cb.accept(null)); return null; });
    }

    /** Fetches bank balance, purse, and glacite corpses for an arbitrary player by IGN. */
    public static void getEconomyByName(MinecraftClient mc, String ign, EconomyCallback cb) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                String uuid = resolveUuidBlocking(ign);
                if (uuid == null) { cb.onData(-1, -1, null); return; }
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(PROXY_URL + "/skyblock/profiles?uuid=" + uuid))
                    .header("X-FishMod-Token", MOD_TOKEN).header("User-Agent", "Mozilla/5.0")
                    .timeout(Duration.ofSeconds(12)).GET().build();
                HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                double bank = -1, purse = -1; String corpses = null;
                JsonObject root = JsonParser.parseString(r.body()).getAsJsonObject();
                if (root.has("profiles") && !root.get("profiles").isJsonNull()) {
                    JsonObject chosen = null;
                    for (JsonElement pe : root.getAsJsonArray("profiles")) {
                        JsonObject p = pe.getAsJsonObject();
                        if (p.has("selected") && p.get("selected").getAsBoolean()) { chosen = p; break; }
                        if (chosen == null) chosen = p;
                    }
                    if (chosen != null) {
                        if (chosen.has("banking") && chosen.getAsJsonObject("banking").has("balance"))
                            bank = chosen.getAsJsonObject("banking").get("balance").getAsDouble();
                        JsonObject member = chosen.getAsJsonObject("members").getAsJsonObject(uuid);
                        if (member.has("currencies") && member.getAsJsonObject("currencies").has("coin_purse"))
                            purse = member.getAsJsonObject("currencies").get("coin_purse").getAsDouble();
                        else if (member.has("coin_purse")) purse = member.get("coin_purse").getAsDouble();
                        if (member.has("glacite_player_data"))
                            corpses = formatCorpses(member.getAsJsonObject("glacite_player_data").get("corpses_looted"));
                    }
                }
                cb.onData(bank, purse, corpses);
            } catch (Exception ex) {
                fishmod.utils.debug.Debug.LOGGER.warn("[Economy] error: {}", ex.toString());
                cb.onData(-1, -1, null);
            }
        });
    }

    /** corpses_looted is an object {type:count}; format as "12L, 3T, ... (total N)". */
    private static String formatCorpses(JsonElement el) {
        if (el == null || el.isJsonNull()) return "0";
        try {
            if (el.isJsonObject()) {
                JsonObject o = el.getAsJsonObject();
                long total = 0;
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                    long v = e.getValue().getAsLong();
                    total += v;
                    String t = e.getKey();
                    String abbr = t.isEmpty() ? "?" : t.substring(0, 1).toUpperCase();
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(v).append(abbr);
                }
                sb.append(" (total ").append(total).append(")");
                return sb.toString();
            }
            return el.getAsString();
        } catch (Exception e) { return "0"; }
    }

    /** Debug: dumps economy/glacite-related fields for the local player's selected profile. */
    public static void dumpEconomy(MinecraftClient mc) {
        if (mc.player == null) return;
        String uuid = mc.player.getUuid().toString().replace("-", "");
        mc.send(() -> Misc.addChatMessage(Text.literal("§7Fetching profile economy fields...")));
        HttpRequest req;
        try {
            req = HttpRequest.newBuilder()
                .uri(URI.create(PROXY_URL + "/skyblock/profiles?uuid=" + uuid))
                .header("X-FishMod-Token", MOD_TOKEN).header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(10)).GET().build();
        } catch (Exception e) { return; }
        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept(r -> {
            try {
                JsonObject root = JsonParser.parseString(r.body()).getAsJsonObject();
                for (JsonElement pe : root.getAsJsonArray("profiles")) {
                    JsonObject profile = pe.getAsJsonObject();
                    if (!profile.has("selected") || !profile.get("selected").getAsBoolean()) continue;
                    JsonObject member = profile.getAsJsonObject("members").getAsJsonObject(uuid);
                    mc.send(() -> {
                        Misc.addChatMessage(Text.literal("§b--- Economy dump ---"));
                        // Bank (profile-level)
                        if (profile.has("banking") && profile.getAsJsonObject("banking").has("balance"))
                            Misc.addChatMessage(Text.literal("§7banking.balance = §f" + profile.getAsJsonObject("banking").get("balance")));
                        else Misc.addChatMessage(Text.literal("§7banking.balance = §cmissing"));
                        // Purse
                        if (member.has("coin_purse")) Misc.addChatMessage(Text.literal("§7coin_purse = §f" + member.get("coin_purse")));
                        if (member.has("currencies")) {
                            JsonObject cur = member.getAsJsonObject("currencies");
                            Misc.addChatMessage(Text.literal("§7currencies keys = §f" + cur.keySet()));
                            if (cur.has("coin_purse")) Misc.addChatMessage(Text.literal("§7currencies.coin_purse = §f" + cur.get("coin_purse")));
                        }
                        // Glacite corpses
                        if (member.has("glacite_player_data")) {
                            JsonObject g = member.getAsJsonObject("glacite_player_data");
                            Misc.addChatMessage(Text.literal("§7glacite_player_data keys = §f" + g.keySet()));
                            if (g.has("corpses")) Misc.addChatMessage(Text.literal("§7glacite.corpses = §f" + g.get("corpses")));
                        } else {
                            Misc.addChatMessage(Text.literal("§7glacite_player_data = §cmissing"));
                        }
                        Misc.addChatMessage(Text.literal("§7member top keys = §f" + member.keySet()));
                        Misc.addChatMessage(Text.literal("§b--- End ---"));
                    });
                    return;
                }
            } catch (Exception e) {
                mc.send(() -> Misc.addChatMessage(Text.literal("§cdump error: " + e.getMessage())));
            }
        });
    }

    /** Returns XP needed to reach the next whole cata level, formatted like "142.3k" or "1.23m". */
    public static String xpToNextLevel(long xp) {
        for (int i = 0; i < CATA_XP_TABLE.length - 1; i++) {
            if (xp < CATA_XP_TABLE[i + 1]) {
                long needed = CATA_XP_TABLE[i + 1] - xp;
                if (needed >= 1_000_000) return String.format("%.2fm", needed / 1_000_000.0);
                if (needed >= 1_000)     return String.format("%.1fk", needed / 1_000.0);
                return String.valueOf(needed);
            }
        }
        // Overflow (level 50+): each extra level = 200,000,000 XP
        long overflow = xp - CATA_XP_TABLE[CATA_XP_TABLE.length - 1];
        long needed = 200_000_000L - (overflow % 200_000_000L);
        if (needed >= 1_000_000) return String.format("%.2fm", needed / 1_000_000.0);
        if (needed >= 1_000)     return String.format("%.1fk", needed / 1_000.0);
        return String.valueOf(needed);
    }

    /** Returns level as a formatted string like "42.75" or "51.30" for overflow cata. */
    public static String formatLevel(long xp) {
        for (int i = CATA_XP_TABLE.length - 1; i >= 0; i--) {
            if (xp >= CATA_XP_TABLE[i]) {
                if (i == CATA_XP_TABLE.length - 1) {
                    // Overflow: each extra level = 200,000,000 XP (from adjectils)
                    double overflow = (double)(xp - CATA_XP_TABLE[i]) / 200_000_000.0;
                    return String.format("%.2f", i + overflow);
                }
                double progress = (double)(xp - CATA_XP_TABLE[i]) / (CATA_XP_TABLE[i + 1] - CATA_XP_TABLE[i]);
                return String.format("%d.%02d", i, (int)(progress * 100));
            }
        }
        return "0.00";
    }

    // ─── Active-pet XP (API-driven, replaces tab scraping + PetXpAutoDetect) ──────

    public interface PetCallback { void onData(PetInfo p); }

    public static final class PetInfo {
        public boolean ok = false;
        public String name;
        public int level;
        public boolean maxed;
        public int overflowLevel = -1;
        public double xpIntoLevel = -1, xpForNext = -1;
        public float pct = -1;
    }

    private static fishmod.features.OverflowPetLevels.Rarity petRarityOf(String tier) {
        try { return fishmod.features.OverflowPetLevels.Rarity.valueOf(tier); }
        catch (Exception e) { return fishmod.features.OverflowPetLevels.Rarity.LEGENDARY; }
    }

    /** "GOLDEN_DRAGON" → "Golden Dragon" (matches PetHud's skill map display names). */
    private static String petTypeToName(String type) {
        String[] parts = type.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    // Standard Taming skill XP table (cumulative) for levels 0..60.
    private static final long[] TAMING_XP = {
        0L,50L,175L,375L,675L,1175L,1925L,2925L,4425L,6425L,
        9925L,14_925L,22_425L,32_425L,47_425L,67_425L,97_425L,147_425L,222_425L,322_425L,
        522_425L,822_425L,1_222_425L,1_722_425L,2_322_425L,3_022_425L,3_822_425L,4_722_425L,5_722_425L,6_822_425L,
        8_022_425L,9_322_425L,10_722_425L,12_222_425L,13_822_425L,15_522_425L,17_322_425L,19_222_425L,21_222_425L,23_322_425L,
        25_522_425L,27_822_425L,30_222_425L,32_722_425L,35_322_425L,38_072_425L,40_972_425L,44_072_425L,47_372_425L,50_872_425L,
        54_572_425L,58_472_425L,62_572_425L,66_872_425L,71_372_425L,76_072_425L,80_972_425L,86_072_425L,91_372_425L,96_872_425L,
        102_572_425L
    };
    private static final java.util.Map<String,Integer> PET_ITEM_BONUS = java.util.Map.ofEntries(
        java.util.Map.entry("PET_ITEM_ALL_SKILLS_BOOST_COMMON",25), java.util.Map.entry("PET_ITEM_ALL_SKILLS_BOOST_RARE",35),
        java.util.Map.entry("PET_ITEM_ALL_SKILLS_BOOST_EPIC",50),
        java.util.Map.entry("PET_ITEM_COMBAT_SKILL_BOOST_COMMON",20), java.util.Map.entry("PET_ITEM_COMBAT_SKILL_BOOST_UNCOMMON",30),
        java.util.Map.entry("PET_ITEM_COMBAT_SKILL_BOOST_RARE",40), java.util.Map.entry("PET_ITEM_COMBAT_SKILL_BOOST_EPIC",50),
        java.util.Map.entry("PET_ITEM_FARMING_SKILL_BOOST_COMMON",20), java.util.Map.entry("PET_ITEM_FARMING_SKILL_BOOST_UNCOMMON",30),
        java.util.Map.entry("PET_ITEM_FARMING_SKILL_BOOST_RARE",40), java.util.Map.entry("PET_ITEM_FARMING_SKILL_BOOST_EPIC",50),
        java.util.Map.entry("PET_ITEM_FISHING_SKILL_BOOST_COMMON",20), java.util.Map.entry("PET_ITEM_FISHING_SKILL_BOOST_UNCOMMON",30),
        java.util.Map.entry("PET_ITEM_FISHING_SKILL_BOOST_RARE",40), java.util.Map.entry("PET_ITEM_FISHING_SKILL_BOOST_EPIC",50),
        java.util.Map.entry("PET_ITEM_MINING_SKILL_BOOST_COMMON",20), java.util.Map.entry("PET_ITEM_MINING_SKILL_BOOST_UNCOMMON",30),
        java.util.Map.entry("PET_ITEM_MINING_SKILL_BOOST_RARE",40), java.util.Map.entry("PET_ITEM_MINING_SKILL_BOOST_EPIC",50),
        java.util.Map.entry("PET_ITEM_FORAGING_SKILL_BOOST_COMMON",20), java.util.Map.entry("PET_ITEM_FORAGING_SKILL_BOOST_UNCOMMON",30),
        java.util.Map.entry("PET_ITEM_FORAGING_SKILL_BOOST_RARE",40), java.util.Map.entry("PET_ITEM_FORAGING_SKILL_BOOST_EPIC",50)
    );
    private static final java.util.Map<String,Integer> BEASTMASTER_TIER =
        java.util.Map.of("BRONZE",30,"SILVER",35,"GOLD",40,"DIAMOND",45);

    /**
     * Fetches the local player's ACTIVE pet (level + XP into level) from the Hypixel API and,
     * when auto-detect is on, refreshes the pet-XP multipliers (taming/beastmaster/pet item/cookie).
     * The API is the authoritative source — tab scraping broke and dungeons have no pet tab entry.
     */
    public static void getActivePet(MinecraftClient mc, PetCallback cb) {
        if (mc.player == null) { cb.onData(new PetInfo()); return; }
        final String uuid = mc.player.getUuid().toString().replace("-", "");
        HttpRequest req;
        try {
            req = HttpRequest.newBuilder()
                .uri(URI.create(PROXY_URL + "/skyblock/profiles?uuid=" + uuid))
                .header("X-FishMod-Token", MOD_TOKEN).header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(10)).GET().build();
        } catch (Exception e) { cb.onData(new PetInfo()); return; }

        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept(r -> {
            PetInfo info = new PetInfo();
            try {
                JsonObject root = JsonParser.parseString(r.body()).getAsJsonObject();
                if (root.has("profiles") && !root.get("profiles").isJsonNull()) {
                    for (JsonElement pe : root.getAsJsonArray("profiles")) {
                        JsonObject profile = pe.getAsJsonObject();
                        if (!profile.has("selected") || !profile.get("selected").getAsBoolean()) continue;
                        JsonObject member = profile.getAsJsonObject("members").getAsJsonObject(uuid);
                        applyPetMultipliers(profile, member);
                        JsonObject active = null;
                        if (member.has("pets_data") && member.getAsJsonObject("pets_data").has("pets")) {
                            for (JsonElement q : member.getAsJsonObject("pets_data").getAsJsonArray("pets")) {
                                JsonObject pet = q.getAsJsonObject();
                                if (pet.has("active") && pet.get("active").getAsBoolean()) { active = pet; break; }
                            }
                        }
                        if (active != null && active.has("type")) {
                            String type = active.get("type").getAsString();
                            String tier = active.has("tier") ? active.get("tier").getAsString() : "LEGENDARY";
                            double exp  = active.has("exp") ? active.get("exp").getAsDouble() : 0;
                            fishmod.features.OverflowPetLevels.Rarity rar = petRarityOf(tier);
                            int maxLevel = "GOLDEN_DRAGON".equals(type) ? 200 : 100;
                            double remaining = exp;
                            int level = 1;
                            double cost = fishmod.features.OverflowPetLevels.getXpForLevel(0, rar);
                            while (remaining >= cost && level < 1000) {
                                remaining -= cost; level++;
                                cost = fishmod.features.OverflowPetLevels.getXpForLevel(level - 1, rar);
                            }
                            info.name = petTypeToName(type);
                            info.overflowLevel = level;
                            info.maxed = level >= maxLevel;
                            info.level = Math.min(level, maxLevel);
                            info.xpIntoLevel = remaining;
                            info.xpForNext = cost;
                            info.pct = cost > 0 ? (float)(remaining / cost * 100.0) : 0f;
                            info.ok = true;
                        }
                        break;
                    }
                }
            } catch (Exception ignored) {}
            mc.execute(() -> cb.onData(info));
        }).exceptionally(t -> { mc.execute(() -> cb.onData(new PetInfo())); return null; });
    }

    private static void applyPetMultipliers(JsonObject profile, JsonObject member) {
        try {
            // Taming level from skill XP
            if (member.has("player_data")) {
                JsonObject pd = member.getAsJsonObject("player_data");
                if (pd.has("experience") && pd.getAsJsonObject("experience").has("SKILL_TAMING")) {
                    long xp = (long) pd.getAsJsonObject("experience").get("SKILL_TAMING").getAsDouble();
                    for (int i = TAMING_XP.length - 1; i >= 0; i--) if (xp >= TAMING_XP[i]) { FishSettings.petXpTamingLevel = i; break; }
                }
            }
            // Active pet's held item bonus
            int petItem = 0;
            if (member.has("pets_data") && member.getAsJsonObject("pets_data").has("pets")) {
                for (JsonElement q : member.getAsJsonObject("pets_data").getAsJsonArray("pets")) {
                    JsonObject pet = q.getAsJsonObject();
                    if (pet.has("active") && pet.get("active").getAsBoolean()
                            && pet.has("heldItem") && !pet.get("heldItem").isJsonNull()) {
                        petItem = PET_ITEM_BONUS.getOrDefault(pet.get("heldItem").getAsString(), 0);
                        break;
                    }
                }
            }
            FishSettings.petXpPetItemBonus = petItem;
            // Beastmaster crest from accessory bag NBT
            FishSettings.petXpBeastmasterBonus = detectBeastmaster(member);
            // Booster cookie
            boolean cookie = false;
            if (member.has("profile")) {
                JsonObject mp = member.getAsJsonObject("profile");
                if (mp.has("booster_cookie_expires_at")) cookie = mp.get("booster_cookie_expires_at").getAsLong() > System.currentTimeMillis();
                else if (mp.has("cookie_buff_active")) cookie = mp.get("cookie_buff_active").getAsBoolean();
            }
            FishSettings.petXpBoosterCookie = cookie;
        } catch (Exception ignored) {}
    }

    private static int detectBeastmaster(JsonObject member) {
        try {
            if (!member.has("accessory_bag_storage")) return 0;
            JsonObject abs = member.getAsJsonObject("accessory_bag_storage");
            if (!abs.has("bag_storage")) return 0;
            JsonObject bag = abs.getAsJsonObject("bag_storage");
            if (!bag.has("data")) return 0;
            String b64 = bag.get("data").getAsString();
            if (b64.isEmpty()) return 0;
            byte[] bytes = java.util.Base64.getDecoder().decode(b64);
            NbtCompound rootNbt = NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtSizeTracker.ofUnlimitedBytes());
            NbtList listItems = rootNbt.getList("i").orElse(null);
            if (listItems == null) return 0;
            int best = 0;
            for (int i = 0; i < listItems.size(); i++) {
                NbtCompound item = listItems.getCompound(i).orElse(null);
                if (item == null || item.isEmpty()) continue;
                String id = getItemId(item);
                if (id == null || !id.contains("BEASTMASTER_CREST")) continue;
                for (Map.Entry<String,Integer> e : BEASTMASTER_TIER.entrySet())
                    if (id.endsWith(e.getKey()) && e.getValue() > best) best = e.getValue();
            }
            return best;
        } catch (Exception ignored) {}
        return 0;
    }

    /** Debug: dumps the Garden API JSON keys for the local player's selected profile (/fmgarden). */
    public static void dumpGarden(MinecraftClient mc) {
        if (mc.player == null) return;
        String uuid = mc.player.getUuid().toString().replace("-", "");
        mc.send(() -> Misc.addChatMessage(Text.literal("§7Fetching garden...")));
        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest pr = HttpRequest.newBuilder()
                    .uri(URI.create(PROXY_URL + "/skyblock/profiles?uuid=" + uuid))
                    .header("X-FishMod-Token", MOD_TOKEN).header("User-Agent", "Mozilla/5.0")
                    .timeout(Duration.ofSeconds(12)).GET().build();
                JsonObject root = JsonParser.parseString(HTTP.send(pr, HttpResponse.BodyHandlers.ofString()).body()).getAsJsonObject();
                String profileId = null;
                for (JsonElement pe : root.getAsJsonArray("profiles")) {
                    JsonObject p = pe.getAsJsonObject();
                    if (profileId == null) profileId = p.get("profile_id").getAsString();
                    if (p.has("selected") && p.get("selected").getAsBoolean()) { profileId = p.get("profile_id").getAsString(); break; }
                }
                if (profileId == null) { mc.send(() -> Misc.addChatMessage(Text.literal("§cno profile"))); return; }
                HttpRequest gr = HttpRequest.newBuilder()
                    .uri(URI.create(PROXY_URL + "/skyblock/garden?profile=" + profileId))
                    .header("X-FishMod-Token", MOD_TOKEN).header("User-Agent", "Mozilla/5.0")
                    .timeout(Duration.ofSeconds(12)).GET().build();
                String body = HTTP.send(gr, HttpResponse.BodyHandlers.ofString()).body();
                JsonObject g = JsonParser.parseString(body).getAsJsonObject();
                JsonObject garden = g.has("garden") && g.get("garden").isJsonObject() ? g.getAsJsonObject("garden") : g;
                mc.send(() -> {
                    Misc.addChatMessage(Text.literal("§b--- Garden keys ---"));
                    Misc.addChatMessage(Text.literal("§7top: §f" + garden.keySet()));
                    for (String k : new String[]{"commission_data","resources_collected","crop_milestones","unlocked_plots_ids"}) {
                        if (garden.has(k) && garden.get(k).isJsonObject())
                            Misc.addChatMessage(Text.literal("§7" + k + ": §f" + garden.getAsJsonObject(k)));
                        else if (garden.has(k))
                            Misc.addChatMessage(Text.literal("§7" + k + ": §f" + garden.get(k)));
                    }
                });
            } catch (Exception ex) {
                mc.send(() -> Misc.addChatMessage(Text.literal("§cgarden err: " + ex)));
            }
        });
    }

    // ─── Skyblock level + farming level (profiles endpoint) ───────────────────

    // General SkyBlock skill XP table (cumulative XP to reach each level, 0..60). NOT the Taming
    // table (that one differs). Used for the Farming skill level.
    private static final long[] SKILL_XP = new long[61];
    static {
        int[] per = {50,125,200,300,500,750,1000,1500,2000,3500,5000,7500,10000,15000,20000,30000,
            50000,75000,100000,200000,300000,400000,500000,600000,700000,800000,900000,1000000,
            1100000,1200000,1300000,1400000,1500000,1600000,1700000,1800000,1900000,2000000,2100000,
            2200000,2300000,2400000,2500000,2600000,2750000,2900000,3100000,3400000,3700000,4000000,
            4300000,4600000,4900000,5200000,5500000,5800000,6100000,6400000,6700000,7000000};
        long c = 0;
        for (int i = 0; i < per.length; i++) { c += per[i]; SKILL_XP[i + 1] = c; }
    }
    private static final long SKILL_OVERFLOW_PER_LEVEL = 7_000_000L;

    /** Farming/skill level as a decimal, including overflow past 60 (e.g. 60.42). */
    private static double skillLevelOverflow(long xp) {
        int lvl = 0;
        for (int i = SKILL_XP.length - 1; i >= 0; i--) if (xp >= SKILL_XP[i]) { lvl = i; break; }
        if (lvl >= 60) return 60 + (xp - SKILL_XP[60]) / (double) SKILL_OVERFLOW_PER_LEVEL;
        long into = xp - SKILL_XP[lvl];
        long need = SKILL_XP[lvl + 1] - SKILL_XP[lvl];
        return lvl + (need > 0 ? (double) into / need : 0.0);
    }

    public interface IntCallback { void onData(int value); }

    /** Recursively collects every numeric value whose key contains {@code needle} (case-insensitive). */
    private static void collectNumbersByKey(JsonElement el, String needle, java.util.Map<String, Integer> out) {
        if (el == null) return;
        if (el.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
                JsonElement v = e.getValue();
                if (e.getKey().toLowerCase().contains(needle) && v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber())
                    out.put(e.getKey(), v.getAsInt());
                collectNumbersByKey(v, needle, out);
            }
        } else if (el.isJsonArray()) {
            for (JsonElement c : el.getAsJsonArray()) collectNumbersByKey(c, needle, out);
        }
    }

    // The five crystals placed in the Crystal Nucleus. A run places all 5, so the (uncapped) run
    // count = how many times each was placed → min of total_placed across them.
    private static final String[] NUCLEUS_CRYSTALS =
        {"amber_crystal", "amethyst_crystal", "jade_crystal", "sapphire_crystal", "topaz_crystal"};

    /** Crystal Nucleus runs = min total_placed across the 5 nucleus crystals (uncapped, what viewers show). */
    private static int pickNucleusRuns(JsonObject member) {
        try {
            JsonObject crystals = member.getAsJsonObject("mining_core").getAsJsonObject("crystals");
            int min = Integer.MAX_VALUE;
            for (String c : NUCLEUS_CRYSTALS) {
                if (crystals.has(c) && crystals.getAsJsonObject(c).has("total_placed"))
                    min = Math.min(min, crystals.getAsJsonObject(c).get("total_placed").getAsInt());
                else { min = -1; break; }
            }
            if (min >= 0 && min != Integer.MAX_VALUE) return min;
        } catch (Exception ignored) {}
        // Fallback: the leveling completion counter (Hypixel caps this at 50).
        try {
            JsonObject comp = member.getAsJsonObject("leveling").getAsJsonObject("completions");
            if (comp.has("NUCLEUS_RUNS")) return comp.get("NUCLEUS_RUNS").getAsInt();
        } catch (Exception ignored) {}
        return -1;
    }

    /** Debug: dumps every numeric field whose key contains "nucleus" or "crystal" (/fmnuc). */
    public static void dumpNucleus(MinecraftClient mc) {
        if (mc.player == null) return;
        String uuid = mc.player.getUuid().toString().replace("-", "");
        mc.send(() -> Misc.addChatMessage(Text.literal("§7Searching nucleus/crystal fields...")));
        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(PROXY_URL + "/skyblock/profiles?uuid=" + uuid))
                    .header("X-FishMod-Token", MOD_TOKEN).header("User-Agent", "Mozilla/5.0")
                    .timeout(Duration.ofSeconds(12)).GET().build();
                JsonObject root = JsonParser.parseString(HTTP.send(req, HttpResponse.BodyHandlers.ofString()).body()).getAsJsonObject();
                JsonObject chosen = null;
                for (JsonElement pe : root.getAsJsonArray("profiles")) {
                    JsonObject p = pe.getAsJsonObject();
                    if (p.has("selected") && p.get("selected").getAsBoolean()) { chosen = p; break; }
                    if (chosen == null) chosen = p;
                }
                if (chosen == null) { mc.send(() -> Misc.addChatMessage(Text.literal("§cno profile"))); return; }
                JsonObject member = chosen.getAsJsonObject("members").getAsJsonObject(uuid);
                java.util.Map<String, Integer> nuc = new java.util.LinkedHashMap<>();
                collectNumbersByKey(member, "nucleus", nuc);
                java.util.Map<String, Integer> cry = new java.util.LinkedHashMap<>();
                collectNumbersByKey(member, "crystal", cry);
                mc.send(() -> {
                    Misc.addChatMessage(Text.literal("§b--- nucleus keys ---"));
                    if (nuc.isEmpty()) Misc.addChatMessage(Text.literal("§7(none)"));
                    nuc.forEach((k, v) -> Misc.addChatMessage(Text.literal("§7" + k + ": §f" + v)));
                    Misc.addChatMessage(Text.literal("§b--- crystal keys ---"));
                    cry.forEach((k, v) -> Misc.addChatMessage(Text.literal("§7" + k + ": §f" + v)));
                });
            } catch (Exception ex) {
                mc.send(() -> Misc.addChatMessage(Text.literal("§cnuc dump err: " + ex)));
            }
        });
    }

    /** Crystal Nucleus runs completed (searches the profile member for the "nucleus" run field). */
    public static void getNucleusRuns(MinecraftClient mc, String ign, IntCallback cb) {
        CompletableFuture.runAsync(() -> {
            String uuid = resolveUuidBlocking(ign);
            if (uuid == null) { cb.onData(-1); return; }
            try {
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(PROXY_URL + "/skyblock/profiles?uuid=" + uuid))
                    .header("X-FishMod-Token", MOD_TOKEN).header("User-Agent", "Mozilla/5.0")
                    .timeout(Duration.ofSeconds(12)).GET().build();
                JsonObject root = JsonParser.parseString(HTTP.send(req, HttpResponse.BodyHandlers.ofString()).body()).getAsJsonObject();
                int runs = -1;
                if (root.has("profiles") && !root.get("profiles").isJsonNull()) {
                    JsonObject chosen = null;
                    for (JsonElement pe : root.getAsJsonArray("profiles")) {
                        JsonObject p = pe.getAsJsonObject();
                        if (p.has("selected") && p.get("selected").getAsBoolean()) { chosen = p; break; }
                        if (chosen == null) chosen = p;
                    }
                    if (chosen != null) {
                        JsonObject member = chosen.getAsJsonObject("members").getAsJsonObject(uuid);
                        runs = pickNucleusRuns(member);
                    }
                }
                cb.onData(runs);
            } catch (Exception ex) {
                fishmod.utils.debug.Debug.LOGGER.warn("[Nucleus] error: {}", ex.toString());
                cb.onData(-1);
            }
        });
    }

    public interface ProfileStatsCallback { void onData(double sbLevel, double farmingLevel); }

    /** Fetches the player's SkyBlock level (leveling.experience / 100) and Farming skill level. */
    public static void getProfileStats(MinecraftClient mc, String ign, ProfileStatsCallback cb) {
        CompletableFuture.runAsync(() -> {
            String uuid = resolveUuidBlocking(ign);
            if (uuid == null) { cb.onData(-1, -1); return; }
            try {
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(PROXY_URL + "/skyblock/profiles?uuid=" + uuid))
                    .header("X-FishMod-Token", MOD_TOKEN).header("User-Agent", "Mozilla/5.0")
                    .timeout(Duration.ofSeconds(12)).GET().build();
                HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                JsonObject root = JsonParser.parseString(r.body()).getAsJsonObject();
                double sb = -1, farm = -1;
                if (root.has("profiles") && !root.get("profiles").isJsonNull()) {
                    JsonObject chosen = null;
                    for (JsonElement pe : root.getAsJsonArray("profiles")) {
                        JsonObject p = pe.getAsJsonObject();
                        if (p.has("selected") && p.get("selected").getAsBoolean()) { chosen = p; break; }
                        if (chosen == null) chosen = p;
                    }
                    if (chosen != null) {
                        JsonObject member = chosen.getAsJsonObject("members").getAsJsonObject(uuid);
                        if (member.has("leveling") && member.getAsJsonObject("leveling").has("experience"))
                            sb = member.getAsJsonObject("leveling").get("experience").getAsDouble() / 100.0;
                        if (member.has("player_data")) {
                            JsonObject pd = member.getAsJsonObject("player_data");
                            if (pd.has("experience") && pd.getAsJsonObject("experience").has("SKILL_FARMING"))
                                farm = skillLevelOverflow((long) pd.getAsJsonObject("experience").get("SKILL_FARMING").getAsDouble());
                        }
                    }
                }
                cb.onData(sb, farm);
            } catch (Exception ex) {
                fishmod.utils.debug.Debug.LOGGER.warn("[ProfileStats] error: {}", ex.toString());
                cb.onData(-1, -1);
            }
        });
    }

    // ─── Worm / Scatha bestiary ────────────────────────────────────────────────

    /** Worm + Scatha bestiary kills and the (combined) Worm bestiary tier. */
    public static class WormStats {
        public int     worm    = 0;     // Crystal Hollows Worm kills (bestiary.kills.worm_*)
        public int     scatha  = 0;     // Scatha kills (bestiary.kills.scatha_*)
        public long    total   = 0;     // worm + scatha (the bestiary tier is based on this combined total)
        public int     tier    = 0;     // current Worm-bestiary tier (0..maxTier)
        public int     maxTier = 0;     // max tier (15)
        public Integer nextTierKills;   // combined kills required for the next tier, null if maxed
        public boolean found   = false; // true if the profile's bestiary data was located
    }

    // Hypixel "Worm" bestiary family (Crystal Hollows) — combines Worm + Scatha kills into one tier.
    // Bracket 5 thresholds truncated at the family's 400-kill cap → 15 tiers. Source: Hypixel bestiary.
    private static final int[] WORM_BESTIARY_BRACKET =
        {1, 2, 3, 5, 7, 10, 15, 20, 25, 30, 60, 120, 200, 300, 400};

    private static WormStats computeWormStats(int worm, int scatha) {
        WormStats s = new WormStats();
        s.worm    = worm;
        s.scatha  = scatha;
        s.total   = (long) worm + scatha;
        s.maxTier = WORM_BESTIARY_BRACKET.length;
        int tier = s.maxTier;
        Integer next = null;
        for (int i = 0; i < WORM_BESTIARY_BRACKET.length; i++) {
            if (s.total < WORM_BESTIARY_BRACKET[i]) { tier = i; next = WORM_BESTIARY_BRACKET[i]; break; }
        }
        s.tier = tier;
        s.nextTierKills = next;
        return s;
    }

    public interface WormStatsCallback { void onData(WormStats data); }

    /**
     * Fetches the player's Worm + Scatha bestiary kills from member.bestiary.kills and computes the
     * Worm bestiary tier. Keys are matched as worm_<n> / scatha_<n> so the Crystal Hollows Worm is
     * not confused with other "worm" families (water_worm, pest_worm, flaming_worm, …) and the lookup
     * survives a future bracket-number change.
     */
    public static void getWormStats(MinecraftClient mc, String ign, WormStatsCallback cb) {
        CompletableFuture.runAsync(() -> {
            String uuid = resolveUuidBlocking(ign);
            if (uuid == null) { cb.onData(new WormStats()); return; }
            try {
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(PROXY_URL + "/skyblock/profiles?uuid=" + uuid))
                    .header("X-FishMod-Token", MOD_TOKEN).header("User-Agent", "Mozilla/5.0")
                    .timeout(Duration.ofSeconds(12)).GET().build();
                HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                JsonObject root = JsonParser.parseString(r.body()).getAsJsonObject();
                int worm = 0, scatha = 0; boolean found = false;
                if (root.has("profiles") && !root.get("profiles").isJsonNull()) {
                    JsonObject chosen = null;
                    for (JsonElement pe : root.getAsJsonArray("profiles")) {
                        JsonObject p = pe.getAsJsonObject();
                        if (p.has("selected") && p.get("selected").getAsBoolean()) { chosen = p; break; }
                        if (chosen == null) chosen = p;
                    }
                    if (chosen != null) {
                        JsonObject member = chosen.getAsJsonObject("members").getAsJsonObject(uuid);
                        if (member.has("bestiary") && member.get("bestiary").isJsonObject()) {
                            JsonObject best = member.getAsJsonObject("bestiary");
                            if (best.has("kills") && best.get("kills").isJsonObject()) {
                                found = true;
                                for (Map.Entry<String, JsonElement> e : best.getAsJsonObject("kills").entrySet()) {
                                    int v;
                                    try { v = e.getValue().getAsInt(); } catch (Exception ex) { continue; }
                                    String k = e.getKey();
                                    if (k.matches("scatha_\\d+"))    scatha += v;
                                    else if (k.matches("worm_\\d+")) worm   += v;
                                }
                            }
                        }
                    }
                }
                WormStats s = computeWormStats(worm, scatha);
                s.found = found;
                cb.onData(s);
            } catch (Exception ex) {
                fishmod.utils.debug.Debug.LOGGER.warn("[WormStats] error: {}", ex.toString());
                cb.onData(new WormStats());
            }
        });
    }
}
