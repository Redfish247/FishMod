package fishmod.features.dungeon

import fishmod.features.HudExample
import fishmod.features.HudPreview
import fishmod.utils.FishConstants
import fishmod.utils.TabListCache
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.FishDiag
import fishmod.utils.dungeon.BossPhase
import fishmod.utils.events.FishEvents
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
            if (!FishSettings.showPuzzles || client.player == null || bossReached || !fishmod.utils.SkyblockLocation.inDungeon()) return@EndTick
            tickCounter++
            if (tickCounter < 25) return@EndTick
            tickCounter = 0
            if (BossPhase.inBoss()) {
                bossReached = true
                puzzles.clear()
                return@EndTick
            }
            try { updatePuzzles(client) } catch (e: Exception) { FishDiag.fail("FishPuzzleDisplay.1", "puzzle tab scan failed", e) }
        })
        FishEvents.locationChange.register { _ ->
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

    private val EXAMPLE = listOf("Puzzles: (3)", "Creeper Beams: [✔]", "Ice Path: [✦]", "???: [✦]")

    // HUD editor example: the real render over example tab lines; box is the text, like in game
    @JvmStatic
    fun example(): HudExample =
        HudPreview.component(puzzleHud, ::render, HudPreview.textWidth(EXAMPLE), HudPreview.textHeight(EXAMPLE.size, FishConstants.TEXT_HEIGHT))

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        val client = Minecraft.getInstance()
        val x = component.scaledX
        val y = component.scaledY

        val list = if (HudPreview.active) EXAMPLE else puzzles
        try {
        for (i in list.indices) {
            val puzzle = list[i]
            val color = when {
                puzzle.startsWith("Puzzles:") -> 0xFFFFFFFF.toInt()
                puzzle.contains("✔") -> FishConstants.GREEN
                puzzle.contains("???") -> FishConstants.BLUE
                else -> FishConstants.RED
            }
            context.text(client.font, puzzle, x, y + i * FishConstants.TEXT_HEIGHT, color, true)
        }
        } catch (e: Exception) {
            FishDiag.fail("FishPuzzleDisplay.3", "puzzle hud render failed (${puzzles.size} lines)", e)
        }
    }
}
