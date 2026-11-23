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
        val id = skyblockId(stack) ?: return null
        val r = resolve(id) ?: return null
        // a head model with the head textures turned off would render as Steve: keep the pack model instead
        if (r.profile != null && !FishSettings.classicItemHeads) return null
        return r.model
    }

    /** Head texture to use for a SkyBlock item that the pack renders as a head, or null. */
    @JvmStatic
    fun profileFor(stack: ItemStack): ResolvableProfile? {
        if (!FishSettings.classicItemsEnabled || !FishSettings.classicItemHeads) return null
        if (stack.isEmpty || stack.`is`(Items.PLAYER_HEAD)) return null
        val id = skyblockId(stack) ?: return null
        return resolve(id)?.profile
    }

    @JvmStatic
    fun tooltipStyle(style: Identifier?): Identifier? =
        if (style != null && style.namespace == PACK_NS && FishSettings.classicItemsEnabled && FishSettings.classicTooltips) null else style
}
