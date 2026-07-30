package fishmod.features.dungeon

import fishmod.utils.Constants
import fishmod.utils.Location
import fishmod.utils.Misc
import fishmod.utils.dungeon.map.DungeonGrid
import fishmod.utils.dungeon.map.GridPos
import fishmod.utils.dungeon.map.MapReader
import fishmod.utils.dungeon.map.RoomSignature
import fishmod.utils.dungeon.map.RoomTile
import fishmod.utils.dungeon.waypoints.DungeonWaypointStore
import fishmod.utils.dungeon.waypoints.StoredWaypoint
import fishmod.utils.dungeon.waypoints.TimerType
import fishmod.utils.dungeon.waypoints.WaypointType
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.util.InputUtil
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import net.minecraft.world.RaycastContext
import org.lwjgl.glfw.GLFW

/** /dwp — an OdinLegacy-style dungeon waypoint editor (github.com/odtheking/OdinLegacy, DungeonWaypoints.kt/DungeonWaypointCommand.kt/DungeonWaypointConfig.kt). */
object DungeonWaypoints {

    private const val PLACE_EPSILON = 0.05

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

    private var placeKey: KeyBinding? = null
    private var lastTile: GridPos? = null

    /** A waypoint applied to the currently-live room, in real world coordinates. */
    private class LiveWaypoint(
        val box: Box,
        val color: Int,
        val filled: Boolean,
        val throughWalls: Boolean,
        val title: String?
    ) {
        val center: Vec3d = box.center
    }

    private var liveWaypoints: List<LiveWaypoint> = ArrayList()

    @JvmStatic
    fun init() {
        placeKey = KeyBindingHelper.registerKeyBinding(
            KeyBinding(
                "FishMod - Dungeon Waypoint place/remove",
                InputUtil.Type.MOUSE,
                GLFW.GLFW_MOUSE_BUTTON_RIGHT,
                fishmod.utils.Keybinds.category ?: KeyBinding.Category.create(Identifier.of(Constants.NAMESPACE))
            )
        )

        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { mc -> onTick(mc) })
        RenderingEvents.NO_DEPTH_FILLED.register { ctx, matrices, vc -> render(ctx, matrices, vc) }
    }

    // ================= Commands =================

    @JvmStatic
    fun toggleEdit() {
        editMode = !editMode
        Misc.addChatMessage(Text.literal("Dungeon Waypoint editing " + (if (editMode) "§aenabled" else "§cdisabled") + "§r!"))
    }

    @JvmStatic
    fun toggleFill() {
        fill = !fill
        Misc.addChatMessage(Text.literal("§7[dwp] Fill: " + (if (fill) "§afilled" else "§coutline")))
    }

    @JvmStatic
    fun setSize(s: Double) {
        size = Math.max(0.1, Math.min(1.0, s))
        Misc.addChatMessage(Text.literal("§7[dwp] Size: §f$size"))
    }

    @JvmStatic
    fun setDistance(d: Int) {
        distance = Math.max(1, d)
        Misc.addChatMessage(Text.literal("§7[dwp] Distance: §f$distance"))
    }

    @JvmStatic
    fun resetSecrets() {
        Misc.addChatMessage(Text.literal("§7[dwp] Secret tracking reset (no-op in this version)."))
    }

    @JvmStatic
    fun setType(name: String) {
        try {
            type = WaypointType.valueOf(name.uppercase())
            Misc.addChatMessage(Text.literal("§7[dwp] Type: §f$type"))
        } catch (e: IllegalArgumentException) {
            Misc.addChatMessage(Text.literal("§cUnknown waypoint type: $name"))
        }
    }

    @JvmStatic
    fun setTimer(name: String) {
        try {
            timer = TimerType.valueOf(name.uppercase())
            Misc.addChatMessage(Text.literal("§7[dwp] Timer: §f$timer"))
        } catch (e: IllegalArgumentException) {
            Misc.addChatMessage(Text.literal("§cUnknown timer type: $name"))
        }
    }

    @JvmStatic
    fun toggleUseBlockSize() {
        useBlockSize = !useBlockSize
        Misc.addChatMessage(Text.literal("§7[dwp] Use block size: " + (if (useBlockSize) "§aon" else "§coff")))
    }

    @JvmStatic
    fun setOffset(x: Double, y: Double, z: Double) {
        offsetX = x; offsetY = y; offsetZ = z
        Misc.addChatMessage(Text.literal("§7[dwp] One-shot offset set to §f$x, $y, $z"))
    }

    @JvmStatic
    fun toggleThrough() {
        through = !through
        Misc.addChatMessage(Text.literal("§7[dwp] Through walls: " + (if (through) "§aon" else "§coff")))
    }

    @JvmStatic
    fun setColor(hex: String?) {
        if (hex == null || hex.length != 8) {
            Misc.addChatMessage(Text.literal("§cColor must be 8 hex chars (RRGGBBAA)."))
            return
        }
        try {
            val rgba = hex.toLong(16)
            val r = ((rgba shr 24) and 0xFF).toInt()
            val g = ((rgba shr 16) and 0xFF).toInt()
            val b = ((rgba shr 8) and 0xFF).toInt()
            val a = (rgba and 0xFF).toInt()
            color = (a shl 24) or (r shl 16) or (g shl 8) or b
            Misc.addChatMessage(Text.literal("§7[dwp] Color set."))
        } catch (e: NumberFormatException) {
            Misc.addChatMessage(Text.literal("§cInvalid hex color: $hex"))
        }
    }

    @JvmStatic
    fun exportToClipboard() {
        val b64 = DungeonWaypointStore.exportBase64()
        if (b64 == null) {
            Misc.addChatMessage(Text.literal("§cExport failed."))
            return
        }
        val mc = MinecraftClient.getInstance()
        mc.keyboard.clipboard = b64
        Misc.addChatMessage(Text.literal("§aWaypoint database copied to clipboard."))
    }

    @JvmStatic
    fun importFromClipboard() {
        val mc = MinecraftClient.getInstance()
        val clip = mc.keyboard.clipboard
        val ok = DungeonWaypointStore.importBase64(clip)
        if (ok) {
            lastTile = null // force re-apply
            Misc.addChatMessage(Text.literal("§aWaypoint database imported from clipboard."))
        } else {
            Misc.addChatMessage(Text.literal("§cImport failed — clipboard doesn't look like a valid waypoint export."))
        }
    }

    @JvmStatic
    fun resetCurrentRoom() {
        val tile = currentRoomTile()
        if (tile == null) {
            Misc.addChatMessage(Text.literal("§cNot in a known dungeon room."))
            return
        }
        DungeonWaypointStore.clearRoom(RoomSignature.withRotation(tile).key())
        applyRoom(tile)
        Misc.addChatMessage(Text.literal("§aCleared waypoints for the current room."))
    }

    @JvmStatic
    fun isEditMode(): Boolean = editMode

    // ================= Tick / interaction =================

    private fun onTick(mc: MinecraftClient) {
        if (mc.player == null || mc.world == null || !Location.inDungeon() || !MapReader.isCalibrated()) {
            liveWaypoints = ArrayList()
            lastTile = null
            return
        }

        val tile = MapReader.worldToGridPos(mc.player!!.x, mc.player!!.z)
        if (tile != lastTile) {
            lastTile = tile
            val roomTile = DungeonGrid.allRooms()[tile]
            if (roomTile != null) applyRoom(roomTile) else liveWaypoints = ArrayList()
        }

        var fired = false
        while (placeKey != null && placeKey!!.wasPressed()) fired = true
        if (fired && editMode && mc.currentScreen == null) {
            handlePlace(mc)
        }
    }

    private fun currentRoomTile(): RoomTile? {
        val mc = MinecraftClient.getInstance()
        if (mc.player == null || !MapReader.isCalibrated()) return null
        val tile = MapReader.worldToGridPos(mc.player!!.x, mc.player!!.z)
        return DungeonGrid.allRooms()[tile]
    }

    /** Raycasts along the player's look vector, per [fishmod.features.PingFeature.placePing]. */
    private fun aimPoint(mc: MinecraftClient): Vec3d {
        val p = mc.player!!
        val delta = mc.renderTickCounter.getTickProgress(false)
        val eye = p.getCameraPosVec(delta)
        val look = p.getRotationVec(delta)
        val end = eye.add(look.multiply(distance.toDouble()))

        val hit: BlockHitResult = mc.world!!.raycast(
            RaycastContext(eye, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, p)
        )
        if (hit.type != HitResult.Type.MISS) {
            val bp: BlockPos = hit.blockPos
            return Vec3d(bp.x + 0.5, bp.y.toDouble(), bp.z + 0.5)
        }
        return end
    }

    private fun handlePlace(mc: MinecraftClient) {
        val tile = currentRoomTile()
        if (tile == null) {
            Misc.addChatMessage(Text.literal("§cNot in a known dungeon room."))
            return
        }

        val aim = aimPoint(mc)
        val px = aim.x + offsetX
        val py = aim.y + offsetY
        val pz = aim.z + offsetZ
        offsetX = 0.0; offsetY = 0.0; offsetZ = 0.0 // one-shot

        val sig = RoomSignature.withRotation(tile)
        val canonical = toCanonical(tile, sig.rotation(), px, py, pz)

        if (mc.player!!.isSneaking) {
            mc.setScreen(DungeonWaypointTitleScreen { title ->
                canonical.title = title
                DungeonWaypointStore.add(sig.key(), canonical)
                applyRoom(tile)
            })
            return
        }

        val removed = DungeonWaypointStore.removeNear(sig.key(), canonical.x, canonical.y, canonical.z, PLACE_EPSILON)
        if (!removed) {
            DungeonWaypointStore.add(sig.key(), canonical)
        }
        applyRoom(tile)
    }

    // ================= Canonical <-> live conversion =================

    private fun toCanonical(tile: RoomTile, liveRotation: Int, worldX: Double, worldY: Double, worldZ: Double): StoredWaypoint {
        val originX = MapReader.tileWorldOriginX(tile.pos().x())
        val originZ = MapReader.tileWorldOriginZ(tile.pos().z())
        val lx = worldX - (originX + 16)
        val lz = worldZ - (originZ + 16)
        // Undo the live rotation to get back to canonical orientation.
        val canon = DungeonWaypointStore.rotate90(lx, lz, (4 - (liveRotation % 4)) % 4)

        val half = if (useBlockSize) 0.5 else size / 2.0
        return StoredWaypoint(
            canon[0], worldY, canon[1], half, half, half,
            color, fill, through, null,
            if (type == WaypointType.NONE) null else type.name,
            if (timer == TimerType.NONE) null else timer.name
        )
    }

    private fun toLive(w: StoredWaypoint, tile: RoomTile, liveRotation: Int): Vec3d {
        val originX = MapReader.tileWorldOriginX(tile.pos().x())
        val originZ = MapReader.tileWorldOriginZ(tile.pos().z())
        val live = DungeonWaypointStore.rotate90(w.x, w.z, liveRotation % 4)
        return Vec3d(originX + 16 + live[0], w.y, originZ + 16 + live[1])
    }

    private fun applyRoom(tile: RoomTile) {
        val result = ArrayList<LiveWaypoint>()
        val sig = RoomSignature.withRotation(tile)
        for (w in DungeonWaypointStore.get(sig.key())) {
            val center = toLive(w, tile, sig.rotation())
            val box = Box(
                center.x - w.halfX, center.y - w.halfY, center.z - w.halfZ,
                center.x + w.halfX, center.y + w.halfY, center.z + w.halfZ
            )
            result.add(LiveWaypoint(box, w.color, w.filled, w.throughWalls, w.title))
        }
        liveWaypoints = result
    }

    // ================= Rendering =================

    private fun render(ctx: WorldRenderContext, matrices: MatrixStack, vc: VertexConsumer) {
        if (!Location.inDungeon()) return

        for (w in liveWaypoints) {
            val rgba = RenderUtils.toFloats(w.color)
            if (w.filled) RenderUtils.renderFilled(matrices, vc, w.box, rgba) else RenderUtils.renderOutline(matrices, vc, w.box, rgba)
            if (w.title != null && w.title.isNotBlank()) {
                RenderUtils.renderText(ctx, matrices, Text.literal(w.title), w.center.x, w.box.maxY + 0.4, w.center.z, 1.0f)
            }
        }

        if (editMode) {
            val mc = MinecraftClient.getInstance()
            if (mc.player != null && mc.world != null && MapReader.isCalibrated()) {
                val aim = aimPoint(mc)
                val half = if (useBlockSize) 0.5 else size / 2.0
                val box = Box(aim.x - half, aim.y - half, aim.z - half, aim.x + half, aim.y + half, aim.z + half)
                RenderUtils.renderOutline(matrices, vc, box, floatArrayOf(1f, 1f, 1f, 0.9f))
            }
        }
    }

    /** Small on-screen settings readout while edit mode is on, drawn near screen center via HudRenderCallback. */
    @JvmStatic
    fun renderOverlay(ctx: DrawContext) {
        if (!editMode) return
        val mc = MinecraftClient.getInstance()
        if (mc.textRenderer == null) return
        val cx = ctx.scaledWindowWidth / 2
        val y = ctx.scaledWindowHeight / 2 + 30
        val line = Text.literal(
            "§b[dwp] §7fill:" + (if (fill) "§ay" else "§cn") + " §7size:§f" + size +
                " §7dist:§f" + distance + " §7blockSize:" + (if (useBlockSize) "§ay" else "§cn") +
                " §7through:" + (if (through) "§ay" else "§cn") + " §7type:§f" + type + " §7timer:§f" + timer
        )
        val textWidth = mc.textRenderer.getWidth(line)
        ctx.drawText(mc.textRenderer, line, cx - textWidth / 2, y, 0xFFFFFFFF.toInt(), true)
    }
}
