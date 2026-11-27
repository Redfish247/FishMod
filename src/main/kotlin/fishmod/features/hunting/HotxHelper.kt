package fishmod.features.hunting

import fishmod.features.extras.*

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

object HotxHelper {
    private val TITLE = Regex("^Heart of the (Mountain|Forest)$")
    // from SkyHanni (HotxHandler): first lore line "Level N", missing means maxed; locked perks end with these
    private val LEVEL = Regex("^Level (\\d+)")
    private val LOCKED = Regex("Requires.*|.*Mountain!|.*Forest!|Click to unlock!")
    // from SkyHanni (HotmData / HotfData): perk name to max level
    private val HOTM_MAX = mapOf("Mining Speed" to 50, "Mining Fortune" to 50, "Titanium Insanium" to 50, "Luck of the Cave" to 45,
        "Efficient Miner" to 100, "Quick Forge" to 20, "Old-School" to 20, "Professional" to 140, "Mole" to 200, "Gem Lover" to 20,
        "Seasoned Mineman" to 100, "Fortunate Mineman" to 50, "Blockhead" to 20, "Keep It Cool" to 50, "Lonesome Miner" to 45,
        "Great Explorer" to 20, "Powder Buff" to 50, "Speedy Mineman" to 50, "Subterranean Fisher" to 40, "Sky Mall" to 1,
        "Precision Mining" to 1, "Front Loaded" to 1, "Daily Grind" to 1, "Daily Powder" to 1, "Pickobulus" to 3,
        "Mining Speed Boost" to 3, "Maniac Miner" to 3, "Sheer Force" to 3, "Tunnel Vision" to 3, "Core of the Mountain" to 10,
        "No Stone Unturned" to 50, "Strong Arm" to 100, "Steady Hand" to 100, "Warm Heart" to 50, "Surveyor" to 20, "Metal Head" to 20,
        "Rags to Riches" to 50, "Eager Adventurer" to 100, "Crystalline" to 50, "Gifts from the Departed" to 100, "Mining Master" to 10,
        "Dead Man's Chest" to 50, "Vanguard Seeker" to 50, "Mineshaft Mayhem" to 1, "Gemstone Infusion" to 1, "Miner's Blessing" to 1)
    private val HOTF_MAX = mapOf("Sweep" to 50, "Foraging Fortune" to 50, "Strength Boost" to 50, "Damage Boost" to 2, "Speed Boost" to 50,
        "Axe Toss" to 2, "Luck of the Forest" to 40, "Daily Wishes" to 100, "Lottery" to 2, "Foraging Madness" to 2, "Deep Waters" to 50,
        "Efficient Forager" to 100, "Collector" to 50, "Early Bird" to 2, "Precision Cutting" to 2, "Monster Hunter" to 2,
        "Tree Whisperer" to 2, "Homing Axe" to 2, "Forest Strength" to 50, "Beekeeper" to 2, "Hunter's Luck" to 50,
        "Galatea's Might" to 50, "Essence Fortune" to 50, "Forest Speed" to 50, "Maniac Slicer" to 2, "Half Empty" to 25,
        "Ricochet" to 10, "Half Full" to 25, "Center of the Forest" to 5)
    // from SkyHanni (HotmApi.PowderType heartPattern, HotfData whisperHeartPattern)
    private val TOTAL = Regex("^(Mithril|Gemstone|Glacite) Powder: ([\\d,]+)")
    private val WHISPERS = Regex("^Forest Whispers: ([\\d,]*)")
    // from SkyHanni (HotmData.perkCostPattern)
    private const val COST = "Cost"
    private val COST_LINE = Regex("([\\d,]+) (Mithril Powder|Gemstone Powder|Glacite Powder|Forest Whispers)")

    private var inMenu = false
    private var hotm = true
    private val totals = HashMap<String, Long>()
    private var tick = 0

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc -> FishDiag.guard("HotxHelper.1", "hotx tick failed") { onTick(mc) } }
        DrawHooks.INVENTORY_SLOT_AFTER.register { ctx, stack, x, y ->
            if (!inMenu || !S.hotxHelper || stack == null || stack.isEmpty) return@register
            val slot = DrawHooks.currentSlot ?: return@register
            if (slot.container is Inventory) return@register
            val (level, max) = perkLevel(stack) ?: return@register
            val maxed = level >= max
            ctx.fill(x, y, x + 16, y + 16, if (maxed) S.hotxMaxedColor else S.hotxLevelColor)
            if (max <= 1) return@register
            val text = level.toString()
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
        val t = if (S.hotxHelper && screen != null) TITLE.find(TextStrip.plain(screen.title.string)) else null
        inMenu = t != null
        if (t != null) hotm = t.groupValues[1] == "Mountain"
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

    private fun perkLevel(st: ItemStack): Pair<Int, Int>? {
        val max = (if (hotm) HOTM_MAX else HOTF_MAX)[TextStrip.plain(st.hoverName.string).trim()] ?: return null
        val raw = st.get(DataComponents.LORE)?.lines() ?: return null
        if (raw.isEmpty()) return null
        if (LOCKED.matches(TextStrip.plain(raw.last().string).trim())) return null
        val first = raw.first()
        val n = LEVEL.find(TextStrip.plain(first.string).trim())?.groupValues?.get(1)?.toIntOrNull() ?: return max to max
        // from SkyHanni (HotmData): aqua level text = Blue Egg bonus, real level is one lower
        val egg = hotm && aquaLevel(first)
        return (if (egg) n - 1 else n) to max
    }

    private fun aquaLevel(c: Component): Boolean {
        if (c.string.contains("§bLevel")) return true
        var aqua = false
        c.visit({ style, s -> if (s.contains("Level") && style.color == net.minecraft.network.chat.TextColor.fromLegacyFormat(net.minecraft.ChatFormatting.AQUA)) aqua = true; java.util.Optional.empty<Unit>() }, net.minecraft.network.chat.Style.EMPTY)
        return aqua
    }

    private fun num(s: String) = s.replace(",", "").toLongOrNull() ?: 0L

    private fun lore(st: ItemStack): List<String> =
        st.get(DataComponents.LORE)?.lines()?.map { TextStrip.plain(it.string).trim() } ?: emptyList()
}
