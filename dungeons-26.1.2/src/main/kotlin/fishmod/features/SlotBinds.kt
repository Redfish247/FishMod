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

object SlotBinds {

    private const val DEFAULT = "Default"
    private val FILE = Paths.get(FolderUtility.CONFIG_PATH + "slot_binds.txt")

    private val profiles = LinkedHashMap<String, LinkedHashMap<Int, Int>>()
    private var previousSlot: Int? = null
    private var loaded = false

    @JvmStatic
    fun init() {
        DrawEvents.INVENTORY_SLOT_AFTER.register(SlotEvent { ctx, _, x, y -> drawSlot(ctx, x, y) })
    }

    private fun activeName(): String = FishSettings.slotBindsProfile.trim().ifEmpty { DEFAULT }

    private val binds: LinkedHashMap<Int, Int>
        get() {
            ensureLoaded()
            return profiles.getOrPut(activeName()) { LinkedHashMap() }
        }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        profiles.getOrPut(DEFAULT) { LinkedHashMap() }
        if (!Files.exists(FILE)) return
        try {
            var current = profiles.getValue(DEFAULT)
            for (raw in Files.readAllLines(FILE)) {
                val line = raw.trim()
                if (line.isEmpty()) continue
                if (line.startsWith("[") && line.endsWith("]")) {
                    val name = line.substring(1, line.length - 1).trim().ifEmpty { DEFAULT }
                    current = profiles.getOrPut(name) { LinkedHashMap() }
                    continue
                }
                val p = line.split("\t", limit = 2)
                if (p.size != 2) continue
                val a = p[0].trim().toIntOrNull() ?: continue
                val b = p[1].trim().toIntOrNull() ?: continue
                current[a] = b
            }
        } catch (ignored: IOException) {}
    }

    private fun save() {
        try {
            Files.createDirectories(FILE.parent)
            val keep = profiles.filter { it.value.isNotEmpty() || it.key == DEFAULT || it.key == activeName() }
            val text = keep.entries.joinToString("\n") { (name, map) ->
                "[$name]\n" + map.entries.joinToString("\n") { "${it.key}\t${it.value}" }
            }
            Files.writeString(FILE, text)
        } catch (ignored: IOException) {}
    }

    @JvmStatic
    fun profileNames(): List<String> {
        ensureLoaded()
        return profiles.filter { it.value.isNotEmpty() || it.key == DEFAULT || it.key == activeName() }.keys.toList()
    }

    @JvmStatic
    fun newProfile() {
        ensureLoaded()
        var n = 1
        while (profiles.containsKey("Profile $n")) n++
        val name = "Profile $n"
        profiles[name] = LinkedHashMap()
        FishSettings.slotBindsProfile = name
        save()
        feedback("§aNew profile §f$name")
    }

    @JvmStatic
    fun deleteActiveProfile() {
        ensureLoaded()
        val name = activeName()
        if (name == DEFAULT) {
            profiles.getValue(DEFAULT).clear()
            save()
            feedback("§eCleared §f$DEFAULT")
            return
        }
        profiles.remove(name)
        FishSettings.slotBindsProfile = DEFAULT
        profiles.getOrPut(DEFAULT) { LinkedHashMap() }
        save()
        feedback("§cDeleted profile §f$name")
    }

    @JvmStatic
    fun cycleProfile() {
        ensureLoaded()
        val names = profileNames()
        if (names.size < 2) { feedback("§7Only one profile"); return }
        val next = names[(names.indexOf(activeName()) + 1).mod(names.size)]
        FishSettings.slotBindsProfile = next
        feedback("§aProfile → §f$next §7(${profiles[next]?.size ?: 0} binds)")
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

    private fun drawSlot(ctx: GuiGraphicsExtractor, x: Int, y: Int) {
        if (!FishSettings.slotBindsEnabled || !FishSettings.slotBindsShow) return
        val screen = Minecraft.getInstance().screen as? InventoryScreen ?: return
        ensureLoaded()
        if (binds.isEmpty()) return
        val slots = screen.menu.slots
        val self = DrawEvents.currentSlot ?: slots.firstOrNull { it.x == x && it.y == y } ?: return
        val idx = self.index
        // Lines are drawn from the inventory side so a hotbar slot with several binds gets one line per bind.
        val invPartner = binds[idx]
        val partners = if (invPartner != null) listOf(invPartner) else binds.entries.filter { it.value == idx }.map { it.key }
        if (partners.isEmpty()) return

        if (FishSettings.slotBindsHoverOnly) {
            val hov = (screen as HandledScreenAccessor).`fishmod$getHoveredSlot`()?.index
            if (hov != idx && hov !in partners) return
        }

        val color = FishSettings.slotBindsColor
        if (FishSettings.slotBindsBorder) border(ctx, x, y, color)
        if (FishSettings.slotBindsLine && invPartner != null) {
            val other = slots.getOrNull(invPartner) ?: slots.firstOrNull { it.index == invPartner } ?: return
            line(ctx, x + 8, y + 8, other.x + 8, other.y + 8, color)
        }
    }

    private fun border(ctx: GuiGraphicsExtractor, x: Int, y: Int, color: Int) {
        ctx.fill(x, y, x + 16, y + 1, color)
        ctx.fill(x, y + 15, x + 16, y + 16, color)
        ctx.fill(x, y, x + 1, y + 16, color)
        ctx.fill(x + 15, y, x + 16, y + 16, color)
    }

    private fun line(ctx: GuiGraphicsExtractor, x1: Int, y1: Int, x2: Int, y2: Int, color: Int) {
        val dx = x2 - x1
        val dy = y2 - y1
        val steps = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
        if (steps == 0) return
        var runX = x1
        var runY = y1
        var runLen = 1
        val horizontal = kotlin.math.abs(dx) >= kotlin.math.abs(dy)
        for (i in 1..steps) {
            val x = x1 + dx * i / steps
            val y = y1 + dy * i / steps
            val extends = if (horizontal) y == runY else x == runX
            if (extends) { runLen++; continue }
            fillRun(ctx, runX, runY, runLen, horizontal, dx, dy, color)
            runX = x; runY = y; runLen = 1
        }
        fillRun(ctx, runX, runY, runLen, horizontal, dx, dy, color)
    }

    private fun fillRun(ctx: GuiGraphicsExtractor, x: Int, y: Int, len: Int, horizontal: Boolean, dx: Int, dy: Int, color: Int) {
        if (horizontal) {
            val x0 = if (dx >= 0) x else x - len + 1
            ctx.fill(x0, y, x0 + len, y + 1, color)
        } else {
            val y0 = if (dy >= 0) y else y - len + 1
            ctx.fill(x, y0, x + 1, y0 + len, color)
        }
    }
}
