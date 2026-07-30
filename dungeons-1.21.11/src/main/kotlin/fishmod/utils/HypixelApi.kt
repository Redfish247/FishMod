package fishmod.utils

import fishmod.utils.config.values.FishSettings
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.MinecraftClient
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.NbtList
import net.minecraft.nbt.NbtSizeTracker
import net.minecraft.text.Text

import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.time.Duration
import java.util.ArrayList
import java.util.Collections
import java.util.HashMap
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Matcher
import java.util.regex.Pattern

object HypixelApi {

    /** Cumulative XP required for each catacombs / class level (index = level 0-50) */
    @JvmField
    val CATA_XP_TABLE: LongArray = longArrayOf(
        0L, 50L, 125L, 235L, 395L, 625L, 955L, 1_425L, 2_095L, 3_045L,
        4_385L, 6_275L, 8_940L, 12_700L, 17_960L, 25_340L, 35_640L, 50_040L, 70_040L, 97_640L,
        135_640L, 188_140L, 259_640L, 356_640L, 488_640L, 668_640L, 911_640L, 1_239_640L, 1_684_640L, 2_284_640L,
        3_084_640L, 4_149_640L, 5_559_640L, 7_459_640L, 9_959_640L, 13_259_640L, 17_559_640L, 23_159_640L, 30_359_640L, 39_559_640L,
        51_559_640L, 66_559_640L, 85_559_640L, 109_559_640L, 139_559_640L, 177_559_640L, 225_559_640L, 285_559_640L, 360_559_640L, 453_559_640L,
        569_809_640L
    )

    @JvmField
    val XP_FOR_50: Long = CATA_XP_TABLE[50]

    /** Community-standard XP cost per "overflow" level past the level-50 cap (catacombs + class curves). */
    @JvmField
    val CATA_OVERFLOW_XP_PER_LEVEL: Long = 200_000_000L

    private const val PROXY_URL = "https://fishmod.redfish2471.workers.dev"
    private const val MOD_TOKEN = "fishmod123"

    private val HTTP: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    private const val CACHE_TTL_MS = 30 * 60 * 1000L // 30 minutes
    /** lower-cased player name → UUID without dashes. Backed by an on-disk cache (see below). */
    @JvmField
    val uuidByName: MutableMap<String, String> = ConcurrentHashMap()
    /** player name → epoch-ms when DungeonData was last fetched */
    @JvmField
    val dataTimestamp: MutableMap<String, Long> = ConcurrentHashMap()

    // On-disk cache of name->UUID; entries expire after UUID_CACHE_TTL_MS so renames self-correct.
    private const val UUID_CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24h
    private val uuidCachedAt: MutableMap<String, Long> = ConcurrentHashMap()
    @Volatile
    private var uuidCacheLoaded = false
    private val uuidSavePending = java.util.concurrent.atomic.AtomicBoolean(false)

    private fun nameKey(name: String?): String {
        return name?.lowercase(java.util.Locale.ROOT) ?: ""
    }

    /** Cached UUID for a name if present and unexpired, else null. Lazily loads the on-disk cache once. */
    @JvmStatic
    fun getCachedUuid(name: String?): String? {
        ensureUuidCacheLoaded()
        val k = nameKey(name)
        val ts = uuidCachedAt[k] ?: return null
        if (System.currentTimeMillis() - ts > UUID_CACHE_TTL_MS) { // expired — drop it
            uuidByName.remove(k)
            uuidCachedAt.remove(k)
            return null
        }
        return uuidByName[k]
    }

    /** Records a fresh name→UUID mapping (from an authoritative resolve) and schedules a debounced save. */
    @JvmStatic
    fun putUuid(name: String?, uuid: String?) {
        if (name == null || uuid == null || uuid.isEmpty()) return
        ensureUuidCacheLoaded()
        val k = nameKey(name)
        uuidByName[k] = uuid
        uuidCachedAt[k] = System.currentTimeMillis()
        scheduleUuidSave()
    }

    @Synchronized
    private fun ensureUuidCacheLoaded() {
        if (uuidCacheLoaded) return
        try {
            val file = FabricLoader.getInstance().configDir.resolve("fishmod_uuid_cache.json")
            if (Files.exists(file)) {
                val root = JsonParser.parseString(Files.readString(file)).asJsonObject
                val entries = if (root.has("entries")) root.getAsJsonObject("entries") else JsonObject()
                val now = System.currentTimeMillis()
                for (e in entries.entrySet()) {
                    try {
                        val o = e.value.asJsonObject
                        val ts = o.get("ts").asLong
                        if (now - ts > UUID_CACHE_TTL_MS) continue  // stale — skip
                        val uuid = o.get("uuid").asString
                        if (uuid == null || uuid.isEmpty()) continue
                        uuidByName[e.key] = uuid
                        uuidCachedAt[e.key] = ts
                    } catch (ignored: Exception) {}
                }
            }
        } catch (ignored: Exception) {}
        // Flush on shutdown so the most recent lookups survive even a hard close.
        try { Runtime.getRuntime().addShutdownHook(Thread(Runnable { saveUuidCacheNow() }, "fishmod-uuid-cache-flush")) }
        catch (ignored: Exception) {}
        uuidCacheLoaded = true
    }

    private fun scheduleUuidSave() {
        if (!uuidSavePending.compareAndSet(false, true)) return  // a flush is already queued
        CompletableFuture.delayedExecutor(5, java.util.concurrent.TimeUnit.SECONDS)
            .execute {
                uuidSavePending.set(false); saveUuidCacheNow()
            }
    }

    @Synchronized
    private fun saveUuidCacheNow() {
        try {
            val file = FabricLoader.getInstance().configDir.resolve("fishmod_uuid_cache.json")
            val entries = JsonObject()
            val now = System.currentTimeMillis()
            for (e in uuidCachedAt.entries) {
                if (now - e.value > UUID_CACHE_TTL_MS) continue
                val uuid = uuidByName[e.key] ?: continue
                val o = JsonObject()
                o.addProperty("uuid", uuid)
                o.addProperty("ts", e.value)
                entries.add(e.key, o)
            }
            val root = JsonObject()
            root.addProperty("version", 1)
            root.add("entries", entries)
            Files.writeString(file, root.toString())
        } catch (ignored: Exception) {}
    }

    @JvmStatic
    fun loadPfCache(liveCache: MutableMap<String, DungeonData>) {
        try {
            val file = FabricLoader.getInstance().configDir.resolve("fishmod_pf_cache.json")
            if (!Files.exists(file)) return
            val root = JsonParser.parseString(Files.readString(file)).asJsonObject
            val entries = root.getAsJsonObject("entries")
            val now = System.currentTimeMillis()
            for (e in entries.entrySet()) {
                val name = e.key
                val obj = e.value.asJsonObject
                val ts = obj.get("timestamp").asLong
                if (now - ts > CACHE_TTL_MS) continue  // stale — skip
                if (obj.has("uuid")) uuidByName[name] = obj.get("uuid").asString
                dataTimestamp[name] = ts
                val d = DungeonData()
                if (obj.has("cataXp")) d.cataXp = obj.get("cataXp").asLong
                if (obj.has("cataLevel")) d.cataLevel = obj.get("cataLevel").asInt
                if (obj.has("totalSecrets")) d.totalSecrets = obj.get("totalSecrets").asLong
                if (obj.has("totalRuns")) d.totalRuns = obj.get("totalRuns").asLong
                if (obj.has("secretAverage") && !obj.get("secretAverage").isJsonNull)
                    d.secretAverage = obj.get("secretAverage").asString
                if (obj.has("cataPbs")) {
                    val arr = obj.getAsJsonArray("cataPbs")
                    for (i in 0 until minOf(arr.size(), 8))
                        d.cataPbs[i] = if (arr[i].isJsonNull) null else arr[i].asString
                }
                if (obj.has("masterPbs")) {
                    val arr = obj.getAsJsonArray("masterPbs")
                    for (i in 0 until minOf(arr.size(), 8))
                        d.masterPbs[i] = if (arr[i].isJsonNull) null else arr[i].asString
                }
                if (obj.has("cataTimes")) {
                    val arr = obj.getAsJsonArray("cataTimes")
                    for (i in 0 until minOf(arr.size(), 8))
                        d.cataTimes[i] = arr[i].asLong
                }
                if (obj.has("masterTimes")) {
                    val arr = obj.getAsJsonArray("masterTimes")
                    for (i in 0 until minOf(arr.size(), 8))
                        d.masterTimes[i] = arr[i].asLong
                }
                if (obj.has("ragnarockChimera")) d.ragnarockChimera = obj.get("ragnarockChimera").asInt
                if (obj.has("magicalPower")) d.magicalPower = obj.get("magicalPower").asInt
                if (obj.has("termUltimate") && !obj.get("termUltimate").isJsonNull) d.termUltimate = obj.get("termUltimate").asString
                if (obj.has("armorStars")) {
                    val a = obj.getAsJsonArray("armorStars")
                    if (a.size() == 4) d.armorStars = intArrayOf(a[0].asInt, a[1].asInt, a[2].asInt, a[3].asInt)
                }
                if (obj.has("equipStars")) {
                    val a = obj.getAsJsonArray("equipStars")
                    if (a.size() == 4) d.equipStars = intArrayOf(a[0].asInt, a[1].asInt, a[2].asInt, a[3].asInt)
                }
                liveCache[name] = d
            }
        } catch (ignored: Exception) {}
    }

    @JvmStatic
    fun savePfCacheAsync(liveCache: Map<String, DungeonData>) {
        CompletableFuture.runAsync {
            try {
                val file = FabricLoader.getInstance().configDir.resolve("fishmod_pf_cache.json")
                val root = JsonObject()
                val entries = JsonObject()
                for (e in liveCache.entries) {
                    val name = e.key
                    val d = e.value
                    val ts = dataTimestamp[name] ?: continue
                    val obj = JsonObject()
                    val uuid = uuidByName[name]
                    if (uuid != null) obj.addProperty("uuid", uuid)
                    obj.addProperty("timestamp", ts)
                    obj.addProperty("cataXp", d.cataXp)
                    obj.addProperty("cataLevel", d.cataLevel)
                    obj.addProperty("totalSecrets", d.totalSecrets)
                    obj.addProperty("totalRuns", d.totalRuns)
                    if (d.secretAverage != null) obj.addProperty("secretAverage", d.secretAverage)
                    else obj.add("secretAverage", JsonNull.INSTANCE)
                    val cataPbs = JsonArray()
                    for (pb in d.cataPbs) { if (pb != null) cataPbs.add(pb) else cataPbs.add(JsonNull.INSTANCE) }
                    obj.add("cataPbs", cataPbs)
                    val masterPbs = JsonArray()
                    for (pb in d.masterPbs) { if (pb != null) masterPbs.add(pb) else masterPbs.add(JsonNull.INSTANCE) }
                    obj.add("masterPbs", masterPbs)
                    val cataTimes = JsonArray()
                    for (t in d.cataTimes) cataTimes.add(t)
                    obj.add("cataTimes", cataTimes)
                    val masterTimes = JsonArray()
                    for (t in d.masterTimes) masterTimes.add(t)
                    obj.add("masterTimes", masterTimes)
                    obj.addProperty("ragnarockChimera", d.ragnarockChimera)
                    obj.addProperty("magicalPower", d.magicalPower)
                    if (d.termUltimate != null) obj.addProperty("termUltimate", d.termUltimate)
                    else obj.add("termUltimate", JsonNull.INSTANCE)
                    if (d.armorStars != null) {
                        val a = JsonArray(); for (s in d.armorStars!!) a.add(s); obj.add("armorStars", a)
                    }
                    if (d.equipStars != null) {
                        val a = JsonArray(); for (s in d.equipStars!!) a.add(s); obj.add("equipStars", a)
                    }
                    entries.add(name, obj)
                }
                root.addProperty("version", 1)
                root.add("entries", entries)
                Files.writeString(file, root.toString())
            } catch (ignored: Exception) {}
        }
    }

    class DungeonData {
        @JvmField var cataXp: Long = 0
        @JvmField var cataLevel: Int = 0       // computed from cataXp
        @JvmField var totalSecrets: Long = 0
        @JvmField var totalRuns: Long = 0
        @JvmField var secretAverage: String? = null // "9.5", null if no runs
        @JvmField var classXp: MutableMap<String, Long> = HashMap()
        // Index 0-7: 0=Entrance/E, 1-7=F1-F7 for cata; 1-7=M1-M7 for master. null = no PB.
        @JvmField var cataPbs: Array<String?> = arrayOfNulls(8)
        @JvmField var masterPbs: Array<String?> = arrayOfNulls(8)
        // Per-floor run counts: index 0-7 (0=entrance for cata, 1-7=floors)
        @JvmField var cataTimes: LongArray = LongArray(8)
        @JvmField var masterTimes: LongArray = LongArray(8)
        // Inventory-derived fields (null/–1 when API is off or item not found)
        @JvmField var ragnarockChimera: Int = -1  // Chimera enchant level on RAGNAROCK_AXE, –1 = none
        @JvmField var termUltimate: String? = null // Ultimate enchant on Terminator(s), null = none/no term
        @JvmField var armorStars: IntArray? = null // [H, C, L, B] dungeon stars; null = inventory API off
        @JvmField var equipStars: IntArray? = null // [N, CL, B, G] dungeon stars; null = inventory API off
        @JvmField var magicalPower: Int = -1   // accessory_bag_storage.magical_power, –1 = unknown
    }


    private val STRIP_COLOR: Pattern = Pattern.compile("§.")
    private val ULTIMATE_PAT: Pattern = Pattern.compile("Ultimate ([A-Za-z ]+?) ([IVX]+)$")

    private fun parseInventoryData(member: JsonObject, result: DungeonData) {
        try {
            if (!member.has("inventory")) return
            val inv = member.getAsJsonObject("inventory")

            val mainItems = parseSlots(inv, "inv_contents")
            val echestItems = parseSlots(inv, "ender_chest_contents")
            val allItems = ArrayList<NbtCompound>(mainItems.size + echestItems.size)
            for (c in mainItems) if (c != null) allItems.add(c)
            for (c in echestItems) if (c != null) allItems.add(c)

            for (item in allItems) {
                val id = getItemId(item)
                if (result.ragnarockChimera < 0 && "RAGNAROCK_AXE" == id)
                    result.ragnarockChimera = getEnchantLevel(item, "chimera")
                if (result.termUltimate == null && id != null && id.contains("TERMINATOR"))
                    result.termUltimate = getUltimateEnchant(item)
            }

            // Armor stars: inv_armor slots [0=boots, 1=legs, 2=chest, 3=head]
            val armorSlots = parseSlots(inv, "inv_armor")
            if (armorSlots.isNotEmpty()) {
                result.armorStars = intArrayOf(
                    getStarCount(if (armorSlots.size > 3) armorSlots[3] else null), // H
                    getStarCount(if (armorSlots.size > 2) armorSlots[2] else null), // C
                    getStarCount(if (armorSlots.size > 1) armorSlots[1] else null), // L
                    getStarCount(if (armorSlots.isNotEmpty()) armorSlots[0] else null)  // B
                )
            }

            // Equipment stars: equipment_contents [0=necklace, 1=cloak, 2=belt, 3=gloves]
            val equipSlots = parseSlots(inv, "equipment_contents")
            if (equipSlots.isNotEmpty()) {
                result.equipStars = intArrayOf(
                    getStarCount(if (equipSlots.isNotEmpty()) equipSlots[0] else null), // N
                    getStarCount(if (equipSlots.size > 1) equipSlots[1] else null), // CL
                    getStarCount(if (equipSlots.size > 2) equipSlots[2] else null), // B (belt)
                    getStarCount(if (equipSlots.size > 3) equipSlots[3] else null)  // G
                )
            }
        } catch (ignored: Exception) {}
    }

    private fun parseSlots(inventory: JsonObject, key: String): List<NbtCompound?> {
        try {
            if (!inventory.has(key)) return Collections.emptyList()
            val slot = inventory.getAsJsonObject(key)
            if (!slot.has("data")) return Collections.emptyList()
            val b64 = slot.get("data").asString
            if (b64.isEmpty()) return Collections.emptyList()
            val bytes = java.util.Base64.getDecoder().decode(b64)
            val root = NbtIo.readCompressed(ByteArrayInputStream(bytes), NbtSizeTracker.ofUnlimitedBytes())
            val listOpt: Optional<NbtList> = root.getList("i")
            val items = listOpt.orElse(null) ?: return Collections.emptyList()
            val out = ArrayList<NbtCompound?>(items.size)
            for (i in 0 until items.size) {
                val c = items.getCompound(i).orElse(null)
                out.add(if (c != null && !c.isEmpty) c else null)
            }
            return out
        } catch (e: Exception) {
            return Collections.emptyList()
        }
    }

    private fun getTag(item: NbtCompound?): NbtCompound? {
        if (item == null) return null
        return try {
            val el = item.get("tag") ?: return null
            el.asCompound().orElse(null)
        } catch (e: Exception) { null }
    }

    private fun getExtras(item: NbtCompound?): NbtCompound? {
        val tag = getTag(item) ?: return null
        return try {
            val el = tag.get("ExtraAttributes") ?: return null
            el.asCompound().orElse(null)
        } catch (e: Exception) { null }
    }

    private fun getItemId(item: NbtCompound?): String? {
        val extras = getExtras(item) ?: return null
        return try {
            val el = extras.get("id") ?: return null
            el.asString().orElse(null)
        } catch (e: Exception) { null }
    }

    private fun getEnchantLevel(item: NbtCompound?, enchantName: String): Int {
        val extras = getExtras(item) ?: return -1
        return try {
            val encEl = extras.get("enchantments") ?: return -1
            val enchants = encEl.asCompound().orElse(null) ?: return -1
            enchants.getInt(enchantName, -1)
        } catch (e: Exception) { -1 }
    }

    private fun getUltimateEnchant(item: NbtCompound?): String? {
        val extras = getExtras(item) ?: return null
        try {
            // Check enchantments map for "ultimate_" prefixed keys
            val encEl = extras.get("enchantments")
            if (encEl != null) {
                val enchants = encEl.asCompound().orElse(null)
                if (enchants != null) {
                    for (k in enchants.keys) {
                        if (!k.startsWith("ultimate_")) continue
                        val lvl = enchants.getInt(k, 0)
                        var name = k.substring("ultimate_".length).replace("_", " ")
                        name = Character.toUpperCase(name[0]) + name.substring(1)
                        return name + " " + toRoman(lvl)
                    }
                }
            }
            // Fallback: scan lore for "Ultimate <Name> <Level>"
            val tag = getTag(item) ?: return null
            val displayEl = tag.get("display") ?: return null
            val display = displayEl.asCompound().orElse(null) ?: return null
            val lore = display.getList("Lore").orElse(null)
            if (lore == null || lore.isEmpty) return null
            for (i in 0 until lore.size) {
                val line = STRIP_COLOR.matcher(lore.getString(i).orElse("")).replaceAll("").trim()
                val m: Matcher = ULTIMATE_PAT.matcher(line)
                if (m.find()) return m.group(1).trim() + " " + m.group(2)
            }
        } catch (ignored: Exception) {}
        return null
    }

    private fun getStarCount(item: NbtCompound?): Int {
        val extras = getExtras(item)
        if (extras != null) {
            try {
                val lvl = extras.getInt("dungeon_item_level", -1)
                if (lvl >= 0) return lvl
            } catch (ignored: Exception) {}
        }
        // Fallback: count ✪ in display name
        return try {
            val tag = getTag(item) ?: return 0
            val displayEl = tag.get("display") ?: return 0
            val display = displayEl.asCompound().orElse(null) ?: return 0
            val nameEl = display.get("Name") ?: return 0
            val name = STRIP_COLOR.matcher(nameEl.asString().orElse("")).replaceAll("")
            name.chars().filter { c -> c == '✪'.code }.count().toInt()
        } catch (e: Exception) { 0 }
    }

    private fun computeMagicalPower(member: JsonObject): Int {
        try {
            if (member.has("accessory_bag_storage")) {
                val abs = member.getAsJsonObject("accessory_bag_storage")
                if (abs.has("highest_magical_power"))
                    return abs.get("highest_magical_power").asDouble.toInt()
                if (abs.has("magical_power"))
                    return abs.get("magical_power").asDouble.toInt()
            }
        } catch (ignored: Exception) {}

        // Compute from bag NBT: parse each accessory's rarity → sum MP values
        try {
            if (!member.has("accessory_bag_storage")) return -1
            val abs = member.getAsJsonObject("accessory_bag_storage")
            val items = parseSlots(abs, "bag_storage")
            if (items.isEmpty()) return -1

            val seen = java.util.HashSet<String>()
            var total = 0
            for (item in items) {
                if (item == null) continue
                // Deduplicate by item ID — only count each accessory once
                val id = getItemId(item)
                if (id != null && !seen.add(id)) continue

                val tag = getTag(item) ?: continue
                val displayEl = tag.get("display") ?: continue
                val display = displayEl.asCompound().orElse(null) ?: continue
                val lore = display.getList("Lore").orElse(null)
                if (lore == null || lore.isEmpty) continue

                // Last non-empty lore line = "§X§lRARITY TYPE"
                for (i in lore.size - 1 downTo 0) {
                    val line = STRIP_COLOR.matcher(lore.getString(i).orElse("")).replaceAll("").trim()
                    if (line.isNotEmpty()) { total += mpForRarity(line); break }
                }
            }
            return total
        } catch (e: Exception) { return -1 }
    }

    private fun mpForRarity(rarityLine: String): Int {
        if (rarityLine.startsWith("MYTHIC")) return 22
        if (rarityLine.startsWith("LEGENDARY")) return 16
        if (rarityLine.startsWith("EPIC")) return 12
        if (rarityLine.startsWith("RARE")) return 8
        if (rarityLine.startsWith("UNCOMMON")) return 5
        if (rarityLine.startsWith("VERY SPECIAL")) return 5
        if (rarityLine.startsWith("COMMON")) return 3
        if (rarityLine.startsWith("SPECIAL")) return 3
        return 0
    }

    private fun toRoman(nIn: Int): String {
        var n = nIn
        if (n <= 0) return n.toString()
        val v = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")
        val r = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
        val sb = StringBuilder()
        for (i in r.indices) while (n >= r[i]) { sb.append(v[i]); n -= r[i] }
        return sb.toString()
    }

    fun interface DungeonDataCallback {
        fun onData(data: DungeonData)
    }


    /** Silent Party Finder lookup: Ashcon for UUID (Mojang fallback) then Hypixel profiles; always calls back. */
    @JvmStatic
    fun getByNameSilent(ign: String, callback: DungeonDataCallback) {
        // Fast path: UUID already known (in-session or on-disk cache) — skip the name→UUID lookup.
        val cachedUuid = getCachedUuid(ign)
        if (cachedUuid != null) { fetchProfilesSilent(cachedUuid, callback); return }
        // Mojang-authoritative resolve (see resolveUuid) so recycled/changed names hit the right account.
        resolveUuid(ign, 0) { uuid ->
            if (uuid == null) { callback.onData(DungeonData()); return@resolveUuid }
            fetchProfilesSilent(uuid, callback)
        }
    }

    private fun fetchProfilesSilent(uuidStr: String, callback: DungeonDataCallback) {
        val req: HttpRequest
        try {
            req = HttpRequest.newBuilder()
                .uri(URI.create("$PROXY_URL/skyblock/profiles?uuid=$uuidStr"))
                .header("X-FishMod-Token", MOD_TOKEN)
                .header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build()
        } catch (e: Exception) { callback.onData(DungeonData()); return }
        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
            .thenAccept { resp ->
                if (friendlyProxyError(resp) != null) { callback.onData(DungeonData()); return@thenAccept }
                try {
                    val root = JsonParser.parseString(resp.body()).asJsonObject
                    if (!root.get("success").asBoolean) { callback.onData(DungeonData()); return@thenAccept }
                    for (profileEl in root.getAsJsonArray("profiles")) {
                        val profile = profileEl.asJsonObject
                        if (!profile.has("selected") || !profile.get("selected").asBoolean) continue
                        val members = profile.getAsJsonObject("members")
                        if (!members.has(uuidStr)) continue
                        val member = members.getAsJsonObject(uuidStr)
                        if (!member.has("dungeons")) continue
                        callback.onData(parseDungeonData(uuidStr, member))
                        return@thenAccept
                    }
                } catch (ignored: Exception) {}
                callback.onData(DungeonData())
            }
            .exceptionally { callback.onData(DungeonData()); null }
    }

    /** Look up by IGN: Mojang UUID lookup → Hypixel profiles. */
    @JvmStatic
    fun getByName(mc: MinecraftClient, ign: String, callback: DungeonDataCallback) {
        if (!checkKey(mc)) return
        mc.send { Misc.addChatMessage(Text.literal("§7Looking up $ign...")) }
        resolveUuid(ign, 0) { uuid ->
            if (uuid == null) {
                mc.send { Misc.addChatMessage(Text.literal("§cPlayer not found: $ign")) }
                return@resolveUuid
            }
            fetchProfiles(mc, uuid, callback)
        }
    }

    /** Resolves IGN to UUID, preferring Mojang (authoritative) over Ashcon/playerdb to avoid stale-mirror rename bugs. */
    private fun resolveUuid(ign: String, attempt: Int, cb: java.util.function.Consumer<String?>) {
        if (attempt == 0) {
            val cached = getCachedUuid(ign)
            if (cached != null) { cb.accept(cached); return }
        }
        val url: String
        val next: Int
        when (attempt) {
            0 -> { url = "https://api.mojang.com/users/profiles/minecraft/$ign"; next = 1 }
            1 -> { url = "https://api.ashcon.app/mojang/v2/user/$ign"; next = 2 }
            2 -> { url = "https://playerdb.co/api/player/minecraft/$ign"; next = 3 }
            else -> { cb.accept(null); return }
        }
        val req: HttpRequest
        try {
            req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "FishMod/1.0")
                .GET()
                .build()
        } catch (e: Exception) { resolveUuid(ign, next, cb); return }

        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
            .thenAccept { resp ->
                val code = resp.statusCode()
                val uuid = if (code in 200..299) parseUuid(resp.body()) else null
                if (uuid != null) { putUuid(ign, uuid); cb.accept(uuid); return@thenAccept }
                // Only fall back to mirrors when Mojang itself couldn't answer; a clean not-found is authoritative.
                val transientErr = code == 429 || code == 408 || code >= 500
                if (attempt == 0 && !transientErr) { cb.accept(null); return@thenAccept }
                resolveUuid(ign, next, cb)
            }
            .exceptionally { resolveUuid(ign, next, cb); null }
    }

    /** Public async name→UUID resolver (Mojang-authoritative, mirror fallback). UUID is dash-less. */
    @JvmStatic
    fun resolveUuidAsync(ign: String, cb: java.util.function.Consumer<String?>) {
        resolveUuid(ign, 0, cb)
    }

    /** Extracts a dash-less UUID from a Mojang / Ashcon / playerdb name-lookup body, or null. */
    private fun parseUuid(body: String?): String? {
        return try {
            val obj = JsonParser.parseString(body).asJsonObject
            var uuid: String? = null
            if (obj.has("id")) uuid = obj.get("id").asString  // mojang
            else if (obj.has("uuid")) uuid = obj.get("uuid").asString  // ashcon
            else if (obj.has("data")) {                                     // playerdb
                val player = obj.getAsJsonObject("data").getAsJsonObject("player")
                if (player.has("id")) uuid = player.get("id").asString
            }
            if (uuid != null && uuid.isNotEmpty()) uuid.replace("-", "") else null
        } catch (e: Exception) { null }
    }

    /** Look up by local player's own UUID (no Mojang step needed). */
    @JvmStatic
    fun getPlayerDungeonData(mc: MinecraftClient, callback: DungeonDataCallback) {
        if (!checkKey(mc)) return
        if (mc.player == null) return
        val uuid = mc.player!!.uuid.toString().replace("-", "")
        mc.send { Misc.addChatMessage(Text.literal("§7Fetching Hypixel data...")) }
        fetchProfiles(mc, uuid, callback)
    }


    private fun parseDungeonData(uuidStr: String, member: JsonObject): DungeonData {
        val result = DungeonData()
        if (!member.has("dungeons")) return result
        val dungeons = member.getAsJsonObject("dungeons")

        if (dungeons.has("dungeon_types")) {
            val types = dungeons.getAsJsonObject("dungeon_types")
            if (types.has("catacombs")) {
                val cata = types.getAsJsonObject("catacombs")
                if (cata.has("experience"))
                    result.cataXp = cata.get("experience").asLong
                for (f in 0..7)
                    result.cataPbs[f] = extractFloorPb(cata, f)
            }

            // tier_completions = completions; times_played = attempts including fails (master_catacombs only has the former).
            var totalRuns = 0L
            if (types.has("catacombs")) {
                val dt = types.getAsJsonObject("catacombs")
                val countField = if (dt.has("tier_completions")) "tier_completions" else "times_played"
                if (dt.has(countField)) {
                    for (e in dt.getAsJsonObject(countField).entrySet()) {
                        val f = parseFloorKey(e.key)
                        if (f < 0) continue
                        val v = e.value.asLong
                        if (f <= 7) result.cataTimes[f] = v
                        totalRuns += v
                    }
                }
            }
            if (types.has("master_catacombs")) {
                val dt = types.getAsJsonObject("master_catacombs")
                val countField = if (dt.has("tier_completions")) "tier_completions" else "times_played"
                if (dt.has(countField)) {
                    for (e in dt.getAsJsonObject(countField).entrySet()) {
                        val f = parseFloorKey(e.key)
                        if (f < 0) continue
                        val v = e.value.asLong
                        if (f in 1..7) result.masterTimes[f] = v
                        totalRuns += v
                    }
                }
            }
            val totalSecrets = if (dungeons.has("secrets")) dungeons.get("secrets").asLong else 0L
            result.totalSecrets = totalSecrets
            result.totalRuns = totalRuns
            if (totalRuns > 0) {
                result.secretAverage = String.format("%.1f", totalSecrets.toDouble() / totalRuns)
            }
            result.cataLevel = calcCataLevel(result.cataXp)

            if (types.has("master_catacombs")) {
                val mc2 = types.getAsJsonObject("master_catacombs")
                for (f in 1..7)
                    result.masterPbs[f] = extractFloorPb(mc2, f)
            }
        }

        if (dungeons.has("player_classes")) {
            val classes = dungeons.getAsJsonObject("player_classes")
            for (cls in arrayOf("healer", "mage", "berserk", "archer", "tank")) {
                if (classes.has(cls)) {
                    val c = classes.getAsJsonObject(cls)
                    if (c.has("experience"))
                        result.classXp[cls] = c.get("experience").asLong
                }
            }
        }

        parseInventoryData(member, result)

        result.magicalPower = computeMagicalPower(member)

        return result
    }

    @JvmStatic
    fun calcCataLevel(xp: Long): Int {
        for (i in CATA_XP_TABLE.size - 1 downTo 0) {
            if (xp >= CATA_XP_TABLE[i]) {
                if (i == CATA_XP_TABLE.size - 1)
                    return (i + (xp - CATA_XP_TABLE[i]) / CATA_OVERFLOW_XP_PER_LEVEL).toInt()
                return i
            }
        }
        return 0
    }

    private fun extractFloorPb(dungeonType: JsonObject, floor: Int): String? {
        val floorKey = floor.toString() // Hypixel API uses "7" not "floor_7"
        for (pair in arrayOf(arrayOf("fastest_time_s_plus", "S+"), arrayOf("fastest_time_s", "S"))) {
            if (dungeonType.has(pair[0])) {
                val times = dungeonType.getAsJsonObject(pair[0])
                if (times.has(floorKey)) {
                    val ms = times.get(floorKey).asLong
                    val s = ms / 1000
                    return String.format("%d:%02d %s", s / 60, s % 60, pair[1])
                }
            }
        }
        return null
    }

    /** Parses a Hypixel floor key like "7" or "floor_7" → floor number, or -1 if unrecognised. */
    @JvmStatic
    fun parseFloorKey(key: String): Int {
        try {
            if (key.matches(Regex("\\d+"))) return key.toInt()
            if (key.startsWith("floor_")) return key.substring(6).toInt()
        } catch (ignored: NumberFormatException) {}
        return -1
    }

    private fun checkKey(mc: MinecraftClient): Boolean {
        return true // API key no longer needed — requests go through proxy
    }

    private fun fetchProfiles(mc: MinecraftClient, uuidStr: String, callback: DungeonDataCallback) {
        val req: HttpRequest
        try {
            req = HttpRequest.newBuilder()
                .uri(URI.create("$PROXY_URL/skyblock/profiles?uuid=$uuidStr"))
                .header("X-FishMod-Token", MOD_TOKEN)
                .header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build()
        } catch (e: Exception) { return }

        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
            .thenAccept { resp ->
                val friendly = friendlyProxyError(resp)
                if (friendly != null) {
                    mc.send { Misc.addChatMessage(Text.literal(friendly)) }
                    return@thenAccept
                }
                try {
                    val root = JsonParser.parseString(resp.body()).asJsonObject
                    if (!root.get("success").asBoolean) {
                        mc.send { Misc.addChatMessage(Text.literal("§cAPI error — proxy rejected request.")) }
                        return@thenAccept
                    }
                    for (profileEl in root.getAsJsonArray("profiles")) {
                        val profile = profileEl.asJsonObject
                        if (!profile.has("selected") || !profile.get("selected").asBoolean) continue
                        val members = profile.getAsJsonObject("members")
                        if (!members.has(uuidStr)) continue
                        val member = members.getAsJsonObject(uuidStr)
                        if (!member.has("dungeons")) continue
                        callback.onData(parseDungeonData(uuidStr, member))
                        return@thenAccept
                    }
                    mc.send { Misc.addChatMessage(Text.literal("§cNo active Skyblock profile found.")) }
                } catch (e: Exception) {
                    mc.send { Misc.addChatMessage(Text.literal("§cAPI parse error: " + e.message)) }
                }
            }
            .exceptionally { mc.send { Misc.addChatMessage(Text.literal("§cAPI request failed.")) }; null }
    }

    /** User-friendly message if the proxy response isn't parseable JSON, else null. */
    private fun friendlyProxyError(resp: HttpResponse<String>): String? {
        val code = resp.statusCode()
        val body = resp.body()
        val trimmed = body?.trim() ?: ""
        val looksJson = trimmed.startsWith("{") || trimmed.startsWith("[")
        if (code in 200..299 && looksJson) return null
        if (code == 429 || trimmed.contains("error code: 1015") || trimmed.contains("error code: 1027"))
            return "§cHypixel proxy is rate-limited (429) — try again in a bit."
        if (code == 502 || code == 503 || code == 504)
            return "§cHypixel proxy unreachable ($code) — try again shortly."
        if (code >= 400) return "§cHypixel proxy error (HTTP $code)."
        if (!looksJson) return "§cHypixel proxy returned a non-JSON response (HTTP $code)."
        return null
    }

    /** Dumps all top-level keys of the member object to chat — used to find which fields the proxy returns. */
    @JvmStatic
    fun dumpMemberKeys(mc: MinecraftClient, ign: String) {
        mc.send { Misc.addChatMessage(Text.literal("§7Looking up $ign (raw)...")) }
        val uuidReq: HttpRequest
        try {
            uuidReq = HttpRequest.newBuilder()
                .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/$ign"))
                .timeout(Duration.ofSeconds(10)).GET().build()
        } catch (e: Exception) { return }
        HTTP.sendAsync(uuidReq, HttpResponse.BodyHandlers.ofString()).thenAccept { ur ->
            try {
                val uuid = JsonParser.parseString(ur.body()).asJsonObject.get("id").asString
                val req = HttpRequest.newBuilder()
                    .uri(URI.create("$PROXY_URL/skyblock/profiles?uuid=$uuid"))
                    .header("X-FishMod-Token", MOD_TOKEN).header("User-Agent", "Mozilla/5.0")
                    .timeout(Duration.ofSeconds(10)).GET().build()
                HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept { r ->
                    try {
                        val root = JsonParser.parseString(r.body()).asJsonObject
                        for (profileEl in root.getAsJsonArray("profiles")) {
                            val profile = profileEl.asJsonObject
                            if (!profile.has("selected") || !profile.get("selected").asBoolean) continue
                            val member = profile.getAsJsonObject("members").getAsJsonObject(uuid)
                            mc.send {
                                Misc.addChatMessage(Text.literal("§b--- accessory_bag_storage keys ---"))
                                if (member.has("accessory_bag_storage")) {
                                    val abs = member.getAsJsonObject("accessory_bag_storage")
                                    for (key in abs.keySet())
                                        Misc.addChatMessage(Text.literal("§7$key = " + abs.get(key).toString().substring(0, minOf(60, abs.get(key).toString().length))))
                                } else {
                                    Misc.addChatMessage(Text.literal("§cmissing"))
                                }
                                Misc.addChatMessage(Text.literal("§b--- End ---"))
                            }
                            return@thenAccept
                        }
                    } catch (e: Exception) {
                        mc.send { Misc.addChatMessage(Text.literal("§cParse error: " + e.message)) }
                    }
                }
            } catch (e: Exception) {
                mc.send { Misc.addChatMessage(Text.literal("§cUUID error: " + e.message)) }
            }
        }
    }

    fun interface NetworthCallback { fun onData(networth: Double, profileName: String?) }

    // SkyHelper public price list (item/pet/modifier prices). Cached.
    private val NW_PRICES: MutableMap<String, Double> = ConcurrentHashMap()
    @Volatile
    private var nwPricesAt: Long = 0

    private val NW_STORAGES = arrayOf(
        "inv_contents", "inv_armor", "ender_chest_contents", "equipment_contents",
        "personal_vault_contents", "talisman_bag", "wardrobe_contents", "fishing_bag", "potion_bag", "quiver", "candy_inventory_contents"
    )

    /** Computes networth via the proxy + SkyHelper price list: liquid + items + pets (estimate). */
    @JvmStatic
    fun getNetworth(mc: MinecraftClient, ign: String, cb: NetworthCallback) {
        CompletableFuture.runAsync {
            val uuid = resolveUuidBlocking(ign)
            if (uuid == null) { cb.onData(-1.0, null); return@runAsync }
            getNetworthLocal(uuid, cb)
        }
    }

    /** Client-side networth estimate using the SkyHelper price list. */
    private fun getNetworthLocal(uuid: String, cb: NetworthCallback) {
        try {
            fishmod.utils.networth.ItemsDb.ensureLoaded()
            val prices = nwPrices()

            val req = HttpRequest.newBuilder()
                .uri(URI.create("$PROXY_URL/skyblock/profiles?uuid=$uuid"))
                .header("X-FishMod-Token", MOD_TOKEN).header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(12)).GET().build()
            val r = HTTP.send(req, HttpResponse.BodyHandlers.ofString())
            val root = JsonParser.parseString(r.body()).asJsonObject
            if (!root.has("success") || !root.get("success").asBoolean || !root.has("profiles")) {
                cb.onData(-1.0, null); return
            }
            var chosen: JsonObject? = null
            for (pe in root.getAsJsonArray("profiles")) {
                val p = pe.asJsonObject
                if (p.has("selected") && p.get("selected").asBoolean) { chosen = p; break }
                if (chosen == null) chosen = p
            }
            if (chosen == null) { cb.onData(-1.0, null); return }
            val pname = if (chosen.has("cute_name")) chosen.get("cute_name").asString else null
            val member = chosen.getAsJsonObject("members").getAsJsonObject(uuid)

            var total = 0.0
            if (chosen.has("banking") && chosen.getAsJsonObject("banking").has("balance"))
                total += chosen.getAsJsonObject("banking").get("balance").asDouble
            if (member.has("currencies") && member.getAsJsonObject("currencies").has("coin_purse"))
                total += member.getAsJsonObject("currencies").get("coin_purse").asDouble
            else if (member.has("coin_purse")) total += member.get("coin_purse").asDouble

            if (member.has("inventory")) {
                val inv = member.getAsJsonObject("inventory")
                for (k in NW_STORAGES) total += sumStorageNw(inv, k, prices)
                if (inv.has("backpack_contents") && inv.get("backpack_contents").isJsonObject) {
                    val bp = inv.getAsJsonObject("backpack_contents")
                    for (k in bp.keySet()) total += sumStorageNw(bp, k, prices)
                }
                // Accessory/fishing/potion/quiver/sacks bags live under bag_contents, not the inventory top level.
                if (inv.has("bag_contents") && inv.get("bag_contents").isJsonObject) {
                    val bags = inv.getAsJsonObject("bag_contents")
                    for (k in bags.keySet()) total += sumStorageNw(bags, k, prices)
                }
            }
            total += petsValueNw(member, prices)
            total += sacksValueNw(member, prices)
            total += essenceValueNw(member, prices)
            total += shardsValueNw(member, prices)

            cb.onData(total, pname)
        } catch (ex: Exception) {
            fishmod.utils.debug.Debug.LOGGER.warn("[Networth] error: {}", ex.toString())
            cb.onData(-1.0, null)
        }
    }

    @Synchronized
    private fun nwPrices(): Map<String, Double> {
        if (System.currentTimeMillis() - nwPricesAt < 10 * 60 * 1000L && NW_PRICES.isNotEmpty()) return NW_PRICES
        try {
            val req = HttpRequest.newBuilder()
                .uri(URI.create("https://raw.githubusercontent.com/SkyHelperBot/Prices/main/pricesV2.json"))
                .timeout(Duration.ofSeconds(20)).GET().build()
            val r = HTTP.send(req, HttpResponse.BodyHandlers.ofString())
            if (r.statusCode() == 200) {
                val o = JsonParser.parseString(r.body()).asJsonObject
                NW_PRICES.clear()
                for (e in o.entrySet()) {
                    try { NW_PRICES[e.key] = e.value.asDouble } catch (ignored: Exception) {}
                }
                nwPricesAt = System.currentTimeMillis()
            }
        } catch (e: Exception) { fishmod.utils.debug.Debug.LOGGER.warn("[Networth] prices fetch: {}", e.message) }
        return NW_PRICES
    }

    private fun price(p: Map<String, Double>, key: String): Double {
        return p[key] ?: 0.0
    }

    private fun sumStorageNw(inventory: JsonObject, key: String, prices: Map<String, Double>): Double {
        var total = 0.0
        for (item in parseSlots(inventory, key)) {
            if (item != null) total += itemValueNw(item, prices)
        }
        return total
    }

    /** Per-item modifier valuation, ported from SkyHelper-Networth; each modifier is try/catch-isolated. */
    private fun itemValueNw(item: NbtCompound, prices: Map<String, Double>): Double {
        val ex = getExtras(item) ?: return 0.0
        val id = getItemId(item) ?: return 0.0

        var count = 1
        try { count = item.getInt("Count", 1); if (count <= 0) count = 1 } catch (ignored: Exception) {}
        // Some lists store Count as byte; fall back gracefully.

        // Item metadata (category / gemstone_slots / upgrade_costs / prestige) for handlers that need it.
        val meta: com.google.gson.JsonObject? = fishmod.utils.networth.ItemsDb.get(id)
        var category = ""
        try { if (meta != null && meta.has("category")) category = meta.get("category").asString } catch (ignored: Exception) {}

        // ---- Base price (ported getItemId): skin / shiny / starred / cake / rune variants ----
        var priceId = id
        try {
            val skin = ex.getString("skin", "")
            if (skin.isNotEmpty()) {
                val skinned = id + "_SKINNED_" + skin
                if (price(prices, skinned) > price(prices, id)) priceId = skinned
            }
            // Rune item -> RUNE_<type>_<tier>
            if ("RUNE" == id || "UNIQUE_RUNE" == id) {
                val runes = compound(ex, "runes")
                if (runes != null) for (rn in runes.keys) {
                    priceId = ("RUNE_" + rn + "_" + runes.getInt(rn, 0)).uppercase()
                    break
                }
            }
            if ("NEW_YEAR_CAKE" == id) {
                val cake = ex.getInt("new_years_cake", 0)
                priceId = "NEW_YEAR_CAKE_$cake"
            }
            // Shiny variant
            if (ex.getInt("is_shiny", 0) > 0 && price(prices, id + "_SHINY") > 0) priceId = id + "_SHINY"
            // Fragged: STARRED_ fallback to base
            if (id.startsWith("STARRED_") && price(prices, id) == 0.0 && price(prices, id.replace("STARRED_", "")) > 0)
                priceId = id.replace("STARRED_", "")
        } catch (ignored: Exception) {}

        val base = price(prices, priceId) * count
        var v = base

        // ---- Crown of Avarice: collected coins interpolate base between 0 and 1B price ----
        try {
            if ("CROWN_OF_AVARICE" == id) {
                var cc: Long
                try { cc = ex.getLong("collected_coins", 0L) }
                catch (e: Exception) { cc = ex.getDouble("collected_coins", 0.0).toLong() }
                if (cc > 0) {
                    val zero = price(prices, "CROWN_OF_AVARICE")
                    val bil = price(prices, "CROWN_OF_AVARICE_1B")
                    val coins = minOf(cc.toDouble(), 1_000_000_000.0)
                    val newBase = zero + (bil - zero) * (coins / 1_000_000_000.0)
                    v += (newBase - base) // SkyHelper replaces the base price with the interpolated value
                }
            }
        } catch (ignored: Exception) {}

        // ---- Recombobulator x0.8 ----
        try {
            val isRecomb = ex.getInt("rarity_upgrades", 0) > 0 && ex.getInt("item_tier", -1) < 0
                    && !ex.contains("item_tier")
            if (isRecomb) {
                val hasEnch = compound(ex, "enchantments") != null
                val allows = fishmod.utils.networth.NwConstants.ALLOWED_RECOMBOBULATED_CATEGORIES.contains(category)
                        || fishmod.utils.networth.NwConstants.ALLOWED_RECOMBOBULATED_IDS.contains(id)
                if (hasEnch || allows) {
                    val w = if ("BONE_BOOMERANG" == id)
                        fishmod.utils.networth.NwConstants.RECOMBOBULATOR * 0.5
                    else
                        fishmod.utils.networth.NwConstants.RECOMBOBULATOR
                    v += price(prices, "RECOMBOBULATOR_3000") * w
                }
            }
        } catch (ignored: Exception) {}

        // ---- Potato books ----
        try {
            val hpc = ex.getInt("hot_potato_count", 0)
            if (hpc > 0) {
                v += price(prices, "HOT_POTATO_BOOK") * minOf(hpc, 10) * fishmod.utils.networth.NwConstants.HOT_POTATO_BOOK
                if (hpc > 10) v += price(prices, "FUMING_POTATO_BOOK") * (hpc - 10) * fishmod.utils.networth.NwConstants.FUMING_POTATO_BOOK
            }
        } catch (ignored: Exception) {}

        // ---- Enchantments (EnchantedBook items valued differently) ----
        try {
            val enc = compound(ex, "enchantments")
            if (enc != null && enc.keys.isNotEmpty()) {
                if ("ENCHANTED_BOOK" == id) {
                    val single = enc.keys.size == 1
                    var bookPrice = 0.0
                    for (name in enc.keys) {
                        val lvl = enc.getInt(name, 0)
                        val p = price(prices, "ENCHANTMENT_" + name.uppercase() + "_" + lvl)
                        if (p == 0.0) continue
                        bookPrice += p * (if (single) 1.0 else fishmod.utils.networth.NwConstants.ENCHANTMENTS)
                    }
                    if (bookPrice > 0) v += bookPrice // replaces basePrice (which is ~0 for the book item)
                } else {
                    v += enchantmentsValueNw(id, enc, prices)
                }
            }
        } catch (ignored: Exception) {}

        // ---- Gemstones (gems themselves + unlock slot costs for Divan/Crimson armor) ----
        try { v += gemsValueNw(id, ex, meta, prices) } catch (ignored: Exception) {}

        // ---- Master Stars (stars 6-10) ----
        try {
            val up = upgradeLevel(ex)
            if (meta != null && meta.has("upgrade_costs") && up > 5) {
                val starsUsed = minOf(up - 5, 5)
                if (meta.getAsJsonArray("upgrade_costs").size() <= 5) {
                    for (s in 0 until starsUsed)
                        v += price(prices, fishmod.utils.networth.NwConstants.MASTER_STARS[s]) * fishmod.utils.networth.NwConstants.MASTER_STAR
                }
            }
        } catch (ignored: Exception) {}

        // ---- Essence Stars ----
        try {
            val up = upgradeLevel(ex)
            if (meta != null && meta.has("upgrade_costs") && up > 0) {
                v += starCostsNw(meta.getAsJsonArray("upgrade_costs"), up, prices, false)
            }
        } catch (ignored: Exception) {}

        // ---- Prestige ----
        try {
            val chain = fishmod.utils.networth.NwConstants.PRESTIGES[id]
            if (chain != null && price(prices, id) == 0.0) {
                for (pItem in chain) {
                    val pMeta: com.google.gson.JsonObject? = fishmod.utils.networth.ItemsDb.get(pItem)
                    if (pMeta != null && pMeta.has("upgrade_costs"))
                        v += starCostsNw(pMeta.getAsJsonArray("upgrade_costs"), pMeta.getAsJsonArray("upgrade_costs").size(), prices, true)
                    if (pMeta != null && pMeta.has("prestige") && pMeta.getAsJsonObject("prestige").has("costs"))
                        v += starCostsNw(pMeta.getAsJsonObject("prestige").getAsJsonArray("costs"),
                            pMeta.getAsJsonObject("prestige").getAsJsonArray("costs").size(), prices, true)
                    if (price(prices, pItem) > 0) { v += price(prices, pItem); break }
                }
            }
        } catch (ignored: Exception) {}

        // ---- Reforge x1 (not for accessories) ----
        try {
            val modifier = ex.getString("modifier", "")
            if (modifier.isNotEmpty() && "ACCESSORY" != category) {
                val stone = fishmod.utils.networth.NwConstants.REFORGES[modifier]
                if (stone != null) v += price(prices, stone) * fishmod.utils.networth.NwConstants.REFORGE
            }
        } catch (ignored: Exception) {}

        // ---- Art of War x0.6 ----
        try { val c = ex.getInt("art_of_war_count", 0); if (c > 0) v += price(prices, "THE_ART_OF_WAR") * c * fishmod.utils.networth.NwConstants.ART_OF_WAR } catch (ignored: Exception) {}
        // ---- Art of Peace x0.8 ----
        try { val c = ex.getInt("artOfPeaceApplied", 0); if (c > 0) v += price(prices, "THE_ART_OF_PEACE") * c * fishmod.utils.networth.NwConstants.ART_OF_PEACE } catch (ignored: Exception) {}
        // ---- Necron-blade ability scrolls x1 ----
        try {
            val scrolls = ex.getList("ability_scroll").orElse(null)
            if (scrolls != null) for (i in 0 until scrolls.size)
                v += price(prices, scrolls.getString(i).orElse("").uppercase()) * fishmod.utils.networth.NwConstants.NECRON_BLADE_SCROLL
        } catch (ignored: Exception) {}
        // ---- Gemstone power scroll x0.5 ----
        try { val ps = ex.getString("power_ability_scroll", ""); if (ps.isNotEmpty()) v += price(prices, ps) * fishmod.utils.networth.NwConstants.GEMSTONE_POWER_SCROLL } catch (ignored: Exception) {}
        // ---- Drill parts x1 ----
        try {
            for (part in arrayOf("drill_part_upgrade_module", "drill_part_fuel_tank", "drill_part_engine")) {
                val pid = ex.getString(part, "")
                if (pid.isNotEmpty()) v += price(prices, pid.uppercase()) * fishmod.utils.networth.NwConstants.DRILL_PART
            }
        } catch (ignored: Exception) {}
        // ---- Rod parts x1 (line/hook/sinker -> compound with `part`) ----
        try {
            for (part in arrayOf("line", "hook", "sinker")) {
                val pc = compound(ex, part)
                if (pc != null) {
                    val pp = pc.getString("part", "")
                    if (pp.isNotEmpty()) v += price(prices, pp.uppercase()) * fishmod.utils.networth.NwConstants.ROD_PART
                }
            }
        } catch (ignored: Exception) {}
        // ---- Etherwarp conduit x1 ----
        try { if (ex.getInt("ethermerge", 0) > 0) v += price(prices, "ETHERWARP_CONDUIT") * fishmod.utils.networth.NwConstants.ETHERWARP } catch (ignored: Exception) {}
        // ---- Transmission tuner x0.7 ----
        try { val tt = ex.getInt("tuned_transmission", 0); if (tt > 0) v += price(prices, "TRANSMISSION_TUNER") * tt * fishmod.utils.networth.NwConstants.TUNED_TRANSMISSION } catch (ignored: Exception) {}
        // ---- Wood singularity x0.5 ----
        try { val c = ex.getInt("wood_singularity_count", 0); if (c > 0) v += price(prices, "WOOD_SINGULARITY") * c * fishmod.utils.networth.NwConstants.WOOD_SINGULARITY } catch (ignored: Exception) {}
        // ---- Jalapeno book x0.8 ----
        try { val c = ex.getInt("jalapeno_count", 0); if (c > 0) v += price(prices, "JALAPENO_BOOK") * c * fishmod.utils.networth.NwConstants.JALAPENO_BOOK } catch (ignored: Exception) {}
        // ---- Mana disintegrator x0.8 ----
        try { val c = ex.getInt("mana_disintegrator_count", 0); if (c > 0) v += price(prices, "MANA_DISINTEGRATOR") * c * fishmod.utils.networth.NwConstants.MANA_DISINTEGRATOR } catch (ignored: Exception) {}
        // ---- Farming for dummies x0.5 ----
        try { val c = ex.getInt("farming_for_dummies_count", 0); if (c > 0) v += price(prices, "FARMING_FOR_DUMMIES") * c * fishmod.utils.networth.NwConstants.FARMING_FOR_DUMMIES } catch (ignored: Exception) {}
        // ---- Overclocker 3000 x0.9 ----
        try { val c = ex.getInt("levelable_overclocks", 0); if (c > 0) v += price(prices, "OVERCLOCKER_3000") * c * fishmod.utils.networth.NwConstants.OVERCLOCKER_3000 } catch (ignored: Exception) {}
        // ---- Polarvoid book x1 ----
        try { val c = ex.getInt("polarvoid", 0); if (c > 0) v += price(prices, "POLARVOID_BOOK") * c * fishmod.utils.networth.NwConstants.POLARVOID_BOOK } catch (ignored: Exception) {}
        // ---- Pocket sack-in-a-sack x0.7 ----
        try { val c = ex.getInt("sack_pss", 0); if (c > 0) v += price(prices, "POCKET_SACK_IN_A_SACK") * c * fishmod.utils.networth.NwConstants.POCKET_SACK_IN_A_SACK } catch (ignored: Exception) {}
        // ---- Divan powder coating x0.8 ----
        try { val c = ex.getInt("divan_powder_coating", 0); if (c > 0) v += price(prices, "DIVAN_POWDER_COATING") * fishmod.utils.networth.NwConstants.DIVAN_POWDER_COATING } catch (ignored: Exception) {}
        // ---- Dye x0.9 ----
        try { val dye = ex.getString("dye_item", ""); if (dye.isNotEmpty()) v += price(prices, dye.uppercase()) * fishmod.utils.networth.NwConstants.DYE } catch (ignored: Exception) {}
        // ---- Runes x0.6 (only on non-rune items) ----
        try {
            val runes = compound(ex, "runes")
            if (runes != null && !id.startsWith("RUNE")) for (rn in runes.keys) {
                val runeId = "RUNE_" + rn + "_" + runes.getInt(rn, 0)
                v += price(prices, runeId.uppercase()) * fishmod.utils.networth.NwConstants.RUNES
                break
            }
        } catch (ignored: Exception) {}
        // ---- Enrichment x0.5 (cheapest enrichment) ----
        try {
            val enr = ex.getString("talisman_enrichment", "")
            if (enr.isNotEmpty()) {
                var cheapest = Double.POSITIVE_INFINITY
                for (e in fishmod.utils.networth.NwConstants.ENRICHMENTS) {
                    val p = price(prices, e)
                    if (p > 0) cheapest = minOf(cheapest, p)
                }
                if (cheapest != Double.POSITIVE_INFINITY) v += cheapest * fishmod.utils.networth.NwConstants.ENRICHMENT
            }
        } catch (ignored: Exception) {}
        // ---- Boosters x0.8 ----
        try {
            val boosters = ex.getList("boosters").orElse(null)
            if (boosters != null) for (i in 0 until boosters.size) {
                val b = boosters.getString(i).orElse("")
                if (b.isNotEmpty()) v += price(prices, b.uppercase() + "_BOOSTER") * fishmod.utils.networth.NwConstants.BOOSTER
            }
        } catch (ignored: Exception) {}
        // ---- New Year Cake Bag (sum of contained cakes, x1) ----
        try {
            val years = ex.getList("new_year_cake_bag_years").orElse(null)
            if (years != null) for (i in 0 until years.size)
                v += price(prices, "NEW_YEAR_CAKE_" + years.getInt(i).orElse(0))
        } catch (ignored: Exception) {}
        // ---- Shen's Auction (price paid x0.85, replaces base if higher) ----
        try {
            if (ex.contains("price") && ex.contains("auction") && ex.contains("bid")) {
                val pricePaid = ex.getDouble("price", 0.0) * fishmod.utils.networth.NwConstants.SHENS_AUCTION_PRICE
                if (pricePaid > base) v += (pricePaid - base)
            }
        } catch (ignored: Exception) {}
        // ---- Midas weapon (max-bid variant replaces base) ----
        try {
            val midas = fishmod.utils.networth.NwConstants.MIDAS_SWORDS[id]
            if (midas != null) {
                val maxBid = midas[0] as Long
                val type = midas[1] as String
                val winning = ex.getDouble("winning_bid", 0.0)
                val additional = ex.getDouble("additional_coins", 0.0)
                if (winning + additional >= maxBid && price(prices, type) > 0)
                    v += (price(prices, type) - base)
            }
        } catch (ignored: Exception) {}
        // ---- Pickonimbus (durability reduces base) ----
        try {
            if ("PICKONIMBUS" == id) {
                val dur = ex.getInt("pickonimbus_durability", 5000)
                if (dur < 5000) v += base * ((dur / 5000.0) - 1)
            }
        } catch (ignored: Exception) {}

        // ---- BONUS (not in SkyHelper): item attributes -> ATTRIBUTE_SHARD_<NAME> x 2^(level-1) ----
        try {
            val att = compound(ex, "attributes")
            if (att != null) for (an in att.keys) {
                val lvl = att.getInt(an, 0)
                if (lvl > 0) {
                    val sp = price(prices, "ATTRIBUTE_SHARD_" + an.uppercase())
                    if (sp > 0) v += sp * Math.pow(2.0, (lvl - 1).toDouble())
                }
            }
        } catch (ignored: Exception) {}

        return v
    }

    private fun compound(parent: NbtCompound, key: String): NbtCompound? {
        return try {
            val el = parent.get(key) ?: return null
            el.asCompound().orElse(null)
        } catch (e: Exception) { null }
    }

    /** dungeon_item_level / upgrade_level, stripped of non-digits, max of the two. */
    private fun upgradeLevel(ex: NbtCompound): Int {
        val dil = digits(strOrInt(ex, "dungeon_item_level"))
        val ul = digits(strOrInt(ex, "upgrade_level"))
        return maxOf(dil, ul)
    }
    private fun strOrInt(ex: NbtCompound, key: String): String {
        return try {
            val el = ex.get(key) ?: return "0"
            val s = el.asString().orElse(null)
            s ?: ex.getInt(key, 0).toString()
        } catch (e: Exception) { "0" }
    }
    private fun digits(s: String): Int {
        val b = StringBuilder()
        for (c in s.toCharArray()) if (Character.isDigit(c)) b.append(c)
        return try { if (b.isEmpty()) 0 else b.toString().toInt() } catch (e: Exception) { 0 }
    }

    /** Ported helper/essenceStars.js starCosts. Sums slice(0, level) of an upgrade_costs array. */
    private fun starCostsNw(upgrades: JsonArray, level: Int, prices: Map<String, Double>, prestigeItem: Boolean): Double {
        var price = 0.0
        val limit = minOf(level, upgrades.size())
        for (i in 0 until limit) {
            val up = upgrades[i]
            if (up.isJsonArray) {
                for (cost in up.asJsonArray) price += starCostOne(cost.asJsonObject, prices)
            } else if (up.isJsonObject) {
                price += starCostOne(up.asJsonObject, prices)
            }
        }
        return price
    }
    private fun starCostOne(up: JsonObject, prices: Map<String, Double>): Double {
        try {
            val amount = if (up.has("amount")) up.get("amount").asDouble else 0.0
            if (up.has("essence_type")) {
                val et = up.get("essence_type").asString
                return amount * price(prices, "ESSENCE_$et") * fishmod.utils.networth.NwConstants.ESSENCE
            } else if (up.has("item_id")) {
                val iid = up.get("item_id").asString
                return amount * price(prices, iid)
            }
        } catch (ignored: Exception) {}
        return 0.0
    }

    /** Ported ItemEnchantments.js: per-enchant value with overrides, silex, upgrades. */
    private fun enchantmentsValueNw(id: String, enc: NbtCompound, prices: Map<String, Double>): Double {
        var v = 0.0
        val blocked = fishmod.utils.networth.NwConstants.BLOCKED_ENCHANTMENTS[id]
        for (rawName in enc.keys) {
            try {
                val name = rawName.uppercase()
                var value = enc.getInt(rawName, 0)
                if (blocked != null && blocked.contains(name)) continue
                val ign = fishmod.utils.networth.NwConstants.IGNORED_ENCHANTMENTS[name]
                if (ign != null && ign == value) continue
                if (fishmod.utils.networth.NwConstants.STACKING_ENCHANTMENTS.contains(name)) value = 1

                // Silex
                if ("EFFICIENCY" == name && value >= 6 && !fishmod.utils.networth.NwConstants.IGNORE_SILEX.contains(id)) {
                    val eff = value - (if ("STONK_PICKAXE" == id) 6 else 5)
                    if (eff > 0) v += price(prices, "SIL_EX") * eff * fishmod.utils.networth.NwConstants.SILEX
                }
                // Enchantment upgrades
                val tierReq: Int? = if (fishmod.utils.networth.NwConstants.ENCHANTMENT_UPGRADE_TIER.containsKey(name))
                    fishmod.utils.networth.NwConstants.ENCHANTMENT_UPGRADE_TIER[name]!![0] else null
                if (tierReq != null && value >= tierReq) {
                    val up = fishmod.utils.networth.NwConstants.ENCHANTMENT_UPGRADE_ITEM[name]
                    if (up != null) v += price(prices, up) * fishmod.utils.networth.NwConstants.ENCHANTMENT_UPGRADES
                }
                // Base enchantment value
                val mult = if (fishmod.utils.networth.NwConstants.ENCHANTMENTS_WORTH.containsKey(name))
                    fishmod.utils.networth.NwConstants.ENCHANTMENTS_WORTH[name]!!
                else
                    fishmod.utils.networth.NwConstants.ENCHANTMENTS
                v += price(prices, "ENCHANTMENT_" + name + "_" + value) * mult
            } catch (ignored: Exception) {}
        }
        return v
    }

    private val GEM_TYPES: Set<String> = java.util.Set.of(
        "RUBY", "AMBER", "SAPPHIRE", "JADE", "AMETHYST", "TOPAZ", "JASPER", "OPAL", "AQUAMARINE", "CITRINE", "ONYX", "PERIDOT")

    /** Values applied gemstones plus gemstone-slot unlock costs (Divan/Crimson armor), per Gemstones.js. */
    private fun gemsValueNw(id: String?, ex: NbtCompound, meta: com.google.gson.JsonObject?, prices: Map<String, Double>): Double {
        val gemsEl = ex.get("gems") ?: return 0.0
        val gems = gemsEl.asCompound().orElse(null) ?: return 0.0
        var total = 0.0

        // ---- Gemstone slot unlock costs (Divan / Crimson family armor) ----
        try {
            val isDivan = id != null && id.matches(Regex("DIVAN_(HELMET|CHESTPLATE|LEGGINGS|BOOTS)"))
            val isCrimson = id != null && id.matches(Regex("(HOT_|FIERY_|BURNING_|INFERNAL_)?(AURORA|CRIMSON|TERROR|HOLLOW|FERVOR)(_HELMET|_CHESTPLATE|_LEGGINGS|_BOOTS)"))
            if ((isDivan || isCrimson) && meta != null && meta.has("gemstone_slots") && meta.get("gemstone_slots").isJsonArray) {
                val application = if (isDivan)
                    fishmod.utils.networth.NwConstants.GEMSTONE_CHAMBERS
                else
                    fishmod.utils.networth.NwConstants.GEMSTONE_SLOTS
                val unlocked = gems.getList("unlocked_slots").orElse(null)
                if (unlocked != null) {
                    val slots = meta.getAsJsonArray("gemstone_slots")
                    for (u in 0 until unlocked.size) {
                        val slotName = unlocked.getString(u).orElse("")
                        // slot entries look like COMBAT_0 -> match by slot_type prefix
                        val slotType = if (slotName.contains("_")) slotName.substring(0, slotName.lastIndexOf('_')) else slotName
                        for (se in slots) {
                            if (!se.isJsonObject) continue
                            val so = se.asJsonObject
                            if (so.has("slot_type") && so.get("slot_type").asString == slotType && so.has("costs")) {
                                var t = 0.0
                                for (ce in so.getAsJsonArray("costs")) {
                                    val co = ce.asJsonObject
                                    val ctype = if (co.has("type")) co.get("type").asString else ""
                                    if ("COINS" == ctype && co.has("coins")) t += co.get("coins").asDouble
                                    else if ("ITEM" == ctype && co.has("item_id"))
                                        t += price(prices, co.get("item_id").asString.uppercase()) * (if (co.has("amount")) co.get("amount").asDouble else 0.0)
                                }
                                total += t * application
                                break
                            }
                        }
                    }
                }
            }
        } catch (ignored: Exception) {}
        for (slot in gems.keys) {
            if (slot == "unlocked_slots" || slot.endsWith("_gem")) continue
            // Tier: either a bare string ("PERFECT") or a compound with "quality".
            var tier = gems.getString(slot, "")
            if (tier.isEmpty()) {
                val c = gems.get(slot)?.asCompound()?.orElse(null)
                if (c != null) tier = c.getString("quality", "")
            }
            if (tier.isEmpty()) continue
            // Type: from slot name (e.g. "JASPER_0") or the companion "<slot>_gem" entry for universal slots.
            var type = slot.split("_")[0]
            if (!GEM_TYPES.contains(type)) {
                val named = gems.getString(slot + "_gem", "")
                if (named.isNotEmpty()) type = named
            }
            if (!GEM_TYPES.contains(type)) continue
            total += price(prices, tier + "_" + type + "_GEM")
        }
        return total
    }

    private fun petsValueNw(member: JsonObject, prices: Map<String, Double>): Double {
        var total = 0.0
        try {
            var pets: JsonArray? = null
            if (member.has("pets_data") && member.getAsJsonObject("pets_data").has("pets"))
                pets = member.getAsJsonObject("pets_data").getAsJsonArray("pets")
            else if (member.has("pets")) pets = member.getAsJsonArray("pets")
            if (pets == null) return 0.0
            for (pe in pets) {
                val pet = pe.asJsonObject
                val type = if (pet.has("type")) pet.get("type").asString else null
                val tier = if (pet.has("tier")) pet.get("tier").asString else null
                if (type == null || tier == null) continue
                val exp = if (pet.has("exp")) pet.get("exp").asDouble else 0.0
                val maxLevel = if ("GOLDEN_DRAGON" == type) 200 else 100
                val level = minOf(maxLevel, fishmod.features.OverflowPetLevels.calcLevel(exp, petRarity(tier)))
                val skin = if (pet.has("skin") && !pet.get("skin").isJsonNull) pet.get("skin").asString else null
                val basePetId = tier + "_" + type
                val petId = if (skin != null) basePetId + "_SKINNED_" + skin else basePetId
                // pet skin uses max(skinned, base) at each level point (non-cosmetic falls back to base)
                val p1 = maxOf(price(prices, "LVL_1_$petId"), price(prices, "LVL_1_$basePetId"))
                val pMax = maxOf(price(prices, "LVL_" + maxLevel + "_" + petId), price(prices, "LVL_" + maxLevel + "_" + basePetId))
                val frac = if (maxLevel <= 1) 1.0 else (level - 1).toDouble() / (maxLevel - 1)
                var base = p1 + (pMax - p1) * frac
                if (base <= 0) base = if (pMax > 0) pMax else p1

                var extra = 0.0
                val heldItem = if (pet.has("heldItem") && !pet.get("heldItem").isJsonNull) pet.get("heldItem").asString else null
                // held pet item x1
                if (heldItem != null) extra += price(prices, heldItem) * fishmod.utils.networth.NwConstants.PET_ITEM
                // pet skin x0.8
                if (skin != null) extra += price(prices, "PET_SKIN_$skin") * fishmod.utils.networth.NwConstants.SOULBOUND_PET_SKINS

                // pet candy reduction (PetCandy.js): reduces the candy-added value portion
                try {
                    val candyUsed = if (pet.has("candyUsed") && !pet.get("candyUsed").isJsonNull) pet.get("candyUsed").asInt else 0
                    if (candyUsed > 0) {
                        var reduceValue = base * (1 - fishmod.utils.networth.NwConstants.PET_CANDY)
                        val maxReduction = if (level == 100) 5_000_000.0 else 2_500_000.0
                        reduceValue = minOf(reduceValue, maxReduction)
                        extra -= reduceValue
                    }
                } catch (ignored: Exception) {}

                total += base + extra
            }
        } catch (ignored: Exception) {}
        return total
    }

    /** Values everything stored in sacks (enchanted resources, gemstones, etc.). */
    private fun sacksValueNw(member: JsonObject, prices: Map<String, Double>): Double {
        var sacks: JsonObject? = null
        if (member.has("inventory") && member.getAsJsonObject("inventory").has("sacks_counts")
            && member.getAsJsonObject("inventory").get("sacks_counts").isJsonObject)
            sacks = member.getAsJsonObject("inventory").getAsJsonObject("sacks_counts")
        else if (member.has("sacks_counts") && member.get("sacks_counts").isJsonObject)
            sacks = member.getAsJsonObject("sacks_counts")
        if (sacks == null) return 0.0
        var total = 0.0
        for (e in sacks.entrySet()) {
            try {
                val cnt = e.value.asDouble
                if (cnt > 0) total += price(prices, e.key) * cnt
            } catch (ignored: Exception) {}
        }
        return total
    }

    /** Values stored essence (wither/crimson/dragon/etc.) from the currencies block. */
    private fun essenceValueNw(member: JsonObject, prices: Map<String, Double>): Double {
        try {
            if (!member.has("currencies")) return 0.0
            val cur = member.getAsJsonObject("currencies")
            if (!cur.has("essence") || !cur.get("essence").isJsonObject) return 0.0
            val ess = cur.getAsJsonObject("essence")
            var total = 0.0
            for (e in ess.entrySet()) {
                var amt = 0.0
                if (e.value.isJsonObject) {
                    val o = e.value.asJsonObject
                    if (o.has("current")) amt = o.get("current").asDouble
                } else {
                    try { amt = e.value.asDouble } catch (ignored: Exception) {}
                }
                if (amt > 0) total += price(prices, "ESSENCE_" + e.key.uppercase()) * amt
            }
            return total
        } catch (ignored: Exception) { return 0.0 }
    }

    /** Values Galatea attribute shards (owned + fused); SkyHelper doesn't value these, so this is an estimate. */
    private fun shardsValueNw(member: JsonObject, prices: Map<String, Double>): Double {
        var total = 0.0
        try {
            val shards = if (member.has("shards") && member.get("shards").isJsonObject)
                member.getAsJsonObject("shards") else null
            if (shards != null && shards.has("owned") && shards.get("owned").isJsonArray) {
                for (e in shards.getAsJsonArray("owned")) {
                    try {
                        val o = e.asJsonObject
                        val type = if (o.has("type")) o.get("type").asString else null
                        val amt = if (o.has("amount_owned")) o.get("amount_owned").asDouble else 0.0
                        if (type != null && amt > 0) total += price(prices, "SHARD_" + type.uppercase()) * amt
                    } catch (ignored: Exception) {}
                }
            }
        } catch (ignored: Exception) {}
        try {
            val attrs = if (member.has("attributes") && member.get("attributes").isJsonObject)
                member.getAsJsonObject("attributes") else null
            if (attrs != null && attrs.has("stacks") && attrs.get("stacks").isJsonObject) {
                val stacks = attrs.getAsJsonObject("stacks")
                for (e in stacks.entrySet()) {
                    try {
                        val cnt = e.value.asDouble
                        if (cnt > 0) total += price(prices, "ATTRIBUTE_SHARD_" + e.key.uppercase()) * cnt
                    } catch (ignored: Exception) {}
                }
            }
        } catch (ignored: Exception) {}
        return total
    }

    private fun petRarity(tier: String): fishmod.features.OverflowPetLevels.Rarity {
        return try { fishmod.features.OverflowPetLevels.Rarity.valueOf(tier) }
        catch (e: Exception) { fishmod.features.OverflowPetLevels.Rarity.LEGENDARY }
    }

    /** Blocking UUID resolve (Mojang → Ashcon), with on-disk cache. */
    private fun resolveUuidBlocking(ign: String): String? {
        val cached = getCachedUuid(ign)
        if (cached != null) return cached
        // Mojang is authoritative; only fall back to the cache-laggy Ashcon mirror when Mojang can't answer.
        try {
            val req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/$ign"))
                .timeout(Duration.ofSeconds(8)).header("User-Agent", "FishMod").GET().build()
            val r = HTTP.send(req, HttpResponse.BodyHandlers.ofString())
            val code = r.statusCode()
            if (code in 200..299) {
                val uuid = parseUuid(r.body())
                if (uuid != null) { putUuid(ign, uuid); return uuid }
            }
            val transientErr = code == 429 || code == 408 || code >= 500
            if (!transientErr) return null // authoritative not-found — don't ask the stale mirror
        } catch (ignored: Exception) {}
        // Mojang unreachable / rate-limited — fall back to Ashcon.
        try {
            val req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.ashcon.app/mojang/v2/user/$ign"))
                .timeout(Duration.ofSeconds(8)).header("User-Agent", "FishMod").GET().build()
            val r = HTTP.send(req, HttpResponse.BodyHandlers.ofString())
            if (r.statusCode() == 200) {
                val uuid = parseUuid(r.body())
                if (uuid != null) { putUuid(ign, uuid); return uuid }
            }
        } catch (ignored: Exception) {}
        return null
    }

    fun interface EconomyCallback { fun onData(bank: Double, purse: Double, corpses: String?) }

    class PowderData {
        @JvmField var mithril: Long = -1
        @JvmField var gemstone: Long = -1
        @JvmField var glacite: Long = -1

        fun hasData(): Boolean {
            return mithril >= 0 || gemstone >= 0 || glacite >= 0
        }
    }

    fun interface PowderCallback { fun onData(data: PowderData) }

    /** Fetches mithril/gemstone/glacite powder from the player's selected SkyBlock profile. */
    @JvmStatic
    fun getPowderByName(mc: MinecraftClient, ign: String, cb: PowderCallback) {
        if (!checkKey(mc)) return
        mc.send { Misc.addChatMessage(Text.literal("§7Looking up $ign's powder...")) }
        resolveUuid(ign, 0) { uuid ->
            if (uuid == null) {
                mc.send { Misc.addChatMessage(Text.literal("§cPlayer not found: $ign")) }
                mc.execute { cb.onData(PowderData()) }
                return@resolveUuid
            }
            fetchPowder(mc, uuid, cb)
        }
    }

    private fun fetchPowder(mc: MinecraftClient, uuidStr: String, cb: PowderCallback) {
        val req: HttpRequest
        try {
            req = HttpRequest.newBuilder()
                .uri(URI.create("$PROXY_URL/skyblock/profiles?uuid=$uuidStr"))
                .header("X-FishMod-Token", MOD_TOKEN)
                .header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build()
        } catch (e: Exception) {
            mc.execute { cb.onData(PowderData()) }
            return
        }
        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
            .thenAccept { resp ->
                var result = PowderData()
                try {
                    val root = JsonParser.parseString(resp.body()).asJsonObject
                    if (!root.get("success").asBoolean) {
                        mc.send { Misc.addChatMessage(Text.literal("§cAPI error — proxy rejected request.")) }
                        mc.execute { cb.onData(PowderData()) }
                        return@thenAccept
                    }
                    val member = findSelectedMember(root, uuidStr)
                    if (member != null) result = parsePowder(member)
                } catch (e: Exception) {
                    mc.send { Misc.addChatMessage(Text.literal("§cAPI parse error: " + e.message)) }
                }
                val finalResult = result
                mc.execute { cb.onData(finalResult) }
            }
            .exceptionally {
                mc.send { Misc.addChatMessage(Text.literal("§cAPI request failed.")) }
                mc.execute { cb.onData(PowderData()) }
                null
            }
    }

    private fun findSelectedMember(root: JsonObject, uuidStr: String): JsonObject? {
        if (!root.has("profiles") || root.get("profiles").isJsonNull) return null
        var chosen: JsonObject? = null
        for (pe in root.getAsJsonArray("profiles")) {
            val p = pe.asJsonObject
            if (p.has("selected") && p.get("selected").asBoolean) { chosen = p; break }
            if (chosen == null) chosen = p
        }
        if (chosen == null || !chosen.has("members")) return null
        val members = chosen.getAsJsonObject("members")
        return if (members.has(uuidStr)) members.getAsJsonObject(uuidStr) else null
    }

    private fun parsePowder(member: JsonObject): PowderData {
        val d = PowderData()
        if (!member.has("mining_core")) return d
        val core = member.getAsJsonObject("mining_core")
        // powder_* = unspent balance; powder_spent = lifetime spent — total = both
        d.mithril = totalPowder(core, "mithril")
        d.gemstone = totalPowder(core, "gemstone")
        d.glacite = totalPowder(core, "glacite")
        return d
    }

    private fun totalPowder(core: JsonObject, type: String): Long {
        val current = readPowderField(core, "powder_$type")
        val spent = readPowderSpent(core, type)
        if (current < 0 && spent < 0) return -1
        return maxOf(0L, current) + maxOf(0L, spent)
    }

    private fun readPowderSpent(core: JsonObject, type: String): Long {
        if (core.has("powder_spent") && !core.get("powder_spent").isJsonNull) {
            val spentEl = core.get("powder_spent")
            if (spentEl.isJsonObject) {
                val v = readPowderField(spentEl.asJsonObject, type)
                if (v >= 0) return v
            }
        }
        return readPowderField(core, "powder_spent_$type")
    }

    private fun readPowderField(obj: JsonObject, key: String): Long {
        if (!obj.has(key) || obj.get(key).isJsonNull) return -1
        return obj.get(key).asDouble.toLong()
    }

    /** Fetches bank balance, purse, and glacite corpses for the local player's selected profile. */
    @JvmStatic
    fun getEconomy(mc: MinecraftClient, cb: EconomyCallback) {
        if (mc.player == null) { cb.onData(-1.0, -1.0, null); return }
        val uuid = mc.player!!.uuid.toString().replace("-", "")
        val req: HttpRequest
        try {
            req = HttpRequest.newBuilder()
                .uri(URI.create("$PROXY_URL/skyblock/profiles?uuid=$uuid"))
                .header("X-FishMod-Token", MOD_TOKEN).header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(10)).GET().build()
        } catch (e: Exception) { cb.onData(-1.0, -1.0, null); return }
        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept { r ->
            var bank = -1.0
            var purse = -1.0
            var corpses: String? = null
            try {
                val root = JsonParser.parseString(r.body()).asJsonObject
                for (pe in root.getAsJsonArray("profiles")) {
                    val profile = pe.asJsonObject
                    if (!profile.has("selected") || !profile.get("selected").asBoolean) continue
                    if (profile.has("banking") && profile.getAsJsonObject("banking").has("balance"))
                        bank = profile.getAsJsonObject("banking").get("balance").asDouble
                    val member = profile.getAsJsonObject("members").getAsJsonObject(uuid)
                    if (member.has("currencies") && member.getAsJsonObject("currencies").has("coin_purse"))
                        purse = member.getAsJsonObject("currencies").get("coin_purse").asDouble
                    else if (member.has("coin_purse")) purse = member.get("coin_purse").asDouble
                    if (member.has("glacite_player_data")) {
                        val cl = member.getAsJsonObject("glacite_player_data").get("corpses_looted")
                        corpses = formatCorpses(cl)
                    }
                    break
                }
            } catch (ignored: Exception) {}
            cb.onData(bank, purse, corpses)
        }.exceptionally { cb.onData(-1.0, -1.0, null); null }
    }

    /** Uploads the local player's cosmetic nick (empty/null clears it) so other mod users can see it. */
    @JvmStatic
    fun uploadNick(uuidNoDashes: String, nick: String?) {
        try {
            val o = JsonObject()
            o.addProperty("uuid", uuidNoDashes)
            o.addProperty("nick", nick ?: "")
            val req = HttpRequest.newBuilder()
                .uri(URI.create("$PROXY_URL/nick"))
                .header("X-FishMod-Token", MOD_TOKEN)
                .header("Content-Type", "application/json")
                .header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(o.toString()))
                .build()
            HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
        } catch (ignored: Exception) {}
    }

    /** Ported from Java record PingData; plain class since Java callers use record-style accessors. */
    class PingData(
        private val uuidVal: String,
        private val nameVal: String,
        private val xVal: Double,
        private val yVal: Double,
        private val zVal: Double,
        private val dimVal: String,
        private val tsVal: Long
    ) {
        fun uuid(): String = uuidVal
        fun name(): String = nameVal
        fun x(): Double = xVal
        fun y(): Double = yVal
        fun z(): Double = zVal
        fun dim(): String = dimVal
        fun ts(): Long = tsVal

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is PingData) return false
            return uuidVal == other.uuidVal && nameVal == other.nameVal && xVal == other.xVal &&
                yVal == other.yVal && zVal == other.zVal && dimVal == other.dimVal && tsVal == other.tsVal
        }

        override fun hashCode(): Int {
            return java.util.Objects.hash(uuidVal, nameVal, xVal, yVal, zVal, dimVal, tsVal)
        }

        override fun toString(): String {
            return "PingData[uuid=$uuidVal, name=$nameVal, x=$xVal, y=$yVal, z=$zVal, dim=$dimVal, ts=$tsVal]"
        }
    }

    /** Publishes (or refreshes) the local player's current location ping to the shared store. */
    @JvmStatic
    fun uploadPing(uuidNoDashes: String, name: String?, x: Double, y: Double, z: Double, dim: String?) {
        try {
            val o = JsonObject()
            o.addProperty("uuid", uuidNoDashes)
            o.addProperty("name", name ?: "")
            o.addProperty("x", x)
            o.addProperty("y", y)
            o.addProperty("z", z)
            o.addProperty("dim", dim ?: "")
            val req = HttpRequest.newBuilder()
                .uri(URI.create("$PROXY_URL/ping"))
                .header("X-FishMod-Token", MOD_TOKEN)
                .header("Content-Type", "application/json")
                .header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(o.toString()))
                .build()
            HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
        } catch (ignored: Exception) {}
    }

    /** Fetches live pings for the given UUIDs newer than `since` (ms). Empty list on any failure. */
    @JvmStatic
    fun fetchPings(uuidsNoDashes: Collection<String>?, since: Long,
                    cb: java.util.function.Consumer<List<PingData>>) {
        if (uuidsNoDashes == null || uuidsNoDashes.isEmpty()) { cb.accept(java.util.List.of()); return }
        try {
            val q = uuidsNoDashes.joinToString(",")
            val req = HttpRequest.newBuilder()
                .uri(URI.create("$PROXY_URL/pings?uuids=$q&since=$since"))
                .header("X-FishMod-Token", MOD_TOKEN)
                .header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(10))
                .GET().build()
            HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept { r ->
                val out = ArrayList<PingData>()
                try {
                    val root = JsonParser.parseString(r.body()).asJsonObject
                    if (root.has("pings") && root.get("pings").isJsonObject) {
                        for (e in root.getAsJsonObject("pings").entrySet()) {
                            if (e.value == null || !e.value.isJsonObject) continue
                            val pr = e.value.asJsonObject
                            out.add(PingData(
                                e.key,
                                if (pr.has("name")) pr.get("name").asString else "",
                                pr.get("x").asDouble, pr.get("y").asDouble, pr.get("z").asDouble,
                                if (pr.has("dim")) pr.get("dim").asString else "",
                                if (pr.has("ts")) pr.get("ts").asLong else 0L))
                        }
                    }
                } catch (ignored: Exception) {}
                cb.accept(out)
            }.exceptionally { cb.accept(java.util.List.of()); null }
        } catch (e: Exception) { cb.accept(java.util.List.of()) }
    }

    /** Ported from Java record RepData; plain class since Java callers use record-style accessors. */
    class RepData(
        private val nameVal: String,
        private val upVal: Int,
        private val downVal: Int
    ) {
        fun name(): String = nameVal
        fun up(): Int = upVal
        fun down(): Int = downVal

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is RepData) return false
            return nameVal == other.nameVal && upVal == other.upVal && downVal == other.downVal
        }

        override fun hashCode(): Int = java.util.Objects.hash(nameVal, upVal, downVal)

        override fun toString(): String = "RepData[name=$nameVal, up=$upVal, down=$downVal]"
    }

    /** Casts the local player's reputation vote on a target. vote ∈ "up" | "down" | "none" (clears). */
    @JvmStatic
    fun voteRep(voterUuid: String, targetUuid: String, targetName: String?, vote: String,
                 cb: java.util.function.BiConsumer<Int, Int>?) {
        try {
            val o = JsonObject()
            o.addProperty("voter", voterUuid)
            o.addProperty("target", targetUuid)
            o.addProperty("name", targetName ?: "")
            o.addProperty("vote", vote)
            val req = HttpRequest.newBuilder()
                .uri(URI.create("$PROXY_URL/rep"))
                .header("X-FishMod-Token", MOD_TOKEN)
                .header("Content-Type", "application/json")
                .header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(o.toString()))
                .build()
            HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept { r ->
                var up = -1
                var down = -1
                try {
                    val root = JsonParser.parseString(r.body()).asJsonObject
                    if (root.has("success") && root.get("success").asBoolean) {
                        up = if (root.has("up")) root.get("up").asInt else 0
                        down = if (root.has("down")) root.get("down").asInt else 0
                    }
                } catch (ignored: Exception) {}
                cb?.accept(up, down)
            }.exceptionally { cb?.accept(-1, -1); null }
        } catch (e: Exception) { cb?.accept(-1, -1) }
    }

    /** Batch-fetches reputation for the given UUIDs → map of uuid(no dashes) → RepData. */
    @JvmStatic
    fun fetchReps(uuidsNoDashes: Collection<String>?,
                   cb: java.util.function.Consumer<Map<String, RepData>>) {
        if (uuidsNoDashes == null || uuidsNoDashes.isEmpty()) { cb.accept(java.util.Map.of()); return }
        try {
            val q = uuidsNoDashes.joinToString(",")
            val req = HttpRequest.newBuilder()
                .uri(URI.create("$PROXY_URL/rep?uuids=$q"))
                .header("X-FishMod-Token", MOD_TOKEN)
                .header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(10))
                .GET().build()
            HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept { r ->
                val out: MutableMap<String, RepData> = HashMap()
                try {
                    val root = JsonParser.parseString(r.body()).asJsonObject
                    if (root.has("reps") && root.get("reps").isJsonObject) {
                        for (e in root.getAsJsonObject("reps").entrySet()) {
                            if (e.value == null || !e.value.isJsonObject) continue
                            val rr = e.value.asJsonObject
                            out[e.key] = RepData(
                                if (rr.has("name")) rr.get("name").asString else "",
                                if (rr.has("up")) rr.get("up").asInt else 0,
                                if (rr.has("down")) rr.get("down").asInt else 0)
                        }
                    }
                } catch (ignored: Exception) {}
                cb.accept(out)
            }.exceptionally { cb.accept(java.util.Map.of()); null }
        } catch (e: Exception) { cb.accept(java.util.Map.of()) }
    }

    /** Batch-fetches cosmetic nicks for the given UUIDs → map of uuid(no dashes) → raw nick. */
    @JvmStatic
    fun fetchNicks(uuidsNoDashes: Collection<String>?,
                    cb: java.util.function.Consumer<Map<String, String>>) {
        if (uuidsNoDashes == null || uuidsNoDashes.isEmpty()) { cb.accept(java.util.Map.of()); return }
        try {
            val q = uuidsNoDashes.joinToString(",")
            val req = HttpRequest.newBuilder()
                .uri(URI.create("$PROXY_URL/nicks?uuids=$q"))
                .header("X-FishMod-Token", MOD_TOKEN)
                .header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(10))
                .GET().build()
            HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept { r ->
                val out: MutableMap<String, String> = HashMap()
                try {
                    val root = JsonParser.parseString(r.body()).asJsonObject
                    if (root.has("nicks") && root.get("nicks").isJsonObject)
                        for (e in root.getAsJsonObject("nicks").entrySet())
                            if (e.value != null && !e.value.isJsonNull)
                                out[e.key] = e.value.asString
                } catch (ignored: Exception) {}
                cb.accept(out)
            }.exceptionally { cb.accept(java.util.Map.of()); null }
        } catch (e: Exception) { cb.accept(java.util.Map.of()) }
    }

    /** Publishes the local player's render size as "x,y,z" (all 1.0 = none/clear). */
    @JvmStatic
    fun uploadScale(uuidNoDashes: String, x: Float, y: Float, z: Float) {
        try {
            val o = JsonObject()
            o.addProperty("uuid", uuidNoDashes)
            o.addProperty("scale", "$x,$y,$z")
            val req = HttpRequest.newBuilder()
                .uri(URI.create("$PROXY_URL/scale"))
                .header("X-FishMod-Token", MOD_TOKEN)
                .header("Content-Type", "application/json")
                .header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(o.toString()))
                .build()
            HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
        } catch (ignored: Exception) {}
    }

    /** Result of a /sync poll: the server version, and (only when changed) the nick/item/scale maps. */
    fun interface SyncCallback {
        /** null nicks/items/scales = nothing changed since last version. */
        fun onData(version: Long, nicks: Map<String, String>?, items: Map<String, String>?, scales: Map<String, String>?)
    }

    /** Version-gated poll: server returns just the version when unchanged, else version + full maps. */
    @JvmStatic
    fun fetchSync(uuidsNoDashes: Collection<String>?, version: Long, cb: SyncCallback) {
        if (uuidsNoDashes == null || uuidsNoDashes.isEmpty()) { cb.onData(version, null, null, null); return }
        try {
            val q = uuidsNoDashes.joinToString(",")
            val req = HttpRequest.newBuilder()
                .uri(URI.create("$PROXY_URL/sync?since=$version&uuids=$q"))
                .header("X-FishMod-Token", MOD_TOKEN)
                .header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(10))
                .GET().build()
            HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept { r ->
                try {
                    val root = JsonParser.parseString(r.body()).asJsonObject
                    if (!root.has("success") || !root.get("success").asBoolean) { cb.onData(version, null, null, null); return@thenAccept }
                    val ver = if (root.has("version")) root.get("version").asLong else version
                    if (!root.has("changed") || !root.get("changed").asBoolean) { cb.onData(ver, null, null, null); return@thenAccept }
                    cb.onData(ver, parseStringMap(root, "nicks"), parseStringMap(root, "items"), parseStringMap(root, "scales"))
                } catch (ignored: Exception) { cb.onData(version, null, null, null) }
            }.exceptionally { cb.onData(version, null, null, null); null }
        } catch (e: Exception) { cb.onData(version, null, null, null) }
    }

    private fun parseStringMap(root: JsonObject, key: String): Map<String, String> {
        val out: MutableMap<String, String> = HashMap()
        if (root.has(key) && root.get(key).isJsonObject)
            for (e in root.getAsJsonObject(key).entrySet())
                if (e.value != null && !e.value.isJsonNull)
                    out[e.key] = e.value.asString
        return out
    }

    /** Uploads the local player's item customizations (JSON array; empty array clears them). */
    @JvmStatic
    fun uploadItems(uuidNoDashes: String, itemsJson: String?) {
        try {
            val o = JsonObject()
            o.addProperty("uuid", uuidNoDashes)
            o.addProperty("items", itemsJson ?: "")
            val req = HttpRequest.newBuilder()
                .uri(URI.create("$PROXY_URL/items"))
                .header("X-FishMod-Token", MOD_TOKEN)
                .header("Content-Type", "application/json")
                .header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(o.toString()))
                .build()
            HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
        } catch (ignored: Exception) {}
    }

    /** Batch-fetches item customizations for the given UUIDs → map of uuid(no dashes) → JSON payload. */
    @JvmStatic
    fun fetchItems(uuidsNoDashes: Collection<String>?,
                    cb: java.util.function.Consumer<Map<String, String>>) {
        if (uuidsNoDashes == null || uuidsNoDashes.isEmpty()) { cb.accept(java.util.Map.of()); return }
        try {
            val q = uuidsNoDashes.joinToString(",")
            val req = HttpRequest.newBuilder()
                .uri(URI.create("$PROXY_URL/items?uuids=$q"))
                .header("X-FishMod-Token", MOD_TOKEN)
                .header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(10))
                .GET().build()
            HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept { r ->
                val out: MutableMap<String, String> = HashMap()
                try {
                    val root = JsonParser.parseString(r.body()).asJsonObject
                    if (root.has("items") && root.get("items").isJsonObject)
                        for (e in root.getAsJsonObject("items").entrySet())
                            if (e.value != null && !e.value.isJsonNull)
                                out[e.key] = e.value.asString
                } catch (ignored: Exception) {}
                cb.accept(out)
            }.exceptionally { cb.accept(java.util.Map.of()); null }
        } catch (e: Exception) { cb.accept(java.util.Map.of()) }
    }

    /** Fetches the local player's selected-profile member object (key held proxy-side). */
    @JvmStatic
    fun getLocalMember(mc: MinecraftClient, cb: java.util.function.Consumer<JsonObject?>) {
        if (mc.player == null) { cb.accept(null); return }
        val uuid = mc.player!!.uuid.toString().replace("-", "")
        val req: HttpRequest
        try {
            req = HttpRequest.newBuilder()
                .uri(URI.create("$PROXY_URL/skyblock/profiles?uuid=$uuid"))
                .header("X-FishMod-Token", MOD_TOKEN).header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(10)).GET().build()
        } catch (e: Exception) { cb.accept(null); return }
        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept { r ->
            var member: JsonObject? = null
            try {
                val root = JsonParser.parseString(r.body()).asJsonObject
                if (root.has("success") && root.get("success").asBoolean)
                    member = findSelectedMember(root, uuid)
            } catch (ignored: Exception) {}
            val fm = member
            mc.execute { cb.accept(fm) }
        }.exceptionally { mc.execute { cb.accept(null) }; null }
    }

    /** Fetches bank balance, purse, and glacite corpses for an arbitrary player by IGN. */
    @JvmStatic
    fun getEconomyByName(mc: MinecraftClient, ign: String, cb: EconomyCallback) {
        CompletableFuture.runAsync {
            try {
                val uuid = resolveUuidBlocking(ign)
                if (uuid == null) { cb.onData(-1.0, -1.0, null); return@runAsync }
                val req = HttpRequest.newBuilder()
                    .uri(URI.create("$PROXY_URL/skyblock/profiles?uuid=$uuid"))
                    .header("X-FishMod-Token", MOD_TOKEN).header("User-Agent", "Mozilla/5.0")
                    .timeout(Duration.ofSeconds(12)).GET().build()
                val r = HTTP.send(req, HttpResponse.BodyHandlers.ofString())
                var bank = -1.0
                var purse = -1.0
                var corpses: String? = null
                val root = JsonParser.parseString(r.body()).asJsonObject
                if (root.has("profiles") && !root.get("profiles").isJsonNull) {
                    var chosen: JsonObject? = null
                    for (pe in root.getAsJsonArray("profiles")) {
                        val p = pe.asJsonObject
                        if (p.has("selected") && p.get("selected").asBoolean) { chosen = p; break }
                        if (chosen == null) chosen = p
                    }
                    if (chosen != null) {
                        if (chosen.has("banking") && chosen.getAsJsonObject("banking").has("balance"))
                            bank = chosen.getAsJsonObject("banking").get("balance").asDouble
                        val member = chosen.getAsJsonObject("members").getAsJsonObject(uuid)
                        if (member.has("currencies") && member.getAsJsonObject("currencies").has("coin_purse"))
                            purse = member.getAsJsonObject("currencies").get("coin_purse").asDouble
                        else if (member.has("coin_purse")) purse = member.get("coin_purse").asDouble
                        if (member.has("glacite_player_data"))
                            corpses = formatCorpses(member.getAsJsonObject("glacite_player_data").get("corpses_looted"))
                    }
                }
                cb.onData(bank, purse, corpses)
            } catch (ex: Exception) {
                fishmod.utils.debug.Debug.LOGGER.warn("[Economy] error: {}", ex.toString())
                cb.onData(-1.0, -1.0, null)
            }
        }
    }

    /** corpses_looted is an object {type:count}; format as "12L, 3T, ... (total N)". */
    private fun formatCorpses(el: JsonElement?): String {
        if (el == null || el.isJsonNull) return "0"
        try {
            if (el.isJsonObject) {
                val o = el.asJsonObject
                var total = 0L
                val sb = StringBuilder()
                for (e in o.entrySet()) {
                    val v = e.value.asLong
                    total += v
                    val t = e.key
                    val abbr = if (t.isEmpty()) "?" else t.substring(0, 1).uppercase()
                    if (sb.isNotEmpty()) sb.append(", ")
                    sb.append(v).append(abbr)
                }
                sb.append(" (total ").append(total).append(")")
                return sb.toString()
            }
            return el.asString
        } catch (e: Exception) { return "0" }
    }

    /** Debug: dumps economy/glacite-related fields for the local player's selected profile. */
    @JvmStatic
    fun dumpEconomy(mc: MinecraftClient) {
        if (mc.player == null) return
        val uuid = mc.player!!.uuid.toString().replace("-", "")
        mc.send { Misc.addChatMessage(Text.literal("§7Fetching profile economy fields...")) }
        val req: HttpRequest
        try {
            req = HttpRequest.newBuilder()
                .uri(URI.create("$PROXY_URL/skyblock/profiles?uuid=$uuid"))
                .header("X-FishMod-Token", MOD_TOKEN).header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(10)).GET().build()
        } catch (e: Exception) { return }
        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept { r ->
            try {
                val root = JsonParser.parseString(r.body()).asJsonObject
                for (pe in root.getAsJsonArray("profiles")) {
                    val profile = pe.asJsonObject
                    if (!profile.has("selected") || !profile.get("selected").asBoolean) continue
                    val member = profile.getAsJsonObject("members").getAsJsonObject(uuid)
                    mc.send {
                        Misc.addChatMessage(Text.literal("§b--- Economy dump ---"))
                        // Bank (profile-level)
                        if (profile.has("banking") && profile.getAsJsonObject("banking").has("balance"))
                            Misc.addChatMessage(Text.literal("§7banking.balance = §f" + profile.getAsJsonObject("banking").get("balance")))
                        else Misc.addChatMessage(Text.literal("§7banking.balance = §cmissing"))
                        // Purse
                        if (member.has("coin_purse")) Misc.addChatMessage(Text.literal("§7coin_purse = §f" + member.get("coin_purse")))
                        if (member.has("currencies")) {
                            val cur = member.getAsJsonObject("currencies")
                            Misc.addChatMessage(Text.literal("§7currencies keys = §f" + cur.keySet()))
                            if (cur.has("coin_purse")) Misc.addChatMessage(Text.literal("§7currencies.coin_purse = §f" + cur.get("coin_purse")))
                        }
                        // Glacite corpses
                        if (member.has("glacite_player_data")) {
                            val g = member.getAsJsonObject("glacite_player_data")
                            Misc.addChatMessage(Text.literal("§7glacite_player_data keys = §f" + g.keySet()))
                            if (g.has("corpses")) Misc.addChatMessage(Text.literal("§7glacite.corpses = §f" + g.get("corpses")))
                        } else {
                            Misc.addChatMessage(Text.literal("§7glacite_player_data = §cmissing"))
                        }
                        Misc.addChatMessage(Text.literal("§7member top keys = §f" + member.keySet()))
                        Misc.addChatMessage(Text.literal("§b--- End ---"))
                    }
                    return@thenAccept
                }
            } catch (e: Exception) {
                mc.send { Misc.addChatMessage(Text.literal("§cdump error: " + e.message)) }
            }
        }
    }

    /** Returns XP needed to reach the next whole cata level, formatted like "142.3k" or "1.23m". */
    @JvmStatic
    fun xpToNextLevel(xp: Long): String {
        for (i in 0 until CATA_XP_TABLE.size - 1) {
            if (xp < CATA_XP_TABLE[i + 1]) {
                val needed = CATA_XP_TABLE[i + 1] - xp
                if (needed >= 1_000_000) return String.format("%.2fm", needed / 1_000_000.0)
                if (needed >= 1_000) return String.format("%.1fk", needed / 1_000.0)
                return needed.toString()
            }
        }
        // Overflow (level 50+): each extra level = CATA_OVERFLOW_XP_PER_LEVEL XP
        val overflow = xp - CATA_XP_TABLE[CATA_XP_TABLE.size - 1]
        val needed = CATA_OVERFLOW_XP_PER_LEVEL - (overflow % CATA_OVERFLOW_XP_PER_LEVEL)
        if (needed >= 1_000_000) return String.format("%.2fm", needed / 1_000_000.0)
        if (needed >= 1_000) return String.format("%.1fk", needed / 1_000.0)
        return needed.toString()
    }

    /** Returns level as a formatted string like "42.75" or "51.30" for overflow cata. */
    @JvmStatic
    fun formatLevel(xp: Long): String {
        for (i in CATA_XP_TABLE.size - 1 downTo 0) {
            if (xp >= CATA_XP_TABLE[i]) {
                if (i == CATA_XP_TABLE.size - 1) {
                    // Overflow: each extra level = CATA_OVERFLOW_XP_PER_LEVEL XP
                    val overflow = (xp - CATA_XP_TABLE[i]).toDouble() / CATA_OVERFLOW_XP_PER_LEVEL.toDouble()
                    return String.format("%.2f", i + overflow)
                }
                val progress = (xp - CATA_XP_TABLE[i]).toDouble() / (CATA_XP_TABLE[i + 1] - CATA_XP_TABLE[i])
                return String.format("%d.%02d", i, (progress * 100).toInt())
            }
        }
        return "0.00"
    }


    fun interface PetCallback { fun onData(p: PetInfo) }

    class PetInfo {
        @JvmField var ok: Boolean = false
        @JvmField var name: String? = null
        @JvmField var level: Int = 0
        @JvmField var maxed: Boolean = false
        @JvmField var overflowLevel: Int = -1
        @JvmField var xpIntoLevel: Double = -1.0
        @JvmField var xpForNext: Double = -1.0
        @JvmField var pct: Float = -1f
    }

    private fun petRarityOf(tier: String): fishmod.features.OverflowPetLevels.Rarity {
        return try { fishmod.features.OverflowPetLevels.Rarity.valueOf(tier) }
        catch (e: Exception) { fishmod.features.OverflowPetLevels.Rarity.LEGENDARY }
    }

    /** "GOLDEN_DRAGON" → "Golden Dragon" (matches PetHud's skill map display names). */
    private fun petTypeToName(type: String): String {
        val parts = type.lowercase().split("_")
        val sb = StringBuilder()
        for (p in parts) {
            if (p.isEmpty()) continue
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(Character.toUpperCase(p[0])).append(p.substring(1))
        }
        return sb.toString()
    }

    // Standard Taming skill XP table (cumulative) for levels 0..60.
    private val TAMING_XP = longArrayOf(
        0L, 50L, 175L, 375L, 675L, 1175L, 1925L, 2925L, 4425L, 6425L,
        9925L, 14_925L, 22_425L, 32_425L, 47_425L, 67_425L, 97_425L, 147_425L, 222_425L, 322_425L,
        522_425L, 822_425L, 1_222_425L, 1_722_425L, 2_322_425L, 3_022_425L, 3_822_425L, 4_722_425L, 5_722_425L, 6_822_425L,
        8_022_425L, 9_322_425L, 10_722_425L, 12_222_425L, 13_822_425L, 15_522_425L, 17_322_425L, 19_222_425L, 21_222_425L, 23_322_425L,
        25_522_425L, 27_822_425L, 30_222_425L, 32_722_425L, 35_322_425L, 38_072_425L, 40_972_425L, 44_072_425L, 47_372_425L, 50_872_425L,
        54_572_425L, 58_472_425L, 62_572_425L, 66_872_425L, 71_372_425L, 76_072_425L, 80_972_425L, 86_072_425L, 91_372_425L, 96_872_425L,
        102_572_425L
    )
    private val PET_ITEM_BONUS: Map<String, Int> = java.util.Map.ofEntries(
        java.util.Map.entry("PET_ITEM_ALL_SKILLS_BOOST_COMMON", 25), java.util.Map.entry("PET_ITEM_ALL_SKILLS_BOOST_RARE", 35),
        java.util.Map.entry("PET_ITEM_ALL_SKILLS_BOOST_EPIC", 50),
        java.util.Map.entry("PET_ITEM_COMBAT_SKILL_BOOST_COMMON", 20), java.util.Map.entry("PET_ITEM_COMBAT_SKILL_BOOST_UNCOMMON", 30),
        java.util.Map.entry("PET_ITEM_COMBAT_SKILL_BOOST_RARE", 40), java.util.Map.entry("PET_ITEM_COMBAT_SKILL_BOOST_EPIC", 50),
        java.util.Map.entry("PET_ITEM_FARMING_SKILL_BOOST_COMMON", 20), java.util.Map.entry("PET_ITEM_FARMING_SKILL_BOOST_UNCOMMON", 30),
        java.util.Map.entry("PET_ITEM_FARMING_SKILL_BOOST_RARE", 40), java.util.Map.entry("PET_ITEM_FARMING_SKILL_BOOST_EPIC", 50),
        java.util.Map.entry("PET_ITEM_FISHING_SKILL_BOOST_COMMON", 20), java.util.Map.entry("PET_ITEM_FISHING_SKILL_BOOST_UNCOMMON", 30),
        java.util.Map.entry("PET_ITEM_FISHING_SKILL_BOOST_RARE", 40), java.util.Map.entry("PET_ITEM_FISHING_SKILL_BOOST_EPIC", 50),
        java.util.Map.entry("PET_ITEM_MINING_SKILL_BOOST_COMMON", 20), java.util.Map.entry("PET_ITEM_MINING_SKILL_BOOST_UNCOMMON", 30),
        java.util.Map.entry("PET_ITEM_MINING_SKILL_BOOST_RARE", 40), java.util.Map.entry("PET_ITEM_MINING_SKILL_BOOST_EPIC", 50),
        java.util.Map.entry("PET_ITEM_FORAGING_SKILL_BOOST_COMMON", 20), java.util.Map.entry("PET_ITEM_FORAGING_SKILL_BOOST_UNCOMMON", 30),
        java.util.Map.entry("PET_ITEM_FORAGING_SKILL_BOOST_RARE", 40), java.util.Map.entry("PET_ITEM_FORAGING_SKILL_BOOST_EPIC", 50)
    )
    private val BEASTMASTER_TIER: Map<String, Int> =
        java.util.Map.of("BRONZE", 30, "SILVER", 35, "GOLD", 40, "DIAMOND", 45)

    /** Fetches the local player's active pet and refreshes pet-XP multipliers if auto-detect is on. */
    @JvmStatic
    fun getActivePet(mc: MinecraftClient, cb: PetCallback) {
        if (mc.player == null) { cb.onData(PetInfo()); return }
        val uuid = mc.player!!.uuid.toString().replace("-", "")
        val req: HttpRequest
        try {
            req = HttpRequest.newBuilder()
                .uri(URI.create("$PROXY_URL/skyblock/profiles?uuid=$uuid"))
                .header("X-FishMod-Token", MOD_TOKEN).header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(10)).GET().build()
        } catch (e: Exception) { cb.onData(PetInfo()); return }

        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept { r ->
            val info = PetInfo()
            try {
                val root = JsonParser.parseString(r.body()).asJsonObject
                if (root.has("profiles") && !root.get("profiles").isJsonNull) {
                    for (pe in root.getAsJsonArray("profiles")) {
                        val profile = pe.asJsonObject
                        if (!profile.has("selected") || !profile.get("selected").asBoolean) continue
                        val member = profile.getAsJsonObject("members").getAsJsonObject(uuid)
                        applyPetMultipliers(profile, member)
                        var active: JsonObject? = null
                        if (member.has("pets_data") && member.getAsJsonObject("pets_data").has("pets")) {
                            for (q in member.getAsJsonObject("pets_data").getAsJsonArray("pets")) {
                                val pet = q.asJsonObject
                                if (pet.has("active") && pet.get("active").asBoolean) { active = pet; break }
                            }
                        }
                        if (active != null && active.has("type")) {
                            val type = active.get("type").asString
                            val tier = if (active.has("tier")) active.get("tier").asString else "LEGENDARY"
                            val exp = if (active.has("exp")) active.get("exp").asDouble else 0.0
                            val rar = petRarityOf(tier)
                            val maxLevel = if ("GOLDEN_DRAGON" == type) 200 else 100
                            var remaining = exp
                            var level = 1
                            var cost = fishmod.features.OverflowPetLevels.getXpForLevel(0, rar)
                            while (remaining >= cost && level < 1000) {
                                remaining -= cost; level++
                                cost = fishmod.features.OverflowPetLevels.getXpForLevel(level - 1, rar)
                            }
                            info.name = petTypeToName(type)
                            info.overflowLevel = level
                            info.maxed = level >= maxLevel
                            info.level = minOf(level, maxLevel)
                            info.xpIntoLevel = remaining
                            info.xpForNext = cost.toDouble()
                            info.pct = if (cost > 0) (remaining / cost * 100.0).toFloat() else 0f
                            info.ok = true
                        }
                        break
                    }
                }
            } catch (ignored: Exception) {}
            mc.execute { cb.onData(info) }
        }.exceptionally { mc.execute { cb.onData(PetInfo()) }; null }
    }

    private fun applyPetMultipliers(profile: JsonObject, member: JsonObject) {
        try {
            // Taming level from skill XP
            if (member.has("player_data")) {
                val pd = member.getAsJsonObject("player_data")
                if (pd.has("experience") && pd.getAsJsonObject("experience").has("SKILL_TAMING")) {
                    val xp = pd.getAsJsonObject("experience").get("SKILL_TAMING").asDouble.toLong()
                    for (i in TAMING_XP.size - 1 downTo 0) if (xp >= TAMING_XP[i]) { FishSettings.petXpTamingLevel = i; break }
                }
            }
            // Active pet's held item bonus
            var petItem = 0
            if (member.has("pets_data") && member.getAsJsonObject("pets_data").has("pets")) {
                for (q in member.getAsJsonObject("pets_data").getAsJsonArray("pets")) {
                    val pet = q.asJsonObject
                    if (pet.has("active") && pet.get("active").asBoolean
                        && pet.has("heldItem") && !pet.get("heldItem").isJsonNull) {
                        petItem = PET_ITEM_BONUS.getOrDefault(pet.get("heldItem").asString, 0)
                        break
                    }
                }
            }
            FishSettings.petXpPetItemBonus = petItem
            // Beastmaster crest from accessory bag NBT
            FishSettings.petXpBeastmasterBonus = detectBeastmaster(member)
            // Booster cookie
            var cookie = false
            if (member.has("profile")) {
                val mp = member.getAsJsonObject("profile")
                cookie = if (mp.has("booster_cookie_expires_at")) mp.get("booster_cookie_expires_at").asLong > System.currentTimeMillis()
                else if (mp.has("cookie_buff_active")) mp.get("cookie_buff_active").asBoolean
                else cookie
            }
            FishSettings.petXpBoosterCookie = cookie
        } catch (ignored: Exception) {}
    }

    private fun detectBeastmaster(member: JsonObject): Int {
        try {
            if (!member.has("accessory_bag_storage")) return 0
            val abs = member.getAsJsonObject("accessory_bag_storage")
            if (!abs.has("bag_storage")) return 0
            val bag = abs.getAsJsonObject("bag_storage")
            if (!bag.has("data")) return 0
            val b64 = bag.get("data").asString
            if (b64.isEmpty()) return 0
            val bytes = java.util.Base64.getDecoder().decode(b64)
            val rootNbt = NbtIo.readCompressed(ByteArrayInputStream(bytes), NbtSizeTracker.ofUnlimitedBytes())
            val listItems = rootNbt.getList("i").orElse(null) ?: return 0
            var best = 0
            for (i in 0 until listItems.size) {
                val item = listItems.getCompound(i).orElse(null)
                if (item == null || item.isEmpty) continue
                val id = getItemId(item)
                if (id == null || !id.contains("BEASTMASTER_CREST")) continue
                for (e in BEASTMASTER_TIER.entries)
                    if (id.endsWith(e.key) && e.value > best) best = e.value
            }
            return best
        } catch (ignored: Exception) {}
        return 0
    }

    /** Debug: dumps the Garden API JSON keys for the local player's selected profile (/fmgarden). */
    @JvmStatic
    fun dumpGarden(mc: MinecraftClient) {
        if (mc.player == null) return
        val uuid = mc.player!!.uuid.toString().replace("-", "")
        mc.send { Misc.addChatMessage(Text.literal("§7Fetching garden...")) }
        CompletableFuture.runAsync {
            try {
                val pr = HttpRequest.newBuilder()
                    .uri(URI.create("$PROXY_URL/skyblock/profiles?uuid=$uuid"))
                    .header("X-FishMod-Token", MOD_TOKEN).header("User-Agent", "Mozilla/5.0")
                    .timeout(Duration.ofSeconds(12)).GET().build()
                val root = JsonParser.parseString(HTTP.send(pr, HttpResponse.BodyHandlers.ofString()).body()).asJsonObject
                var profileId: String? = null
                for (pe in root.getAsJsonArray("profiles")) {
                    val p = pe.asJsonObject
                    if (profileId == null) profileId = p.get("profile_id").asString
                    if (p.has("selected") && p.get("selected").asBoolean) { profileId = p.get("profile_id").asString; break }
                }
                if (profileId == null) { mc.send { Misc.addChatMessage(Text.literal("§cno profile")) }; return@runAsync }
                val gr = HttpRequest.newBuilder()
                    .uri(URI.create("$PROXY_URL/skyblock/garden?profile=$profileId"))
                    .header("X-FishMod-Token", MOD_TOKEN).header("User-Agent", "Mozilla/5.0")
                    .timeout(Duration.ofSeconds(12)).GET().build()
                val body = HTTP.send(gr, HttpResponse.BodyHandlers.ofString()).body()
                val g = JsonParser.parseString(body).asJsonObject
                val garden = if (g.has("garden") && g.get("garden").isJsonObject) g.getAsJsonObject("garden") else g
                mc.send {
                    Misc.addChatMessage(Text.literal("§b--- Garden keys ---"))
                    Misc.addChatMessage(Text.literal("§7top: §f" + garden.keySet()))
                    for (k in arrayOf("commission_data", "resources_collected", "crop_milestones", "unlocked_plots_ids")) {
                        if (garden.has(k) && garden.get(k).isJsonObject)
                            Misc.addChatMessage(Text.literal("§7$k: §f" + garden.getAsJsonObject(k)))
                        else if (garden.has(k))
                            Misc.addChatMessage(Text.literal("§7$k: §f" + garden.get(k)))
                    }
                }
            } catch (ex: Exception) {
                mc.send { Misc.addChatMessage(Text.literal("§cgarden err: $ex")) }
            }
        }
    }


    // General SkyBlock skill XP table (cumulative), used for the Farming skill level; differs from the Taming table.
    private val SKILL_XP = LongArray(61)
    init {
        val per = intArrayOf(
            50, 125, 200, 300, 500, 750, 1000, 1500, 2000, 3500, 5000, 7500, 10000, 15000, 20000, 30000,
            50000, 75000, 100000, 200000, 300000, 400000, 500000, 600000, 700000, 800000, 900000, 1000000,
            1100000, 1200000, 1300000, 1400000, 1500000, 1600000, 1700000, 1800000, 1900000, 2000000, 2100000,
            2200000, 2300000, 2400000, 2500000, 2600000, 2750000, 2900000, 3100000, 3400000, 3700000, 4000000,
            4300000, 4600000, 4900000, 5200000, 5500000, 5800000, 6100000, 6400000, 6700000, 7000000
        )
        var c = 0L
        for (i in per.indices) { c += per[i]; SKILL_XP[i + 1] = c }
    }
    private const val SKILL_OVERFLOW_PER_LEVEL = 7_000_000L

    /** Farming/skill level as a decimal, including overflow past 60 (e.g. 60.42). */
    private fun skillLevelOverflow(xp: Long): Double {
        var lvl = 0
        for (i in SKILL_XP.size - 1 downTo 0) if (xp >= SKILL_XP[i]) { lvl = i; break }
        if (lvl >= 60) return 60 + (xp - SKILL_XP[60]) / SKILL_OVERFLOW_PER_LEVEL.toDouble()
        val into = xp - SKILL_XP[lvl]
        val need = SKILL_XP[lvl + 1] - SKILL_XP[lvl]
        return lvl + (if (need > 0) into.toDouble() / need else 0.0)
    }

    fun interface IntCallback { fun onData(value: Int) }

    /** Recursively collects every numeric value whose key contains `needle` (case-insensitive). */
    private fun collectNumbersByKey(el: JsonElement?, needle: String, out: MutableMap<String, Int>) {
        if (el == null) return
        if (el.isJsonObject) {
            for (e in el.asJsonObject.entrySet()) {
                val v = e.value
                if (e.key.lowercase().contains(needle) && v.isJsonPrimitive && v.asJsonPrimitive.isNumber)
                    out[e.key] = v.asInt
                collectNumbersByKey(v, needle, out)
            }
        } else if (el.isJsonArray) {
            for (c in el.asJsonArray) collectNumbersByKey(c, needle, out)
        }
    }

    // The five Crystal Nucleus crystals; run count = min total_placed across them (uncapped).
    private val NUCLEUS_CRYSTALS =
        arrayOf("amber_crystal", "amethyst_crystal", "jade_crystal", "sapphire_crystal", "topaz_crystal")

    /** Crystal Nucleus runs = min total_placed across the 5 nucleus crystals (uncapped, what viewers show). */
    private fun pickNucleusRuns(member: JsonObject): Int {
        try {
            val crystals = member.getAsJsonObject("mining_core").getAsJsonObject("crystals")
            var min = Int.MAX_VALUE
            for (c in NUCLEUS_CRYSTALS) {
                if (crystals.has(c) && crystals.getAsJsonObject(c).has("total_placed"))
                    min = minOf(min, crystals.getAsJsonObject(c).get("total_placed").asInt)
                else { min = -1; break }
            }
            if (min >= 0 && min != Int.MAX_VALUE) return min
        } catch (ignored: Exception) {}
        // Fallback: the leveling completion counter (Hypixel caps this at 50).
        try {
            val comp = member.getAsJsonObject("leveling").getAsJsonObject("completions")
            if (comp.has("NUCLEUS_RUNS")) return comp.get("NUCLEUS_RUNS").asInt
        } catch (ignored: Exception) {}
        return -1
    }

    /** Debug: dumps every numeric field whose key contains "nucleus" or "crystal" (/fmnuc). */
    @JvmStatic
    fun dumpNucleus(mc: MinecraftClient) {
        if (mc.player == null) return
        val uuid = mc.player!!.uuid.toString().replace("-", "")
        mc.send { Misc.addChatMessage(Text.literal("§7Searching nucleus/crystal fields...")) }
        CompletableFuture.runAsync {
            try {
                val req = HttpRequest.newBuilder()
                    .uri(URI.create("$PROXY_URL/skyblock/profiles?uuid=$uuid"))
                    .header("X-FishMod-Token", MOD_TOKEN).header("User-Agent", "Mozilla/5.0")
                    .timeout(Duration.ofSeconds(12)).GET().build()
                val root = JsonParser.parseString(HTTP.send(req, HttpResponse.BodyHandlers.ofString()).body()).asJsonObject
                var chosen: JsonObject? = null
                for (pe in root.getAsJsonArray("profiles")) {
                    val p = pe.asJsonObject
                    if (p.has("selected") && p.get("selected").asBoolean) { chosen = p; break }
                    if (chosen == null) chosen = p
                }
                if (chosen == null) { mc.send { Misc.addChatMessage(Text.literal("§cno profile")) }; return@runAsync }
                val member = chosen.getAsJsonObject("members").getAsJsonObject(uuid)
                val nuc = java.util.LinkedHashMap<String, Int>()
                collectNumbersByKey(member, "nucleus", nuc)
                val cry = java.util.LinkedHashMap<String, Int>()
                collectNumbersByKey(member, "crystal", cry)
                mc.send {
                    Misc.addChatMessage(Text.literal("§b--- nucleus keys ---"))
                    if (nuc.isEmpty()) Misc.addChatMessage(Text.literal("§7(none)"))
                    nuc.forEach { k, v -> Misc.addChatMessage(Text.literal("§7$k: §f$v")) }
                    Misc.addChatMessage(Text.literal("§b--- crystal keys ---"))
                    cry.forEach { k, v -> Misc.addChatMessage(Text.literal("§7$k: §f$v")) }
                }
            } catch (ex: Exception) {
                mc.send { Misc.addChatMessage(Text.literal("§cnuc dump err: $ex")) }
            }
        }
    }

    /** Crystal Nucleus runs completed (searches the profile member for the "nucleus" run field). */
    @JvmStatic
    fun getNucleusRuns(mc: MinecraftClient, ign: String, cb: IntCallback) {
        CompletableFuture.runAsync {
            val uuid = resolveUuidBlocking(ign)
            if (uuid == null) { cb.onData(-1); return@runAsync }
            try {
                val req = HttpRequest.newBuilder()
                    .uri(URI.create("$PROXY_URL/skyblock/profiles?uuid=$uuid"))
                    .header("X-FishMod-Token", MOD_TOKEN).header("User-Agent", "Mozilla/5.0")
                    .timeout(Duration.ofSeconds(12)).GET().build()
                val root = JsonParser.parseString(HTTP.send(req, HttpResponse.BodyHandlers.ofString()).body()).asJsonObject
                var runs = -1
                if (root.has("profiles") && !root.get("profiles").isJsonNull) {
                    var chosen: JsonObject? = null
                    for (pe in root.getAsJsonArray("profiles")) {
                        val p = pe.asJsonObject
                        if (p.has("selected") && p.get("selected").asBoolean) { chosen = p; break }
                        if (chosen == null) chosen = p
                    }
                    if (chosen != null) {
                        val member = chosen.getAsJsonObject("members").getAsJsonObject(uuid)
                        runs = pickNucleusRuns(member)
                    }
                }
                cb.onData(runs)
            } catch (ex: Exception) {
                fishmod.utils.debug.Debug.LOGGER.warn("[Nucleus] error: {}", ex.toString())
                cb.onData(-1)
            }
        }
    }

    fun interface ProfileStatsCallback { fun onData(sbLevel: Double, farmingLevel: Double) }

    /** Fetches the player's SkyBlock level (leveling.experience / 100) and Farming skill level. */
    @JvmStatic
    fun getProfileStats(mc: MinecraftClient, ign: String, cb: ProfileStatsCallback) {
        CompletableFuture.runAsync {
            val uuid = resolveUuidBlocking(ign)
            if (uuid == null) { cb.onData(-1.0, -1.0); return@runAsync }
            try {
                val req = HttpRequest.newBuilder()
                    .uri(URI.create("$PROXY_URL/skyblock/profiles?uuid=$uuid"))
                    .header("X-FishMod-Token", MOD_TOKEN).header("User-Agent", "Mozilla/5.0")
                    .timeout(Duration.ofSeconds(12)).GET().build()
                val r = HTTP.send(req, HttpResponse.BodyHandlers.ofString())
                val root = JsonParser.parseString(r.body()).asJsonObject
                var sb = -1.0
                var farm = -1.0
                if (root.has("profiles") && !root.get("profiles").isJsonNull) {
                    var chosen: JsonObject? = null
                    for (pe in root.getAsJsonArray("profiles")) {
                        val p = pe.asJsonObject
                        if (p.has("selected") && p.get("selected").asBoolean) { chosen = p; break }
                        if (chosen == null) chosen = p
                    }
                    if (chosen != null) {
                        val member = chosen.getAsJsonObject("members").getAsJsonObject(uuid)
                        if (member.has("leveling") && member.getAsJsonObject("leveling").has("experience"))
                            sb = member.getAsJsonObject("leveling").get("experience").asDouble / 100.0
                        if (member.has("player_data")) {
                            val pd = member.getAsJsonObject("player_data")
                            if (pd.has("experience") && pd.getAsJsonObject("experience").has("SKILL_FARMING"))
                                farm = skillLevelOverflow(pd.getAsJsonObject("experience").get("SKILL_FARMING").asDouble.toLong())
                        }
                    }
                }
                cb.onData(sb, farm)
            } catch (ex: Exception) {
                fishmod.utils.debug.Debug.LOGGER.warn("[ProfileStats] error: {}", ex.toString())
                cb.onData(-1.0, -1.0)
            }
        }
    }


    /** Worm + Scatha bestiary kills and the (combined) Worm bestiary tier. */
    class WormStats {
        @JvmField var worm: Int = 0      // Crystal Hollows Worm kills (bestiary.kills.worm_*)
        @JvmField var scatha: Int = 0    // Scatha kills (bestiary.kills.scatha_*)
        @JvmField var total: Long = 0    // worm + scatha (the bestiary tier is based on this combined total)
        @JvmField var tier: Int = 0      // current Worm-bestiary tier (0..maxTier)
        @JvmField var maxTier: Int = 0   // max tier (15)
        @JvmField var nextTierKills: Int? = null  // combined kills required for the next tier, null if maxed
        @JvmField var found: Boolean = false // true if the profile's bestiary data was located
    }

    // Hypixel's Worm bestiary family combines Worm+Scatha kills; bracket 5 truncates at the 400-kill cap (15 tiers).
    private val WORM_BESTIARY_BRACKET =
        intArrayOf(1, 2, 3, 5, 7, 10, 15, 20, 25, 30, 60, 120, 200, 300, 400)

    private fun computeWormStats(worm: Int, scatha: Int): WormStats {
        val s = WormStats()
        s.worm = worm
        s.scatha = scatha
        s.total = worm.toLong() + scatha
        s.maxTier = WORM_BESTIARY_BRACKET.size
        var tier = s.maxTier
        var next: Int? = null
        for (i in WORM_BESTIARY_BRACKET.indices) {
            if (s.total < WORM_BESTIARY_BRACKET[i]) { tier = i; next = WORM_BESTIARY_BRACKET[i]; break }
        }
        s.tier = tier
        s.nextTierKills = next
        return s
    }

    fun interface WormStatsCallback { fun onData(data: WormStats) }

    /** Fetches Worm + Scatha bestiary kills and computes the combined Worm bestiary tier. */
    @JvmStatic
    fun getWormStats(mc: MinecraftClient, ign: String, cb: WormStatsCallback) {
        CompletableFuture.runAsync {
            val uuid = resolveUuidBlocking(ign)
            if (uuid == null) { cb.onData(WormStats()); return@runAsync }
            try {
                val req = HttpRequest.newBuilder()
                    .uri(URI.create("$PROXY_URL/skyblock/profiles?uuid=$uuid"))
                    .header("X-FishMod-Token", MOD_TOKEN).header("User-Agent", "Mozilla/5.0")
                    .timeout(Duration.ofSeconds(12)).GET().build()
                val r = HTTP.send(req, HttpResponse.BodyHandlers.ofString())
                val root = JsonParser.parseString(r.body()).asJsonObject
                var worm = 0
                var scatha = 0
                var found = false
                if (root.has("profiles") && !root.get("profiles").isJsonNull) {
                    var chosen: JsonObject? = null
                    for (pe in root.getAsJsonArray("profiles")) {
                        val p = pe.asJsonObject
                        if (p.has("selected") && p.get("selected").asBoolean) { chosen = p; break }
                        if (chosen == null) chosen = p
                    }
                    if (chosen != null) {
                        val member = chosen.getAsJsonObject("members").getAsJsonObject(uuid)
                        if (member.has("bestiary") && member.get("bestiary").isJsonObject) {
                            val best = member.getAsJsonObject("bestiary")
                            if (best.has("kills") && best.get("kills").isJsonObject) {
                                found = true
                                for (e in best.getAsJsonObject("kills").entrySet()) {
                                    val v: Int
                                    try { v = e.value.asInt } catch (ex: Exception) { continue }
                                    val k = e.key
                                    if (k.matches(Regex("scatha_\\d+"))) scatha += v
                                    else if (k.matches(Regex("worm_\\d+"))) worm += v
                                }
                            }
                        }
                    }
                }
                val s = computeWormStats(worm, scatha)
                s.found = found
                cb.onData(s)
            } catch (ex: Exception) {
                fishmod.utils.debug.Debug.LOGGER.warn("[WormStats] error: {}", ex.toString())
                cb.onData(WormStats())
            }
        }
    }
}
