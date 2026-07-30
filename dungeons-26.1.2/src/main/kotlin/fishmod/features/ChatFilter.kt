package fishmod.features

import fishmod.utils.config.values.FishSettings
import net.minecraft.network.chat.Component
import java.util.regex.Pattern

/**
 * Decides which Hypixel chat lines to hide. This is a pure predicate — it is checked at the chat
 * DISPLAY layer (`ChatHudMixin.addMessage`), NOT on the `ON_GAME_MESSAGE` packet event.
 *
 * That distinction matters: dungeon splits, DungeonScore, Simon Says, etc. all parse chat via
 * `ON_GAME_MESSAGE`, and that event short-circuits — a handler that cancels a message starves
 * every handler after it. Filtering on the packet event would therefore eat trigger lines (e.g.
 * "[BOSS] …") before the splits parser sees them. Doing it at display time keeps parsing intact and
 * only suppresses the visible line.
 */
object ChatFilter {

    // "Friend > <name> joined." / "... left." — the friend-list online/offline notices.
    private val FRIEND_JOIN_LEAVE: Pattern = Pattern.compile("Friend > \\S+ (?:joined|left)\\.")

    /** @return true if this chat line should be hidden from the chat HUD. */
    @JvmStatic
    fun shouldHide(text: Component?): Boolean {
        if (!FishSettings.chatFilterEnabled || text == null) return false
        var s: String? = text.string
        if (s == null || s.isEmpty()) return false
        // getString() is already free of § codes, but strip any literal ones defensively.
        s = s.replace(Regex("§."), "").trim()
        if (s.isEmpty()) return false

        if (FishSettings.cfKillCombo && s.contains("Kill Combo")) return true
        if (FishSettings.cfBossMessages && s.contains("[BOSS]")) return true
        if (FishSettings.cfFriendJoinLeave && FRIEND_JOIN_LEAVE.matcher(s).find()) return true
        if (FishSettings.cfBazaar && s.startsWith("[Bazaar] Executing instant buy")) return true
        if (FishSettings.cfWarping && s == "Warping...") return true

        return false
    }
}
