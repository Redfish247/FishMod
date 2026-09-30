package fishmod.features.dungeon

import fishmod.utils.Constants
import fishmod.utils.TabListCache
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.FishDiag
import fishmod.utils.dungeon.Phase
import fishmod.utils.events.Events
import fishmod.shaded.practicalconfig.hud.HUDComponent
import fishmod.shaded.practicalconfig.manager.ConfigValue
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

object FishPuzzleDisplay {

    private val puzzles: MutableList<String> = ArrayList()
    private var tickCounter = 0
    private var bossReached = false

    @JvmField
    @ConfigValue
    var puzzleHud: HUDComponent = HUDComponent(
        0.0, 0.0, 150, 100, 1f, "FM Puzzles",
        { display() },
        { component, context -> render(component, context) },
        { true }
    )

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { client ->
            if (!FishSettings.showPuzzles || client.player == null || bossReached || !fishmod.utils.Location.inDungeon()) return@EndTick
            tickCounter++
            if (tickCounter < 25) return@EndTick
            tickCounter = 0
            if (Phase.inBoss()) {
                bossReached = true
                puzzles.clear()
                return@EndTick
            }
            try { updatePuzzles(client) } catch (e: Exception) { FishDiag.fail("FishPuzzleDisplay.1", "puzzle tab scan failed", e) }
        })
        Events.ON_LOCATION_CHANGE.register { _ ->
            bossReached = false
            puzzles.clear()
            false
        }
    }

    private fun updatePuzzles(client: Minecraft) {
        if (client.connection == null) return
        puzzles.clear()

        var puzzleHeader: String? = null

        for (entry in TabListCache.entries) {
            val clean = entry.stripped.trim()

            if (clean.startsWith("Puzzles:")) {
                puzzleHeader = clean
            } else if (clean.contains("[✦]") || clean.contains("[✔]") || clean.contains("???")) {
                puzzles.add(clean)
            }
        }

        if (puzzleHeader != null) {
            FishDiag.check(puzzleHeader.contains('('), "FishPuzzleDisplay.2") { "puzzle header has no count: '$puzzleHeader'" }
            puzzles.add(0, puzzleHeader)
        }
    }

    @JvmStatic
    fun getPuzzles(): List<String> = puzzles

    @JvmStatic
    fun display(): Boolean = FishSettings.showPuzzles && !bossReached && puzzles.isNotEmpty()

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        val client = Minecraft.getInstance()
        val x = component.scaledX
        val y = component.scaledY

        try {
        for (i in puzzles.indices) {
            val puzzle = puzzles[i]
            val color = when {
                puzzle.startsWith("Puzzles:") -> 0xFFFFFFFF.toInt()
                puzzle.contains("✔") -> Constants.GREEN
                puzzle.contains("???") -> Constants.BLUE
                else -> Constants.RED
            }
            context.text(client.font, puzzle, x, y + i * Constants.TEXT_HEIGHT, color, true)
        }
        } catch (e: Exception) {
            FishDiag.fail("FishPuzzleDisplay.3", "puzzle hud render failed (${puzzles.size} lines)", e)
        }
    }
}
