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
 * /fm wp — a waypoint editor. Disabled while [Location.inDungeon] is true;
 * outside dungeons, waypoints are keyed by Skyblock island/server+dimension at absolute
 * coordinates (see [globalKey]).
 */
object DungeonWaypoints {

    private const val PLACE_EPSILON = 0.05
    private const val ROUTE_REACH_RADIUS = 1.75
    private val ROUTE_LINE_RGBA = floatArrayOf(1f, 1f, 1f, 0.6f)

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
        RenderingEvents.NO_DEPTH_FILLED.register { ctx, matrices, vc -> render(ctx, matrices, vc) }
        // Route lines/cursor box use a dedicated GL_LINES layer, never the box-fill triangle strip.
        RenderingEvents.NO_DEPTH_LINE.register { _, matrices, vc -> renderLines(matrices, vc) }
    }

    /**
     * Whether [target] is visible from the player's eyes — everything renders on the no-depth
     * layer regardless of the per-waypoint flag (the depth-tested layer silently broke room
     * waypoints), so occlusion is faked with a manual raycast instead.
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
        if (Location.inDungeon()) {
            Misc.addChatMessage(Component.literal("§cWaypoints aren't available inside dungeons."))
            return
        }
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

    /** Waypoints are dungeon-run-specific ground truth we don't have; only usable outside a dungeon. */
    private fun blocked(): Boolean = Location.inDungeon()

    /** Keyed by Skyblock island/zone rather than dimension, since Skyblock crams islands into one dimension. */
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

    private fun onTick(mc: Minecraft) {
        if (mc.player == null || mc.level == null) {
            liveWaypoints.clear()
            lastGlobalDim = null
            return
        }

        if (blocked()) {
            liveWaypoints.clear()
            lastGlobalDim = null
            while (placeKey != null && placeKey!!.consumeClick()) { /* drop clicks while unavailable */ }
            return
        }

        val key = globalKey()
        if (key != lastGlobalDim) {
            lastGlobalDim = key
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

    /** Raycasts along the player's look vector. */
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
            // Center on Y too (not just X/Z) — old code left Y at the block's bottom, clipping the marker.
            val bp: BlockPos = hit.blockPos
            return Vec3(bp.x + 0.5, bp.y + 0.5, bp.z + 0.5)
        }
        return end
    }

    private fun handlePlace(mc: Minecraft) {
        if (blocked()) {
            Misc.addChatMessage(Component.literal("§cWaypoints aren't available inside dungeons."))
            return
        }
        handlePlaceGlobal(mc)
    }

    /** If a route is currently being recorded, tags [w] with it and advances the recording order. */
    private fun tagRoute(w: StoredWaypoint) {
        if (recordingRouteId == null) return
        w.routeId = recordingRouteId
        w.routeOrder = recordingNextOrder++
    }

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
        if (blocked()) liveWaypoints.clear() else applyGlobal()
    }

    private fun render(ctx: LevelRenderContext, matrices: PoseStack, vc: VertexConsumer) {
        val mc = Minecraft.getInstance()
        for (w in liveWaypoints) {
            if (!w.throughWalls && !hasLineOfSight(mc, w.center)) continue
            if (w.routeId != null && routeReached.getOrDefault(w.routeId, emptySet()).contains(w.routeOrder)) continue
            val rgba = RenderUtils.toFloats(w.color)
            // Outlines are thin filled boxes, not GL_LINES, to keep them on the same triangle-strip
            // layer as fills — mixing topologies on one layer caused the earlier "bowtie" corruption.
            if (w.filled) RenderUtils.renderFilled(matrices, vc, w.box, rgba)
            else RenderUtils.renderThickOutline(matrices, vc, w.box, rgba, lineWidth)
            if (w.titleComponent != null) {
                RenderUtils.renderText(ctx, matrices, w.titleComponent, w.center.x, w.box.maxY + 0.4, w.center.z, 1.0f)
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

    /** Route connector lines only — must never share a VertexConsumer with [render]'s triangle-strip layer. */
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
