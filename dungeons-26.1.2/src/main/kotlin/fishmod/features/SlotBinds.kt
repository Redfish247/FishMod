package fishmod.features

import com.mojang.blaze3d.platform.InputConstants
import fishmod.mixin.accessors.HandledScreenAccessor
import fishmod.mixin.accessors.KeyBindingAccessor
import fishmod.utils.Keybinds
import fishmod.utils.config.FolderUtility
import fishmod.utils.config.values.FishSettings
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.input.MouseButtonEvent
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
                }
            } else {
                if (slotId in binds || binds.values.contains(slotId)) {
                    binds.remove(slotId)
                    binds.entries.removeIf { it.value == slotId }
                    save()
                } else {
                    previousSlot = slotId
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

    @JvmStatic
    fun render(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, screen: AbstractContainerScreen<*>) {
        if (!FishSettings.slotBindsEnabled || !FishSettings.slotBindsShow || screen !is InventoryScreen) return
        ensureLoaded()
        if (binds.isEmpty()) return
        val acc = screen as HandledScreenAccessor
        val bgX = acc.bgX
        val bgY = acc.bgY
        val color = FishSettings.slotBindsColor
        val slots = screen.menu.slots

        // Compute hover geometrically from the passed cursor rather than trusting the vanilla
        // hoveredSlot, which isn't reliably populated during the render-state extraction pass.
        fun over(s: net.minecraft.world.inventory.Slot): Boolean =
            mouseX >= bgX + s.x && mouseX < bgX + s.x + 16 && mouseY >= bgY + s.y && mouseY < bgY + s.y + 16

        for ((inv, hb) in binds) {
            val s1 = slots.getOrNull(inv) ?: continue
            val s2 = slots.getOrNull(hb) ?: continue
            if (FishSettings.slotBindsHoverOnly && !over(s1) && !over(s2)) continue
            border(ctx, bgX + s1.x, bgY + s1.y, color)
            border(ctx, bgX + s2.x, bgY + s2.y, color)
        }
    }

    private fun border(ctx: GuiGraphicsExtractor, x: Int, y: Int, color: Int) {
        ctx.fill(x, y, x + 16, y + 1, color)
        ctx.fill(x, y + 15, x + 16, y + 16, color)
        ctx.fill(x, y, x + 1, y + 16, color)
        ctx.fill(x + 15, y, x + 16, y + 16, color)
    }
}
