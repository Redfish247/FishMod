package fishmod.features.general

import fishmod.compat.hud
import fishmod.features.ScreenTheme
import fishmod.mixin.FishChatInvoker
import fishmod.utils.TextStrip
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.FishDiag
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

// Chat channel tabs on top of the chat history (fishmod.dev #599); one click = one /chat command
object ChatTabs {
    enum class Tab(val label: String, val channel: String, val prefix: String?) {
        ALL("All", "all", null),
        PARTY("Party", "party", "Party >"),
        GUILD("Guild", "guild", "Guild >"),
        OFFICER("Officer", "officer", "Officer >"),
        COOP("Co-op", "coop", "Co-op >");

        fun enabled(): Boolean = when (this) {
            ALL -> true
            PARTY -> FishSettings.chatTabParty
            GUILD -> FishSettings.chatTabGuild
            OFFICER -> FishSettings.chatTabOfficer
            COOP -> FishSettings.chatTabCoop
        }
    }

    private const val H = 11
    private const val PAD = 4
    private const val GAP = 2

    @JvmStatic
    var selected: Tab = Tab.ALL
        private set

    @JvmStatic
    val on: Boolean
        get() = FishSettings.chatFeatureEnabled && FishSettings.chatTabs

    @JvmStatic
    val active: Boolean
        get() = on && selected != Tab.ALL && selected.enabled()

    @JvmStatic
    fun matches(line: Component): Boolean {
        val p = selected.prefix ?: return true
        return TextStrip.plain(line.string).trimStart().startsWith(p)
    }

    private fun tabs(): List<Tab> = Tab.entries.filter { it.enabled() }

    // Top edge of the visible chat lines (same geometry as Copy Chat)
    private fun chatTop(screenH: Int): Int {
        val chat = Minecraft.getInstance().hud?.chat ?: return screenH - 14
        val inv = chat as FishChatInvoker
        val lines = minOf(chat.linesPerPage, inv.visibleMessages.size).coerceAtLeast(1)
        val h = (lines * inv.invokeLineHeight() * inv.invokeChatScale()).toInt()
        return (screenH - 40 - h).coerceAtLeast(H + 2)
    }

    private inline fun layout(screenH: Int, each: (Tab, Int, Int, Int, Int) -> Unit) {
        val font = Minecraft.getInstance().font
        var x = 2
        val y = chatTop(screenH) - H - 1
        for (t in tabs()) {
            val w = font.width(t.label) + PAD * 2
            each(t, x, y, w, H)
            x += w + GAP
        }
    }

    @JvmStatic
    fun render(ctx: GuiGraphicsExtractor, screenH: Int, mouseX: Int, mouseY: Int) {
        if (!on) return
        val font = Minecraft.getInstance().font
        layout(screenH) { t, x, y, w, h ->
            val hover = mouseX in x until x + w && mouseY in y until y + h
            val bg = when {
                t == selected -> FishSettings.chatTabsSelectedColor
                hover -> FishSettings.chatTabsHoverColor
                else -> FishSettings.chatTabsBgColor
            }
            ScreenTheme.roundedRect(ctx, x, y, w, h, 3, bg)
            ctx.text(font, t.label, x + PAD, y + 2, FishSettings.chatTabsTextColor, false)
        }
    }

    @JvmStatic
    fun click(screenH: Int, mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (!on || button != 0) return false
        var hit: Tab? = null
        layout(screenH) { t, x, y, w, h ->
            if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h) hit = t
        }
        val t = hit ?: return false
        select(t)
        if (FishSettings.chatTabsSwitchChannel) {
            FishDiag.guard("ChatTabs.1", "chat tab /chat ${t.channel} failed") {
                Minecraft.getInstance().connection?.sendCommand("chat ${t.channel}")
            }
        }
        return true
    }

    private fun select(t: Tab) {
        if (t == selected) return
        selected = t
        val chat = Minecraft.getInstance().hud?.chat ?: return
        FishDiag.guard("ChatTabs.2", "chat tab refresh failed (${t.name})") {
            chat.resetChatScroll()
            (chat as FishChatInvoker).invokeRefresh()
        }
    }
}
