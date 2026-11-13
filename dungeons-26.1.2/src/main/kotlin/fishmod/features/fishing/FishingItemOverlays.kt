package fishmod.features.fishing

import fishmod.features.fishing.FishingWorldSettings as W
import fishmod.features.item.fishmodCustomDataTag
import fishmod.utils.TextStrip
import fishmod.utils.debug.FishDiag
import fishmod.utils.rendering.DrawHooks
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import java.util.WeakHashMap

object FishingItemOverlays {
    private class Info(val book: Boolean, val parts: List<Pair<String, Int>>, val pct: Int)
    private val NONE = Info(false, emptyList(), -1)

    private val PART = Regex("^(ථ|ꨃ|࿉) (Hook NONE|Line NONE|Sinker NONE|.+ Hook|.+ Line|.+ Sinker)$")
    private val BOTTLES = mapOf("Empty Thunder Bottle" to 50_000.0, "Empty Storm Bottle" to 500_000.0, "Empty Hurricane Bottle" to 5_000_000.0)
    private val cache = WeakHashMap<ItemStack, Info>()
    private var cacheKey = ""
    private var books: Set<String> = emptySet()

    fun init() {
        DrawHooks.INVENTORY_SLOT_AFTER.register { ctx, stack, x, y ->
            if (!FishingSettings.fishing || !W.fishingItems) return@register
            if (!W.trashBooks && !W.rodParts && !W.thunderBottle) return@register
            if (stack == null || stack.isEmpty) return@register
            try { draw(ctx, stack, x, y) } catch (t: Throwable) { FishDiag.fail("FishingItemOverlays.1", "fishing slot overlay failed", t) }
        }
    }

    private fun draw(ctx: GuiGraphicsExtractor, stack: ItemStack, x: Int, y: Int) {
        val key = "${W.trashBooks}|${W.rodParts}|${W.thunderBottle}|${W.trashBooksList}"
        if (key != cacheKey) {
            cacheKey = key; cache.clear()
            books = W.trashBooksList.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        }
        val info = cache.getOrPut(stack) { compute(stack) }
        if (info === NONE) return
        val mc = Minecraft.getInstance()
        if (info.book) ctx.fill(x, y, x + 16, y + 16, W.trashBooksColor)
        if (info.parts.isNotEmpty()) {
            var yy = 0f
            for ((t, c) in info.parts) {
                ctx.pose().pushMatrix()
                ctx.pose().translate(x.toFloat(), y + yy)
                ctx.pose().scale(0.6f, 0.6f)
                ctx.text(mc.font, t, 0, 0, c or 0xFF000000.toInt(), true)
                ctx.pose().popMatrix()
                yy += 5.4f
            }
        }
        if (info.pct >= 0) {
            val t = "${info.pct}%"
            ctx.text(mc.font, t, x + 17 - mc.font.width(t), y + 9, 0xFF54FCFC.toInt(), true)
        }
    }

    private fun compute(stack: ItemStack): Info {
        val name = TextStrip.plain(stack.hoverName.string)
        val lore = stack.get(DataComponents.LORE)?.lines()
        var book = false
        var parts: List<Pair<String, Int>> = emptyList()
        var pct = -1

        if (W.trashBooks && books.isNotEmpty() && name == "Enchanted Book" && lore != null) {
            val first = lore.map { TextStrip.plain(it.string).trim() }.firstOrNull { it.isNotEmpty() && it != "Combinable in Anvil" }
            book = first != null && first.lowercase() in books
        }
        if (W.rodParts && lore != null) {
            val out = ArrayList<Pair<String, Int>>(3)
            for (l in lore) {
                val m = PART.find(TextStrip.plain(l.string)) ?: continue
                val body = m.groupValues[2]
                if (body.endsWith("NONE")) continue
                val abbr = body.split(' ').filter { it.isNotEmpty() }.joinToString("") { it.take(1).uppercase() }
                val color = l.siblings.firstNotNullOfOrNull { it.style.color?.value } ?: l.style.color?.value ?: 0xFFFFFF
                out.add(abbr to color)
                if (out.size == 3) break
            }
            parts = out
        }
        if (W.thunderBottle) {
            val max = BOTTLES[name]
            if (max != null) {
                val charge = stack.fishmodCustomDataTag()?.getDoubleOr("thunder_charge", 0.0) ?: 0.0
                pct = Math.floor(charge / max * 100).toInt().coerceIn(0, 100)
            }
        }
        return if (!book && parts.isEmpty() && pct < 0) NONE else Info(book, parts, pct)
    }
}
