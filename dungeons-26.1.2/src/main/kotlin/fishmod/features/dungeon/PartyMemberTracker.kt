package fishmod.features.dungeon

import fishmod.utils.Constants
import fishmod.utils.events.Events
import java.util.LinkedHashMap
import java.util.regex.Pattern

/**
 * Tracks the *exact* display names Hypixel uses for current/recent party members — including
 * /nick'd names — straight from party join/invite chat lines. The local tab list can lag or omit
 * players outside render range right after they join, which made `.kick <fragment>` resolve to
 * nothing (and Hypixel reply "That player does not exist") for a player kicked moments after
 * joining. This is a second, higher-priority source for [PartyCommandHandler.resolvePartyTarget].
 */
object PartyMemberTracker {

    private const val MAX = 24

    // lowercased -> exact display name as Hypixel rendered it, oldest evicted first
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

    /** Best display-name match for a possibly-truncated fragment, or null if nothing tracked matches. */
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
