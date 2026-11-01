package fishmod.features.other

import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack

// Caches SkyBlock equipment from the /equipment menu and draws it left of the inventory.
object EquipmentDisplay {
    // necklace, cloak, belt, gloves/bracelet
    private val TYPES = listOf(listOf("NECKLACE"), listOf("CLOAK"), listOf("BELT"), listOf("GLOVES", "BRACELET"))
    private val cached = arrayOfNulls<ItemStack>(4)

    @JvmStatic
    fun init() {
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen !is AbstractContainerScreen<*> || screen is InventoryScreen) return@register
            if (!screen.title.string.contains("Equipment")) return@register
            ScreenEvents.afterTick(screen).register { s ->
                FishDiag.guard("EquipmentDisplay.1", "equipment scan failed") { scan(s as AbstractContainerScreen<*>) }
            }
        }
    }

    private fun typeOf(st: ItemStack): Int {
        val lines = st.get(DataComponents.LORE)?.lines() ?: return -1
        val last = lines.asReversed().map { it.string.replace(Regex("§."), "").trim() }.firstOrNull { it.isNotEmpty() } ?: return -1
        val words = last.split(' ')
        return TYPES.indexOfFirst { t -> words.any { it in t } }
    }

    private fun scan(s: AbstractContainerScreen<*>) {
        val found = arrayOfNulls<ItemStack>(4)
        val menuSize = s.menu.slots.size - 36 // skip the player's own inventory
        for (i in 0 until menuSize.coerceAtLeast(0)) {
            val st = s.menu.slots[i].item
            if (st.isEmpty) continue
            val t = typeOf(st)
            if (t >= 0 && found[t] == null) found[t] = st.copy()
        }
        if (found.any { it != null }) for (i in 0..3) cached[i] = found[i] ?: ItemStack.EMPTY
    }

    private const val X = -19

    // player clicked the column: open the equipment menu (one click = one command)
    @JvmStatic
    fun click(mouseX: Double, mouseY: Double): Boolean {
        if (mouseX < X - 1 || mouseX >= X + 17 || mouseY < 7 || mouseY >= 8 + 72) return false
        fishmod.utils.Misc.executeCommand("stats")
        return true
    }

    @JvmStatic
    fun render(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, left: Int, top: Int) {
        val x = X
        for (i in 0..3) {
            val y = 8 + i * 18
            ctx.fill(x - 1, y - 1, x + 17, y + 17, 0x90000000.toInt())
            val st = cached[i] ?: continue
            if (st.isEmpty) continue
            ctx.item(st, x, y)
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16)
                ctx.setTooltipForNextFrame(Minecraft.getInstance().font, st, mouseX + left, mouseY + top)
        }
        if (cached.all { it == null } && mouseX >= x && mouseX < x + 16 && mouseY >= 8 && mouseY < 8 + 72)
            ctx.text(Minecraft.getInstance().font, "Open /equipment once", x - 40, 8 + 74, 0xffaaaaaa.toInt(), true)
    }
}
