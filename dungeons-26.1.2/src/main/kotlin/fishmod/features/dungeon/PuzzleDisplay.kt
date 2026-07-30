package fishmod.features.dungeon

import config.practical.hud.HUDComponent
import config.practical.manager.ConfigValue
import fishmod.utils.Constants
import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.multiplayer.PlayerInfo

object PuzzleDisplay {

    private val puzzles: MutableList<String> = ArrayList()
    private var tickCounter = 0

    @JvmField
    @ConfigValue
    var puzzleHud: HUDComponent = HUDComponent(
        0.0, 0.0, 150, 100, 1f, "Puzzles",
        { display() },
        { component, context -> render(component, context) },
        { true }
    )

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { client ->
            if (!FishSettings.showPuzzles || client.player == null) return@EndTick
            tickCounter++
            if (tickCounter < 20) return@EndTick
            tickCounter = 0
            updatePuzzles(client)
        })
    }

    private fun updatePuzzles(client: Minecraft) {
        puzzles.clear()
        val handler = client.connection ?: return

        val entries: Collection<PlayerInfo> = handler.onlinePlayers
        var puzzleHeader: String? = null

        for (entry in entries) {
            if (entry.tabListDisplayName == null) continue
            val line = entry.tabListDisplayName!!.string
            val clean = line.replace(Regex("§."), "").trim()

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
    fun display(): Boolean {
        return FishSettings.showPuzzles && puzzles.isNotEmpty()
    }

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        val client = Minecraft.getInstance()
        val x = component.scaledX
        val y = component.scaledY

        for (i in puzzles.indices) {
            val puzzle = puzzles[i]
            val color: Int = if (puzzle.startsWith("Puzzles:")) 0xFFFFFFFF.toInt()
            else if (puzzle.contains("✔")) Constants.GREEN
            else if (puzzle.contains("???")) Constants.BLUE
            else Constants.RED
            context.text(client.font, puzzle, x, y + i * Constants.TEXT_HEIGHT, color, true)
        }
    }
}
