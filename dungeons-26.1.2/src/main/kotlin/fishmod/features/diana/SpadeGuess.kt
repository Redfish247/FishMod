package fishmod.features.diana

import fishmod.compat.speed
import fishmod.utils.events.FishEvents
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt

object SpadeGuess {

    private val points = ArrayList<Vec3>()
    private var lastUseMs = 0L
    private var lastLavaMs = 0L
    private var trailGuess: BlockPos? = null

    fun reset() { points.clear(); trailGuess = null }

    fun init() {
        FishEvents.particle.register { p ->
            if (DianaSettings.dianaGuessing && DianaSettings.dianaSpadeGuess && Diana.inHub() && p.particle.type === ParticleTypes.DRIPPING_LAVA &&
                p.count == 2 && (abs(p.speed + 0.5f) < 1e-4f || testTrail(p))
            ) onPoint(Vec3(p.x, p.y, p.z))
            false
        }
        FishEvents.worldChange.register { reset(); false }
    }

    // /particle can't send a negative speed, so test worlds use speed 0 with no spread
    private fun testTrail(p: net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket) =
        Diana.testMode && p.speed == 0f && p.xDist == 0f && p.yDist == 0f && p.zDist == 0f

    fun onSpadeUse() {
        val now = System.currentTimeMillis()
        if (now - lastLavaMs < 200) return
        points.clear()
        trailGuess = null
        lastUseMs = now    }

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

    fun trailActive() = System.currentTimeMillis() - lastLavaMs < 3000

    private fun place(v: Vec3) {
        // Far guesses get a wild Y; outside the hub Y range the cleanup tick deleted it every frame (flicker/vanish)
        val y = floor(v.y - 0.5).toInt().coerceIn(DianaWaypoints.MIN_Y + 1, DianaWaypoints.MAX_Y)
        val raw = BlockPos(floor(v.x).toInt(), y, floor(v.z).toInt())
        // Rough Y (worse far away): sit it on grass so the invalid-block check doesn't keep deleting it
        val pos = if (DianaWaypoints.chunkLoaded(raw) && !DianaWaypoints.isValidBlock(raw)) DianaWaypoints.snapToGround(raw) ?: raw else raw
        if (DianaWaypoints.removedRecently(pos)) return
        // Each trail point nudges the guess a little; keep the current one unless it really moved (stops flicker)
        if (DianaWaypoints.list.any { it.type == WpType.GUESS && flat(it.pos, pos) <= 4 }) return
        DianaWaypoints.list.removeIf { it.type == WpType.GUESS && it.pos != pos && it.distTo(v) <= 32 }
        if (DianaWaypoints.list.any { it.pos == pos && (it.type == WpType.BURROW || it.type == WpType.GUESS) }) return
        ArrowGuess.clearGuesses()
        trailGuess?.let { old -> DianaWaypoints.list.removeIf { it.type == WpType.GUESS && it.pos == old } }
        DianaTest.log("spade: place $pos (raw $v)")
        DianaWaypoints.add(Waypoint(pos, WpType.GUESS, "Guess"))
        trailGuess = pos
    }

    private fun flat(a: BlockPos, b: BlockPos): Double { val dx = (a.x - b.x).toDouble(); val dz = (a.z - b.z).toDouble(); return sqrt(dx * dx + dz * dz) }

    fun guess(): Vec3? {
        if (points.size < 6) return null
        val n = points.size
        val r = fishmod.utils.TrailBezier.solve(DoubleArray(n) { points[it].x }, DoubleArray(n) { points[it].y }, DoubleArray(n) { points[it].z }, n) ?: return null
        return Vec3(r[0], r[1], r[2])
    }
}
