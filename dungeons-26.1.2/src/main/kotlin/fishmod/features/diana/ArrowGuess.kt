package fishmod.features.diana

import fishmod.utils.events.Events
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sign

// Reads the dust arrow shown after a dig and walks the ray for grass blocks in the arrow's distance band
object ArrowGuess {

    private const val SHAFT = 20
    private const val TOL = 0.12
    private const val NEAR_BASE = 2
    private const val NEAR_TIP = 4
    private const val EPS = 1e-6

    private class Entry(val cands: MutableList<BlockPos>) {
        var idx = 0
        val current get() = cands.getOrNull(idx)
    }

    private const val DUST_TTL_MS = 2000L
    private const val DUST_CAP = 120

    // point -> when first seen; Hypixel resends the same arrow every few ticks
    private val dust = LinkedHashMap<Vec3, Long>()
    private var pending = false
    private var range: IntRange? = null
    private val entries = ArrayList<Entry>()
    private val seenRays = HashMap<Pair<Vec3, Vec3>, Long>()

    fun reset() { dust.clear(); entries.clear(); seenRays.clear(); range = null; pending = false }

    fun init() {
        Events.ON_PARTICLE.register { p ->
            if (!DianaSettings.dianaGuessing || !DianaSettings.dianaArrowGuess || !Diana.inHub()) return@register false
            if (p.particle.type !== ParticleTypes.DUST || p.count != 0 || abs(p.maxSpeed - 1f) > 1e-4f || p.particle !is DustParticleOptions) return@register false
            val r = bandFor(p.xDist.toDouble(), p.yDist.toDouble(), p.zDist.toDouble()) ?: return@register false
            val v = Vec3(p.x, p.y, p.z)
            val last = Diana.lastClickedWaypoint
            if (last != null && v.distanceTo(Vec3(last.x + 0.5, last.y + 0.5, last.z + 0.5)) > 7) return@register false
            range = r
            // Only a genuinely new point is worth another shape search
            if (dust.putIfAbsent(v, System.currentTimeMillis()) == null) pending = true
            false
        }
        ClientTickEvents.END_CLIENT_TICK.register {
            if (!Diana.inHub()) return@register
            // At most one search per tick, however many particles arrived
            if (pending) {
                pending = false
                val now = System.currentTimeMillis()
                dust.entries.removeIf { now - it.value > DUST_TTL_MS }
                if (dust.size > DUST_CAP) dust.keys.take(dust.size - DUST_CAP).forEach { dust.remove(it) }
                detect()
            }
            if (entries.isNotEmpty()) advance()
        }
    }

    private fun bandFor(x: Double, y: Double, z: Double): IntRange? = when {
        x == 0.0 && y == 128.0 && z == 0.0 -> 0..117
        x == 255.0 && y == 255.0 && z == 0.0 -> 112..282
        x == 255.0 && y == 0.0 && z == 0.0 -> 281..600
        else -> null
    }

    fun onBurrowDug() { dust.clear() }

    // A real burrow at pos supersedes any arrow guess landing there
    fun onBurrowAt(pos: BlockPos) {
        val it = entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (e.current == pos) {
                DianaWaypoints.removeAt(pos, WpType.ARROW)
                it.remove()
                e.cands.drop(e.idx + 1).forEach { c -> DianaWaypoints.removeAt(c, WpType.SUB) }
            } else if (pos in e.cands) DianaWaypoints.removeAt(pos, WpType.SUB)
        }
    }

    private fun detect() {
        if (dust.size < SHAFT) return
        val pts = dust.keys.toList()
        val line = findShaft(pts) ?: return DianaTest.log("arrow: no shaft in ${pts.size} pts")
        // Neighbours include the shaft's own points: base end has 2 (shaft only), tip end 4 (shaft + barbs)
        val c1 = line[1]; val c2 = line[line.size - 2]
        val n1 = pts.count { it != c1 && it.distanceTo(c1) <= TOL }
        val n2 = pts.count { it != c2 && it.distanceTo(c2) <= TOL }
        if (!(n1 == NEAR_BASE && n2 == NEAR_TIP || n1 == NEAR_TIP && n2 == NEAR_BASE))
            return DianaTest.log("arrow: barb counts $n1/$n2")
        val (base, tip) = if (n1 == NEAR_TIP) line.last() to line.first() else line.first() to line.last()
        val origin = base.add(0.0, -1.5, 0.0)
        val dir = tip.add(0.0, -1.5, 0.0).subtract(origin).normalize()
        val now = System.currentTimeMillis()
        seenRays.entries.removeIf { now - it.value > 18_000 }
        val key = origin to dir
        dust.clear()
        if (seenRays.containsKey(key)) return
        seenRays[key] = now
        DianaTest.log("arrow: ray $origin -> $dir band $range")
        solve(origin, dir, range ?: return)
    }

    private fun findShaft(pts: List<Vec3>): List<Vec3>? {
        data class Cand(val line: List<Vec3>, val score: Double)
        val near = HashMap<Vec3, List<Vec3>>(pts.size * 2)
        for (p in pts) near[p] = pts.filter { it !== p && it.distanceTo(p) <= TOL }
        val cands = arrayListOf<Cand>()
        for (start in pts) {
            if (near[start].isNullOrEmpty()) continue
            val line = arrayListOf(start)
            val used = hashSetOf(start)
            while (line.size < SHAFT) {
                val last = line.last()
                val a = line[0]; val b = if (line.size > 1) line[1] else line[0]
                val next = near[last]!!.filter { it !in used && collinear(a, b, it) }
                    .minByOrNull { it.distanceTo(last) } ?: break
                line.add(next); used.add(next)
            }
            if (line.size < SHAFT) continue
            cands.add(Cand(line, line.sumOf { perp(it, line.first(), line.last()) }))
        }
        return cands.minWithOrNull(compareBy<Cand> { it.score }.thenByDescending { it.line.size })?.line
    }

    private fun collinear(a: Vec3, b: Vec3, c: Vec3): Boolean {
        if (a == b) return true
        return b.subtract(a).cross(c.subtract(a)).lengthSqr() < EPS
    }

    private fun perp(p: Vec3, a: Vec3, b: Vec3): Double {
        val ab = b.subtract(a)
        val l = ab.length()
        return if (l < EPS) p.distanceTo(a) else ab.cross(p.subtract(a)).length() / l
    }

    private fun comp(v: Vec3, axis: Int) = when (axis) { 0 -> v.x; 1 -> v.y; else -> v.z }

    // Step along the ray's shallowest axis inside the hub box, keep the best-aligned valid blocks
    private fun solve(origin: Vec3, dir: Vec3, band: IntRange) {
        val exit = exitPoint(origin, dir) ?: return fail()
        val delta = exit.subtract(origin)
        val axis = (0..2).filter { abs(comp(delta, it)) > 0.9 }.minByOrNull { abs(comp(delta, it)) } ?: return fail()
        val da = comp(dir, axis)
        if (abs(da) < EPS) return fail()
        val steps = abs(comp(delta, axis)).toInt()
        data class Cand(val pos: BlockPos, val score: Double, val dist: Double)
        val cands = LinkedHashMap<BlockPos, Cand>()
        for (i in 1..steps) {
            val t = i * sign(da) / da
            val p = origin.add(dir.scale(t))
            val bp = BlockPos(floor(p.x).toInt(), floor(p.y).toInt(), floor(p.z).toInt())
            if (!DianaWaypoints.isValidBlock(bp)) continue
            val rel = Vec3(bp.x + 0.5, bp.y + 0.5, bp.z + 0.5).subtract(origin)
            val along = rel.dot(dir)
            val toRay = if (along < 0) Double.MAX_VALUE else rel.subtract(dir.scale(along)).length()
            val fromOrigin = p.distanceTo(origin)
            cands[bp] = Cand(bp, toRay * 500_000 / fromOrigin.coerceAtLeast(EPS), fromOrigin)
        }
        val order = compareBy<Cand> { it.score }.thenBy { it.dist }
        val best = cands.values.minWithOrNull(order) ?: return fail()
        val tied = cands.values.filter { abs(it.score - best.score) <= 1e-6 }
        val picked = tied.filter { it.dist.toInt() in band }.map { it.pos }
        if (picked.isEmpty()) return fail()
        DianaTest.log("arrow: candidates $picked")
        addGuess(picked)
    }

    internal fun addGuess(picked: List<BlockPos>) {
        if (picked.isEmpty() || entries.any { it.cands.drop(it.idx) == picked }) return
        entries.add(Entry(picked.toMutableList()))
        DianaWaypoints.add(Waypoint(picked[0], WpType.ARROW, "Guess"))
        if (DianaSettings.dianaSubGuesses) {
            picked.drop(1).forEach { DianaWaypoints.add(Waypoint(it, WpType.SUB, "")) }
        }
    }

    private fun fail() { DianaTest.log("arrow: no candidate") }

    private fun exitPoint(o: Vec3, d: Vec3): Vec3? {
        val min = doubleArrayOf(DianaWaypoints.MIN_X.toDouble(), DianaWaypoints.MIN_Y.toDouble(), DianaWaypoints.MIN_Z.toDouble())
        val max = doubleArrayOf(DianaWaypoints.MAX_X.toDouble(), DianaWaypoints.MAX_Y.toDouble(), DianaWaypoints.MAX_Z.toDouble())
        val oa = doubleArrayOf(o.x, o.y, o.z); val da = doubleArrayOf(d.x, d.y, d.z)
        for (i in 0..2) if (oa[i] <= min[i] || oa[i] > max[i]) return null
        var tExit = Double.MAX_VALUE
        for (i in 0..2) {
            if (abs(da[i]) < EPS) continue
            val t = ((if (da[i] > 0) max[i] else min[i]) - oa[i]) / da[i]
            if (t in 0.0..tExit) tExit = t
        }
        return if (tExit == Double.MAX_VALUE) null else o.add(d.scale(tExit))
    }

    // Invalid block, or spade held within 32 blocks for a full second with no burrow: next candidate or drop the guess
    private fun advance() {
        if (Diana.player() == null) return
        val it = entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            val cur = e.current ?: run { it.remove(); continue }
            val spadeWrong = DianaWaypoints.at(cur, WpType.ARROW)?.let { w -> DianaWaypoints.spadeDisproved(w) } ?: false
            if (!DianaWaypoints.isValidBlock(cur) || spadeWrong) {
                dropEntryWaypoints(e)
                if (!e.moveToNext()) {
                    it.remove()
                    continue
                }
                val next = e.current!!
                DianaWaypoints.add(Waypoint(next, WpType.ARROW, "Guess"))
                if (DianaSettings.dianaSubGuesses) {
                    e.cands.drop(e.idx + 1).forEach { DianaWaypoints.add(Waypoint(it, WpType.SUB, "")) }
                }
            }
        }
    }

    private fun Entry.moveToNext(): Boolean {
        idx++
        while (idx < cands.size && !DianaWaypoints.isValidBlock(cands[idx])) idx++
        return idx < cands.size
    }

    private fun dropEntryWaypoints(e: Entry) {
        e.current?.let { DianaWaypoints.removeAt(it, WpType.ARROW) }
        e.cands.drop(e.idx + 1).forEach { DianaWaypoints.removeAt(it, WpType.SUB) }
    }

    fun renderChains(draw: (Vec3, Vec3) -> Unit) {
        for (e in entries) {
            val cur = e.current ?: continue
            val a = Vec3(cur.x + 0.5, cur.y + 1.0, cur.z + 0.5)
            for (c in e.cands.drop(e.idx + 1)) draw(a, Vec3(c.x + 0.5, c.y + 1.0, c.z + 0.5))
        }
    }
}
