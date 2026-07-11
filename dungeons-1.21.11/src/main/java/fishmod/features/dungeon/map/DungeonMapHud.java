package fishmod.features.dungeon.map;

import config.practical.hud.HUDComponent;
import config.practical.manager.ConfigValue;
import fishmod.utils.Location;
import fishmod.utils.config.values.DungeonMapSettings;
import fishmod.utils.dungeon.map.DoorKey;
import fishmod.utils.dungeon.map.DoorTile;
import fishmod.utils.dungeon.map.DungeonGrid;
import fishmod.utils.dungeon.map.GridPos;
import fishmod.utils.dungeon.map.MapReader;
import fishmod.utils.dungeon.map.PredictedRoomTile;
import fishmod.utils.dungeon.map.RoomState;
import fishmod.utils.dungeon.map.RoomTile;
import fishmod.utils.dungeon.map.RoomType;
import fishmod.utils.dungeon.map.Tile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the fixed 6x6 dungeon room/door grid, following the same explicit-render pattern as
 * {@code F7Huds}: one {@link HUDComponent} field, condition-supplier forced {@code () -> false},
 * rendered from a {@code HudRenderCallback} in FishModInit.
 *
 * <p>Unlike earlier versions, this draws the whole fixed grid at a constant screen layout rather
 * than a player-centered sliding window — the grid itself no longer has any relationship to the
 * player's world position (see {@link GridPos}'s javadoc), so there's nothing to center on.
 */
public class DungeonMapHud {
    private static final int GRID_SIZE = 6;
    private static final int ROOM_PX = 20;
    private static final int DOOR_PX = 4;
    private static final int SIZE = GRID_SIZE * ROOM_PX + (GRID_SIZE - 1) * DOOR_PX;
    private static final int SECRETS_LINE_HEIGHT = 12;

    @ConfigValue
    public static HUDComponent dungeonMap = new HUDComponent(10, 260, SIZE, SIZE + SECRETS_LINE_HEIGHT, 1, "Dungeon Map",
            () -> false, DungeonMapHud::render, () -> DungeonMapSettings.enabled);

    public static boolean display() {
        return DungeonMapSettings.enabled && Location.inDungeon() && MapReader.isCalibrated()
                && !fishmod.utils.dungeon.Phase.inBoss();
    }

    /** Registered directly with a HudRenderCallback in FishModInit — mirrors F7Huds.renderHud. */
    public static void renderHud(DrawContext ctx) {
        keepOnScreen(dungeonMap, 10, 260);
        if (!display()) return;
        Matrix3x2fStack stack = ctx.getMatrices();
        stack.pushMatrix();
        stack.scale(dungeonMap.getScale(), dungeonMap.getScale());
        render(dungeonMap, ctx);
        stack.popMatrix();
    }

    private static void keepOnScreen(HUDComponent component, int targetX, int targetY) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) return;
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        int x = component.getScaledX();
        int y = component.getScaledY();
        if (x >= 0 && x <= screenWidth - component.getWidth() && y >= 0 && y <= screenHeight - component.getHeight()) return;
        component.move((double) (targetX - x) * component.getScale() / screenWidth,
                (double) (targetY - y) * component.getScale() / screenHeight);
    }

    private record LabelJob(int x, int y, int width, int height, String text, int color) {}

    public static void render(HUDComponent component, DrawContext ctx) {
        int baseX = component.getScaledX();
        int baseY = component.getScaledY();
        List<LabelJob> labelJobs = new ArrayList<>();

        for (int tileX = 0; tileX < GRID_SIZE; tileX++) {
            for (int tileZ = 0; tileZ < GRID_SIZE; tileZ++) {
                GridPos pos = new GridPos(tileX, tileZ);
                int x = baseX + tileX * (ROOM_PX + DOOR_PX);
                int y = baseY + tileZ * (ROOM_PX + DOOR_PX);

                Tile tile = DungeonGrid.getWithPrediction(pos);
                if (tile instanceof PredictedRoomTile predicted) {
                    // No text label here on purpose — this is still an unopened/unconfirmed room,
                    // just narrowed to a few candidates; the color wedges alone communicate that
                    // without implying a name/type is actually known yet.
                    drawWedges(ctx, x, y, ROOM_PX, predicted);
                } else {
                    int color = tile.color();
                    if (color != 0) ctx.fill(x, y, x + ROOM_PX, y + ROOM_PX, color);
                    if (DungeonMapSettings.showRoomNames && tile instanceof RoomTile room && room.type() != null
                            && room.type() != RoomType.ENTRANCE
                            && room.state() != RoomState.UNOPENED
                            && DungeonGrid.isLabelAnchor(room)) {
                        String text = room.name() != null ? room.name() : label(room.type());
                        if (!text.isEmpty()) {
                            int[] bbox = boundingBox(room);
                            int boxX = baseX + bbox[0] * (ROOM_PX + DOOR_PX);
                            int boxY = baseY + bbox[1] * (ROOM_PX + DOOR_PX);
                            int boxW = (bbox[2] - bbox[0]) * (ROOM_PX + DOOR_PX) + ROOM_PX;
                            int boxH = (bbox[3] - bbox[1]) * (ROOM_PX + DOOR_PX) + ROOM_PX;
                            labelJobs.add(new LabelJob(boxX, boxY, boxW, boxH, text, labelColor(room.state())));
                        }
                    }
                }

                if (tileX < GRID_SIZE - 1) {
                    GridPos east = pos.offset(1, 0);
                    DoorTile door = DungeonGrid.allDoors().get(new DoorKey(pos, true));
                    if (door != null && door.color() != 0) {
                        int doorH = ROOM_PX / 2;
                        int doorY = y + (ROOM_PX - doorH) / 2;
                        ctx.fill(x + ROOM_PX, doorY, x + ROOM_PX + DOOR_PX, doorY + doorH, door.color());
                    } else if (DungeonGrid.isMerged(pos, east)) {
                        // Same logical room on both sides of this gap — fill it so the room reads
                        // as one connected shape instead of two disconnected squares with a blank gap.
                        ctx.fill(x + ROOM_PX, y, x + ROOM_PX + DOOR_PX, y + ROOM_PX, tile.color());
                    }
                }
                if (tileZ < GRID_SIZE - 1) {
                    GridPos south = pos.offset(0, 1);
                    DoorTile door = DungeonGrid.allDoors().get(new DoorKey(pos, false));
                    if (door != null && door.color() != 0) {
                        int doorW = ROOM_PX / 2;
                        int doorX = x + (ROOM_PX - doorW) / 2;
                        ctx.fill(doorX, y + ROOM_PX, doorX + doorW, y + ROOM_PX + DOOR_PX, door.color());
                    } else if (DungeonGrid.isMerged(pos, south)) {
                        ctx.fill(x, y + ROOM_PX, x + ROOM_PX, y + ROOM_PX + DOOR_PX, tile.color());
                    }
                }
                // The center intersection point of a merged 2x2 (or bigger) room — where the east,
                // south, and southeast gap-fills above all meet — isn't covered by any of those
                // three rects individually, so it needs its own fill or it shows as a small hole.
                if (tileX < GRID_SIZE - 1 && tileZ < GRID_SIZE - 1) {
                    GridPos east = pos.offset(1, 0);
                    GridPos south = pos.offset(0, 1);
                    GridPos southEast = pos.offset(1, 1);
                    if (DungeonGrid.isMerged(pos, east) && DungeonGrid.isMerged(pos, south) && DungeonGrid.isMerged(pos, southEast)) {
                        ctx.fill(x + ROOM_PX, y + ROOM_PX, x + ROOM_PX + DOOR_PX, y + ROOM_PX + DOOR_PX, tile.color());
                    }
                }
            }
        }

        // Labels are drawn in their own pass after every room/door fill so a later-iterated
        // neighboring room's fill can never paint over a previously-drawn label (this was the
        // "text getting cut off" bug when labels were drawn inline during the fill loop).
        for (LabelJob job : labelJobs) {
            drawLabel(ctx, job.x(), job.y(), job.width(), job.height(), job.text(), job.color());
        }

        if (DungeonMapSettings.showPlayerMarkers) {
            for (var marker : DungeonGrid.playerMarkers()) {
                int mx = baseX + Math.round(marker.tileX() * (ROOM_PX + DOOR_PX));
                int my = baseY + Math.round(marker.tileZ() * (ROOM_PX + DOOR_PX));
                int color = marker.self() ? DungeonMapSettings.selfMarkerColor : DungeonMapSettings.teammateMarkerColor;
                drawPlayerArrow(ctx, mx, my, marker.yaw(), color);
            }
        }

        if (DungeonMapSettings.showSecretCounts) {
            int total = fishmod.features.dungeon.DungeonScore.getTotalSecrets();
            int found = fishmod.features.dungeon.DungeonScore.getSecretCount();
            if (total > 0) {
                int remaining = Math.max(0, total - found);
                TextRenderer font = MinecraftClient.getInstance().textRenderer;
                if (font != null) {
                    String text = "Secrets left: " + remaining;
                    int textWidth = font.getWidth(text);
                    ctx.drawText(font, Text.literal(text), baseX + (SIZE - textWidth) / 2, baseY + SIZE + 2, 0xffffffff, true);
                }
            }
        }
    }

    /** A small rotated arrow pointing the direction the player is facing, matching Noamm's marker style. */
    private static void drawPlayerArrow(DrawContext ctx, int mx, int my, float yaw, int color) {
        Matrix3x2fStack stack = ctx.getMatrices();
        stack.pushMatrix();
        stack.translate(mx, my);
        stack.rotate((float) Math.toRadians(yaw));
        ctx.fill(0, -4, 1, -3, color);
        ctx.fill(-1, -3, 2, -2, color);
        ctx.fill(-2, -2, 3, -1, color);
        ctx.fill(-2, -1, 3, 1, color);
        ctx.fill(-2, 1, 3, 2, color);
        stack.popMatrix();
    }

    /**
     * A pie-chart-ish 2x2 quadrant split instead of horizontal stripes — {@code ctx.fill} only
     * draws axis-aligned rects (no true angled wedges available), so this approximates a pie split
     * using quadrants: 2 candidates alternate diagonally (top-left+bottom-right vs top-right+
     * bottom-left) rather than a plain top/bottom split, reading more like alternating slices.
     */
    private static void drawWedges(DrawContext ctx, int x, int y, int size, PredictedRoomTile predicted) {
        int count = predicted.candidates().size();
        int half = size / 2;
        int[] quadrantCandidate = switch (count) {
            case 2 -> new int[]{0, 1, 1, 0};
            case 3 -> new int[]{0, 1, 2, 0};
            default -> new int[]{0, 1, 2, 3};
        };
        int[][] quadrantPos = {{0, 0}, {1, 0}, {0, 1}, {1, 1}}; // TL, TR, BL, BR
        for (int i = 0; i < 4; i++) {
            int candidateIndex = quadrantCandidate[i] % count;
            int qx = x + quadrantPos[i][0] * half;
            int qy = y + quadrantPos[i][1] * half;
            ctx.fill(qx, qy, qx + half, qy + half, predicted.colorAt(candidateIndex));
        }
    }

    /** Short type abbreviation shown on a room, matching the room's color rather than its exact
     *  design name — this feature only ever knows room TYPE (from map pixels), never the exact
     *  room (that needs a room-shape database this feature deliberately doesn't have). */
    private static String label(RoomType type) {
        return switch (type) {
            case PUZZLE -> "P";
            case TRAP -> "T";
            case MINIBOSS -> "M";
            case BLOOD -> "B";
            case FAIRY -> "F";
            case ENTRANCE -> "E";
            case NORMAL, RARE, UNKNOWN -> "";
        };
    }

    /** All grid cells belonging to the same logical room as {@code anchor}, in tile coordinates. */
    private static int[] boundingBox(RoomTile anchor) {
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (RoomTile seg : DungeonGrid.segmentsOf(anchor)) {
            GridPos p = seg.pos();
            minX = Math.min(minX, p.x());
            maxX = Math.max(maxX, p.x());
            minZ = Math.min(minZ, p.z());
            maxZ = Math.max(maxZ, p.z());
        }
        return new int[]{minX, minZ, maxX, maxZ};
    }

    /** Centered both horizontally and vertically across the room's full bounding box, so the name
     *  sits at the room's true center point rather than hugging an edge, and scaled down to fit if
     *  the name is too wide. */
    private static void drawLabel(DrawContext ctx, int boxX, int boxY, int boxWidth, int boxHeight, String text, int color) {
        if (text.isEmpty()) return;
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        if (font == null) return;
        int textWidth = font.getWidth(text);
        int maxWidth = boxWidth - 2;
        float scale = (textWidth > maxWidth && textWidth > 0) ? (float) maxWidth / textWidth : 1f;

        Matrix3x2fStack stack = ctx.getMatrices();
        stack.pushMatrix();
        stack.translate(boxX + boxWidth / 2f, boxY + boxHeight / 2f - font.fontHeight / 2f);
        stack.scale(scale, scale);
        ctx.drawText(font, Text.literal(text), -textWidth / 2, 0, color, true);
        stack.popMatrix();
    }

    /** Matches NoammAddons' text-color convention: the room fill always stays its type color, only the label reflects clear/fail state. */
    private static int labelColor(RoomState state) {
        return switch (state) {
            case CLEARED -> 0xff55ff55;
            case FAILED -> 0xffff5555;
            case PARTIAL -> 0xffffffff;
            default -> 0xffaaaaaa;
        };
    }

    /** For /fmdbg dungeonmap and the room-name/secret-count text overlay (Phase 1 leaves these text-only). */
    public static String describe(RoomTile tile) {
        return tile.type() + " " + tile.state();
    }

    public static String describe(DoorTile tile) {
        return String.valueOf(tile.type());
    }
}
