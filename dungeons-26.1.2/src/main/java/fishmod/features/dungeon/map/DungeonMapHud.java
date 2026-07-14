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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the fixed 6x6 dungeon room/door grid, following the same explicit-render pattern as
 * {@code F7Huds}: one {@link HUDComponent} field, condition-supplier forced {@code () -> false},
 * rendered from a {@code HudElementRegistry} callback in FishModInit.
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

    /** Registered directly with HudElementRegistry in FishModInit — mirrors F7Huds.renderHud. */
    public static void renderHud(GuiGraphicsExtractor ctx) {
        keepOnScreen(dungeonMap, 10, 260);
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

    private record LabelJob(int centerX, int centerY, String text, int color, String secretsText) {}

    public static void render(HUDComponent component, GuiGraphicsExtractor ctx) {
        int baseX = component.getScaledX();
        int baseY = component.getScaledY();
        List<LabelJob> labelJobs = new ArrayList<>();

        if (DungeonMapSettings.backgroundColor != 0) {
            ctx.fill(baseX, baseY, baseX + SIZE, baseY + SIZE, DungeonMapSettings.backgroundColor);
        }
        if (DungeonMapSettings.borderColor != 0 && DungeonMapSettings.borderThickness > 0) {
            int t = DungeonMapSettings.borderThickness;
            int c = DungeonMapSettings.borderColor;
            ctx.fill(baseX - t, baseY - t, baseX + SIZE + t, baseY, c);
            ctx.fill(baseX - t, baseY + SIZE, baseX + SIZE + t, baseY + SIZE + t, c);
            ctx.fill(baseX - t, baseY, baseX, baseY + SIZE, c);
            ctx.fill(baseX + SIZE, baseY, baseX + SIZE + t, baseY + SIZE, c);
        }

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
                    if (DungeonMapSettings.showCheckmarks && tile instanceof RoomTile room && room.type() != null
                            && room.type() != RoomType.ENTRANCE && room.state() != RoomState.UNDISCOVERED) {
                        drawCheckmark(ctx, x, y, room.state());
                    }
                    if (DungeonMapSettings.showRoomNames && tile instanceof RoomTile room && room.type() != null
                            && room.type() != RoomType.ENTRANCE // always known/obvious, not worth labeling
                            && room.state() != RoomState.UNOPENED // type/name not actually confirmed yet
                            && DungeonGrid.isLabelAnchor(room)) { // multi-cell room: label once, not per segment
                        String text = room.name() != null ? room.name() : label(room.type());
                        if (!text.isEmpty()) {
                            float[] centroid = centroidOf(room);
                            int centerX = baseX + Math.round(centroid[0] * (ROOM_PX + DOOR_PX)) + ROOM_PX / 2;
                            int centerY = baseY + Math.round(centroid[1] * (ROOM_PX + DOOR_PX)) + ROOM_PX / 2;
                            labelJobs.add(new LabelJob(centerX, centerY, text, labelColor(room.state()), secretsLabel(room)));
                        }
                    }
                }

                if (tileX < GRID_SIZE - 1) {
                    GridPos east = pos.offset(1, 0);
                    DoorTile door = DungeonGrid.allDoors().get(new DoorKey(pos, true));
                    if (door != null && door.color() != 0) {
                        // Half-height, centered — a full-height fill read as "just more hallway"
                        // instead of a distinct door marker.
                        int doorH = ROOM_PX / 2;
                        int doorY = y + (ROOM_PX - doorH) / 2;
                        ctx.fill(x + ROOM_PX, doorY, x + ROOM_PX + DOOR_PX, doorY + doorH, door.color());
                        drawWitherDoorEsp(ctx, door, x + ROOM_PX, doorY, DOOR_PX, doorH);
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
                        drawWitherDoorEsp(ctx, door, doorX, y + ROOM_PX, doorW, DOOR_PX);
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
            drawLabel(ctx, job.centerX(), job.centerY(), job.text(), job.color(), job.secretsText());
        }

        if (DungeonMapSettings.showPlayerMarkers) {
            Minecraft mc = Minecraft.getInstance();
            PlayerInfo selfEntry = mc.player != null && mc.getConnection() != null
                    ? mc.getConnection().getPlayerInfo(mc.player.getUUID()) : null;
            for (var marker : DungeonGrid.playerMarkers()) {
                int mx = baseX + Math.round(marker.tileX() * (ROOM_PX + DOOR_PX));
                int my = baseY + Math.round(marker.tileZ() * (ROOM_PX + DOOR_PX));
                if (marker.self()) {
                    if (selfEntry != null) drawPlayerHead(ctx, selfEntry, mx, my, DungeonMapSettings.selfMarkerColor);
                    else drawPlayerArrow(ctx, mx, my, marker.yaw(), DungeonMapSettings.selfMarkerColor);
                } else {
                    PlayerInfo entry = findTabEntry(mc, marker.name());
                    if (entry != null) drawPlayerHead(ctx, entry, mx, my, DungeonMapSettings.teammateMarkerColor);
                    else drawPlayerArrow(ctx, mx, my, marker.yaw(), DungeonMapSettings.teammateMarkerColor);
                }
                if (DungeonMapSettings.showPlayerNames) {
                    String displayName = marker.self() && mc.player != null ? mc.player.getName().getString() : marker.name();
                    if (displayName != null) drawPlayerNameLabel(ctx, mx, my, displayName);
                }
            }
        }

        int extraLineY = baseY + SIZE + 2;
        if (DungeonMapSettings.showSecretCounts) {
            int total = fishmod.features.dungeon.DungeonScore.getTotalSecrets();
            int found = fishmod.features.dungeon.DungeonScore.getSecretCount();
            if (total > 0) {
                int remaining = Math.max(0, total - found);
                Font font = Minecraft.getInstance().font;
                if (font != null) {
                    String text = "Secrets left: " + remaining;
                    int textWidth = font.width(text);
                    ctx.text(font, Component.literal(text), baseX + (SIZE - textWidth) / 2, extraLineY, 0xffffffff, true);
                    extraLineY += SECRETS_LINE_HEIGHT;
                }
            }
        }

        if (DungeonMapSettings.showExtraInfoUnderMap) {
            Font font = Minecraft.getInstance().font;
            if (font != null) {
                String text = "Crypts: " + fishmod.features.dungeon.DungeonScore.getCryptCount()
                        + "  M:" + (fishmod.features.dungeon.DungeonScore.isMimicKilled() ? "✔" : "✘")
                        + " P:" + (fishmod.features.dungeon.DungeonScore.isPrinceKilled() ? "✔" : "✘")
                        + "  Deaths: " + fishmod.features.dungeon.DungeonScore.getDeathCount();
                int textWidth = font.width(text);
                ctx.text(font, Component.literal(text), baseX + (SIZE - textWidth) / 2, extraLineY, 0xffffffff, true);
            }
        }
    }

    /** Small clear-state icon drawn in the room's corner (or center if configured). Additive to the existing label-color signal. */
    private static void drawCheckmark(GuiGraphicsExtractor ctx, int x, int y, RoomState state) {
        if (DungeonMapSettings.hideUnknownCheckmark
                && state != RoomState.CLEARED && state != RoomState.FAILED) return;
        String symbol = switch (state) {
            case CLEARED -> "✔";
            case FAILED -> "✘";
            default -> "?";
        };
        int color = switch (state) {
            case CLEARED -> 0xff55ff55;
            case FAILED -> 0xffff5555;
            default -> 0xffaaaaaa;
        };
        Font font = Minecraft.getInstance().font;
        if (font == null) return;
        float scale = DungeonMapSettings.checkmarkScale;
        int textWidth = font.width(symbol);
        int cx = DungeonMapSettings.centerCheckmark ? x + ROOM_PX / 2 : x + ROOM_PX - 6;
        int cy = DungeonMapSettings.centerCheckmark ? y + ROOM_PX / 2 : y + 1;
        Matrix3x2fStack stack = ctx.pose();
        stack.pushMatrix();
        stack.translate(cx, cy);
        stack.scale(scale, scale);
        ctx.text(font, Component.literal(symbol), -textWidth / 2, 0, color, true);
        stack.popMatrix();
    }

    /** Self-only wither-key ESP: outlines an unopened wither door based on whether the local player currently holds a Wither Key. */
    private static void drawWitherDoorEsp(GuiGraphicsExtractor ctx, DoorTile door, int x, int y, int w, int h) {
        if (!DungeonMapSettings.boxWitherDoors || door.type() != fishmod.utils.dungeon.map.DoorType.WITHER || door.opened()) return;
        int color = hasWitherKey() ? DungeonMapSettings.witherKeyHeldColor : DungeonMapSettings.witherKeyMissingColor;
        ctx.fill(x - 1, y - 1, x + w + 1, y, color);
        ctx.fill(x - 1, y + h, x + w + 1, y + h + 1, color);
        ctx.fill(x - 1, y, x, y + h, color);
        ctx.fill(x + w, y, x + w + 1, y + h, color);
    }

    /** True if the local player currently has a Wither Key anywhere in their inventory. Self-only — no info about teammates is read or shown. */
    private static boolean hasWitherKey() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        Inventory inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack != null && !stack.isEmpty() && stack.getHoverName().getString().contains("Wither Key")) return true;
        }
        return false;
    }

    private static void drawPlayerNameLabel(GuiGraphicsExtractor ctx, int mx, int my, String name) {
        Font font = Minecraft.getInstance().font;
        if (font == null) return;
        String clean = name.replaceAll("§.", "").trim();
        if (clean.isEmpty()) return;
        int textWidth = font.width(clean);
        float scale = DungeonMapSettings.playerNameScale;
        Matrix3x2fStack stack = ctx.pose();
        stack.pushMatrix();
        stack.translate(mx, my - 8);
        stack.scale(scale, scale);
        ctx.text(font, Component.literal(clean), -textWidth / 2, 0, 0xffffffff, true);
        stack.popMatrix();
    }

    /**
     * Matches a map decoration's name against the tab list to find that teammate's skin — best
     * effort, since Hypixel doesn't always populate the decoration name. Callers fall back to the
     * plain directional arrow when this returns null, so an unresolved name just looks like it did
     * before this feature existed rather than breaking anything.
     */
    private static PlayerInfo findTabEntry(Minecraft mc, String decorationName) {
        if (decorationName == null || mc.getConnection() == null) return null;
        String stripped = decorationName.replaceAll("§.", "").trim();
        if (stripped.isEmpty()) return null;
        for (PlayerInfo entry : mc.getConnection().getOnlinePlayers()) {
            if (entry.getProfile() != null && stripped.equalsIgnoreCase(entry.getProfile().name())) return entry;
        }
        return null;
    }

    /** An 8x8 skin head on a small colored backing square (green=self/white=teammate by default), matching Noamm's player-head map markers. */
    private static void drawPlayerHead(GuiGraphicsExtractor ctx, PlayerInfo entry, int mx, int my, int borderColor) {
        int size = 8;
        ctx.fill(mx - size / 2 - 1, my - size / 2 - 1, mx + size / 2 + 1, my + size / 2 + 1, borderColor);
        try {
            PlayerFaceExtractor.extractRenderState(ctx, entry.getSkin(), mx - size / 2, my - size / 2, size);
        } catch (Exception ignored) {}
    }

    /**
     * A rotated arrow pointing the direction that player is facing — the fallback marker for when
     * {@link #drawPlayerHead} can't be used (self should always resolve via {@code mc.player}'s own
     * tab-list entry; a teammate only falls back here if Hypixel's map decoration didn't carry a
     * name that matches anyone in the tab list, which happens occasionally in practice).
     */
    private static void drawPlayerArrow(GuiGraphicsExtractor ctx, int mx, int my, float yaw, int color) {
        Matrix3x2fStack stack = ctx.pose();
        stack.pushMatrix();
        stack.translate(mx, my);
        stack.rotate((float) Math.toRadians(yaw + 180));
        // A 5-row isoceles triangle pointing "up" (north) before rotation — smoother than a 3-rect
        // chevron at this scale.
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
    private static void drawWedges(GuiGraphicsExtractor ctx, int x, int y, int size, PredictedRoomTile predicted) {
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

    /** Centered on the room's centroid (not its raw bounding box — see {@link #centroidOf}). Left at
     *  full scale and allowed to overflow the box horizontally (rooms are small, names aren't)
     *  rather than shrinking to fit; only wraps onto a second line if the name is long enough that
     *  one line would sprawl across most of the map. A known secrets count renders as its own line
     *  below the (possibly wrapped) name, and the whole block is centered together so the name
     *  doesn't sit dead-center with secrets hanging off the bottom edge. */
    private static void drawLabel(GuiGraphicsExtractor ctx, int centerX, int centerY, String text, int color, String secretsText) {
        if (text.isEmpty()) return;
        Font font = Minecraft.getInstance().font;
        if (font == null) return;

        int textWidth = font.width(text);
        int wrapThreshold = SIZE / 2;
        List<String> nameLines = new ArrayList<>();
        if (textWidth <= wrapThreshold) {
            nameLines.add(text);
        } else {
            int splitAt = splitPoint(text);
            nameLines.add(text.substring(0, splitAt).trim());
            nameLines.add(text.substring(splitAt).trim());
        }

        boolean hasSecrets = secretsText != null && !secretsText.isEmpty();
        int lineHeight = font.lineHeight;
        int totalLines = nameLines.size() + (hasSecrets ? 1 : 0);
        int topY = centerY - totalLines * lineHeight / 2;

        for (int i = 0; i < nameLines.size(); i++) {
            String line = nameLines.get(i);
            int w = font.width(line);
            ctx.text(font, Component.literal(line), centerX - w / 2, topY + i * lineHeight, color, true);
        }
        if (hasSecrets) {
            int sw = font.width(secretsText);
            ctx.text(font, Component.literal(secretsText), centerX - sw / 2, topY + nameLines.size() * lineHeight, 0xffffd700, true);
        }
    }

    /** Nearest space to the midpoint, so a wrapped room name breaks between words instead of
     *  mid-word. Falls back to a hard midpoint split if there's no space (single long word). */
    private static int splitPoint(String text) {
        int mid = text.length() / 2;
        int best = -1, bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == ' ') {
                int dist = Math.abs(i - mid);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = i;
                }
            }
        }
        return best >= 0 ? best : mid;
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
