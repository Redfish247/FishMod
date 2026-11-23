package fishmod.features.chat

import fishmod.features.general.*

import fishmod.compat.hud
import fishmod.features.ScreenTheme
import fishmod.mixin.FishChatInvoker
import fishmod.utils.TextStrip
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.FishDiag
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

object ChatTabs {
    enum class Tab(val label: String, val prefix: String?) {
        ALL("All", null),
        PARTY("Party", "Party >"),
        GUILD("Guild", "Guild >"),
        OFFICER("Officer", "Officer >"),
        COOP("Co-op", "Co-op >");

        fun enabled(): Boolean = when (this) {
            ALL -> true
            PARTY -> FishSettings.chatTabParty
            GUILD -> FishSettings.chatTabGuild
            OFFICER -> FishSettings.chatTabOfficer
            COOP -> FishSettings.chatTabCoop
        }
    }

    private const val H = 13
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
        val s = TextStrip.plain(line.string).trimStart()
        return s.startsWith(p) || SWITCHED.containsMatchIn(s)
    }

    private val SWITCHED = Regex("^You are now in the [A-Z-]+ channel")

    private fun tabs(): List<Tab> = Tab.entries.filter { it.enabled() }

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
            ctx.text(font, t.label, x + PAD, y + 3, FishSettings.chatTabsTextColor, false)
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
