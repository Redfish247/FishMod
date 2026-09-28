package fishmod.features.diana

import fishmod.utils.events.Events
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

// Fits a cubic to the spade's lava trail and extrapolates where the trail lands
object SpadeGuess {

    private val points = ArrayList<Vec3>()
    private var lastUseMs = 0L
    private var lastLavaMs = 0L

    fun reset() { points.clear() }

    fun init() {
        Events.ON_PARTICLE.register { p ->
            if (DianaSettings.dianaSpadeGuess && Diana.inHub() && p.particle.type === ParticleTypes.DRIPPING_LAVA &&
                p.count == 2 && (abs(p.maxSpeed + 0.5f) < 1e-4f || testTrail(p))
            ) onPoint(Vec3(p.x, p.y, p.z))
            false
        }
        Events.ON_WORLD_CHANGE.register { reset(); false }
    }

    // /particle can't send a negative speed, so test worlds use speed 0 with no spread
    private fun testTrail(p: net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket) =
        Diana.testMode && p.maxSpeed == 0f && p.xDist == 0f && p.yDist == 0f && p.zDist == 0f

    fun onSpadeUse() {
        val now = System.currentTimeMillis()
        if (now - lastLavaMs < 200) return
        points.clear()
        lastUseMs = now
    }

    private fun onPoint(v: Vec3) {
        val now = System.currentTimeMillis()
        lastLavaMs = now
        if (now - lastUseMs > 3000) return DianaTest.log("spade: point ignored, no recent use")
        if (points.isNotEmpty()) {
            val d = points.last().distanceTo(v)
            if (d <= 0 || d > 3) return
        }
        points.add(v)
        val g = guess()
        DianaTest.log("spade: ${points.size} pts guess=$g")
        g?.let { place(it) }
    }

    private fun place(v: Vec3) {
        val pos = BlockPos(floor(v.x).toInt(), floor(v.y - 0.5).toInt(), floor(v.z).toInt())
        if (DianaWaypoints.removedRecently(pos)) return
        DianaWaypoints.list.removeIf { it.type == WpType.GUESS && it.pos != pos && it.distTo(v) <= 32 }
        if (DianaWaypoints.list.any { it.pos == pos && (it.type == WpType.BURROW || it.type == WpType.GUESS) }) return
        DianaWaypoints.add(Waypoint(pos, WpType.GUESS, "Guess"))
    }

    fun guess(): Vec3? {
        if (points.size < 4) return null
        val cx = fit(points.map { it.x }) ?: return null
        val cy = fit(points.map { it.y }) ?: return null
        val cz = fit(points.map { it.z }) ?: return null
        val dx = cx[1]; val dy = cy[1]; val dz = cz[1]
        val len = sqrt(dx * dx + dy * dy + dz * dz)
        if (len < 1e-9) return null
        val pitch0 = -atan2(dy, sqrt(dx * dx + dz * dz))
        // Solve the launch pitch whose trail tangent at t=0 matches the observed one
        var lo = -PI / 2; var hi = PI / 2; var pitch = 0.0
        repeat(100) {
            pitch = (lo + hi) / 2
            val r = atan2(sin(pitch) - 0.75, cos(pitch))
            if (r < pitch0) lo = pitch else hi = pitch
        }
        val controlDist = sqrt(24 * sin(pitch - PI) + 25)
        val t = 3 * controlDist / len
        return Vec3(eval(cx, t), eval(cy, t), eval(cz, t))
    }

    private fun eval(c: DoubleArray, t: Double) = c[0] + c[1] * t + c[2] * t * t + c[3] * t * t * t

    // Least-squares cubic over sample index; coefficients low-to-high
    private fun fit(ys: List<Double>): DoubleArray? {
        val n = 4
        val a = Array(n) { DoubleArray(n + 1) }
        for ((i, y) in ys.withIndex()) {
            val t = i.toDouble()
            val pw = DoubleArray(2 * n) { 1.0 }
            for (k in 1 until 2 * n) pw[k] = pw[k - 1] * t
            for (r in 0 until n) {
                for (c in 0 until n) a[r][c] += pw[r + c]
                a[r][n] += pw[r] * y
            }
        }
        for (col in 0 until n) {
            var piv = col
            for (r in col + 1 until n) if (abs(a[r][col]) > abs(a[piv][col])) piv = r
            if (abs(a[piv][col]) < 1e-12) return null
            val tmp = a[col]; a[col] = a[piv]; a[piv] = tmp
            for (r in 0 until n) if (r != col) {
                val f = a[r][col] / a[col][col]
                for (c in col..n) a[r][c] -= f * a[col][c]
            }
        }
        return DoubleArray(n) { a[it][n] / a[it][it] }
    }
}
