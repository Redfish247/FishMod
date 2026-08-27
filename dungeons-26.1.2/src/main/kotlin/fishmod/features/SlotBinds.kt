package fishmod.features

import com.mojang.blaze3d.platform.InputConstants
import fishmod.mixin.accessors.HandledScreenAccessor
import fishmod.mixin.accessors.KeyBindingAccessor
import fishmod.utils.Keybinds
import fishmod.utils.config.FolderUtility
import fishmod.utils.config.values.FishSettings
import fishmod.utils.rendering.DrawEvents
import fishmod.utils.rendering.drawevents.SlotEvent
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.ContainerInput
import org.lwjgl.glfw.GLFW
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Slot Binds (ported from NoammAddons' SlotBinding). Hold [Keybinds.slotBind] and click a hotbar
 * slot then an inventory slot to link them; afterwards shift-left-click either slot to hot-swap the
 * two stacks. Links persist to `slot_binds.txt`. Only active in the player's own inventory.
 */
object SlotBinds {

    private val FILE = Paths.get(FolderUtility.CONFIG_PATH + "slot_binds.txt")

    /** inventory-slot index -> hotbar-slot index (both are container slot ids; hotbar is 36..44). */
    private val binds = LinkedHashMap<Int, Int>()
    private var previousSlot: Int? = null
    private var loaded = false

    @JvmStatic
    fun init() {
        DrawEvents.INVENTORY_SLOT_AFTER.register(SlotEvent { ctx, _, x, y -> drawSlot(ctx, x, y) })
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        if (!Files.exists(FILE)) return
        try {
            for (line in Files.readAllLines(FILE)) {
                val p = line.split("\t", limit = 2)
                if (p.size != 2) continue
                val a = p[0].trim().toIntOrNull() ?: continue
                val b = p[1].trim().toIntOrNull() ?: continue
                binds[a] = b
            }
        } catch (ignored: IOException) {}
    }

    private fun save() {
        try {
            Files.createDirectories(FILE.parent)
            Files.writeString(FILE, binds.entries.joinToString("\n") { "${it.key}\t${it.value}" })
        } catch (ignored: IOException) {}
    }

    private fun bindKeyHeld(): Boolean {
        val key = (Keybinds.slotBind as KeyBindingAccessor?)?.boundKey ?: return false
        if (key == InputConstants.UNKNOWN) return false
        val handle = Minecraft.getInstance().window.handle()
        return when (key.type) {
            InputConstants.Type.MOUSE -> GLFW.glfwGetMouseButton(handle, key.value) == GLFW.GLFW_PRESS
            else -> InputConstants.isKeyDown(Minecraft.getInstance().window, key.value)
        }
    }

    private fun partnerOf(slot: Int): Int? = binds[slot] ?: binds.entries.firstOrNull { it.value == slot }?.key

    /** @return true to swallow the click. */
    @JvmStatic
    fun onMouseClick(click: MouseButtonEvent, screen: AbstractContainerScreen<*>): Boolean {
        if (!FishSettings.slotBindsEnabled || screen !is InventoryScreen) return false
        ensureLoaded()
        val slotId = (screen as HandledScreenAccessor).`fishmod$getHoveredSlot`()?.index ?: return false

        if (bindKeyHeld()) {
            val prev = previousSlot
            if (prev != null) {
                previousSlot = null
                if (prev == slotId) return true
                val prevHb = prev in 36..44
                val curHb = slotId in 36..44
                if (prevHb != curHb) {
                    val inv = if (prevHb) slotId else prev
                    val hb = if (prevHb) prev else slotId
                    binds[inv] = hb
                    save()
                    feedback("§aLinked§7 inv slot §f$inv§7 ↔ hotbar §f${hb - 36 + 1}")
                } else {
                    feedback("§cPick one hotbar slot and one inventory slot")
                }
            } else {
                if (slotId in binds || binds.values.contains(slotId)) {
                    binds.remove(slotId)
                    binds.entries.removeIf { it.value == slotId }
                    save()
                    feedback("§eUnlinked slot §f$slotId")
                } else {
                    previousSlot = slotId
                    feedback("§7Now click the slot to link with…")
                }
            }
            return true
        }

        if (click.button() != 0 || !hasShiftDown()) return false
        val partner = partnerOf(slotId) ?: return false
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return false
        val hotbarIndex = if (slotId in 36..44) slotId - 36 else partner - 36
        val invSlot = if (slotId in 36..44) partner else slotId
        mc.gameMode?.handleContainerInput(player.containerMenu.containerId, invSlot, hotbarIndex, ContainerInput.SWAP, player)
        return true
    }

    private fun hasShiftDown(): Boolean {
        val h = Minecraft.getInstance().window.handle()
        return GLFW.glfwGetKey(h, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
            GLFW.glfwGetKey(h, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS
    }

    @JvmStatic
    fun onClose() { previousSlot = null }

    private fun feedback(msg: String) =
        fishmod.utils.Misc.addChatMessage(Component.literal("§dSlot Binds §7» §r$msg"))

    /**
     * Drawn per-slot from [DrawEvents.INVENTORY_SLOT_AFTER] (same reliable pass the rarity
     * background uses). Coords here are GUI-local — [net.minecraft.world.inventory.Slot.x]/`y` are
     * in the same space, so no leftPos/topPos offset is needed.
     */
    private fun drawSlot(ctx: GuiGraphicsExtractor, x: Int, y: Int) {
        if (!FishSettings.slotBindsEnabled || !FishSettings.slotBindsShow) return
        val screen = Minecraft.getInstance().screen as? InventoryScreen ?: return
        ensureLoaded()
        if (binds.isEmpty()) return
        val slots = screen.menu.slots
        val self = slots.firstOrNull { it.x == x && it.y == y } ?: return
        val idx = self.index
        val partner = partnerOf(idx) ?: return
        val other = slots.getOrNull(partner) ?: slots.firstOrNull { it.index == partner } ?: return

        if (FishSettings.slotBindsHoverOnly) {
            val hov = (screen as HandledScreenAccessor).`fishmod$getHoveredSlot`()?.index
            if (hov != idx && hov != partner) return
        }

        val color = FishSettings.slotBindsColor
        if (FishSettings.slotBindsBorder) border(ctx, x, y, color)
        // Draw the connector once per pair, on the later-iterated (higher-index) endpoint so it
        // lands on top of the slots it crosses.
        if (FishSettings.slotBindsLine && idx > partner) {
            line(ctx, x + 8, y + 8, other.x + 8, other.y + 8, color)
        }
    }

    private fun border(ctx: GuiGraphicsExtractor, x: Int, y: Int, color: Int) {
        // 1px outline flush to the 16x16 slot.
        ctx.fill(x, y, x + 16, y + 1, color)
        ctx.fill(x, y + 15, x + 16, y + 16, color)
        ctx.fill(x, y, x + 1, y + 16, color)
        ctx.fill(x + 15, y, x + 16, y + 16, color)
    }

    /** 1px diagonal connector as a run of single pixels stepped along the path. */
    private fun line(ctx: GuiGraphicsExtractor, x1: Int, y1: Int, x2: Int, y2: Int, color: Int) {
        val dx = x2 - x1
        val dy = y2 - y1
        val steps = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
        if (steps == 0) return
        for (i in 0..steps) {
            val x = x1 + dx * i / steps
            val y = y1 + dy * i / steps
            ctx.fill(x, y, x + 1, y + 1, color)
        }
    }
}
