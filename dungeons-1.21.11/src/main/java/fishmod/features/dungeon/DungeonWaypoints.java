package fishmod.features.dungeon;

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
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
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

    private static KeyBinding placeKey;
    private static GridPos lastTile = null;

    /** A waypoint applied to the currently-live room, in real world coordinates. */
    private static final class LiveWaypoint {
        final Box box; final int color; final boolean filled; final boolean throughWalls; final String title; final Vec3d center;
        LiveWaypoint(Box box, int color, boolean filled, boolean throughWalls, String title) {
            this.box = box; this.color = color; this.filled = filled; this.throughWalls = throughWalls; this.title = title;
            this.center = box.getCenter();
        }
    }

    private static List<LiveWaypoint> liveWaypoints = new ArrayList<>();

    public static void init() {
        placeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "FishMod - Dungeon Waypoint place/remove",
                InputUtil.Type.MOUSE,
                GLFW.GLFW_MOUSE_BUTTON_RIGHT,
                fishmod.utils.Keybinds.category != null ? fishmod.utils.Keybinds.category
                        : KeyBinding.Category.create(Identifier.of(Constants.NAMESPACE))));

        ClientTickEvents.END_CLIENT_TICK.register(DungeonWaypoints::onTick);
        RenderingEvents.NO_DEPTH_FILLED.register((ctx, matrices, vc) -> render(ctx, matrices, vc));
    }

    // ================= Commands =================

    public static void toggleEdit() {
        editMode = !editMode;
        Misc.addChatMessage(Text.literal("Dungeon Waypoint editing " + (editMode ? "§aenabled" : "§cdisabled") + "§r!"));
    }

    public static void toggleFill() {
        fill = !fill;
        Misc.addChatMessage(Text.literal("§7[dwp] Fill: " + (fill ? "§afilled" : "§coutline")));
    }

    public static void setSize(double s) {
        size = Math.max(0.1, Math.min(1.0, s));
        Misc.addChatMessage(Text.literal("§7[dwp] Size: §f" + size));
    }

    public static void setDistance(int d) {
        distance = Math.max(1, d);
        Misc.addChatMessage(Text.literal("§7[dwp] Distance: §f" + distance));
    }

    public static void resetSecrets() {
        Misc.addChatMessage(Text.literal("§7[dwp] Secret tracking reset (no-op in this version)."));
    }

    public static void setType(String name) {
        try {
            type = WaypointType.valueOf(name.toUpperCase());
            Misc.addChatMessage(Text.literal("§7[dwp] Type: §f" + type));
        } catch (IllegalArgumentException e) {
            Misc.addChatMessage(Text.literal("§cUnknown waypoint type: " + name));
        }
    }

    public static void setTimer(String name) {
        try {
            timer = TimerType.valueOf(name.toUpperCase());
            Misc.addChatMessage(Text.literal("§7[dwp] Timer: §f" + timer));
        } catch (IllegalArgumentException e) {
            Misc.addChatMessage(Text.literal("§cUnknown timer type: " + name));
        }
    }

    public static void toggleUseBlockSize() {
        useBlockSize = !useBlockSize;
        Misc.addChatMessage(Text.literal("§7[dwp] Use block size: " + (useBlockSize ? "§aon" : "§coff")));
    }

    public static void setOffset(double x, double y, double z) {
        offsetX = x; offsetY = y; offsetZ = z;
        Misc.addChatMessage(Text.literal("§7[dwp] One-shot offset set to §f" + x + ", " + y + ", " + z));
    }

    public static void toggleThrough() {
        through = !through;
        Misc.addChatMessage(Text.literal("§7[dwp] Through walls: " + (through ? "§aon" : "§coff")));
    }

    public static void setColor(String hex) {
        if (hex == null || hex.length() != 8) {
            Misc.addChatMessage(Text.literal("§cColor must be 8 hex chars (RRGGBBAA)."));
            return;
        }
        try {
            long rgba = Long.parseLong(hex, 16);
            int r = (int) ((rgba >> 24) & 0xFF);
            int g = (int) ((rgba >> 16) & 0xFF);
            int b = (int) ((rgba >> 8) & 0xFF);
            int a = (int) (rgba & 0xFF);
            color = (a << 24) | (r << 16) | (g << 8) | b;
            Misc.addChatMessage(Text.literal("§7[dwp] Color set."));
        } catch (NumberFormatException e) {
            Misc.addChatMessage(Text.literal("§cInvalid hex color: " + hex));
        }
    }

    public static void exportToClipboard() {
        String b64 = DungeonWaypointStore.exportBase64();
        if (b64 == null) {
            Misc.addChatMessage(Text.literal("§cExport failed."));
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.keyboard.setClipboard(b64);
        Misc.addChatMessage(Text.literal("§aWaypoint database copied to clipboard."));
    }

    public static void importFromClipboard() {
        MinecraftClient mc = MinecraftClient.getInstance();
        String clip = mc.keyboard.getClipboard();
        boolean ok = DungeonWaypointStore.importBase64(clip);
        if (ok) {
            lastTile = null; // force re-apply
            Misc.addChatMessage(Text.literal("§aWaypoint database imported from clipboard."));
        } else {
            Misc.addChatMessage(Text.literal("§cImport failed — clipboard doesn't look like a valid waypoint export."));
        }
    }

    public static void resetCurrentRoom() {
        RoomTile tile = currentRoomTile();
        if (tile == null) {
            Misc.addChatMessage(Text.literal("§cNot in a known dungeon room."));
            return;
        }
        DungeonWaypointStore.clearRoom(RoomSignature.withRotation(tile).key());
        applyRoom(tile);
        Misc.addChatMessage(Text.literal("§aCleared waypoints for the current room."));
    }

    public static boolean isEditMode() {
        return editMode;
    }

    // ================= Tick / interaction =================

    private static void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || !Location.inDungeon() || !MapReader.isCalibrated()) {
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
        while (placeKey != null && placeKey.wasPressed()) fired = true;
        if (fired && editMode && mc.currentScreen == null) {
            handlePlace(mc);
        }
    }

    private static RoomTile currentRoomTile() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || !MapReader.isCalibrated()) return null;
        GridPos tile = MapReader.worldToGridPos(mc.player.getX(), mc.player.getZ());
        return DungeonGrid.allRooms().get(tile);
    }

    /** Raycasts along the player's look vector, per {@link fishmod.features.PingFeature#placePing}. */
    private static Vec3d aimPoint(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        float delta = mc.getRenderTickCounter().getTickProgress(false);
        Vec3d eye = p.getCameraPosVec(delta);
        Vec3d look = p.getRotationVec(delta);
        Vec3d end = eye.add(look.multiply(distance));

        BlockHitResult hit = mc.world.raycast(new RaycastContext(
                eye, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, p));
        if (hit != null && hit.getType() != HitResult.Type.MISS) {
            BlockPos bp = hit.getBlockPos();
            return new Vec3d(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5);
        }
        return end;
    }

    private static void handlePlace(MinecraftClient mc) {
        RoomTile tile = currentRoomTile();
        if (tile == null) {
            Misc.addChatMessage(Text.literal("§cNot in a known dungeon room."));
            return;
        }

        Vec3d aim = aimPoint(mc);
        double px = aim.x + offsetX, py = aim.y + offsetY, pz = aim.z + offsetZ;
        offsetX = 0; offsetY = 0; offsetZ = 0; // one-shot

        RoomSignature.WithRotation sig = RoomSignature.withRotation(tile);
        StoredWaypoint canonical = toCanonical(tile, sig.rotation(), px, py, pz);

        if (mc.player.isSneaking()) {
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

    private static Vec3d toLive(StoredWaypoint w, RoomTile tile, int liveRotation) {
        int originX = MapReader.tileWorldOriginX(tile.pos().x());
        int originZ = MapReader.tileWorldOriginZ(tile.pos().z());
        double[] live = DungeonWaypointStore.rotate90(w.x, w.z, liveRotation % 4);
        return new Vec3d(originX + 16 + live[0], w.y, originZ + 16 + live[1]);
    }

    private static void applyRoom(RoomTile tile) {
        List<LiveWaypoint> result = new ArrayList<>();
        RoomSignature.WithRotation sig = RoomSignature.withRotation(tile);
        for (StoredWaypoint w : DungeonWaypointStore.get(sig.key())) {
            Vec3d center = toLive(w, tile, sig.rotation());
            Box box = new Box(center.x - w.halfX, center.y - w.halfY, center.z - w.halfZ,
                    center.x + w.halfX, center.y + w.halfY, center.z + w.halfZ);
            result.add(new LiveWaypoint(box, w.color, w.filled, w.throughWalls, w.title));
        }
        liveWaypoints = result;
    }

    // ================= Rendering =================

    private static void render(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext ctx,
                                net.minecraft.client.util.math.MatrixStack matrices,
                                net.minecraft.client.render.VertexConsumer vc) {
        if (!Location.inDungeon()) return;

        for (LiveWaypoint w : liveWaypoints) {
            float[] rgba = RenderUtils.toFloats(w.color);
            if (w.filled) RenderUtils.renderFilled(matrices, vc, w.box, rgba);
            else RenderUtils.renderOutline(matrices, vc, w.box, rgba);
            if (w.title != null && !w.title.isBlank()) {
                RenderUtils.renderText(ctx, matrices, Text.literal(w.title), w.center.x, w.box.maxY + 0.4, w.center.z, 1.0f);
            }
        }

        if (editMode) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null && mc.world != null && MapReader.isCalibrated()) {
                Vec3d aim = aimPoint(mc);
                double half = useBlockSize ? 0.5 : size / 2.0;
                Box box = new Box(aim.x - half, aim.y - half, aim.z - half, aim.x + half, aim.y + half, aim.z + half);
                RenderUtils.renderOutline(matrices, vc, box, new float[]{1f, 1f, 1f, 0.9f});
            }
        }
    }

    /** Small on-screen settings readout while edit mode is on, drawn near screen center via HudRenderCallback. */
    public static void renderOverlay(net.minecraft.client.gui.DrawContext ctx) {
        if (!editMode) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.textRenderer == null) return;
        int cx = ctx.getScaledWindowWidth() / 2;
        int y = ctx.getScaledWindowHeight() / 2 + 30;
        Text line = Text.literal("§b[dwp] §7fill:" + (fill ? "§ay" : "§cn") + " §7size:§f" + size
                + " §7dist:§f" + distance + " §7blockSize:" + (useBlockSize ? "§ay" : "§cn")
                + " §7through:" + (through ? "§ay" : "§cn") + " §7type:§f" + type + " §7timer:§f" + timer);
        int textWidth = mc.textRenderer.getWidth(line);
        ctx.drawText(mc.textRenderer, line, cx - textWidth / 2, y, 0xFFFFFFFF, true);
    }
}
