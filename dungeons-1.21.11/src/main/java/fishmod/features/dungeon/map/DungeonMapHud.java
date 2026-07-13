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
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.PlayerListEntry;
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

    private record LabelJob(int centerX, int centerY, int maxWidth, String text, int color, String secretsText) {}

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
                            int boxW = (bbox[2] - bbox[0]) * (ROOM_PX + DOOR_PX) + ROOM_PX;
                            float[] centroid = centroidOf(room);
                            int centerX = baseX + Math.round(centroid[0] * (ROOM_PX + DOOR_PX)) + ROOM_PX / 2;
                            int centerY = baseY + Math.round(centroid[1] * (ROOM_PX + DOOR_PX)) + ROOM_PX / 2;
                            labelJobs.add(new LabelJob(centerX, centerY, boxW - 2, text, labelColor(room.state()), secretsLabel(room)));
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
            drawLabel(ctx, job.centerX(), job.centerY(), job.maxWidth(), job.text(), job.color(), job.secretsText());
        }

        if (DungeonMapSettings.showPlayerMarkers) {
            MinecraftClient mc = MinecraftClient.getInstance();
            PlayerListEntry selfEntry = mc.player != null && mc.getNetworkHandler() != null
                    ? mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid()) : null;
            for (var marker : DungeonGrid.playerMarkers()) {
                int mx = baseX + Math.round(marker.tileX() * (ROOM_PX + DOOR_PX));
                int my = baseY + Math.round(marker.tileZ() * (ROOM_PX + DOOR_PX));
                if (marker.self()) {
                    if (selfEntry != null) drawPlayerHead(ctx, selfEntry, mx, my, DungeonMapSettings.selfMarkerColor);
                    else drawPlayerArrow(ctx, mx, my, marker.yaw(), DungeonMapSettings.selfMarkerColor);
                } else {
                    PlayerListEntry entry = findTabEntry(mc, marker.name());
                    if (entry != null) drawPlayerHead(ctx, entry, mx, my, DungeonMapSettings.teammateMarkerColor);
                    else drawPlayerArrow(ctx, mx, my, marker.yaw(), DungeonMapSettings.teammateMarkerColor);
                }
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

    /**
     * Matches a map decoration's name against the tab list to find that teammate's skin — best
     * effort, since Hypixel doesn't always populate the decoration name. Callers fall back to the
     * plain directional arrow when this returns null, so an unresolved name just looks like it did
     * before this feature existed rather than breaking anything.
     */
    private static PlayerListEntry findTabEntry(MinecraftClient mc, String decorationName) {
        if (decorationName == null || mc.getNetworkHandler() == null) return null;
        String stripped = decorationName.replaceAll("§.", "").trim();
        if (stripped.isEmpty()) return null;
        for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            if (entry.getProfile() != null && stripped.equalsIgnoreCase(entry.getProfile().name())) return entry;
        }
        return null;
    }

    /** An 8x8 skin head on a small colored backing square (green=self/white=teammate by default), matching Noamm's player-head map markers. */
    private static void drawPlayerHead(DrawContext ctx, PlayerListEntry entry, int mx, int my, int borderColor) {
        int size = 8;
        ctx.fill(mx - size / 2 - 1, my - size / 2 - 1, mx + size / 2 + 1, my + size / 2 + 1, borderColor);
        try {
            PlayerSkinDrawer.draw(ctx, entry.getSkinTextures(), mx - size / 2, my - size / 2, size);
        } catch (Exception ignored) {}
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
     * Splits the room evenly among however many candidates remain: a plain left/right half split
     * for 2, three equal vertical thirds for 3, and the 2x2 quadrant split only once there are
     * actually 4 candidates to give each its own corner. Previously this always rendered as
     * quadrants (alternating diagonally for 2, reusing a spare corner for 3), which read as "4
     * colors" on screen even when there were really only 2 or 3.
     */
    private static void drawWedges(DrawContext ctx, int x, int y, int size, PredictedRoomTile predicted) {
        int count = predicted.candidates().size();
        switch (count) {
            case 1 -> ctx.fill(x, y, x + size, y + size, predicted.colorAt(0));
            case 2 -> {
                int half = size / 2;
                ctx.fill(x, y, x + half, y + size, predicted.colorAt(0));
                ctx.fill(x + half, y, x + size, y + size, predicted.colorAt(1));
            }
            case 3 -> {
                int third = size / 3;
                ctx.fill(x, y, x + third, y + size, predicted.colorAt(0));
                ctx.fill(x + third, y, x + 2 * third, y + size, predicted.colorAt(1));
                ctx.fill(x + 2 * third, y, x + size, y + size, predicted.colorAt(2));
            }
            default -> {
                int half = size / 2;
                int[][] quadrantPos = {{0, 0}, {1, 0}, {0, 1}, {1, 1}}; // TL, TR, BL, BR
                for (int i = 0; i < 4; i++) {
                    int qx = x + quadrantPos[i][0] * half;
                    int qy = y + quadrantPos[i][1] * half;
                    ctx.fill(qx, qy, qx + half, qy + half, predicted.colorAt(i % count));
                }
            }
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

    /** Average tile position of the room's occupied cells. For an irregular shape like an L, the
     *  bounding box's geometric center falls over the missing corner cell; the centroid instead
     *  biases the label toward where the room actually is, matching Noamm/Odin's placement. */
    private static float[] centroidOf(RoomTile anchor) {
        List<RoomTile> segments = DungeonGrid.segmentsOf(anchor);
        float sx = 0, sz = 0;
        for (RoomTile seg : segments) {
            sx += seg.pos().x();
            sz += seg.pos().z();
        }
        return new float[]{sx / segments.size(), sz / segments.size()};
    }

    /** "N Secret(s)" for a room whose exact design (and therefore secret count) is known, or null otherwise. */
    private static String secretsLabel(RoomTile room) {
        if (!DungeonMapSettings.showSecretCounts) return null;
        int secrets = room.secrets();
        if (secrets < 0) return null;
        return secrets == 1 ? "1 Secret" : secrets + " Secrets";
    }

    /** Centered on the room's centroid (not its raw bounding box — see {@link #centroidOf}), scaled
     *  down to fit if the name is too wide. When a secrets line is known, the name shifts up half a
     *  line so the name+secrets pair reads as one centered block instead of the name alone sitting
     *  dead-center with secrets hanging off the bottom edge. */
    private static void drawLabel(DrawContext ctx, int centerX, int centerY, int maxWidth, String text, int color, String secretsText) {
        if (text.isEmpty()) return;
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        if (font == null) return;
        boolean hasSecrets = secretsText != null && !secretsText.isEmpty();
        int lineHeight = font.fontHeight;
        int totalLines = hasSecrets ? 2 : 1;
        float topY = centerY - totalLines * lineHeight / 2f;

        int textWidth = font.getWidth(text);
        float scale = (textWidth > maxWidth && textWidth > 0) ? (float) maxWidth / textWidth : 1f;
        Matrix3x2fStack stack = ctx.getMatrices();
        stack.pushMatrix();
        stack.translate(centerX, topY);
        stack.scale(scale, scale);
        ctx.drawText(font, Text.literal(text), -textWidth / 2, 0, color, true);
        stack.popMatrix();

        if (hasSecrets) {
            int secretsWidth = font.getWidth(secretsText);
            float secretsScale = (secretsWidth > maxWidth && secretsWidth > 0) ? (float) maxWidth / secretsWidth : 1f;
            Matrix3x2fStack s2 = ctx.getMatrices();
            s2.pushMatrix();
            s2.translate(centerX, topY + lineHeight);
            s2.scale(secretsScale, secretsScale);
            ctx.drawText(font, Text.literal(secretsText), -secretsWidth / 2, 0, 0xffffd700, true);
            s2.popMatrix();
        }
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
