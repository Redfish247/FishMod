package fishmod.features.dungeon

import fishmod.features.FishHudEditor
import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.Events
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

// Quiz countdown (NoammAddons timings): 11s to the first question, 5s after each answer, until all 3 are done.
object QuizHud {

    private const val NAME = "Quiz Timer"
    private val COLOR = fishmod.utils.Constants.STRIP_COLOR_REGEX

    private var stage = 0
    private var ticksLeft = 0

    @JvmStatic
    fun init() {
        FishHudEditor.register(
            NAME,
            { FishSettings.quizHudX }, { v -> FishSettings.quizHudX = v },
            { FishSettings.quizHudY }, { v -> FishSettings.quizHudY = v },
            90, 10,
            { FishSettings.quizHudScale }, { v -> FishSettings.quizHudScale = v }
        )
        Events.ON_GAME_MESSAGE.register { text ->
            val s = COLOR.replace(text.string, "")
            when {
                s.contains("I am Oruo the Omniscient. I have lived many lives.") -> { stage = 1; ticksLeft = 220 }
                s.contains("2 questions left... Then you will have proven your worth to me!") -> { stage = 2; ticksLeft = 100 }
                s.contains("One more question!") -> { stage = 3; ticksLeft = 100 }
                s.startsWith("[STATUE] Oruo the Omniscient: ") && s.contains("answered the final question") -> { stage = 0; ticksLeft = 0 }
            }
            false
        }
        Events.ON_SERVER_TICK.register { if (ticksLeft > 0) ticksLeft--; false }
        Events.ON_WORLD_CHANGE.register { stage = 0; ticksLeft = 0; false }
    }

    @JvmStatic
    fun renderHud(ctx: GuiGraphicsExtractor, tick: DeltaTracker) {
        if (!FishSettings.quizHudEnabled || !Location.inDungeon() || stage == 0 || ticksLeft <= 0) return
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.options.hideGui) return
        val sc = FishSettings.quizHudScale.toFloat()
        ctx.pose().pushMatrix()
        ctx.pose().translate(FishSettings.quizHudX.toFloat(), FishSettings.quizHudY.toFloat())
        ctx.pose().scale(sc, sc)
        ctx.text(mc.font, "§dQuiz §7(§f$stage/3§7): §b" + fishmod.utils.Fmt.f1(ticksLeft / 20.0) + "s", 0, 0, -1, true)
        ctx.pose().popMatrix()
    }
}
