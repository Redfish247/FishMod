package fishmod.features.chat

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileReader
import java.io.FileWriter

/**
 * One chat-watch rule: filter text (plain substring/exact or regex) plus a set of outputs fired
 * on a match. Modeled after Skyblocker's `ChatRule` (github.com/SkyblockerMod/Skyblocker, MIT),
 * scaled down to the outputs FishMod actually renders (no toast-icon/sound-browser).
 */
data class ChatRule(
    var name: String = "New Rule",
    var enabled: Boolean = true,
    var filter: String = "",
    var regex: Boolean = false,
    var partialMatch: Boolean = true,
    var ignoreCase: Boolean = true,
    var hideMessage: Boolean = false,
    var chatMessage: String = "",
    var actionBarMessage: String = "",
    var titleMessage: String = "",
    var titleDurationMs: Long = 3000L,
    var soundEnabled: Boolean = false
)

/** Client-only chat-notification rules, persisted separately from [fishmod.utils.config.values.FishSettings] since it's a list of complex objects, not scalars. */
object ChatRuleStore {

    private const val FILE_PATH = "config/fishmod-chat-rules.json"
    private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()

    private data class Data(
        var masterEnabled: Boolean = false,
        var hudX: Int = 10,
        var hudY: Int = 400,
        var hudScale: Double = 1.0,
        val rules: MutableList<ChatRule> = ArrayList()
    )

    private var data = Data()

    init { load() }

    @JvmStatic fun isMasterEnabled(): Boolean = data.masterEnabled
    @JvmStatic fun setMasterEnabled(v: Boolean) { data.masterEnabled = v; save() }

    @JvmStatic fun hudX(): Int = data.hudX
    @JvmStatic fun setHudX(v: Int) { data.hudX = v; save() }
    @JvmStatic fun hudY(): Int = data.hudY
    @JvmStatic fun setHudY(v: Int) { data.hudY = v; save() }
    @JvmStatic fun hudScale(): Double = data.hudScale
    @JvmStatic fun setHudScale(v: Double) { data.hudScale = v; save() }

    @JvmStatic fun rules(): MutableList<ChatRule> = data.rules

    @JvmStatic
    fun addRule(after: ChatRule? = null): ChatRule {
        val rule = ChatRule()
        val idx = if (after != null) data.rules.indexOf(after) + 1 else data.rules.size
        data.rules.add(idx, rule)
        save()
        return rule
    }

    @JvmStatic
    fun removeRule(rule: ChatRule) {
        data.rules.remove(rule)
        save()
    }

    @JvmStatic
    fun save() {
        try {
            val file = File(FILE_PATH)
            file.parentFile?.mkdirs()
            FileWriter(file).use { writer -> GSON.toJson(data, writer) }
        } catch (ignored: Exception) {
        }
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
        } catch (ignored: Exception) {
        }
    }
}
