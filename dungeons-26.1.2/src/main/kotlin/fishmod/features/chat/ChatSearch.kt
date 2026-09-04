package fishmod.features.chat

import fishmod.mixin.ChatHudInvoker
import fishmod.utils.config.values.FishSettings
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

/**
 * Chat Search: a text field rendered along the bottom of the open chat screen (just above the
 * vanilla input line). While it holds a non-blank query, [fishmod.mixin.ChatHistoryMixin]'s
 * display-queue filter hides every scrollback line that doesn't contain the query — the lines stay
 * in the buffer and return the moment the query is cleared.
 */
object ChatSearch {

    /** Current query. Empty / blank = inactive (all lines shown). */
    @JvmStatic
    @Volatile
    var query: String = ""
        private set

    @JvmStatic
    val active: Boolean
        get() = FishSettings.chatFeatureEnabled && FishSettings.chatSearch && query.isNotBlank()

    /** True when [line] should be drawn given the current query. */
    @JvmStatic
    fun matches(line: Component): Boolean {
        if (!active) return true
        val plain = line.string.replace(COLOR_CODE, "")
        return plain.contains(query.trim(), ignoreCase = true)
    }

    /** EditBox responder — restash the query and rebuild the visible chat immediately. */
    @JvmStatic
    fun onQueryChanged(value: String) {
        query = value
        refresh()
    }

    /** Clear the query (e.g. when the chat screen closes) and restore the full buffer. */
    @JvmStatic
    fun clear() {
        if (query.isEmpty()) return
        query = ""
        refresh()
    }

    private fun refresh() {
        val chat = Minecraft.getInstance().gui?.chat ?: return
        (chat as ChatHudInvoker).invokeRefresh()
    }

    private val COLOR_CODE = fishmod.utils.Constants.STRIP_COLOR_REGEX
}
