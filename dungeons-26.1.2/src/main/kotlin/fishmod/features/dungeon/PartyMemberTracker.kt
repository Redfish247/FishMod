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
    private val CHAT = Pattern.compile("^Party > (?:\\[[^]]+]\\s+)?(\\w{1,16})[^:]*: ")
    private val FINDER = Pattern.compile("^Party Finder > (\\w{1,16}) joined the dungeon group!")
    private val LIST = Pattern.compile("^Party (?:Leader|Moderators|Members): (.+)$")
    private val LIST_NAME = Pattern.compile("(?:\\[[^]]+]\\s+)?(\\w{1,16}) ●")
    private val LEFT = Pattern.compile("^(?:\\[[^]]+]\\s+)?(\\w{1,16}) (?:has left|has been removed from|was removed from) the party")
    private val RESET = Pattern.compile("^(?:You left the party\\.|You have been kicked from the party|The party was disbanded|You are not currently in a party\\.)")

    @JvmStatic
    fun init() {
        Events.ON_GAME_MESSAGE.register { text ->
            val stripped = Constants.STRIP_COLOR_REGEX.replace(text.string, "")
            JOIN.matcher(stripped).let { if (it.find()) remember(it.group(1)) }
            INVITE.matcher(stripped).let { if (it.find()) remember(it.group(1)) }
            CHAT.matcher(stripped).let { if (it.find()) remember(it.group(1)) }
            FINDER.matcher(stripped).let { if (it.find()) remember(it.group(1)) }
            LIST.matcher(stripped).let { if (it.find()) { val m = LIST_NAME.matcher(it.group(1)); while (m.find()) remember(m.group(1)) } }
            LEFT.matcher(stripped).let { if (it.find()) forget(it.group(1)) }
            if (RESET.matcher(stripped).find()) clear()
            false
        }
    }

    @Synchronized
    private fun remember(name: String) {
        seen[name.lowercase()] = name
    }

    @Synchronized
    private fun forget(name: String) { seen.remove(name.lowercase()) }

    @Synchronized
    private fun clear() = seen.clear()

    @JvmStatic
    @Synchronized
    fun resolve(fragment: String?): String? {
        if (fragment.isNullOrBlank()) return null
        val frag = fragment.lowercase()
        seen[frag]?.let { return it }
        var best: String? = null
        val self = net.minecraft.client.Minecraft.getInstance().player?.gameProfile?.name()?.lowercase()
        for ((key, name) in seen) {
            if (key != self && key.startsWith(frag) && (best == null || name.length < best!!.length)) best = name
        }
        return best
    }
}
