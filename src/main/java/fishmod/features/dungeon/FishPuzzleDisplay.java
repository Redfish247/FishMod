package fishmod.features.dungeon;

import fishmod.utils.Constants;
import fishmod.utils.config.values.FishSettings;
import fishmod.utils.dungeon.Phase;
import fishmod.utils.events.Events;
import config.practical.hud.HUDComponent;
import config.practical.manager.ConfigValue;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * FishMod-exclusive puzzle display — lives only in FishMod's jar so it
 * always loads correctly even when blade-addons is also present.
 */
public class FishPuzzleDisplay {

    private static final Pattern COLOR_STRIP = Pattern.compile("§.");
    private static final List<String> puzzles = new ArrayList<>();
    private static int tickCounter = 0;
    private static boolean bossReached = false;

    @ConfigValue
    public static HUDComponent puzzleHud = new HUDComponent(0, 0, 150, 100, 1, "FM Puzzles",
            FishPuzzleDisplay::display,
            FishPuzzleDisplay::render,
            () -> true
    );

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!FishSettings.showPuzzles || client.player == null || bossReached) return;
            tickCounter++;
            if (tickCounter < 25) return;
            tickCounter = 0;
            if (Phase.inBoss()) {
                bossReached = true;
                puzzles.clear();
                return;
            }
            updatePuzzles(client);
        });
        // Reset on location change (new dungeon run)
        Events.ON_LOCATION_CHANGE.register(loc -> {
            bossReached = false;
            puzzles.clear();
            return false;
        });
    }

    private static void updatePuzzles(Minecraft client) {
        puzzles.clear();
        ClientPacketListener handler = client.getConnection();
        if (handler == null) return;

        Collection<PlayerInfo> entries = handler.getOnlinePlayers();
        String puzzleHeader = null;

        for (PlayerInfo entry : entries) {
            if (entry.getTabListDisplayName() == null) continue;
            String line = entry.getTabListDisplayName().getString();
            String clean = COLOR_STRIP.matcher(line).replaceAll("").trim();

            if (clean.startsWith("Puzzles:")) {
                puzzleHeader = clean;
            } else if (clean.contains("[✦]") || clean.contains("[✔]") || clean.contains("???")) {
                puzzles.add(clean);
            }
        }

        if (puzzleHeader != null) {
            puzzles.add(0, puzzleHeader);
        }
    }

    public static List<String> getPuzzles() { return puzzles; }

    public static boolean display() {
        return FishSettings.showPuzzles && !bossReached && !puzzles.isEmpty();
    }

    public static void render(HUDComponent component, GuiGraphicsExtractor context) {
        Minecraft client = Minecraft.getInstance();
        int x = component.getScaledX();
        int y = component.getScaledY();

        for (int i = 0; i < puzzles.size(); i++) {
            String puzzle = puzzles.get(i);
            int color;
            if (puzzle.startsWith("Puzzles:"))     color = 0xFFFFFFFF;       // white header
            else if (puzzle.contains("✔"))          color = Constants.GREEN;   // solved
            else if (puzzle.contains("???"))        color = Constants.BLUE;    // undiscovered
            else                                    color = Constants.RED;     // discovered, unsolved
            context.text(client.font, puzzle, x, y + i * Constants.TEXT_HEIGHT, color, true);
        }
    }
}
