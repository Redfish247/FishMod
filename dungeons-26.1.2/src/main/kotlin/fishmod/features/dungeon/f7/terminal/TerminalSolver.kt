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

/**
 * F7 P3 Terminal Solver. The open terminal is resolved from the window title and its board read
 * straight from the live container menu every tick (see [tickSync]); the solution is recomputed by
 * [TerminalHandlers] and its slots highlighted in the chest GUI, with optional wrong-click blocking.
 */
object TerminalSolver {

    @Volatile var current: TerminalHandler? = null
        private set

    /** Colour-stripped title [current] was built for. A new terminal of the same type but a
     *  different parameter (STARTS_WITH letter, SELECT colour) has a different title → rebuild. */
    @Volatile private var currentTitle = ""

    /** Set while a /fmtermsim board is open — feeds the highlight/click logic without a real terminal. */
    @Volatile var simActive = false
    @JvmStatic fun setSimTerminal(h: TerminalHandler?) { current = h; simActive = h != null }

    /** Called from HandledScreenMixin.removed — a real terminal closed, drop the stale board. */
    /**
     * A window re-open (Hypixel/the practice sim do this on every click) tears down the old screen
     * and shows a new one in the same frame, firing this in between. Dropping the board here is what
     * made the solver die after one click. Defer the reset a tick and only take it if we're really
     * out of a container GUI — a reopen will have set a new one by then.
     */
    @JvmStatic
    fun onScreenClosed() {
        if (simActive) return
        Minecraft.getInstance().schedule {
            if (!simActive && Minecraft.getInstance().screen !is AbstractContainerScreen<*>) reset()
        }
    }

    private fun reset() { current = null; currentTitle = "" }

    /**
     * Make [current] match the terminal named by [rawTitle], reusing the existing handler when the
     * title is unchanged (Hypixel re-opens the window on every click) and rebuilding when it differs
     * (a different terminal — including the practice sim cycling STARTS_WITH letters / SELECT
     * colours). Returns null (and resets) when the title isn't a terminal.
     */
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
            // capture the whole colour phrase, not a substring (else "gray" hits inside "light gray")
            TerminalType.SELECT -> SELECT_COLOR.matcher(name).let {
                if (it.find()) SelectAllHandler(it.group(1).trim().lowercase().replace("silver", "light gray").replace(' ', '_'))
                else null
            }
        }
        debug("build $type '${name.take(24)}'")
        return current
    }

    private val STARTS_WITH_LETTER = Pattern.compile("What starts with: '?(\\w+)'?")
    private val SELECT_COLOR = Pattern.compile("Select all the ([\\w ]+?) items!")
    private val ACTIVATED = Pattern.compile("(.{1,16}) activated a terminal! \\((\\d)/(\\d)\\)")
    private val COLOR = fishmod.utils.Constants.STRIP_COLOR_REGEX

    @JvmStatic
    fun init() {
        // board data comes from the live container menu in tickSync(), not packet mirroring (the practice sim uses a different container id and the id-tracking version dropped updates)
        Events.ON_PACKET.register { packet ->
            if (packet is ClientboundOpenScreenPacket) onOpen(packet)
            false
        }
        Events.ON_WORLD_CHANGE.register { reset(); false }
        Events.ON_PACKET.register { packet ->
            // don't reset on the close packet: Hypixel sends close+reopen on every click; onScreenClosed()'s deferred check handles a real exit
            if (packet is net.minecraft.network.protocol.game.ClientboundContainerClosePacket && !simActive) onScreenClosed()
            false
        }
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register { tickSync() }

        Events.ON_GAME_MESSAGE.register { text ->
            val m = ACTIVATED.matcher(COLOR.replace(text.string, ""))
            if (m.find() && m.group(1) == Minecraft.getInstance().player?.gameProfile?.name) {
                if (FishSettings.terminalSolverSound) SoundManager.ping("termSolved", 0)
            }
            false
        }

        DrawEvents.INVENTORY_SLOT_BEFORE.register { ctx, stack, x, y -> drawSlot(ctx, x, y, before = true) }
        DrawEvents.INVENTORY_SLOT_AFTER.register { ctx, stack, x, y -> drawSlot(ctx, x, y, before = false) }

        // "Stop Tooltips" — no hover tooltips while a terminal is open (they cover the solution).
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
        ensureHandler(packet.title.string) // fast path; tickSync would pick it up next tick anyway
    }

    /** Called from HandledScreenMixin's mouseClicked HEAD. @return true to cancel the click. */
    @JvmStatic
    fun onMouseClick(button: Int, screen: AbstractContainerScreen<*>): Boolean {
        val term = current ?: return false
        if (!FishSettings.terminalSolverEnabled && !simActive) return false
        val slot = (screen as HandledScreenAccessor).`fishmod$getHoveredSlot`() ?: return false
        if (slot.container is Inventory) return false
        // "First Click Protection": swallow clicks for the first N ms after the terminal opens (default 500, set to ~500 minus ping)
        if (!simActive && System.currentTimeMillis() - term.timeOpened < FishSettings.terminalFirstClickProtMs) return true
        val right = button == 1
        if (FishSettings.terminalBlockWrongClicks && !term.canClick(slot.index, right)) return true

        // "Middle Click GUI": re-issue as a middle-click (CLONE) so the item never lands on the cursor; left->middle, right stays right; real terminals only
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
        // no optimistic state change: the highlight clears when tickSync sees the server's slot update, so a dropped click just leaves it lit
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
            // "Stop Rendering Wrong" — paint over non-solution terminal items on the AFTER pass.
            if (!before && FishSettings.terminalHideWrong && idx < term.type.windowSize
                && term.type != TerminalType.NUMBERS  // numbers panes handled below
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
            else -> 0 // over-clicked back to target, or out of range
        }
        // row 0 / row 5 = column marker, everything else = pointer.
        TerminalType.MELODY -> if (idx / 9 == 0 || idx / 9 == 5) FishSettings.terminalMelodyColor
            else FishSettings.terminalMelodyPointerColor
        TerminalType.STARTS_WITH -> FishSettings.terminalStartsWithColor
        TerminalType.SELECT -> FishSettings.terminalSelectColor
        TerminalType.PANES -> FishSettings.terminalHighlightColor
    }

    /**
     * Ground-truth sync: every tick, resolve the handler from the open window's title and mirror the
     * live container menu into [TerminalHandler.items], then recompute the solution straight from
     * those slots. This is the whole data path — no packet mirroring, no click tracking — so the
     * overlay always reflects the terminal's own contents and a dropped/laggy click can't desync it.
     */
    private fun tickSync() {
        if (simActive) return // /fmtermsim feeds its own board
        if (!FishSettings.terminalSolverEnabled) return
        val screen = Minecraft.getInstance().screen as? AbstractContainerScreen<*> ?: return
        val term = ensureHandler(screen.title.string) ?: return

        val menu = screen.menu
        val n = term.type.windowSize
        if (menu.slots.size < n) return // menu not fully built yet

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

    /** One-line sample of the play-area items so a mismatch (wrong item type / name source) is visible. */
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

    /** Toggle with §f/fm badev termInfo§7. */
    private fun debug(msg: String) {
        if (Debug.termInfo) Misc.addChatMessage(Component.literal("§b[term] §7$msg"))
    }
}
