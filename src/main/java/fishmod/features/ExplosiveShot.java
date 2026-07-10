package fishmod.features;

import fishmod.features.dungeon.ChatCommandState;
import fishmod.utils.config.values.FishSettings;
import fishmod.utils.dungeon.DungeonClass;
import fishmod.utils.events.Events;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
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
 * Only active during the F7 Maxor fight: starts on Maxor's opening taunt, stops the moment Storm's
 * lightning line appears (Terminator/Explosive Shot readings aren't relevant to any other boss).
 *
 * The damage in the message is the TOTAL across all enemies hit; dividing by the enemy count gives
 * the per-target hit. Reads {@link Events#ON_GAME_MESSAGE} but never cancels (the chat line stays).
 */
public final class ExplosiveShot {

    private ExplosiveShot() {}

    // hit N enemy/enemies for D damage  (D may carry thousands commas and a decimal)
    private static final Pattern PATTERN = Pattern.compile(
            "Your Explosive Shot hit (\\d+) (?:enemy|enemies) for ([\\d,]+(?:\\.\\d+)?) damage");

    private static final String MAXOR_START = "[BOSS] Maxor: WELL WELL WELL LOOK WHO'S HERE!";
    private static final String MAXOR_END   = "[BOSS] Storm: The power of lightning is quite phenomenal. A single strike can vaporize a person whole.";
    private static boolean maxorActive = false;

    public static void init() {
        Events.ON_GAME_MESSAGE.register(ExplosiveShot::onMessage);
        Events.ON_WORLD_CHANGE.register(() -> { maxorActive = false; return false; });
    }

    private static boolean onMessage(Component text) {
        if (text == null) return false;
        String s = text.getString();
        if (s == null) return false;

        if (s.equals(MAXOR_START)) { maxorActive = true; return false; }
        if (s.equals(MAXOR_END))   { maxorActive = false; return false; }

        if (!FishSettings.explosiveShotEnabled || !maxorActive) return false;
        if (s.indexOf("Explosive Shot") < 0) return false;

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

        if (FishSettings.explosiveShotAnnounceParty && DungeonClass.isClass(DungeonClass.ARCHER)) {
            announceToParty(dmg, enemies);
        }
        return false; // keep the original chat line
    }

    /** Shares the same crit-hit info already shown on screen with the party. Delayed + rate-limit
     *  suppressed the same way {@code PartyCommandHandler.sendCmd} does. */
    private static void announceToParty(String dmg, int enemies) {
        String message = "Explosive Shot: " + dmg + " dmg (" + enemies + (enemies == 1 ? " enemy)" : " enemies)");
        CompletableFuture.delayedExecutor(250, TimeUnit.MILLISECONDS)
                .execute(() -> Minecraft.getInstance().execute(() -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.getConnection() != null) {
                        mc.getConnection().sendCommand("pc " + message);
                        ChatCommandState.lastPartyCommandAt = System.currentTimeMillis();
                    }
                }));
    }

    /** Whole numbers print with thousands separators; fractional values keep one decimal. */
    private static String formatDamage(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) return String.format("%,d", (long) v);
        return String.format("%,.1f", v);
    }
}
