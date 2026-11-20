package fishmod.features.extras

import fishmod.compat.curScreen
import fishmod.utils.TextStrip
import fishmod.utils.debug.FishDiag
import fishmod.utils.rendering.DrawHooks
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import fishmod.features.extras.ExtrasSettings as S

// Heart of the Mountain / Forest: perk level tint + number, and your powder/whispers under the cost
object HotxHelper {
    // from Skyblocker (HotmPerkLevelAdder / HotfPerkLevelAdder): menu titles
    private val TITLE = Regex("^Heart of the (Mountain|Forest)$")
    // from Skyblocker (HeartOfTheXAdder): no "/max" means maxed
    private val LEVEL = Regex("^Level (\\d+)/?(\\d+)?")
    // from SkyHanni (HotmApi.PowderType heartPattern, HotfData whisperHeartPattern)
    private val TOTAL = Regex("^(Mithril|Gemstone|Glacite) Powder: ([\\d,]+)")
    private val WHISPERS = Regex("^Forest Whispers: ([\\d,]*)")
    // from SkyHanni (HotmData.perkCostPattern)
    private const val COST = "Cost"
    private val COST_LINE = Regex("([\\d,]+) (Mithril Powder|Gemstone Powder|Glacite Powder|Forest Whispers)")

    private var inMenu = false
    private val totals = HashMap<String, Long>()
    private var tick = 0

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc -> FishDiag.guard("HotxHelper.1", "hotx tick failed") { onTick(mc) } }
        DrawHooks.INVENTORY_SLOT_AFTER.register { ctx, stack, x, y ->
            if (!inMenu || !S.hotxHelper || stack == null || stack.isEmpty) return@register
            val slot = DrawHooks.currentSlot ?: return@register
            if (slot.container is Inventory || slot.index > 44) return@register
            val m = LEVEL.find(lore(stack).firstOrNull() ?: return@register) ?: return@register
            val maxed = m.groupValues[2].isEmpty()
            ctx.fill(x, y, x + 16, y + 16, if (maxed) S.hotxMaxedColor else S.hotxLevelColor)
            val text = m.groupValues[1]
            val font = Minecraft.getInstance().font
            ctx.text(font, text, x + 17 - font.width(text), y + 9, if (maxed) 0xFFFFAA00.toInt() else 0xFFFFFFFF.toInt(), true)
        }
        ItemTooltipCallback.EVENT.register(ItemTooltipCallback { _, _, _, lines ->
            if (!inMenu || !S.hotxHelper) return@ItemTooltipCallback
            try { addHave(lines) } catch (e: Exception) { FishDiag.fail("HotxHelper.2", "hotx tooltip failed", e) }
        })
    }

    private fun onTick(mc: Minecraft) {
        val screen = mc.curScreen as? AbstractContainerScreen<*>
        inMenu = S.hotxHelper && screen != null && TITLE.matches(TextStrip.plain(screen.title.string))
        if (!inMenu || screen == null || ++tick % 10 != 0) return
        for (slot in screen.menu.slots) {
            if (slot.container is Inventory) continue
            for (l in lore(slot.item)) {
                TOTAL.find(l)?.let { totals[it.groupValues[1] + " Powder"] = num(it.groupValues[2]) }
                WHISPERS.find(l)?.let { totals["Forest Whispers"] = num(it.groupValues[1]) }
            }
        }
    }

    private fun addHave(lines: MutableList<Component>) {
        val i = lines.indexOfFirst { TextStrip.plain(it.string).trim() == COST }
        if (i < 0 || i + 1 >= lines.size) return
        val m = COST_LINE.find(TextStrip.plain(lines[i + 1].string)) ?: return
        val have = totals[m.groupValues[2]] ?: return
        val need = num(m.groupValues[1])
        val color = if (have >= need) "§a" else "§c"
        // from SkyHanni (CurrencyPerHotxPerk): "You have" line
        lines.add(i + 2, Component.literal("§7You have $color" + "%,d".format(have) + " §7" + m.groupValues[2]))
    }

    private fun num(s: String) = s.replace(",", "").toLongOrNull() ?: 0L

    private fun lore(st: ItemStack): List<String> =
        st.get(DataComponents.LORE)?.lines()?.map { TextStrip.plain(it.string).trim() } ?: emptyList()
}
