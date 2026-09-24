package fishmod.features.dungeon.f7.terminal

import fishmod.utils.Location
import fishmod.utils.Misc
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.Debug
import fishmod.utils.events.Events
import fishmod.mixin.accessors.HandledScreenAccessor
import fishmod.utils.rendering.DrawEvents
import fishmod.utils.sound.SoundManager
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import java.util.regex.Pattern

object TerminalSolver {

    @Volatile var current: TerminalHandler? = null
        private set

    @Volatile private var currentTitle = ""

    @Volatile var simActive = false
    @JvmStatic fun setSimTerminal(h: TerminalHandler?) { current = h; simActive = h != null }

    @JvmStatic
    fun onScreenClosed() {
        if (simActive) return
        Minecraft.getInstance().schedule {
            if (!simActive && Minecraft.getInstance().screen !is AbstractContainerScreen<*>) reset()
        }
    }

    private fun reset() { current = null; currentTitle = "" }

    private fun ensureHandler(rawTitle: String): TerminalHandler? {
        val name = COLOR.replace(rawTitle, "")
        val type = TerminalType.entries.firstOrNull { name.startsWith(it.windowPrefix) }
        if (type == null) { reset(); return null }
        if (current?.type == type && name == currentTitle) return current
        currentTitle = name
        current = when (type) {
            TerminalType.PANES -> PanesHandler()
            TerminalType.NUMBERS -> NumbersHandler()
            TerminalType.RUBIX -> RubixHandler()
            TerminalType.MELODY -> MelodyHandler()
            TerminalType.STARTS_WITH -> STARTS_WITH_LETTER.matcher(name).let { if (it.find()) StartsWithHandler(it.group(1)) else null }
            TerminalType.SELECT -> SELECT_COLOR.matcher(name).let {
                if (it.find()) SelectAllHandler(it.group(1).trim().lowercase().replace("silver", "light gray").replace(' ', '_'))
                else null
            }
        }
        debug("build $type '${name.take(24)}'")
        current?.let { if (!simActive) { lastType = it.type; lastOpenedMs = it.timeOpened } }
        return current
    }

    @Volatile private var lastType: TerminalType? = null
    @Volatile private var lastOpenedMs = 0L

    private fun termName(t: TerminalType) = when (t) {
        TerminalType.PANES -> "Panes"
        TerminalType.RUBIX -> "Rubix"
        TerminalType.NUMBERS -> "Numbers"
        TerminalType.STARTS_WITH -> "Starts With"
        TerminalType.SELECT -> "Select All"
        TerminalType.MELODY -> "Melody"
    }

    private fun onOwnTerminalDone() {
        val type = lastType ?: return
        lastType = null
        val secs = (System.currentTimeMillis() - lastOpenedMs) / 1000.0
        if (secs > 60.0 || fishmod.utils.dungeon.PracticeMode.active) return
        fishmod.features.dungeon.PbMessages.announce(FishSettings.pbMessagesTerminals, "term:${type.name}",
            Component.literal("§b${termName(type)} Terminal"), secs)
    }

    private val STARTS_WITH_LETTER = Pattern.compile("What starts with: '?(\\w+)'?")
    private val SELECT_COLOR = Pattern.compile("Select all the ([\\w ]+?) items!")
    private val ACTIVATED = Pattern.compile("(.{1,16}) activated a terminal! \\((\\d)/(\\d)\\)")
    private val COLOR = fishmod.utils.Constants.STRIP_COLOR_REGEX

    @JvmStatic
    fun init() {
        Events.ON_PACKET.register { packet ->
            if (packet is ClientboundOpenScreenPacket) onOpen(packet)
            false
        }
        Events.ON_WORLD_CHANGE.register { reset(); false }
        Events.ON_PACKET.register { packet ->
            if (packet is net.minecraft.network.protocol.game.ClientboundContainerClosePacket && !simActive) onScreenClosed()
            false
        }
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register { tickSync() }

        Events.ON_GAME_MESSAGE.register { text ->
            val m = ACTIVATED.matcher(COLOR.replace(text.string, ""))
            if (m.find() && m.group(1) == Minecraft.getInstance().player?.gameProfile?.name) {
                if (FishSettings.terminalSolverSound) SoundManager.ping("termSolved", 0)
                onOwnTerminalDone()
            }
            false
        }

        DrawEvents.INVENTORY_SLOT_BEFORE.register { ctx, stack, x, y -> drawSlot(ctx, x, y, before = true) }
        DrawEvents.INVENTORY_SLOT_AFTER.register { ctx, stack, x, y -> drawSlot(ctx, x, y, before = false) }

        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register(
            net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback { _, _, _, lines ->
                if ((FishSettings.terminalSolverEnabled || simActive) && FishSettings.terminalStopTooltips && current != null
                    && Minecraft.getInstance().screen is AbstractContainerScreen<*>
                ) lines.clear()
            })
    }

    private fun onOpen(packet: ClientboundOpenScreenPacket) {
        if (!Location.inDungeon()) { reset(); return }
        debug("open '${COLOR.replace(packet.title.string, "").take(30)}' id=${packet.containerId}")
        ensureHandler(packet.title.string)
    }

    @JvmStatic
    fun onMouseClick(button: Int, screen: AbstractContainerScreen<*>): Boolean {
        val term = current ?: return false
        if (!FishSettings.terminalSolverEnabled && !simActive) return false
        val slot = (screen as HandledScreenAccessor).`fishmod$getHoveredSlot`() ?: return false
        if (slot.container is Inventory) return false
        if (!simActive && System.currentTimeMillis() - term.timeOpened < FishSettings.terminalFirstClickProtMs) return true
        val right = button == 1
        if (FishSettings.terminalBlockWrongClicks && !term.canClick(slot.index, right)) return true

        if (FishSettings.terminalMiddleClickGui && !simActive && button != 2) {
            val mc = Minecraft.getInstance()
            val p = mc.player
            val gm = mc.gameMode
            if (p != null && gm != null) {
                val outBtn = if (button == 0) 2 else button
                val mode = if (outBtn == 2) ContainerInput.CLONE else ContainerInput.PICKUP
                gm.handleContainerInput(screen.menu.containerId, slot.index, outBtn, mode, p)
                return true
            }
        }
        return false
    }

    private fun drawSlot(ctx: GuiGraphicsExtractor, x: Int, y: Int, before: Boolean) {
        if (!FishSettings.terminalSolverEnabled && !simActive) return
        val term = current ?: return
        if (term.type == TerminalType.MELODY && FishSettings.terminalStopMelody) return
        val mc = Minecraft.getInstance()
        val screen = mc.screen as? AbstractContainerScreen<*> ?: return
        val slot = screen.menu.slots.firstOrNull { it.x == x && it.y == y } ?: return
        if (slot.container is Inventory) return
        val idx = slot.index
        val inSol = idx in term.solution

        if (!inSol) {
            if (!before && FishSettings.terminalHideWrong && idx < term.type.windowSize
                && term.type != TerminalType.NUMBERS
            ) {
                ctx.fill(x, y, x + 16, y + 16, FishSettings.terminalWrongCover)
            }
            return
        }

        if (before) {
            ctx.fill(x, y, x + 16, y + 16, slotColor(term, idx))
        } else {
            if (!FishSettings.terminalShowNumbers) return
            when (term.type) {
                TerminalType.RUBIX -> {
                    val needed = term.solution.count { it == idx }
                    val n = if (needed < 3) needed else needed - 5
                    if (n != 0) ctx.text(mc.font, n.toString(), x + 5, y + 4, 0xFFFFFFFF.toInt(), true)
                }
                TerminalType.NUMBERS -> {
                    val ord = term.solution.indexOf(idx)
                    if (ord in 0..2) ctx.text(mc.font, (ord + 1).toString(), x + 5, y + 4, 0xFFFFFFFF.toInt(), true)
                }
                else -> {}
            }
        }
    }

    fun slotColor(term: TerminalHandler, idx: Int): Int = when (term.type) {
        TerminalType.NUMBERS -> when (term.solution.indexOf(idx)) {
            0 -> FishSettings.terminalOrderColor1
            1 -> FishSettings.terminalOrderColor2
            else -> FishSettings.terminalOrderColor3
        }
        TerminalType.RUBIX -> when (term.solution.count { it == idx }.let { if (it < 3) it else it - 5 }) {
            1 -> FishSettings.terminalRubixColor
            2 -> FishSettings.terminalRubixColor2
            -1 -> FishSettings.terminalRubixNeg1
            -2 -> FishSettings.terminalRubixNeg2
            else -> 0
        }
        TerminalType.MELODY -> if (idx / 9 == 0 || idx / 9 == 5) FishSettings.terminalMelodyColor
            else FishSettings.terminalMelodyPointerColor
        TerminalType.STARTS_WITH -> FishSettings.terminalStartsWithColor
        TerminalType.SELECT -> FishSettings.terminalSelectColor
        TerminalType.PANES -> FishSettings.terminalHighlightColor
    }

    private fun tickSync() {
        if (simActive) return
        if (!FishSettings.terminalSolverEnabled) return
        val screen = Minecraft.getInstance().screen as? AbstractContainerScreen<*> ?: return
        val term = ensureHandler(screen.title.string) ?: return

        val menu = screen.menu
        val n = term.type.windowSize
        if (menu.slots.size < n) return

        var changed = false
        for (i in 0 until n) {
            val live = menu.slots[i].item
            if (term.items[i] !== live) { term.items[i] = live; changed = true }
        }

        if (changed || term.solution.isEmpty()) {
            term.handleSlotUpdate(n - 1)
            if (changed) {
                debug("sync ${term.type} items=${term.items.count { it != null && !it.isEmpty }} sol=${term.solution.size}")
                if (Debug.termInfo && term.solution.isEmpty()) dumpBoard(term, n)
            }
        }
    }

    private fun dumpBoard(term: TerminalHandler, n: Int) {
        val sample = (0 until n).mapNotNull { i ->
            val st = term.items[i] ?: return@mapNotNull null
            if (st.isEmpty) return@mapNotNull null
            val id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.item).path
            if (id == "black_stained_glass_pane" || id == "gray_stained_glass_pane") return@mapNotNull null
            "$i:$id${if (st.hasFoil()) "*" else ""}x${st.count}'${COLOR.replace(st.hoverName.string, "").trim().take(14)}'"
        }.take(8)
        debug("  " + sample.joinToString(" "))
    }

    private fun debug(msg: String) {
        if (Debug.termInfo) Misc.addChatMessage(Component.literal("§b[term] §7$msg"))
    }
}
