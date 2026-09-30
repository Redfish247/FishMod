package fishmod.utils.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import fishmod.utils.debug.FishDiag
import java.io.File
import java.io.FileReader
import java.io.FileWriter

object UserColorStore {

    private const val FILE_PATH = "config/fishmod-user-colors.json"
    private const val MAX_COLORS = 30
    private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()

    private data class Data(val colors: MutableList<Int> = ArrayList())

    private var data = Data()

    init { load() }

    @JvmStatic fun all(): List<Int> = data.colors

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
            FishDiag.fail("UserColorStore.1", "failed to load $FILE_PATH", e)
            fishmod.utils.debug.Debug.LOGGER.warn("[UserColorStore] load failed: {}", e.toString())
        }
    }

    private fun save() {
        try {
            val file = File(FILE_PATH)
            file.parentFile?.mkdirs()
            FileWriter(file).use { writer -> GSON.toJson(data, writer) }
        } catch (e: Exception) {
            FishDiag.fail("UserColorStore.2", "failed to save $FILE_PATH (${data.colors.size} colours)", e)
            fishmod.utils.debug.Debug.LOGGER.warn("[UserColorStore] save failed: {}", e.toString())
        }
    }
}
