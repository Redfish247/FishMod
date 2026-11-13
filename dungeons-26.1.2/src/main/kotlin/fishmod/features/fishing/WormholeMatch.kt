package fishmod.features.fishing

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// Pure wormhole matching, no Minecraft types so it is unit-testable.
// Idea: each Froggles arrow points horizontally at a wormhole; pick the known node whose bearing from the arrow best lines up.
// Node data: SkyHanni-Repo (MIT), see NOTICE. The 0.98 tolerance and the arrow format are UNVERIFIED in game.
object WormholeMatch {
    const val DIRECTION_TOLERANCE = 0.98

    class Node(@JvmField val x: Int, @JvmField val y: Int, @JvmField val z: Int)

    fun bestNode(ox: Double, oz: Double, fx: Double, fz: Double, nodes: List<Node>, tolerance: Double = DIRECTION_TOLERANCE): Int {
        val fl = Math.sqrt(fx * fx + fz * fz)
        if (fl < 1e-6) return -1
        val nx = fx / fl
        val nz = fz / fl
        var best = -1
        var bestDot = tolerance
        for (i in nodes.indices) {
            val dx = nodes[i].x + 0.5 - ox
            val dz = nodes[i].z + 0.5 - oz
            val l = Math.sqrt(dx * dx + dz * dz)
            if (l < 1e-6) continue
            val dot = (dx * nx + dz * nz) / l
            if (dot >= bestDot) { bestDot = dot; best = i }
        }
        return best
    }

    fun nearest(px: Double, pz: Double, nodes: List<Node>, indices: Collection<Int>): Int {
        var best = -1
        var bd = Double.MAX_VALUE
        for (i in indices) {
            val dx = nodes[i].x + 0.5 - px
            val dz = nodes[i].z + 0.5 - pz
            val d = dx * dx + dz * dz
            if (d < bd) { bd = d; best = i }
        }
        return best
    }

    fun parse(json: String): Map<String, List<Node>> {
        val raw: Map<String, List<List<Int>>> = Gson().fromJson(json, object : TypeToken<Map<String, List<List<Int>>>>() {}.type) ?: return emptyMap()
        val out = HashMap<String, List<Node>>()
        for ((k, v) in raw) out[k] = v.filter { it.size >= 3 }.map { Node(it[0], it[1], it[2]) }
        return out
    }
}
