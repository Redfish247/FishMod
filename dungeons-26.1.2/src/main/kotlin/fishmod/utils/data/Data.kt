package fishmod.utils.data

object Data {

    class DungeonData {
        @JvmField
        var classXp: MutableMap<String, Long> = HashMap()
    }

    @JvmField
    var dungeon: DungeonData = DungeonData()
}
