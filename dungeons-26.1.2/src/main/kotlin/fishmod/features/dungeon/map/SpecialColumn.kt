package fishmod.features.dungeon.map

import fishmod.utils.config.values.DungeonMapSettings

object SpecialColumn {

    @JvmStatic
    var column = -1

    @JvmStatic
    var discoveredFullSpecialColumn = 0
        private set

    private var columnRoomCount = 0

    @JvmStatic
    var discovered1x1s = 0
        private set

    @JvmStatic
    val opened1x1s: MutableSet<Room> = LinkedHashSet()

    @JvmStatic
    fun reset() {
        column = -1
        discoveredFullSpecialColumn = 0
        discovered1x1s = 0
        columnRoomCount = 0
        opened1x1s.clear()
    }

    private fun puzzleCount() = DungeonScore.puzzleCount

    @JvmStatic
    fun updateSpecialColumn() {
        val ms = DungeonMap.getMapSize() ?: return
        for (x in 0 until ms.x) {
            for (z in 0 until ms.z) {
                val tile = Scan.roomsList[MapVec2i(x, z).roomListIndex()]
                val owner = tile.owner
                if (owner != null && owner.tiles.size == 1 && owner.type != Room.Type.BLOOD && owner.type != Room.Type.ENTRANCE) {
                    if (owner.state == Room.State.UNOPENED) {
                        if (x == column) {
                            if (!owner.isKnown1x1) {
                                owner.isKnown1x1 = true
                                discovered1x1s++
                                columnRoomCount++
                            }
                        } else {
                            val horizOk = (x == ms.x - 1 || x + 1 == column || checkRoom(x + 1, z)) && (x == 0 || checkRoom(x - 1, z))
                            val vertOk = (z == ms.z - 1 || checkRoom(x, z + 1)) && (z == 0 || checkRoom(x, z - 1))
                            if (horizOk && vertOk && !owner.isKnown1x1) {
                                owner.isKnown1x1 = true
                                discovered1x1s++
                            }
                        }
                    } else if (owner.state != Room.State.UNDISCOVERED &&
                        (owner.type == Room.Type.CHAMPION || owner.type == Room.Type.TRAP || owner.type == Room.Type.PUZZLE)) {
                        opened1x1s.add(owner)
                    }
                }
            }
        }

        if (discovered1x1s == puzzleCount() + 2) {
            discoveredFullSpecialColumn = ms.z
        } else if (column != -1) {
            var discovered = ms.z
            for (z in 0 until ms.z) {
                val tile = Scan.roomsList[MapVec2i(column - 1, z).roomListIndex()]
                val owner = tile.owner
                if (owner != null && !owner.isKnown1x1) {
                    val isHiddenColumnRoom = (owner.state == Room.State.UNOPENED && owner.type != Room.Type.BLOOD) ||
                        owner.state == Room.State.UNDISCOVERED
                    if (isHiddenColumnRoom) discovered--
                }
            }
            discoveredFullSpecialColumn = discovered
        }
    }

    private fun checkRoom(x: Int, z: Int): Boolean {
        val tile = Scan.roomsList[MapVec2i(x, z).roomListIndex()]
        val owner = tile.owner ?: return false
        if (owner.state == Room.State.UNDISCOVERED) return false
        return owner.state != Room.State.UNOPENED || MapVec2i(x, z) == owner.entryTile
    }

    @JvmStatic
    fun roomColorGuess(room: Room): IntArray {
        val s = DungeonMapSettings
        val dm = MapColors.darkenMultiplier()
        val puzzle = MapColors.darker(s.mapPuzzleRoomColor, dm)
        val trap = MapColors.darker(s.mapTrapRoomColor, dm)
        val champ = MapColors.darker(s.mapChampionRoomColor, dm)
        val specialColumnRoomCount = if (column != -1 && columnRoomCount == 0) 1 else columnRoomCount
        val ms = DungeonMap.getMapSize()
        val mapSizeZ = ms?.z ?: 6
        val pc = puzzleCount()

        if (room.specialTile) {
            val specialSize = mapSizeZ - discoveredFullSpecialColumn + specialColumnRoomCount
            return if (specialSize > pc && discovered1x1s - specialColumnRoomCount < 2) {
                if (anyOpened { r -> r.type == Room.Type.TRAP && !r.specialTile }) {
                    intArrayOf(puzzle)
                } else if (specialSize == pc + 1) {
                    intArrayOf(trap, puzzle)
                } else if (discovered1x1s - specialColumnRoomCount == 1) {
                    intArrayOf(trap, puzzle)
                } else {
                    if (anyOpened { r -> r.type == Room.Type.CHAMPION }) intArrayOf(champ, trap) else intArrayOf(champ, trap, puzzle)
                }
            } else {
                intArrayOf(puzzle)
            }
        } else {
            val nonspecialPuzzles = countOpened { r -> r.type == Room.Type.PUZZLE && !r.specialTile }
            val totalPuzzles = specialColumnRoomCount + nonspecialPuzzles
            if (totalPuzzles == pc) {
                return if (anyOpened { r -> r.type == Room.Type.TRAP }) {
                    intArrayOf(champ)
                } else {
                    if (anyOpened { r -> r.type == Room.Type.CHAMPION }) intArrayOf(trap) else intArrayOf(champ, trap)
                }
            } else if (!room.specialTile && specialColumnRoomCount == pc + 1) {
                return intArrayOf(champ)
            } else {
                val champOrTrap = countOpened { r -> r.type == Room.Type.CHAMPION || r.type == Room.Type.TRAP }
                if (champOrTrap == 2) return intArrayOf(puzzle)

                if (opened1x1s.size == pc + 1) {
                    if (noneOpened { r -> r.type == Room.Type.TRAP }) return intArrayOf(trap)
                    if (noneOpened { r -> r.type == Room.Type.CHAMPION }) return intArrayOf(champ)
                }

                val result = ArrayList<Int>()
                result.add(puzzle)
                if (noneOpened { r -> r.type == Room.Type.TRAP }) result.add(trap)
                if (noneOpened { r -> r.type == Room.Type.CHAMPION }) result.add(champ)
                return result.toIntArray()
            }
        }
    }

    private fun anyOpened(p: (Room) -> Boolean): Boolean = opened1x1s.any(p)
    private fun noneOpened(p: (Room) -> Boolean): Boolean = !anyOpened(p)
    private fun countOpened(p: (Room) -> Boolean): Int = opened1x1s.count(p)
}
