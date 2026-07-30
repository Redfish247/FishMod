package fishmod.features

import fishmod.cosmetic.NameRewriter
import fishmod.cosmetic.NickState
import fishmod.utils.config.values.FishSettings
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.text.Text

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
        val mc = MinecraftClient.getInstance()
        if (mc.networkHandler != null) {
            for (e in mc.networkHandler!!.playerList) {
                val gp = e.profile
                if (gp.name.isNotEmpty() && gp.name.length >= 3) s.add(gp.name)
            }
        }
        val self = NickState.realName()
        if (self.isNotEmpty()) s.add(self)
        namesCache = s
        return s
    }

    private fun obfuscate(name: String): Text = Text.literal(name).styled { st -> st.withObfuscated(true) }

    private fun scramble(text: Text?, names: Set<String>): Text? {
        if (text == null || names.isEmpty()) return text
        val str = text.string
        var out = text
        for (n in names) {
            if (str.contains(n)) out = NameRewriter.replaceName(out, n, obfuscate(n))
        }
        return out
    }

    /** Chat: scramble just your own IGN. */
    @JvmStatic
    fun censorChat(text: Text?): Text? {
        if (!FishSettings.streamerMode || text == null) return text
        val self = NickState.realName()
        if (self.isEmpty() || !text.string.contains(self)) return text
        return NameRewriter.replaceName(text, self, obfuscate(self))
    }

    /** On-screen GUI text: scramble names in Party Finder menus, and in the tab list when enabled. */
    @JvmStatic
    fun forGui(text: Text?, inMenu: Boolean): Text? {
        if (!FishSettings.streamerMode || text == null) return text
        if (inMenu) return if (inPartyFinder()) scramble(text, onlineNames()) else text
        return if (FishSettings.streamerHideTab) scramble(text, onlineNames()) else text
    }

    private fun inPartyFinder(): Boolean {
        val mc = MinecraftClient.getInstance()
        val screen = mc.currentScreen
        if (screen !is HandledScreen<*>) return false
        val title = screen.title
        val t = title.string.lowercase()
        return t.contains("party finder") || t.contains("group builder") ||
                t.contains("parties") || t.contains("your party") || t.contains("party settings")
    }
}
