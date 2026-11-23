package fishmod.features.visuals

import fishmod.pv.gui.inventories.HypixelItemResolver
import fishmod.pv.gui.inventories.LegacyMaterialMap
import fishmod.pv.gui.inventories.SkullTextureApplier
import fishmod.pv.price.HypixelItemsCache
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.FishDiag
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ResolvableProfile
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/**
 * Classic Skyblock Items (Pack Disabler port). Hypixel's SkyBlock resource pack gives items `hypixel_skyblock:*`
 * item models. This swaps them back to the plain vanilla model / head texture for that SkyBlock ID, using the
 * Hypixel items data FishMod already loads (material + skin), so no bundled item list is needed.
 */
object ClassicSkyblock {
    private const val PACK_NS = "hypixel_skyblock"

    private val models = ConcurrentHashMap<String, Optional<Identifier>>()
    private val profiles = ConcurrentHashMap<String, Optional<ResolvableProfile>>()
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

    /** Vanilla model to show instead of [current], or null to leave it alone. */
    @JvmStatic
    fun modelFor(stack: ItemStack, current: Identifier): Identifier? {
        if (!FishSettings.classicItemsEnabled || !FishSettings.classicItemModels) return null
        if (current.namespace != PACK_NS) return null
        val id = skyblockId(stack) ?: return null
        models[id]?.let { return it.orElse(null) }
        ensureData()
        val def = HypixelItemsCache.get(id) ?: return null   // data not loaded yet: keep pack model, retry next frame
        val material = def.get("material")?.takeIf { it.isJsonPrimitive }?.asString
        val durability = def.get("durability")?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asInt }.getOrDefault(0) } ?: 0
        val model: Identifier? = try {
            val hasSkin = HypixelItemResolver.extractHypixelSkinValue(def) != null
            val item = if (hasSkin) Items.PLAYER_HEAD else LegacyMaterialMap.resolveMaterial(material, id, durability)
            item.components().get(DataComponents.ITEM_MODEL)
        } catch (t: Throwable) {
            FishDiag.fail("ClassicSkyblock.2", "model lookup failed for $id", t)
            null
        }
        models[id] = Optional.ofNullable(model)
        return model
    }

    /** Head texture to use for a SkyBlock item that the pack renders as a head, or null. */
    @JvmStatic
    fun profileFor(stack: ItemStack): ResolvableProfile? {
        if (!FishSettings.classicItemsEnabled || !FishSettings.classicItemHeads) return null
        if (stack.isEmpty || stack.`is`(Items.PLAYER_HEAD)) return null
        val id = skyblockId(stack) ?: return null
        profiles[id]?.let { return it.orElse(null) }
        ensureData()
        val def = HypixelItemsCache.get(id) ?: return null
        val skin = HypixelItemResolver.extractHypixelSkinValue(def)
        var profile: ResolvableProfile? = null
        if (skin != null && !HypixelItemResolver.isBrokenHypixelPlaceholderSkin(skin)) {
            try {
                val head = ItemStack(Items.PLAYER_HEAD)
                SkullTextureApplier.applyValue(head, skin, null)
                profile = head.get(DataComponents.PROFILE)
            } catch (t: Throwable) {
                FishDiag.fail("ClassicSkyblock.3", "head profile failed for $id", t)
            }
        }
        profiles[id] = Optional.ofNullable(profile)
        return profile
    }

    @JvmStatic
    fun tooltipStyle(style: Identifier?): Identifier? =
        if (style != null && style.namespace == PACK_NS && FishSettings.classicItemsEnabled && FishSettings.classicTooltips) null else style
}
