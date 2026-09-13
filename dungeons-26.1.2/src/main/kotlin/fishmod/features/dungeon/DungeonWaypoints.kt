package fishmod.features.dungeon

import com.mojang.blaze3d.platform.InputConstants
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import fishmod.utils.Constants
import fishmod.utils.Location
import fishmod.utils.Misc
import fishmod.utils.dungeon.waypoints.DungeonWaypointStore
import fishmod.utils.dungeon.waypoints.StoredWaypoint
import fishmod.utils.dungeon.waypoints.TimerType
import fishmod.utils.dungeon.waypoints.WaypointType
import fishmod.utils.config.values.FishSettings
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.lwjgl.glfw.GLFW
import java.util.LinkedHashMap

/**
 * /fm wp — a waypoint editor. Works everywhere, dungeons included. Outside dungeons waypoints are
 * keyed by Skyblock island/server+dimension at absolute world coordinates; inside a dungeon they're
 * keyed by room name and stored in that room's canonical frame ([DungeonRoomAnchor]), so they carry
 * across runs regardless of where the room instance spawned or how it's rotated. Boss rooms and any
 * spot the map scanner can't resolve fall back to absolute coordinates. See [globalKey].
 */
object DungeonWaypoints {

    private const val PLACE_EPSILON = 0.05
    private const val ROUTE_REACH_RADIUS = 1.75
    private val ROUTE_LINE_RGBA = floatArrayOf(1f, 1f, 1f, 0.6f)
    private const val ROUTE_LINE_ARGB = 0x99FFFFFF.toInt()

    // Applied to the NEXT waypoint placed.
    private var editMode = false
    private var fill = false
    private var size = 0.5
    private var distance = 20
    private var useBlockSize = true
    private var through = false
    private var color = 0xFF55FFFF.toInt() // ARGB
    private var type = WaypointType.NONE
    private var timer = TimerType.NONE
    private var offsetX = 0.0
    private var offsetY = 0.0
    private var offsetZ = 0.0

    /** Outline thickness in blocks, applies to non-filled waypoint boxes and the edit-mode cursor box. */
    private var lineWidth = 0.05

    private var placeKey: KeyMapping? = null
    private var lastGlobalDim: String? = null

    private var recordingRouteId: String? = null
    private var recordingNextOrder = 0

    /** routeId -> set of routeOrder values already reached on the current run; cleared by endRoute. Session-only. */
    private val routeReached: MutableMap<String, MutableSet<Int>> = HashMap()

    private class LiveWaypoint(
        @JvmField val box: AABB,
        @JvmField val color: Int,
        @JvmField val filled: Boolean,
        @JvmField val throughWalls: Boolean,
        @JvmField val title: String?,
        @JvmField val routeId: String?,
        @JvmField val routeOrder: Int
    ) {
        @JvmField val center: Vec3 = box.center
        // Built once here instead of every render frame — title text is immutable for this waypoint's lifetime.
        @JvmField val titleComponent: Component? = if (title != null && title.isNotBlank()) Component.literal(title) else null
    }

    private var liveWaypoints: MutableList<LiveWaypoint> = ArrayList()

    // Merged-waypoint geometry is comparatively expensive (a 3D occupancy grid per color/fill group) and
    // only changes when liveWaypoints does, so it's traced once per applyGlobal() call and cached here
    // rather than every render frame.
    private var cachedMergedOccluded: List<MergedGroup> = emptyList()
    private var cachedMergedThrough: List<MergedGroup> = emptyList()

    @JvmStatic
    fun init() {
        val category = fishmod.utils.Keybinds.category()
        placeKey = KeyMappingHelper.registerKeyMapping(
            KeyMapping(
                "FishMod: Dungeon Waypoint place/remove",
                InputConstants.Type.MOUSE,
                GLFW.GLFW_MOUSE_BUTTON_RIGHT,
                category
            )
        )

        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { mc -> onTick(mc) })
        // Occluded (non-through-wall) waypoints + their route lines go through vanilla Gizmos, which
        // depth-test against terrain for real. Through-wall waypoints and the edit cursor stay on the
        // hand-rolled no-depth layers (fills on QUADS, lines on DEBUG_LINES — never mixed).
        RenderingEvents.GIZMO.register { _ -> renderGizmo() }
        RenderingEvents.NO_DEPTH_FILLED.register { ctx, matrices, vc -> render(ctx, matrices, vc) }
        RenderingEvents.NO_DEPTH_LINE.register { _, matrices, vc -> renderLines(matrices, vc) }
    }

    @JvmStatic
    fun toggleEdit() {
        editMode = !editMode
        Misc.addChatMessage(Component.literal("Dungeon Waypoint editing " + (if (editMode) "§aenabled" else "§cdisabled") + "§r!"))
    }

    @JvmStatic
    fun toggleFill() {
        fill = !fill
        Misc.addChatMessage(Component.literal("§7[fm wp] Fill: " + (if (fill) "§afilled" else "§coutline")))
    }

    @JvmStatic
    fun setSize(s: Double) {
        size = s.coerceIn(0.1, 1.0)
        Misc.addChatMessage(Component.literal("§7[fm wp] Size: §f$size"))
    }

    @JvmStatic
    fun setDistance(d: Int) {
        distance = maxOf(1, d)
        Misc.addChatMessage(Component.literal("§7[fm wp] Distance: §f$distance"))
    }

    @JvmStatic
    fun resetSecrets() {
        Misc.addChatMessage(Component.literal("§7[fm wp] Secret tracking reset (no-op in this version)."))
    }

    @JvmStatic
    fun setType(name: String) {
        try {
            type = WaypointType.valueOf(name.uppercase())
            Misc.addChatMessage(Component.literal("§7[fm wp] Type: §f$type"))
        } catch (e: IllegalArgumentException) {
            Misc.addChatMessage(Component.literal("§cUnknown waypoint type: $name"))
        }
    }

    @JvmStatic
    fun setTimer(name: String) {
        try {
            timer = TimerType.valueOf(name.uppercase())
            Misc.addChatMessage(Component.literal("§7[fm wp] Timer: §f$timer"))
        } catch (e: IllegalArgumentException) {
            Misc.addChatMessage(Component.literal("§cUnknown timer type: $name"))
        }
    }

    @JvmStatic
    fun toggleUseBlockSize() {
        useBlockSize = !useBlockSize
        Misc.addChatMessage(Component.literal("§7[fm wp] Use block size: " + (if (useBlockSize) "§aon" else "§coff")))
    }

    @JvmStatic
    fun setOffset(x: Double, y: Double, z: Double) {
        offsetX = x; offsetY = y; offsetZ = z
        Misc.addChatMessage(Component.literal("§7[fm wp] One-shot offset set to §f$x, $y, $z"))
    }

    @JvmStatic
    fun toggleThrough() {
        through = !through
        Misc.addChatMessage(Component.literal("§7[fm wp] Through walls: " + (if (through) "§aon" else "§coff")))
    }

    /** Outline thickness in blocks — only visible when fill is off. */
    @JvmStatic
    fun setLineWidth(w: Double) {
        lineWidth = w.coerceIn(0.01, 0.5)
        Misc.addChatMessage(Component.literal("§7[fm wp] Line size: §f$lineWidth"))
    }

    @JvmStatic
    fun setColor(hex: String?) {
        if (hex == null || hex.length != 8) {
            Misc.addChatMessage(Component.literal("§cColor must be 8 hex chars (RRGGBBAA)."))
            return
        }
        try {
            val rgba = hex.toLong(16)
            val r = ((rgba shr 24) and 0xFF).toInt()
            val g = ((rgba shr 16) and 0xFF).toInt()
            val b = ((rgba shr 8) and 0xFF).toInt()
            val a = (rgba and 0xFF).toInt()
            color = (a shl 24) or (r shl 16) or (g shl 8) or b
            Misc.addChatMessage(Component.literal("§7[fm wp] Color set."))
        } catch (e: NumberFormatException) {
            Misc.addChatMessage(Component.literal("§cInvalid hex color: $hex"))
        }
    }

    @JvmStatic
    fun exportToClipboard() {
        val b64 = DungeonWaypointStore.exportBase64()
        if (b64 == null) {
            Misc.addChatMessage(Component.literal("§cExport failed."))
            return
        }
        val mc = Minecraft.getInstance()
        mc.keyboardHandler.clipboard = b64
        Misc.addChatMessage(Component.literal("§aWaypoint database copied to clipboard."))
    }

    @JvmStatic
    fun importFromClipboard() {
        val mc = Minecraft.getInstance()
        val clip = mc.keyboardHandler.clipboard
        val ok = DungeonWaypointStore.importBase64(clip)
        if (ok) {
            refreshLive()
            Misc.addChatMessage(Component.literal("§aWaypoint database imported from clipboard."))
        } else {
            Misc.addChatMessage(Component.literal("§cImport failed — clipboard doesn't look like a valid waypoint export."))
        }
    }

    @JvmStatic
    fun resetCurrentArea() {
        DungeonWaypointStore.clearRoom(globalKey())
        applyGlobal()
        Misc.addChatMessage(Component.literal("§aCleared waypoints for the current area."))
    }

    @JvmStatic
    fun isEditMode(): Boolean = editMode

    /** Toggles route recording; unnamed auto-numbers as "route1", "route2", etc. */
    @JvmStatic
    fun toggleRoute(name: String?) {
        if (recordingRouteId != null) {
            Misc.addChatMessage(Component.literal("§aFinished recording route '$recordingRouteId' ($recordingNextOrder point(s))."))
            recordingRouteId = null
            return
        }
        recordingRouteId = if (name == null || name.isBlank()) nextAutoRouteName() else name
        recordingNextOrder = 0
        Misc.addChatMessage(Component.literal("§aRecording route '$recordingRouteId' — place waypoints in order, then §f/fm wp route§a to finish."))
    }

    @JvmStatic
    fun isRecordingRoute(): Boolean = recordingRouteId != null

    private fun routeExists(id: String): Boolean {
        for (list in DungeonWaypointStore.allData().values)
            for (w in list) if (id == w.routeId) return true
        return false
    }

    private fun nextAutoRouteName(): String {
        var n = 1
        while (routeExists("route$n")) n++
        return "route$n"
    }

    /** Clears a route's "reached" progress so its waypoints show up again for another run. Null/blank name resets every route. */
    @JvmStatic
    fun endRoute(name: String?) {
        if (name == null || name.isBlank()) {
            routeReached.clear()
            Misc.addChatMessage(Component.literal("§aAll routes reset — waypoints visible again."))
        } else {
            routeReached.remove(name)
            Misc.addChatMessage(Component.literal("§aRoute '$name' reset — waypoints visible again."))
        }
    }

    @JvmStatic
    fun deleteRoute(name: String?) {
        if (name == null || name.isBlank()) {
            Misc.addChatMessage(Component.literal("§cGive a route name: /fm wp route delete <name>"))
            return
        }
        val n = DungeonWaypointStore.removeRoute(name)
        routeReached.remove(name)
        refreshLive()
        Misc.addChatMessage(
            Component.literal(
                if (n > 0) "§aDeleted route '$name' ($n point(s))."
                else "§cNo route named '$name' found."
            )
        )
    }

    private const val ROOM_KEY_PREFIX = "dungeon:room:"
    private fun isRoomKey(key: String) = key.startsWith(ROOM_KEY_PREFIX)

    /**
     * Inside a dungeon, key by room name and store coordinates in the room's canonical frame (see
     * [DungeonRoomAnchor]) so a waypoint carries across runs. Elsewhere, key by Skyblock island/zone
     * (or server+dimension) at absolute coordinates.
     */
    private fun globalKey(): String {
        if (Location.inDungeon()) {
            val a = DungeonRoomAnchor.current()
            if (a != null) return ROOM_KEY_PREFIX + a.name
        }
        if (Location.inSkyblock()) {
            return "global:skyblock:" + Location.getCurrentLocation().name
        }

        val mc = Minecraft.getInstance()
        val server: String = try {
            val si = mc.currentServer
            if (si != null && si.ip != null) si.ip else "singleplayer"
        } catch (e: Exception) {
            "singleplayer"
        }
        val dimId = if (mc.level != null) mc.level!!.dimension().identifier().toString() else "unknown"
        return "global:$server:$dimId"
    }

    @JvmStatic
    fun currentGlobalKeyForGui(): String = globalKey()

    private fun onTick(mc: Minecraft) {
        if (mc.player == null || mc.level == null) {
            liveWaypoints.clear()
            lastGlobalDim = null
            return
        }

        val key = globalKey()
        if (key != lastGlobalDim) {
            lastGlobalDim = key
            applyGlobal()
        } else if (isRoomKey(key)) {
            // The room anchor (rotation/clay) can shift for a tick or two as the map scanner refines
            // the room, so re-project room-anchored waypoints every tick while inside one.
            applyGlobal()
        }

        advanceRouteProgress(mc)

        var fired = false
        while (placeKey != null && placeKey!!.consumeClick()) fired = true
        if (fired && editMode && mc.screen == null) {
            handlePlace(mc)
        }
    }

    /** Groups live waypoints by route, sorted in placement order. Pure/read-only — safe to call from render. */
    private fun groupRoutes(): Map<String, List<LiveWaypoint>> {
        val routeGroups = LinkedHashMap<String, MutableList<LiveWaypoint>>()
        for (w in liveWaypoints) {
            if (w.routeId != null) routeGroups.getOrPut(w.routeId) { ArrayList() }.add(w)
        }
        for (group in routeGroups.values) {
            group.sortBy { it.routeOrder }
        }
        return routeGroups
    }

    /** Marks the next unreached point of each visible route as reached once the player gets close enough. */
    private fun advanceRouteProgress(mc: Minecraft) {
        val player = mc.player ?: return
        val playerPos = player.position()
        for ((key, value) in groupRoutes()) {
            val reached = routeReached.getOrPut(key) { HashSet() }
            for (w in value) {
                if (reached.contains(w.routeOrder)) continue
                if (playerPos.distanceTo(w.center) < ROUTE_REACH_RADIUS) reached.add(w.routeOrder)
                break // only the next point in sequence counts
            }
        }
    }

    /** Result of an aim raycast: the point to center a waypoint on, and the aimed block's actual shape (null on a miss). */
    private class AimResult(@JvmField val point: Vec3, @JvmField val blockBox: AABB?)

    /**
     * Raycasts along the player's look vector. When it hits a block, [AimResult.blockBox] carries that
     * block's real collision-shape bounds in world space — e.g. a slab reports a half-height box at the
     * correct top/bottom half — so callers using block-size placement match the block instead of always
     * assuming a full 1x1x1 cube.
     */
    private fun aimPoint(mc: Minecraft): AimResult {
        val p = mc.player!!
        val delta = mc.deltaTracker.getGameTimeDeltaPartialTick(false)
        val eye = p.getEyePosition(delta)
        val look = p.getViewVector(delta)
        val end = eye.add(look.scale(distance.toDouble()))

        val hit: BlockHitResult? = mc.level!!.clip(
            ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p)
        )
        if (hit != null && hit.type != HitResult.Type.MISS) {
            val bp: BlockPos = hit.blockPos
            val state = mc.level!!.getBlockState(bp)
            val shape = state.getShape(mc.level!!, bp)
            val bounds = if (shape.isEmpty) AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0) else shape.bounds()
            val worldBox = AABB(
                bp.x + bounds.minX, bp.y + bounds.minY, bp.z + bounds.minZ,
                bp.x + bounds.maxX, bp.y + bounds.maxY, bp.z + bounds.maxZ
            )
            return AimResult(worldBox.center, worldBox)
        }
        return AimResult(end, null)
    }

    private fun handlePlace(mc: Minecraft) {
        handlePlaceGlobal(mc)
    }

    /** If a route is currently being recorded, tags [w] with it and advances the recording order. */
    private fun tagRoute(w: StoredWaypoint) {
        if (recordingRouteId == null) return
        w.routeId = recordingRouteId
        w.routeOrder = recordingNextOrder++
    }

    private fun handlePlaceGlobal(mc: Minecraft) {
        val aimResult = aimPoint(mc)
        val aim = aimResult.point
        var px = aim.x + offsetX
        var py = aim.y + offsetY
        var pz = aim.z + offsetZ
        offsetX = 0.0; offsetY = 0.0; offsetZ = 0.0 // one-shot

        // Block-size placement matches the aimed block's real shape — a slab gets a half-height box at
        // the correct top/bottom half instead of always a full 1x1x1 cube.
        val halfX: Double; val halfY: Double; val halfZ: Double
        if (useBlockSize && aimResult.blockBox != null) {
            halfX = (aimResult.blockBox.maxX - aimResult.blockBox.minX) / 2.0
            halfY = (aimResult.blockBox.maxY - aimResult.blockBox.minY) / 2.0
            halfZ = (aimResult.blockBox.maxZ - aimResult.blockBox.minZ) / 2.0
        } else {
            val half = if (useBlockSize) 0.5 else size / 2.0
            halfX = half; halfY = half; halfZ = half
        }

        val key = globalKey()

        // Inside a resolved room, persist as the block's canonical-frame coords so the waypoint carries
        // across runs. X/Z are block-granular on purpose: rotating a fractional centre lands a block off
        // on 90° room rotations. Y isn't touched by room rotation, so its fractional part (e.g. a slab's
        // half-height centre) is preserved as-is rather than snapped to the full-block centre.
        if (isRoomKey(key)) {
            val a = DungeonRoomAnchor.current()
            if (a != null) {
                val local = DungeonRoomAnchor.toLocal(
                    a, BlockPos(Math.floor(px).toInt(), Math.floor(py).toInt(), Math.floor(pz).toInt())
                )
                val fracY = py - Math.floor(py)
                px = local.x + 0.5; py = local.y + fracY; pz = local.z + 0.5
            }
        }

        if (mc.player!!.isShiftKeyDown) {
            mc.setScreen(DungeonWaypointTitleScreen { title ->
                val w = StoredWaypoint(
                    px, py, pz, halfX, halfY, halfZ,
                    color, fill, through, title,
                    if (type == WaypointType.NONE) null else type.name,
                    if (timer == TimerType.NONE) null else timer.name
                )
                tagRoute(w)
                DungeonWaypointStore.add(key, w)
                applyGlobal()
            })
            return
        }

        val removed = DungeonWaypointStore.removeNear(key, px, py, pz, PLACE_EPSILON)
        if (!removed) {
            val w = StoredWaypoint(
                px, py, pz, halfX, halfY, halfZ,
                color, fill, through, null,
                if (type == WaypointType.NONE) null else type.name,
                if (timer == TimerType.NONE) null else timer.name
            )
            tagRoute(w)
            DungeonWaypointStore.add(key, w)
        }
        applyGlobal()
    }

    private fun applyGlobal() {
        val key = globalKey()
        val anchor = if (isRoomKey(key)) DungeonRoomAnchor.current() else null
        // Room key but the room isn't currently resolvable (doorway, mid-rescan): draw nothing rather
        // than treat the stored room-local coords as absolute.
        if (isRoomKey(key) && anchor == null) {
            liveWaypoints = ArrayList()
            cachedMergedOccluded = emptyList()
            cachedMergedThrough = emptyList()
            return
        }

        val result = ArrayList<LiveWaypoint>()
        for (w in DungeonWaypointStore.get(key)) {
            val c = if (anchor != null) {
                val wb = DungeonRoomAnchor.toWorld(
                    anchor, BlockPos(Math.floor(w.x).toInt(), Math.floor(w.y).toInt(), Math.floor(w.z).toInt())
                )
                // Y isn't touched by room rotation — reapply the stored fractional Y (e.g. a slab's
                // half-height centre) instead of snapping back to the full-block centre.
                val fracY = w.y - Math.floor(w.y)
                Vec3(wb.x + 0.5, wb.y + fracY, wb.z + 0.5)
            } else Vec3(w.x, w.y, w.z)
            val box = AABB(
                c.x - w.halfX, c.y - w.halfY, c.z - w.halfZ,
                c.x + w.halfX, c.y + w.halfY, c.z + w.halfZ
            )
            result.add(LiveWaypoint(box, w.color, w.filled, w.throughWalls, w.title, w.routeId, w.routeOrder))
        }
        liveWaypoints = result
        cachedMergedOccluded = buildMergedGroups(throughWalls = false)
        cachedMergedThrough = buildMergedGroups(throughWalls = true)
    }

    /** Called by the waypoint list GUI after it edits/deletes entries, to refresh what's currently rendering. */
    @JvmStatic
    fun refreshLive() {
        applyGlobal()
    }

    private fun reached(w: LiveWaypoint): Boolean =
        w.routeId != null && routeReached.getOrDefault(w.routeId, emptySet()).contains(w.routeOrder)

    private const val MERGE_EPSILON = 1e-4

    private fun compressAxis(values: List<Double>): List<Double> {
        val sorted = values.sorted()
        val result = ArrayList<Double>()
        for (v in sorted) {
            if (result.isEmpty() || v - result[result.size - 1] > MERGE_EPSILON) result.add(v)
        }
        return result
    }

    /** Greedy rectangle cover of the `true` cells in a 2D boolean grid — `n1`x`n2` cells, indices exclusive on the high end. */
    private fun greedyRects(occ: Array<BooleanArray>, n1: Int, n2: Int): List<IntArray> {
        val used = Array(n1) { BooleanArray(n2) }
        val result = ArrayList<IntArray>()
        for (a in 0 until n1) {
            for (b in 0 until n2) {
                if (occ[a][b] && !used[a][b]) {
                    var b2 = b
                    while (b2 + 1 < n2 && occ[a][b2 + 1] && !used[a][b2 + 1]) b2++
                    var a2 = a
                    outer@ while (a2 + 1 < n1) {
                        for (bb in b..b2) if (!occ[a2 + 1][bb] || used[a2 + 1][bb]) break@outer
                        a2++
                    }
                    for (aa in a..a2) for (bb in b..b2) used[aa][bb] = true
                    result.add(intArrayOf(a, b, a2 + 1, b2 + 1))
                }
            }
        }
        return result
    }

    private class MergedGroup(
        @JvmField val color: Int,
        @JvmField val filled: Boolean,
        @JvmField val quads: List<Array<Vec3>>, // exposed faces of the merged solid, for filled draws
        @JvmField val edges: List<DoubleArray> // x1,y1,z1,x2,y2,z2 — the solid's silhouette edges, for outline draws
    )

    /**
     * Traces the true exterior surface of the union of [boxes] via a coordinate-compressed 3D occupancy
     * grid: [quads] are the exposed faces (an internal face between two touching boxes is never emitted,
     * so a straight join has no seam), and [edges] are the silhouette wireframe (an edge is drawn only
     * where the 4 cells around it aren't all the same — a flat face's rim, or a genuine corner/bend on
     * any axis, but never a cut line inside a flat run). This is what lets adjacent waypoints merge into
     * one shape around a turn — including a vertical one — instead of two boxes with an internal seam.
     */
    private fun traceSurface(boxes: List<AABB>): Pair<List<Array<Vec3>>, List<DoubleArray>>? {
        val xs = compressAxis(boxes.flatMap { listOf(it.minX, it.maxX) })
        val ys = compressAxis(boxes.flatMap { listOf(it.minY, it.maxY) })
        val zs = compressAxis(boxes.flatMap { listOf(it.minZ, it.maxZ) })
        val nx = xs.size - 1
        val ny = ys.size - 1
        val nz = zs.size - 1
        if (nx <= 0 || ny <= 0 || nz <= 0) return null

        val occ = Array(nx) { i ->
            Array(ny) { j ->
                BooleanArray(nz) { k ->
                    val cx = (xs[i] + xs[i + 1]) / 2.0
                    val cy = (ys[j] + ys[j + 1]) / 2.0
                    val cz = (zs[k] + zs[k + 1]) / 2.0
                    boxes.any {
                        cx > it.minX + MERGE_EPSILON && cx < it.maxX - MERGE_EPSILON &&
                            cy > it.minY + MERGE_EPSILON && cy < it.maxY - MERGE_EPSILON &&
                            cz > it.minZ + MERGE_EPSILON && cz < it.maxZ - MERGE_EPSILON
                    }
                }
            }
        }
        fun occAt(i: Int, j: Int, k: Int) = i in 0 until nx && j in 0 until ny && k in 0 until nz && occ[i][j][k]

        val quads = ArrayList<Array<Vec3>>()
        // +Y / -Y faces: slice over (X,Z) per Y layer.
        for (j in 0 until ny) {
            val top = Array(nx) { i -> BooleanArray(nz) { k -> occ[i][j][k] && !occAt(i, j + 1, k) } }
            for (r in greedyRects(top, nx, nz)) {
                val x1 = xs[r[0]]; val z1 = zs[r[1]]; val x2 = xs[r[2]]; val z2 = zs[r[3]]; val y = ys[j + 1]
                quads.add(arrayOf(Vec3(x1, y, z1), Vec3(x2, y, z1), Vec3(x2, y, z2), Vec3(x1, y, z2)))
            }
            val bottom = Array(nx) { i -> BooleanArray(nz) { k -> occ[i][j][k] && !occAt(i, j - 1, k) } }
            for (r in greedyRects(bottom, nx, nz)) {
                val x1 = xs[r[0]]; val z1 = zs[r[1]]; val x2 = xs[r[2]]; val z2 = zs[r[3]]; val y = ys[j]
                quads.add(arrayOf(Vec3(x1, y, z1), Vec3(x1, y, z2), Vec3(x2, y, z2), Vec3(x2, y, z1)))
            }
        }
        // +X / -X faces: slice over (Y,Z) per X layer.
        for (i in 0 until nx) {
            val pos = Array(ny) { j -> BooleanArray(nz) { k -> occ[i][j][k] && !occAt(i + 1, j, k) } }
            for (r in greedyRects(pos, ny, nz)) {
                val y1 = ys[r[0]]; val z1 = zs[r[1]]; val y2 = ys[r[2]]; val z2 = zs[r[3]]; val x = xs[i + 1]
                quads.add(arrayOf(Vec3(x, y1, z1), Vec3(x, y1, z2), Vec3(x, y2, z2), Vec3(x, y2, z1)))
            }
            val neg = Array(ny) { j -> BooleanArray(nz) { k -> occ[i][j][k] && !occAt(i - 1, j, k) } }
            for (r in greedyRects(neg, ny, nz)) {
                val y1 = ys[r[0]]; val z1 = zs[r[1]]; val y2 = ys[r[2]]; val z2 = zs[r[3]]; val x = xs[i]
                quads.add(arrayOf(Vec3(x, y1, z1), Vec3(x, y2, z1), Vec3(x, y2, z2), Vec3(x, y1, z2)))
            }
        }
        // +Z / -Z faces: slice over (X,Y) per Z layer.
        for (k in 0 until nz) {
            val pos = Array(nx) { i -> BooleanArray(ny) { j -> occ[i][j][k] && !occAt(i, j, k + 1) } }
            for (r in greedyRects(pos, nx, ny)) {
                val x1 = xs[r[0]]; val y1 = ys[r[1]]; val x2 = xs[r[2]]; val y2 = ys[r[3]]; val z = zs[k + 1]
                quads.add(arrayOf(Vec3(x1, y1, z), Vec3(x2, y1, z), Vec3(x2, y2, z), Vec3(x1, y2, z)))
            }
            val neg = Array(nx) { i -> BooleanArray(ny) { j -> occ[i][j][k] && !occAt(i, j, k - 1) } }
            for (r in greedyRects(neg, nx, ny)) {
                val x1 = xs[r[0]]; val y1 = ys[r[1]]; val x2 = xs[r[2]]; val y2 = ys[r[3]]; val z = zs[k]
                quads.add(arrayOf(Vec3(x1, y1, z), Vec3(x1, y2, z), Vec3(x2, y2, z), Vec3(x2, y1, z)))
            }
        }

        // Silhouette edges: standard boundary-tracing parity rule — an edge is on the surface exactly
        // when an ODD number of the (up to) 4 cells around it are occupied. An even count (0, 2 flat-
        // matching, or 4) means either nothing here or a flat run continuing straight through with no
        // real corner, so no edge; odd means a genuine face rim or a bend. (Using "not all 4 equal"
        // instead — as if a flat run's own two matching sides made it a boundary — drew a seam at every
        // straight join, which is why nothing merged cleanly on any axis.)
        val edges = ArrayList<DoubleArray>()
        for (i in 0..nx) for (j in 0..ny) {
            var k = 0
            while (k < nz) {
                fun boundary(kk: Int) = occAt(i - 1, j - 1, kk) xor occAt(i, j - 1, kk) xor occAt(i - 1, j, kk) xor occAt(i, j, kk)
                if (boundary(k)) {
                    var k2 = k
                    while (k2 + 1 < nz && boundary(k2 + 1)) k2++
                    edges.add(doubleArrayOf(xs[i], ys[j], zs[k], xs[i], ys[j], zs[k2 + 1]))
                    k = k2 + 1
                } else k++
            }
        }
        for (j in 0..ny) for (k in 0..nz) {
            var i = 0
            while (i < nx) {
                fun boundary(ii: Int) = occAt(ii, j - 1, k - 1) xor occAt(ii, j, k - 1) xor occAt(ii, j - 1, k) xor occAt(ii, j, k)
                if (boundary(i)) {
                    var i2 = i
                    while (i2 + 1 < nx && boundary(i2 + 1)) i2++
                    edges.add(doubleArrayOf(xs[i], ys[j], zs[k], xs[i2 + 1], ys[j], zs[k]))
                    i = i2 + 1
                } else i++
            }
        }
        for (i in 0..nx) for (k in 0..nz) {
            var j = 0
            while (j < ny) {
                fun boundary(jj: Int) = occAt(i - 1, jj, k - 1) xor occAt(i, jj, k - 1) xor occAt(i - 1, jj, k) xor occAt(i, jj, k)
                if (boundary(j)) {
                    var j2 = j
                    while (j2 + 1 < ny && boundary(j2 + 1)) j2++
                    edges.add(doubleArrayOf(xs[i], ys[j], zs[k], xs[i], ys[j2 + 1], zs[k]))
                    j = j2 + 1
                } else j++
            }
        }

        return Pair(quads, edges)
    }

    /**
     * Waypoints without a title or route are plain area markers — placing several side by side, even
     * around a bend (including a vertical one), is meant to mark one contiguous region rather than a row
     * of separate boxes. Groups those (matching [throughWalls]) by color/fill and traces each group's true
     * surface via [traceSurface], so a bend merges into one shape instead of leaving an internal seam at
     * the join. Titled/routed waypoints (each needs its own box for its label/route position) aren't merged.
     */
    private fun buildMergedGroups(throughWalls: Boolean): List<MergedGroup> {
        val candidates = liveWaypoints.filter { it.throughWalls == throughWalls && it.routeId == null && it.titleComponent == null }
        val result = ArrayList<MergedGroup>()
        for ((key, group) in candidates.groupBy { Pair(it.color, it.filled) }) {
            val (quads, edges) = traceSurface(group.map { it.box }) ?: continue
            result.add(MergedGroup(key.first, key.second, quads, edges))
        }
        return result
    }

    private fun buildSingles(throughWalls: Boolean): List<LiveWaypoint> =
        liveWaypoints.filter { it.throughWalls == throughWalls && !reached(it) && (it.routeId != null || it.titleComponent != null) }

    private fun drawMergedFillGizmo(g: MergedGroup) {
        for (q in g.quads) RenderUtils.gizmoQuad(q, g.color, 0)
    }

    private fun drawMergedFillThrough(matrices: PoseStack, vc: VertexConsumer, g: MergedGroup) {
        val rgba = RenderUtils.toFloats(g.color)
        if (rgba[3] == 0f) return
        for (q in g.quads) RenderUtils.renderFilledQuad(matrices, vc, q, rgba)
    }

    private fun drawMergedOutlineGizmo(g: MergedGroup) {
        if ((g.color ushr 24) == 0) return
        val hw = lineWidth / 2.0
        for (e in g.edges) RenderUtils.gizmoThickEdge(Vec3(e[0], e[1], e[2]), Vec3(e[3], e[4], e[5]), hw, g.color)
    }

    private fun drawMergedOutlineThrough(matrices: PoseStack, vc: VertexConsumer, g: MergedGroup) {
        val rgba = RenderUtils.toFloats(g.color)
        if (rgba[3] == 0f) return
        val hw = lineWidth / 2.0
        for (e in g.edges) RenderUtils.renderThickEdge(matrices, vc, Vec3(e[0], e[1], e[2]), Vec3(e[3], e[4], e[5]), hw, rgba)
    }

    /** Occluded pass: non-through-wall waypoints, titles and route lines as vanilla gizmos. */
    private fun renderGizmo() {
        if (!FishSettings.dungeonWaypointsEnabled) return
        for (g in cachedMergedOccluded) {
            if (g.filled) drawMergedFillGizmo(g) else drawMergedOutlineGizmo(g)
        }
        for (w in buildSingles(throughWalls = false)) {
            if (w.filled) RenderUtils.gizmoBox(w.box, w.color, 0)
            else RenderUtils.gizmoThickOutline(w.box, w.color, lineWidth)
        }
        for (w in liveWaypoints) {
            if (w.throughWalls || reached(w) || w.titleComponent == null) continue
            RenderUtils.gizmoText(w.titleComponent, Vec3(w.center.x, w.box.maxY + 0.4, w.center.z), 1.0f, -0x1)
        }
        for ((_, value) in groupRoutes()) {
            var prev: LiveWaypoint? = null
            for (w in value) {
                if (reached(w)) continue
                val p = prev
                if (p != null && !p.throughWalls && !w.throughWalls) RenderUtils.gizmoLine(p.center, w.center, ROUTE_LINE_ARGB)
                prev = w
            }
        }
    }

    /** Through-walls pass: through-wall waypoints (+ their titles) and the edit cursor, on the no-depth layers. */
    private fun render(ctx: LevelRenderContext, matrices: PoseStack, vc: VertexConsumer) {
        val mc = Minecraft.getInstance()
        if (FishSettings.dungeonWaypointsEnabled) {
            for (g in cachedMergedThrough) {
                if (g.filled) drawMergedFillThrough(matrices, vc, g) else drawMergedOutlineThrough(matrices, vc, g)
            }
            for (w in buildSingles(throughWalls = true)) {
                val rgba = RenderUtils.toFloats(w.color)
                // Outlines are thin filled boxes, not GL_LINES, to keep them on the same triangle-strip
                // layer as fills — mixing topologies on one layer caused the earlier "bowtie" corruption.
                if (w.filled) RenderUtils.renderFilled(matrices, vc, w.box, rgba)
                else RenderUtils.renderThickOutline(matrices, vc, w.box, rgba, lineWidth)
            }
            for (w in liveWaypoints) {
                if (!w.throughWalls || reached(w) || w.titleComponent == null) continue
                RenderUtils.renderText(ctx, matrices, w.titleComponent, w.center.x, w.box.maxY + 0.4, w.center.z, 1.0f)
            }
        }

        if (editMode) {
            if (mc.player != null && mc.level != null) {
                val aimResult = aimPoint(mc)
                val aim = aimResult.point
                val box = if (useBlockSize && aimResult.blockBox != null) {
                    aimResult.blockBox
                } else {
                    val half = if (useBlockSize) 0.5 else size / 2.0
                    AABB(aim.x - half, aim.y - half, aim.z - half, aim.x + half, aim.y + half, aim.z + half)
                }
                RenderUtils.renderThickOutline(matrices, vc, box, floatArrayOf(1f, 1f, 1f, 0.9f), lineWidth)
            }
        }
    }

    /** Route connector lines for through-wall routes only — gizmo routes are drawn in [renderGizmo]. */
    private fun renderLines(matrices: PoseStack, vc: VertexConsumer) {
        if (!FishSettings.dungeonWaypointsEnabled) return
        for ((key, value) in groupRoutes()) {
            val reached = routeReached.getOrDefault(key, emptySet())
            var prev: LiveWaypoint? = null
            for (w in value) {
                if (reached.contains(w.routeOrder)) continue
                val p = prev
                if (p != null && (p.throughWalls || w.throughWalls)) RenderUtils.renderLine(matrices, vc, p.center, w.center, ROUTE_LINE_RGBA)
                prev = w
            }
        }
    }

    // Overlay text only changes when a /fm wp setting command runs, not every frame — cache the built
    // Component and its measured width, keyed on the settings that feed into the string.
    private var cachedOverlayKey: String? = null
    private var cachedOverlayLine: Component? = null
    private var cachedOverlayWidth: Int = 0

    /** Small on-screen settings readout while edit mode is on, drawn near screen center via HudRenderCallback. */
    @JvmStatic
    fun renderOverlay(ctx: GuiGraphicsExtractor) {
        if (!editMode) return
        val mc = Minecraft.getInstance()
        if (mc.font == null) return

        val key = "$fill|$size|$distance|$useBlockSize|$through|$type|$timer|$lineWidth|$recordingRouteId"
        if (key != cachedOverlayKey) {
            cachedOverlayKey = key
            val line = Component.literal(
                "§b[fm wp] §7fill:" + (if (fill) "§ay" else "§cn") + " §7size:§f" + size
                    + " §7dist:§f" + distance + " §7blockSize:" + (if (useBlockSize) "§ay" else "§cn")
                    + " §7through:" + (if (through) "§ay" else "§cn") + " §7type:§f" + type + " §7timer:§f" + timer
                    + (if (!fill) " §7line:§f$lineWidth" else "")
                    + (if (recordingRouteId != null) " §d🔗route:$recordingRouteId" else "")
            )
            cachedOverlayLine = line
            cachedOverlayWidth = mc.font!!.width(line)
        }

        val cx = ctx.guiWidth() / 2
        val y = ctx.guiHeight() / 2 + 30
        ctx.text(mc.font, cachedOverlayLine!!, cx - cachedOverlayWidth / 2, y, 0xFFFFFFFF.toInt(), true)
    }
}
