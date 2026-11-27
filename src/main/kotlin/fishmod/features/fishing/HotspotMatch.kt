package fishmod.features.fishing

object HotspotMatch {
    const val MAX_STEP = 3.0
    const val NEW_TRAIL_GAP_MS = 1500L
    const val MIN_POINTS = 6
    const val SETTLED_MS = 600L
    const val MAX_POINTS = 256
    const val SURFACE_SCAN = 40
    // A trail point this close to an earlier one is a repeat Hypixel sends (SkyHanni: 0.01 blocks)
    const val REPEAT_TOL = 0.01
    const val MAX_GUESS_DIST = 150.0
    // Radar re-use inside this many ms of the last flame particle is cancelled (SkyHanni does the same, 0.2 s)
    const val REUSE_GUARD_MS = 200L

    fun interface FluidProbe { fun isFluid(x: Double, y: Double, z: Double): Boolean }

    fun surfaceY(probe: FluidProbe?, x: Double, startY: Double, z: Double): Double? {
        if (probe == null) return null
        val top = Math.floor(startY)
        for (i in 0..SURFACE_SCAN) if (probe.isFluid(x, top - i, z)) return top - i + 0.9
        return null
    }

    fun sane(gx: Double, gy: Double, gz: Double, ex: Double, ez: Double, minY: Double, maxY: Double): Boolean {
        if (gx.isNaN() || gy.isNaN() || gz.isNaN() || gx.isInfinite() || gy.isInfinite() || gz.isInfinite()) return false
        if (gy < minY || gy > maxY) return false
        val dx = gx - ex; val dz = gz - ez
        return dx * dx + dz * dz <= MAX_GUESS_DIST * MAX_GUESS_DIST
    }

    class Trail {
        private val xs = DoubleArray(MAX_POINTS)
        private val ys = DoubleArray(MAX_POINTS)
        private val zs = DoubleArray(MAX_POINTS)
        var size = 0; private set
        var lastMs = 0L; private set

        fun clear() { size = 0 }

        fun add(x: Double, y: Double, z: Double, nowMs: Long): Boolean {
            if (size > 0 && nowMs - lastMs > NEW_TRAIL_GAP_MS) size = 0
            if (size > 0) {
                val dx = x - xs[size - 1]; val dy = y - ys[size - 1]; val dz = z - zs[size - 1]
                if (dx * dx + dy * dy + dz * dz > MAX_STEP * MAX_STEP) return false
            }
            for (i in 0 until size) {
                val dx = x - xs[i]; val dy = y - ys[i]; val dz = z - zs[i]
                if (dx * dx + dy * dy + dz * dz < REPEAT_TOL * REPEAT_TOL) return false
            }
            if (size >= MAX_POINTS) return false
            xs[size] = x; ys[size] = y; zs[size] = z; size++
            lastMs = nowMs
            return true
        }

        fun hasGuess() = size >= MIN_POINTS
        fun settled(nowMs: Long) = size > 0 && nowMs - lastMs > SETTLED_MS
        fun x(i: Int) = xs[i]
        fun y(i: Int) = ys[i]
        fun z(i: Int) = zs[i]
        fun endX() = xs[size - 1]
        fun endY() = ys[size - 1]
        fun endZ() = zs[size - 1]

        fun solve(): DoubleArray? = if (size < MIN_POINTS) null else fishmod.utils.TrailBezier.solve(xs, ys, zs, size)
    }

    class Known(@JvmField val id: Int, @JvmField var x: Double, @JvmField var y: Double, @JvmField var z: Double, @JvmField var perk: String) {
        @JvmField var lastSeenMs = 0L
        @JvmField var shownDist = -1
        @JvmField var label: Any? = null
    }

    class Registry {
        val list = ArrayList<Known>()

        fun find(id: Int): Known? { for (i in list.indices) if (list[i].id == id) return list[i]; return null }

        fun see(id: Int, x: Double, y: Double, z: Double, perk: String, nowMs: Long): Known {
            var k = find(id)
            if (k == null) { k = Known(id, x, y, z, perk); list.add(k) }
            else { k.x = x; k.y = y; k.z = z; if (perk.isNotEmpty() && perk != "Unknown") { if (k.perk != perk) k.shownDist = -1; k.perk = perk } }
            k.lastSeenMs = nowMs
            return k
        }

        fun prune(isLive: (Int) -> Boolean, px: Double, pz: Double, nearBlocks: Double, nowMs: Long, maxAgeMs: Long) {
            val nearSq = nearBlocks * nearBlocks
            var i = 0
            while (i < list.size) {
                val k = list[i]
                var drop = false
                if (!isLive(k.id)) {
                    val dx = k.x - px; val dz = k.z - pz
                    drop = dx * dx + dz * dz <= nearSq || nowMs - k.lastSeenMs > maxAgeMs
                }
                if (drop) list.removeAt(i) else i++
            }
        }

        fun clear() = list.clear()
    }

    fun covered(gx: Double, gz: Double, sx: Double, sz: Double, radius: Double): Boolean {
        val dx = gx - sx; val dz = gz - sz
        return dx * dx + dz * dz <= radius * radius
    }

    fun distance(px: Double, py: Double, pz: Double, x: Double, y: Double, z: Double): Int {
        val dx = x - px; val dy = y - py; val dz = z - pz
        return Math.sqrt(dx * dx + dy * dy + dz * dz).toInt()
    }
}
