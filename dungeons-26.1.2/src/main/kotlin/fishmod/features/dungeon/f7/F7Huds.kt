package fishmod.features.dungeon.f7

import config.practical.hud.HUDComponent
import config.practical.manager.ConfigValue
import fishmod.utils.config.values.Floor7
import fishmod.utils.dungeon.Section
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

/**
 * Holds the Floor 7 timer/notification HUD components and renders them.
 *
 * Like the splits/boss-timer HUDs in `Phase`, these are rendered explicitly from a
 * HudRenderCallback ([renderHud]) rather than through practical-config's HudElementRegistry
 * auto-render — so their condition-suppliers are forced `{ false }`. Each frame we also pull
 * them back on-screen if their saved position is an out-of-range fraction (older builds saved pixel
 * coords like x=10, which `getScaledX` blows up to 10*screenWidth → off-screen).
 */
object F7Huds {

    private const val TICK_W = 60
    private const val NOTI_W = 120

    @JvmField
    @ConfigValue
    var maxorTickTimer: HUDComponent = HUDComponent(
        10.0, 80.0, TICK_W, 10, 1f, "Maxor Tick Timer",
        { false }, MaxorTickTimer::render, { Floor7.enableMaxorTickTimer }
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
    var stormTickTimer: HUDComponent = HUDComponent(
        10.0, 80.0, TICK_W, 10, 1f, "Storm Tick Timer",
        { false }, StormTickTimer::render, { Floor7.enableStormTickTimer }
    )

    @JvmField
    @ConfigValue
    var stormDeathTime: HUDComponent = HUDComponent(
        10.0, 92.0, 40, 10, 1f, "Storm Death Time",
        { false }, StormTickTimer::renderDeathTime, { Floor7.enableStormDeathTime }
    )

    @JvmField
    @ConfigValue
    var lbReleaseTimer: HUDComponent = HUDComponent(
        10.0, 104.0, TICK_W, 10, 1f, "LB Release Timer",
        { false }, StormTickTimer::renderLbReleaseTimer, { Floor7.enableLbReleaseTimer }
    )

    @JvmField
    @ConfigValue
    var stormCrush: HUDComponent = HUDComponent(
        0.0, 0.0, NOTI_W, 10, 1f, "Storm Crushed",
        { false }, PillarExplode::render, { Floor7.notifyStormCrush }
    )

    @JvmField
    @ConfigValue
    var goldorTickTimer: HUDComponent = HUDComponent(
        10.0, 80.0, TICK_W, 10, 1f, "Goldor Tick Timer",
        { false }, GoldorTickTimer::render, { Floor7.enableGoldorTickTimer }
    )

    @JvmField
    @ConfigValue
    var termStartTimer: HUDComponent = HUDComponent(
        10.0, 104.0, TICK_W, 10, 1f, "Term Start Timer",
        { false }, TermStartTimer::render, { Floor7.enableTermStartTimer }
    )

    @JvmField
    @ConfigValue
    var sectionProgress: HUDComponent = HUDComponent(
        10.0, 116.0, 40, 10, 1f, "Section Progress",
        { false }, SectionProgress::render, { Floor7.showSectionProgress }
    )

    @JvmField
    @ConfigValue
    var goldorLeapTimer: HUDComponent = HUDComponent(
        10.0, 128.0, TICK_W, 10, 1f, "Goldor Leap Timer",
        { false }, GoldorLeapTimer::render, { Floor7.leapNotifications }
    )

    @JvmStatic
    fun init() {
        MaxorTickTimer.init()
        CrystalSpawn.init()
        StormTickTimer.init()
        PillarExplode.init()
        GoldorTickTimer.init()
        GoldorLeapTimer.init()
        TermStartTimer.init()
        SectionProgress.init()
    }

    /** Render all enabled F7 HUDs (called from a HudRenderCallback in FishModInit). */
    @JvmStatic
    fun renderHud(ctx: GuiGraphicsExtractor) {
        // Distinct left-column default targets so nothing stacks. These are only used to pull an
        // off-screen element back on-screen; once on-screen the user's dragged position is kept.
        renderOne(ctx, maxorTickTimer, MaxorTickTimer.display(), MaxorTickTimer::render, 10, 70)
        renderOne(ctx, stormTickTimer, StormTickTimer.display(), StormTickTimer::render, 10, 82)
        renderOne(ctx, goldorTickTimer, GoldorTickTimer.display(), GoldorTickTimer::render, 10, 94)
        renderOne(ctx, termStartTimer, TermStartTimer.display(), TermStartTimer::render, 10, 106)
        renderOne(ctx, crystalSpawnTime, CrystalSpawn.display(), CrystalSpawn::render, 10, 118)
        renderOne(ctx, stormDeathTime, StormTickTimer.displayDeathTime(), StormTickTimer::renderDeathTime, 10, 130)
        renderOne(ctx, sectionProgress, SectionProgress.display(), SectionProgress::render, 10, 142)
        renderOne(ctx, Section.terminalSplits, Section.display(), Section::render, 10, 154)
        renderOne(ctx, lbReleaseTimer, StormTickTimer.displayLbReleaseTimer(), StormTickTimer::renderLbReleaseTimer, 10, 166)
        renderOne(ctx, crystalReminder, CrystalSpawn.displayNotification(), CrystalSpawn::renderNotification, 10, 40)
        renderOne(ctx, stormCrush, PillarExplode.display(), PillarExplode::render, 10, 28)
        renderOne(ctx, goldorLeapTimer, GoldorLeapTimer.display(), GoldorLeapTimer::render, 10, 178)
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
