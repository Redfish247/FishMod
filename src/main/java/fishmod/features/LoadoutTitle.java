package fishmod.features;

import fishmod.utils.config.values.FishSettings;
import fishmod.utils.events.Events;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;

/**
 * Parses the item-customizer "You equipped &lt;Name&gt;!" chat line and flashes the loadout name
 * as an on-screen title, since the chat line alone is easy to miss mid-fight.
 */
public final class LoadoutTitle {

    private LoadoutTitle() {}

    private static final Pattern PATTERN = Pattern.compile("^You equipped (.+)!$");

    public static void init() {
        Events.ON_GAME_MESSAGE.register(LoadoutTitle::onMessage);
    }

    private static boolean onMessage(Component text) {
        if (!FishSettings.loadoutTitleEnabled || text == null) return false;
        String s = text.getString();
        if (s == null) return false;

        Matcher m = PATTERN.matcher(s.trim());
        if (!m.matches()) return false;

        String name = m.group(1);
        Component title = Component.literal(name);
        Component subtitle = Component.literal("§7Loadout equipped");

        // ON_GAME_MESSAGE fires on the network thread — touch the HUD only on the client thread.
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            Gui hud = mc.gui;
            hud.setTimes(0, 25, 8);
            hud.setTitle(title);
            hud.setSubtitle(subtitle);
        });
        return false; // keep the original chat line
    }
}
