package fishmod.features.item

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileReader
import java.io.FileWriter

/** Client-only cosmetic item overrides (dye/trim/name), keyed by Hypixel item instance uuid. Never touches item NBT/DataComponents. Adapted from Skyblocker's item customization feature (github.com/SkyblockerMod/Skyblocker, MIT). */
object ItemCustomizationStore {

    data class Keyframe(val color: Int, val time: Float)
    data class AnimatedDye(val keyframes: List<Keyframe>, val cycleBack: Boolean, val delay: Float, val duration: Float)
    data class ArmorTrimId(val material: String, val pattern: String)

    private const val FILE_PATH = "config/fishmod-item-customization.json"
    private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()

    private data class Data(
        val dyeColors: MutableMap<String, Int> = HashMap(),
        val animatedDyes: MutableMap<String, AnimatedDye> = HashMap(),
        val armorTrims: MutableMap<String, ArmorTrimId> = HashMap(),
        val itemNames: MutableMap<String, String> = HashMap(),
        val modelIds: MutableMap<String, String> = HashMap()
    )

    private var data = Data()

    init { load() }

    @JvmStatic fun getDyeColor(uuid: String): Int? = data.dyeColors[uuid]
    @JvmStatic fun setDyeColor(uuid: String, argb: Int) { data.dyeColors[uuid] = argb; data.animatedDyes.remove(uuid); save() }
    @JvmStatic fun removeDyeColor(uuid: String) { if (data.dyeColors.remove(uuid) != null) save() }

    @JvmStatic fun getAnimatedDye(uuid: String): AnimatedDye? = data.animatedDyes[uuid]
    @JvmStatic fun setAnimatedDye(uuid: String, dye: AnimatedDye) { data.animatedDyes[uuid] = dye; data.dyeColors.remove(uuid); save() }
    @JvmStatic fun removeAnimatedDye(uuid: String) { if (data.animatedDyes.remove(uuid) != null) save() }

    @JvmStatic fun getArmorTrim(uuid: String): ArmorTrimId? = data.armorTrims[uuid]
    @JvmStatic fun setArmorTrim(uuid: String, trim: ArmorTrimId) { data.armorTrims[uuid] = trim; save() }
    @JvmStatic fun removeArmorTrim(uuid: String) { if (data.armorTrims.remove(uuid) != null) save() }

    @JvmStatic fun getItemName(uuid: String): String? = data.itemNames[uuid]
    @JvmStatic fun setItemName(uuid: String, name: String) { data.itemNames[uuid] = name; save() }
    @JvmStatic fun removeItemName(uuid: String) { if (data.itemNames.remove(uuid) != null) save() }

    /** Custom ITEM_MODEL override (e.g. "minecraft:trident") — reskins the item as another item's model. */
    @JvmStatic fun getModelId(uuid: String): String? = data.modelIds[uuid]
    @JvmStatic fun setModelId(uuid: String, id: String) { data.modelIds[uuid] = id; save() }
    @JvmStatic fun removeModelId(uuid: String) { if (data.modelIds.remove(uuid) != null) save() }

    private fun load() {
        val file = File(FILE_PATH)
        if (!file.exists()) return
        try {
            FileReader(file).use { reader ->
                val type = object : TypeToken<Data>() {}.type
                val loaded: Data? = GSON.fromJson(reader, type)
                if (loaded != null) data = loaded
            }
        } catch (ignored: Exception) {
        }
    }

    private fun save() {
        try {
            val file = File(FILE_PATH)
            file.parentFile?.mkdirs()
            FileWriter(file).use { writer -> GSON.toJson(data, writer) }
        } catch (ignored: Exception) {
        }
    }
}
