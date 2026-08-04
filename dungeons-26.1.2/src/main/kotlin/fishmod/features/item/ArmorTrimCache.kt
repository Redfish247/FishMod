package fishmod.features.item

import net.minecraft.client.Minecraft
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.equipment.trim.ArmorTrim
import net.minecraft.world.item.equipment.trim.TrimMaterial
import net.minecraft.world.item.equipment.trim.TrimPattern

/** Resolves an [ItemCustomizationStore.ArmorTrimId] to a real [ArmorTrim] via the client registries. */
object ArmorTrimCache {

    @JvmStatic
    fun get(id: ItemCustomizationStore.ArmorTrimId): ArmorTrim? {
        val registryAccess = Minecraft.getInstance().player?.registryAccess() ?: return null
        val materialRegistry = registryAccess.lookupOrThrow(Registries.TRIM_MATERIAL)
        val patternRegistry = registryAccess.lookupOrThrow(Registries.TRIM_PATTERN)
        val material = materialRegistry.get(Identifier.parse(id.material)).orElse(null) ?: return null
        val pattern = patternRegistry.get(Identifier.parse(id.pattern)).orElse(null) ?: return null
        return ArmorTrim(material, pattern)
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
