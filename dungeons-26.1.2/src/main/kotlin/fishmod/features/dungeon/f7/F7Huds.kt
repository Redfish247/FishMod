package fishmod.features.dungeon.f7

import fishmod.shaded.practicalconfig.hud.HUDComponent
import fishmod.shaded.practicalconfig.manager.ConfigValue
import fishmod.utils.config.values.Floor7
import fishmod.utils.dungeon.Section
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

object F7Huds {

    private const val TICK_W = 60
    private const val NOTI_W = 120

    @JvmField
    @ConfigValue
    var tickTimer: HUDComponent = HUDComponent(
        10.0, 80.0, TICK_W, 10, 1f, "Tick Timer",
        { false }, BossTickTimer::render,
        { Floor7.enableTickTimers && (Floor7.enableMaxorTickTimer || Floor7.enableStormTickTimer || Floor7.enableGoldorTickTimer) }
    )

    @JvmField
    @ConfigValue
    var crystalSpawnTime: HUDComponent = HUDComponent(
        10.0, 92.0, TICK_W, 10, 1f, "Crystal Spawn Time",
        { false }, CrystalSpawn::render, { Floor7.enableCrystalSpawnTime }
    )

    @JvmField
    @ConfigValue
    var crystalReminder: HUDComponent = HUDComponent(
        0.0, 0.0, NOTI_W, 10, 1f, "Crystal Reminder",
        { false }, CrystalSpawn::renderNotification, { Floor7.crystalPlaceReminder }
    )

    @JvmField
    @ConfigValue
    var stormDeathTime: HUDComponent = HUDComponent(
        10.0, 92.0, 40, 10, 1f, "Storm Death Time",
        { false }, StormTickTimer::renderDeathTime, { Floor7.enableTickTimers && Floor7.enableStormDeathTime }
    )

    @JvmField
    @ConfigValue
    var pyTimer: HUDComponent = HUDComponent(
        10.0, 116.0, TICK_W, 10, 1f, "Py Tick Timer",
        { false }, StormTickTimer::renderPyTimer, { Floor7.enableTickTimers && Floor7.enablePyTimer }
    )

    @JvmField
    @ConfigValue
    var lbReleaseTimer: HUDComponent = HUDComponent(
        10.0, 104.0, TICK_W, 10, 1f, "LB Release Timer",
        { false }, StormTickTimer::renderLbReleaseTimer, { Floor7.enableTickTimers && Floor7.enableLbReleaseTimer }
    )

    @JvmField
    @ConfigValue
    var stormCrush: HUDComponent = HUDComponent(
        0.0, 0.0, NOTI_W, 10, 1f, "Storm Crushed",
        { false }, PillarExplode::render, { Floor7.enableTickTimers && Floor7.notifyStormCrush }
    )

    @JvmField
    @ConfigValue
    var termStartTimer: HUDComponent = HUDComponent(
        10.0, 104.0, TICK_W, 10, 1f, "Term Start Timer",
        { false }, TermStartTimer::render, { Floor7.enableTickTimers && Floor7.enableTermStartTimer }
    )

    @JvmField
    @ConfigValue
    var sectionProgress: HUDComponent = HUDComponent(
        10.0, 116.0, 40, 10, 1f, "Section Progress",
        { false }, SectionProgress::render, { Floor7.showSectionProgress }
    )

    @JvmField
    @ConfigValue
    var currentSection: HUDComponent = HUDComponent(
        10.0, 190.0, TICK_W, 10, 1f, "Current Section",
        { false }, CurrentSection::render, { Floor7.showCurrentSection }
    )

    @JvmField
    @ConfigValue
    var deviceNotifier: HUDComponent = HUDComponent(
        0.0, 0.0, NOTI_W, 10, 1f, "Device Completed",
        { false }, DeviceNotifier::render, { Floor7.notifyPre4Completion || Floor7.notifySSCompletion }
    )

    @JvmField
    @ConfigValue
    var melodyWarning: HUDComponent = HUDComponent(
        0.0, 0.0, NOTI_W, 10, 1f, "Melody Warning",
        { false }, MelodyWarning::render, { Floor7.notifiyMelody }
    )

    @JvmField
    @ConfigValue
    var sectionCompletion: HUDComponent = HUDComponent(
        0.0, 0.0, NOTI_W, 10, 1f, "Section Completion",
        { false }, SectionCompletion::render, { Floor7.sectionCompletionNotification }
    )

    @JvmField
    @ConfigValue
    var playersLeaped: HUDComponent = HUDComponent(
        0.0, 0.0, 110, 10, 1f, "Players Leaped",
        { false }, PlayersLeaped::render, { Floor7.playersLeapedEnabled }
    )

    @JvmStatic
    fun init() {
        MaxorTickTimer.init()
        CrystalSpawn.init()
        StormTickTimer.init()
        PillarExplode.init()
        GoldorTickTimer.init()
        TermStartTimer.init()
        SectionProgress.init()
        CurrentSection.init()
        DeviceNotifier.init()
        MelodyWarning.init()
        SectionCompletion.init()
        GateDisplay.init()
        PlayersLeaped.init()
        BloodSolver.init()
    }

    @JvmStatic
    fun renderHud(ctx: GuiGraphicsExtractor) {
        renderOne(ctx, tickTimer, BossTickTimer.display(), BossTickTimer::render, 10, 70)
        renderOne(ctx, termStartTimer, TermStartTimer.display(), TermStartTimer::render, 10, 106)
        renderOne(ctx, crystalSpawnTime, CrystalSpawn.display(), CrystalSpawn::render, 10, 118)
        renderOne(ctx, stormDeathTime, StormTickTimer.displayDeathTime(), StormTickTimer::renderDeathTime, 10, 130)
        renderOne(ctx, sectionProgress, SectionProgress.display(), SectionProgress::render, 10, 142)
        renderOne(ctx, Section.terminalSplits, Section.display(), Section::render, 10, 154)
        renderOne(ctx, lbReleaseTimer, StormTickTimer.displayLbReleaseTimer(), StormTickTimer::renderLbReleaseTimer, 10, 166)
        renderOne(ctx, pyTimer, StormTickTimer.displayPyTimer(), StormTickTimer::renderPyTimer, 10, 178)
        renderOne(ctx, crystalReminder, CrystalSpawn.displayNotification(), CrystalSpawn::renderNotification, 10, 40)
        renderOne(ctx, stormCrush, PillarExplode.display(), PillarExplode::render, 10, 28)
        renderOne(ctx, currentSection, CurrentSection.display(), CurrentSection::render, 10, 202)
        renderOne(ctx, deviceNotifier, DeviceNotifier.display(), DeviceNotifier::render, 10, 52)
        renderOne(ctx, melodyWarning, MelodyWarning.display(), MelodyWarning::render, 10, 64)
        renderOne(ctx, sectionCompletion, SectionCompletion.display(), SectionCompletion::render, 10, 16)
        renderOne(ctx, playersLeaped, PlayersLeaped.display(), PlayersLeaped::render, 10, 90)
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
        render.render(c, ctx)
        stack.popMatrix()
    }

    private fun keepOnScreen(component: HUDComponent, targetX: Int, targetY: Int) {
        val client = Minecraft.getInstance()
        if (client == null || client.window == null) return
        val screenWidth = client.window.guiScaledWidth
        val screenHeight = client.window.guiScaledHeight
        val x = component.scaledX
        val y = component.scaledY
        if (x >= 0 && x <= screenWidth - component.width && y >= 0 && y <= screenHeight - component.height) return
        component.move(
            (targetX - x).toDouble() * component.scale / screenWidth,
            (targetY - y).toDouble() * component.scale / screenHeight
        )
    }
}
