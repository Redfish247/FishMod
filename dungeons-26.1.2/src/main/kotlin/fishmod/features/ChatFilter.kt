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

    enum class Mode(val label: String) { PARTIAL("Partial"), EXACT("Exact"), REGEX("Regex") }

    class Entry(var enabled: Boolean = true, var text: String = "", var mode: Mode = Mode.PARTIAL) {
        @Transient private var key: String? = null
        @Transient private var pattern: Pattern? = null

        fun compiled(): Pattern? {
            if (key != text) { key = text; pattern = compile(text) }
            return pattern
        }

        fun matches(s: String): Boolean {
            if (text.isBlank()) return false
            return when (mode) {
                Mode.PARTIAL -> s.contains(text, true)
                Mode.EXACT -> s.equals(text, true)
                Mode.REGEX -> compiled()?.matcher(s)?.find() == true
            }
        }

        // Stored as one line: # = off, =text exact, /regex/ regex, else partial.
        fun encode(): String = (if (enabled) "" else "#") + when (mode) {
            Mode.PARTIAL -> text
            Mode.EXACT -> "=$text"
            Mode.REGEX -> "/$text/"
        }
    }

    @JvmStatic
    fun parse(raw: String): MutableList<Entry> {
        val lines = if ('\n' in raw) raw.split('\n') else raw.split(';')
        val out = ArrayList<Entry>()
        for (l in lines.map { it.trim() }.filter { it.isNotEmpty() }) {
            val on = !l.startsWith("#")
            val line = l.removePrefix("#").trim()
            out.add(when {
                line.length > 2 && line.startsWith("/") && line.endsWith("/") -> Entry(on, line.substring(1, line.length - 1), Mode.REGEX)
                line.startsWith("^") || line.endsWith("$") -> Entry(on, line, Mode.REGEX)
                line.startsWith("=") -> Entry(on, line.substring(1).trim(), Mode.EXACT)
                else -> Entry(on, line, Mode.PARTIAL)
            })
        }
        return out
    }

    @JvmStatic
    fun encode(entries: List<Entry>): String = entries.filter { it.text.isNotBlank() }.joinToString("\n") { it.encode() }

    private fun compile(p: String): Pattern? =
        try { Pattern.compile(p, Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE) } catch (e: PatternSyntaxException) { null }

    @Volatile private var customRaw: String? = null
    @Volatile private var customEntries: List<Entry> = emptyList()

    private fun customHides(s: String): Boolean {
        val raw = FishSettings.cfCustomPatterns
        if (raw != customRaw) { customEntries = parse(raw).filter { it.enabled }; customRaw = raw }
        return customEntries.any { it.matches(s) }
    }

    // Copies hide-only Chat Notification rules into Custom Messages and disables the originals.
    @JvmStatic
    fun importNotificationHides() {
        val rules = fishmod.features.chat.ChatRuleStore.rules().filter {
            it.enabled && it.hideMessage && it.filter.isNotBlank() && it.chatMessage.isBlank()
                && it.actionBarMessage.isBlank() && it.titleMessage.isBlank() && !it.soundEnabled
        }
        val entries = parse(FishSettings.cfCustomPatterns)
        for (r in rules) {
            val f = r.filter.trim()
            val e = when {
                r.regex && r.partialMatch -> Entry(true, f, Mode.REGEX)
                r.regex -> Entry(true, "^(?:$f)$", Mode.REGEX)
                r.partialMatch -> Entry(true, f, Mode.PARTIAL)
                else -> Entry(true, f, Mode.EXACT)
            }
            if (entries.none { it.text == e.text && it.mode == e.mode }) entries.add(e)
            r.enabled = false
        }
        FishSettings.cfCustomPatterns = encode(entries)
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
        return reason(s) != null
    }

    // Which filter would hide this (colour-stripped) line, or null; used by the editor's tester.
    @JvmStatic
    fun reason(s: String): String? {
        if (FishSettings.cfKillCombo && s.contains("Kill Combo")) return "Kill Combo"
        if (FishSettings.cfBossMessages && s.contains("[BOSS]")) return "Boss Messages"
        if (FishSettings.cfFriendJoinLeave && FRIEND_JOIN_LEAVE.matcher(s).find()) return "Friend Join/Leave"
        if (FishSettings.cfBazaar && s.startsWith("[Bazaar] Executing instant buy")) return "Bazaar"
        if (FishSettings.cfWarping && s == "Warping...") return "Warping"
        if (FishSettings.cfNoammSpam && SPAM_LIST.any { it.matcher(s).matches() }) return "Skyblock/Dungeon Spam"
        if (FishSettings.cfCommonSpam && COMMON_SPAM.any { it.matcher(s).matches() }) return "Common Hypixel Spam"
        if (FishSettings.cfCustom && customHides(s)) return "Custom Messages"
        return null
    }
}
