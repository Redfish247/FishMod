package fishmod.features.garden

import fishmod.utils.Misc
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import kotlin.math.floor
import fishmod.features.garden.GardenExtraSettings as X

// Shared helpers + init for the Working-On garden cards (display/highlight/chat only, nothing is clicked or sent for the player)
object GardenExtras {

    fun init() {
        GardenJeff.init()
        GardenPerks.init()
        GardenMenus.init()
        GardenPlots.init()
        ClientCommandRegistrationCallback.EVENT.register { d, _ ->
            d.register(ClientCommands.literal("fmmutations").executes { mutationsLink(); 1 })
        }
    }

    // Clickable + copied link to the mutations website
    private fun mutationsLink() {
        if (!X.gardenMutationsLinkOn) { Misc.addChatMessage(Component.literal("§7Mutations Link is off in the Farming tab.")); return }
        val url = X.gardenMutationsUrl.trim()
        val mc = Minecraft.getInstance()
        mc.keyboardHandler.setClipboard(url)
        val link = Component.literal("§b§n$url").withStyle { it.withClickEvent(ClickEvent.OpenUrl(java.net.URI.create(url))) }
        Misc.addChatMessage(Component.literal("§aMutations: ").append(link).append(Component.literal(" §7(copied)")))
    }

    fun screen(): AbstractContainerScreen<*>? = Minecraft.getInstance().screen as? AbstractContainerScreen<*>
    fun title(): String = screen()?.let { Garden.strip(it.title.string).trim() } ?: ""
    fun inMenu(vararg keys: String): Boolean = title().let { t -> t.isNotEmpty() && keys.any { t.contains(it, true) } }
    fun slots(): List<Slot> = screen()?.menu?.slots?.filter { !it.item.isEmpty && it.container !== Minecraft.getInstance().player?.inventory } ?: emptyList()
    fun name(stack: ItemStack) = Garden.strip(stack.hoverName.string).trim()

    // Round down so shown numbers never overstate
    fun floorShort(v: Double): String = when {
        v >= 1e9 -> "${floor(v / 1e8) / 10}B"
        v >= 1e6 -> "${floor(v / 1e5) / 10}M"
        v >= 1e3 -> "${floor(v / 1e2) / 10}k"
        else -> "${floor(v).toLong()}"
    }

    fun num(s: String) = s.replace(",", "").toDoubleOrNull() ?: 0.0

    val CROP_ITEMS: Map<String, Item> = linkedMapOf(
        "wheat" to Items.WHEAT, "carrot" to Items.CARROT, "potato" to Items.POTATO, "pumpkin" to Items.PUMPKIN,
        "melon" to Items.MELON_SLICE, "mushroom" to Items.RED_MUSHROOM, "cocoa" to Items.COCOA_BEANS, "cactus" to Items.CACTUS,
        "sugar cane" to Items.SUGAR_CANE, "nether wart" to Items.NETHER_WART,
    )
    fun cropItem(text: String): Item? = CROP_ITEMS.entries.firstOrNull { text.contains(it.key, true) }?.value

    // Small item badge at the slot's bottom-right
    fun badge(ctx: GuiGraphicsExtractor, item: Item, x: Int, y: Int, scale: Float = 0.6f) {
        val p = ctx.pose()
        p.pushMatrix(); p.translate(x + 16 - 16 * scale, y + 16 - 16 * scale); p.scale(scale, scale)
        ctx.item(ItemStack(item), 0, 0)
        p.popMatrix()
    }

    // Header line + indented children from the tab list; header matched anywhere in the line
    fun tabBlock(header: String): List<String> {
        val out = ArrayList<String>()
        var inside = false
        for (e in fishmod.utils.TabListCache.entries) {
            val s = e.stripped
            if (!inside) { if (header.isNotBlank() && s.contains(header, true)) { inside = true; out += s.trim() }; continue }
            if (s.isBlank() || !s.startsWith(" ")) break
            out += s.trim()
        }
        return out
    }
}
