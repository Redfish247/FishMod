package fishmod.features.dungeon.map;

import config.practical.hud.HUDComponent;
import config.practical.manager.ConfigValue;
import fishmod.utils.Location;
import fishmod.utils.config.values.DungeonMapSettings;
import fishmod.utils.dungeon.map.DoorTile;
import fishmod.utils.dungeon.map.DungeonGrid;
import fishmod.utils.dungeon.map.GridPos;
import fishmod.utils.dungeon.map.MapReader;
import fishmod.utils.dungeon.map.PredictedRoomTile;
import fishmod.utils.dungeon.map.RoomTile;
import fishmod.utils.dungeon.map.Tile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;

/**
 * Draws the dungeon room/door grid centered on the player, following the same explicit-render
 * pattern as {@code F7Huds}: one {@link HUDComponent} field, condition-supplier forced
 * {@code () -> false}, rendered from a {@code HudElementRegistry} callback in FishModInit.
 */
public class DungeonMapHud {
    private static final int RANGE = 6; // grid rooms shown in each direction from the player
    private static final int ROOM_PX = 16;
    private static final int DOOR_PX = 4;
    private static final int SIZE = (RANGE * 2 + 1) * ROOM_PX; // generous bound for the component box

    @ConfigValue
    public static HUDComponent dungeonMap = new HUDComponent(10, 200, SIZE, SIZE, 1, "Dungeon Map",
            () -> false, DungeonMapHud::render, () -> DungeonMapSettings.enabled);

    public static boolean display() {
        return DungeonMapSettings.enabled && Location.inDungeon() && MapReader.isCalibrated();
    }

    /** Registered directly with HudElementRegistry in FishModInit — mirrors F7Huds.renderHud. */
    public static void renderHud(GuiGraphicsExtractor ctx) {
        keepOnScreen(dungeonMap, 10, 200);
        if (!display()) return;
        Matrix3x2fStack stack = ctx.pose();
        stack.pushMatrix();
        stack.scale(dungeonMap.getScale(), dungeonMap.getScale());
        render(dungeonMap, ctx);
        stack.popMatrix();
    }

    private static void keepOnScreen(HUDComponent component, int targetX, int targetY) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getWindow() == null) return;
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        int x = component.getScaledX();
        int y = component.getScaledY();
        if (x >= 0 && x <= screenWidth - component.getWidth() && y >= 0 && y <= screenHeight - component.getHeight()) return;
        component.move((double) (targetX - x) * component.getScale() / screenWidth,
                (double) (targetY - y) * component.getScale() / screenHeight);
    }

    public static void render(HUDComponent component, GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        GridPos playerGrid = GridPos.fromWorld(mc.player.getX(), mc.player.getZ());

        int[] offsets = new int[RANGE * 4 + 1]; // covers delta range [-RANGE*2, RANGE*2]
        int origin = RANGE * 2;
        offsets[origin] = 0;
        for (int d = 1; d <= RANGE * 2; d++) {
            int stepUnit = ((d - 1) & 1) == 0 ? ROOM_PX : DOOR_PX; // even step index = leaving a room cell
            offsets[origin + d] = offsets[origin + d - 1] + stepUnit;
            offsets[origin - d] = offsets[origin - d + 1] - stepUnit;
        }

        int baseX = component.getScaledX();
        int baseY = component.getScaledY();

        for (int dx = -RANGE * 2; dx <= RANGE * 2; dx++) {
            for (int dz = -RANGE * 2; dz <= RANGE * 2; dz++) {
                GridPos pos = playerGrid.offset(dx, dz);
                Tile tile = DungeonGrid.getWithPrediction(pos);
                int size = pos.isRoomCell() ? ROOM_PX : DOOR_PX;
                int x = baseX + offsets[origin + dx];
                int y = baseY + offsets[origin + dz];

                if (tile instanceof PredictedRoomTile predicted) {
                    drawSplit(ctx, x, y, size, predicted.primaryColor(), predicted.secondaryColor());
                    continue;
                }
                int color = tile.color();
                if (color == 0) continue;
                ctx.fill(x, y, x + size, y + size, color);
            }
        }
    }

    private static void drawSplit(GuiGraphicsExtractor ctx, int x, int y, int size, int colorA, int colorB) {
        int half = size / 2;
        ctx.fill(x, y, x + size, y + half, colorA);
        ctx.fill(x, y + half, x + size, y + size, colorB);
    }

    /** For /fmdbg dungeonmap and the room-name/secret-count text overlay (Phase 1 leaves these text-only). */
    public static String describe(RoomTile tile) {
        return tile.type() + " " + tile.state();
    }

    public static String describe(DoorTile tile) {
        return tile.type() + " " + tile.state();
    }
}
