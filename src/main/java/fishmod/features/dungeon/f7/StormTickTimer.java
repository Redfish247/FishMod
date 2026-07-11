package fishmod.features.dungeon.f7;

import config.practical.hud.HUDComponent;
import fishmod.utils.Constants;
import fishmod.utils.Location;
import fishmod.utils.Misc;
import fishmod.utils.config.values.Floor7;
import fishmod.utils.dungeon.Phase;
import fishmod.utils.events.Events;
import fishmod.utils.rendering.RenderUtils;
import java.util.regex.Pattern;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Storm (P2) tick timer + first-death time. Ported from blade-addons (spirit-mask warning omitted). */
public class StormTickTimer {

    private static final Pattern PATTERN = Pattern.compile("^⚠ Storm is enraged! ⚠$");
    private static final long DEATH_DISPLAY_DURATION = 2000;
    private static final int CRUSH_TICK = 31 * 20;
    private static final int COUNTDOWN_DURATION = 5 * 20;

    // LB (Last Breath) release window: visible once the Storm clock hits 30s, counting down to 34.35s.
    private static final int LB_START_TICK = 30 * 20;
    private static final int LB_END_TICK = (int) Math.round(34.35 * 20);

    private static int tick = 0;
    private static double deathTime = 0;
    private static long deathStartDisplayTime = 0;

    public static void init() {
        Events.ON_SERVER_TICK.register(() -> {
            if (Location.inDungeon() && Phase.inP2() && !Phase.stormDead()) tick++;
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

    public static boolean displayLbReleaseTimer() {
        return Floor7.enableLbReleaseTimer && Location.inDungeon() && Phase.inP2() && !Phase.stormDead()
                && tick >= LB_START_TICK && tick <= LB_END_TICK;
    }

    public static void renderLbReleaseTimer(HUDComponent component, GuiGraphicsExtractor context) {
        double remaining = (LB_END_TICK - tick) * Constants.TICK_DURATION;
        RenderUtils.drawTimer(component, context, remaining, Floor7.lbReleaseTimerColor);
    }
}
