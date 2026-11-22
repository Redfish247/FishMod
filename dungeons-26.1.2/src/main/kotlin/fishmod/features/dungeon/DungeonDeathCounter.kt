package fishmod.features.dungeon

import fishmod.features.HudPreview
import fishmod.shaded.practicalconfig.hud.HUDComponent
import fishmod.utils.SkyblockLocation
import fishmod.utils.config.values.DungeonSettings
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.FishEvents
import fishmod.utils.events.chatRaw
import fishmod.utils.rendering.FishDraw
import net.minecraft.client.gui.GuiGraphicsExtractor

object DungeonDeathCounter {

    private var deaths = 0

    @JvmStatic
    fun init() {
        FishEvents.chat.register { text ->
            if (!DungeonSettings.deathCounterHud || !SkyblockLocation.inDungeon()) return@register false
            val line = text.chatRaw.trim()
            // Every dungeon death (incl. disconnects) ends with "became a ghost."
            if (line.startsWith("☠ ") && line.endsWith(" became a ghost.")) deaths++
            false
        }
        FishEvents.worldChange.register { deaths = 0; false }
    }

    @JvmStatic
    fun display(): Boolean = DungeonSettings.deathCounterHud && SkyblockLocation.inDungeon()

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        val n = if (HudPreview.active) 2 else deaths
        FishDiag.guard("DungeonDeathCounter.1", "death counter hud threw") {
            FishDraw.drawPrefixedText(component, context, "Deaths", (if (n == 0) "§a0" else "§c$n"))
        }
    }
}
