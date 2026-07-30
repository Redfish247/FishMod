package fishmod.features.dungeon

import com.mojang.blaze3d.platform.InputConstants
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import fishmod.utils.Constants
import fishmod.utils.Location
import fishmod.utils.Misc
import fishmod.utils.dungeon.map.GridPos
import fishmod.utils.dungeon.map.MapReader
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
 * /fmwp — an OdinLegacy-style dungeon waypoint editor (github.com/odtheking/OdinLegacy,
 * DungeonWaypoints.kt/DungeonWaypointCommand.kt/DungeonWaypointConfig.kt). In a calibrated Hypixel
 * dungeon, waypoints are stored per grid tile (see [tileKey]), keyed off the fixed 32-block
 * world grid via [MapReader.worldToGridPos] — no room-shape/door detection is involved, so a
 * waypoint only replays automatically when the same tile position recurs (no rotation normalization).
 * Outside a calibrated dungeon (any other server/world), waypoints instead fall back to a freeform
 * mode keyed by island/server+dimension and stored at absolute world coordinates — see [globalKey].
 */
object DungeonWaypoints {

    private const val PLACE_EPSILON = 0.05
    private const val ROUTE_REACH_RADIUS = 1.75
    private val ROUTE_LINE_RGBA = floatArrayOf(1f, 1f, 1f, 0.6f)

    // --- Edit-mode placement settings (apply to the NEXT waypoint placed) ---
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
    private var lastTile: GridPos? = null
    private var lastGlobalDim: String? = null

    // --- Route recording (see toggleRoute) ---
    private var recordingRouteId: String? = null
    private var recordingNextOrder = 0

    /** routeId -> set of routeOrder values already reached on the current run; cleared by endRoute. Session-only. */
    private val routeReached: MutableMap<String, MutableSet<Int>> = HashMap()

    /** A waypoint applied to the currently-live room, in real world coordinates. */
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
        RenderingEvents.NO_DEPTH_FILLED.register { ctx, matrices, vc -> render(ctx, matrices, vc) }
        // All GL_LINES geometry (route connector lines, edit-mode cursor box) goes through a
        // dedicated lines layer, never the box-fill layer's triangle strip — see renderLines().
        RenderingEvents.NO_DEPTH_LINE.register { _, matrices, vc -> renderLines(matrices, vc) }
    }

    /**
     * Whether [target] is actually visible from the player's eyes right now — used for
     * non-through-walls waypoints. Everything here always renders on a no-depth (through-walls)
     * layer regardless of the per-waypoint flag: registering on this mod's depth-tested layer
     * (FILLED_BLOCK/LINE) turned out to be unproven plumbing nothing else in the codebase actually
     * exercises, and it silently broke room waypoints outright rather than just occluding them. A
     * simple line-of-sight raycast — the same ClipContext machinery [aimPoint] already uses —
     * gets the same "hidden behind a wall" result without depending on that.
     */
    private fun hasLineOfSight(mc: Minecraft, target: Vec3): Boolean {
        val p = mc.player ?: return true
        if (mc.level == null) return true
        val delta = mc.deltaTracker.getGameTimeDeltaPartialTick(false)
        val eye = p.getEyePosition(delta)
        val hit: BlockHitResult? = mc.level!!.clip(
            ClipContext(eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)
        )
        return hit == null || hit.type == HitResult.Type.MISS
    }

    // ================= Commands =================

    @JvmStatic
    fun toggleEdit() {
        editMode = !editMode
        Misc.addChatMessage(Component.literal("Dungeon Waypoint editing " + (if (editMode) "§aenabled" else "§cdisabled") + "§r!"))
    }

    @JvmStatic
    fun toggleFill() {
        fill = !fill
        Misc.addChatMessage(Component.literal("§7[fmwp] Fill: " + (if (fill) "§afilled" else "§coutline")))
    }

    @JvmStatic
    fun setSize(s: Double) {
        size = s.coerceIn(0.1, 1.0)
        Misc.addChatMessage(Component.literal("§7[fmwp] Size: §f$size"))
    }

    @JvmStatic
    fun setDistance(d: Int) {
        distance = maxOf(1, d)
        Misc.addChatMessage(Component.literal("§7[fmwp] Distance: §f$distance"))
    }

    @JvmStatic
    fun resetSecrets() {
        Misc.addChatMessage(Component.literal("§7[fmwp] Secret tracking reset (no-op in this version)."))
    }

    @JvmStatic
    fun setType(name: String) {
        try {
            type = WaypointType.valueOf(name.uppercase())
            Misc.addChatMessage(Component.literal("§7[fmwp] Type: §f$type"))
        } catch (e: IllegalArgumentException) {
            Misc.addChatMessage(Component.literal("§cUnknown waypoint type: $name"))
        }
    }

    @JvmStatic
    fun setTimer(name: String) {
        try {
            timer = TimerType.valueOf(name.uppercase())
            Misc.addChatMessage(Component.literal("§7[fmwp] Timer: §f$timer"))
        } catch (e: IllegalArgumentException) {
            Misc.addChatMessage(Component.literal("§cUnknown timer type: $name"))
        }
    }

    @JvmStatic
    fun toggleUseBlockSize() {
        useBlockSize = !useBlockSize
        Misc.addChatMessage(Component.literal("§7[fmwp] Use block size: " + (if (useBlockSize) "§aon" else "§coff")))
    }

    @JvmStatic
    fun setOffset(x: Double, y: Double, z: Double) {
        offsetX = x; offsetY = y; offsetZ = z
        Misc.addChatMessage(Component.literal("§7[fmwp] One-shot offset set to §f$x, $y, $z"))
    }

    @JvmStatic
    fun toggleThrough() {
        through = !through
        Misc.addChatMessage(Component.literal("§7[fmwp] Through walls: " + (if (through) "§aon" else "§coff")))
    }

    /** Outline thickness in blocks — only visible when fill is off. */
    @JvmStatic
    fun setLineWidth(w: Double) {
        lineWidth = w.coerceIn(0.01, 0.5)
        Misc.addChatMessage(Component.literal("§7[fmwp] Line size: §f$lineWidth"))
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
            Misc.addChatMessage(Component.literal("§7[fmwp] Color set."))
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
            lastTile = null // force re-apply
            Misc.addChatMessage(Component.literal("§aWaypoint database imported from clipboard."))
        } else {
            Misc.addChatMessage(Component.literal("§cImport failed — clipboard doesn't look like a valid waypoint export."))
        }
    }

    @JvmStatic
    fun resetCurrentRoom() {
        val tile = currentTile()
        if (tile == null) {
            Misc.addChatMessage(Component.literal("§cNot in a known dungeon room."))
            return
        }
        DungeonWaypointStore.clearRoom(tileKey(tile))
        applyRoom(tile)
        Misc.addChatMessage(Component.literal("§aCleared waypoints for the current room."))
    }

    /** Key for the waypoint bucket belonging to a single fixed grid tile. */
    private fun tileKey(tile: GridPos): String {
        return "tile:${tile.x()},${tile.z()}"
    }

    @JvmStatic
    fun isEditMode(): Boolean = editMode

    // ================= Routes =================

    /**
     * Toggles route recording, on or off — a plain on/off switch like toggleFill/toggleThrough.
     * Off -> on: waypoints placed from now on are appended, in order, to a route instead of being
     * standalone. Plain `/fmwp route` (no name) auto-names it "route1", "route2", etc.; a name
     * can still be given to pick one explicitly. On -> off: stops recording (any name argument is
     * ignored on the way off, so `/fmwp route` always ends whatever's currently recording).
     */
    @JvmStatic
    fun toggleRoute(name: String?) {
        if (recordingRouteId != null) {
            Misc.addChatMessage(Component.literal("§aFinished recording route '$recordingRouteId' ($recordingNextOrder point(s))."))
            recordingRouteId = null
            return
        }
        recordingRouteId = if (name == null || name.isBlank()) nextAutoRouteName() else name
        recordingNextOrder = 0
        Misc.addChatMessage(Component.literal("§aRecording route '$recordingRouteId' — place waypoints in order, then §f/fmwp route§a to finish."))
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
            Misc.addChatMessage(Component.literal("§cGive a route name: /fmwp route delete <name>"))
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

    private fun dungeonMode(): Boolean {
        return Location.inDungeon() && MapReader.isCalibrated()
    }

    /**
     * Key for the current freeform waypoint bucket, used outside calibrated dungeons. On Hypixel
     * Skyblock this is keyed by the current island/zone (per [Location], e.g. HUB, THE_PARK,
     * CRYSTAL_HOLLOWS) rather than the Minecraft dimension — Skyblock crams most islands into a single
     * dimension, so a dimension-keyed bucket would mix waypoints from unrelated places together.
     * Off Skyblock, falls back to server address + dimension.
     */
    private fun globalKey(): String {
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

    // ================= Tick / interaction =================

    private fun onTick(mc: Minecraft) {
        if (mc.player == null || mc.level == null) {
            liveWaypoints.clear()
            lastTile = null
            lastGlobalDim = null
            return
        }

        if (dungeonMode()) {
            lastGlobalDim = null
            val tile = MapReader.worldToGridPos(mc.player!!.x, mc.player!!.z)
            if (tile != lastTile) {
                lastTile = tile
                applyRoom(tile)
            }
        } else {
            lastTile = null
            val key = globalKey()
            if (key != lastGlobalDim) {
                lastGlobalDim = key
                applyGlobal()
            }
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

    private fun currentTile(): GridPos? {
        val mc = Minecraft.getInstance()
        if (mc.player == null || !MapReader.isCalibrated()) return null
        return MapReader.worldToGridPos(mc.player!!.x, mc.player!!.z)
    }

    /** Raycasts along the player's look vector, per [fishmod.features.PingFeature.placePing]. */
    private fun aimPoint(mc: Minecraft): Vec3 {
        val p = mc.player!!
        val delta = mc.deltaTracker.getGameTimeDeltaPartialTick(false)
        val eye = p.getEyePosition(delta)
        val look = p.getViewVector(delta)
        val end = eye.add(look.scale(distance.toDouble()))

        val hit: BlockHitResult? = mc.level!!.clip(
            ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p)
        )
        if (hit != null && hit.type != HitResult.Type.MISS) {
            // Center on the targeted block itself, same as the X/Z centering below — the old code
            // only added the +0.5 on X/Z and left Y as the block's raw (bottom) coordinate, so the
            // marker sat half a block low and got clipped by the block underneath it.
            val bp: BlockPos = hit.blockPos
            return Vec3(bp.x + 0.5, bp.y + 0.5, bp.z + 0.5)
        }
        return end
    }

    private fun handlePlace(mc: Minecraft) {
        if (!dungeonMode()) {
            handlePlaceGlobal(mc)
            return
        }

        val tile = currentTile()
        if (tile == null) {
            Misc.addChatMessage(Component.literal("§cNot in a known dungeon room."))
            return
        }

        val aim = aimPoint(mc)
        val px = aim.x + offsetX
        val py = aim.y + offsetY
        val pz = aim.z + offsetZ
        offsetX = 0.0; offsetY = 0.0; offsetZ = 0.0 // one-shot

        val key = tileKey(tile)
        val canonical = toCanonical(tile, px, py, pz)

        if (mc.player!!.isShiftKeyDown) {
            mc.setScreen(DungeonWaypointTitleScreen { title ->
                canonical.title = title
                tagRoute(canonical)
                DungeonWaypointStore.add(key, canonical)
                applyRoom(tile)
            })
            return
        }

        val removed = DungeonWaypointStore.removeNear(key, canonical.x, canonical.y, canonical.z, PLACE_EPSILON)
        if (!removed) {
            tagRoute(canonical)
            DungeonWaypointStore.add(key, canonical)
        }
        applyRoom(tile)
    }

    /** If a route is currently being recorded, tags [w] with it and advances the recording order. */
    private fun tagRoute(w: StoredWaypoint) {
        if (recordingRouteId == null) return
        w.routeId = recordingRouteId
        w.routeOrder = recordingNextOrder++
    }

    /** Places/removes a freeform waypoint at absolute world coordinates, used outside calibrated dungeons. */
    private fun handlePlaceGlobal(mc: Minecraft) {
        val aim = aimPoint(mc)
        val px = aim.x + offsetX
        val py = aim.y + offsetY
        val pz = aim.z + offsetZ
        offsetX = 0.0; offsetY = 0.0; offsetZ = 0.0 // one-shot

        val half = if (useBlockSize) 0.5 else size / 2.0
        val key = globalKey()

        if (mc.player!!.isShiftKeyDown) {
            mc.setScreen(DungeonWaypointTitleScreen { title ->
                val w = StoredWaypoint(
                    px, py, pz, half, half, half,
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
                px, py, pz, half, half, half,
                color, fill, through, null,
                if (type == WaypointType.NONE) null else type.name,
                if (timer == TimerType.NONE) null else timer.name
            )
            tagRoute(w)
            DungeonWaypointStore.add(key, w)
        }
        applyGlobal()
    }

    /** Loads the freeform waypoints for the current server+dimension as live, absolute-coordinate boxes. */
    private fun applyGlobal() {
        val result = ArrayList<LiveWaypoint>()
        for (w in DungeonWaypointStore.get(globalKey())) {
            val box = AABB(
                w.x - w.halfX, w.y - w.halfY, w.z - w.halfZ,
                w.x + w.halfX, w.y + w.halfY, w.z + w.halfZ
            )
            result.add(LiveWaypoint(box, w.color, w.filled, w.throughWalls, w.title, w.routeId, w.routeOrder))
        }
        liveWaypoints = result
    }

    /** Called by the waypoint list GUI after it edits/deletes entries, to refresh what's currently rendering. */
    @JvmStatic
    fun refreshLive() {
        if (dungeonMode()) {
            val tile = currentTile()
            if (tile != null) applyRoom(tile) else liveWaypoints.clear()
        } else {
            applyGlobal()
        }
    }

    // ================= Canonical <-> live conversion =================

    private fun toCanonical(tile: GridPos, worldX: Double, worldY: Double, worldZ: Double): StoredWaypoint {
        val originX = MapReader.tileWorldOriginX(tile.x())
        val originZ = MapReader.tileWorldOriginZ(tile.z())
        val lx = worldX - (originX + 16)
        val lz = worldZ - (originZ + 16)

        val half = if (useBlockSize) 0.5 else size / 2.0
        return StoredWaypoint(
            lx, worldY, lz, half, half, half,
            color, fill, through, null,
            if (type == WaypointType.NONE) null else type.name,
            if (timer == TimerType.NONE) null else timer.name
        )
    }

    private fun toLive(w: StoredWaypoint, tile: GridPos): Vec3 {
        val originX = MapReader.tileWorldOriginX(tile.x())
        val originZ = MapReader.tileWorldOriginZ(tile.z())
        return Vec3(originX + 16 + w.x, w.y, originZ + 16 + w.z)
    }

    private fun applyRoom(tile: GridPos) {
        val result = ArrayList<LiveWaypoint>()
        for (w in DungeonWaypointStore.get(tileKey(tile))) {
            val center = toLive(w, tile)
            val box = AABB(
                center.x - w.halfX, center.y - w.halfY, center.z - w.halfZ,
                center.x + w.halfX, center.y + w.halfY, center.z + w.halfZ
            )
            result.add(LiveWaypoint(box, w.color, w.filled, w.throughWalls, w.title, w.routeId, w.routeOrder))
        }
        liveWaypoints = result
    }

    // ================= Rendering =================

    private fun render(ctx: LevelRenderContext, matrices: PoseStack, vc: VertexConsumer) {
        val mc = Minecraft.getInstance()
        for (w in liveWaypoints) {
            if (!w.throughWalls && !hasLineOfSight(mc, w.center)) continue
            if (w.routeId != null && routeReached.getOrDefault(w.routeId, emptySet()).contains(w.routeOrder)) continue
            val rgba = RenderUtils.toFloats(w.color)
            // Outlines are thin filled boxes (renderThickOutline), not GL_LINES — that keeps their
            // thickness an actual configurable size and, just as importantly, keeps them on the same
            // triangle-strip layer as filled boxes so nothing gets mixed with real GL_LINES data
            // (mixing topologies on one layer is what caused the earlier "bowtie" corruption).
            if (w.filled) RenderUtils.renderFilled(matrices, vc, w.box, rgba)
            else RenderUtils.renderThickOutline(matrices, vc, w.box, rgba, lineWidth)
            if (w.title != null && w.title.isNotBlank()) {
                RenderUtils.renderText(ctx, matrices, Component.literal(w.title), w.center.x, w.box.maxY + 0.4, w.center.z, 1.0f)
            }
        }

        if (editMode) {
            if (mc.player != null && mc.level != null) {
                val aim = aimPoint(mc)
                val half = if (useBlockSize) 0.5 else size / 2.0
                val box = AABB(aim.x - half, aim.y - half, aim.z - half, aim.x + half, aim.y + half, aim.z + half)
                RenderUtils.renderThickOutline(matrices, vc, box, floatArrayOf(1f, 1f, 1f, 0.9f), lineWidth)
            }
        }
    }

    /**
     * Draws the one thing that's genuine GL_LINES geometry: route connector lines. Runs on a
     * dedicated GL_LINES layer (see [RenderingEvents.NO_DEPTH_LINE]) — this must never share a
     * VertexConsumer with [render] (box fills/outlines + text), which uses a triangle-strip
     * layer. Pushing line-pair vertices into that triangle-strip buffer is exactly what produced the
     * corrupted "bowtie" shapes users reported: each 2-vertex line got stitched into the strip as a
     * stray, often huge, degenerate triangle connecting unrelated geometry.
     */
    private fun renderLines(matrices: PoseStack, vc: VertexConsumer) {
        for ((key, value) in groupRoutes()) {
            val reached = routeReached.getOrDefault(key, emptySet())
            var prev: LiveWaypoint? = null
            for (w in value) {
                if (reached.contains(w.routeOrder)) continue
                if (prev != null) RenderUtils.renderLine(matrices, vc, prev.center, w.center, ROUTE_LINE_RGBA)
                prev = w
            }
        }
    }

    /** Small on-screen settings readout while edit mode is on, drawn near screen center via HudRenderCallback. */
    @JvmStatic
    fun renderOverlay(ctx: GuiGraphicsExtractor) {
        if (!editMode) return
        val mc = Minecraft.getInstance()
        if (mc.font == null) return
        val cx = ctx.guiWidth() / 2
        val y = ctx.guiHeight() / 2 + 30
        val line = Component.literal(
            "§b[fmwp] §7fill:" + (if (fill) "§ay" else "§cn") + " §7size:§f" + size
                + " §7dist:§f" + distance + " §7blockSize:" + (if (useBlockSize) "§ay" else "§cn")
                + " §7through:" + (if (through) "§ay" else "§cn") + " §7type:§f" + type + " §7timer:§f" + timer
                + (if (!fill) " §7line:§f$lineWidth" else "")
                + (if (recordingRouteId != null) " §d🔗route:$recordingRouteId" else "")
        )
        val textWidth = mc.font!!.width(line)
        ctx.text(mc.font, line, cx - textWidth / 2, y, 0xFFFFFFFF.toInt(), true)
    }
}
