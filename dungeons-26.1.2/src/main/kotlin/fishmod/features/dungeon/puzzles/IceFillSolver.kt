package fishmod.features.dungeon.puzzles

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import fishmod.features.dungeon.map.Room
import fishmod.utils.config.values.FishSettings
import fishmod.utils.rendering.RenderUtils
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import java.util.ArrayDeque
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Ice Fill solver — ported from NoammAddons' IceFillSolver: BFS the ice clusters, then a
 * turn-penalised Hamiltonian-path DFS per cluster between the two checkpoints, drawn as a line.
 * Coords via [Room.realCoord] with NoammAddons' `360 - rotation`. Runs on a worker thread since the
 * DFS can take tens of ms.
 *
 * NOTE: room-centre/rotation calibration unverified headless.
 */
class IceFillSolver : PuzzleSolver {

    override val roomName = "Ice Fill"

    private val paths = CopyOnWriteArrayList<List<BlockPos>>()
    @Volatile private var solving = false

    override fun onEnter(room: Room) {
        reset()
        if (!FishSettings.iceFillSolver) return
        solving = true
        Thread({ try { solve(room) } catch (_: Throwable) {} finally { solving = false } }, "fm-icefill").start()
    }

    override fun onExit() = reset()
    override fun reset() { paths.clear() }

    private fun solve(room: Room) {
        val level = Minecraft.getInstance().level ?: return
        val deg = 360 - room.rotationDegrees
        val checkpoints = listOf(
            room.realCoord(BlockPos(0, 69, -8), deg) ?: return,
            room.realCoord(BlockPos(0, 70, -3), deg) ?: return,
            room.realCoord(BlockPos(0, 71, 4), deg) ?: return,
            room.realCoord(BlockPos(0, 71, 11), deg) ?: return,
        )
        val c = room.centerBlock ?: return

        val ice = HashSet<BlockPos>()
        for (dx in -22..22) for (dz in -22..22) for (dy in 68..73) {
            val pos = BlockPos(c.x + dx, dy, c.z + dz)
            val s = level.getBlockState(pos)
            if ((s.`is`(Blocks.ICE) || s.`is`(Blocks.PACKED_ICE)) && level.getBlockState(pos.above()).isAir) {
                ice.add(pos.above())
            }
        }
        if (ice.isEmpty()) return

        // Flood-fill into clusters.
        val clusters = ArrayList<MutableSet<BlockPos>>()
        val seen = HashSet<BlockPos>()
        for (p in ice) {
            if (p in seen) continue
            val cl = HashSet<BlockPos>()
            val q = ArrayDeque<BlockPos>(); q.add(p); seen.add(p)
            while (q.isNotEmpty()) {
                val cur = q.poll(); cl.add(cur)
                for (d in Direction.Plane.HORIZONTAL) {
                    val n = cur.relative(d)
                    if (n in ice && seen.add(n)) q.add(n)
                }
            }
            clusters.add(cl)
        }
        clusters.sortBy { cl -> cl.minOf { it.distSqr(checkpoints[0]) } }

        for ((i, cl) in clusters.withIndex()) {
            if (i >= 3) break
            val spaces = HashSet(cl)
            val start = spaces.minByOrNull { it.distSqr(checkpoints[i]) } ?: continue
            val end = spaces.minByOrNull { it.distSqr(checkpoints[i + 1]) } ?: continue
            val path = Puzzle(spaces, start, end).solve()
            if (path.size > 1) paths.add(path)
        }
    }

    override fun renderWorld(matrices: PoseStack, vc: VertexConsumer) {
        if (!FishSettings.iceFillSolver) return
        val rgba = RenderUtils.toFloats(FishSettings.iceFillColor)
        for (path in paths) {
            for (i in 1 until path.size) {
                val a = path[i - 1]; val b = path[i]
                RenderUtils.renderLine(matrices, vc,
                    Vec3(a.x + 0.5, a.y + 0.02, a.z + 0.5), Vec3(b.x + 0.5, b.y + 0.02, b.z + 0.5), rgba)
            }
        }
    }

    // ── NoammAddons IceFillPuzzle DFS ───────────────────────────────────────
    private class Puzzle(val spaces: HashSet<BlockPos>, val start: BlockPos, val end: BlockPos) {
        private val graph = spaces.associateWith { p -> Direction.Plane.HORIZONTAL.filter { p.relative(it) in spaces } }
        private val TURN = 1; private val NO_WALL = 5; private val DOUBLE_TURN = 2

        private fun dirBetween(a: BlockPos, b: BlockPos) = when {
            b.x > a.x -> Direction.EAST; b.x < a.x -> Direction.WEST
            b.z > a.z -> Direction.SOUTH; else -> Direction.NORTH
        }
        private fun backedByWall(p: BlockPos, d: Direction) = p.relative(d) !in spaces

        private fun connected(current: BlockPos, visited: Set<BlockPos>): Boolean {
            val remaining = spaces.size - visited.size + 1
            if (remaining <= 1) return true
            val s = HashSet<BlockPos>(remaining); val st = ArrayDeque<BlockPos>()
            st.push(current); s.add(current)
            while (st.isNotEmpty()) {
                val cur = st.pop()
                for (d in graph[cur] ?: emptyList()) {
                    val n = cur.relative(d)
                    if ((n == current || n !in visited) && s.add(n)) st.push(n)
                }
            }
            return s.size == remaining
        }

        private fun pathCost(path: List<BlockPos>): Int {
            if (path.size < 3) return 0
            var total = 0; var prev: Direction? = null; var prevTurn = false
            for (i in 1 until path.size) {
                val d = dirBetween(path[i - 1], path[i])
                if (prev != null && d != prev) {
                    total += TURN
                    if (!backedByWall(path[i - 1], prev)) total += NO_WALL
                    if (prevTurn) total += DOUBLE_TURN
                    prevTurn = true
                } else prevTurn = false
                prev = d
            }
            return total
        }

        private fun search(stopAtFirst: Boolean, bound: Int): List<BlockPos>? {
            var best = bound; var bestPath: List<BlockPos>? = null
            val visited = hashSetOf(start)
            val temp = ArrayList<BlockPos>(spaces.size).apply { add(start) }
            fun dfs(cur: BlockPos, lastDir: Direction?, lastTurn: Boolean, cost: Int): Boolean {
                if (cost >= best) return false
                if (visited.size == spaces.size) {
                    if (cur == end) { best = cost; bestPath = temp.toList(); return stopAtFirst }
                    return false
                }
                if (cur == end) return false
                val cands = (graph[cur] ?: emptyList())
                    .map { it to cur.relative(it) }
                    .filter { (_, p) -> p !in visited }
                    .sortedWith(compareBy(
                        { (_, p) -> (graph[p] ?: emptyList()).count { p.relative(it) !in visited } },
                        { (d, _) -> if (d == lastDir) 0 else 1 },
                    ))
                for ((d, next) in cands) {
                    var step = cost; var turned = false
                    if (lastDir != null && d != lastDir) {
                        turned = true; step += TURN
                        if (!backedByWall(cur, lastDir)) step += NO_WALL
                        if (lastTurn) step += DOUBLE_TURN
                    }
                    if (step >= best) continue
                    visited.add(next); temp.add(next)
                    val stop = connected(next, visited) && dfs(next, d, turned, step)
                    temp.removeAt(temp.size - 1); visited.remove(next)
                    if (stop) return true
                }
                return false
            }
            dfs(start, null, false, 0)
            return bestPath
        }

        fun solve(): List<BlockPos> {
            if (start !in spaces || end !in spaces) return emptyList()
            val fallback = search(true, Int.MAX_VALUE) ?: return emptyList()
            val optimized = search(false, pathCost(fallback))
            return optimized ?: fallback
        }
    }
}
