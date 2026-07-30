package fishmod.utils

import fishmod.utils.config.values.FishSettings
import net.minecraft.text.Text

/** FishMod chat output with the configurable mod prefix; kept out of Misc so blade-addons' copy can't shadow it. */
object FishMsg {

    /** Formatted prefix, e.g. "§b§lFM §8> §r" (configurable, <=10 chars, via FishSettings.modPrefix). */
    @JvmStatic
    fun prefix(): String {
        if (!FishSettings.modPrefixEnabled) return ""
        var p = FishSettings.modPrefix
        if (p == null || p.isBlank()) p = "FM"
        if (p.length > 10) p = p.substring(0, 10)
        return "§b§l" + p + " §8> §r"
    }

    /** Sends a FishMod chat message with the configurable mod prefix. */
    @JvmStatic
    fun send(message: String) {
        Misc.addChatMessage(Text.literal(prefix() + message))
    }
}
