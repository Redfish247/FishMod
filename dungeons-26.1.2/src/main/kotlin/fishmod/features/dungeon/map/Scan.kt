package fishmod.features.dungeon.map

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.chunk.LevelChunk
import java.util.Collections
import java.util.LinkedHashSet

/** World-scan half of the dungeon map feature: identifies rooms via their block "core" hash and infers doors/rotations. */
object Scan {

    @JvmField
    val topLeftRoom: MapVec2i = MapVec2i(-185, -185)

    @JvmField
    var roomsList: Array<Room.Tile> = newRoomsList()

    @JvmField
    val rooms: MutableSet<Room> = Collections.synchronizedSet(LinkedHashSet())

    @JvmField
    val doors: MutableList<Door> = Collections.synchronizedList(ArrayList())

    @JvmField
    var loadedAllRooms: Boolean = false

    @JvmField
    var blood: Room? = null

    @JvmField
    var allSecrets: Int = 0

    @JvmField
    var puzzles: MutableList<Room> = ArrayList()

    @JvmField
    var chest: BlockPos? = null

    private var shouldScan = false
    private var lastScanMs = 0L
    private const val MIN_SCAN_INTERVAL_MS = 250L
    private val BLACKLISTED = arrayOf(Blocks.CHEST, Blocks.TRAPPED_CHEST)

    @JvmStatic
    fun setBlood(r: Room?) {
        blood = r
    }

    @JvmStatic
    fun setShouldScan(b: Boolean) {
        shouldScan = b
    }

    @JvmStatic
    fun register() {
        ClientChunkEvents.CHUNK_LOAD.register(ClientChunkEvents.Load { _, _ ->
            // once every tile is identified the world scan is fixed (wither unlocks come via the map packet), so stop re-arming
            if (!loadedAllRooms && DungeonState.isInDungeon()) shouldScan = true
        })
        ClientTickEvents.END_LEVEL_TICK.register(ClientTickEvents.EndLevelTick { world ->
            if (shouldScan) {
                val now = System.currentTimeMillis()
                if (now - lastScanMs >= MIN_SCAN_INTERVAL_MS) {
                    shouldScan = false
                    lastScanMs = now
                    try {
                        scan(world)
                    } catch (t: Throwable) {
                    }
                }
            }
        })
    }

    private fun newRoomsList(): Array<Room.Tile> {
        return Array(36) { i ->
            val x = i / 6
            val z = i % 6
            Room.Tile(null, topLeftRoom.add(x * 32, z * 32))
        }
    }

    @JvmStatic
    fun reset() {
        rooms.clear()
        doors.clear()
        roomsList = newRoomsList()
        loadedAllRooms = false
        blood = null
        allSecrets = 0
        puzzles = ArrayList()
        chest = null
        shouldScan = false
        lastScanMs = 0L
    }

    @JvmStatic
    fun scan(world: ClientLevel) {
        if (!DungeonState.isInDungeon()) return
        if (!DungeonMap.anyFeatureEnabled()) return

        if (!RoomData.isLoaded()) RoomData.loadRoomData()

        if (loadedAllRooms) {
            scanWorldDoors(world)
            recomputeSecrets()
        } else {
            scanRooms(world)
            val puz = ArrayList<Room>()
            for (r in ArrayList(rooms)) {
                if (r.type == Room.Type.PUZZLE) puz.add(r)
            }
            puzzles = puz

            scanRoomRotations(world)
            scanWorldDoors(world)

            val ms = DungeonMap.getMapSize()
            if (ms != null) {
                val zMax = if (SpecialColumn.column != -1) ms.z - 1 else ms.z
                var all = true

                outer@ for (x in 0 until ms.x) {
                    for (z in 0 until zMax) {
                        val t = roomsList[MapVec2i(x, z).roomListIndex()]
                        val owner = t.owner
                        if (owner == null || owner.data == null || owner.rotation == Room.Rotation.NONE) {
                            all = false
                            break@outer
                        }
                    }
                }

                loadedAllRooms = all
            }

            recomputeSecrets()
            Prince.updatePrince()
        }
    }

    private fun recomputeSecrets() {
        var total = 0
        for (r in ArrayList(rooms)) {
            val d = r.data
            if (d != null) total += d.secrets
        }
        allSecrets = total
    }

    private fun getTopY(chunk: LevelChunk, pos: MapVec2i): Int? {
        var height = 0
        for (y in 160 downTo 11) {
            val block = chunk.getBlockState(BlockPos(pos.x and 15, y, pos.z and 15)).block
            if (block === Blocks.VOID_AIR) return null
            if (block !== Blocks.AIR) {
                height = y
                break
            }
        }
        return height
    }

    private fun getBottomY(chunk: LevelChunk, pos: MapVec2i): Int? {
        for (y in 0..160) {
            val block = chunk.getBlockState(BlockPos(pos.x and 15, y, pos.z and 15)).block
            if (block !== Blocks.VOID_AIR && block !== Blocks.AIR) return y
        }
        return null
    }

    private fun calculateCore(chunk: LevelChunk, pos: MapVec2i): IntArray? {
        val sb = StringBuilder(150)
        val top = getTopY(chunk, pos) ?: return null
        val scanHeight = top.coerceIn(11, 140)
        sb.append(140 - scanHeight)
        var bedrock = 0

        for (y in scanHeight downTo 12) {
            val block = chunk.getBlockState(BlockPos(pos.x and 15, y, pos.z and 15)).block
            if (bedrock >= 2 && block === Blocks.AIR) {
                val n = y - 11
                sb.append("a".repeat(maxOf(n, 0)))
            }

            if (block === Blocks.BEDROCK) {
                bedrock++
            } else {
                bedrock = 0
                var black = false
                for (b in BLACKLISTED) {
                    if (b === block) {
                        black = true
                        break
                    }
                }
                if (black) continue
            }

            val path = BuiltInRegistries.BLOCK.getKey(block).path
            if (path.isNotEmpty()) sb.append(path[0].lowercaseChar())
        }

        return intArrayOf(sb.toString().hashCode(), top)
    }

    private fun scanRooms(world: ClientLevel) {
        for (x in 0 until 6) {
            for (z in 0 until 6) {
                val place = MapVec2i(x, z)

                // don't re-hash a fully-identified tile: a transient bad core read was flipping resolved rooms and resetting puzzle solvers
                val resolved = roomsList[place.roomListIndex()].owner
                if (resolved != null && resolved.data != null && resolved.rotation != Room.Rotation.NONE) continue

                val curr = topLeftRoom.add(MapVec2i(x, z).multiply(32))
                val chunk = world.getChunk(curr.x shr 4, curr.z shr 4)

                val core = calculateCore(chunk, curr) ?: continue
                val coreHash = core[0]
                val height = core[1]
                if (coreHash == 48696) continue

                val rd = RoomData.getRoomData(coreHash)
                if (fishmod.utils.debug.Debug.roomCores) {
                    fishmod.utils.Misc.addChatMessage(net.minecraft.network.chat.Component.literal(
                        "§b[roomCore] §7cell ($x,$z) §fhash=§e$coreHash §7-> §f${rd?.name ?: "§cUNKNOWN"}"))
                }
                if (rd == null) continue

                var found: Room? = null
                for (r in ArrayList(rooms)) {
                    val d = r.data
                    if (d != null && rd.name == d.name) {
                        found = r
                        break
                    }
                }

                if (found != null) {
                    var already = false
                    for (t in found.tiles) {
                        if (t.pos == curr) {
                            already = true
                            break
                        }
                    }
                    if (already) continue

                    // cap tiles at the room's shape: a bad far-cell core read hashing to a known name was welding on a stray tile
                    val cap = (found.shape ?: rd.shape)?.tileCount ?: 0
                    if (cap > 0 && found.tiles.size >= cap) continue
                }

                if (getBottomY(chunk, curr) == null) continue
                val mapItemRoom = roomsList[place.roomListIndex()].owner

                if (mapItemRoom != null) {
                    if (mapItemRoom != found) {
                        if (found != null && mapItemRoom.tiles.size == 1) {
                            rooms.remove(mapItemRoom)
                            found.doors.addAll(mapItemRoom.doors)
                            val tile = found.roomTile(curr)
                            val old = mapItemRoom

                            for (door in ArrayList(doors)) {
                                var refersOld = false
                                for (dt in door.rooms) {
                                    if (dt.owner === old) {
                                        refersOld = true
                                        break
                                    }
                                }

                                if (refersOld) {
                                    val newRooms = door.rooms.filterNot { it.owner === old }.toMutableList()
                                    if (tile != null) newRooms.add(tile)
                                    replaceDoorRooms(door, newRooms)
                                }
                            }
                        } else {
                            mapItemRoom.data = rd
                            mapItemRoom.type = rd.type
                            mapItemRoom.shape = rd.shape
                            mapItemRoom.height = height
                        }
                    }
                } else {
                    var actualFound = found
                    if (actualFound == null) {
                        actualFound = Room(rd, height)
                        rooms.add(actualFound)
                    }
                    actualFound.roomTile(curr)
                }
            }
        }
    }

    private fun replaceDoorRooms(door: Door, newRooms: List<Room.Tile>) {
        door.rooms.clear()
        door.rooms.addAll(newRooms)
    }

    private fun scanWorldDoors(world: ClientLevel) {
        for (a in 0 until 6) {
            for (b in 0 until 5) {
                val goingRight = MapVec2i(topLeftRoom.x + a * 32, topLeftRoom.z + 16 + 32 * b)
                handleWorldDoor(world, goingRight, roomsList[MapVec2i(a, b).roomListIndex()], roomsList[MapVec2i(a, b + 1).roomListIndex()])
                val goingDown = MapVec2i(goingRight.z, goingRight.x)
                handleWorldDoor(world, goingDown, roomsList[MapVec2i(b, a).roomListIndex()], roomsList[MapVec2i(b + 1, a).roomListIndex()])
            }
        }
    }

    private fun handleWorldDoor(world: ClientLevel, pos: MapVec2i, t1: Room.Tile, t2: Room.Tile) {
        val r1 = t1.owner
        val r2 = t2.owner
        if (r1 == null || r2 == null) return

        for (d in ArrayList(doors)) {
            if (pos == d.pos) return
        }

        val chunk = world.getChunk(pos.x shr 4, pos.z shr 4)
        val top = getTopY(chunk, pos) ?: return
        val height = top
        // Door.rooms is later mutated in place (room-merge rewiring), so it must be a real ArrayList, not listOf()
        val tiles = arrayListOf(t1, t2)

        if (height != 73 && height != 81) {
            if (height > 73 && (r1.type == Room.Type.ENTRANCE || r2.type == Room.Type.ENTRANCE)) {
                doors.add(Door(pos, Door.Type.NORMAL, tiles))
            }
        } else {
            val block = world.getBlockState(BlockPos(pos.x, 69, pos.z)).block
            val type: Door.Type
            if (block === Blocks.COAL_BLOCK) {
                r1.rushRoom = true
                r2.rushRoom = true
                type = Door.Type.WITHER
            } else if (block === Blocks.RED_TERRACOTTA) {
                type = Door.Type.BLOOD
            } else {
                type = Door.Type.NORMAL
            }
            doors.add(Door(pos, type, tiles))
        }
    }

    private fun scanRoomRotations(level: ClientLevel) {
        for (room in ArrayList(rooms)) {
            if (room.data != null && room.rotation == Room.Rotation.NONE) {
                updateRotation(level, room)
            }
        }
    }

    @JvmStatic
    fun updateRotationOffShape(room: Room): Boolean {
        val shape = room.shape ?: return false
        if (shape == Room.Shape.S1x1 || room.tiles.size != shape.tileCount) return false

        var topLeft: Room.Tile? = null
        var bottomRight: Room.Tile? = null
        var minKey = Int.MAX_VALUE
        var maxKey = Int.MIN_VALUE

        for (t in room.tiles) {
            val k = t.pos.x * 1000 + t.pos.z
            if (k < minKey) {
                minKey = k
                topLeft = t
            }
            if (k > maxKey) {
                maxKey = k
                bottomRight = t
            }
        }

        if (topLeft == null || bottomRight == null) return false

        if (shape == Room.Shape.SL) {
            var other: Room.Tile? = null
            for (t in room.tiles) {
                if (t !== topLeft && t !== bottomRight) {
                    other = t
                    break
                }
            }
            if (other == null) return false

            if (topLeft.pos.x == bottomRight.pos.x) {
                room.clayPos = BlockPos(other.pos.x - 15, 0, topLeft.pos.z + 15)
                room.rotation = Room.Rotation.EAST
            } else if (topLeft.pos.z == bottomRight.pos.z) {
                room.clayPos = BlockPos(bottomRight.pos.x + 15, 0, bottomRight.pos.z - 15)
                room.rotation = Room.Rotation.WEST
            } else if (other.pos.x == topLeft.pos.x) {
                room.clayPos = BlockPos(topLeft.pos.x - 15, 0, topLeft.pos.z - 15)
                room.rotation = Room.Rotation.SOUTH
            } else {
                room.clayPos = BlockPos(bottomRight.pos.x + 15, 0, bottomRight.pos.z + 15)
                room.rotation = Room.Rotation.NORTH
            }
        } else if (topLeft.pos.x == bottomRight.pos.x) {
            room.clayPos = BlockPos(topLeft.pos.x + 15, 0, topLeft.pos.z - 15)
            room.rotation = Room.Rotation.WEST
        } else {
            room.clayPos = BlockPos(topLeft.pos.x - 15, 0, topLeft.pos.z - 15)
            room.rotation = Room.Rotation.SOUTH
        }

        return true
    }

    private fun updateRotation(level: ClientLevel, room: Room): Boolean {
        if (room.data == null) return false
        if (updateRotationOffShape(room)) return true
        val height = room.height ?: return false

        if (room.data?.name == "Fairy") {
            if (room.tiles.isEmpty()) return false
            val tile = room.tiles[0]
            room.clayPos = BlockPos(tile.pos.x - 15, height, tile.pos.z - 15)
            room.rotation = Room.Rotation.SOUTH
            return true
        }

        if (room.shape == Room.Shape.S4x1 && room.tiles.size != 4) return false

        val candidates = arrayOf(Room.Rotation.NORTH, Room.Rotation.SOUTH, Room.Rotation.WEST, Room.Rotation.EAST)

        for (rotation in candidates) {
            var clay: BlockPos? = null

            for (tile in room.tiles) {
                val bp = BlockPos(tile.pos.x + rotation.pos.x, height, tile.pos.z + rotation.pos.z)
                if (level.getBlockState(bp).block === Blocks.BLUE_TERRACOTTA) {
                    if (room.tiles.size == 1) {
                        clay = bp
                        break
                    }

                    var ok = true
                    for (facing in Direction.Plane.HORIZONTAL) {
                        val dx = if (facing.axis == Direction.Axis.X) facing.stepX else 0
                        val dz = if (facing.axis == Direction.Axis.Z) facing.stepZ else 0
                        val nb = level.getBlockState(bp.offset(dx, 0, dz)).block
                        if (nb !== Blocks.AIR && nb !== Blocks.BLUE_TERRACOTTA) {
                            ok = false
                            break
                        }
                    }

                    if (ok) {
                        clay = bp
                        break
                    }
                }
            }

            if (clay != null) {
                room.clayPos = clay
                room.rotation = rotation
                return true
            }
        }

        room.rotation = Room.Rotation.NONE
        return false
    }
}
