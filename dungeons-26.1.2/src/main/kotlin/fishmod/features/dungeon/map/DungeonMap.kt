package fishmod.features.dungeon.map

import fishmod.mixin.accessors.MapItemSavedDataAccessor
import fishmod.utils.config.values.DungeonMapSettings
import fishmod.utils.events.Events
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket
import net.minecraft.world.level.saveddata.maps.MapId

/** Reads the in-game dungeon map item (colors + decorations) and feeds Scan's room/door graph. */
object DungeonMap {

    private var mapId: MapId? = null
    private var mapCenter: MapVec2i? = null
    private var startCoords: MapVec2i? = null
    private var mapSize: MapVec2i? = null
    private var roomSize: Int? = null

    @JvmStatic
    fun getMapSize(): MapVec2i? = mapSize

    @JvmStatic
    fun getMapCenter(): MapVec2i? = mapCenter

    @JvmStatic
    fun getRoomSize(): Int? = roomSize

    // secretDisplay/doorEsp/puzzleOverlay* from Java's Settings aren't modeled in DungeonMapSettings
    // (doorEsp is the excluded ESP addon; the others are unrelated, separately-owned features).
    @JvmStatic
    fun anyFeatureEnabled(): Boolean {
        val s = DungeonMapSettings
        return s.mapEnabled || s.mapInfoEnabled == true || s.mapScoreMessages
    }

    @JvmStatic
    fun calculateMapSize(): MapVec2i = mapSize ?: MapVec2i(6, 6)

    @JvmStatic
    fun roomPlayerIn(): Room.Tile? {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return null
        val idx = MapVec2i(player.blockX, player.blockZ).index()
        return if (idx in Scan.roomsList.indices) Scan.roomsList[idx] else null
    }

    @JvmStatic
    fun init() {
        Events.ON_PACKET.register { packet ->
            if (packet is ClientboundMapItemDataPacket) {
                val mc = Minecraft.getInstance()
                mc.execute {
                    try {
                        rescanMapItem(packet)
                    } catch (t: Throwable) {
                    }
                }
            }
            false
        }
    }

    @JvmStatic
    fun reset() {
        mapId = null
        mapCenter = null
        startCoords = null
        mapSize = null
        roomSize = null
        Scan.reset()
        SpecialColumn.reset()
        Prince.reset()
        DungeonState.reset()
        DungeonPlayers.reset()
        DungeonScore.reset()
    }

    private fun rescanMapItem(packet: ClientboundMapItemDataPacket) {
        if (!DungeonState.isInDungeon() || !anyFeatureEnabled()) return
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return

        if (mapId == null) mapId = packet.mapId()
        if (mapId != packet.mapId()) return

        val state = level.getMapData(packet.mapId()) ?: return
        val colors = state.colors ?: return
        if (colors.isEmpty()) return

        if (startCoords == null && !initializeSizes(colors)) return

        DungeonPlayers.updateDecorations((state as MapItemSavedDataAccessor).decorations)
        if (!Scan.loadedAllRooms) updateRoomTiles(colors)

        updateRoomState(colors)
        scanDoors(colors)

        var bloodDoor: Door? = null
        for (d in Scan.doors) {
            if (d.type == Door.Type.BLOOD) {
                bloodDoor = d
                break
            }
        }

        if (bloodDoor != null) {
            var hasBloodRoom = false
            for (t in bloodDoor.rooms) {
                val r = t.owner
                if (r != null && r.type == Room.Type.BLOOD) {
                    hasBloodRoom = true
                    break
                }
            }

            if (!hasBloodRoom) {
                for (t in bloodDoor.rooms) {
                    val r = t.owner
                    if (r != null && r.doors.size == 1 && !r.rushRoom && r.shape == Room.Shape.S1x1) {
                        r.type = Room.Type.BLOOD
                        break
                    }
                }
            }
        }

        SpecialColumn.updateSpecialColumn()
        Prince.updatePrince()
    }

    private fun initializeSizes(colors: ByteArray): Boolean {
        var start = -1
        var length = 0
        var greenStart = -1
        var greenLength = 0

        for (i in colors.indices) {
            if (colors[i].toInt() == 30) {
                if (length++ == 0) start = i
            } else {
                if (length >= 16) {
                    greenStart = start
                    greenLength = length
                    break
                }
                length = 0
            }
        }

        if (greenStart == -1) {
            greenStart = start
            greenLength = length
        }

        if (greenLength != 16 && greenLength != 18) return false

        val floor = DungeonState.floorNumber()
        val sc: MapVec2i
        val center: MapVec2i
        val size: MapVec2i

        when (floor) {
            0 -> {
                sc = MapVec2i(22, 22)
                center = MapVec2i(-137, -137)
                size = MapVec2i(4, 4)
            }
            1 -> {
                sc = MapVec2i(22, 11)
                center = MapVec2i(-137, -121)
                size = MapVec2i(4, 5)
            }
            else -> {
                val s = MapVec2i((greenStart and 127) % (greenLength + 4), (greenStart shr 7) % (greenLength + 4))
                val extra = MapVec2i(if (s.x == 5) 1 else 0, if (s.z == 5) 1 else 0)
                size = MapVec2i(5, 5).add(extra)
                center = MapVec2i(-121, -121).add(MapVec2i(extra.x * 16, extra.z * 16))
                sc = s
            }
        }

        roomSize = greenLength
        startCoords = sc
        mapCenter = center
        mapSize = size

        if (((floor == 6 || floor == 5) && size.x == 6 && size.z == 6) || (floor == 4 && size.x == 6 && size.z == 5)) {
            SpecialColumn.column = 5
        }

        return true
    }

    private fun updateRoomTiles(colors: ByteArray) {
        val rs = roomSize ?: return
        val sc = startCoords ?: return
        val ms = mapSize ?: return
        val grid = Array(ms.x) { IntArray(ms.z) { -1 } }

        var roomIndex = 0
        for (i in 0 until ms.x) {
            for (j in 0 until ms.z) {
                val idx = MapVec2i(i, j).multiply(rs + 4).add(sc).mapIndex()
                if (colors.size > idx && colors[idx].toInt() != 0) {
                    grid[i][j] = roomIndex++
                }
            }
        }

        var changed: Boolean
        do {
            changed = false
            for (a in 0 until 5) {
                for (b in 0 until 6) {
                    val door = rs + 1 + a * (rs + 4)
                    val midRoom = b * (rs + 4) + 1
                    val next = a + 1

                    val doorIndex = sc.add(MapVec2i(door, midRoom)).mapIndex()
                    if (colors.size > doorIndex && next < grid.size && b < grid[next].size && grid[next][b] != grid[a][b] && colors[doorIndex].toInt() != 0) {
                        grid[next][b] = grid[a][b]
                        changed = true
                    }

                    val doorIndex2 = sc.add(MapVec2i(midRoom, door)).mapIndex()
                    if (colors.size > doorIndex2 && b < grid.size && next < grid[b].size && grid[b][next] != grid[b][a] && colors[doorIndex2].toInt() != 0) {
                        grid[b][next] = grid[b][a]
                        changed = true
                    }
                }
            }
        } while (changed)

        val uniques = ArrayList<Unique>()
        for (i in grid.indices) {
            for (j in grid[i].indices) {
                val id = grid[i][j]
                if (id != -1) {
                    var u = uniques.firstOrNull { it.id == id }
                    if (u == null) {
                        u = Unique(id)
                        uniques.add(u)
                    }
                    u.tiles.add(MapVec2i(i, j))
                }
            }
        }

        for (u in uniques) {
            val coords = u.tiles[0]
            val idx = coords.multiply(rs + 4).add(sc).mapIndex()
            if (colors.size <= idx) continue
            val color = colors[idx]

            val type = when (color.toInt()) {
                18 -> Room.Type.BLOOD
                30 -> Room.Type.ENTRANCE
                62 -> Room.Type.TRAP
                63 -> Room.Type.NORMAL
                66 -> Room.Type.PUZZLE
                74 -> Room.Type.CHAMPION
                82 -> Room.Type.FAIRY
                85 -> Room.Type.UNKNOWN
                else -> null
            } ?: continue

            val shape: Room.Shape = if (type == Room.Type.UNKNOWN) {
                Room.Shape.UNKNOWN
            } else {
                when (u.tiles.size) {
                    1 -> Room.Shape.S1x1
                    2 -> Room.Shape.S2x1
                    3 -> if (collinear(u.tiles)) Room.Shape.S3x1 else Room.Shape.SL
                    4 -> if (collinear(u.tiles)) Room.Shape.S4x1 else Room.Shape.S2x2
                    else -> Room.Shape.UNKNOWN
                }
            }

            var found: Room? = null
            for (tile in u.tiles) {
                val existing = Scan.roomsList[tile.roomListIndex()]
                if (existing.owner != null) {
                    found = existing.owner
                    break
                }
            }

            if (found != null) {
                if ((type != Room.Type.UNKNOWN && found.type == Room.Type.UNKNOWN) || (shape != Room.Shape.UNKNOWN && found.shape == Room.Shape.UNKNOWN)) {
                    found.type = type
                    found.shape = shape
                }

                var strays: MutableSet<Room>? = null
                for (tile in u.tiles) {
                    val owner = Scan.roomsList[tile.roomListIndex()].owner
                    if (owner != null && owner !== found) {
                        if (strays == null) strays = LinkedHashSet()
                        strays.add(owner)
                    }
                }

                for (tile in u.tiles) {
                    found.roomTile(tile.multiply(32).add(-185, -185))
                }

                if (strays != null) absorbStrayRooms(found, strays)
                if (type != Room.Type.UNKNOWN) Scan.updateRotationOffShape(found)
            } else {
                val room = Room(type, shape, null, null, null)
                Scan.rooms.add(room)
                for (tile in u.tiles) {
                    room.roomTile(tile.multiply(32).add(-185, -185))
                }
                if (type != Room.Type.UNKNOWN) Scan.updateRotationOffShape(room)
            }
        }
    }

    private fun collinear(tiles: List<MapVec2i>): Boolean {
        val a = tiles[0]
        val b = tiles[1]
        val c = tiles[2]
        return (a.x == b.x && a.x == c.x) || (a.z == b.z && a.z == c.z)
    }

    private fun absorbStrayRooms(found: Room, strays: Set<Room>) {
        for (stray in strays) {
            Scan.rooms.remove(stray)
            found.doors.addAll(stray.doors)
        }

        for (door in ArrayList(Scan.doors)) {
            var refers = false
            for (dt in door.rooms) {
                if (strays.contains(dt.owner)) {
                    refers = true
                    break
                }
            }
            if (!refers) continue

            val fixed = ArrayList<Room.Tile>()
            for (dt in door.rooms) {
                if (!strays.contains(dt.owner)) {
                    fixed.add(dt)
                } else {
                    val repl = found.tiles.firstOrNull { it.pos == dt.pos }
                    if (repl != null && !fixed.contains(repl)) fixed.add(repl)
                }
            }

            door.rooms.clear()
            door.rooms.addAll(fixed)
        }
    }

    private fun updateRoomState(colors: ByteArray): List<Room.StateUpdated> {
        val updated = ArrayList<Room.StateUpdated>()
        val rs = roomSize ?: return updated
        val startSc = startCoords ?: return updated
        val sc = startSc.add(rs / 2, rs / 2)
        val tile = rs + 4

        for (room in ArrayList(Scan.rooms)) {
            if (room.places.isEmpty()) continue

            var topLeft = room.places[0]
            var bestKey = key(topLeft)
            for (p in room.places) {
                val k = key(p)
                if (k < bestKey) {
                    bestKey = k
                    topLeft = p
                }
            }

            var placement = topLeft
            var color = colorAt(colors, sc, tile, topLeft)
            if (color.toInt() == 0) {
                for (p in room.places) {
                    val c2 = colorAt(colors, sc, tile, p)
                    if (c2.toInt() != 0) {
                        placement = p
                        color = c2
                        break
                    }
                }
            }

            val u = room.updateState(placement, color.toInt())
            if (u != null) updated.add(u)
        }

        return updated
    }

    private fun colorAt(colors: ByteArray, sc: MapVec2i, tile: Int, placement: MapVec2i): Byte {
        val idx = sc.add(placement.multiply(tile)).mapIndex()
        return if (colors.size <= idx) 0 else colors[idx]
    }

    private fun scanDoors(colors: ByteArray) {
        val rs = roomSize ?: return
        val hrs = rs / 2
        val startSc = startCoords ?: return
        val sc = startSc.add(MapVec2i(hrs, hrs))

        for (a in 0 until 5) {
            for (b in 0 until 6) {
                val door = hrs + a * (rs + 4)
                val midRoom = b * (rs + 4)
                val coordsDoor = Scan.topLeftRoom.x + 16 + a * 32
                val coordsMidRoom = Scan.topLeftRoom.z + b * 32

                val doorIndex = sc.add(MapVec2i(door, midRoom)).mapIndex()
                val roomIndex = sc.add(MapVec2i(door, midRoom - hrs + 1)).mapIndex()
                if (colors.size > doorIndex && roomIndex in colors.indices && colors[roomIndex].toInt() == 0) {
                    handleDoor(MapVec2i(coordsDoor, coordsMidRoom), MapVec2i(a, b), MapVec2i(1, 0), colors[doorIndex])
                }

                val doorIndex2 = sc.add(MapVec2i(midRoom, door)).mapIndex()
                val roomIndex2 = sc.add(MapVec2i(midRoom - hrs + 1, door)).mapIndex()
                if (colors.size > doorIndex2 && roomIndex2 in colors.indices && colors[roomIndex2].toInt() == 0) {
                    handleDoor(MapVec2i(coordsMidRoom, coordsDoor), MapVec2i(b, a), MapVec2i(0, 1), colors[doorIndex2])
                }
            }
        }
    }

    private fun handleDoor(pos: MapVec2i, place: MapVec2i, offset: MapVec2i, color: Byte) {
        val type = when (color.toInt()) {
            0 -> null
            18 -> Door.Type.BLOOD
            62, 63, 66, 74, 85 -> Door.Type.NORMAL
            82, 119 -> Door.Type.WITHER
            else -> null
        } ?: return

        val i1 = place.roomListIndex()
        val i2 = place.add(offset).roomListIndex()
        val rooms = ArrayList<Room.Tile>()
        if (i1 in Scan.roomsList.indices && Scan.roomsList[i1].owner != null) rooms.add(Scan.roomsList[i1])
        if (i2 in Scan.roomsList.indices && Scan.roomsList[i2].owner != null) rooms.add(Scan.roomsList[i2])

        var door = Scan.doors.firstOrNull { it.pos == pos }
        if (door == null) {
            door = Door(pos, type, rooms)
            Scan.doors.add(door)
        }

        if (type == Door.Type.WITHER && door.type != Door.Type.WITHER) {
            door.type = Door.Type.WITHER
            door.locked = true
        }

        if (type == Door.Type.WITHER || type == Door.Type.BLOOD) {
            for (t in door.rooms) {
                t.owner?.rushRoom = true
            }
        }

        if (type == Door.Type.NORMAL || color.toInt() == 82) {
            door.locked = false
        }

        if (color.toInt() == 18) {
            door.locked = !DungeonState.bloodOpened
        }
    }

    private fun key(p: MapVec2i): Int = p.x * 1000 + p.z

    private class Unique(val id: Int) {
        val tiles: MutableList<MapVec2i> = ArrayList()
    }
}
