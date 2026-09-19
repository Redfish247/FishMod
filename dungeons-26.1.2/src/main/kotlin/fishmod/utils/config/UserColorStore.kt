package fishmod.utils.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileReader
import java.io.FileWriter

/** Custom hex colors saved from any [fishmod.features.FishModScreen.ColorPickerSetting] "Your Colors" tab; shared across every color picker in the mod. */
object UserColorStore {

    private const val FILE_PATH = "config/fishmod-user-colors.json"
    private const val MAX_COLORS = 30
    private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()

    private data class Data(val colors: MutableList<Int> = ArrayList())

    private var data = Data()

    init { load() }

    @JvmStatic fun all(): List<Int> = data.colors

    /** Adds (or moves to front if it already exists) so the most recently added color shows first. */
    @JvmStatic fun add(argb: Int) {
        data.colors.remove(argb)
        data.colors.add(0, argb)
        while (data.colors.size > MAX_COLORS) data.colors.removeAt(data.colors.size - 1)
        save()
    }

    @JvmStatic fun remove(argb: Int) {
        if (data.colors.remove(argb)) save()
    }

    private fun load() {
        val file = File(FILE_PATH)
        if (!file.exists()) return
        try {
            FileReader(file).use { reader ->
                val type = object : TypeToken<Data>() {}.type
                val loaded: Data? = GSON.fromJson(reader, type)
                if (loaded != null) data = loaded
            }
        } catch (e: Exception) {
            fishmod.utils.debug.Debug.LOGGER.warn("[UserColorStore] load failed: {}", e.toString())
        }
    }

    private fun save() {
        try {
            val file = File(FILE_PATH)
            file.parentFile?.mkdirs()
            FileWriter(file).use { writer -> GSON.toJson(data, writer) }
        } catch (e: Exception) {
            fishmod.utils.debug.Debug.LOGGER.warn("[UserColorStore] save failed: {}", e.toString())
        }
    }
}
