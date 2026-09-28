package fishmod.features.dungeon.puzzles

import fishmod.features.dungeon.puzzles.odin.OdinScan
import fishmod.utils.config.values.FishSettings
import fishmod.utils.dungeon.Phase
import fishmod.utils.rendering.RenderUtils
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.decoration.ItemFrame
import net.minecraft.world.item.MapItem
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import java.util.concurrent.CopyOnWriteArrayList

object TicTacToeSolver {

    private val bestMoves = CopyOnWriteArrayList<BlockPos>()
    private val aiPredictions = CopyOnWriteArrayList<BlockPos>()
    private val prefirePredictions = CopyOnWriteArrayList<BlockPos>()
    private var tickAcc = 0
    private var lastBoardKey: String? = null
    private var lastDiag: String? = null

    fun reset() {
        bestMoves.clear()
        aiPredictions.clear()
        prefirePredictions.clear()
        tickAcc = 0
        lastBoardKey = null
        lastDiag = null
        bestMovesCache.clear()
    }

    fun onTick() {
        if (!FishSettings.tttSolver || OdinScan.currentRoomName != "Tic Tac Toe") return
        if (++tickAcc < 5) return
        tickAcc = 0
        solve(Minecraft.getInstance())
    }

    fun shouldBlock(pos: BlockPos): Boolean {
        if (!FishSettings.tttSolver || !FishSettings.tttPreventMissClick) return false
        if (OdinScan.currentRoomName != "Tic Tac Toe" || Phase.inBoss()) return false
        val level = Minecraft.getInstance().level ?: return false
        if (level.getBlockState(pos).block != Blocks.STONE_BUTTON) return false
        return bestMoves.isNotEmpty() && pos !in bestMoves
    }

    fun onRenderWorld() {
        if (!FishSettings.tttSolver || OdinScan.currentRoomName != "Tic Tac Toe") return
        val rot = OdinScan.currentRoom?.rotationDeg ?: return
        for (p in bestMoves) renderBox(p, rot, FishSettings.tttColor)
        if (FishSettings.tttPrediction) {
            for (p in aiPredictions) renderBox(p, rot, 0xFFFF0000.toInt())
            for (p in prefirePredictions) renderBox(p, rot, FishSettings.tttPredictionColor)
        }
    }

    private fun solve(mc: Minecraft) {
        val level = mc.level ?: return
        val center = OdinScan.currentRoom?.centerPos ?: return
        val box = AABB(center.x - 9.0, 65.0, center.z - 9.0, center.x + 9.0, 73.0, center.z + 9.0)
        val frames = level.getEntitiesOfClass(ItemFrame::class.java, box)
            .filter { it.item.item is MapItem && it.item.has(DataComponents.MAP_ID) }

        val diag = "room=${OdinScan.currentRoom?.rotationDeg} center=$center frames=${frames.size} " + frames.joinToString(" ") { f ->
            val px = f.item.get(DataComponents.MAP_ID)?.let { level.getMapData(it) }?.colors?.get(8256)?.toInt()?.and(0xFF)
            "[${f.blockPosition().toShortString()} ${f.direction} px=$px]"
        }
        if (diag != lastDiag) { lastDiag = diag; fishmod.utils.debug.Debug.LOGGER.info("[TTT] $diag") }

        if (frames.size == 8) { reset(); return }
        if (frames.size % 2 == 0) return

        val board = CharArray(9) { UNPLAYED }
        var leftmostRow: BlockPos? = null
        var facing = 'X'
        var sign = 1

        for (frame in frames) {
            val mapId = frame.item.get(DataComponents.MAP_ID) ?: continue
            val mapData = level.getMapData(mapId) ?: continue
            val dir = frame.direction
            sign = if (dir == Direction.SOUTH || dir == Direction.WEST) -1 else 1
            val fp = frame.blockPosition()

            var row = 0
            for (i in 2 downTo 0) {
                val ri = i * sign
                val bp = if (frame.x % 0.5 == 0.0) fp.offset(ri, 0, 0)
                else { facing = 'Z'; fp.offset(0, 0, ri) }
                val b = level.getBlockState(bp).block
                if (b == Blocks.STONE_BUTTON || b == Blocks.AIR) { leftmostRow = bp; row = i; break }
            }

            val col = (72 - frame.y.toInt()).takeIf { it in 0..2 } ?: continue
            val byte = mapData.colors[8256].toInt() and 0xFF
            val idx = col * 3 + row
            if (byte == 114) board[idx] = 'X' else if (byte == 33) board[idx] = 'O'
        }

        val lr = leftmostRow ?: return
        val boardKey = String(board)
        if (boardKey == lastBoardKey) return
        lastBoardKey = boardKey
        bestMoves.clear(); aiPredictions.clear(); prefirePredictions.clear()

        val playerBest = findBestMoves(board, 'O', 'X')
        for (m in playerBest) {
            val pos = indexToPos(m, lr, facing, sign)
            if (level.getBlockState(pos).block == Blocks.STONE_BUTTON) bestMoves.add(pos)
        }

        if (FishSettings.tttPrediction && playerBest.isNotEmpty()) {
            val myMove = playerBest.first()
            val sim = board.copyOf().also { it[myMove] = 'O' }
            for (aiIdx in findBestMoves(sim, 'X', 'O')) {
                aiPredictions.add(indexToPos(aiIdx, lr, facing, sign))
                val prefireBoard = sim.copyOf().also { it[aiIdx] = 'X' }
                if (!isWon(prefireBoard)) {
                    for (preIdx in findBestMoves(prefireBoard, 'O', 'X')) {
                        val pos = indexToPos(preIdx, lr, facing, sign)
                        if (pos !in prefirePredictions) prefirePredictions.add(pos)
                    }
                }
            }
            if (prefirePredictions.size == 7) prefirePredictions.clear()
        }
    }

    private fun indexToPos(index: Int, lr: BlockPos, facing: Char, sign: Int): BlockPos {
        val r = index % 3; val c = index / 3
        val x = if (facing == 'X') lr.x - sign * r else lr.x
        val y = 72 - c
        val z = if (facing == 'Z') lr.z - sign * r else lr.z
        return BlockPos(x, y, z)
    }

    private fun renderBox(pos: BlockPos, rot: Int, argb: Int) {
        if (Minecraft.getInstance().level?.getBlockState(pos)?.block != Blocks.STONE_BUTTON) return
        val hw = 0.2; val hh = 0.13; val th = 0.2
        val bx = pos.x.toDouble(); val by = pos.y.toDouble(); val bz = pos.z.toDouble()
        val minY = by + 0.5 - hh; val maxY = by + 0.5 + hh
        val (minX, maxX, minZ, maxZ) = when (((rot % 360) + 360) % 360) {
            0 -> arrayOf(bx, bx + th, bz + 0.5 - hw, bz + 0.5 + hw)
            90 -> arrayOf(bx + 0.5 - hw, bx + 0.5 + hw, bz, bz + th)
            180 -> arrayOf(bx + 1.0 - th, bx + 1.0, bz + 0.5 - hw, bz + 0.5 + hw)
            270 -> arrayOf(bx + 0.5 - hw, bx + 0.5 + hw, bz + 1.0 - th, bz + 1.0)
            else -> return
        }
        RenderUtils.gizmoBox(AABB(minX, minY, minZ, maxX, maxY, maxZ), argb, 0)
    }

    private const val UNPLAYED = '\u0000'

    private val WIN = arrayOf(
        intArrayOf(0, 1, 2), intArrayOf(3, 4, 5), intArrayOf(6, 7, 8),
        intArrayOf(0, 3, 6), intArrayOf(1, 4, 7), intArrayOf(2, 5, 8),
        intArrayOf(0, 4, 8), intArrayOf(6, 4, 2),
    )

    private fun isWon(b: CharArray) = WIN.any { b[it[0]] != UNPLAYED && b[it[0]] == b[it[1]] && b[it[0]] == b[it[2]] }
    private fun moves(b: CharArray) = b.indices.filter { b[it] == UNPLAYED }

    private val bestMovesCache = HashMap<String, List<Int>>()

    private fun findBestMoves(board: CharArray, player: Char, ai: Char): List<Int> {
        val ms = moves(board)
        if (ms.isEmpty()) return emptyList()
        val key = "${String(board)}|$player|$ai"
        bestMovesCache[key]?.let { return it }

        var best = Double.NEGATIVE_INFINITY
        val scores = HashMap<Int, Double>()
        for (m in ms) {
            val s = minimax(board.copyOf().also { it[m] = player }, player, ai, false, 1, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)
            scores[m] = s
            if (s > best) best = s
        }
        val result = scores.filter { it.value >= best - 1e-4 }.keys.toList()
        bestMovesCache[key] = result
        return result
    }

    private fun minimax(b: CharArray, ai: Char, opp: Char, maximizing: Boolean, depth: Int, alphaIn: Double, betaIn: Double): Double {
        val won = isWon(b); val ms = moves(b)
        if (won || ms.isEmpty()) return if (won) (if (!maximizing) 1.0 / depth else -1.0 / depth) else 0.0
        var alpha = alphaIn
        var beta = betaIn
        if (maximizing) {
            var value = Double.NEGATIVE_INFINITY
            for (m in ms) {
                value = maxOf(value, minimax(b.copyOf().also { s -> s[m] = ai }, ai, opp, false, depth + 1, alpha, beta))
                alpha = maxOf(alpha, value)
                if (alpha >= beta) break
            }
            return value
        } else {
            var value = Double.POSITIVE_INFINITY
            for (m in ms) {
                value = minOf(value, minimax(b.copyOf().also { s -> s[m] = opp }, ai, opp, true, depth + 1, alpha, beta))
                beta = minOf(beta, value)
                if (alpha >= beta) break
            }
            return value
        }
    }
}
