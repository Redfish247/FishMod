package fishmod.features

import fishmod.cosmetic.NameRewriter
import fishmod.cosmetic.NickState
import fishmod.utils.config.values.FishSettings
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component

/**
 * Streamer Mode — anti-snipe name hiding. The real leak for streamers is the Party Finder menu (and
 * lobby tab), where viewers can read teammates' IGNs off-stream and follow/snipe. So this scrambles
 * actual player names with Minecraft's own `§k` obfuscated font — same width, unreadable:
 *
 *   • Party Finder / Group menus — always (while Streamer Mode is on): every online player's IGN in
 *     the menu is scrambled.
 *   • Your own IGN in chat — scrambled, so your messages don't expose your name.
 *   • Lobby tab list — optional ("Hide Tab Names"), for when you're sitting in a hub/lobby; leave it
 *     off in dungeons so you can still read your teammates.
 *
 * Only names that are actually online (in the tab list) are touched, so class/floor/other menu text
 * is never garbled. Render-only — nothing about what you send changes.
 */
object StreamerMode {

    private var namesCache: Set<String> = setOf()
    private var namesAt = 0L

    /** Online player IGNs (tab list + you), cached ~1s so we don't rebuild it on every text draw. */
    private fun onlineNames(): Set<String> {
        val now = System.currentTimeMillis()
        if (now - namesAt < 1000) return namesCache
        namesAt = now
        val s = HashSet<String>()
        val mc = Minecraft.getInstance()
        val connection = mc.getConnection()
        if (connection != null) {
            for (e in connection.getOnlinePlayers()) {
                val gp = e.getProfile()
                if (gp != null && gp.name() != null && gp.name().length >= 3) s.add(gp.name())
            }
        }
        val self = NickState.realName()
        if (self.isNotEmpty()) s.add(self)
        namesCache = s
        return s
    }

    private fun obfuscate(name: String): Component = Component.literal(name).withStyle { st -> st.withObfuscated(true) }

    private fun scramble(text: Component?, names: Set<String>): Component? {
        if (text == null || names.isEmpty()) return text
        val str = text.getString()
        var out = text
        for (n in names) {
            if (str.contains(n)) out = NameRewriter.replaceName(out, n, obfuscate(n))
        }
        return out
    }

    /** Chat: scramble just your own IGN. */
    @JvmStatic
    fun censorChat(text: Component?): Component? {
        if (!FishSettings.streamerMode || text == null) return text
        val self = NickState.realName()
        if (self.isEmpty() || !text.getString().contains(self)) return text
        return NameRewriter.replaceName(text, self, obfuscate(self))
    }

    /** On-screen GUI text: scramble names in Party Finder menus, and in the tab list when enabled. */
    @JvmStatic
    fun forGui(text: Component?, inMenu: Boolean): Component? {
        if (!FishSettings.streamerMode || text == null) return text
        if (inMenu) return if (inPartyFinder()) scramble(text, onlineNames()) else text
        return if (FishSettings.streamerHideTab) scramble(text, onlineNames()) else text
    }

    private fun inPartyFinder(): Boolean {
        val mc = Minecraft.getInstance()
        val screen = mc.screen
        if (screen !is AbstractContainerScreen<*>) return false
        val title = screen.getTitle()
        val t = title.getString().lowercase()
        return t.contains("party finder") || t.contains("group builder") ||
                t.contains("parties") || t.contains("your party") || t.contains("party settings")
    }
}
