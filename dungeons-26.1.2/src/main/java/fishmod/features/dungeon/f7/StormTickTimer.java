package fishmod.features.dungeon.f7;

import config.practical.hud.HUDComponent;
import fishmod.utils.Constants;
import fishmod.utils.Location;
import fishmod.utils.Misc;
import fishmod.utils.Scheduler;
import fishmod.utils.config.values.Floor7;
import fishmod.utils.dungeon.DungeonClass;
import fishmod.utils.dungeon.Phase;
import fishmod.utils.events.Events;
import fishmod.utils.rendering.RenderUtils;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/** Storm (P2) tick timer + first-death time. Ported from blade-addons (spirit-mask warning omitted). */
public class StormTickTimer {

    private static final Pattern PATTERN = Pattern.compile("^⚠ Storm is enraged! ⚠$");
    private static final long DEATH_DISPLAY_DURATION = 2000;
    private static final int CRUSH_TICK = 31 * 20;
    private static final int COUNTDOWN_DURATION = 5 * 20;

    // LB (Last Breath) release window: visible once the Storm clock hits 30s.
    // Archer releases at 34.35s, Healer at 34.05s; hidden on other classes.
    private static final int LB_START_TICK = 30 * 20;
    private static final int LB_ARCHER_END_TICK = (int) Math.round(34.35 * 20);
    private static final int LB_HEALER_END_TICK = (int) Math.round(34.05 * 20);

    private static int tick = 0;
    private static double deathTime = 0;
    private static long deathStartDisplayTime = 0;

    public static void init() {
        Events.ON_SERVER_TICK.register(() -> {
            if (Location.inDungeon() && Phase.inP2() && !Phase.stormDead()) {
                tick++;
                int lbEnd = lbEndTick();
                if (Floor7.enableLbReleaseTimer && lbEnd > 0 && tick == lbEnd) {
                    Misc.forceTitle(Component.literal("RELEASE NOW!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), Component.empty());
                    Scheduler.scheduleSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1, 1);
                }
            }
            return false;
        });
        Events.ON_LOCATION_CHANGE.register(newLocation -> {
            if (Location.inDungeon()) { tick = 0; deathTime = 0; deathStartDisplayTime = 0; }
            return false;
        });
        Events.ON_GAME_MESSAGE.register(text -> {
            if (!Location.inDungeon() || !Phase.inP2() || !Floor7.enableStormDeathTime) return false;
            if (PATTERN.matcher(text.getString()).find()) {
                deathTime = tick * Constants.TICK_DURATION;
                deathStartDisplayTime = System.currentTimeMillis();
                Misc.addChatMessage(Component.literal("§aStorm died at: §e"
                        + Constants.DECIMAL_FORMAT.format(deathTime) + "s§a."));
            }
            return false;
        });
    }

    public static boolean display() {
        if (Floor7.tickDownStormTickTimer) {
            double diff = CRUSH_TICK - tick;
            if (diff > COUNTDOWN_DURATION || diff < 0) return false;
        }
        return Floor7.enableStormTickTimer && Location.inDungeon() && Phase.inP2() && !Phase.stormDead();
    }

    public static void render(HUDComponent component, GuiGraphicsExtractor context) {
        double num = tick * Constants.TICK_DURATION;
        if (Floor7.tickDownStormTickTimer) num = CRUSH_TICK * Constants.TICK_DURATION - num;
        RenderUtils.drawTimer(component, context, num, Floor7.stormTickTimerColor);
    }

    public static boolean displayDeathTime() {
        return Floor7.enableStormDeathTime && Location.inDungeon() && Phase.inP2() && !Phase.stormDead()
                && deathTime > 0 && deathStartDisplayTime > System.currentTimeMillis() - DEATH_DISPLAY_DURATION;
    }

    public static void renderDeathTime(HUDComponent component, GuiGraphicsExtractor context) {
        RenderUtils.drawTimer(component, context, deathTime, Constants.DARK_PURPLE);
    }

    /** Class-specific LB release tick, or -1 when the timer shouldn't show for this class. */
    private static int lbEndTick() {
        if (DungeonClass.isClass(DungeonClass.ARCHER)) return LB_ARCHER_END_TICK;
        if (DungeonClass.isClass(DungeonClass.HEALER)) return LB_HEALER_END_TICK;
        return -1;
    }

    public static boolean displayLbReleaseTimer() {
        int lbEnd = lbEndTick();
        return Floor7.enableLbReleaseTimer && lbEnd > 0 && Location.inDungeon() && Phase.inP2() && !Phase.stormDead()
                && tick >= LB_START_TICK && tick <= lbEnd;
    }

    public static void renderLbReleaseTimer(HUDComponent component, GuiGraphicsExtractor context) {
        double remaining = (lbEndTick() - tick) * Constants.TICK_DURATION;
        RenderUtils.drawTimer(component, context, remaining, Floor7.lbReleaseTimerColor);
    }
}
