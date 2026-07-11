package fishmod.features;

import fishmod.utils.config.values.FishSettings;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;

/**
 * Decides which Hypixel chat lines to hide. This is a pure predicate — it is checked at the chat
 * DISPLAY layer ({@code ChatHudMixin.addMessage}), NOT on the {@code ON_GAME_MESSAGE} packet event.
 *
 * That distinction matters: dungeon splits, DungeonScore, Simon Says, etc. all parse chat via
 * {@code ON_GAME_MESSAGE}, and that event short-circuits — a handler that cancels a message starves
 * every handler after it. Filtering on the packet event would therefore eat trigger lines (e.g.
 * "[BOSS] …") before the splits parser sees them. Doing it at display time keeps parsing intact and
 * only suppresses the visible line.
 */
public final class ChatFilter {

    private ChatFilter() {}

    // "Friend > <name> joined." / "... left." — the friend-list online/offline notices.
    private static final Pattern FRIEND_JOIN_LEAVE = Pattern.compile("Friend > \\S+ (?:joined|left)\\.");

    /** @return true if this chat line should be hidden from the chat HUD. */
    public static boolean shouldHide(Component text) {
        if (!FishSettings.chatFilterEnabled || text == null) return false;
        String s = text.getString();
        if (s == null || s.isEmpty()) return false;
        // getString() is already free of § codes, but strip any literal ones defensively.
        s = s.replaceAll("§.", "").trim();
        if (s.isEmpty()) return false;

        if (FishSettings.cfKillCombo       && s.contains("Kill Combo"))                       return true;
        if (FishSettings.cfBossMessages    && s.contains("[BOSS]"))                           return true;
        if (FishSettings.cfFriendJoinLeave && FRIEND_JOIN_LEAVE.matcher(s).find())            return true;
        if (FishSettings.cfBazaar          && s.startsWith("[Bazaar] Executing instant buy")) return true;
        if (FishSettings.cfWarping         && s.equals("Warping..."))                         return true;

        return false;
    }
}
