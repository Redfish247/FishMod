package fishmod.features.dungeon

import fishmod.utils.Constants
import fishmod.utils.TabListCache
import fishmod.utils.config.values.FishSettings
import fishmod.utils.dungeon.Phase
import fishmod.utils.events.Events
import config.practical.hud.HUDComponent
import config.practical.manager.ConfigValue
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

/**
 * FishMod-exclusive puzzle display — lives only in FishMod's jar so it
 * always loads correctly even when blade-addons is also present.
 */
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
            if (!FishSettings.showPuzzles || client.player == null || bossReached) return@EndTick
            tickCounter++
            if (tickCounter < 25) return@EndTick
            tickCounter = 0
            if (Phase.inBoss()) {
                bossReached = true
                puzzles.clear()
                return@EndTick
            }
            updatePuzzles(client)
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
    }
}
