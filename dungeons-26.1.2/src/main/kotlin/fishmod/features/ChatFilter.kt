package fishmod.features

import com.google.gson.Gson
import fishmod.utils.config.values.FishSettings
import net.minecraft.network.chat.Component
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * Pure predicate checked at the chat DISPLAY layer (`ChatHudMixin.addMessage`), not on
 * `ON_GAME_MESSAGE` — that packet event short-circuits, so filtering there would eat trigger lines
 * (e.g. "[BOSS] …") before splits/DungeonScore/Simon Says parsers see them.
 *
 * Combines FishMod's own granular toggles with a bundled "useless messages" spam list
 * (`/chatSpam.json`) and a user regex list.
 */
object ChatFilter {

    private val COLOR = fishmod.utils.Constants.STRIP_COLOR_REGEX

    // "Friend > <name> joined." / "... left." — the friend-list online/offline notices.
    private val FRIEND_JOIN_LEAVE: Pattern = Pattern.compile("Friend > \\S+ (?:joined|left)\\.")

    /** Bundled spam list, compiled once. */
    private val SPAM_LIST: List<Pattern> by lazy {
        try {
            ChatFilter::class.java.getResourceAsStream("/chatSpam.json")!!.use { s ->
                Gson().fromJson(InputStreamReader(s, StandardCharsets.UTF_8), Array<String>::class.java)
                    .mapNotNull { runCatching { Pattern.compile(it) }.getOrNull() }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Volatile private var customRaw: String? = null
    @Volatile private var customList: List<Pattern> = emptyList()

    private fun custom(): List<Pattern> {
        val raw = FishSettings.cfCustomPatterns
        if (raw == customRaw) return customList
        customRaw = raw
        customList = raw.split('\n', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { try { Pattern.compile(it) } catch (e: PatternSyntaxException) { null } }
        return customList
    }

    @Volatile private var lastBlank = false

    @JvmStatic
    fun shouldHide(text: Component?): Boolean {
        if (!FishSettings.chatFeatureEnabled || !FishSettings.chatFilterEnabled || text == null) return false
        val raw = text.string ?: return false
        // getString() is already free of § codes, but strip any literal ones defensively.
        val s = raw.replace(COLOR, "").trim()

        if (s.isEmpty()) {
            val hide = FishSettings.cfCollapseBlank && lastBlank
            lastBlank = true
            return hide
        }
        lastBlank = false

        if (FishSettings.cfKillCombo && s.contains("Kill Combo")) return true
        if (FishSettings.cfBossMessages && s.contains("[BOSS]")) return true
        if (FishSettings.cfFriendJoinLeave && FRIEND_JOIN_LEAVE.matcher(s).find()) return true
        if (FishSettings.cfBazaar && s.startsWith("[Bazaar] Executing instant buy")) return true
        if (FishSettings.cfWarping && s == "Warping...") return true

        if (FishSettings.cfNoammSpam && SPAM_LIST.any { it.matcher(s).matches() }) return true
        if (FishSettings.cfCustom && custom().any { it.matcher(s).matches() }) return true

        return false
    }
}
