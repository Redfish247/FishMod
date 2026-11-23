package fishmod.features.visuals

import com.google.gson.JsonObject
import fishmod.pv.gui.inventories.HypixelItemResolver
import fishmod.pv.gui.inventories.LegacyMaterialMap
import fishmod.pv.gui.inventories.NeuItemResolver
import fishmod.pv.gui.inventories.SkullTextureApplier
import fishmod.pv.price.HypixelItemsCache
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.FishDiag
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ResolvableProfile
import java.util.concurrent.ConcurrentHashMap

/**
 * Classic Skyblock Items (Pack Disabler port). Hypixel's SkyBlock resource pack gives items `hypixel_skyblock:*`
 * item models. This swaps them back to the plain vanilla model / head texture for that SkyBlock ID.
 *
 * Many of those items are really `minecraft:paper` stacks (necklaces, belts, cosmetics...), so the vanilla look has to
 * come from data, in this order: the Hypixel items API `item_model` when it is a vanilla one, the API skin, the API
 * material (when not paper), then the NEU repo (head texture / item id). If nothing is certain the pack model stays.
 */
object ClassicSkyblock {
    private const val PACK_NS = "hypixel_skyblock"
    private const val NEU_WAIT_MS = 20_000L

    private class Resolved(val model: Identifier?, val profile: ResolvableProfile?)

    private val resolved = ConcurrentHashMap<String, Resolved>()
    private val firstSeen = ConcurrentHashMap<String, Long>()
    @Volatile private var requested = false

    @JvmStatic
    fun skyblockId(stack: ItemStack): String? {
        val tag = stack.get(DataComponents.CUSTOM_DATA)?.copyTag() ?: return null
        val id = tag.getString("id").orElse(null) ?: return null
        return id.replace(":", "-")
    }

    private fun ensureData() {
        if (requested) return
        requested = true
        try { HypixelItemsCache.requestIfEmpty() } catch (t: Throwable) { FishDiag.fail("ClassicSkyblock.1", "items data request failed", t) }
    }

    private fun modelOf(item: net.minecraft.world.item.Item): Identifier? = item.components().get(DataComponents.ITEM_MODEL)

    private fun headProfile(skin: String?, id: String): ResolvableProfile? {
        if (skin.isNullOrBlank() || HypixelItemResolver.isBrokenHypixelPlaceholderSkin(skin)) return null
        return try {
            val head = ItemStack(Items.PLAYER_HEAD)
            SkullTextureApplier.applyValue(head, skin, null)
            if (SkullTextureApplier.isTexturedPlayerHead(head)) head.get(DataComponents.PROFILE) else null
        } catch (t: Throwable) {
            FishDiag.fail("ClassicSkyblock.3", "head profile failed for $id", t)
            null
        }
    }

    private fun head(profile: ResolvableProfile) = Resolved(modelOf(Items.PLAYER_HEAD), profile)

    /** The vanilla look for [id], or null while the data is still loading. */
    private fun resolve(id: String): Resolved? {
        resolved[id]?.let { return it }
        // 0) Noamm's list (built from an older NEU repo): the exact old look, when it knows the item
        ClassicItemData.start()
        when (ClassicItemData.state) {
            ClassicItemData.State.READY -> ClassicItemData.get(id)?.let { e ->
                try {
                    val model = Identifier.parse(e.model)
                    val profile = e.texture?.let { headProfile(it, id) }
                    if (!(model.path == "player_head" && profile == null)) return remember(id, Resolved(model, profile))
                } catch (t: Throwable) {
                    FishDiag.fail("ClassicSkyblock.4", "old item entry unusable for $id", t)
                }
            }
            ClassicItemData.State.FAILED -> {}
            else -> return null   // still downloading/parsing, keep the pack model for now and retry next frame
        }
        ensureData()
        val def: JsonObject = HypixelItemsCache.get(id) ?: return null
        try {
            // 1) the API names a vanilla model directly (e.g. minecraft:writable_book)
            val im = def.get("item_model")?.takeIf { it.isJsonPrimitive }?.asString
            if (im != null && im.startsWith("minecraft:")) {
                return remember(id, Resolved(Identifier.parse(im), null))
            }
            // 2) the API has a skin
            headProfile(HypixelItemResolver.extractHypixelSkinValue(def), id)?.let { return remember(id, head(it)) }
            // 3) the API material, unless it is the unhelpful paper / an untextured head
            val material = def.get("material")?.takeIf { it.isJsonPrimitive }?.asString
            val durability = def.get("durability")?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asInt }.getOrDefault(0) } ?: 0
            val parsed = LegacyMaterialMap.parseMaterial(material, durability)
            if (parsed != null && parsed.name != "PAPER") {
                val item = LegacyMaterialMap.resolveMaterial(material, id, durability)
                if (item !== Items.PAPER && item !== Items.PLAYER_HEAD) return remember(id, Resolved(modelOf(item), null))
            }
            // 4) the NEU repo (head textures live there)
            val neu = NeuItemResolver.buildFromNeu(id.uppercase())
            if (neu != null) {
                if (neu.`is`(Items.PLAYER_HEAD)) {
                    neu.get(DataComponents.PROFILE)?.let { return remember(id, head(it)) }
                } else if (!neu.`is`(Items.PAPER)) {
                    return remember(id, Resolved(modelOf(neu.item), null))
                }
            } else {
                val since = firstSeen.computeIfAbsent(id) { System.currentTimeMillis() }
                if (System.currentTimeMillis() - since < NEU_WAIT_MS) return null   // NEU entry still loading, retry
            }
        } catch (t: Throwable) {
            FishDiag.fail("ClassicSkyblock.2", "vanilla look lookup failed for $id", t)
        }
        // 5) nothing certain: keep the pack's own model
        return remember(id, Resolved(null, null))
    }

    private fun remember(id: String, r: Resolved): Resolved { resolved[id] = r; return r }

    /** Vanilla model to show instead of [current], or null to leave it alone. */
    @JvmStatic
    fun modelFor(stack: ItemStack, current: Identifier): Identifier? {
        if (!FishSettings.classicItemsEnabled || !FishSettings.classicItemModels) return null
        if (current.namespace != PACK_NS) return null
        val tag = stack.get(DataComponents.CUSTOM_DATA)?.copyTag() ?: return null
        val id = tag.getString("id").orElse(null)?.replace(":", "-")
        if (id == null) return if (tag.contains("quiver_arrow")) modelOf(Items.ARROW) else null
        val r = resolve(id) ?: return null
        // a head model with the head textures turned off would render as Steve: keep the pack model instead
        if (r.profile != null && !FishSettings.classicItemHeads) return null
        val base = r.model ?: return null
        return dynamicModel(id, stack, tag, current, base)
    }

    private val ATTUNED = mapOf(
        "FIREDUST_DAGGER" to (1 to Items.GOLDEN_SWORD), "BURSTFIRE_DAGGER" to (1 to Items.GOLDEN_SWORD), "HEARTFIRE_DAGGER" to (1 to Items.GOLDEN_SWORD),
        "MAWDUST_DAGGER" to (3 to Items.DIAMOND_SWORD), "BURSTMAW_DAGGER" to (3 to Items.DIAMOND_SWORD), "HEARTMAW_DAGGER" to (3 to Items.DIAMOND_SWORD),
    )
    private val KATANAS = setOf("VOIDEDGE_KATANA", "VORPAL_KATANA", "ATOMSPLIT_KATANA")
    private val FUNGI = setOf("FUNGI_CUTTER", "FUNGI_CUTTER_2", "FUNGI_CUTTER_3")

    /** Items whose look depends on their state (attune mode, cooldown, ...), like Pack Disabler's DynamicItemModels. */
    private fun dynamicModel(id: String, stack: ItemStack, tag: net.minecraft.nbt.CompoundTag, current: Identifier, base: Identifier): Identifier {
        try {
            if (id == "CARNIVAL_SHOVEL") {
                val item = when (current.path.substringAfterLast('/')) {
                    "carnival_shovel_iron" -> Items.IRON_SHOVEL
                    "carnival_shovel_gold" -> Items.GOLDEN_SHOVEL
                    "carnival_shovel_diamond" -> Items.DIAMOND_SHOVEL
                    else -> return base
                }
                return modelOf(item) ?: base
            }
            ATTUNED[id]?.let { (mode, item) -> return if (tag.getInt("td_attune_mode").orElse(-1) == mode) (modelOf(item) ?: base) else base }
            if (id in KATANAS) {
                val cd = net.minecraft.client.Minecraft.getInstance().player?.cooldowns?.isOnCooldown(stack) == true
                return if (cd) (modelOf(Items.GOLDEN_SWORD) ?: base) else base
            }
            if (id in FUNGI) {
                return when (tag.getString("fungi_cutter_mode").orElse(null)) {
                    "RED" -> modelOf(Items.RED_MUSHROOM) ?: base
                    "BROWN" -> modelOf(Items.BROWN_MUSHROOM) ?: base
                    else -> base
                }
            }
        } catch (t: Throwable) {
            FishDiag.fail("ClassicSkyblock.5", "dynamic model failed for $id", t)
        }
        return base
    }

    /** Head texture to use for a SkyBlock item that the pack renders as a head, or null. */
    @JvmStatic
    fun profileFor(stack: ItemStack): ResolvableProfile? {
        if (!FishSettings.classicItemsEnabled || !FishSettings.classicItemHeads) return null
        if (stack.isEmpty || stack.`is`(Items.PLAYER_HEAD)) return null
        val tag = stack.get(DataComponents.CUSTOM_DATA)?.copyTag() ?: return null
        val id = tag.getString("id").orElse(null)?.replace(":", "-") ?: return null
        val skin = tag.getString("skin").orElse(null)?.replace(":", "-")
        if (skin != null) skinProfile(skin)?.let { return it }
        return resolve(id)?.profile
    }

    private val skinProfiles = ConcurrentHashMap<String, java.util.Optional<ResolvableProfile>>()

    /** Head texture of an item skin (custom_data `skin`), when the old item list knows it. */
    private fun skinProfile(skin: String): ResolvableProfile? {
        skinProfiles[skin]?.let { return it.orElse(null) }
        if (ClassicItemData.state != ClassicItemData.State.READY) return null
        val p = ClassicItemData.get(skin)?.texture?.let { headProfile(it, skin) }
        skinProfiles[skin] = java.util.Optional.ofNullable(p)
        return p
    }

    @JvmStatic
    fun tooltipStyle(style: Identifier?): Identifier? =
        if (style != null && style.namespace == PACK_NS && FishSettings.classicItemsEnabled && FishSettings.classicTooltips) null else style
}
