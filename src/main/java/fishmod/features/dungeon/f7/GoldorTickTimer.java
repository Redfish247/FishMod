package fishmod.features.dungeon.f7;

import config.practical.hud.HUDComponent;
import fishmod.utils.Constants;
import fishmod.utils.Location;
import fishmod.utils.config.values.Floor7;
import fishmod.utils.dungeon.Phase;
import fishmod.utils.events.Events;
import fishmod.utils.rendering.RenderUtils;
import net.minecraft.client.gui.GuiGraphics;

/** Goldor / terminals tick timer (3-tick cycle, optional tick-up). Ported from blade-addons. */
public class GoldorTickTimer {

    private static int tick = 0;

    public static void init() {
        Events.ON_SERVER_TICK.register(() -> {
            if (Location.inDungeon() && Phase.inTerminals()) tick++;
            return false;
        });
        Events.ON_LOCATION_CHANGE.register(newLocation -> { if (Location.inDungeon()) tick = 0; return false; });
    }

    public static boolean display() {
        return Floor7.enableGoldorTickTimer && Location.inDungeon() && Phase.inTerminals();
    }

    public static void render(HUDComponent component, GuiGraphics context) {
        double num = tick * Constants.TICK_DURATION;
        double mod = num % 3;
        if (Floor7.inDeathTicks && !Floor7.makeGoldorTickUp) mod = 3.0 - mod;
        if (Floor7.inDeathTicks) num = mod;
        int color = mod < 1 ? Constants.GREEN : mod < 2 ? Constants.GOLD : Constants.RED;
        RenderUtils.drawTimer(component, context, num, color);
    }
}
