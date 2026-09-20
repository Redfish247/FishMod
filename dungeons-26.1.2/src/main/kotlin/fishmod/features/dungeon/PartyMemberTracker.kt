package fishmod.features.dungeon

import fishmod.utils.Constants
import fishmod.utils.events.Events
import java.util.LinkedHashMap
import java.util.regex.Pattern

object PartyMemberTracker {

    private const val MAX = 24

    private val seen = object : LinkedHashMap<String, String>(16, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > MAX
    }

    private val JOIN = Pattern.compile("^(?:\\[[^]]+]\\s+)?(\\w{1,16}) joined the party\\.$")
    private val INVITE = Pattern.compile("^(?:\\[[^]]+]\\s+)?\\w{1,16} invited (?:\\[[^]]+]\\s+)?(\\w{1,16}) to the party!")

    @JvmStatic
    fun init() {
        Events.ON_GAME_MESSAGE.register { text ->
            val stripped = Constants.STRIP_COLOR_REGEX.replace(text.string, "")
            JOIN.matcher(stripped).let { if (it.find()) remember(it.group(1)) }
            INVITE.matcher(stripped).let { if (it.find()) remember(it.group(1)) }
            false
        }
        Events.ON_WORLD_CHANGE.register { seen.clear(); false }
    }

    @Synchronized
    private fun remember(name: String) {
        seen[name.lowercase()] = name
    }

    @JvmStatic
    @Synchronized
    fun resolve(fragment: String?): String? {
        if (fragment.isNullOrBlank()) return null
        val frag = fragment.lowercase()
        seen[frag]?.let { return it }
        var best: String? = null
        for ((key, name) in seen) {
            if (key.startsWith(frag) && (best == null || name.length < best!!.length)) best = name
        }
        return best
    }
}
