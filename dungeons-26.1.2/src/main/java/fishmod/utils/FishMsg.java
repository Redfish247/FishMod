package fishmod.utils;

import fishmod.utils.config.values.FishSettings;
import net.minecraft.network.chat.Component;

/**
 * FishMod chat output with the configurable mod prefix. Lives in a FishMod-unique class so it is
 * NOT shadowed by blade-addons' copy of fishmod.utils.Misc (which lacks these methods).
 * Output still goes through Misc.addChatMessage, which exists in both jars.
 */
public final class FishMsg {
    private FishMsg() {}

    /** Formatted prefix, e.g. "§b§lFM §8> §r" (configurable, ≤10 chars, via FishSettings.modPrefix). */
    public static String prefix() {
        if (!FishSettings.modPrefixEnabled) return "";
        String p = FishSettings.modPrefix;
        if (p == null || p.isBlank()) p = "FM";
        if (p.length() > 10) p = p.substring(0, 10);
        return "§b§l" + p + " §8> §r";
    }

    /** Sends a FishMod chat message with the configurable mod prefix. */
    public static void send(String message) {
        Misc.addChatMessage(Component.literal(prefix() + message));
    }
}
