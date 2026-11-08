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
    // Points used for the end-of-trail heading
    const val FIT_WINDOW = 8
    // Projection march: 1 block steps out to this range
    const val MAX_RANGE = 160
    // Horizontal distance used when the ray never meets fluid
    const val FALLBACK_RANGE = 40.0
    // Rays (flat) further than this from the triangulated point mean the shots point at different hotspots
    const val MAX_RESIDUAL = 15.0
    const val MAX_SHOTS = 4

    // Trail end point, horizontal unit heading (dx, dz) and vertical change per horizontal block
    class Line(@JvmField val ox: Double, @JvmField val oy: Double, @JvmField val oz: Double,
               @JvmField val dx: Double, @JvmField val dz: Double, @JvmField val slope: Double)

    class Estimate(@JvmField val x: Double, @JvmField val y: Double, @JvmField val z: Double, @JvmField val kind: Kind)
    enum class Kind { SURFACE, FALLBACK, TRIANGULATED }

    fun interface FluidProbe { fun isFluid(x: Double, y: Double, z: Double): Boolean }

    // Walk the heading forward from the trail end; the first fluid block met is the estimate (hotspots sit on water/lava).
    // No hit (or no probe): FALLBACK_RANGE ahead at fallbackY. ASSUMES a straight line; the real trail curves down faster.
    fun project(l: Line, probe: FluidProbe?, fallbackY: Double): Estimate {
        if (probe != null) {
            for (i in 1..MAX_RANGE) {
                val x = l.ox + l.dx * i; val z = l.oz + l.dz * i; val y = l.oy + l.slope * i
                if (probe.isFluid(x, y, z)) return Estimate(x, Math.floor(y) + 0.9, z, Kind.SURFACE)
                if (probe.isFluid(x, y - 1.0, z)) return Estimate(x, Math.floor(y - 1.0) + 0.9, z, Kind.SURFACE)
            }
        }
        return Estimate(l.ox + l.dx * FALLBACK_RANGE, fallbackY, l.oz + l.dz * FALLBACK_RANGE, Kind.FALLBACK)
    }

    // Least-squares crossing of the shots' flat rays (horizontal x/z only). Null when parallel, behind any origin,
    // or inconsistent (a ray more than MAX_RESIDUAL off, e.g. shots aimed at two different hotspots). Y is the mean end Y.
    fun triangulate(lines: List<Line>): Estimate? {
        val n = lines.size
        if (n < 2) return null
        var a = 0.0; var b = 0.0; var d = 0.0; var rx = 0.0; var rz = 0.0
        for (i in 0 until n) {
            val l = lines[i]
            val nx = -l.dz; val nz = l.dx
            a += nx * nx; b += nx * nz; d += nz * nz
            val c = nx * l.ox + nz * l.oz
            rx += nx * c; rz += nz * c
        }
        val det = a * d - b * b
        // det is n^2 sin^2(angle)/4-ish: under ~0.03 the rays are within ~10 degrees of parallel
        if (det < 0.03 * n * n / 4.0) return null
        val px = (d * rx - b * rz) / det
        val pz = (a * rz - b * rx) / det
        var ySum = 0.0
        for (i in 0 until n) {
            val l = lines[i]
            if ((px - l.ox) * l.dx + (pz - l.oz) * l.dz <= 0.0) return null
            val res = (px - l.ox) * -l.dz + (pz - l.oz) * l.dx
            if (Math.abs(res) > MAX_RESIDUAL) return null
            ySum += l.oy
        }
        return Estimate(px, ySum / n, pz, Kind.TRIANGULATED)
    }

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
        fun x(i: Int) = xs[i]
        fun y(i: Int) = ys[i]
        fun z(i: Int) = zs[i]

        // Heading at the trail end: least-squares slope over the last FIT_WINDOW points. Null when too short or not moving sideways.
        fun fit(): Line? {
            if (size < 2) return null
            val n = if (size < FIT_WINDOW) size else FIT_WINDOW
            val from = size - n
            var mi = 0.0; var mx = 0.0; var my = 0.0; var mz = 0.0
            for (k in 0 until n) { mi += k; mx += xs[from + k]; my += ys[from + k]; mz += zs[from + k] }
            mi /= n; mx /= n; my /= n; mz /= n
            var sii = 0.0; var six = 0.0; var siy = 0.0; var siz = 0.0
            for (k in 0 until n) {
                val di = k - mi
                sii += di * di; six += di * (xs[from + k] - mx); siy += di * (ys[from + k] - my); siz += di * (zs[from + k] - mz)
            }
            if (sii < 1e-9) return null
            val sx = six / sii; val sy = siy / sii; val sz = siz / sii
            val h = Math.sqrt(sx * sx + sz * sz)
            if (h < 1e-6) return null
            return Line(xs[size - 1], ys[size - 1], zs[size - 1], sx / h, sz / h, sy / h)
        }
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
