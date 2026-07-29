package fishmod.features.dungeon;

import fishmod.utils.Constants;
import fishmod.utils.Location;
import fishmod.utils.Misc;
import fishmod.utils.dungeon.map.GridPos;
import fishmod.utils.dungeon.map.MapReader;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * /fmwp — an OdinLegacy-style dungeon waypoint editor (github.com/odtheking/OdinLegacy,
 * DungeonWaypoints.kt/DungeonWaypointCommand.kt/DungeonWaypointConfig.kt). In a calibrated Hypixel
 * dungeon, waypoints are stored per grid tile (see {@link #tileKey}), keyed off the fixed 32-block
 * world grid via {@link MapReader#worldToGridPos} — no room-shape/door detection is involved, so a
 * waypoint only replays automatically when the same tile position recurs (no rotation normalization).
 * Outside a calibrated dungeon (any other server/world), waypoints instead fall back to a freeform
 * mode keyed by server+dimension and stored at absolute world coordinates — see {@link #globalKey}.
 */
public final class DungeonWaypoints {

    private DungeonWaypoints() {}

    private static final double PLACE_EPSILON = 0.05;
    private static final double ROUTE_REACH_RADIUS = 1.75;
    private static final float[] ROUTE_LINE_RGBA = {1f, 1f, 1f, 0.6f};

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
    /** Outline line width, applies to non-filled waypoint boxes and the edit-mode cursor box. */
    private static double lineWidth = 2.0;

    private static KeyBinding placeKey;
    private static GridPos lastTile = null;
    private static String lastGlobalDim = null;

    // --- Route recording (see toggleRoute) ---
    private static String recordingRouteId = null;
    private static int recordingNextOrder = 0;
    /** routeId -> set of routeOrder values already reached on the current run; cleared by endRoute. Session-only. */
    private static final Map<String, Set<Integer>> routeReached = new HashMap<>();

    /** A waypoint applied to the currently-live room, in real world coordinates. */
    private static final class LiveWaypoint {
        final Box box; final int color; final boolean filled; final boolean throughWalls; final String title; final Vec3d center;
        final String routeId; final int routeOrder;
        LiveWaypoint(Box box, int color, boolean filled, boolean throughWalls, String title, String routeId, int routeOrder) {
            this.box = box; this.color = color; this.filled = filled; this.throughWalls = throughWalls; this.title = title;
            this.center = box.getCenter();
            this.routeId = routeId; this.routeOrder = routeOrder;
        }
    }

    private static List<LiveWaypoint> liveWaypoints = new ArrayList<>();

    public static void init() {
        placeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "FishMod - Dungeon Waypoint place/remove",
                InputUtil.Type.MOUSE,
                GLFW.GLFW_MOUSE_BUTTON_RIGHT,
                fishmod.utils.Keybinds.category()));

        ClientTickEvents.END_CLIENT_TICK.register(DungeonWaypoints::onTick);
        RenderingEvents.NO_DEPTH_FILLED.register((ctx, matrices, vc) -> render(ctx, matrices, vc));
        // All GL_LINES geometry (box outlines, route connector lines, edit-mode cursor box) goes
        // through a dedicated lines layer, never the box-fill layer's triangle strip — see renderLines().
        RenderingEvents.NO_DEPTH_LINE.register((ctx, matrices, vc) -> renderLines(matrices, vc));
    }

    /**
     * Whether {@code target} is actually visible from the player's eyes right now — used for
     * non-through-walls waypoints. Everything here always renders on a no-depth (through-walls)
     * layer regardless of the per-waypoint flag: registering on this mod's depth-tested layer
     * (FILLED_BLOCK/LINE) turned out to be unproven plumbing nothing else in the codebase actually
     * exercises, and it silently broke room waypoints outright rather than just occluding them. A
     * simple line-of-sight raycast — the same RaycastContext machinery {@link #aimPoint} already
     * uses — gets the same "hidden behind a wall" result without depending on that.
     */
    private static boolean hasLineOfSight(MinecraftClient mc, Vec3d target) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) return true;
        float delta = mc.getRenderTickCounter().getTickProgress(false);
        Vec3d eye = p.getCameraPosVec(delta);
        BlockHitResult hit = mc.world.raycast(new RaycastContext(
                eye, target, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
        return hit == null || hit.getType() == HitResult.Type.MISS;
    }

    // ================= Commands =================

    public static void toggleEdit() {
        editMode = !editMode;
        Misc.addChatMessage(Text.literal("Dungeon Waypoint editing " + (editMode ? "§aenabled" : "§cdisabled") + "§r!"));
    }

    public static void toggleFill() {
        fill = !fill;
        Misc.addChatMessage(Text.literal("§7[fmwp] Fill: " + (fill ? "§afilled" : "§coutline")));
    }

    public static void setSize(double s) {
        size = Math.max(0.1, Math.min(1.0, s));
        Misc.addChatMessage(Text.literal("§7[fmwp] Size: §f" + size));
    }

    public static void setDistance(int d) {
        distance = Math.max(1, d);
        Misc.addChatMessage(Text.literal("§7[fmwp] Distance: §f" + distance));
    }

    public static void resetSecrets() {
        Misc.addChatMessage(Text.literal("§7[fmwp] Secret tracking reset (no-op in this version)."));
    }

    public static void setType(String name) {
        try {
            type = WaypointType.valueOf(name.toUpperCase());
            Misc.addChatMessage(Text.literal("§7[fmwp] Type: §f" + type));
        } catch (IllegalArgumentException e) {
            Misc.addChatMessage(Text.literal("§cUnknown waypoint type: " + name));
        }
    }

    public static void setTimer(String name) {
        try {
            timer = TimerType.valueOf(name.toUpperCase());
            Misc.addChatMessage(Text.literal("§7[fmwp] Timer: §f" + timer));
        } catch (IllegalArgumentException e) {
            Misc.addChatMessage(Text.literal("§cUnknown timer type: " + name));
        }
    }

    public static void toggleUseBlockSize() {
        useBlockSize = !useBlockSize;
        Misc.addChatMessage(Text.literal("§7[fmwp] Use block size: " + (useBlockSize ? "§aon" : "§coff")));
    }

    public static void setOffset(double x, double y, double z) {
        offsetX = x; offsetY = y; offsetZ = z;
        Misc.addChatMessage(Text.literal("§7[fmwp] One-shot offset set to §f" + x + ", " + y + ", " + z));
    }

    public static void toggleThrough() {
        through = !through;
        Misc.addChatMessage(Text.literal("§7[fmwp] Through walls: " + (through ? "§aon" : "§coff")));
    }

    /** Outline line width — only visible when fill is off. */
    public static void setLineWidth(double w) {
        lineWidth = Math.max(0.5, Math.min(10.0, w));
        Misc.addChatMessage(Text.literal("§7[fmwp] Line size: §f" + lineWidth));
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
            Misc.addChatMessage(Text.literal("§7[fmwp] Color set."));
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
        GridPos tile = currentTile();
        if (tile == null) {
            Misc.addChatMessage(Text.literal("§cNot in a known dungeon room."));
            return;
        }
        DungeonWaypointStore.clearRoom(tileKey(tile));
        applyRoom(tile);
        Misc.addChatMessage(Text.literal("§aCleared waypoints for the current room."));
    }

    /** Key for the waypoint bucket belonging to a single fixed grid tile. */
    private static String tileKey(GridPos tile) {
        return "tile:" + tile.x() + "," + tile.z();
    }

    public static boolean isEditMode() {
        return editMode;
    }

    public static void openGui() {
        MinecraftClient.getInstance().setScreen(new DungeonWaypointListScreen());
    }

    // ================= Routes =================

    /**
     * Toggles route recording, on or off — a plain on/off switch like toggleFill/toggleThrough.
     * Off -> on: waypoints placed from now on are appended, in order, to a route instead of being
     * standalone. Plain {@code /fmwp route} (no name) auto-names it "route1", "route2", etc.; a name
     * can still be given to pick one explicitly. On -> off: stops recording (any name argument is
     * ignored on the way off, so {@code /fmwp route} always ends whatever's currently recording).
     */
    public static void toggleRoute(String name) {
        if (recordingRouteId != null) {
            Misc.addChatMessage(Text.literal("§aFinished recording route '" + recordingRouteId + "' (" + recordingNextOrder + " point(s))."));
            recordingRouteId = null;
            return;
        }
        recordingRouteId = (name == null || name.isBlank()) ? nextAutoRouteName() : name;
        recordingNextOrder = 0;
        Misc.addChatMessage(Text.literal("§aRecording route '" + recordingRouteId + "' — place waypoints in order, then §f/fmwp route§a to finish."));
    }

    public static boolean isRecordingRoute() {
        return recordingRouteId != null;
    }

    private static boolean routeExists(String id) {
        for (List<StoredWaypoint> list : DungeonWaypointStore.allData().values())
            for (StoredWaypoint w : list) if (id.equals(w.routeId)) return true;
        return false;
    }

    private static String nextAutoRouteName() {
        int n = 1;
        while (routeExists("route" + n)) n++;
        return "route" + n;
    }

    /** Clears a route's "reached" progress so its waypoints show up again for another run. Null/blank name resets every route. */
    public static void endRoute(String name) {
        if (name == null || name.isBlank()) {
            routeReached.clear();
            Misc.addChatMessage(Text.literal("§aAll routes reset — waypoints visible again."));
        } else {
            routeReached.remove(name);
            Misc.addChatMessage(Text.literal("§aRoute '" + name + "' reset — waypoints visible again."));
        }
    }

    public static void deleteRoute(String name) {
        if (name == null || name.isBlank()) {
            Misc.addChatMessage(Text.literal("§cGive a route name: /fmwp route delete <name>"));
            return;
        }
        int n = DungeonWaypointStore.removeRoute(name);
        routeReached.remove(name);
        refreshLive();
        Misc.addChatMessage(Text.literal(n > 0
                ? "§aDeleted route '" + name + "' (" + n + " point(s))."
                : "§cNo route named '" + name + "' found."));
    }

    private static boolean dungeonMode() {
        return Location.inDungeon() && MapReader.isCalibrated();
    }

    /**
     * Key for the current freeform waypoint bucket, used outside calibrated dungeons. On Hypixel
     * Skyblock this is keyed by the current island/zone (per {@link Location}, e.g. HUB, THE_PARK,
     * CRYSTAL_HOLLOWS) rather than the Minecraft dimension — Skyblock crams most islands into a single
     * dimension, so a dimension-keyed bucket would mix waypoints from unrelated places together.
     * Off Skyblock, falls back to server address + dimension.
     */
    private static String globalKey() {
        if (Location.inSkyblock()) {
            return "global:skyblock:" + Location.getCurrentLocation().name();
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        String server;
        try {
            var si = mc.getCurrentServerEntry();
            server = (si != null && si.address != null) ? si.address : "singleplayer";
        } catch (Exception e) {
            server = "singleplayer";
        }
        String dimId = mc.world != null ? mc.world.getRegistryKey().getValue().toString() : "unknown";
        return "global:" + server + ":" + dimId;
    }

    public static String currentGlobalKeyForGui() {
        return globalKey();
    }

    // ================= Tick / interaction =================

    private static void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) {
            liveWaypoints.clear();
            lastTile = null;
            lastGlobalDim = null;
            return;
        }

        if (dungeonMode()) {
            lastGlobalDim = null;
            GridPos tile = MapReader.worldToGridPos(mc.player.getX(), mc.player.getZ());
            if (!tile.equals(lastTile)) {
                lastTile = tile;
                applyRoom(tile);
            }
        } else {
            lastTile = null;
            String key = globalKey();
            if (!key.equals(lastGlobalDim)) {
                lastGlobalDim = key;
                applyGlobal();
            }
        }

        advanceRouteProgress(mc);

        boolean fired = false;
        while (placeKey != null && placeKey.wasPressed()) fired = true;
        if (fired && editMode && mc.currentScreen == null) {
            handlePlace(mc);
        }
    }

    /** Groups live waypoints by route, sorted in placement order. Pure/read-only — safe to call from render. */
    private static Map<String, List<LiveWaypoint>> groupRoutes() {
        Map<String, List<LiveWaypoint>> routeGroups = new LinkedHashMap<>();
        for (LiveWaypoint w : liveWaypoints) {
            if (w.routeId != null) routeGroups.computeIfAbsent(w.routeId, k -> new ArrayList<>()).add(w);
        }
        for (List<LiveWaypoint> group : routeGroups.values()) {
            group.sort(Comparator.comparingInt(w -> w.routeOrder));
        }
        return routeGroups;
    }

    /** Marks the next unreached point of each visible route as reached once the player gets close enough. */
    private static void advanceRouteProgress(MinecraftClient mc) {
        if (mc.player == null) return;
        Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        for (Map.Entry<String, List<LiveWaypoint>> g : groupRoutes().entrySet()) {
            Set<Integer> reached = routeReached.computeIfAbsent(g.getKey(), k -> new HashSet<>());
            for (LiveWaypoint w : g.getValue()) {
                if (reached.contains(w.routeOrder)) continue;
                if (playerPos.distanceTo(w.center) < ROUTE_REACH_RADIUS) reached.add(w.routeOrder);
                break; // only the next point in sequence counts
            }
        }
    }

    private static GridPos currentTile() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || !MapReader.isCalibrated()) return null;
        return MapReader.worldToGridPos(mc.player.getX(), mc.player.getZ());
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
            // Center on the targeted block itself, same as the X/Z centering below — the old code
            // only added the +0.5 on X/Z and left Y as the block's raw (bottom) coordinate, so the
            // marker sat half a block low and got clipped by the block underneath it.
            BlockPos bp = hit.getBlockPos();
            return new Vec3d(bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5);
        }
        return end;
    }

    private static void handlePlace(MinecraftClient mc) {
        if (!dungeonMode()) {
            handlePlaceGlobal(mc);
            return;
        }

        GridPos tile = currentTile();
        if (tile == null) {
            Misc.addChatMessage(Text.literal("§cNot in a known dungeon room."));
            return;
        }

        Vec3d aim = aimPoint(mc);
        double px = aim.x + offsetX, py = aim.y + offsetY, pz = aim.z + offsetZ;
        offsetX = 0; offsetY = 0; offsetZ = 0; // one-shot

        String key = tileKey(tile);
        StoredWaypoint canonical = toCanonical(tile, px, py, pz);

        if (mc.player.isSneaking()) {
            mc.setScreen(new DungeonWaypointTitleScreen(title -> {
                canonical.title = title;
                tagRoute(canonical);
                DungeonWaypointStore.add(key, canonical);
                applyRoom(tile);
            }));
            return;
        }

        boolean removed = DungeonWaypointStore.removeNear(key, canonical.x, canonical.y, canonical.z, PLACE_EPSILON);
        if (!removed) {
            tagRoute(canonical);
            DungeonWaypointStore.add(key, canonical);
        }
        applyRoom(tile);
    }

    /** If a route is currently being recorded, tags {@code w} with it and advances the recording order. */
    private static void tagRoute(StoredWaypoint w) {
        if (recordingRouteId == null) return;
        w.routeId = recordingRouteId;
        w.routeOrder = recordingNextOrder++;
    }

    /** Places/removes a freeform waypoint at absolute world coordinates, used outside calibrated dungeons. */
    private static void handlePlaceGlobal(MinecraftClient mc) {
        Vec3d aim = aimPoint(mc);
        double px = aim.x + offsetX, py = aim.y + offsetY, pz = aim.z + offsetZ;
        offsetX = 0; offsetY = 0; offsetZ = 0; // one-shot

        double half = useBlockSize ? 0.5 : size / 2.0;
        String key = globalKey();

        if (mc.player.isSneaking()) {
            mc.setScreen(new DungeonWaypointTitleScreen(title -> {
                StoredWaypoint w = new StoredWaypoint(px, py, pz, half, half, half,
                        color, fill, through, title,
                        type == WaypointType.NONE ? null : type.name(),
                        timer == TimerType.NONE ? null : timer.name());
                tagRoute(w);
                DungeonWaypointStore.add(key, w);
                applyGlobal();
            }));
            return;
        }

        boolean removed = DungeonWaypointStore.removeNear(key, px, py, pz, PLACE_EPSILON);
        if (!removed) {
            StoredWaypoint w = new StoredWaypoint(px, py, pz, half, half, half,
                    color, fill, through, null,
                    type == WaypointType.NONE ? null : type.name(),
                    timer == TimerType.NONE ? null : timer.name());
            tagRoute(w);
            DungeonWaypointStore.add(key, w);
        }
        applyGlobal();
    }

    /** Loads the freeform waypoints for the current server+dimension as live, absolute-coordinate boxes. */
    private static void applyGlobal() {
        List<LiveWaypoint> result = new ArrayList<>();
        for (StoredWaypoint w : DungeonWaypointStore.get(globalKey())) {
            Box box = new Box(w.x - w.halfX, w.y - w.halfY, w.z - w.halfZ,
                    w.x + w.halfX, w.y + w.halfY, w.z + w.halfZ);
            result.add(new LiveWaypoint(box, w.color, w.filled, w.throughWalls, w.title, w.routeId, w.routeOrder));
        }
        liveWaypoints = result;
    }

    /** Called by the waypoint list GUI after it edits/deletes entries, to refresh what's currently rendering. */
    public static void refreshLive() {
        if (dungeonMode()) {
            GridPos tile = currentTile();
            if (tile != null) applyRoom(tile);
            else liveWaypoints.clear();
        } else {
            applyGlobal();
        }
    }

    // ================= Canonical <-> live conversion =================

    private static StoredWaypoint toCanonical(GridPos tile, double worldX, double worldY, double worldZ) {
        int originX = MapReader.tileWorldOriginX(tile.x());
        int originZ = MapReader.tileWorldOriginZ(tile.z());
        double lx = worldX - (originX + 16);
        double lz = worldZ - (originZ + 16);

        double half = useBlockSize ? 0.5 : size / 2.0;
        return new StoredWaypoint(lx, worldY, lz, half, half, half,
                color, fill, through, null,
                type == WaypointType.NONE ? null : type.name(),
                timer == TimerType.NONE ? null : timer.name());
    }

    private static Vec3d toLive(StoredWaypoint w, GridPos tile) {
        int originX = MapReader.tileWorldOriginX(tile.x());
        int originZ = MapReader.tileWorldOriginZ(tile.z());
        return new Vec3d(originX + 16 + w.x, w.y, originZ + 16 + w.z);
    }

    private static void applyRoom(GridPos tile) {
        List<LiveWaypoint> result = new ArrayList<>();
        for (StoredWaypoint w : DungeonWaypointStore.get(tileKey(tile))) {
            Vec3d center = toLive(w, tile);
            Box box = new Box(center.x - w.halfX, center.y - w.halfY, center.z - w.halfZ,
                    center.x + w.halfX, center.y + w.halfY, center.z + w.halfZ);
            result.add(new LiveWaypoint(box, w.color, w.filled, w.throughWalls, w.title, w.routeId, w.routeOrder));
        }
        liveWaypoints = result;
    }

    // ================= Rendering =================

    private static void render(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext ctx,
                                net.minecraft.client.util.math.MatrixStack matrices,
                                net.minecraft.client.render.VertexConsumer vc) {
        MinecraftClient mc = MinecraftClient.getInstance();
        for (LiveWaypoint w : liveWaypoints) {
            if (!w.throughWalls && !hasLineOfSight(mc, w.center)) continue;
            if (w.routeId != null && routeReached.getOrDefault(w.routeId, Set.of()).contains(w.routeOrder)) continue;
            // Outlines are drawn separately in renderLines() — this triangle-strip layer corrupts
            // into stray triangles ("bowtie" artifacts) if outline line-pairs are pushed into it.
            if (w.filled) {
                RenderUtils.renderFilled(matrices, vc, w.box, RenderUtils.toFloats(w.color));
            }
            if (w.title != null && !w.title.isBlank()) {
                RenderUtils.renderText(ctx, matrices, Text.literal(w.title), w.center.x, w.box.maxY + 0.4, w.center.z, 1.0f);
            }
        }
    }

    /**
     * Draws everything that's actual GL_LINES geometry: non-filled waypoint box outlines, route
     * connector lines, and the edit-mode cursor preview outline. Runs on a dedicated GL_LINES layer
     * (see {@link RenderingEvents#NO_DEPTH_LINE}) — this must never share a VertexConsumer with
     * {@link #render} (box fills + text), which uses a triangle-strip layer.
     */
    private static void renderLines(net.minecraft.client.util.math.MatrixStack matrices,
                                     net.minecraft.client.render.VertexConsumer vc) {
        MinecraftClient mc = MinecraftClient.getInstance();
        for (LiveWaypoint w : liveWaypoints) {
            if (w.filled) continue;
            if (!w.throughWalls && !hasLineOfSight(mc, w.center)) continue;
            if (w.routeId != null && routeReached.getOrDefault(w.routeId, Set.of()).contains(w.routeOrder)) continue;
            RenderUtils.renderOutline(matrices, vc, w.box, RenderUtils.toFloats(w.color), (float) lineWidth);
        }

        for (Map.Entry<String, List<LiveWaypoint>> g : groupRoutes().entrySet()) {
            Set<Integer> reached = routeReached.getOrDefault(g.getKey(), Set.of());
            LiveWaypoint prev = null;
            for (LiveWaypoint w : g.getValue()) {
                if (reached.contains(w.routeOrder)) continue;
                if (prev != null) RenderUtils.renderLine(matrices, vc, prev.center, w.center, ROUTE_LINE_RGBA);
                prev = w;
            }
        }

        if (editMode) {
            if (mc.player != null && mc.world != null) {
                Vec3d aim = aimPoint(mc);
                double half = useBlockSize ? 0.5 : size / 2.0;
                Box box = new Box(aim.x - half, aim.y - half, aim.z - half, aim.x + half, aim.y + half, aim.z + half);
                RenderUtils.renderOutline(matrices, vc, box, new float[]{1f, 1f, 1f, 0.9f}, (float) lineWidth);
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
        Text line = Text.literal("§b[fmwp] §7fill:" + (fill ? "§ay" : "§cn") + " §7size:§f" + size
                + " §7dist:§f" + distance + " §7blockSize:" + (useBlockSize ? "§ay" : "§cn")
                + " §7through:" + (through ? "§ay" : "§cn") + " §7type:§f" + type + " §7timer:§f" + timer
                + (!fill ? " §7line:§f" + lineWidth : "")
                + (recordingRouteId != null ? " §d🔗route:" + recordingRouteId : ""));
        int textWidth = mc.textRenderer.getWidth(line);
        ctx.drawText(mc.textRenderer, line, cx - textWidth / 2, y, 0xFFFFFFFF, true);
    }
}
