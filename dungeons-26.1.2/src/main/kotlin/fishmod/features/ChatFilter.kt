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
    @Volatile private var customContains: List<String> = emptyList()
    @Volatile private var customExact: List<String> = emptyList()
    @Volatile private var customRegex: List<Pattern> = emptyList()

    private fun refreshCustom() {
        val raw = FishSettings.cfCustomPatterns
        if (raw == customRaw) return
        val contains = ArrayList<String>(); val exact = ArrayList<String>(); val regex = ArrayList<Pattern>()
        for (line in raw.split('\n', ';').map { it.trim() }.filter { it.isNotEmpty() }) {
            when {
                line.length > 2 && line.startsWith("/") && line.endsWith("/") -> compile(line.substring(1, line.length - 1))?.let(regex::add)
                line.startsWith("^") || line.endsWith("$") -> compile(line)?.let(regex::add)
                line.startsWith("=") -> exact.add(line.substring(1).trim())
                else -> contains.add(line)
            }
        }
        customContains = contains; customExact = exact; customRegex = regex
        customRaw = raw
    }

    private fun compile(p: String): Pattern? =
        try { Pattern.compile(p, Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE) } catch (e: PatternSyntaxException) { null }

    // text = partial match, =text = whole line, /regex/ = regex anywhere; all ignore case.
    private fun customHides(s: String): Boolean {
        refreshCustom()
        return customContains.any { s.contains(it, true) } || customExact.any { s.equals(it, true) }
            || customRegex.any { it.matcher(s).find() }
    }

    // Copies hide-only Chat Notification rules into Custom Messages and disables the originals.
    @JvmStatic
    fun importNotificationHides() {
        val rules = fishmod.features.chat.ChatRuleStore.rules().filter {
            it.enabled && it.hideMessage && it.filter.isNotBlank() && it.chatMessage.isBlank()
                && it.actionBarMessage.isBlank() && it.titleMessage.isBlank() && !it.soundEnabled
        }
        val existing = FishSettings.cfCustomPatterns.split('\n').map { it.trim() }.toMutableList()
        existing.removeAll { it.isEmpty() }
        for (r in rules) {
            val f = r.filter.trim()
            val line = when {
                r.regex && r.partialMatch -> "/$f/"
                r.regex -> "/^(?:$f)$/"
                r.partialMatch -> f
                else -> "=$f"
            }
            if (line !in existing) existing.add(line)
            r.enabled = false
        }
        FishSettings.cfCustomPatterns = existing.joinToString("\n")
        FishSettings.cfCustom = true
        fishmod.features.chat.ChatRuleStore.save()
        fishmod.utils.Misc.addChatMessage(Component.literal(fishmod.utils.FishMsg.prefix() +
            "Chat Filter: imported ${rules.size} hide-only notification rules (originals disabled)."))
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
