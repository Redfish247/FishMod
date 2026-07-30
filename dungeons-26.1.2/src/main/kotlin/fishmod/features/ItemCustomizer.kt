package fishmod.features

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.Identifier
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.equipment.trim.ArmorTrim
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Base64
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.UUID

/**
 * Client-side item customization: rename an item, render it with another item's model, add dungeon
 * stars, dye leather, and apply an armor trim. Stored per item (keyed by SkyBlock uuid → SkyBlock
 * id → vanilla registry id) and re-applied every tick to inventory + open-container slots, since
 * server slot updates overwrite our component edits.
 *
 * Customizations are also published to the mod proxy so other mod users see them on your worn armor
 * and held items — see [fishmod.cosmetic.RemoteItems].
 */
object ItemCustomizer {

    /** name (&-codes ok), model item id, dungeon star count (0-10), armor dye RGB (-1 = none),
     *  armor trim material id + pattern id (null/empty = none), and the source item's vanilla
     *  registry id (e.g. "minecraft:diamond_sword"). The vanilla id is how OTHER players match this
     *  custom: Hypixel strips SkyBlock NBT (the uuid/id key) off other players' items, so the only
     *  thing a viewer can identify is the vanilla item type. */
    class Custom(
        private val nameVal: String?,
        private val modelIdVal: String?,
        private val starsVal: Int,
        private val dyeVal: Int,
        private val trimMatVal: String?,
        private val trimPatVal: String?,
        private val skinVal: String?,
        private val vanillaVal: String?
    ) {
        fun name(): String? = nameVal
        fun modelId(): String? = modelIdVal
        fun stars(): Int = starsVal
        fun dye(): Int = dyeVal
        fun trimMat(): String? = trimMatVal
        fun trimPat(): String? = trimPatVal
        fun skin(): String? = skinVal
        fun vanilla(): String? = vanillaVal

        fun withVanilla(v: String?): Custom =
            Custom(nameVal, modelIdVal, starsVal, dyeVal, trimMatVal, trimPatVal, skinVal, v)
    }

    private val MASTER = charArrayOf('➊', '➋', '➌', '➍', '➎') // ➊➋➌➍➎

    /** SkyBlock-style star suffix: 1-5 gold ✪, 6-10 = 5 gold ✪ + red master-star count glyph. */
    @JvmStatic
    fun starSuffix(s: Int): String {
        if (s <= 0) return ""
        if (s <= 5) return " &6" + "✪".repeat(s)
        val masters = minOf(s, 10) - 5
        return " &6✪✪✪✪✪&c" + MASTER[masters - 1]
    }

    private val SAVE: Path = Paths.get("config/fishmod/item_customs.json")
    private val DATA: MutableMap<String, Custom> = LinkedHashMap()

    @JvmStatic
    fun init() {
        load()
        uploadOwn() // publish persisted customs on startup
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (DATA.isEmpty() || mc.player == null) return@register
            try {
                var dirty = false
                val inv = mc.player!!.inventory
                for (i in 0 until inv.containerSize) dirty = applyAndBackfill(inv.getItem(i)) || dirty
                val h: AbstractContainerMenu? = mc.player!!.containerMenu
                if (h != null) for (slot in h.slots) dirty = applyAndBackfill(slot.item) || dirty
                // A custom captured its vanilla type for the first time → persist + republish so other
                // players (who can only match by vanilla type) start seeing it.
                if (dirty) { save(); uploadOwn() }
            } catch (ignored: Exception) {}
        }
    }

    /** The vanilla registry id of a stack's base item (e.g. "minecraft:diamond_sword"). */
    @JvmStatic
    fun vanillaId(st: ItemStack?): String? {
        if (st == null || st.isEmpty) return null
        return try { BuiltInRegistries.ITEM.getKey(st.item).toString() } catch (e: Exception) { null }
    }

    /**
     * Applies the local custom for this stack and, if the custom doesn't yet know its source vanilla
     * type, records it from the stack. Returns true when a vanilla type was newly captured (so the
     * caller saves + re-uploads). Backfills pre-existing customs as their items pass through inventory.
     */
    private fun applyAndBackfill(st: ItemStack?): Boolean {
        if (st == null || st.isEmpty) return false
        val key = keyFor(st) ?: return false
        var c = DATA[key] ?: return false
        var captured = false
        if (c.vanilla().isNullOrEmpty()) {
            val v = vanillaId(st)
            if (v != null) { c = c.withVanilla(v); DATA[key] = c; captured = true }
        }
        applyCustom(st, c)
        return captured
    }

    /**
     * Resolves a stable key for a stack: SkyBlock uuid (per-stack) or SkyBlock id (per-item-type).
     * No vanilla-registry fallback — that's too broad (all player_heads would share a key, so
     * customizing one would apply to every fishing trophy frog you ever catch). If the stack has
     * neither a uuid nor a SkyBlock id, it can't be customized.
     */
    @JvmStatic
    fun keyFor(st: ItemStack?): String? {
        if (st == null || st.isEmpty) return null
        try {
            val cd: CustomData? = st.get(DataComponents.CUSTOM_DATA)
            if (cd != null) {
                val nbt: CompoundTag = cd.copyTag()
                val ea = nbt.getCompound("ExtraAttributes").orElse(null)
                if (ea != null) {
                    val u = ea.getStringOr("uuid", "")
                    if (u.isNotEmpty()) return "uuid:$u"
                    val id = ea.getStringOr("id", "")
                    if (id.isNotEmpty()) return "id:$id"
                }
                val u = nbt.getStringOr("uuid", "")
                if (u.isNotEmpty()) return "uuid:$u"
                val id = nbt.getStringOr("id", "")
                if (id.isNotEmpty()) return "id:$id"
            }
        } catch (ignored: Exception) {}
        return null
    }

    /** Wipes every saved customization (recovery for accidentally-broad keys from older builds). */
    @JvmStatic
    fun clearAll() {
        DATA.clear()
        save()
        uploadOwn()
    }

    @JvmStatic
    fun get(st: ItemStack?): Custom? {
        val k = keyFor(st) ?: return null
        return DATA[k]
    }

    @JvmStatic
    fun set(st: ItemStack?, name: String?, modelId: String?, stars: Int, dye: Int,
            trimMat: String?, trimPat: String?, skin: String?) {
        val k = keyFor(st) ?: return
        val noTrim = trimMat.isNullOrEmpty() || trimPat.isNullOrEmpty()
        val noSkin = skin.isNullOrEmpty()
        val empty = name.isNullOrEmpty() && modelId.isNullOrEmpty() && stars <= 0 && dye < 0 && noTrim && noSkin
        if (empty) DATA.remove(k)
        else DATA[k] = Custom(name, modelId, stars, dye,
                if (noTrim) null else trimMat, if (noTrim) null else trimPat,
                if (noSkin) null else skin, vanillaId(st))
        save()
        apply(st)
        uploadOwn()
    }

    /** Looks up the local customization for this stack (if any) and applies it. */
    @JvmStatic
    fun apply(st: ItemStack?) {
        if (st == null || st.isEmpty) return
        val c = get(st)
        if (c != null) applyCustom(st, c)
    }

    /**
     * Mutates a stack's CUSTOM_NAME / ITEM_MODEL / DYED_COLOR / TRIM components from a Custom.
     * Used for both the local player's items and (via [fishmod.cosmetic.RemoteItems]) other
     * players' worn armor / held items. Names are run through the profanity filter so a shared
     * custom name can never display a slur on your screen.
     */
    @JvmStatic
    @JvmOverloads
    fun applyCustom(st: ItemStack?, c: Custom?, applySkin: Boolean = true) {
        if (st == null || st.isEmpty || c == null) return
        try {
            val hasName = !c.name().isNullOrEmpty()
            if (hasName || c.stars() > 0) {
                val base = if (hasName) fishmod.cosmetic.ProfanityFilter.censor(c.name())
                           else st.item.getName(st).string
                val styled: net.minecraft.network.chat.Component = fishmod.cosmetic.NickState.parse(base + starSuffix(c.stars()))
                // Vanilla auto-italicizes CUSTOM_NAME (anvil-rename behavior); explicitly clear it.
                val name: net.minecraft.network.chat.MutableComponent = net.minecraft.network.chat.Component.empty()
                        .append(styled)
                        .setStyle(net.minecraft.network.chat.Style.EMPTY.withItalic(false))
                st.set(DataComponents.CUSTOM_NAME, name)
            }
            if (!c.modelId().isNullOrEmpty()) {
                st.set(DataComponents.ITEM_MODEL, ident(c.modelId()!!))
            }
            if (c.dye() >= 0)
                st.set(DataComponents.DYED_COLOR, net.minecraft.world.item.component.DyedItemColor(c.dye() and 0xFFFFFF))
            applyTrim(st, c)
            if (applySkin) applyHeadSkin(st, c)
        } catch (ignored: Exception) {}
    }

    /**
     * Repaints a player-head's profile texture from the custom's skin (a SkyBlock pet/cosmetic head
     * skin). Accepts a texture hash, a textures.minecraft.net URL, or a raw base64 textures value.
     * No-op for non-head items so other customized items are untouched.
     */
    private fun applyHeadSkin(st: ItemStack, c: Custom) {
        if (c.skin().isNullOrEmpty()) return
        if (!st.`is`(net.minecraft.world.item.Items.PLAYER_HEAD)) return
        try {
            val pc = buildSkinProfile(c.skin())
            if (pc != null) st.set(DataComponents.PROFILE, pc)
        } catch (ignored: Exception) {}
    }

    /** Builds a PROFILE component carrying the given head texture, or null if it can't be resolved. */
    @JvmStatic
    fun buildSkinProfile(skin: String?): net.minecraft.world.item.component.ResolvableProfile? {
        val value = texturesValue(skin) ?: return null
        // A stable UUID per texture keeps the profile cache-friendly; the name is cosmetic.
        val id: UUID = UUID.nameUUIDFromBytes(value.toByteArray(StandardCharsets.UTF_8))
        val gp = com.mojang.authlib.GameProfile(id, "FishModSkin")
        gp.properties().put("textures", com.mojang.authlib.properties.Property("textures", value))
        return net.minecraft.world.item.component.ResolvableProfile.createResolved(gp)
    }

    /**
     * Normalizes a skin string to a base64 "textures" property value. Recognizes a full URL, a bare
     * texture hash (the part after .../texture/), or an already-encoded base64 value (used as-is).
     */
    private fun texturesValue(skinIn: String?): String? {
        if (skinIn == null) return null
        val skin = skinIn.trim()
        if (skin.isEmpty()) return null
        var url: String? = null
        if (skin.startsWith("http://") || skin.startsWith("https://")) {
            url = skin
        } else if (skin.startsWith("textures.minecraft.net")) {
            url = "http://$skin"
        } else if (skin.matches(Regex("[0-9a-fA-F]{16,128}"))) {
            url = "http://textures.minecraft.net/texture/" + skin.lowercase()
        }
        if (url != null) {
            val json = "{\"textures\":{\"SKIN\":{\"url\":\"$url\"}}}"
            return Base64.getEncoder().encodeToString(json.toByteArray(StandardCharsets.UTF_8))
        }
        // Otherwise treat the input as a base64 textures value already (what Mojang stores).
        return skin
    }

    /** Resolves trim material + pattern from the world's data registries and sets the TRIM component. */
    private fun applyTrim(st: ItemStack, c: Custom) {
        if (c.trimMat().isNullOrEmpty() || c.trimPat().isNullOrEmpty()) return
        try {
            val mc = Minecraft.getInstance()
            if (mc.level == null) return
            val drm = mc.level!!.registryAccess()
            val matReg = drm.lookup(Registries.TRIM_MATERIAL).orElse(null) ?: return
            val patReg = drm.lookup(Registries.TRIM_PATTERN).orElse(null) ?: return
            val mat = matReg.get(ident(c.trimMat()!!)).orElse(null)
            val pat = patReg.get(ident(c.trimPat()!!)).orElse(null)
            if (mat != null && pat != null) st.set(DataComponents.TRIM, ArmorTrim(mat, pat))
        } catch (ignored: Exception) {}
    }

    private fun ident(id: String): Identifier =
        if (id.contains(":")) Identifier.parse(id) else Identifier.withDefaultNamespace(id)

    // ── persistence + sharing ──────────────────────────────────────────────────

    /** Serializes the local customization map to the shared JSON-array format. */
    @JvmStatic
    @Synchronized
    fun serialize(): String = toJson().toString()

    /** Debug: the keys of the local player's own customizations (what gets uploaded). */
    @JvmStatic
    fun debugKeys(): Set<String> = LinkedHashSet(DATA.keys)

    private fun toJson(): JsonArray {
        val arr = JsonArray()
        for ((key, v) in DATA) {
            val o = JsonObject()
            o.addProperty("key", key)
            if (v.name() != null) o.addProperty("name", v.name())
            if (v.modelId() != null) o.addProperty("model", v.modelId())
            o.addProperty("stars", v.stars())
            o.addProperty("dye", v.dye())
            if (v.trimMat() != null) o.addProperty("trimMat", v.trimMat())
            if (v.trimPat() != null) o.addProperty("trimPat", v.trimPat())
            if (v.skin() != null) o.addProperty("skin", v.skin())
            if (v.vanilla() != null) o.addProperty("vanilla", v.vanilla())
            arr.add(o)
        }
        return arr
    }

    /** Parses a shared JSON-array payload into a key→Custom map (used for remote players). */
    @JvmStatic
    fun parsePayload(json: String?): Map<String, Custom> {
        val out: MutableMap<String, Custom> = LinkedHashMap()
        if (json.isNullOrEmpty()) return out
        try {
            val arr = JsonParser.parseString(json).asJsonArray
            for (el in arr) {
                val o = el.asJsonObject
                if (!o.has("key")) continue
                out[o.get("key").asString] = Custom(
                        if (o.has("name")) o.get("name").asString else null,
                        if (o.has("model")) o.get("model").asString else null,
                        if (o.has("stars")) o.get("stars").asInt else 0,
                        if (o.has("dye")) o.get("dye").asInt else -1,
                        if (o.has("trimMat")) o.get("trimMat").asString else null,
                        if (o.has("trimPat")) o.get("trimPat").asString else null,
                        if (o.has("skin")) o.get("skin").asString else null,
                        if (o.has("vanilla")) o.get("vanilla").asString else null)
            }
        } catch (ignored: Exception) {}
        return out
    }

    /** Publishes the local player's customizations to the proxy so other mod users see them. */
    @JvmStatic
    fun uploadOwn() {
        if (!fishmod.utils.config.values.FishSettings.remoteItemsEnabled) return
        val mc = Minecraft.getInstance()
        if (mc.user == null) return
        val id: UUID? = mc.user.profileId
        if (id == null) return
        fishmod.utils.HypixelApi.uploadItems(id.toString().replace("-", ""), serialize())
    }

    @Synchronized
    private fun save() {
        try {
            Files.createDirectories(SAVE.parent)
            Files.writeString(SAVE, toJson().toString())
        } catch (ignored: Exception) {}
    }

    @Synchronized
    private fun load() {
        try {
            if (!Files.exists(SAVE)) return
            DATA.clear()
            DATA.putAll(parsePayload(Files.readString(SAVE)))
        } catch (ignored: Exception) {}
    }
}
