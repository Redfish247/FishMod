package fishmod.features.dungeon.map

import fishmod.utils.config.values.DungeonMapSettings
import net.minecraft.client.gui.GuiGraphicsExtractor

class Door(val pos: MapVec2i, type: Type, val rooms: MutableList<Room.Tile>) {

    enum class Type { BLOOD, NORMAL, WITHER }

    var type: Type = type
    var locked: Boolean = type == Type.WITHER || type == Type.BLOOD

    init {
        for (t in rooms) {
            t.owner?.doors?.add(this)
        }
    }

    val seen: Boolean
        get() {
            for (t in rooms) {
                val r = t.owner
                if (r != null && r.state != Room.State.UNDISCOVERED && r.state != Room.State.UNOPENED) return true
            }
            return false
        }

    private fun placement(): FloatArray {
        val thickness = DungeonMapSettings.mapDoorThickness
        val x = (pos.x + 185) shr 4
        val z = (pos.z + 185) shr 4
        val xEven = x % 2
        val zEven = z % 2
        val t = (16.0f - thickness) / 2.0f
        val xOff = (x shr 1) * 20.0f + xEven * 16.0f + (xEven xor 1) * t
        val yOff = (z shr 1) * 20.0f + zEven * 16.0f + (zEven xor 1) * t
        return floatArrayOf(xOff, yOff)
    }

    private fun size(): MapVec2i {
        val thickness = DungeonMapSettings.mapDoorThickness.toInt()
        val xOffset = ((pos.x + 185) shr 4) % 2
        val zOffset = ((pos.z + 185) shr 4) % 2
        return MapVec2i((xOffset xor 1) * thickness + xOffset * 4, (zOffset xor 1) * thickness + zOffset * 4)
    }

    fun render(context: GuiGraphicsExtractor) {
        val size = size()
        if (size.x != 0 || size.z != 0) {
            val p = placement()
            val matrices = context.pose()
            matrices.pushMatrix()
            matrices.translate(p[0], p[1])
            context.fill(0, 0, size.x, size.z, color())
            matrices.popMatrix()
        }
    }

    private fun color(): Int {
        var noInfo = false
        for (t in rooms) {
            val r = t.owner
            if (r != null && (r.state == Room.State.UNDISCOVERED || r.state == Room.State.UNOPENED)) {
                noInfo = true
                break
            }
        }

        val legit = MapColors.legit()
        val dm = MapColors.darkenMultiplier()
        val s = DungeonMapSettings
        if (legit && noInfo) {
            return when (type) {
                Type.BLOOD -> if (locked) MapColors.darker(s.mapBloodDoorColor, dm) else s.mapBloodDoorColor
                Type.NORMAL -> s.mapUnopenedDoorColor
                Type.WITHER -> if (locked) MapColors.darker(s.mapWitherDoorColor, dm) else s.mapWitherDoorColor
            }
        } else {
            val color = cheatingColor()
            return if (noInfo) MapColors.darker(color, dm) else color
        }
    }

    private fun cheatingColor(): Int {
        val s = DungeonMapSettings
        return when (type) {
            Type.BLOOD -> s.mapBloodDoorColor
            Type.NORMAL -> {
                if (DungeonMapSettings.mapDoorGay) {
                    s.mapNormalDoorColor
                } else {
                    val t2 = firstOwnerType { t -> t != Room.Type.NORMAL && t != Room.Type.FAIRY }
                    if (t2 == null) {
                        s.mapNormalDoorColor
                    } else {
                        when (t2) {
                            Room.Type.ENTRANCE -> s.mapEntranceDoorColor
                            Room.Type.BLOOD -> s.mapBloodDoorColor
                            Room.Type.CHAMPION -> s.mapChampionDoorColor
                            Room.Type.FAIRY -> s.mapFairyDoorColor
                            Room.Type.PUZZLE -> s.mapPuzzleDoorColor
                            Room.Type.RARE -> s.mapRareDoorColor
                            Room.Type.TRAP -> s.mapTrapDoorColor
                            Room.Type.NORMAL, Room.Type.UNKNOWN -> s.mapNormalDoorColor
                            else -> s.mapNormalDoorColor
                        }
                    }
                }
            }
            Type.WITHER -> {
                if (locked) {
                    s.mapWitherDoorColor
                } else {
                    val found = firstOwnerType { t -> t != Room.Type.NORMAL }
                    if (found == Room.Type.FAIRY) s.mapFairyDoorColor else s.mapNormalDoorColor
                }
            }
        }
    }

    private fun firstOwnerType(pred: (Room.Type) -> Boolean): Room.Type? {
        for (t in rooms) {
            val type = t.owner?.type
            if (type != null && pred(type)) return type
        }
        return null
    }
}
