package fishmod.features.item

import fishmod.utils.debug.FishDiag
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.equipment.trim.ArmorTrim
import net.minecraft.world.item.equipment.trim.TrimMaterial
import net.minecraft.world.item.equipment.trim.TrimPattern

object ArmorTrimCache {

    private var trimsFor: Any? = null
    private val trims = HashMap<String, java.util.Optional<ArmorTrim>>()
    private val modelIds = HashMap<String, java.util.Optional<Identifier>>()

    @JvmStatic
    fun get(id: ItemCustomizationStore.ArmorTrimId): ArmorTrim? {
        val registryAccess = Minecraft.getInstance().player?.registryAccess() ?: return null
        if (registryAccess !== trimsFor) {
            trimsFor = registryAccess
            trims.clear()
        }
        return trims.getOrPut(id.material + "|" + id.pattern) {
            try {
                val material = FishDiag.notNull(registryAccess.lookupOrThrow(Registries.TRIM_MATERIAL).get(Identifier.parse(id.material)).orElse(null), "ArmorTrimCache.1") { "trim material not in registry: '${id.material}'" }
                val pattern = FishDiag.notNull(registryAccess.lookupOrThrow(Registries.TRIM_PATTERN).get(Identifier.parse(id.pattern)).orElse(null), "ArmorTrimCache.2") { "trim pattern not in registry: '${id.pattern}'" }
                java.util.Optional.ofNullable(if (material != null && pattern != null) ArmorTrim(material, pattern) else null)
            } catch (e: Exception) {
                FishDiag.fail("ArmorTrimCache.3", "armor trim lookup failed material='${id.material}' pattern='${id.pattern}'", e)
                java.util.Optional.empty()
            }
        }.orElse(null)
    }

    @JvmStatic
    fun modelId(raw: String): Identifier? {
        if (modelIds.size > 256) modelIds.clear()
        return modelIds.getOrPut(raw) {
            java.util.Optional.ofNullable(FishDiag.notNull(Identifier.tryParse(raw), "ArmorTrimCache.4") { "custom item model id not a valid identifier: '$raw'" })
        }.orElse(null)
    }

    @JvmStatic
    fun materials(): List<String> {
        val registryAccess = Minecraft.getInstance().player?.registryAccess() ?: return emptyList()
        return registryAccess.lookupOrThrow(Registries.TRIM_MATERIAL).listElements().map { it.key().identifier().toString() }.toList()
    }

    @JvmStatic
    fun patterns(): List<String> {
        val registryAccess = Minecraft.getInstance().player?.registryAccess() ?: return emptyList()
        return registryAccess.lookupOrThrow(Registries.TRIM_PATTERN).listElements().map { it.key().identifier().toString() }.toList()
    }
}
