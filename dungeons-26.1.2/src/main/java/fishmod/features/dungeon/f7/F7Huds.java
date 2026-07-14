package fishmod.features.dungeon.f7;

import config.practical.hud.HUDComponent;
import config.practical.manager.ConfigValue;
import fishmod.utils.config.values.Floor7;
import fishmod.utils.dungeon.Section;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;

/**
 * Holds the Floor 7 timer/notification HUD components and renders them.
 *
 * <p>Like the splits/boss-timer HUDs in {@code Phase}, these are rendered explicitly from a
 * HudRenderCallback ({@link #renderHud}) rather than through practical-config's HudElementRegistry
 * auto-render — so their condition-suppliers are forced {@code () -> false}. Each frame we also pull
 * them back on-screen if their saved position is an out-of-range fraction (older builds saved pixel
 * coords like x=10, which {@code getScaledX} blows up to 10*screenWidth → off-screen).
 */
public class F7Huds {

    private static final int TICK_W = 60;
    private static final int NOTI_W = 120;

    @ConfigValue
    public static HUDComponent maxorTickTimer = new HUDComponent(10, 80, TICK_W, 10, 1, "Maxor Tick Timer",
            () -> false, MaxorTickTimer::render, () -> Floor7.enableMaxorTickTimer);

    @ConfigValue
    public static HUDComponent crystalSpawnTime = new HUDComponent(10, 92, TICK_W, 10, 1, "Crystal Spawn Time",
            () -> false, CrystalSpawn::render, () -> Floor7.enableCrystalSpawnTime);

    @ConfigValue
    public static HUDComponent crystalReminder = new HUDComponent(0, 0, NOTI_W, 10, 1, "Crystal Reminder",
            () -> false, CrystalSpawn::renderNotification, () -> Floor7.crystalPlaceReminder);

    @ConfigValue
    public static HUDComponent stormTickTimer = new HUDComponent(10, 80, TICK_W, 10, 1, "Storm Tick Timer",
            () -> false, StormTickTimer::render, () -> Floor7.enableStormTickTimer);

    @ConfigValue
    public static HUDComponent stormDeathTime = new HUDComponent(10, 92, 40, 10, 1, "Storm Death Time",
            () -> false, StormTickTimer::renderDeathTime, () -> Floor7.enableStormDeathTime);

    @ConfigValue
    public static HUDComponent lbReleaseTimer = new HUDComponent(10, 104, TICK_W, 10, 1, "LB Release Timer",
            () -> false, StormTickTimer::renderLbReleaseTimer, () -> Floor7.enableLbReleaseTimer);

    @ConfigValue
    public static HUDComponent stormCrush = new HUDComponent(0, 0, NOTI_W, 10, 1, "Storm Crushed",
            () -> false, PillarExplode::render, () -> Floor7.notifyStormCrush);

    @ConfigValue
    public static HUDComponent goldorTickTimer = new HUDComponent(10, 80, TICK_W, 10, 1, "Goldor Tick Timer",
            () -> false, GoldorTickTimer::render, () -> Floor7.enableGoldorTickTimer);

    @ConfigValue
    public static HUDComponent termStartTimer = new HUDComponent(10, 104, TICK_W, 10, 1, "Term Start Timer",
            () -> false, TermStartTimer::render, () -> Floor7.enableTermStartTimer);

    @ConfigValue
    public static HUDComponent sectionProgress = new HUDComponent(10, 116, 40, 10, 1, "Section Progress",
            () -> false, SectionProgress::render, () -> Floor7.showSectionProgress);

    @ConfigValue
    public static HUDComponent goldorLeapTimer = new HUDComponent(10, 128, TICK_W, 10, 1, "Goldor Leap Timer",
            () -> false, GoldorLeapTimer::render, () -> Floor7.leapNotifications);

    public static void init() {
        MaxorTickTimer.init();
        CrystalSpawn.init();
        StormTickTimer.init();
        PillarExplode.init();
        GoldorTickTimer.init();
        GoldorLeapTimer.init();
        TermStartTimer.init();
        SectionProgress.init();
    }

    /** Render all enabled F7 HUDs (called from a HudRenderCallback in FishModInit). */
    public static void renderHud(GuiGraphicsExtractor ctx) {
        // Distinct left-column default targets so nothing stacks. These are only used to pull an
        // off-screen element back on-screen; once on-screen the user's dragged position is kept.
        renderOne(ctx, maxorTickTimer,   MaxorTickTimer.display(),          MaxorTickTimer::render,          10, 70);
        renderOne(ctx, stormTickTimer,   StormTickTimer.display(),          StormTickTimer::render,          10, 82);
        renderOne(ctx, goldorTickTimer,  GoldorTickTimer.display(),         GoldorTickTimer::render,         10, 94);
        renderOne(ctx, termStartTimer,   TermStartTimer.display(),          TermStartTimer::render,          10, 106);
        renderOne(ctx, crystalSpawnTime, CrystalSpawn.display(),            CrystalSpawn::render,            10, 118);
        renderOne(ctx, stormDeathTime,   StormTickTimer.displayDeathTime(), StormTickTimer::renderDeathTime, 10, 130);
        renderOne(ctx, sectionProgress,  SectionProgress.display(),         SectionProgress::render,         10, 142);
        renderOne(ctx, Section.terminalSplits, Section.display(),          Section::render,                 10, 154);
        renderOne(ctx, lbReleaseTimer,   StormTickTimer.displayLbReleaseTimer(), StormTickTimer::renderLbReleaseTimer, 10, 166);
        renderOne(ctx, crystalReminder,  CrystalSpawn.displayNotification(),CrystalSpawn::renderNotification,10, 40);
        renderOne(ctx, stormCrush,       PillarExplode.display(),           PillarExplode::render,           10, 28);
        renderOne(ctx, goldorLeapTimer,  GoldorLeapTimer.display(),         GoldorLeapTimer::render,         10, 178);
    }

    private static void renderOne(GuiGraphicsExtractor ctx, HUDComponent c, boolean show,
                                  HUDComponent.RenderSupplier render, int targetX, int targetY) {
        keepOnScreen(c, targetX, targetY);
        if (!show) return;
        Matrix3x2fStack stack = ctx.pose();
        stack.pushMatrix();
        stack.scale(c.getScale(), c.getScale());
        render.render(c, ctx);
        stack.popMatrix();
    }

    private static void keepOnScreen(HUDComponent component, int targetX, int targetY) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getWindow() == null) return;
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        int x = component.getScaledX();
        int y = component.getScaledY();
        if (x >= 0 && x <= screenWidth - component.getWidth() && y >= 0 && y <= screenHeight - component.getHeight()) return;
        component.move((double) (targetX - x) * component.getScale() / screenWidth,
                (double) (targetY - y) * component.getScale() / screenHeight);
    }
}
