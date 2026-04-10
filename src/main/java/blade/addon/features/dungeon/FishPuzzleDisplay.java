package blade.addon.features.dungeon;

import blade.addon.utils.Constants;
import blade.addon.utils.config.values.FishSettings;
import blade.addon.utils.dungeon.Phase;
import blade.addon.utils.events.Events;
import config.practical.hud.HUDComponent;
import config.practical.manager.ConfigValue;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;

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

    private static void updatePuzzles(MinecraftClient client) {
        puzzles.clear();
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler == null) return;

        Collection<PlayerListEntry> entries = handler.getPlayerList();
        String puzzleHeader = null;

        for (PlayerListEntry entry : entries) {
            if (entry.getDisplayName() == null) continue;
            String line = entry.getDisplayName().getString();
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

    public static void render(HUDComponent component, DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        int x = component.getScaledX();
        int y = component.getScaledY();

        for (int i = 0; i < puzzles.size(); i++) {
            String puzzle = puzzles.get(i);
            int color;
            if (puzzle.startsWith("Puzzles:"))     color = 0xFFFFFFFF;       // white header
            else if (puzzle.contains("✔"))          color = Constants.GREEN;   // solved
            else if (puzzle.contains("???"))        color = Constants.BLUE;    // undiscovered
            else                                    color = Constants.RED;     // discovered, unsolved
            context.drawText(client.textRenderer, puzzle, x, y + i * Constants.TEXT_HEIGHT, color, true);
        }
    }
}
