package fishmod.features.dungeon.map

object Prince {

    @JvmStatic
    var legitPrince = true
        private set

    @JvmStatic
    var cheaterPrince = true
        private set

    @JvmStatic
    fun reset() {
        legitPrince = true
        cheaterPrince = true
    }

    @JvmStatic
    fun updatePrince() {
        val princeRooms = ArrayList<Room>()
        for (r in ArrayList(Scan.rooms)) {
            val d = r.data
            if (d != null && d.prince) princeRooms.add(r)
        }

        if (princeRooms.isEmpty() && Scan.loadedAllRooms) {
            cheaterPrince = false
        }

        var revealed = 0
        for (r in princeRooms) {
            if (r.state != Room.State.UNDISCOVERED && r.state != Room.State.UNOPENED) revealed++
        }

        if (revealed <= 0) {
            var notVisible = 0
            for (r in ArrayList(Scan.rooms)) {
                if (r.type != Room.Type.BLOOD && (r.state == Room.State.UNDISCOVERED || r.state == Room.State.UNOPENED)) {
                    notVisible++
                }
            }
            if (Scan.loadedAllRooms && notVisible == 0) legitPrince = false
        }
    }
}
