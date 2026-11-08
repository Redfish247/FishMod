package fishmod.features.fishing

// Pure hotspot finder logic, no Minecraft types so it is unit-testable.
// All Hypixel behaviour here (radar flame trail, stand despawn) is UNVERIFIED in game; thresholds are first guesses.
object HotspotMatch {
    // Largest gap between two consecutive trail particles that still counts as one trail
    const val MAX_STEP = 3.0
    // Silence before the next flame particle starts a fresh trail
    const val NEW_TRAIL_GAP_MS = 1500L
    // A trail needs this many points before it yields a guess
    const val MIN_POINTS = 6
    // No new particle for this long = trail finished
    const val SETTLED_MS = 600L
    const val MAX_POINTS = 256
    // Points used for the end-of-trail heading
    const val FIT_WINDOW = 8
    // UNVERIFIED, one real sample (1.7.46 log): trail 32 blocks long, stand 12.5 past its end (ratio 0.39); a second, cut-off
    // trail 20 long had the stand 35 past (ratio 1.7). Heading was accurate in both; range is the unknown, hence a band.
    const val RANGE_RATIO = 0.4
    const val BAND_MIN_RATIO = 0.25
    const val BAND_MAX_RATIO = 1.8
    // Trail this close to the player (flat blocks) means the hotspot is right there: no projection
    const val NEAR_END_BLOCKS = 20.0
    // Column scan depth when looking for the fluid surface under a projected point
    const val SURFACE_SCAN = 120
    // Rays must cross at more than ~20 degrees, ends of shots at least this far apart, crossing within this of every shot end
    const val MIN_SHOT_SEP = 10.0
    const val MAX_CROSS_RANGE = 120.0
    const val MIN_ANGLE_SIN_SQ = 0.117
    const val MAX_RESIDUAL = 15.0
    const val MAX_SHOTS = 4

    // Trail end point, horizontal unit heading (dx, dz), vertical change per horizontal block, and flat start->end length
    class Line(@JvmField val ox: Double, @JvmField val oy: Double, @JvmField val oz: Double,
               @JvmField val dx: Double, @JvmField val dz: Double, @JvmField val slope: Double, @JvmField val len: Double = 0.0)

    // Central point plus the near/far ends of the range band along the heading (flat coordinates)
    class Estimate(@JvmField val x: Double, @JvmField val y: Double, @JvmField val z: Double, @JvmField val kind: Kind,
                   @JvmField val nearX: Double = x, @JvmField val nearZ: Double = z, @JvmField val farX: Double = x, @JvmField val farZ: Double = z)
    enum class Kind { SURFACE, FALLBACK, TRIANGULATED }

    fun interface FluidProbe { fun isFluid(x: Double, y: Double, z: Double): Boolean }

    // Top of the first fluid block found scanning down from startY, or null (unloaded chunk, no fluid)
    fun surfaceY(probe: FluidProbe?, x: Double, startY: Double, z: Double): Double? {
        if (probe == null) return null
        val top = Math.floor(startY)
        for (i in 0..SURFACE_SCAN) if (probe.isFluid(x, top - i, z)) return top - i + 0.9
        return null
    }

    // Straight ahead from the trail end by RANGE_RATIO x trail length (band: BAND_MIN..BAND_MAX ratio). Y is the fluid
    // surface under that point, else fallbackY. ASSUMES the hotspot lies on the trail's end heading and that range scales with trail length.
    fun project(l: Line, probe: FluidProbe?, fallbackY: Double): Estimate {
        val c = l.len * RANGE_RATIO
        val x = l.ox + l.dx * c; val z = l.oz + l.dz * c
        val sy = surfaceY(probe, x, l.oy + 3.0, z)
        return Estimate(x, sy ?: fallbackY, z, if (sy != null) Kind.SURFACE else Kind.FALLBACK,
            l.ox + l.dx * l.len * BAND_MIN_RATIO, l.oz + l.dz * l.len * BAND_MIN_RATIO,
            l.ox + l.dx * l.len * BAND_MAX_RATIO, l.oz + l.dz * l.len * BAND_MAX_RATIO)
    }

    // Least-squares crossing of the shots' flat rays (x/z). Null when: fewer than 2, ends closer than MIN_SHOT_SEP,
    // rays within ~20 degrees of parallel, crossing behind any end or further than MAX_CROSS_RANGE from any end, or any ray more
    // than MAX_RESIDUAL off (shots at different hotspots). Y is the mean end Y.
    fun triangulate(lines: List<Line>): Estimate? {
        val n = lines.size
        if (n < 2) return null
        for (i in 0 until n) for (j in i + 1 until n) {
            val dx = lines[i].ox - lines[j].ox; val dz = lines[i].oz - lines[j].oz
            if (dx * dx + dz * dz < MIN_SHOT_SEP * MIN_SHOT_SEP) return null
        }
        var a = 0.0; var b = 0.0; var d = 0.0; var rx = 0.0; var rz = 0.0
        for (i in 0 until n) {
            val l = lines[i]
            val nx = -l.dz; val nz = l.dx
            a += nx * nx; b += nx * nz; d += nz * nz
            val c = nx * l.ox + nz * l.oz
            rx += nx * c; rz += nz * c
        }
        val det = a * d - b * b
        // n=2: det = sin^2(angle between rays)
        if (det < MIN_ANGLE_SIN_SQ * n * n / 4.0) return null
        val px = (d * rx - b * rz) / det
        val pz = (a * rz - b * rx) / det
        var ySum = 0.0
        for (i in 0 until n) {
            val l = lines[i]
            val ahead = (px - l.ox) * l.dx + (pz - l.oz) * l.dz
            if (ahead <= 0.0 || ahead > MAX_CROSS_RANGE) return null
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
            val fx = xs[size - 1] - xs[0]; val fz = zs[size - 1] - zs[0]
            return Line(xs[size - 1], ys[size - 1], zs[size - 1], sx / h, sz / h, sy / h, Math.sqrt(fx * fx + fz * fz))
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
