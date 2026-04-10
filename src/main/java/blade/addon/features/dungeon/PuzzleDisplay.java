package blade.addon.features.dungeon;

import blade.addon.utils.Constants;
import blade.addon.utils.config.values.FishSettings;
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

public class PuzzleDisplay {

    private static final List<String> puzzles = new ArrayList<>();
    private static int tickCounter = 0;

    @ConfigValue
    public static HUDComponent puzzleHud = new HUDComponent(0, 0, 150, 100, 1, "Puzzles",
            PuzzleDisplay::display,
            PuzzleDisplay::render,
            () -> true
    );

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!FishSettings.showPuzzles || client.player == null) return;
            tickCounter++;
            if (tickCounter < 20) return;
            tickCounter = 0;
            updatePuzzles(client);
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
            String clean = line.replaceAll("§.", "").trim();

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

    public static boolean display() {
        return FishSettings.showPuzzles && !puzzles.isEmpty();
    }

    public static void render(HUDComponent component, DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        int x = component.getScaledX();
        int y = component.getScaledY();

        for (int i = 0; i < puzzles.size(); i++) {
            String puzzle = puzzles.get(i);
            int color;
            if (puzzle.startsWith("Puzzles:")) color = 0xFFFFFFFF;
            else if (puzzle.contains("✔")) color = Constants.GREEN;
            else if (puzzle.contains("???")) color = Constants.BLUE;
            else color = Constants.RED;
            context.drawText(client.textRenderer, puzzle, x, y + i * Constants.TEXT_HEIGHT, color, true);
        }
    }
}