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

// Locks player-inventory slots (0-8 hotbar, 9-35 main) against clicks, swaps and drops.
object SlotLocking {

    private val FILE = Paths.get(FolderUtility.CONFIG_PATH + "slot_locks.txt")
    private val locked = HashSet<Int>()
    private var loaded = false
    private var lastWarnAt = 0L

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

    /** True = cancel this click. */
    @JvmStatic
    fun onSlotClicked(slot: Slot?, button: Int, input: ContainerInput): Boolean {
        if (!FishSettings.slotLockingEnabled) return false
        ensureLoaded()
        val blocked = isLocked(slot) || (input == ContainerInput.SWAP && button in 0..8 && button in locked)
        if (blocked) warn()
        return blocked
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
        ctx.fill(x, y, x + 16, y + 16, (c and 0x00FFFFFF) or 0x40000000)
        ctx.fill(x, y, x + 16, y + 1, c)
        ctx.fill(x, y + 15, x + 16, y + 16, c)
        ctx.fill(x, y, x + 1, y + 16, c)
        ctx.fill(x + 15, y, x + 16, y + 16, c)
    }
}
