package fishmod.features;

import fishmod.utils.config.values.FishSettings;
import fishmod.utils.events.Events;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;

/**
 * Parses the Terminator "Explosive Shot" chat line and shows the per-enemy damage as a title.
 *
 *   "Your Explosive Shot hit 1 enemy for 472.5 damage."  → title "472.5", subtitle "Explosive Shot • 1 enemy"
 *   "Your Explosive Shot hit 8 enemies for 4,000 damage." → title "500",  subtitle "Explosive Shot • 8 enemies"
 *
 * The damage in the message is the TOTAL across all enemies hit; dividing by the enemy count gives
 * the per-target hit. Reads {@link Events#ON_GAME_MESSAGE} but never cancels (the chat line stays).
 */
public final class ExplosiveShot {

    private ExplosiveShot() {}

    // hit N enemy/enemies for D damage  (D may carry thousands commas and a decimal)
    private static final Pattern PATTERN = Pattern.compile(
            "Your Explosive Shot hit (\\d+) (?:enemy|enemies) for ([\\d,]+(?:\\.\\d+)?) damage");

    public static void init() {
        Events.ON_GAME_MESSAGE.register(ExplosiveShot::onMessage);
    }

    private static boolean onMessage(Component text) {
        if (!FishSettings.explosiveShotEnabled || text == null) return false;
        String s = text.getString();
        if (s == null || s.indexOf("Explosive Shot") < 0) return false;

        Matcher m = PATTERN.matcher(s);
        if (!m.find()) return false;

        int enemies;
        double total;
        try {
            enemies = Integer.parseInt(m.group(1));
            total = Double.parseDouble(m.group(2).replace(",", ""));
        } catch (NumberFormatException e) {
            return false;
        }
        if (enemies <= 0) return false;

        double perEnemy = total / enemies;
        String dmg = formatDamage(perEnemy);
        Component title = Component.literal(dmg).withStyle(ChatFormatting.RED);
        Component subtitle = Component.literal("§7Explosive Shot §8• §f" + enemies
                + (enemies == 1 ? " enemy" : " enemies"));

        // ON_GAME_MESSAGE fires on the network thread — touch the HUD only on the client thread.
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            Gui hud = mc.gui;
            hud.setTimes(0, 25, 8); // snappy: no fade-in, ~1.25s hold, quick fade-out
            hud.setTitle(title);
            hud.setSubtitle(subtitle);
        });
        return false; // keep the original chat line
    }

    /** Whole numbers print with thousands separators; fractional values keep one decimal. */
    private static String formatDamage(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) return String.format("%,d", (long) v);
        return String.format("%,.1f", v);
    }
}
