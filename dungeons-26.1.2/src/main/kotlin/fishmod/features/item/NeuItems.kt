package fishmod.features.item

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import fishmod.utils.debug.FishDiag
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ResolvableProfile
import java.io.File
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.zip.ZipInputStream

// SkyBlock skins (Model tab) and dyes (Dye tab) from the NEU repo, for item customize.
object NeuItems {

    const val PREFIX = "neu:"

    data class Entry(val id: String, val name: String, val model: String, val texture: String?, val hex: String? = null)

    private const val ZIP_URL = "https://codeload.github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/zip/refs/heads/master"
    private val CACHE = File("config/fishmod/neu_cosmetics.json")
    private const val MAX_AGE_MS = 24L * 60 * 60 * 1000

    private val MODEL_RE = Regex("""ItemModel:"([a-z0-9_.:/-]+)"""")
    private val TEXTURE_RE = Regex("""Value:"([A-Za-z0-9+/=]+)"""")
    private val HEX_RE = Regex("#([0-9A-Fa-f]{6})")

    @Volatile private var entries: Map<String, Entry> = emptyMap()
    @Volatile private var sorted: List<Entry> = emptyList()
    @Volatile private var dyeList: List<Entry> = emptyList()
    @Volatile var loading = false; private set
    @Volatile var failed = false; private set
    @Volatile private var started = false

    private val stacks = HashMap<String, ItemStack>()
    private val profiles = HashMap<String, java.util.Optional<ResolvableProfile>>()
    private val models = HashMap<String, java.util.Optional<Identifier>>()

    fun ready(): Boolean = entries.isNotEmpty()

    @JvmStatic
    fun ensureLoaded() {
        if (started) return
        started = true
        loading = true
        fishmod.utils.IoExecutor.execute {
            try {
                val fresh = CACHE.exists() && System.currentTimeMillis() - CACHE.lastModified() < MAX_AGE_MS
                val list = (if (fresh) readCache() else null) ?: download()?.also { writeCache(it) } ?: readCache()
                if (list != null) publish(list) else failed = true
            } catch (e: Exception) {
                failed = true
                FishDiag.fail("NeuItems.1", "NEU item repo load failed", e)
                readCache()?.let { publish(it); failed = false }
            } finally {
                loading = false
            }
        }
    }

    private fun publish(list: List<Entry>) {
        sorted = list.filter { it.hex == null }.sortedBy { it.name.lowercase() }
        dyeList = list.filter { it.hex != null }.sortedBy { it.name.lowercase() }
        entries = list.associateBy { it.id }
    }

    private fun readCache(): List<Entry>? = try {
        if (!CACHE.exists()) null
        else CACHE.reader().use { Gson().fromJson<List<Entry>>(it, object : TypeToken<List<Entry>>() {}.type) }?.takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        FishDiag.fail("NeuItems.2", "NEU item cache unreadable", e); null
    }

    private fun writeCache(list: List<Entry>) {
        FishDiag.guard("NeuItems.3", "NEU item cache write failed") { fishmod.utils.SafeFiles.writeAtomic(CACHE, Gson().toJson(list)) }
    }

    private fun download(): List<Entry>? {
        val req = HttpRequest.newBuilder(URI.create(ZIP_URL)).timeout(Duration.ofSeconds(90)).GET().build()
        val res = fishmod.utils.Http.CLIENT.send(req, HttpResponse.BodyHandlers.ofInputStream())
        if (res.statusCode() != 200) {
            res.body().close()
            FishDiag.fail("NeuItems.4", "NEU repo download HTTP ${res.statusCode()}")
            return null
        }
        val out = ArrayList<Entry>(8000)
        ZipInputStream(res.body()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val n = e.name
                if (e.isDirectory || !n.endsWith(".json") || !n.substringBeforeLast('/').endsWith("/items")) continue
                parse(n.substringAfterLast('/').removeSuffix(".json"), zip.readBytes().toString(Charsets.UTF_8))?.let { out.add(it) }
            }
        }
        return out.takeIf { it.isNotEmpty() }
    }

    private fun isSkin(id: String) = id.contains("SKIN") && !id.endsWith("BARN_SKIN")

    private fun parse(id: String, text: String): Entry? = try {
        val dye = id.startsWith("DYE_")
        if (!dye && !isSkin(id)) return null
        val o = JsonParser.parseString(text).asJsonObject
        val nbt = o.get("nbttag")?.asString ?: ""
        val name = fishmod.utils.TextStrip.plain(o.get("displayname")?.asString ?: id).trim().ifEmpty { id }
        val legacy = o.get("itemid")?.asString ?: "minecraft:stone"
        val model = MODEL_RE.find(nbt)?.groupValues?.get(1) ?: if (legacy == "minecraft:skull") "minecraft:player_head" else legacy
        val hex = if (dye) o.getAsJsonArray("lore")?.firstNotNullOfOrNull { HEX_RE.find(it.asString)?.groupValues?.get(1)?.uppercase() } else null
        if (dye && hex == null) null
        else Entry(id, name.removeSuffix(" Dye"), model, TEXTURE_RE.find(nbt)?.groupValues?.get(1), hex)
    } catch (e: Exception) { null }

    fun get(id: String): Entry? = entries[id]

    fun dyes(): List<Entry> = dyeList

    fun search(query: String, limit: Int): List<Entry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val out = ArrayList<Entry>()
        for (e in sorted) {
            if (e.name.lowercase().contains(q) || e.id.lowercase().contains(q)) { out.add(e); if (out.size >= limit) break }
        }
        return out
    }

    /** "neu:ID" -> NEU id, or null for a plain model id. */
    @JvmStatic
    fun idOf(stored: String?): String? = if (stored != null && stored.startsWith(PREFIX)) stored.substring(PREFIX.length) else null

    @JvmStatic
    fun model(id: String): Identifier? {
        ensureLoaded()
        val e = entries[id] ?: return null
        return models.getOrPut(id) { java.util.Optional.ofNullable(Identifier.tryParse(e.model)) }.orElse(null)
    }

    @JvmStatic
    fun profile(id: String): ResolvableProfile? {
        ensureLoaded()
        val e = entries[id] ?: return null
        return profiles.getOrPut(id) {
            val tex = e.texture ?: return@getOrPut java.util.Optional.empty()
            val props = com.google.common.collect.ImmutableMultimap.of("textures", com.mojang.authlib.properties.Property("textures", tex))
            val gp = com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes(id.toByteArray()), "fmneu", com.mojang.authlib.properties.PropertyMap(props))
            java.util.Optional.of(ResolvableProfile.createResolved(gp))
        }.orElse(null)
    }

    /** Icon stack for the picker grid. */
    fun icon(e: Entry): ItemStack = stacks.getOrPut(e.id) {
        FishDiag.guard("NeuItems.5", "NEU icon build failed for ${e.id}") {
            ItemStack(Items.PLAYER_HEAD).also { st ->
                model(e.id)?.let { st.set(DataComponents.ITEM_MODEL, it) }
                profile(e.id)?.let { st.set(DataComponents.PROFILE, it) }
            }
        } ?: ItemStack(Items.BARRIER)
    }
}
