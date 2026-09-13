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
    }

    /** Called by the waypoint list GUI after it edits/deletes entries, to refresh what's currently rendering. */
    @JvmStatic
    fun refreshLive() {
        applyGlobal()
    }

    private fun reached(w: LiveWaypoint): Boolean =
        w.routeId != null && routeReached.getOrDefault(w.routeId, emptySet()).contains(w.routeOrder)

    private const val MERGE_EPSILON = 1e-4

    private fun near(a: Double, b: Double) = Math.abs(a - b) < MERGE_EPSILON

    /** Combines [a] and [b] into their union box if they're equal-footprint boxes touching face-to-face on exactly one axis. Null if they don't tile cleanly. */
    private fun tryMergeBoxes(a: AABB, b: AABB): AABB? {
        if (near(a.minY, b.minY) && near(a.maxY, b.maxY) && near(a.minZ, b.minZ) && near(a.maxZ, b.maxZ)) {
            if (near(a.maxX, b.minX)) return AABB(a.minX, a.minY, a.minZ, b.maxX, a.maxY, a.maxZ)
            if (near(b.maxX, a.minX)) return AABB(b.minX, a.minY, a.minZ, a.maxX, a.maxY, a.maxZ)
        }
        if (near(a.minX, b.minX) && near(a.maxX, b.maxX) && near(a.minZ, b.minZ) && near(a.maxZ, b.maxZ)) {
            if (near(a.maxY, b.minY)) return AABB(a.minX, a.minY, a.minZ, a.maxX, b.maxY, a.maxZ)
            if (near(b.maxY, a.minY)) return AABB(a.minX, b.minY, a.minZ, a.maxX, a.maxY, a.maxZ)
        }
        if (near(a.minX, b.minX) && near(a.maxX, b.maxX) && near(a.minY, b.minY) && near(a.maxY, b.maxY)) {
            if (near(a.maxZ, b.minZ)) return AABB(a.minX, a.minY, a.minZ, a.maxX, a.maxY, b.maxZ)
            if (near(b.maxZ, a.minZ)) return AABB(a.minX, a.minY, b.minZ, a.maxX, a.maxY, a.maxZ)
        }
        return null
    }

    /** Greedily merges every pair of touching, equal-footprint boxes in [boxes] until no more merges apply. */
    private fun mergeBoxGroup(boxes: List<AABB>): List<AABB> {
        val result = boxes.toMutableList()
        var merged = true
        while (merged) {
            merged = false
            outer@ for (i in result.indices) {
                for (j in i + 1 until result.size) {
                    val combined = tryMergeBoxes(result[i], result[j])
                    if (combined != null) {
                        result[i] = combined
                        result.removeAt(j)
                        merged = true
                        break@outer
                    }
                }
            }
        }
        return result
    }

    /**
     * Waypoints without a title or route are plain area markers — placing several side by side is meant
     * to mark one contiguous region, not a row of separate boxes. Merges each color/fill group of those
     * (matching [throughWalls]) into the fewest boxes that cover the same space, so adjacent waypoints
     * draw as a single solid box instead of two boxes with a seam at the shared face. Titled and routed
     * waypoints are left untouched (each needs its own box for its label/route position) and returned as-is.
     */
    private fun mergedBoxesFor(throughWalls: Boolean): List<LiveWaypoint> {
        val candidates = ArrayList<LiveWaypoint>()
        val fixed = ArrayList<LiveWaypoint>()
        for (w in liveWaypoints) {
            if (w.throughWalls != throughWalls || reached(w)) continue
            if (w.routeId == null && w.titleComponent == null) candidates.add(w) else fixed.add(w)
        }
        val merged = ArrayList<LiveWaypoint>()
        for ((_, group) in candidates.groupBy { Pair(it.color, it.filled) }) {
            val proto = group[0]
            for (box in mergeBoxGroup(group.map { it.box })) {
                merged.add(LiveWaypoint(box, proto.color, proto.filled, proto.throughWalls, null, null, 0))
            }
        }
        merged.addAll(fixed)
        return merged
    }

    /** Occluded pass: non-through-wall waypoints, titles and route lines as vanilla gizmos. */
    private fun renderGizmo() {
        for (w in mergedBoxesFor(throughWalls = false)) {
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
        for (w in mergedBoxesFor(throughWalls = true)) {
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
