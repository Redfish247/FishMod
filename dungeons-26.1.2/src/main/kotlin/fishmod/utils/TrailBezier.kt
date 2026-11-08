package fishmod.utils

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Shared by Diana's spade trail and the fishing Hotspot Radar: Hypixel draws a cubic Bezier of particles from the player
// toward the target. Fit a least-squares cubic per axis over the particle index, take the start tangent, derive the first
// control point's distance from the launch pitch, and evaluate the cubic at t = 3 * controlDist / |tangent|.
// Model from SkyHanni's ParticlePathBezierFitter behaviour (studied, not copied); constants calibrated in HotspotMatchTest.
object TrailBezier {
    // xs/ys/zs hold n points in order. Returns {x, y, z} or null (too few points, singular fit, flat tangent).
    @JvmStatic
    fun solve(xs: DoubleArray, ys: DoubleArray, zs: DoubleArray, n: Int): DoubleArray? {
        if (n < 4) return null
        val cx = fit(xs, n) ?: return null
        val cy = fit(ys, n) ?: return null
        val cz = fit(zs, n) ?: return null
        val dx = cx[1]; val dy = cy[1]; val dz = cz[1]
        val len = sqrt(dx * dx + dy * dy + dz * dz)
        if (len < 1e-9) return null
        val t = 3 * controlDistance(dx, dy, dz) / len
        return doubleArrayOf(eval(cx, t), eval(cy, t), eval(cz, t))
    }

    // Distance of the first control point from the start, from the launch pitch implied by the start tangent
    @JvmStatic
    fun controlDistance(dx: Double, dy: Double, dz: Double): Double {
        val pitch0 = -atan2(dy, sqrt(dx * dx + dz * dz))
        // Solve the launch pitch whose trail tangent at t=0 matches the observed one
        var lo = -PI / 2; var hi = PI / 2; var pitch = 0.0
        repeat(100) {
            pitch = (lo + hi) / 2
            val r = atan2(sin(pitch) - 0.75, cos(pitch))
            if (r < pitch0) lo = pitch else hi = pitch
        }
        return sqrt(24 * sin(pitch - PI) + 25)
    }

    private fun eval(c: DoubleArray, t: Double) = c[0] + c[1] * t + c[2] * t * t + c[3] * t * t * t

    // Least-squares cubic over sample index; coefficients low-to-high
    private fun fit(ys: DoubleArray, count: Int): DoubleArray? {
        val n = 4
        val a = Array(n) { DoubleArray(n + 1) }
        val pw = DoubleArray(2 * n)
        for (i in 0 until count) {
            val y = ys[i]
            pw[0] = 1.0
            for (k in 1 until 2 * n) pw[k] = pw[k - 1] * i
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
