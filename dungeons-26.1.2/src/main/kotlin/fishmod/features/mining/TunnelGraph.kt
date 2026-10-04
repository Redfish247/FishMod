package fishmod.features.mining

import com.google.gson.JsonParser
import fishmod.utils.debug.FishDiag
import net.minecraft.world.phys.Vec3
import java.io.File
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.PriorityQueue

// Walkable Glacite Tunnels node graph from the SkyHanni repo (same data SkyHanni's tunnel maps path along)
object TunnelGraph {

    class Node(val id: Int, val pos: Vec3, val name: String) { val next = ArrayList<Pair<Node, Double>>() }

    private const val URL = "https://raw.githubusercontent.com/hannibal002/SkyHanni-REPO/main/constants/island_graphs/GLACITE_TUNNELS.json"
    private val FILE = File("config/fishmod/glacite_tunnels_graph.json")
    private const val REFRESH_MS = 24 * 60 * 60 * 1000L

    @Volatile var nodes: List<Node> = emptyList(); private set
    @Volatile private var loading = false

    fun ensureLoaded() {
        if (loading || (nodes.isNotEmpty() && System.currentTimeMillis() - FILE.lastModified() < REFRESH_MS)) return
        loading = true
        fishmod.utils.IoExecutor.execute {
            try {
                if (nodes.isEmpty() && FILE.exists()) parse(FILE.readText())
                if (!FILE.exists() || System.currentTimeMillis() - FILE.lastModified() > REFRESH_MS) {
                    val req = HttpRequest.newBuilder(URI.create(URL)).timeout(Duration.ofSeconds(15)).GET().build()
                    val res = fishmod.utils.Http.CLIENT.send(req, HttpResponse.BodyHandlers.ofString())
                    if (res.statusCode() == 200 && parse(res.body())) fishmod.utils.SafeFiles.writeAtomic(FILE, res.body())
                    else FishDiag.fail("TunnelGraph.1", "graph fetch status=${res.statusCode()}")
                }
            } catch (e: Exception) {
                FishDiag.fail("TunnelGraph.2", "graph load failed (cached=${nodes.size})", e)
            } finally { loading = false }
        }
    }

    private fun parse(json: String): Boolean {
        val root = JsonParser.parseString(json).asJsonObject
        val byId = HashMap<Int, Node>()
        for ((k, v) in root.entrySet()) {
            val o = v.asJsonObject
            val p = o.get("Position").asString.split(":").map { it.toDouble() }
            val name = o.get("Name")?.asString?.let { Mining.strip(it) } ?: ""
            byId[k.toInt()] = Node(k.toInt(), Vec3(p[0] + 0.5, p[1], p[2] + 0.5), name)
        }
        for ((k, v) in root.entrySet()) {
            val n = byId[k.toInt()] ?: continue
            v.asJsonObject.getAsJsonObject("Neighbours")?.entrySet()?.forEach { (id, d) ->
                byId[id.toInt()]?.let { n.next += it to d.asDouble }
            }
        }
        if (byId.isEmpty()) return false
        nodes = byId.values.toList()
        return true
    }

    fun closest(p: Vec3): Node? = nodes.minByOrNull { it.pos.distanceToSqr(p) }

    // Dijkstra from start; returns (distance, previous) for every reachable node
    fun search(start: Node): Pair<Map<Node, Double>, Map<Node, Node>> {
        val dist = HashMap<Node, Double>().apply { put(start, 0.0) }
        val prev = HashMap<Node, Node>()
        val q = PriorityQueue<Pair<Node, Double>>(compareBy { it.second }).apply { add(start to 0.0) }
        while (q.isNotEmpty()) {
            val (n, d) = q.poll()
            if (d > (dist[n] ?: Double.MAX_VALUE)) continue
            for ((m, w) in n.next) {
                val nd = d + w
                if (nd < (dist[m] ?: Double.MAX_VALUE)) { dist[m] = nd; prev[m] = n; q.add(m to nd) }
            }
        }
        return dist to prev
    }

    fun path(prev: Map<Node, Node>, end: Node): List<Node> {
        val out = ArrayList<Node>()
        var c: Node? = end
        while (c != null) { out += c; c = prev[c] }
        return out.asReversed()
    }
}
