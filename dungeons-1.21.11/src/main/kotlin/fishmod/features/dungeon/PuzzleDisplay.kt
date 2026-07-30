package fishmod.features.dungeon

import config.practical.hud.HUDComponent
import config.practical.manager.ConfigValue
import fishmod.utils.Constants
import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.network.PlayerListEntry

object PuzzleDisplay {

    private val puzzles: MutableList<String> = ArrayList()
    private var tickCounter = 0

    @JvmField
    @ConfigValue
    var puzzleHud: HUDComponent = HUDComponent(
        0, 0, 150, 100, 1, "Puzzles",
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

    private fun updatePuzzles(client: MinecraftClient) {
        puzzles.clear()
        val handler = client.networkHandler ?: return

        val entries: Collection<PlayerListEntry> = handler.playerList
        var puzzleHeader: String? = null

        for (entry in entries) {
            if (entry.displayName == null) continue
            val line = entry.displayName!!.string
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
    fun render(component: HUDComponent, context: DrawContext) {
        val client = MinecraftClient.getInstance()
        val x = component.scaledX
        val y = component.scaledY

        for (i in puzzles.indices) {
            val puzzle = puzzles[i]
            val color: Int = if (puzzle.startsWith("Puzzles:")) 0xFFFFFFFF.toInt()
            else if (puzzle.contains("✔")) Constants.GREEN
            else if (puzzle.contains("???")) Constants.BLUE
            else Constants.RED
            context.drawText(client.textRenderer, puzzle, x, y + i * Constants.TEXT_HEIGHT, color, true)
        }
    }
}
