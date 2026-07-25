package fishmod.features.dungeon;

import com.mojang.blaze3d.platform.InputConstants;
import fishmod.utils.Constants;
import fishmod.utils.Location;
import fishmod.utils.Misc;
import fishmod.utils.dungeon.map.DungeonGrid;
import fishmod.utils.dungeon.map.GridPos;
import fishmod.utils.dungeon.map.MapReader;
import fishmod.utils.dungeon.map.RoomSignature;
import fishmod.utils.dungeon.map.RoomTile;
import fishmod.utils.dungeon.waypoints.DungeonWaypointStore;
import fishmod.utils.dungeon.waypoints.StoredWaypoint;
import fishmod.utils.dungeon.waypoints.TimerType;
import fishmod.utils.dungeon.waypoints.WaypointType;
import fishmod.utils.rendering.RenderUtils;
import fishmod.utils.rendering.RenderingEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * /dwp — an OdinLegacy-style dungeon waypoint editor (github.com/odtheking/OdinLegacy,
 * DungeonWaypoints.kt/DungeonWaypointCommand.kt/DungeonWaypointConfig.kt). Waypoints are stored
 * per-room, keyed by {@link RoomSignature#key()} (rotation-normalized), so a waypoint set on one
 * instance of a room shape+door pattern replays correctly the next time that same room appears,
 * even if it's rotated differently — see {@link #toCanonical} / {@link #toLive}.
 */
public final class DungeonWaypoints {

    private DungeonWaypoints() {}

    private static final double PLACE_EPSILON = 0.05;

    // --- Edit-mode placement settings (apply to the NEXT waypoint placed) ---
    private static boolean editMode = false;
    private static boolean fill = false;
    private static double size = 0.5;
    private static int distance = 20;
    private static boolean useBlockSize = true;
    private static boolean through = false;
    private static int color = 0xFF55FFFF; // ARGB
    private static WaypointType type = WaypointType.NONE;
    private static TimerType timer = TimerType.NONE;
    private static double offsetX = 0, offsetY = 0, offsetZ = 0;

    private static KeyMapping placeKey;
    private static GridPos lastTile = null;

    /** A waypoint applied to the currently-live room, in real world coordinates. */
    private static final class LiveWaypoint {
        final AABB box; final int color; final boolean filled; final boolean throughWalls; final String title; final Vec3 center;
        LiveWaypoint(AABB box, int color, boolean filled, boolean throughWalls, String title) {
            this.box = box; this.color = color; this.filled = filled; this.throughWalls = throughWalls; this.title = title;
            this.center = box.getCenter();
        }
    }

    private static List<LiveWaypoint> liveWaypoints = new ArrayList<>();

    public static void init() {
        KeyMapping.Category category = fishmod.utils.Keybinds.category;
        if (category == null) category = KeyMapping.Category.register(Identifier.parse(Constants.NAMESPACE));
        placeKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "FishMod: Dungeon Waypoint place/remove",
                InputConstants.Type.MOUSE,
                GLFW.GLFW_MOUSE_BUTTON_RIGHT,
                category));

        ClientTickEvents.END_CLIENT_TICK.register(DungeonWaypoints::onTick);
        RenderingEvents.NO_DEPTH_FILLED.register((ctx, matrices, vc) -> render(ctx, matrices, vc));
    }

    // ================= Commands =================

    public static void toggleEdit() {
        editMode = !editMode;
        Misc.addChatMessage(Component.literal("Dungeon Waypoint editing " + (editMode ? "§aenabled" : "§cdisabled") + "§r!"));
    }

    public static void toggleFill() {
        fill = !fill;
        Misc.addChatMessage(Component.literal("§7[dwp] Fill: " + (fill ? "§afilled" : "§coutline")));
    }

    public static void setSize(double s) {
        size = Math.max(0.1, Math.min(1.0, s));
        Misc.addChatMessage(Component.literal("§7[dwp] Size: §f" + size));
    }

    public static void setDistance(int d) {
        distance = Math.max(1, d);
        Misc.addChatMessage(Component.literal("§7[dwp] Distance: §f" + distance));
    }

    public static void resetSecrets() {
        Misc.addChatMessage(Component.literal("§7[dwp] Secret tracking reset (no-op in this version)."));
    }

    public static void setType(String name) {
        try {
            type = WaypointType.valueOf(name.toUpperCase());
            Misc.addChatMessage(Component.literal("§7[dwp] Type: §f" + type));
        } catch (IllegalArgumentException e) {
            Misc.addChatMessage(Component.literal("§cUnknown waypoint type: " + name));
        }
    }

    public static void setTimer(String name) {
        try {
            timer = TimerType.valueOf(name.toUpperCase());
            Misc.addChatMessage(Component.literal("§7[dwp] Timer: §f" + timer));
        } catch (IllegalArgumentException e) {
            Misc.addChatMessage(Component.literal("§cUnknown timer type: " + name));
        }
    }

    public static void toggleUseBlockSize() {
        useBlockSize = !useBlockSize;
        Misc.addChatMessage(Component.literal("§7[dwp] Use block size: " + (useBlockSize ? "§aon" : "§coff")));
    }

    public static void setOffset(double x, double y, double z) {
        offsetX = x; offsetY = y; offsetZ = z;
        Misc.addChatMessage(Component.literal("§7[dwp] One-shot offset set to §f" + x + ", " + y + ", " + z));
    }

    public static void toggleThrough() {
        through = !through;
        Misc.addChatMessage(Component.literal("§7[dwp] Through walls: " + (through ? "§aon" : "§coff")));
    }

    public static void setColor(String hex) {
        if (hex == null || hex.length() != 8) {
            Misc.addChatMessage(Component.literal("§cColor must be 8 hex chars (RRGGBBAA)."));
            return;
        }
        try {
            long rgba = Long.parseLong(hex, 16);
            int r = (int) ((rgba >> 24) & 0xFF);
            int g = (int) ((rgba >> 16) & 0xFF);
            int b = (int) ((rgba >> 8) & 0xFF);
            int a = (int) (rgba & 0xFF);
            color = (a << 24) | (r << 16) | (g << 8) | b;
            Misc.addChatMessage(Component.literal("§7[dwp] Color set."));
        } catch (NumberFormatException e) {
            Misc.addChatMessage(Component.literal("§cInvalid hex color: " + hex));
        }
    }

    public static void exportToClipboard() {
        String b64 = DungeonWaypointStore.exportBase64();
        if (b64 == null) {
            Misc.addChatMessage(Component.literal("§cExport failed."));
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        mc.keyboardHandler.setClipboard(b64);
        Misc.addChatMessage(Component.literal("§aWaypoint database copied to clipboard."));
    }

    public static void importFromClipboard() {
        Minecraft mc = Minecraft.getInstance();
        String clip = mc.keyboardHandler.getClipboard();
        boolean ok = DungeonWaypointStore.importBase64(clip);
        if (ok) {
            lastTile = null; // force re-apply
            Misc.addChatMessage(Component.literal("§aWaypoint database imported from clipboard."));
        } else {
            Misc.addChatMessage(Component.literal("§cImport failed — clipboard doesn't look like a valid waypoint export."));
        }
    }

    public static void resetCurrentRoom() {
        RoomTile tile = currentRoomTile();
        if (tile == null) {
            Misc.addChatMessage(Component.literal("§cNot in a known dungeon room."));
            return;
        }
        DungeonWaypointStore.clearRoom(RoomSignature.withRotation(tile).key());
        applyRoom(tile);
        Misc.addChatMessage(Component.literal("§aCleared waypoints for the current room."));
    }

    public static boolean isEditMode() {
        return editMode;
    }

    // ================= Tick / interaction =================

    private static void onTick(Minecraft mc) {
        if (mc.player == null || mc.level == null || !Location.inDungeon() || !MapReader.isCalibrated()) {
            liveWaypoints.clear();
            lastTile = null;
            return;
        }

        GridPos tile = MapReader.worldToGridPos(mc.player.getX(), mc.player.getZ());
        if (!tile.equals(lastTile)) {
            lastTile = tile;
            RoomTile roomTile = DungeonGrid.allRooms().get(tile);
            if (roomTile != null) applyRoom(roomTile);
            else liveWaypoints.clear();
        }

        boolean fired = false;
        while (placeKey != null && placeKey.consumeClick()) fired = true;
        if (fired && editMode && mc.screen == null) {
            handlePlace(mc);
        }
    }

    private static RoomTile currentRoomTile() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !MapReader.isCalibrated()) return null;
        GridPos tile = MapReader.worldToGridPos(mc.player.getX(), mc.player.getZ());
        return DungeonGrid.allRooms().get(tile);
    }

    /** Raycasts along the player's look vector, per {@link fishmod.features.PingFeature#placePing}. */
    private static Vec3 aimPoint(Minecraft mc) {
        LocalPlayer p = mc.player;
        float delta = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 eye = p.getEyePosition(delta);
        Vec3 look = p.getViewVector(delta);
        Vec3 end = eye.add(look.scale(distance));

        BlockHitResult hit = mc.level.clip(new ClipContext(
                eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        if (hit != null && hit.getType() != HitResult.Type.MISS) {
            BlockPos bp = hit.getBlockPos();
            return new Vec3(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5);
        }
        return end;
    }

    private static void handlePlace(Minecraft mc) {
        RoomTile tile = currentRoomTile();
        if (tile == null) {
            Misc.addChatMessage(Component.literal("§cNot in a known dungeon room."));
            return;
        }

        Vec3 aim = aimPoint(mc);
        double px = aim.x + offsetX, py = aim.y + offsetY, pz = aim.z + offsetZ;
        offsetX = 0; offsetY = 0; offsetZ = 0; // one-shot

        RoomSignature.WithRotation sig = RoomSignature.withRotation(tile);
        StoredWaypoint canonical = toCanonical(tile, sig.rotation(), px, py, pz);

        if (mc.player.isShiftKeyDown()) {
            mc.setScreen(new DungeonWaypointTitleScreen(title -> {
                canonical.title = title;
                DungeonWaypointStore.add(sig.key(), canonical);
                applyRoom(tile);
            }));
            return;
        }

        boolean removed = DungeonWaypointStore.removeNear(sig.key(), canonical.x, canonical.y, canonical.z, PLACE_EPSILON);
        if (!removed) {
            DungeonWaypointStore.add(sig.key(), canonical);
        }
        applyRoom(tile);
    }

    // ================= Canonical <-> live conversion =================

    private static StoredWaypoint toCanonical(RoomTile tile, int liveRotation, double worldX, double worldY, double worldZ) {
        int originX = MapReader.tileWorldOriginX(tile.pos().x());
        int originZ = MapReader.tileWorldOriginZ(tile.pos().z());
        double lx = worldX - (originX + 16);
        double lz = worldZ - (originZ + 16);
        // Undo the live rotation to get back to canonical orientation.
        double[] canon = DungeonWaypointStore.rotate90(lx, lz, (4 - (liveRotation % 4)) % 4);

        double half = useBlockSize ? 0.5 : size / 2.0;
        return new StoredWaypoint(canon[0], worldY, canon[1], half, half, half,
                color, fill, through, null,
                type == WaypointType.NONE ? null : type.name(),
                timer == TimerType.NONE ? null : timer.name());
    }

    private static Vec3 toLive(StoredWaypoint w, RoomTile tile, int liveRotation) {
        int originX = MapReader.tileWorldOriginX(tile.pos().x());
        int originZ = MapReader.tileWorldOriginZ(tile.pos().z());
        double[] live = DungeonWaypointStore.rotate90(w.x, w.z, liveRotation % 4);
        return new Vec3(originX + 16 + live[0], w.y, originZ + 16 + live[1]);
    }

    private static void applyRoom(RoomTile tile) {
        List<LiveWaypoint> result = new ArrayList<>();
        RoomSignature.WithRotation sig = RoomSignature.withRotation(tile);
        for (StoredWaypoint w : DungeonWaypointStore.get(sig.key())) {
            Vec3 center = toLive(w, tile, sig.rotation());
            AABB box = new AABB(center.x - w.halfX, center.y - w.halfY, center.z - w.halfZ,
                    center.x + w.halfX, center.y + w.halfY, center.z + w.halfZ);
            result.add(new LiveWaypoint(box, w.color, w.filled, w.throughWalls, w.title));
        }
        liveWaypoints = result;
    }

    // ================= Rendering =================

    private static void render(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext ctx,
                                com.mojang.blaze3d.vertex.PoseStack matrices,
                                com.mojang.blaze3d.vertex.VertexConsumer vc) {
        if (!Location.inDungeon()) return;

        for (LiveWaypoint w : liveWaypoints) {
            float[] rgba = RenderUtils.toFloats(w.color);
            if (w.filled) RenderUtils.renderFilled(matrices, vc, w.box, rgba);
            else RenderUtils.renderOutline(matrices, vc, w.box, rgba);
            if (w.title != null && !w.title.isBlank()) {
                RenderUtils.renderText(ctx, matrices, Component.literal(w.title), w.center.x, w.box.maxY + 0.4, w.center.z, 1.0f);
            }
        }

        if (editMode) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.level != null && MapReader.isCalibrated()) {
                Vec3 aim = aimPoint(mc);
                double half = useBlockSize ? 0.5 : size / 2.0;
                AABB box = new AABB(aim.x - half, aim.y - half, aim.z - half, aim.x + half, aim.y + half, aim.z + half);
                RenderUtils.renderOutline(matrices, vc, box, new float[]{1f, 1f, 1f, 0.9f});
            }
        }
    }

    /** Small on-screen settings readout while edit mode is on, drawn near screen center via HudRenderCallback. */
    public static void renderOverlay(net.minecraft.client.gui.GuiGraphicsExtractor ctx) {
        if (!editMode) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.font == null) return;
        int cx = ctx.guiWidth() / 2;
        int y = ctx.guiHeight() / 2 + 30;
        Component line = Component.literal("§b[dwp] §7fill:" + (fill ? "§ay" : "§cn") + " §7size:§f" + size
                + " §7dist:§f" + distance + " §7blockSize:" + (useBlockSize ? "§ay" : "§cn")
                + " §7through:" + (through ? "§ay" : "§cn") + " §7type:§f" + type + " §7timer:§f" + timer);
        int textWidth = mc.font.width(line);
        ctx.text(mc.font, line, cx - textWidth / 2, y, 0xFFFFFFFF, true);
    }
}
