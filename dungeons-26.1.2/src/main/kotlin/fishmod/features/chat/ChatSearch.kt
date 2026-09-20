package fishmod.features.chat

import fishmod.mixin.ChatHudInvoker
import fishmod.utils.config.values.FishSettings
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

object ChatSearch {

    @JvmStatic
    @Volatile
    var query: String = ""
        private set

    @JvmStatic
    val active: Boolean
        get() = FishSettings.chatFeatureEnabled && FishSettings.chatSearch && query.isNotBlank()

    @JvmStatic
    fun matches(line: Component): Boolean {
        if (!active) return true
        val plain = line.string.replace(COLOR_CODE, "")
        return plain.contains(query.trim(), ignoreCase = true)
    }

    @JvmStatic
    fun onQueryChanged(value: String) {
        query = value
        refresh()
    }

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
