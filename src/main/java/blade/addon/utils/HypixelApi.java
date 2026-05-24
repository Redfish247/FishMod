package blade.addon.utils;

import blade.addon.utils.config.values.FishSettings;
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
    /** player name (case-sensitive as returned by Mojang) → UUID without dashes */
    public  static final Map<String, String> uuidByName    = new ConcurrentHashMap<>();
    /** player name → epoch-ms when DungeonData was last fetched */
    public  static final Map<String, Long>   dataTimestamp = new ConcurrentHashMap<>();

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
        // Fast path: UUID already known — skip Ashcon/Mojang lookup entirely
        String cachedUuid = uuidByName.get(ign);
        if (cachedUuid != null) { fetchProfilesSilent(cachedUuid, callback); return; }

        fetchAsync("https://api.ashcon.app/mojang/v2/user/" + ign)
            .thenAccept(body -> {
                try {
                    JsonObject obj = JsonParser.parseString(body).getAsJsonObject();
                    if (obj.has("uuid")) {
                        String uuid = obj.get("uuid").getAsString().replace("-", "");
                        uuidByName.put(ign, uuid);
                        fetchProfilesSilent(uuid, callback); return;
                    }
                } catch (Exception ignored) {}
                // Ashcon failed — fall back to Mojang
                fetchAsync("https://api.mojang.com/users/profiles/minecraft/" + ign)
                    .thenAccept(body2 -> {
                        try {
                            JsonObject obj2 = JsonParser.parseString(body2).getAsJsonObject();
                            if (obj2.has("id")) {
                                String uuid = obj2.get("id").getAsString();
                                uuidByName.put(ign, uuid);
                                fetchProfilesSilent(uuid, callback); return;
                            }
                        } catch (Exception ignored) {}
                        callback.onData(new DungeonData());
                    })
                    .exceptionally(e -> { callback.onData(new DungeonData()); return null; });
            })
            .exceptionally(e -> {
                fetchAsync("https://api.mojang.com/users/profiles/minecraft/" + ign)
                    .thenAccept(body2 -> {
                        try {
                            JsonObject obj2 = JsonParser.parseString(body2).getAsJsonObject();
                            if (obj2.has("id")) {
                                String uuid = obj2.get("id").getAsString();
                                uuidByName.put(ign, uuid);
                                fetchProfilesSilent(uuid, callback); return;
                            }
                        } catch (Exception ignored) {}
                        callback.onData(new DungeonData());
                    })
                    .exceptionally(e2 -> { callback.onData(new DungeonData()); return null; });
                return null;
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

    private static java.util.concurrent.CompletableFuture<String> fetchAsync(String url) {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", "Mozilla/5.0")
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
            .thenApply(HttpResponse::body);
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

    /** Try multiple name→UUID services in sequence. Mojang's endpoint frequently rate-limits, so we try alts first. */
    private static void resolveUuid(String ign, int attempt, java.util.function.Consumer<String> cb) {
        String url; final int next;
        switch (attempt) {
            case 0 -> { url = "https://api.ashcon.app/mojang/v2/user/" + ign;                 next = 1; }
            case 1 -> { url = "https://playerdb.co/api/player/minecraft/" + ign;             next = 2; }
            case 2 -> { url = "https://api.mojang.com/users/profiles/minecraft/" + ign;      next = 3; }
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
                    try {
                        if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                            JsonObject obj = JsonParser.parseString(resp.body()).getAsJsonObject();
                            String uuid = null;
                            if (obj.has("uuid"))      uuid = obj.get("uuid").getAsString();   // ashcon
                            else if (obj.has("id"))   uuid = obj.get("id").getAsString();     // mojang
                            else if (obj.has("data")) {                                       // playerdb
                                JsonObject data = obj.getAsJsonObject("data").getAsJsonObject("player");
                                if (data.has("id")) uuid = data.get("id").getAsString();
                            }
                            if (uuid != null && !uuid.isEmpty()) { cb.accept(uuid.replace("-", "")); return; }
                        }
                        resolveUuid(ign, next, cb);
                    } catch (Exception e) { resolveUuid(ign, next, cb); }
                })
                .exceptionally(e -> { resolveUuid(ign, next, cb); return null; });
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

    /**
     * Fetches the accurate networth from SkyCrypt's API. Called directly from the client —
     * SkyCrypt blocks datacenter/proxy IPs (that's why the worker got 403), but a residential
     * client IP works fine. SkyCrypt runs the full networth engine (pets, enchants, stars,
     * gemstones, reforges, attributes, etc.), so the number matches in-game.
     */
    public static void getNetworth(MinecraftClient mc, String ign, NetworthCallback cb) {
        HttpRequest req;
        try {
            req = HttpRequest.newBuilder()
                .uri(URI.create("https://sky.shiiyu.moe/api/v2/profile/" + ign))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(25)).GET().build();
        } catch (Exception e) { cb.onData(-1, null); return; }
        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept(r -> {
            double nw = -1; String pname = null;
            try {
                if (r.statusCode() == 200) {
                    JsonObject root = JsonParser.parseString(r.body()).getAsJsonObject();
                    if (root.has("profiles") && root.get("profiles").isJsonObject()) {
                        JsonObject chosen = null;
                        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("profiles").entrySet()) {
                            if (!e.getValue().isJsonObject()) continue;
                            JsonObject p = e.getValue().getAsJsonObject();
                            if (p.has("current") && p.get("current").getAsBoolean()) { chosen = p; break; }
                            if (chosen == null) chosen = p;
                        }
                        if (chosen != null) {
                            if (chosen.has("cute_name")) pname = chosen.get("cute_name").getAsString();
                            JsonObject data = chosen.has("data") && chosen.get("data").isJsonObject() ? chosen.getAsJsonObject("data") : null;
                            JsonObject nwObj = data != null && data.has("networth") && data.get("networth").isJsonObject() ? data.getAsJsonObject("networth") : null;
                            if (nwObj != null && nwObj.has("networth")) nw = nwObj.get("networth").getAsDouble();
                        }
                    }
                } else {
                    blade.addon.utils.debug.Debug.LOGGER.warn("[Networth] skycrypt status {}", r.statusCode());
                }
            } catch (Exception ex) {
                blade.addon.utils.debug.Debug.LOGGER.warn("[Networth] error: {}", ex.getMessage());
            }
            cb.onData(nw, pname);
        }).exceptionally(t -> { cb.onData(-1, null); return null; });
    }

    public interface EconomyCallback { void onData(double bank, double purse, String corpses); }

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
}
