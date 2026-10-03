package fishmod.features.mining

import fishmod.utils.rendering.DrawEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents
import fishmod.features.mining.MiningSettings as S

// Commission HUD from the tab list, completion title, and completed-commission highlight in the Commissions menu
object Commissions {

    class Comm(val name: String, val progress: String) {
        val done get() = progress.equals("DONE", true) || progress == "100%"
    }

    private val LINE = Regex("""^(.+?): (DONE|[\d.]+%)$""")
    @JvmStatic var current: List<Comm> = emptyList(); private set
    private var tick = 0

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (mc.player == null || tick++ % 10 != 0) return@register
            if (!Mining.inMiningIsland()) { current = emptyList(); return@register }
            val next = Mining.tabWidget("Commissions:").mapNotNull { LINE.find(it) }.map { Comm(it.groupValues[1], it.groupValues[2]) }
            // Title only when the same commission flips to done (not on first read)
            if (S.miningCommTitle) for (c in next) {
                val before = current.firstOrNull { it.name == c.name }
                if (c.done && before != null && !before.done) Mining.title(S.miningCommTitleText, "§e${c.name}", 2000, true)
            }
            current = next
        }
        MiningHuds.reg("Commissions", "mining_comms", 160, 50,
            { S.miningCommHudX }, { S.miningCommHudX = it }, { S.miningCommHudY }, { S.miningCommHudY = it },
            { S.miningCommHudScale }, { S.miningCommHudScale = it },
            { S.miningCommHud && current.isNotEmpty() }, ::lines)

        DrawEvents.INVENTORY_SLOT_BEFORE.register { ctx, stack, x, y ->
            if (!S.miningCommHighlight || stack.isEmpty) return@register
            val scr = Minecraft.getInstance().screen as? AbstractContainerScreen<*> ?: return@register
            if (scr.title.string != "Commissions") return@register
            val slot = DrawEvents.currentSlot ?: return@register
            if (slot.container === Minecraft.getInstance().player?.inventory) return@register
            val lore = stack.get(DataComponents.LORE) ?: return@register
            if (lore.lines().any { it.string.contains("COMPLETED") })
                ctx.fill(x, y, x + 16, y + 16, Mining.alpha(S.miningCommHighlightColor, S.miningCommHighlightOpacity))
        }
    }

    private fun lines(): List<String> = listOf("§9§lCommissions") + current.map {
        val p = if (it.done) "§aDONE" else {
            val v = it.progress.removeSuffix("%").toDoubleOrNull() ?: 0.0
            (if (v >= 75) "§e" else if (v >= 25) "§6" else "§c") + it.progress
        }
        "§f${it.name}§7: $p"
    }
}
