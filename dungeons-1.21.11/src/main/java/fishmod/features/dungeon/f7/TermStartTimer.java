package fishmod.features.dungeon.f7;

import config.practical.hud.HUDComponent;
import fishmod.utils.Constants;
import fishmod.utils.Location;
import fishmod.utils.config.values.Floor7;
import fishmod.utils.dungeon.Phase;
import fishmod.utils.events.Events;
import fishmod.utils.rendering.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** Counts down ~5s from Storm's death to terminal start. Ported from blade-addons. */
public class TermStartTimer {

    private static final int TOTAL_TICKS = 100;
    private static int tick = TOTAL_TICKS;

    public static void init() {
        Events.ON_SERVER_TICK.register(() -> {
            if (Location.inDungeon() && Phase.inP2() && Phase.stormDead()) tick--;
            return false;
        });
        Events.ON_LOCATION_CHANGE.register(newLocation -> { tick = TOTAL_TICKS; return false; });
    }

    public static boolean display() {
        return Floor7.enableTermStartTimer && Location.inDungeon() && Phase.inP2() && Phase.stormDead();
    }

    public static void render(HUDComponent component, DrawContext context) {
        double num = tick * Constants.TICK_DURATION;
        RenderUtils.drawCenteredText(context, component, Text.literal(Constants.DECIMAL_FORMAT.format(num)), Constants.YELLOW);
    }
}
