package fishmod.features.dungeon;

import fishmod.utils.Constants;
import fishmod.utils.config.values.FishSettings;
import config.practical.hud.HUDComponent;
import config.practical.manager.ConfigValue;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
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

    private static void updatePuzzles(Minecraft client) {
        puzzles.clear();
        ClientPacketListener handler = client.getConnection();
        if (handler == null) return;

        Collection<PlayerInfo> entries = handler.getOnlinePlayers();
        String puzzleHeader = null;

        for (PlayerInfo entry : entries) {
            if (entry.getTabListDisplayName() == null) continue;
            String line = entry.getTabListDisplayName().getString();
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

    public static void render(HUDComponent component, GuiGraphics context) {
        Minecraft client = Minecraft.getInstance();
        int x = component.getScaledX();
        int y = component.getScaledY();

        for (int i = 0; i < puzzles.size(); i++) {
            String puzzle = puzzles.get(i);
            int color;
            if (puzzle.startsWith("Puzzles:")) color = 0xFFFFFFFF;
            else if (puzzle.contains("✔")) color = Constants.GREEN;
            else if (puzzle.contains("???")) color = Constants.BLUE;
            else color = Constants.RED;
            context.drawString(client.font, puzzle, x, y + i * Constants.TEXT_HEIGHT, color, true);
        }
    }
}