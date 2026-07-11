package fishmod.features.dungeon.f7;

import config.practical.hud.HUDComponent;
import fishmod.utils.Location;
import fishmod.utils.config.values.Floor7;
import fishmod.utils.dungeon.Phase;
import fishmod.utils.events.Events;
import fishmod.utils.rendering.RenderUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Maxor (P1) tick timer — counts server ticks while in P1. Ported from blade-addons. */
public class MaxorTickTimer {

    private static int tick = 0;

    public static void init() {
        Events.ON_SERVER_TICK.register(() -> {
            if (Location.inDungeon() && Phase.inP1()) tick++;
            return false;
        });
        Events.ON_LOCATION_CHANGE.register(newLocation -> { tick = 0; return false; });
    }

    public static boolean display() {
        return Floor7.enableMaxorTickTimer && Location.inDungeon() && Phase.inP1();
    }

    public static void render(HUDComponent component, GuiGraphicsExtractor context) {
        RenderUtils.drawTimer(component, context, tick, 0xffffffff);
    }
}
