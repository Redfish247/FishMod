package fishmod.features.dungeon.f7;

import config.practical.hud.HUDComponent;
import fishmod.utils.Constants;
import fishmod.utils.Location;
import fishmod.utils.Misc;
import fishmod.utils.config.values.Floor7;
import fishmod.utils.dungeon.Phase;
import fishmod.utils.events.Events;
import fishmod.utils.rendering.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.regex.Pattern;

/** Storm (P2) tick timer + first-death time. Ported from blade-addons (spirit-mask warning omitted). */
public class StormTickTimer {

    private static final Pattern PATTERN = Pattern.compile("^⚠ Storm is enraged! ⚠$");
    private static final long DEATH_DISPLAY_DURATION = 2000;
    private static final int CRUSH_TICK = 31 * 20;
    private static final int COUNTDOWN_DURATION = 5 * 20;

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
                Misc.addChatMessage(Text.literal("§aStorm died at: §e"
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

    public static void render(HUDComponent component, DrawContext context) {
        double num = tick * Constants.TICK_DURATION;
        if (Floor7.tickDownStormTickTimer) num = CRUSH_TICK * Constants.TICK_DURATION - num;
        RenderUtils.drawTimer(component, context, num, Floor7.stormTickTimerColor);
    }

    public static boolean displayDeathTime() {
        return Floor7.enableStormDeathTime && Location.inDungeon() && Phase.inP2() && !Phase.stormDead()
                && deathTime > 0 && deathStartDisplayTime > System.currentTimeMillis() - DEATH_DISPLAY_DURATION;
    }

    public static void renderDeathTime(HUDComponent component, DrawContext context) {
        RenderUtils.drawTimer(component, context, deathTime, Constants.DARK_PURPLE);
    }
}
