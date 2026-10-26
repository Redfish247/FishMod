package fishmod.features

import fishmod.utils.debug.FishDiag
import com.google.gson.Gson
import fishmod.utils.config.values.FishSettings
import net.minecraft.network.chat.Component
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

object ChatFilter {

    private val COLOR = fishmod.utils.Constants.STRIP_COLOR_REGEX

    private val FRIEND_JOIN_LEAVE: Pattern = Pattern.compile("Friend > \\S+ (?:joined|left)\\.")

    private val SPAM_LIST: List<Pattern> by lazy { load("chatSpam.json") }
    private val COMMON_SPAM: List<Pattern> by lazy { load("chatSpamCommon.json") }

    private fun load(file: String): List<Pattern> = try {
        ChatFilter::class.java.getResourceAsStream("/$file")!!.use { s ->
            Gson().fromJson(InputStreamReader(s, StandardCharsets.UTF_8), Array<String>::class.java)
                .mapNotNull { p -> runCatching { Pattern.compile(p) }.onFailure { FishDiag.fail("ChatFilter.1", "bad bundled spam regex '$p'", it) }.getOrNull() }
        }
    } catch (e: Exception) {
        FishDiag.fail("ChatFilter.2", "$file load failed", e)
        emptyList()
    }

    @Volatile private var customRaw: String? = null
    @Volatile private var customList: List<Pattern> = emptyList()
    @Volatile private var customText: List<String> = emptyList()

    private fun refreshCustom() {
        val raw = FishSettings.cfCustomPatterns
        if (raw == customRaw) return
        val lines = raw.split('\n', ';').map { it.trim() }.filter { it.isNotEmpty() }
        customText = lines
        customList = lines.mapNotNull { try { Pattern.compile(it) } catch (e: PatternSyntaxException) { null } }
        customRaw = raw
    }

    // Plain text hides any line containing it; regex must match the whole line.
    private fun customHides(s: String): Boolean {
        refreshCustom()
        return customText.any { s.contains(it) } || customList.any { it.matcher(s).matches() }
    }

    @Volatile private var lastBlank = false

    @JvmStatic
    fun shouldHide(text: Component?): Boolean {
        if (!FishSettings.chatFilterEnabled || text == null) return false
        val raw = text.string ?: return false
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
        if (FishSettings.cfCommonSpam && COMMON_SPAM.any { it.matcher(s).matches() }) return true
        if (FishSettings.cfCustom && customHides(s)) return true

        return false
    }
}
