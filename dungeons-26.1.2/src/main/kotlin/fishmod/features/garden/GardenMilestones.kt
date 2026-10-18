package fishmod.features.garden

import fishmod.utils.Misc
import fishmod.utils.TabListCache
import fishmod.utils.rendering.DrawEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import fishmod.features.garden.GardenMilestoneSettings as S

// Crop milestone HUD, icon numbers, tooltip totals, clipboard export (formats from SkyHanni memory, unverified)
object GardenMilestones {

    private val TAB_LINE = Regex("^(.+?) (\\d+): ([\\d.]+)%")
    private val NAME_TIER = Regex("^([A-Za-z' ]+?) (\\d+)$")
    private val LORE_TIER = Regex("(?:Current )?(?:Tier|Level):? ?(\\d+)")

    private val tiers = LinkedHashMap<String, Int>()
    private var cropNow = ""
    private var tierNow = 0
    private var pctNow = 0.0
    private val samples = ArrayDeque<Pair<Long, Double>>()
    private var tick = 0

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (mc.player == null) return@register
            if (tick++ % 10 == 0) scanTab()
            if (tick % 10 == 5) scanMenu()
        }
        ItemTooltipCallback.EVENT.register(ItemTooltipCallback { stack, _, _, lines -> tooltip(stack, lines) })
        DrawEvents.INVENTORY_SLOT_AFTER.register { ctx, stack, x, y ->
            if (S.gardenIconNumbers) try { drawNumber(ctx, stack, x, y) } catch (_: Throwable) {}
        }
        Garden.hud("Crop Milestone", "garden_milestone", 150, 36,
            { S.gardenMilestoneHudX }, { S.gardenMilestoneHudX = it }, { S.gardenMilestoneHudY }, { S.gardenMilestoneHudY = it },
            { S.gardenMilestoneHudScale }, { S.gardenMilestoneHudScale = it },
            { S.gardenMilestoneHud && Garden.inGarden() && cropNow.isNotEmpty() }, { S.gardenMilestoneHud }) { hudLines() }
    }

    private fun scanTab() {
        if (!Garden.inGarden()) { cropNow = ""; return }
        for (e in TabListCache.entries) {
            val line = e.stripped.trim()
            if (line.startsWith("Garden Level")) continue
            val m = TAB_LINE.find(line) ?: continue
            val crop = m.groupValues[1]
            if (crop.length > 16) continue
            val tier = m.groupValues[2].toInt()
            val pct = m.groupValues[3].toDoubleOrNull() ?: continue
            if (crop != cropNow) samples.clear()
            cropNow = crop; tierNow = tier; pctNow = pct; tiers[crop] = tier
            val v = tier + pct / 100
            val now = System.currentTimeMillis()
            if (samples.isEmpty() || samples.last().second != v) samples.addLast(now to v)
            while (samples.size > 1 && now - samples.first().first > 300_000) samples.removeFirst()
            return
        }
        cropNow = ""
    }

    private fun menuKind(): String {
        val scr = Minecraft.getInstance().screen as? AbstractContainerScreen<*> ?: return ""
        val t = Garden.strip(scr.title.string)
        return when {
            t.contains("Crop Milestones") -> "milestone"
            t.contains("Crop Upgrades") -> "crop"
            t.contains("Composter Upgrades") -> "composter"
            else -> ""
        }
    }

    private fun scanMenu() {
        if (menuKind() != "milestone") return
        val scr = Minecraft.getInstance().screen as AbstractContainerScreen<*>
        for (s in scr.menu.slots) {
            val st = s.item
            if (st.isEmpty) continue
            val m = NAME_TIER.find(Garden.strip(st.hoverName.string).trim()) ?: continue
            if (m.groupValues[1].startsWith("Page")) continue
            tiers[m.groupValues[1]] = m.groupValues[2].toInt()
        }
    }

    private fun average() = if (tiers.isEmpty()) 0.0 else tiers.values.average()

    private fun hudLines(): List<String> {
        val c = Garden.legacy(S.gardenMilestoneColor)
        val out = arrayListOf("$c§lMilestone §7$cropNow $tierNow")
        out += "§7Next: §f${tierNow + 1} §8- §e${"%.1f".format(pctNow)}%" + (if (tiers.size > 1) "  §7Avg: §f${"%.1f".format(average())}" else "")
        if (S.gardenMilestoneEta && samples.size >= 2) {
            val (t0, v0) = samples.first()
            val (t1, v1) = samples.last()
            val hrs = (t1 - t0) / 3_600_000.0
            if (hrs > 20 / 3600.0 && v1 > v0) {
                val rate = (v1 - v0) / hrs
                out += "§7ETA: §b${eta(((tierNow + 1 - v1) / rate * 3600).toLong())} §8(${"%.2f".format(rate)} lvl/h)"
            }
        }
        return out
    }

    private fun eta(sec: Long): String = when {
        sec >= 3600 -> "${sec / 3600}h ${sec % 3600 / 60}m"
        sec >= 60 -> "${sec / 60}m ${sec % 60}s"
        else -> "${sec}s"
    }

    private fun iconNumber(stack: ItemStack): Int? {
        val name = Garden.strip(stack.hoverName.string).trim()
        return when (menuKind()) {
            "milestone" -> NAME_TIER.find(name)?.takeIf { !it.groupValues[1].startsWith("Page") }?.groupValues?.get(2)?.toIntOrNull()
            "crop", "composter" -> Garden.lore(stack).firstNotNullOfOrNull { LORE_TIER.find(it) }?.groupValues?.get(1)?.toIntOrNull()
            else -> null
        }
    }

    private fun drawNumber(ctx: GuiGraphicsExtractor, stack: ItemStack, x: Int, y: Int) {
        if (stack.isEmpty || !Garden.inGarden()) return
        val n = iconNumber(stack) ?: return
        ctx.text(Minecraft.getInstance().font, n.toString(), x + 1, y + 1, S.gardenIconNumberColor, true)
    }

    private fun tooltip(stack: ItemStack, lines: MutableList<Component>) {
        if (!S.gardenMilestoneTooltip || stack.isEmpty || menuKind() != "milestone" || tiers.isEmpty()) return
        if (iconNumber(stack) == null) return
        lines += Component.literal("")
        lines += Component.literal("§6§lFishMod Milestones")
        lines += Component.literal("§7Total levels: §f${tiers.values.sum()} §8(${tiers.size} crops)")
        lines += Component.literal("§7Average: §f${"%.2f".format(average())}")
    }

    // Clipboard export of known tiers
    fun copy() {
        val txt = if (tiers.isEmpty()) "no milestone data yet (open the Crop Milestones menu)"
        else tiers.entries.joinToString("\n") { "${it.key}: ${it.value}" } + "\nAverage: ${"%.2f".format(average())}"
        Minecraft.getInstance().keyboardHandler.clipboard = txt
        Misc.addChatMessage(Component.literal("§aCopied milestone data (${tiers.size} crops)."))
    }
}
