package fishmod.features.dungeon;

import com.mojang.blaze3d.platform.InputConstants;
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
 * mode keyed by island/server+dimension and stored at absolute world coordinates — see {@link #globalKey}.
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
    /** Outline thickness in blocks, applies to non-filled waypoint boxes and the edit-mode cursor box. */
    private static double lineWidth = 0.05;

    private static KeyMapping placeKey;
    private static GridPos lastTile = null;
    private static String lastGlobalDim = null;

    // --- Route recording (see toggleRoute) ---
    private static String recordingRouteId = null;
    private static int recordingNextOrder = 0;
    /** routeId -> set of routeOrder values already reached on the current run; cleared by endRoute. Session-only. */
    private static final Map<String, Set<Integer>> routeReached = new HashMap<>();

    /** A waypoint applied to the currently-live room, in real world coordinates. */
    private static final class LiveWaypoint {
        final AABB box; final int color; final boolean filled; final boolean throughWalls; final String title; final Vec3 center;
        final String routeId; final int routeOrder;
        LiveWaypoint(AABB box, int color, boolean filled, boolean throughWalls, String title, String routeId, int routeOrder) {
            this.box = box; this.color = color; this.filled = filled; this.throughWalls = throughWalls; this.title = title;
            this.center = box.getCenter();
            this.routeId = routeId; this.routeOrder = routeOrder;
        }
    }

    private static List<LiveWaypoint> liveWaypoints = new ArrayList<>();

    public static void init() {
        KeyMapping.Category category = fishmod.utils.Keybinds.category();
        placeKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "FishMod: Dungeon Waypoint place/remove",
                InputConstants.Type.MOUSE,
                GLFW.GLFW_MOUSE_BUTTON_RIGHT,
                category));

        ClientTickEvents.END_CLIENT_TICK.register(DungeonWaypoints::onTick);
        RenderingEvents.NO_DEPTH_FILLED.register((ctx, matrices, vc) -> render(ctx, matrices, vc));
        // All GL_LINES geometry (route connector lines, edit-mode cursor box) goes through a
        // dedicated lines layer, never the box-fill layer's triangle strip — see renderLines().
        RenderingEvents.NO_DEPTH_LINE.register((ctx, matrices, vc) -> renderLines(matrices, vc));
    }

    /**
     * Whether {@code target} is actually visible from the player's eyes right now — used for
     * non-through-walls waypoints. Everything here always renders on a no-depth (through-walls)
     * layer regardless of the per-waypoint flag: registering on this mod's depth-tested layer
     * (FILLED_BLOCK/LINE) turned out to be unproven plumbing nothing else in the codebase actually
     * exercises, and it silently broke room waypoints outright rather than just occluding them. A
     * simple line-of-sight raycast — the same ClipContext machinery {@link #aimPoint} already uses —
     * gets the same "hidden behind a wall" result without depending on that.
     */
    private static boolean hasLineOfSight(Minecraft mc, Vec3 target) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return true;
        float delta = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 eye = p.getEyePosition(delta);
        BlockHitResult hit = mc.level.clip(new ClipContext(
                eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return hit == null || hit.getType() == HitResult.Type.MISS;
    }

    // ================= Commands =================

    public static void toggleEdit() {
        editMode = !editMode;
        Misc.addChatMessage(Component.literal("Dungeon Waypoint editing " + (editMode ? "§aenabled" : "§cdisabled") + "§r!"));
    }

    public static void toggleFill() {
        fill = !fill;
        Misc.addChatMessage(Component.literal("§7[fmwp] Fill: " + (fill ? "§afilled" : "§coutline")));
    }

    public static void setSize(double s) {
        size = Math.max(0.1, Math.min(1.0, s));
        Misc.addChatMessage(Component.literal("§7[fmwp] Size: §f" + size));
    }

    public static void setDistance(int d) {
        distance = Math.max(1, d);
        Misc.addChatMessage(Component.literal("§7[fmwp] Distance: §f" + distance));
    }

    public static void resetSecrets() {
        Misc.addChatMessage(Component.literal("§7[fmwp] Secret tracking reset (no-op in this version)."));
    }

    public static void setType(String name) {
        try {
            type = WaypointType.valueOf(name.toUpperCase());
            Misc.addChatMessage(Component.literal("§7[fmwp] Type: §f" + type));
        } catch (IllegalArgumentException e) {
            Misc.addChatMessage(Component.literal("§cUnknown waypoint type: " + name));
        }
    }

    public static void setTimer(String name) {
        try {
            timer = TimerType.valueOf(name.toUpperCase());
            Misc.addChatMessage(Component.literal("§7[fmwp] Timer: §f" + timer));
        } catch (IllegalArgumentException e) {
            Misc.addChatMessage(Component.literal("§cUnknown timer type: " + name));
        }
    }

    public static void toggleUseBlockSize() {
        useBlockSize = !useBlockSize;
        Misc.addChatMessage(Component.literal("§7[fmwp] Use block size: " + (useBlockSize ? "§aon" : "§coff")));
    }

    public static void setOffset(double x, double y, double z) {
        offsetX = x; offsetY = y; offsetZ = z;
        Misc.addChatMessage(Component.literal("§7[fmwp] One-shot offset set to §f" + x + ", " + y + ", " + z));
    }

    public static void toggleThrough() {
        through = !through;
        Misc.addChatMessage(Component.literal("§7[fmwp] Through walls: " + (through ? "§aon" : "§coff")));
    }

    /** Outline thickness in blocks — only visible when fill is off. */
    public static void setLineWidth(double w) {
        lineWidth = Math.max(0.01, Math.min(0.5, w));
        Misc.addChatMessage(Component.literal("§7[fmwp] Line size: §f" + lineWidth));
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
            Misc.addChatMessage(Component.literal("§7[fmwp] Color set."));
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
        GridPos tile = currentTile();
        if (tile == null) {
            Misc.addChatMessage(Component.literal("§cNot in a known dungeon room."));
            return;
        }
        DungeonWaypointStore.clearRoom(tileKey(tile));
        applyRoom(tile);
        Misc.addChatMessage(Component.literal("§aCleared waypoints for the current room."));
    }

    /** Key for the waypoint bucket belonging to a single fixed grid tile. */
    private static String tileKey(GridPos tile) {
        return "tile:" + tile.x() + "," + tile.z();
    }

    public static boolean isEditMode() {
        return editMode;
    }

    public static void openGui() {
        Minecraft.getInstance().setScreen(new DungeonWaypointListScreen());
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
            Misc.addChatMessage(Component.literal("§aFinished recording route '" + recordingRouteId + "' (" + recordingNextOrder + " point(s))."));
            recordingRouteId = null;
            return;
        }
        recordingRouteId = (name == null || name.isBlank()) ? nextAutoRouteName() : name;
        recordingNextOrder = 0;
        Misc.addChatMessage(Component.literal("§aRecording route '" + recordingRouteId + "' — place waypoints in order, then §f/fmwp route§a to finish."));
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
            Misc.addChatMessage(Component.literal("§aAll routes reset — waypoints visible again."));
        } else {
            routeReached.remove(name);
            Misc.addChatMessage(Component.literal("§aRoute '" + name + "' reset — waypoints visible again."));
        }
    }

    public static void deleteRoute(String name) {
        if (name == null || name.isBlank()) {
            Misc.addChatMessage(Component.literal("§cGive a route name: /fmwp route delete <name>"));
            return;
        }
        int n = DungeonWaypointStore.removeRoute(name);
        routeReached.remove(name);
        refreshLive();
        Misc.addChatMessage(Component.literal(n > 0
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

        Minecraft mc = Minecraft.getInstance();
        String server;
        try {
            var si = mc.getCurrentServer();
            server = (si != null && si.ip != null) ? si.ip : "singleplayer";
        } catch (Exception e) {
            server = "singleplayer";
        }
        String dimId = mc.level != null ? mc.level.dimension().identifier().toString() : "unknown";
        return "global:" + server + ":" + dimId;
    }

    public static String currentGlobalKeyForGui() {
        return globalKey();
    }

    // ================= Tick / interaction =================

    private static void onTick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
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
        while (placeKey != null && placeKey.consumeClick()) fired = true;
        if (fired && editMode && mc.screen == null) {
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
    private static void advanceRouteProgress(Minecraft mc) {
        if (mc.player == null) return;
        Vec3 playerPos = mc.player.position();
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
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !MapReader.isCalibrated()) return null;
        return MapReader.worldToGridPos(mc.player.getX(), mc.player.getZ());
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
            // Center on the targeted block itself, same as the X/Z centering below — the old code
            // only added the +0.5 on X/Z and left Y as the block's raw (bottom) coordinate, so the
            // marker sat half a block low and got clipped by the block underneath it.
            BlockPos bp = hit.getBlockPos();
            return new Vec3(bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5);
        }
        return end;
    }

    private static void handlePlace(Minecraft mc) {
        if (!dungeonMode()) {
            handlePlaceGlobal(mc);
            return;
        }

        GridPos tile = currentTile();
        if (tile == null) {
            Misc.addChatMessage(Component.literal("§cNot in a known dungeon room."));
            return;
        }

        Vec3 aim = aimPoint(mc);
        double px = aim.x + offsetX, py = aim.y + offsetY, pz = aim.z + offsetZ;
        offsetX = 0; offsetY = 0; offsetZ = 0; // one-shot

        String key = tileKey(tile);
        StoredWaypoint canonical = toCanonical(tile, px, py, pz);

        if (mc.player.isShiftKeyDown()) {
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
    private static void handlePlaceGlobal(Minecraft mc) {
        Vec3 aim = aimPoint(mc);
        double px = aim.x + offsetX, py = aim.y + offsetY, pz = aim.z + offsetZ;
        offsetX = 0; offsetY = 0; offsetZ = 0; // one-shot

        double half = useBlockSize ? 0.5 : size / 2.0;
        String key = globalKey();

        if (mc.player.isShiftKeyDown()) {
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
            AABB box = new AABB(w.x - w.halfX, w.y - w.halfY, w.z - w.halfZ,
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

    private static Vec3 toLive(StoredWaypoint w, GridPos tile) {
        int originX = MapReader.tileWorldOriginX(tile.x());
        int originZ = MapReader.tileWorldOriginZ(tile.z());
        return new Vec3(originX + 16 + w.x, w.y, originZ + 16 + w.z);
    }

    private static void applyRoom(GridPos tile) {
        List<LiveWaypoint> result = new ArrayList<>();
        for (StoredWaypoint w : DungeonWaypointStore.get(tileKey(tile))) {
            Vec3 center = toLive(w, tile);
            AABB box = new AABB(center.x - w.halfX, center.y - w.halfY, center.z - w.halfZ,
                    center.x + w.halfX, center.y + w.halfY, center.z + w.halfZ);
            result.add(new LiveWaypoint(box, w.color, w.filled, w.throughWalls, w.title, w.routeId, w.routeOrder));
        }
        liveWaypoints = result;
    }

    // ================= Rendering =================

    private static void render(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext ctx,
                                com.mojang.blaze3d.vertex.PoseStack matrices,
                                com.mojang.blaze3d.vertex.VertexConsumer vc) {
        Minecraft mc = Minecraft.getInstance();
        for (LiveWaypoint w : liveWaypoints) {
            if (!w.throughWalls && !hasLineOfSight(mc, w.center)) continue;
            if (w.routeId != null && routeReached.getOrDefault(w.routeId, Set.of()).contains(w.routeOrder)) continue;
            float[] rgba = RenderUtils.toFloats(w.color);
            // Outlines are thin filled boxes (renderThickOutline), not GL_LINES — that keeps their
            // thickness an actual configurable size and, just as importantly, keeps them on the same
            // triangle-strip layer as filled boxes so nothing gets mixed with real GL_LINES data
            // (mixing topologies on one layer is what caused the earlier "bowtie" corruption).
            if (w.filled) RenderUtils.renderFilled(matrices, vc, w.box, rgba);
            else RenderUtils.renderThickOutline(matrices, vc, w.box, rgba, lineWidth);
            if (w.title != null && !w.title.isBlank()) {
                RenderUtils.renderText(ctx, matrices, Component.literal(w.title), w.center.x, w.box.maxY + 0.4, w.center.z, 1.0f);
            }
        }

        if (editMode) {
            if (mc.player != null && mc.level != null) {
                Vec3 aim = aimPoint(mc);
                double half = useBlockSize ? 0.5 : size / 2.0;
                AABB box = new AABB(aim.x - half, aim.y - half, aim.z - half, aim.x + half, aim.y + half, aim.z + half);
                RenderUtils.renderThickOutline(matrices, vc, box, new float[]{1f, 1f, 1f, 0.9f}, lineWidth);
            }
        }
    }

    /**
     * Draws the one thing that's genuine GL_LINES geometry: route connector lines. Runs on a
     * dedicated GL_LINES layer (see {@link RenderingEvents#NO_DEPTH_LINE}) — this must never share a
     * VertexConsumer with {@link #render} (box fills/outlines + text), which uses a triangle-strip
     * layer. Pushing line-pair vertices into that triangle-strip buffer is exactly what produced the
     * corrupted "bowtie" shapes users reported: each 2-vertex line got stitched into the strip as a
     * stray, often huge, degenerate triangle connecting unrelated geometry.
     */
    private static void renderLines(com.mojang.blaze3d.vertex.PoseStack matrices,
                                     com.mojang.blaze3d.vertex.VertexConsumer vc) {
        for (Map.Entry<String, List<LiveWaypoint>> g : groupRoutes().entrySet()) {
            Set<Integer> reached = routeReached.getOrDefault(g.getKey(), Set.of());
            LiveWaypoint prev = null;
            for (LiveWaypoint w : g.getValue()) {
                if (reached.contains(w.routeOrder)) continue;
                if (prev != null) RenderUtils.renderLine(matrices, vc, prev.center, w.center, ROUTE_LINE_RGBA);
                prev = w;
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
        Component line = Component.literal("§b[fmwp] §7fill:" + (fill ? "§ay" : "§cn") + " §7size:§f" + size
                + " §7dist:§f" + distance + " §7blockSize:" + (useBlockSize ? "§ay" : "§cn")
                + " §7through:" + (through ? "§ay" : "§cn") + " §7type:§f" + type + " §7timer:§f" + timer
                + (!fill ? " §7line:§f" + lineWidth : "")
                + (recordingRouteId != null ? " §d🔗route:" + recordingRouteId : ""));
        int textWidth = mc.font.width(line);
        ctx.text(mc.font, line, cx - textWidth / 2, y, 0xFFFFFFFF, true);
    }
}
