package fishmod.features.fishing

// Pure hotspot finder logic, no Minecraft types so it is unit-testable.
// All Hypixel behaviour here (radar flame trail, stand despawn) is UNVERIFIED in game; thresholds are first guesses.
object HotspotMatch {
    // Largest gap between two consecutive trail particles that still counts as one trail
    const val MAX_STEP = 3.0
    // Silence before the next flame particle starts a fresh trail
    const val NEW_TRAIL_GAP_MS = 1500L
    // A trail needs this many points before it yields a guess
    const val MIN_POINTS = 4
    // No new particle for this long = trail finished
    const val SETTLED_MS = 600L
    const val MAX_POINTS = 256

    // Particle trail from one radar use; the last point is taken as the hotspot (the trail walks toward it).
    class Trail {
        private val xs = DoubleArray(MAX_POINTS)
        private val ys = DoubleArray(MAX_POINTS)
        private val zs = DoubleArray(MAX_POINTS)
        var size = 0; private set
        var lastMs = 0L; private set

        fun clear() { size = 0 }

        // Returns true when the point was kept (also true when it started a fresh trail).
        fun add(x: Double, y: Double, z: Double, nowMs: Long): Boolean {
            if (size > 0 && nowMs - lastMs > NEW_TRAIL_GAP_MS) size = 0
            if (size > 0) {
                val dx = x - xs[size - 1]; val dy = y - ys[size - 1]; val dz = z - zs[size - 1]
                if (dx * dx + dy * dy + dz * dz > MAX_STEP * MAX_STEP) return false
            }
            if (size >= MAX_POINTS) return false
            xs[size] = x; ys[size] = y; zs[size] = z; size++
            lastMs = nowMs
            return true
        }

        fun hasGuess() = size >= MIN_POINTS
        fun settled(nowMs: Long) = size > 0 && nowMs - lastMs > SETTLED_MS
        fun endX() = xs[size - 1]
        fun endY() = ys[size - 1]
        fun endZ() = zs[size - 1]
    }

    // A remembered hotspot (armor stand) or radar guess
    class Known(@JvmField val id: Int, @JvmField var x: Double, @JvmField var y: Double, @JvmField var z: Double, @JvmField var perk: String) {
        @JvmField var lastSeenMs = 0L
        // Render cache so the label is only rebuilt when the whole-block distance changes
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

        // Drop entries whose stand is gone. A missing stand near the player despawned (it would be loaded);
        // a missing stand far away may only be out of render range, so it is kept until maxAgeMs.
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

    // True when a radar guess is explained by a real stand within radius (flat distance)
    fun covered(gx: Double, gz: Double, sx: Double, sz: Double, radius: Double): Boolean {
        val dx = gx - sx; val dz = gz - sz
        return dx * dx + dz * dz <= radius * radius
    }

    fun distance(px: Double, py: Double, pz: Double, x: Double, y: Double, z: Double): Int {
        val dx = x - px; val dy = y - py; val dz = z - pz
        return Math.sqrt(dx * dx + dy * dy + dz * dz).toInt()
    }
}
