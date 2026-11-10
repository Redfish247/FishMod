package fishmod.features.dungeon.f7

import fishmod.compat.BladeCompat
import fishmod.features.HudPreview
import fishmod.shaded.practicalconfig.hud.HUDComponent
import fishmod.shaded.practicalconfig.manager.ConfigValue
import fishmod.utils.config.values.Floor7Settings
import fishmod.features.dungeon.FishEstTotal
import fishmod.utils.FishConstants
import fishmod.utils.dungeon.BossPhase
import fishmod.utils.dungeon.GoldorSection
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import fishmod.utils.debug.FishDiag

object F7Huds {

    private const val TICK_W = 60
    private const val NOTI_W = 120

    @JvmField
    @ConfigValue
    var tickTimer: HUDComponent = HUDComponent(
        10.0, 80.0, TICK_W, 10, 1f, "Boss Tick Clock",
        { false }, BossTickTimer::render,
        { Floor7Settings.enableTickTimers && (Floor7Settings.maxorTicksHud || Floor7Settings.stormTicksHud || Floor7Settings.goldorTicksHud) }
    )

    @JvmField
    @ConfigValue
    var crystalRespawnBox: HUDComponent = HUDComponent(
        10.0, 92.0, TICK_W, 10, 1f, "Crystal Respawn",
        { false }, MaxorCrystalTimer::render, { Floor7Settings.crystalSpawnHud }
    )

    @JvmField
    @ConfigValue
    var crystalReminder: HUDComponent = HUDComponent(
        0.0, 0.0, NOTI_W, 10, 1f, "Crystal Not Placed",
        { false }, MaxorCrystalTimer::renderNotification, { Floor7Settings.crystalSpawnHud && Floor7Settings.crystalPlaceAlert }
    )

    @JvmField
    @ConfigValue
    var stormKillBox: HUDComponent = HUDComponent(
        10.0, 92.0, 40, 10, 1f, "Storm Kill Time",
        { false }, StormTicks::renderDeathTime, { Floor7Settings.enableTickTimers && Floor7Settings.stormKillTimeHud }
    )

    @JvmField
    @ConfigValue
    var pyTimer: HUDComponent = HUDComponent(
        10.0, 116.0, TICK_W, 10, 1f, "Py Tick Timer",
        { false }, StormTicks::renderPyTimer, { Floor7Settings.enableTickTimers && Floor7Settings.enablePyTimer }
    )

    @JvmField
    @ConfigValue
    var necronLbTimer: HUDComponent = HUDComponent(
        10.0, 128.0, TICK_W, 10, 1f, "Necron LB Timer",
        { false }, NecronLbTimer::render, { Floor7Settings.enableTickTimers && Floor7Settings.enableNecronLbTimer }
    )

    @JvmField
    @ConfigValue
    var necronDragTimer: HUDComponent = HUDComponent(
        10.0, 140.0, TICK_W, 10, 1f, "Necron Drag Timer",
        { false }, NecronDropPredict::render, { Floor7Settings.enableTickTimers && Floor7Settings.enableNecronDropPredict }
    )

    @JvmField
    @ConfigValue
    var necronDropLanding: HUDComponent = HUDComponent(
        0.0, 20.0, NOTI_W, 10, 1f, "Necron Drop Lava/Platform",
        { false }, NecronDropPredict::renderLanding, { Floor7Settings.enableNecronDropPredict && Floor7Settings.necronDropLavaPopup }
    )

    @JvmField
    @ConfigValue
    var lbReleaseTimer: HUDComponent = HUDComponent(
        10.0, 104.0, TICK_W, 10, 1f, "LB Release Timer",
        { false }, StormTicks::renderLbReleaseTimer, { Floor7Settings.enableTickTimers && Floor7Settings.enableLbReleaseTimer }
    )

    @JvmField
    @ConfigValue
    var stormCrush: HUDComponent = HUDComponent(
        0.0, 0.0, NOTI_W, 10, 1f, "Storm Crush Banner",
        { false }, StormPillarTimer::render, { Floor7Settings.enableTickTimers && Floor7Settings.stormCrushAlert }
    )

    @JvmField
    @ConfigValue
    var pillarExplosion: HUDComponent = HUDComponent(
        10.0, 140.0, TICK_W, 10, 1f, "Pillar Blast Timer",
        { false }, StormPillarTimer::renderTimer, { Floor7Settings.pillarBlastTimer }
    )

    @JvmField
    @ConfigValue
    var termOpenBox: HUDComponent = HUDComponent(
        10.0, 104.0, TICK_W, 10, 1f, "Terminals Open In",
        { false }, TermOpenTimer::render, { Floor7Settings.enableTickTimers && Floor7Settings.termOpenHud }
    )

    @JvmField
    @ConfigValue
    var sectionProgress: HUDComponent = HUDComponent(
        10.0, 116.0, 40, 10, 1f, "Section Count",
        { false }, SectionCountHud::render, { Floor7Settings.sectionProgressHud }
    )

    @JvmField
    @ConfigValue
    var currentSection: HUDComponent = HUDComponent(
        10.0, 190.0, TICK_W, 10, 1f, "P3 Section",
        { false }, GoldorSectionHud::render, { Floor7Settings.currentSectionHud }
    )

    @JvmField
    @ConfigValue
    var deviceNotifier: HUDComponent = HUDComponent(
        0.0, 0.0, NOTI_W, 10, 1f, "Device Done",
        { false }, DeviceDoneAlert::render, { Floor7Settings.pre4DoneAlert || Floor7Settings.ssDoneAlert }
    )

    @JvmField
    @ConfigValue
    var melodyWarning: HUDComponent = HUDComponent(
        0.0, 0.0, NOTI_W, 10, 1f, "Melody Callout",
        { false }, MelodyAlert::render, { Floor7Settings.melodyAlert }
    )

    @JvmField
    @ConfigValue
    var sectionCompletion: HUDComponent = HUDComponent(
        0.0, 0.0, NOTI_W, 10, 1f, "Section Cleared",
        { false }, SectionDoneHud::render, { Floor7Settings.sectionDoneAlert }
    )

    @JvmField
    @ConfigValue
    var playersLeaped: HUDComponent = HUDComponent(
        0.0, 0.0, 110, 10, 1f, "Players Leaped",
        { false }, PlayersLeaped::render, { Floor7Settings.playersLeapedEnabled }
    )

    // Editor examples: each HUD's real render, fed example data while HudPreview.active
    private fun examples() {
        fun ex(name: String, c: HUDComponent, r: HUDComponent.RenderSupplier) = HudPreview.register(name) { HudPreview.component(c, r) }
        // left-aligned label HUDs: the box is the text itself, like in game
        fun exText(name: String, c: HUDComponent, r: HUDComponent.RenderSupplier, sample: String) = HudPreview.register(name) {
            HudPreview.component(c, r, HudPreview.textWidth(listOf(sample)), HudPreview.TEXT_H)
        }
        ex("Boss Tick Clock", tickTimer, BossTickTimer::render)
        HudPreview.register("Terminal Tracker") { TerminalTracker.example() }
        HudPreview.register("Goldor Splits") {
            HudPreview.component(GoldorSection.goldorSplitsBox, GoldorSection::render, GoldorSection.SPLIT_LENGTH, HudPreview.textHeight(4, FishConstants.TEXT_HEIGHT))
        }
        HudPreview.register("Puzzles") { fishmod.features.dungeon.FishPuzzleDisplay.example() }
        HudPreview.register("Run Splits") { BossPhase.runSplitsExample() }
        HudPreview.register("Est. Total (follows Splits)") { FishEstTotal.example() }
        ex("Crystal Respawn", crystalRespawnBox, MaxorCrystalTimer::render)
        ex("Crystal Not Placed", crystalReminder, MaxorCrystalTimer::renderNotification)
        ex("Storm Kill Time", stormKillBox, StormTicks::renderDeathTime)
        ex("Py Tick Timer", pyTimer, StormTicks::renderPyTimer)
        ex("LB Release Timer", lbReleaseTimer, StormTicks::renderLbReleaseTimer)
        ex("Necron LB Timer", necronLbTimer, NecronLbTimer::render)
        exText("Necron Drag Timer", necronDragTimer, NecronDropPredict::render, "Dragged: 3.45")
        ex("Necron Drop Lava/Platform", necronDropLanding, NecronDropPredict::renderLanding)
        ex("Storm Crush Banner", stormCrush, StormPillarTimer::render)
        ex("Pillar Blast Timer", pillarExplosion, StormPillarTimer::renderTimer)
        ex("Terminals Open In", termOpenBox, TermOpenTimer::render)
        ex("Section Count", sectionProgress, SectionCountHud::render)
        ex("Device Done", deviceNotifier, DeviceDoneAlert::render)
        ex("Melody Callout", melodyWarning, MelodyAlert::render)
        ex("Section Cleared", sectionCompletion, SectionDoneHud::render)
        exText("P3 Section", currentSection, GoldorSectionHud::render, "P3:  2/4")
        exText("Players Leaped", playersLeaped, PlayersLeaped::render, "Leaped (HEE2): 2/4")
    }

    @JvmStatic
    fun init() {
        examples()
        StormTicks.init()
        NecronLbTimer.init()
        NecronDropPredict.init()
        GateDisplay.init()
        BloodSolver.init()
        // Blade Addons ships these, so skip ours to avoid double HUDs/sounds
        if (BladeCompat.active) return
        MaxorTicks.init()
        MaxorCrystalTimer.init()
        StormPillarTimer.init()
        GoldorTicks.init()
        TermOpenTimer.init()
        SectionCountHud.init()
        GoldorSectionHud.init()
        DeviceDoneAlert.init()
        P3TitleFilter.init()
        MelodyAlert.init()
        SectionDoneHud.init()
        PlayersLeaped.init()
    }

    @JvmStatic
    fun renderHud(ctx: GuiGraphicsExtractor) {
        val own = !BladeCompat.active
        renderOne(ctx, tickTimer, own && BossTickTimer.display(), BossTickTimer::render, 10, 70)
        renderOne(ctx, termOpenBox, own && TermOpenTimer.display(), TermOpenTimer::render, 10, 106)
        renderOne(ctx, crystalRespawnBox, own && MaxorCrystalTimer.display(), MaxorCrystalTimer::render, 10, 118)
        renderOne(ctx, stormKillBox, own && StormTicks.displayDeathTime(), StormTicks::renderDeathTime, 10, 130)
        renderOne(ctx, sectionProgress, own && SectionCountHud.display(), SectionCountHud::render, 10, 142)
        renderOne(ctx, GoldorSection.goldorSplitsBox, own && GoldorSection.display(), GoldorSection::render, 10, 154)
        renderOne(ctx, TerminalTracker.terminalTrackerHud, TerminalTracker.display(), TerminalTracker::render, 10, 230)
        renderOne(ctx, lbReleaseTimer, StormTicks.displayLbReleaseTimer(), StormTicks::renderLbReleaseTimer, 10, 166)
        renderOne(ctx, pyTimer, StormTicks.displayPyTimer(), StormTicks::renderPyTimer, 10, 178)
        renderOne(ctx, necronDragTimer, NecronDropPredict.display(), NecronDropPredict::render, 10, 220)
        renderOne(ctx, necronDropLanding, NecronDropPredict.displayLanding(), NecronDropPredict::renderLanding, 10, 76)
        renderOne(ctx, necronLbTimer, NecronLbTimer.display(), NecronLbTimer::render, 10, 190)
        renderOne(ctx, crystalReminder, own && MaxorCrystalTimer.displayNotification(), MaxorCrystalTimer::renderNotification, 10, 40)
        renderOne(ctx, stormCrush, own && StormPillarTimer.display(), StormPillarTimer::render, 10, 28)
        renderOne(ctx, pillarExplosion, own && StormPillarTimer.displayTimer(), StormPillarTimer::renderTimer, 10, 214)
        renderOne(ctx, currentSection, own && GoldorSectionHud.display(), GoldorSectionHud::render, 10, 202)
        renderOne(ctx, deviceNotifier, own && DeviceDoneAlert.display(), DeviceDoneAlert::render, 10, 52)
        renderOne(ctx, melodyWarning, own && MelodyAlert.display(), MelodyAlert::render, 10, 64)
        renderOne(ctx, sectionCompletion, own && SectionDoneHud.display(), SectionDoneHud::render, 10, 16)
        renderOne(ctx, playersLeaped, own && PlayersLeaped.display(), PlayersLeaped::render, 10, 90)
    }

    private fun renderOne(
        ctx: GuiGraphicsExtractor, c: HUDComponent, show: Boolean,
        render: HUDComponent.RenderSupplier, targetX: Int, targetY: Int
    ) {
        keepOnScreen(c, targetX, targetY)
        if (!show) return
        val stack = ctx.pose()
        stack.pushMatrix()
        stack.scale(c.scale, c.scale)
        try { render.render(c, ctx) } catch (e: Exception) { FishDiag.fail("F7Huds.1", "F7 HUD render threw (target $targetX,$targetY)", e) }
        stack.popMatrix()
    }

    private fun keepOnScreen(component: HUDComponent, targetX: Int, targetY: Int) {
        val client = Minecraft.getInstance()
        if (client == null || client.window == null) return
        val screenWidth = client.window.guiScaledWidth
        val screenHeight = client.window.guiScaledHeight
        FishDiag.check(component.scale > 0f, "F7Huds.2") { "HUD at target $targetX,$targetY has non-positive scale ${component.scale}" }
        val x = component.scaledX
        val y = component.scaledY
        if (x >= 0 && x <= screenWidth - component.width && y >= 0 && y <= screenHeight - component.height) return
        component.move(
            (targetX - x).toDouble() * component.scale / screenWidth,
            (targetY - y).toDouble() * component.scale / screenHeight
        )
    }
}
