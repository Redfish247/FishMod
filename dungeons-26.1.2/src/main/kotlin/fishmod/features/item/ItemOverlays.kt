package fishmod.features.item

import fishmod.features.extras.ExtrasSettings as S
import fishmod.utils.Location
import fishmod.utils.debug.FishDiag
import fishmod.utils.rendering.DrawEvents
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Skyblocker-style slot text (book level, pet level, minion tier, cake year) + obtained date tooltip
object ItemOverlays {
    private val PET_LVL = Regex("^\\[Lvl (\\d+)]")
    private val MINION_TIER = Regex("_GENERATOR_(\\d+)$")
    private val OLD_STAMP = SimpleDateFormat("M/d/yy h:mm a", Locale.US)
    private val OUT_DATE = SimpleDateFormat("MMM d, yyyy", Locale.US)

    @JvmStatic
    fun init() {
        DrawEvents.INVENTORY_SLOT_AFTER.register { ctx, stack, x, y ->
            if (!S.overlayItemLevels || !Location.inSkyblock()) return@register
            try { draw(ctx, stack, x, y) } catch (t: Throwable) { FishDiag.fail("ItemOverlays.1", "item overlay draw failed", t) }
        }
        ItemTooltipCallback.EVENT.register(ItemTooltipCallback { stack, _, _, lines ->
            if (!S.obtainedDateTooltip || !Location.inSkyblock()) return@ItemTooltipCallback
            try { obtained(stack)?.let { lines.add(Component.literal("§7Obtained: §f$it")) } }
            catch (t: Throwable) { FishDiag.fail("ItemOverlays.2", "obtained date tooltip failed", t) }
        })
    }

    private fun overlayText(stack: ItemStack): String? {
        val tag = stack.fishmodCustomDataTag() ?: return null
        val id = tag.getString("id").orElse("")
        if (S.overlayBookLevel && (id == "ENCHANTED_BOOK" || stack.`is`(Items.ENCHANTED_BOOK))) {
            val ench = tag.getCompound("enchantments").orElse(null) ?: return null
            val keys = ench.keySet()
            if (keys.size != 1) return null
            val key = keys.first()
            val lvl = ench.getInt(key).orElse(0).takeIf { it > 0 } ?: ench.getString(key).orElse("").toIntOrNull() ?: return null
            return lvl.toString()
        }
        if (S.overlayPetLevel && id == "PET") {
            return PET_LVL.find(stack.hoverName.string)?.groupValues?.get(1)
        }
        if (S.overlayMinionTier) MINION_TIER.find(id)?.let { return it.groupValues[1] }
        if (S.overlayCakeYear && id == "NEW_YEAR_CAKE") {
            return tag.getInt("new_years_cake").orElse(0).takeIf { it > 0 }?.toString()
        }
        return null
    }

    private fun draw(ctx: GuiGraphicsExtractor, stack: ItemStack?, x: Int, y: Int) {
        if (stack == null || stack.isEmpty || stack.count > 1) return
        val text = overlayText(stack) ?: return
        val font = Minecraft.getInstance().font
        ctx.text(font, text, x + 17 - font.width(text), y + 9, S.overlayTextColor, true)
    }

    private fun obtained(stack: ItemStack): String? {
        val tag = stack.fishmodCustomDataTag() ?: return null
        val ms = tag.getLong("timestamp").orElse(0L)
        if (ms > 0L) return OUT_DATE.format(Date(ms))
        val raw = tag.getString("timestamp").orElse("")
        if (raw.isEmpty()) return null
        return try { OUT_DATE.format(OLD_STAMP.parse(raw)) } catch (_: Exception) { raw }
    }
}
