package fishmod.features.dungeon.f7.terminal

import fishmod.utils.Location
import fishmod.utils.Misc
import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.Events
import fishmod.mixin.accessors.HandledScreenAccessor
import fishmod.utils.rendering.DrawEvents
import fishmod.utils.sound.SoundManager
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import java.util.regex.Pattern

/**
 * F7 P3 Terminal Solver — Odin's `TerminalSolver` adapted to Fabric/26.1.2. Tracks the open terminal
 * via [ClientboundOpenScreenPacket] + [ClientboundContainerSetSlotPacket], recomputes the solution
 * from the mirrored slot array (see [TerminalHandlers]), highlights the solution slots in the chest
 * GUI, and optionally blocks incorrect clicks.
 */
object TerminalSolver {

    @Volatile var current: TerminalHandler? = null
        private set

    private val STARTS_WITH_LETTER = Pattern.compile("What starts with: '?(\\w+)'?")
    private val ACTIVATED = Pattern.compile("(.{1,16}) activated a terminal! \\((\\d)/(\\d)\\)")
    private val COLOR = Regex("§.")
    private var lastClick = 0L

    private val DYE_PATHS = arrayOf(
        "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
        "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black",
    )

    @JvmStatic
    fun init() {
        Events.ON_PACKET.register { packet ->
            when (packet) {
                is ClientboundOpenScreenPacket -> onOpen(packet)
                is ClientboundContainerSetContentPacket -> onSetContent(packet)
                is ClientboundContainerSetSlotPacket -> onSetSlot(packet)
            }
            false
        }
        Events.ON_WORLD_CHANGE.register { current = null; false }
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register { tickReload() }

        Events.ON_GAME_MESSAGE.register { text ->
            val m = ACTIVATED.matcher(COLOR.replace(text.string, ""))
            if (m.find() && m.group(1) == Minecraft.getInstance().player?.gameProfile?.name) {
                if (FishSettings.terminalSolverSound) SoundManager.ping("termSolved", 0)
            }
            false
        }

        DrawEvents.INVENTORY_SLOT_BEFORE.register { ctx, stack, x, y -> drawSlot(ctx, x, y, before = true) }
        DrawEvents.INVENTORY_SLOT_AFTER.register { ctx, stack, x, y -> drawSlot(ctx, x, y, before = false) }

        // Odin "Stop Tooltips" — no hover tooltips while a terminal is open (they cover the solution).
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register(
            net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback { _, _, _, lines ->
                if (FishSettings.terminalSolverEnabled && FishSettings.terminalStopTooltips && current != null
                    && Minecraft.getInstance().screen is AbstractContainerScreen<*>
                ) lines.clear()
            })
    }

    private fun onOpen(packet: ClientboundOpenScreenPacket) {
        if (!Location.inDungeon()) { current = null; return }
        val name = packet.title.string.let { COLOR.replace(it, "") }
        val type = TerminalType.entries.firstOrNull { name.startsWith(it.windowPrefix) }
        if (type == null) { current = null; return }
        if (current?.type == type) return
        current = when (type) {
            TerminalType.PANES -> PanesHandler()
            TerminalType.NUMBERS -> NumbersHandler()
            TerminalType.RUBIX -> RubixHandler()
            TerminalType.MELODY -> MelodyHandler()
            TerminalType.STARTS_WITH -> {
                val mm = STARTS_WITH_LETTER.matcher(name)
                if (mm.find()) StartsWithHandler(mm.group(1)) else null
            }
            TerminalType.SELECT -> {
                val path = DYE_PATHS.firstOrNull { name.contains(it.replace("_", " "), ignoreCase = true) }
                if (path != null) SelectAllHandler(path) else null
            }
        }
    }

    /**
     * The whole-window fill Hypixel sends right after the terminal opens. Without this the per-slot
     * updates alone never populate a full [TerminalHandler.items] array on a fresh terminal, so no
     * solution is ever computed and nothing highlights.
     */
    private fun onSetContent(packet: ClientboundContainerSetContentPacket) {
        val term = current ?: return
        if (packet.containerId() == 0) return // 0 = player inventory, not the terminal
        val items = packet.items()
        val n = minOf(items.size, term.type.windowSize)
        for (i in 0 until n) term.items[i] = items[i]
        term.handleSlotUpdate(term.type.windowSize - 1) // force the solution recompute
    }

    private fun onSetSlot(packet: ClientboundContainerSetSlotPacket) {
        val term = current ?: return
        val slot = packet.slot
        if (slot < 0 || slot >= term.type.windowSize) return
        term.items[slot] = packet.item
        if (term.handleSlotUpdate(slot)) { /* solution recomputed */ }
    }

    /** Called from HandledScreenMixin's mouseClicked HEAD. @return true to cancel the click. */
    @JvmStatic
    fun onMouseClick(button: Int, screen: AbstractContainerScreen<*>): Boolean {
        val term = current ?: return false
        if (!FishSettings.terminalSolverEnabled) return false
        val slot = (screen as HandledScreenAccessor).`fishmod$getHoveredSlot`() ?: return false
        if (slot.container is Inventory) return false
        lastClick = System.currentTimeMillis()
        term.isClicked = true
        val right = button == 1
        if (FishSettings.terminalBlockWrongClicks && !term.canClick(slot.index, right)) return true
        term.simulateClick(slot.index, right)
        return false
    }

    private fun drawSlot(ctx: GuiGraphicsExtractor, x: Int, y: Int, before: Boolean) {
        if (!FishSettings.terminalSolverEnabled) return
        val term = current ?: return
        if (term.type == TerminalType.MELODY && FishSettings.terminalStopMelody) return
        val mc = Minecraft.getInstance()
        val screen = mc.screen as? AbstractContainerScreen<*> ?: return
        val slot = screen.menu.slots.firstOrNull { it.x == x && it.y == y } ?: return
        if (slot.container is Inventory) return
        val idx = slot.index
        val inSol = idx in term.solution

        if (!inSol) {
            // Odin "Stop Rendering Wrong" — paint over non-solution terminal items on the AFTER pass.
            if (!before && FishSettings.terminalHideWrong && idx < term.type.windowSize
                && term.type != TerminalType.NUMBERS  // numbers panes are handled below / not distracting
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

    private fun slotColor(term: TerminalHandler, idx: Int): Int = when (term.type) {
        TerminalType.NUMBERS -> when (term.solution.indexOf(idx)) {
            0 -> FishSettings.terminalOrderColor1
            1 -> FishSettings.terminalOrderColor2
            else -> FishSettings.terminalOrderColor3
        }
        TerminalType.RUBIX -> {
            val needed = term.solution.count { it == idx }
            when (if (needed < 3) needed else needed - 5) {
                1 -> FishSettings.terminalRubixColor
                2 -> FishSettings.terminalRubixColor2
                -1 -> FishSettings.terminalRubixNeg1
                else -> FishSettings.terminalRubixNeg2
            }
        }
        TerminalType.MELODY -> FishSettings.terminalMelodyPointerColor
        TerminalType.STARTS_WITH -> FishSettings.terminalStartsWithColor
        TerminalType.SELECT -> FishSettings.terminalSelectColor
        TerminalType.PANES -> FishSettings.terminalHighlightColor
    }

    private fun tickReload() {
        val term = current ?: return
        if (term.isClicked && System.currentTimeMillis() - lastClick >= FishSettings.terminalReloadMs.coerceIn(300, 1000).toLong()) {
            term.isClicked = false
        }
    }

    @Suppress("unused")
    private fun debug(msg: String) = Misc.addChatMessage(Component.literal("§b[term] §7$msg"))
}
