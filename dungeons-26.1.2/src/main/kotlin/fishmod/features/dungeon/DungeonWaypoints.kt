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
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.lwjgl.glfw.GLFW
import java.util.LinkedHashMap

object DungeonWaypoints {

    private const val PLACE_EPSILON = 0.05
    private const val ROUTE_REACH_RADIUS = 1.75
    private const val PIXEL_SIZE = 1.0 / 16.0
    private val ROUTE_LINE_RGBA = floatArrayOf(1f, 1f, 1f, 0.6f)
    private const val ROUTE_LINE_ARGB = 0x99FFFFFF.toInt()

    private var editMode = false
    private var fill = false
    private var size = 0.5
    private var distance = 20
    private var useBlockSize = true
    private var pixelMode = false
    private var through = false
    private var color = 0xFF55FFFF.toInt()
    private var type = WaypointType.NONE
    private var timer = TimerType.NONE
    private var offsetX = 0.0
    private var offsetY = 0.0
    private var offsetZ = 0.0

    private var lineWidth = 0.05

    private var placeKey: KeyMapping? = null
    private var lastGlobalDim: String? = null
    private var lastRoomAnchor: DungeonRoomAnchor.Anchor? = null

    private var recordingRouteId: String? = null
    private var recordingNextOrder = 0

    private val routeReached: MutableMap<String, MutableSet<Int>> = HashMap()

    private val routeLoopCursor: MutableMap<String, Int> = HashMap()

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
        @JvmField val titleComponent: Component? = if (title != null && title.isNotBlank()) Component.literal(title) else null
    }

    private var liveWaypoints: MutableList<LiveWaypoint> = ArrayList()

    private var cachedMergedOccluded: List<MergedGroup> = emptyList()
    private var cachedMergedThrough: List<MergedGroup> = emptyList()

    @JvmStatic
    fun init() {
        val category = fishmod.utils.Keybinds.category()
        placeKey = KeyMappingHelper.registerKeyMapping(
            KeyMapping(
                "Dungeon Waypoint Place/Remove",
                InputConstants.Type.MOUSE,
                GLFW.GLFW_MOUSE_BUTTON_RIGHT,
                category
            )
        )

        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { mc -> onTick(mc) })
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
    fun togglePixelMode() {
        pixelMode = !pixelMode
        Misc.addChatMessage(Component.literal("§7[fm wp] Pixel mode: " + (if (pixelMode) "§aon §7(1/16-block precision, snapped to the pixel you're aiming at)" else "§coff")))
    }

    private fun snapToPixel(v: Double): Double = Math.round(v / PIXEL_SIZE) * PIXEL_SIZE

    private fun pixelCellCenter(v: Double): Double = (Math.floor(v / PIXEL_SIZE) + 0.5) * PIXEL_SIZE

    private fun snapAimForPixel(exact: Vec3, face: Direction?): Vec3 {
        val axis = face?.axis
        val x = if (axis == Direction.Axis.X) snapToPixel(exact.x) else pixelCellCenter(exact.x)
        val y = if (axis == Direction.Axis.Y) snapToPixel(exact.y) else pixelCellCenter(exact.y)
        val z = if (axis == Direction.Axis.Z) snapToPixel(exact.z) else pixelCellCenter(exact.z)
        return Vec3(x, y, z)
    }

    @JvmStatic
    fun toggleThrough() {
        through = !through
        Misc.addChatMessage(Component.literal("§7[fm wp] Through walls: " + (if (through) "§aon" else "§coff")))
    }

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

    @JvmStatic fun isFill(): Boolean = fill
    @JvmStatic fun setFill(v: Boolean) { fill = v }
    @JvmStatic fun getSize(): Double = size
    @JvmStatic fun setSizeQuiet(s: Double) { size = s.coerceIn(0.1, 1.0) }
    @JvmStatic fun getDistance(): Int = distance
    @JvmStatic fun setDistanceQuiet(d: Int) { distance = maxOf(1, d) }
    @JvmStatic fun isUseBlockSize(): Boolean = useBlockSize
    @JvmStatic fun setUseBlockSize(v: Boolean) { useBlockSize = v }
    @JvmStatic fun isPixelMode(): Boolean = pixelMode
    @JvmStatic fun setPixelMode(v: Boolean) { pixelMode = v }
    @JvmStatic fun isThrough(): Boolean = through
    @JvmStatic fun setThrough(v: Boolean) { through = v }
    @JvmStatic fun getLineWidth(): Double = lineWidth
    @JvmStatic fun setLineWidthQuiet(w: Double) { lineWidth = w.coerceIn(0.01, 0.5) }
    @JvmStatic fun getColorArgb(): Int = color
    @JvmStatic fun setColorArgb(argb: Int) { color = argb }
    @JvmStatic fun getType(): WaypointType = type
    @JvmStatic fun setTypeEnum(v: WaypointType) { type = v }
    @JvmStatic fun getTimer(): TimerType = timer
    @JvmStatic fun setTimerEnum(v: TimerType) { timer = v }

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

    @JvmStatic
    fun endRoute(name: String?) {
        if (name == null || name.isBlank()) {
            routeReached.clear()
            routeLoopCursor.clear()
            Misc.addChatMessage(Component.literal("§aAll routes reset — waypoints visible again."))
        } else {
            routeReached.remove(name)
            routeLoopCursor.remove(name)
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
        routeLoopCursor.remove(name)
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
            lastRoomAnchor = null
            return
        }

        val key = globalKey()
        if (key != lastGlobalDim) {
            lastGlobalDim = key
            lastRoomAnchor = if (isRoomKey(key)) DungeonRoomAnchor.current() else null
            applyGlobal()
        } else if (isRoomKey(key)) {
            val anchor = DungeonRoomAnchor.current()
            if (anchor != lastRoomAnchor) {
                lastRoomAnchor = anchor
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

    private fun advanceRouteProgress(mc: Minecraft) {
        val player = mc.player ?: return
        val playerPos = player.position()
        for ((key, value) in groupRoutes()) {
            val reached = routeReached.getOrPut(key) { HashSet() }
            for (w in value) {
                if (reached.contains(w.routeOrder)) continue
                if (playerPos.distanceTo(w.center) < ROUTE_REACH_RADIUS) {
                    reached.add(w.routeOrder)
                    if (reached.size >= value.size) {
                        val orders = value.map { it.routeOrder }
                        val lastCursor = routeLoopCursor[key]
                        val lastIdx = if (lastCursor != null) orders.indexOf(lastCursor) else -1
                        val nextIdx = if (lastIdx == -1) 0 else (lastIdx + 1) % orders.size
                        val next = orders[nextIdx]
                        reached.remove(next)
                        routeLoopCursor[key] = next
                    }
                }
                break
            }
        }
    }

    private class AimResult(@JvmField val point: Vec3, @JvmField val blockBox: AABB?, @JvmField val exact: Vec3, @JvmField val face: Direction?)

    private fun playerEyePos(mc: Minecraft): Vec3? {
        val p = mc.player ?: return null
        val delta = mc.deltaTracker.getGameTimeDeltaPartialTick(false)
        return p.getEyePosition(delta).add(p.getViewVector(delta).scale(0.2))
    }

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
            return AimResult(worldBox.center, worldBox, hit.location, hit.direction)
        }
        val snapped = Vec3(Math.floor(end.x) + 0.5, Math.floor(end.y) + 0.5, Math.floor(end.z) + 0.5)
        return AimResult(snapped, null, snapped, null)
    }

    private fun pixelHalfExtents(face: Direction?): Triple<Double, Double, Double> {
        val full = PIXEL_SIZE / 2.0
        val flat = PIXEL_SIZE / 32.0
        return when (face?.axis) {
            Direction.Axis.X -> Triple(flat, full, full)
            Direction.Axis.Y -> Triple(full, flat, full)
            Direction.Axis.Z -> Triple(full, full, flat)
            null -> Triple(full, full, full)
        }
    }

    private fun handlePlace(mc: Minecraft) {
        handlePlaceGlobal(mc)
    }

    private fun tagRoute(w: StoredWaypoint) {
        if (recordingRouteId == null) return
        w.routeId = recordingRouteId
        w.routeOrder = recordingNextOrder++
    }

    private fun handlePlaceGlobal(mc: Minecraft) {
        val aimResult = aimPoint(mc)
        val aim = if (pixelMode) snapAimForPixel(aimResult.exact, aimResult.face) else aimResult.point
        var px = aim.x + offsetX
        var py = aim.y + offsetY
        var pz = aim.z + offsetZ
        offsetX = 0.0; offsetY = 0.0; offsetZ = 0.0

        val halfX: Double; val halfY: Double; val halfZ: Double
        if (pixelMode) {
            val (hx, hy, hz) = pixelHalfExtents(aimResult.face)
            halfX = hx; halfY = hy; halfZ = hz
        } else if (useBlockSize && aimResult.blockBox != null) {
            halfX = (aimResult.blockBox.maxX - aimResult.blockBox.minX) / 2.0
            halfY = (aimResult.blockBox.maxY - aimResult.blockBox.minY) / 2.0
            halfZ = (aimResult.blockBox.maxZ - aimResult.blockBox.minZ) / 2.0
        } else {
            val half = if (useBlockSize) 0.5 else size / 2.0
            halfX = half; halfY = half; halfZ = half
        }

        val placeFilled = if (pixelMode) true else fill

        val key = globalKey()

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
                    color, placeFilled, through, title,
                    if (type == WaypointType.NONE) null else type.name,
                    if (timer == TimerType.NONE) null else timer.name
                )
                tagRoute(w)
                DungeonWaypointStore.add(key, w)
                applyGlobal()
            })
            return
        }

        val removeEpsilon = if (pixelMode) 1e-6 else PLACE_EPSILON
        val removed = DungeonWaypointStore.removeNear(key, px, py, pz, removeEpsilon)
        if (!removed) {
            val w = StoredWaypoint(
                px, py, pz, halfX, halfY, halfZ,
                color, placeFilled, through, null,
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
        @JvmField val quads: List<Array<Vec3>>,
        @JvmField val edges: List<DoubleArray>
    )

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
        for (j in 0 until ny) {
            val top = Array(nx) { i -> BooleanArray(nz) { k -> occ[i][j][k] && !occAt(i, j + 1, k) } }
            for (r in greedyRects(top, nx, nz)) {
                val x1 = xs[r[0]]; val z1 = zs[r[1]]; val x2 = xs[r[2]]; val z2 = zs[r[3]]; val y = ys[j + 1]
                quads.add(arrayOf(Vec3(x1, y, z1), Vec3(x1, y, z2), Vec3(x2, y, z2), Vec3(x2, y, z1)))
            }
            val bottom = Array(nx) { i -> BooleanArray(nz) { k -> occ[i][j][k] && !occAt(i, j - 1, k) } }
            for (r in greedyRects(bottom, nx, nz)) {
                val x1 = xs[r[0]]; val z1 = zs[r[1]]; val x2 = xs[r[2]]; val z2 = zs[r[3]]; val y = ys[j]
                quads.add(arrayOf(Vec3(x1, y, z1), Vec3(x2, y, z1), Vec3(x2, y, z2), Vec3(x1, y, z2)))
            }
        }
        for (i in 0 until nx) {
            val pos = Array(ny) { j -> BooleanArray(nz) { k -> occ[i][j][k] && !occAt(i + 1, j, k) } }
            for (r in greedyRects(pos, ny, nz)) {
                val y1 = ys[r[0]]; val z1 = zs[r[1]]; val y2 = ys[r[2]]; val z2 = zs[r[3]]; val x = xs[i + 1]
                quads.add(arrayOf(Vec3(x, y1, z1), Vec3(x, y2, z1), Vec3(x, y2, z2), Vec3(x, y1, z2)))
            }
            val neg = Array(ny) { j -> BooleanArray(nz) { k -> occ[i][j][k] && !occAt(i - 1, j, k) } }
            for (r in greedyRects(neg, ny, nz)) {
                val y1 = ys[r[0]]; val z1 = zs[r[1]]; val y2 = ys[r[2]]; val z2 = zs[r[3]]; val x = xs[i]
                quads.add(arrayOf(Vec3(x, y1, z1), Vec3(x, y1, z2), Vec3(x, y2, z2), Vec3(x, y2, z1)))
            }
        }
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

    private const val FLAT_PIXEL_THRESHOLD = 0.01

    private fun isFlatPixel(box: AABB): Boolean =
        (box.maxX - box.minX) < FLAT_PIXEL_THRESHOLD ||
            (box.maxY - box.minY) < FLAT_PIXEL_THRESHOLD ||
            (box.maxZ - box.minZ) < FLAT_PIXEL_THRESHOLD

    private fun buildMergedGroups(throughWalls: Boolean): List<MergedGroup> {
        val candidates = liveWaypoints.filter {
            it.throughWalls == throughWalls && it.routeId == null && it.titleComponent == null && !isFlatPixel(it.box)
        }
        val result = ArrayList<MergedGroup>()
        for ((key, group) in candidates.groupBy { Pair(it.color, it.filled) }) {
            val (quads, edges) = traceSurface(group.map { it.box }) ?: continue
            result.add(MergedGroup(key.first, key.second, quads, edges))
        }
        return result
    }

    private fun buildSingles(throughWalls: Boolean): List<LiveWaypoint> =
        liveWaypoints.filter {
            it.throughWalls == throughWalls && !reached(it) &&
                (it.routeId != null || it.titleComponent != null || isFlatPixel(it.box))
        }

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

    private fun renderGizmo() {
        if (!FishSettings.dungeonWaypointsEnabled) return
        val mc = Minecraft.getInstance()
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
        val eye = playerEyePos(mc)
        if (eye != null) {
            for ((_, value) in groupRoutes()) {
                val next = value.firstOrNull { !reached(it) } ?: continue
                if (!next.throughWalls) RenderUtils.gizmoThickLine(eye, next.center, lineWidth / 2.0, ROUTE_LINE_ARGB)
            }
        }
    }

    private fun render(ctx: LevelRenderContext, matrices: PoseStack, vc: VertexConsumer) {
        val mc = Minecraft.getInstance()
        if (FishSettings.dungeonWaypointsEnabled) {
            for (g in cachedMergedThrough) {
                if (g.filled) drawMergedFillThrough(matrices, vc, g) else drawMergedOutlineThrough(matrices, vc, g)
            }
            for (w in buildSingles(throughWalls = true)) {
                val rgba = RenderUtils.toFloats(w.color)
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
                val aim = if (pixelMode) snapAimForPixel(aimResult.exact, aimResult.face) else aimResult.point
                val box = if (pixelMode) {
                    val (hx, hy, hz) = pixelHalfExtents(aimResult.face)
                    AABB(aim.x - hx, aim.y - hy, aim.z - hz, aim.x + hx, aim.y + hy, aim.z + hz)
                } else if (useBlockSize && aimResult.blockBox != null) {
                    aimResult.blockBox
                } else {
                    val half = if (useBlockSize) 0.5 else size / 2.0
                    AABB(aim.x - half, aim.y - half, aim.z - half, aim.x + half, aim.y + half, aim.z + half)
                }
                if (pixelMode) RenderUtils.renderFilled(matrices, vc, box, floatArrayOf(1f, 1f, 1f, 0.9f))
                else RenderUtils.renderThickOutline(matrices, vc, box, floatArrayOf(1f, 1f, 1f, 0.9f), lineWidth)
            }
        }
    }

    private fun renderLines(matrices: PoseStack, vc: VertexConsumer) {
        if (!FishSettings.dungeonWaypointsEnabled) return
        val eye = playerEyePos(Minecraft.getInstance()) ?: return
        for ((key, value) in groupRoutes()) {
            val reached = routeReached.getOrDefault(key, emptySet())
            val next = value.firstOrNull { !reached.contains(it.routeOrder) } ?: continue
            if (next.throughWalls) RenderUtils.renderThickLine(matrices, vc, eye, next.center, lineWidth / 2.0, ROUTE_LINE_RGBA)
        }
    }

    private var cachedOverlayKey: String? = null
    private var cachedOverlayLine: Component? = null
    private var cachedOverlayWidth: Int = 0

    @JvmStatic
    fun renderOverlay(ctx: GuiGraphicsExtractor) {
        if (!editMode) return
        val mc = Minecraft.getInstance()
        if (mc.font == null) return

        val key = "$fill|$size|$distance|$useBlockSize|$pixelMode|$through|$type|$timer|$lineWidth|$recordingRouteId"
        if (key != cachedOverlayKey) {
            cachedOverlayKey = key
            val line = Component.literal(
                "§b[fm wp] §7fill:" + (if (fill) "§ay" else "§cn") + " §7size:§f" + size
                    + " §7dist:§f" + distance + " §7blockSize:" + (if (useBlockSize) "§ay" else "§cn")
                    + " §7pixel:" + (if (pixelMode) "§ay" else "§cn")
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
