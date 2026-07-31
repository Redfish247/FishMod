package fishmod.features

import fishmod.utils.config.values.FishSettings
import net.minecraft.network.chat.Component
import java.util.regex.Pattern

/** Pure predicate checked at the chat DISPLAY layer (`ChatHudMixin.addMessage`), not on `ON_GAME_MESSAGE` — that packet event short-circuits, so filtering there would eat trigger lines (e.g. "[BOSS] …") before splits/DungeonScore/Simon Says parsers see them. */
object ChatFilter {

    // "Friend > <name> joined." / "... left." — the friend-list online/offline notices.
    private val FRIEND_JOIN_LEAVE: Pattern = Pattern.compile("Friend > \\S+ (?:joined|left)\\.")

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
