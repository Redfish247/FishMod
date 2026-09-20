package fishmod.utils

import fishmod.utils.config.values.FishSettings
import net.minecraft.network.chat.Component

object FishMsg {

    @JvmStatic
    fun prefix(): String {
        if (!FishSettings.modPrefixEnabled) return ""
        var p = FishSettings.modPrefix
        if (p == null || p.isBlank()) p = "FM"
        if (p.length > 10) p = p.substring(0, 10)
        return "§b§l" + p + " §8> §r"
    }

    @JvmStatic
    fun send(message: String) {
        Misc.addChatMessage(Component.literal(prefix() + message))
    }
}
