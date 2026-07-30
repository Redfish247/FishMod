package fishmod.features.dungeon.map

import config.practical.hud.HUDComponent
import config.practical.manager.ConfigValue
import fishmod.utils.Location
import fishmod.utils.config.values.DungeonMapSettings
import fishmod.utils.dungeon.map.DoorKey
import fishmod.utils.dungeon.map.DoorTile
import fishmod.utils.dungeon.map.DungeonGrid
import fishmod.utils.dungeon.map.GridPos
import fishmod.utils.dungeon.map.MapReader
import fishmod.utils.dungeon.map.PredictedRoomTile
import fishmod.utils.dungeon.map.RoomState
import fishmod.utils.dungeon.map.RoomTile
import fishmod.utils.dungeon.map.RoomType
import fishmod.utils.dungeon.map.Tile
import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.PlayerSkinDrawer
import net.minecraft.client.network.PlayerListEntry
import net.minecraft.text.Text
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws the fixed 6x6 dungeon room/door grid, following the same explicit-render pattern as
 * `F7Huds`: one [HUDComponent] field, condition-supplier forced `() -> false`,
 * rendered from a `HudRenderCallback` in FishModInit.
 *
 * Unlike earlier versions, this draws the whole fixed grid at a constant screen layout rather
 * than a player-centered sliding window — the grid itself no longer has any relationship to the
 * player's world position (see [GridPos]'s javadoc), so there's nothing to center on.
 */
object DungeonMapHud {
    private const val GRID_SIZE = 6
    private const val ROOM_PX = 20
    private const val DOOR_PX = 4
    private const val SIZE = GRID_SIZE * ROOM_PX + (GRID_SIZE - 1) * DOOR_PX
    private const val SECRETS_LINE_HEIGHT = 12

    @JvmField
    @ConfigValue
    var dungeonMap: HUDComponent = HUDComponent(10, 260, SIZE, SIZE + SECRETS_LINE_HEIGHT, 1, "Dungeon Map",
        { false },
        { component, context -> render(component, context) },
        { DungeonMapSettings.enabled })

    @JvmStatic
    fun display(): Boolean {
        return DungeonMapSettings.enabled && Location.inDungeon() && MapReader.isCalibrated()
                && !fishmod.utils.dungeon.Phase.inBoss()
    }

    /** Registered directly with a HudRenderCallback in FishModInit — mirrors F7Huds.renderHud. */
    @JvmStatic
    fun renderHud(ctx: DrawContext) {
        keepOnScreen(dungeonMap, 10, 260)
        if (!display()) return
        val stack = ctx.matrices
        stack.pushMatrix()
        stack.scale(dungeonMap.scale, dungeonMap.scale)
        render(dungeonMap, ctx)
        stack.popMatrix()
    }

    private fun keepOnScreen(component: HUDComponent, targetX: Int, targetY: Int) {
        val client = MinecraftClient.getInstance()
        if (client == null || client.window == null) return
        val screenWidth = client.window.scaledWidth
        val screenHeight = client.window.scaledHeight
        val x = component.scaledX
        val y = component.scaledY
        if (x >= 0 && x <= screenWidth - component.width && y >= 0 && y <= screenHeight - component.height) return
        component.move(
            (targetX - x).toDouble() * component.scale / screenWidth,
            (targetY - y).toDouble() * component.scale / screenHeight
        )
    }

    private data class LabelJob(
        val centerX: Int,
        val centerY: Int,
        val maxWidth: Int,
        val text: String,
        val color: Int,
        val secretsText: String?
    )

    @JvmStatic
    fun render(component: HUDComponent, ctx: DrawContext) {
        val baseX = component.scaledX
        val baseY = component.scaledY
        val labelJobs = ArrayList<LabelJob>()

        for (tileX in 0 until GRID_SIZE) {
            for (tileZ in 0 until GRID_SIZE) {
                val pos = GridPos(tileX, tileZ)
                val x = baseX + tileX * (ROOM_PX + DOOR_PX)
                val y = baseY + tileZ * (ROOM_PX + DOOR_PX)

                val tile: Tile = DungeonGrid.getWithPrediction(pos)
                if (tile is PredictedRoomTile) {
                    // No text label here on purpose — this is still an unopened/unconfirmed room,
                    // just narrowed to a few candidates; the color wedges alone communicate that
                    // without implying a name/type is actually known yet.
                    drawWedges(ctx, x, y, ROOM_PX, tile)
                } else {
                    val color = tile.color()
                    if (color != 0) ctx.fill(x, y, x + ROOM_PX, y + ROOM_PX, color)
                    if (DungeonMapSettings.showRoomNames && tile is RoomTile && tile.type() != null
                        && tile.type() != RoomType.ENTRANCE
                        && tile.state() != RoomState.UNOPENED
                        && DungeonGrid.isLabelAnchor(tile)
                    ) {
                        val text = if (tile.name() != null) tile.name() else label(tile.type())
                        if (!text.isNullOrEmpty()) {
                            val bbox = boundingBox(tile)
                            val boxW = (bbox[2] - bbox[0]) * (ROOM_PX + DOOR_PX) + ROOM_PX
                            val centroid = centroidOf(tile)
                            val centerX = baseX + (centroid[0] * (ROOM_PX + DOOR_PX)).roundToInt() + ROOM_PX / 2
                            val centerY = baseY + (centroid[1] * (ROOM_PX + DOOR_PX)).roundToInt() + ROOM_PX / 2
                            labelJobs.add(LabelJob(centerX, centerY, boxW - 2, text, labelColor(tile.state()), secretsLabel(tile)))
                        }
                    }
                }

                if (tileX < GRID_SIZE - 1) {
                    val east = pos.offset(1, 0)
                    val door = DungeonGrid.allDoors()[DoorKey(pos, true)]
                    if (door != null && door.color() != 0) {
                        val doorH = ROOM_PX / 2
                        val doorY = y + (ROOM_PX - doorH) / 2
                        ctx.fill(x + ROOM_PX, doorY, x + ROOM_PX + DOOR_PX, doorY + doorH, door.color())
                    } else if (DungeonGrid.isMerged(pos, east)) {
                        // Same logical room on both sides of this gap — fill it so the room reads
                        // as one connected shape instead of two disconnected squares with a blank gap.
                        ctx.fill(x + ROOM_PX, y, x + ROOM_PX + DOOR_PX, y + ROOM_PX, tile.color())
                    }
                }
                if (tileZ < GRID_SIZE - 1) {
                    val south = pos.offset(0, 1)
                    val door = DungeonGrid.allDoors()[DoorKey(pos, false)]
                    if (door != null && door.color() != 0) {
                        val doorW = ROOM_PX / 2
                        val doorX = x + (ROOM_PX - doorW) / 2
                        ctx.fill(doorX, y + ROOM_PX, doorX + doorW, y + ROOM_PX + DOOR_PX, door.color())
                    } else if (DungeonGrid.isMerged(pos, south)) {
                        ctx.fill(x, y + ROOM_PX, x + ROOM_PX, y + ROOM_PX + DOOR_PX, tile.color())
                    }
                }
                // The center intersection point of a merged 2x2 (or bigger) room — where the east,
                // south, and southeast gap-fills above all meet — isn't covered by any of those
                // three rects individually, so it needs its own fill or it shows as a small hole.
                if (tileX < GRID_SIZE - 1 && tileZ < GRID_SIZE - 1) {
                    val east = pos.offset(1, 0)
                    val south = pos.offset(0, 1)
                    val southEast = pos.offset(1, 1)
                    if (DungeonGrid.isMerged(pos, east) && DungeonGrid.isMerged(pos, south) && DungeonGrid.isMerged(pos, southEast)) {
                        ctx.fill(x + ROOM_PX, y + ROOM_PX, x + ROOM_PX + DOOR_PX, y + ROOM_PX + DOOR_PX, tile.color())
                    }
                }
            }
        }

        // Labels are drawn in their own pass after every room/door fill so a later-iterated
        // neighboring room's fill can never paint over a previously-drawn label (this was the
        // "text getting cut off" bug when labels were drawn inline during the fill loop).
        for (job in labelJobs) {
            drawLabel(ctx, job.centerX, job.centerY, job.maxWidth, job.text, job.color, job.secretsText)
        }

        if (DungeonMapSettings.showPlayerMarkers) {
            val mc = MinecraftClient.getInstance()
            val selfEntry = if (mc.player != null && mc.networkHandler != null)
                mc.networkHandler!!.getPlayerListEntry(mc.player!!.uuid) else null
            for (marker in DungeonGrid.playerMarkers()) {
                val mx = baseX + (marker.tileX() * (ROOM_PX + DOOR_PX)).roundToInt()
                val my = baseY + (marker.tileZ() * (ROOM_PX + DOOR_PX)).roundToInt()
                if (marker.self()) {
                    if (selfEntry != null) drawPlayerHead(ctx, selfEntry, mx, my, DungeonMapSettings.selfMarkerColor)
                    else drawPlayerArrow(ctx, mx, my, marker.yaw(), DungeonMapSettings.selfMarkerColor)
                } else {
                    val entry = findTabEntry(mc, marker.name())
                    if (entry != null) drawPlayerHead(ctx, entry, mx, my, DungeonMapSettings.teammateMarkerColor)
                    else drawPlayerArrow(ctx, mx, my, marker.yaw(), DungeonMapSettings.teammateMarkerColor)
                }
            }
        }

        if (DungeonMapSettings.showSecretCounts) {
            val total = fishmod.features.dungeon.DungeonScore.getTotalSecrets()
            val found = fishmod.features.dungeon.DungeonScore.getSecretCount()
            if (total > 0) {
                val remaining = max(0, total - found)
                val font = MinecraftClient.getInstance().textRenderer
                if (font != null) {
                    val text = "Secrets left: $remaining"
                    val textWidth = font.getWidth(text)
                    ctx.drawText(font, Text.literal(text), baseX + (SIZE - textWidth) / 2, baseY + SIZE + 2, 0xffffffff.toInt(), true)
                }
            }
        }
    }

    /**
     * Matches a map decoration's name against the tab list to find that teammate's skin — best
     * effort, since Hypixel doesn't always populate the decoration name. Callers fall back to the
     * plain directional arrow when this returns null, so an unresolved name just looks like it did
     * before this feature existed rather than breaking anything.
     */
    private fun findTabEntry(mc: MinecraftClient, decorationName: String?): PlayerListEntry? {
        if (decorationName == null || mc.networkHandler == null) return null
        val stripped = decorationName.replace(Regex("§."), "").trim()
        if (stripped.isEmpty()) return null
        for (entry in mc.networkHandler!!.playerList) {
            if (entry.profile != null && stripped.equals(entry.profile.name, ignoreCase = true)) return entry
        }
        return null
    }

    /** An 8x8 skin head on a small colored backing square (green=self/white=teammate by default), matching Noamm's player-head map markers. */
    private fun drawPlayerHead(ctx: DrawContext, entry: PlayerListEntry, mx: Int, my: Int, borderColor: Int) {
        val size = 8
        ctx.fill(mx - size / 2 - 1, my - size / 2 - 1, mx + size / 2 + 1, my + size / 2 + 1, borderColor)
        try {
            PlayerSkinDrawer.draw(ctx, entry.skinTextures, mx - size / 2, my - size / 2, size)
        } catch (ignored: Exception) {
        }
    }

    /** A small rotated arrow pointing the direction the player is facing, matching Noamm's marker style. */
    private fun drawPlayerArrow(ctx: DrawContext, mx: Int, my: Int, yaw: Float, color: Int) {
        val stack = ctx.matrices
        stack.pushMatrix()
        stack.translate(mx.toFloat(), my.toFloat())
        stack.rotate(Math.toRadians(yaw.toDouble()).toFloat())
        ctx.fill(0, -4, 1, -3, color)
        ctx.fill(-1, -3, 2, -2, color)
        ctx.fill(-2, -2, 3, -1, color)
        ctx.fill(-2, -1, 3, 1, color)
        ctx.fill(-2, 1, 3, 2, color)
        stack.popMatrix()
    }

    /**
     * Splits the room evenly among however many candidates remain: a plain left/right half split
     * for 2, three equal vertical thirds for 3, and the 2x2 quadrant split only once there are
     * actually 4 candidates to give each its own corner. Previously this always rendered as
     * quadrants (alternating diagonally for 2, reusing a spare corner for 3), which read as "4
     * colors" on screen even when there were really only 2 or 3.
     */
    private fun drawWedges(ctx: DrawContext, x: Int, y: Int, size: Int, predicted: PredictedRoomTile) {
        val count = predicted.candidates().size
        when (count) {
            1 -> ctx.fill(x, y, x + size, y + size, predicted.colorAt(0))
            2 -> {
                val half = size / 2
                ctx.fill(x, y, x + half, y + size, predicted.colorAt(0))
                ctx.fill(x + half, y, x + size, y + size, predicted.colorAt(1))
            }
            3 -> {
                val third = size / 3
                ctx.fill(x, y, x + third, y + size, predicted.colorAt(0))
                ctx.fill(x + third, y, x + 2 * third, y + size, predicted.colorAt(1))
                ctx.fill(x + 2 * third, y, x + size, y + size, predicted.colorAt(2))
            }
            else -> {
                val half = size / 2
                val quadrantPos = arrayOf(intArrayOf(0, 0), intArrayOf(1, 0), intArrayOf(0, 1), intArrayOf(1, 1)) // TL, TR, BL, BR
                for (i in 0 until 4) {
                    val qx = x + quadrantPos[i][0] * half
                    val qy = y + quadrantPos[i][1] * half
                    ctx.fill(qx, qy, qx + half, qy + half, predicted.colorAt(i % count))
                }
            }
        }
    }

    /** Short type abbreviation shown on a room, matching the room's color rather than its exact
     *  design name — this feature only ever knows room TYPE (from map pixels), never the exact
     *  room (that needs a room-shape database this feature deliberately doesn't have). */
    private fun label(type: RoomType): String {
        return when (type) {
            RoomType.PUZZLE -> "P"
            RoomType.TRAP -> "T"
            RoomType.MINIBOSS -> "M"
            RoomType.BLOOD -> "B"
            RoomType.FAIRY -> "F"
            RoomType.ENTRANCE -> "E"
            RoomType.NORMAL, RoomType.RARE, RoomType.UNKNOWN -> ""
            else -> ""
        }
    }

    /** All grid cells belonging to the same logical room as `anchor`, in tile coordinates. */
    private fun boundingBox(anchor: RoomTile): IntArray {
        var minX = Int.MAX_VALUE
        var minZ = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxZ = Int.MIN_VALUE
        for (seg in DungeonGrid.segmentsOf(anchor)) {
            val p = seg.pos()
            minX = min(minX, p.x())
            maxX = max(maxX, p.x())
            minZ = min(minZ, p.z())
            maxZ = max(maxZ, p.z())
        }
        return intArrayOf(minX, minZ, maxX, maxZ)
    }

    /** Average tile position of the room's occupied cells. For an irregular shape like an L, the
     *  bounding box's geometric center falls over the missing corner cell; the centroid instead
     *  biases the label toward where the room actually is, matching Noamm/Odin's placement. */
    private fun centroidOf(anchor: RoomTile): FloatArray {
        val segments = DungeonGrid.segmentsOf(anchor)
        var sx = 0f
        var sz = 0f
        for (seg in segments) {
            sx += seg.pos().x()
            sz += seg.pos().z()
        }
        return floatArrayOf(sx / segments.size, sz / segments.size)
    }

    /** "N Secret(s)" for a room whose exact design (and therefore secret count) is known, or null otherwise. */
    private fun secretsLabel(room: RoomTile): String? {
        if (!DungeonMapSettings.showSecretCounts) return null
        val secrets = room.secrets()
        if (secrets < 0) return null
        return if (secrets == 1) "1 Secret" else "$secrets Secrets"
    }

    /** Centered on the room's centroid (not its raw bounding box — see [centroidOf]), scaled
     *  down to fit if the name is too wide. When a secrets line is known, the name shifts up half a
     *  line so the name+secrets pair reads as one centered block instead of the name alone sitting
     *  dead-center with secrets hanging off the bottom edge. */
    private fun drawLabel(ctx: DrawContext, centerX: Int, centerY: Int, maxWidth: Int, text: String, color: Int, secretsText: String?) {
        if (text.isEmpty()) return
        val font = MinecraftClient.getInstance().textRenderer ?: return
        val hasSecrets = !secretsText.isNullOrEmpty()
        val lineHeight = font.fontHeight
        val totalLines = if (hasSecrets) 2 else 1
        val topY = centerY - totalLines * lineHeight / 2f

        val textWidth = font.getWidth(text)
        val scale = if (textWidth > maxWidth && textWidth > 0) maxWidth.toFloat() / textWidth else 1f
        val stack = ctx.matrices
        stack.pushMatrix()
        stack.translate(centerX.toFloat(), topY)
        stack.scale(scale, scale)
        ctx.drawText(font, Text.literal(text), -textWidth / 2, 0, color, true)
        stack.popMatrix()

        if (hasSecrets) {
            val secretsWidth = font.getWidth(secretsText)
            val secretsScale = if (secretsWidth > maxWidth && secretsWidth > 0) maxWidth.toFloat() / secretsWidth else 1f
            val s2 = ctx.matrices
            s2.pushMatrix()
            s2.translate(centerX.toFloat(), topY + lineHeight)
            s2.scale(secretsScale, secretsScale)
            ctx.drawText(font, Text.literal(secretsText), -secretsWidth / 2, 0, 0xffffd700.toInt(), true)
            s2.popMatrix()
        }
    }

    /** Matches NoammAddons' text-color convention: the room fill always stays its type color, only the label reflects clear/fail state. */
    private fun labelColor(state: RoomState): Int {
        return when (state) {
            RoomState.CLEARED -> 0xff55ff55.toInt()
            RoomState.FAILED -> 0xffff5555.toInt()
            RoomState.PARTIAL -> 0xffffffff.toInt()
            else -> 0xffaaaaaa.toInt()
        }
    }

    /** For /fmdbg dungeonmap and the room-name/secret-count text overlay (Phase 1 leaves these text-only). */
    @JvmStatic
    fun describe(tile: RoomTile): String {
        return tile.type().toString() + " " + tile.state()
    }

    @JvmStatic
    fun describe(tile: DoorTile): String {
        return tile.type().toString()
    }
}
