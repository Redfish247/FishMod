package fishmod.features.dungeon.map

import fishmod.utils.config.values.DungeonMapSettings
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier

/** A discovered/inferred dungeon room. Ported 1:1 from System22's Room.java. */
class Room(
    var type: Type?,
    var shape: Shape?,
    var data: RoomData?,
    var height: Int?,
    var floorHeight: Int?
) {
    constructor(data: RoomData, height: Int, floorHeight: Int) : this(data.type, data.shape, data, height, floorHeight)

    val tiles: MutableList<Tile> = ArrayList()
    val places: MutableList<MapVec2i> = ArrayList()
    var state: State = State.UNDISCOVERED
    var clayPos: BlockPos? = null
    var rotation: Rotation = Rotation.NONE
    val doors: MutableSet<Door> = LinkedHashSet()
    var entryTile: MapVec2i? = null
    var isKnown1x1: Boolean = false
    var specialTile: Boolean = false
    var rushRoom: Boolean = false
    var mimic: Boolean = false
        private set

    fun setMimic(b: Boolean) {
        mimic = b
    }

    fun updateState(placement: MapVec2i, color: Int): StateUpdated? {
        if (state == State.GREEN && data != null && data!!.name == "Golden Oasis") {
            return null
        }

        val old = state
        when (color) {
            0 -> state = State.UNDISCOVERED
            18 -> {
                if (type == Type.BLOOD) {
                    Scan.setBlood(this)
                    state = State.DISCOVERED
                } else if (type == Type.PUZZLE) {
                    state = State.FAILED
                }
            }
            30 -> state = if (type == Type.ENTRANCE) State.DISCOVERED else State.GREEN
            34 -> state = State.CLEARED
            85, 119 -> {
                entryTile = placement
                specialTile = placement.x == SpecialColumn.column
                state = State.UNOPENED
            }
            else -> state = State.DISCOVERED
        }

        return if (state == old) null else StateUpdated(this, old, state)
    }

    private fun color(): IntArray {
        val legit = MapColors.legit()
        val dm = MapColors.darkenMultiplier()
        val s = DungeonMapSettings
        val mimicPeek = mimic && s.mapMimicOnInsight && MapColors.peeking() && !s.mapInsightLegit

        if (legit && state == State.UNOPENED && !mimicPeek) {
            if (isKnown1x1) {
                var seen = 0
                for (d in doors) if (d.seen) seen++
                if (seen == 1) return SpecialColumn.roomColorGuess(this)
            }
            return if (type == Type.BLOOD) intArrayOf(MapColors.darker(s.mapBloodRoomColor, dm)) else intArrayOf(s.mapUnopenedRoomColor)
        } else {
            val base: Int
            if (!mimic || (!s.mapRoomAdditionsMimic || legit) && !mimicPeek) {
                if (type == Type.UNKNOWN) return intArrayOf(s.mapUnopenedRoomColor)
                base = MapColors.roomColor(type!!)
            } else {
                base = s.mapMimicRoomColor
            }

            return if (state != State.UNDISCOVERED && state != State.UNOPENED) intArrayOf(base) else intArrayOf(MapColors.darker(base, dm))
        }
    }

    fun render(context: GuiGraphicsExtractor) {
        val legit = MapColors.legit()
        if (!legit || state != State.UNDISCOVERED) {
            val matrices = context.pose()
            if (legit && state == State.UNOPENED) {
                val e = entryTile
                if (e != null) {
                    matrices.pushMatrix()
                    matrices.translate(e.x * 20.0f, e.z * 20.0f)
                    val c = color()
                    when (c.size) {
                        1 -> context.fill(0, 0, 16, 16, c[0])
                        2 -> {
                            context.fill(0, 0, 16, 8, c[0])
                            context.fill(0, 8, 16, 16, c[1])
                        }
                        3 -> {
                            context.fill(0, 0, 16, 5, c[0])
                            context.fill(0, 0, 5, 10, c[0])
                            context.fill(10, 5, 16, 16, c[1])
                            context.fill(0, 10, 16, 16, c[1])
                            context.fill(5, 5, 11, 11, c[2])
                        }
                    }
                    matrices.popMatrix()
                }
            } else if (tiles.isNotEmpty()) {
                var topLeft = tiles[0].placement
                var bottomRight = tiles[0].placement
                var minKey = key(topLeft)
                var maxKey = key(bottomRight)

                for (t in tiles) {
                    val k = key(t.placement)
                    if (k < minKey) {
                        minKey = k
                        topLeft = t.placement
                    }
                    if (k > maxKey) {
                        maxKey = k
                        bottomRight = t.placement
                    }
                }

                val c = color()
                val rgb = c[0]
                if (shape == Shape.SL && tiles.size > 2) {
                    when (rotation.ordinal) {
                        0 -> {
                            context.fill(topLeft.x, topLeft.z, bottomRight.x + 16, topLeft.z + 16, rgb)
                            context.fill(bottomRight.x, topLeft.z, bottomRight.x + 16, bottomRight.z + 16, rgb)
                        }
                        1 -> {
                            context.fill(topLeft.x, topLeft.z, topLeft.x + 16, bottomRight.z + 16, rgb)
                            context.fill(topLeft.x, bottomRight.z, bottomRight.x + 16, bottomRight.z + 16, rgb)
                        }
                        2 -> {
                            context.fill(topLeft.x, topLeft.z, topLeft.x + 36, topLeft.z + 16, rgb)
                            context.fill(topLeft.x, topLeft.z, topLeft.x + 16, topLeft.z + 36, rgb)
                        }
                        3 -> {
                            context.fill(topLeft.x, topLeft.z, topLeft.x + 16, topLeft.z + 36, rgb)
                            context.fill(topLeft.x - 20, bottomRight.z, topLeft.x + 16, bottomRight.z + 16, rgb)
                        }
                        else -> context.fill(topLeft.x, topLeft.z, bottomRight.x + 16, bottomRight.z + 16, rgb)
                    }
                } else {
                    context.fill(topLeft.x, topLeft.z, bottomRight.x + 16, bottomRight.z + 16, rgb)
                }

                if (DungeonMapSettings.mapRoomAdditionsPrince && data != null && data!!.prince && !DungeonScore.princeKilled) {
                    matrices.pushMatrix()
                    matrices.translate(bottomRight.x + 9.0f, bottomRight.z + 10.0f)
                    matrices.scale(0.7f)
                    context.blit(RenderPipelines.GUI_TEXTURED, MapTextures.PRINCE_CROWN, 0, 0, 0.0f, 0.0f, 10, 10, 10, 10, -1)
                    matrices.popMatrix()
                }
            }
        }
    }

    fun renderName(context: GuiGraphicsExtractor, textFactor: Float) {
        val mc = Minecraft.getInstance()
        val matrices = context.pose()
        val fontHeight = 9
        val legit = MapColors.legit()
        val showName = (!legit || (state != State.UNDISCOVERED && state != State.UNOPENED)) && type != Type.FAIRY && data != null && data!!.name != null

        if (showName) {
            val splitName = data!!.name!!.split(" ")
            val showSecrets = DungeonMapSettings.mapShowRoomSecrets && data!!.secrets > 0
            val lineCount = splitName.size + (if (showSecrets) 1 else 0)
            val defaultHeight = 8.0f - fontHeight / (2.0f * textFactor) - ((lineCount - 1) / 2.0f * (fontHeight / textFactor)).toInt()
            val placement = textPlacement()

            for (index in splitName.indices) {
                matrices.pushMatrix()
                matrices.translate(placement.x + 8.0f, placement.z + index * (fontHeight / textFactor) + defaultHeight)
                matrices.scale(DungeonMapSettings.mapTextScaling)
                val color = when (state) {
                    State.GREEN -> -16711936
                    State.CLEARED -> -1
                    State.FAILED -> -65536
                    State.DISCOVERED -> -10197916
                    else -> -1
                }
                context.centeredText(mc.font, splitName[index], 0, 0, color)
                matrices.popMatrix()
            }

            if (showSecrets) {
                matrices.pushMatrix()
                matrices.translate(placement.x + 8.0f, placement.z + splitName.size * (fontHeight / textFactor) + defaultHeight)
                matrices.scale(DungeonMapSettings.mapTextScaling)
                context.centeredText(mc.font, "§e${data!!.secrets}§7s", 0, 0, -1)
                matrices.popMatrix()
            }
        } else {
            val resource: Identifier? = when (state) {
                State.GREEN -> MapTextures.GREEN_CHECK
                State.CLEARED -> MapTextures.WHITE_CHECK
                State.FAILED -> MapTextures.CROSS
                State.UNOPENED -> if (DungeonMapSettings.mapUglyQuestionMarks && (!isKnown1x1 || doors.size != 1) && type != Type.BLOOD) MapTextures.QUESTION else null
                else -> null
            }

            if (resource != null) {
                if (resource == MapTextures.QUESTION || type != Type.FAIRY) {
                    val placement: MapVec2i
                    if (state == State.UNOPENED) {
                        val e = entryTile ?: return
                        placement = e.multiply(20)
                    } else {
                        placement = textPlacement()
                    }

                    context.blit(RenderPipelines.GUI_TEXTURED, resource, placement.x + 2, placement.z + 2, 0.0f, 0.0f, 12, 12, 12, 12, -1)
                }
            }
        }
    }

    fun roomTile(pos: MapVec2i): Tile? {
        for (t in tiles) {
            if (pos == t.pos) return null
        }

        val tile = Tile(this, pos)
        tiles.add(tile)
        places.add(pos.add(185, 185).divide(32))
        val place = MapVec2i((pos.x + 185) / 32, (pos.z + 185) / 32)
        Scan.roomsList[place.roomListIndex()] = tile
        return tile
    }

    fun topLeftTilePlacement(): MapVec2i {
        var best = tiles[0]
        var bestKey = best.pos.x * 1000 + best.pos.z
        for (t in tiles) {
            val k = t.pos.x * 1000 + t.pos.z
            if (k < bestKey) {
                bestKey = k
                best = t
            }
        }
        return best.placement
    }

    fun textPlacement(): MapVec2i {
        if (rotation != Rotation.NONE && DungeonMapSettings.mapTextCenter) {
            if (shape == Shape.SL && tiles.size > 2) {
                var best = tiles[0]
                var bestKey = best.pos.x * 1000 + best.pos.z
                for (t in tiles) {
                    val k = t.pos.x * 1000 + t.pos.z
                    if (k < bestKey) {
                        bestKey = k
                        best = t
                    }
                }
                val topLeft = best.placement
                return when (rotation.ordinal) {
                    1, 3 -> topLeft.add(10, 20)
                    else -> topLeft.add(10, 0)
                }
            } else {
                var minX = Int.MAX_VALUE
                var maxX = Int.MIN_VALUE
                var minZ = Int.MAX_VALUE
                var maxZ = Int.MIN_VALUE

                for (t in tiles) {
                    val p = t.placement
                    minX = minOf(minX, p.x)
                    maxX = maxOf(maxX, p.x)
                    minZ = minOf(minZ, p.z)
                    maxZ = maxOf(maxZ, p.z)
                }

                return MapVec2i((minX + maxX) / 2, (minZ + maxZ) / 2)
            }
        } else {
            return topLeftTilePlacement()
        }
    }

    fun offset(blockPos: BlockPos): BlockPos? {
        val clay = clayPos ?: return null
        val rotated = rotateAroundNorth(blockPos, rotation)
        return rotated.offset(clay.x, 0, clay.z)
    }

    /** Room content centre in world coords (y is meaningless — pass your own on [local]). */
    val centerBlock: BlockPos?
        get() {
            if (tiles.isEmpty()) return null
            val xs = tiles.map { it.pos.x }
            val zs = tiles.map { it.pos.z }
            return BlockPos((xs.min() + xs.max()) / 2 + 15, 0, (zs.min() + zs.max()) / 2 + 15)
        }

    /** [rotation] as NoammAddons/ScanUtils-style degrees. */
    val rotationDegrees: Int
        get() = when (rotation) {
            Rotation.EAST -> 90
            Rotation.NORTH -> 180
            Rotation.WEST -> 270
            else -> 0
        }

    /** NoammAddons `ScanUtils.getRealCoord`: centre-relative, north-up [local] -> world. */
    @JvmOverloads
    fun realCoord(local: BlockPos, deg: Int = rotationDegrees): BlockPos? {
        val c = centerBlock ?: return null
        val x = local.x; val z = local.z
        val rx: Int; val rz: Int
        when (((deg % 360) + 360) % 360) {
            90 -> { rx = z; rz = -x }
            180 -> { rx = -x; rz = -z }
            270 -> { rx = -z; rz = x }
            else -> { rx = x; rz = z }
        }
        return BlockPos(c.x + rx, local.y, c.z + rz)
    }

    enum class Type {
        BLOOD, CHAMPION, ENTRANCE, FAIRY, NORMAL, PUZZLE, RARE, TRAP, UNKNOWN
    }

    enum class Shape(val str: String, val tileCount: Int) {
        UNKNOWN("Unknown", 0),
        SL("L", 3),
        S1x1("1x1", 1),
        S2x1("1x2", 2),
        S3x1("1x3", 3),
        S4x1("1x4", 4),
        S2x2("2x2", 4);

        companion object {
            @JvmStatic
            fun fromStr(s: String?): Shape? = entries.firstOrNull { it.str == s }
        }
    }

    enum class State {
        GREEN, CLEARED, FAILED, DISCOVERED, UNOPENED, UNDISCOVERED
    }

    enum class Rotation(val pos: MapVec2i) {
        NORTH(MapVec2i(15, 15)),
        SOUTH(MapVec2i(-15, -15)),
        WEST(MapVec2i(15, -15)),
        EAST(MapVec2i(-15, 15)),
        NONE(MapVec2i(0, 0))
    }

    class Tile(val owner: Room?, val pos: MapVec2i) {
        val placement: MapVec2i

        init {
            val x = (pos.x + 185) shr 5
            val z = (pos.z + 185) shr 5
            placement = MapVec2i(x * 20, z * 20)
        }

        val listIndex: Int
            get() = (pos.x + 185) / 32 * 6 + (pos.z + 185) / 32
    }

    class StateUpdated(val room: Room, val old: State, val neu: State)

    companion object {
        private fun key(p: MapVec2i): Int = p.x * 1000 + p.z

        @JvmStatic
        fun rotateAroundNorth(p: BlockPos, rot: Rotation): BlockPos {
            return when (rot.ordinal) {
                0 -> BlockPos(-p.x, p.y, -p.z)
                1 -> BlockPos(p.x, p.y, p.z)
                2 -> BlockPos(-p.z, p.y, p.x)
                3 -> BlockPos(p.z, p.y, -p.x)
                else -> p
            }
        }
    }
}
