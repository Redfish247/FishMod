package fishmod.features

import fishmod.mixin.accessors.HandledScreenAccessor
import fishmod.utils.Keybinds
import fishmod.utils.config.FolderUtility
import fishmod.utils.config.values.FishSettings
import fishmod.utils.rendering.DrawEvents
import fishmod.utils.rendering.drawevents.SlotEvent
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

// Locks player-inventory slots (0-8 hotbar, 9-35 main) against being dropped.
object SlotLocking {

    private val FILE = Paths.get(FolderUtility.CONFIG_PATH + "slot_locks.txt")
    private val locked = HashSet<Int>()
    private var loaded = false
    private var lastWarnAt = 0L
    private var carriedFromLocked = false

    @JvmStatic
    fun init() {
        DrawEvents.INVENTORY_SLOT_AFTER.register(SlotEvent { ctx, _, x, y -> drawSlot(ctx, x, y) })
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        if (!Files.exists(FILE)) return
        try {
            Files.readAllLines(FILE).mapNotNullTo(locked) { it.trim().toIntOrNull() }
        } catch (ignored: IOException) {}
    }

    private fun save() {
        try {
            Files.createDirectories(FILE.parent)
            Files.writeString(FILE, locked.sorted().joinToString("\n"))
        } catch (ignored: IOException) {}
    }

    private fun invIndex(slot: Slot?): Int? {
        if (slot == null || slot.container !is Inventory) return null
        return slot.containerSlot.takeIf { it in 0..35 }
    }

    private fun isLocked(slot: Slot?): Boolean {
        ensureLoaded()
        return invIndex(slot)?.let { it in locked } == true
    }

    /** True = cancel this click. Only drops are blocked; moving/swapping locked items is allowed. */
    @JvmStatic
    fun onSlotClicked(slot: Slot?, slotId: Int, input: ContainerInput): Boolean {
        if (!FishSettings.slotLockingEnabled) return false
        ensureLoaded()
        val carried = Minecraft.getInstance().player?.containerMenu?.carried?.isEmpty == false
        // Clicking outside the window drops whatever is on the cursor.
        val blocked = when {
            input == ContainerInput.THROW -> isLocked(slot)
            slotId == -999 && carried -> carriedFromLocked
            else -> false
        }
        if (blocked) { warn(); return true }
        if (input == ContainerInput.PICKUP && slot != null) carriedFromLocked = !carried && isLocked(slot) && slot.hasItem()
        return false
    }

    /** True = cancel the in-world drop. */
    @JvmStatic
    fun onDrop(): Boolean {
        if (!FishSettings.slotLockingEnabled) return false
        ensureLoaded()
        val player = Minecraft.getInstance().player ?: return false
        val blocked = player.inventory.selectedSlot in locked
        if (blocked) warn()
        return blocked
    }

    @JvmStatic
    fun keyPressed(input: KeyEvent, screen: AbstractContainerScreen<*>): Boolean {
        if (!FishSettings.slotLockingEnabled) return false
        val key = Keybinds.slotLock ?: return false
        if (key.isUnbound || !key.matches(input)) return false
        val hovered = (screen as HandledScreenAccessor).`fishmod$getHoveredSlot`()
        val idx = invIndex(hovered) ?: return false
        ensureLoaded()
        if (!locked.add(idx)) locked.remove(idx)
        save()
        Minecraft.getInstance().player?.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, if (idx in locked) 1.4f else 0.8f)
        return true
    }

    private fun warn() {
        val now = System.currentTimeMillis()
        if (now - lastWarnAt < 1500) return
        lastWarnAt = now
        fishmod.utils.Misc.addChatMessage(Component.literal("§cThat slot is locked."))
    }

    private fun drawSlot(ctx: GuiGraphicsExtractor, x: Int, y: Int) {
        if (!FishSettings.slotLockingEnabled) return
        ensureLoaded()
        if (locked.isEmpty()) return
        val screen = Minecraft.getInstance().screen as? AbstractContainerScreen<*> ?: return
        val slot = screen.menu.slots.firstOrNull { it.x == x && it.y == y && it.container is Inventory } ?: return
        if (!isLocked(slot)) return
        val c = FishSettings.slotLockingColor
        val alpha = (FishSettings.slotLockingOpacity.coerceIn(0, 100) * 255 / 100) shl 24
        if (alpha != 0) ctx.fill(x, y, x + 16, y + 16, (c and 0x00FFFFFF) or alpha)
        if (!FishSettings.slotLockingOutline) return
        ctx.fill(x, y, x + 16, y + 1, c)
        ctx.fill(x, y + 15, x + 16, y + 16, c)
        ctx.fill(x, y, x + 1, y + 16, c)
        ctx.fill(x + 15, y, x + 16, y + 16, c)
    }
}
