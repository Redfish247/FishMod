package fishmod.features.diana

import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.sqrt

enum class WpType { BURROW, GUESS, ARROW, SUB, RARE, WORLD }

enum class BurrowType(val label: String) { START("Start"), MOB("Mob"), TREASURE("Treasure") }

class Waypoint(val pos: BlockPos, var type: WpType, var label: String) {
    var burrowType: BurrowType? = null
    var timesDug = 0
    var clicked = false
    val created = System.currentTimeMillis()
    var expiresAt = created + 1_800_000L
    var hiddenUntil = 0L
    var warpHint: String? = null

    val center: Vec3 get() = Vec3(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
    fun hidden() = System.currentTimeMillis() < hiddenUntil
    fun distTo(v: Vec3) = center.distanceTo(v)

    fun carryFrom(o: Waypoint) {
        timesDug = maxOf(timesDug, o.timesDug)
        clicked = clicked || o.clicked
    }
}

object DianaWaypoints {

    // Hub play area, same bounds Hypixel spawns burrows in
    const val MIN_X = -283; const val MIN_Y = 60; const val MIN_Z = -208
    const val MAX_X = 175; const val MAX_Y = 105; const val MAX_Z = 205

    val list = CopyOnWriteArrayList<Waypoint>()
    private val removedAt = HashMap<BlockPos, Long>()

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { tick() }
        Events.ON_WORLD_CHANGE.register { clearAll(); false }
        RenderingEvents.GIZMO.register { _ -> render() }
    }

    fun inHubBounds(p: BlockPos) =
        p.x > MIN_X && p.x <= MAX_X && p.y > MIN_Y && p.y <= MAX_Y && p.z > MIN_Z && p.z <= MAX_Z

    // Burrows sit on grass with air above; an unloaded chunk is optimistically valid
    fun isValidBlock(p: BlockPos): Boolean {
        if (!inHubBounds(p)) return false
        val level = Minecraft.getInstance().level ?: return true
        if (!level.hasChunk(p.x shr 4, p.z shr 4)) return true
        val st = level.getBlockState(p)
        val ok = st.`is`(Blocks.GRASS_BLOCK) || (st.isAir && Diana.clickedRecently(p))
        return ok && level.getBlockState(p.above()).isAir
    }

    fun at(p: BlockPos, vararg types: WpType): Waypoint? = list.firstOrNull { it.pos == p && it.type in types }

    fun findDiggable(p: BlockPos): Waypoint? =
        at(p, WpType.BURROW) ?: at(p, WpType.ARROW) ?: at(p, WpType.GUESS) ?: at(p, WpType.SUB)

    fun add(w: Waypoint): Waypoint { list.add(w); return w }

    fun remove(w: Waypoint) { list.remove(w) }

    fun removeAt(p: BlockPos, vararg types: WpType) { list.removeIf { it.pos == p && it.type in types } }

    fun markRemoved(p: BlockPos) { removedAt[p] = System.currentTimeMillis() }

    fun removedRecently(p: BlockPos) = removedAt[p]?.let { System.currentTimeMillis() - it < 1000 } ?: false

    fun addRareMob(p: BlockPos, label: String, ttlMs: Long): Waypoint =
        add(Waypoint(p, WpType.RARE, label).also { it.expiresAt = System.currentTimeMillis() + ttlMs })

    fun addWorld(p: BlockPos, label: String, ttlMs: Long): Waypoint =
        add(Waypoint(p, WpType.WORLD, label).also { it.expiresAt = System.currentTimeMillis() + ttlMs })

    fun removeRareMobsNear(v: Vec3, r: Double) { list.removeIf { it.type == WpType.RARE && it.distTo(v) <= r } }

    fun rareMobNear(v: Vec3, r: Double) = list.any { it.type == WpType.RARE && it.distTo(v) <= r }

    fun newestRareMob(): Waypoint? = list.filter { it.type == WpType.RARE }.maxByOrNull { it.created }

    fun targets(): List<Waypoint> =
        if (!DianaSettings.dianaGuessing) emptyList() else list.filter { !it.hidden() && (it.type == WpType.BURROW || it.type == WpType.ARROW || it.type == WpType.GUESS) }

    fun closestTarget(from: Vec3): Waypoint? = targets().minByOrNull { it.distTo(from) }

    fun clearAll() {
        list.clear(); removedAt.clear()
        ArrowGuess.reset(); SpadeGuess.reset(); BurrowDetector.reset()
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        removedAt.entries.removeIf { now - it.value > 1000 }
        list.removeIf { now > it.expiresAt || (it.type != WpType.WORLD && it.type != WpType.RARE && !inHubBounds(it.pos)) }
        // Spade guesses lose to any burrow/arrow within 32 blocks, which inherits their dig state
        for (g in list.filter { it.type == WpType.GUESS }) {
            val better = list.firstOrNull { (it.type == WpType.BURROW || it.type == WpType.ARROW) && it.center.distanceTo(g.center) <= 32 }
            if (better != null) { better.carryFrom(g); list.remove(g); continue }
            if (!isValidBlock(g.pos)) list.remove(g)
        }
        // Arrow on a known burrow merges into it
        for (a in list.filter { it.type == WpType.ARROW || it.type == WpType.SUB }) {
            val b = at(a.pos, WpType.BURROW) ?: continue
            b.carryFrom(a); list.remove(a)
        }
    }

    // ---- rendering ----

    private fun visible(): Boolean = Diana.active() || Diana.testMode

    private fun baseColor(w: Waypoint, closest: Waypoint?): Int = when (w.type) {
        WpType.BURROW -> when (w.burrowType) {
            BurrowType.START -> DianaSettings.dianaColorStart
            BurrowType.MOB -> DianaSettings.dianaColorMob
            else -> DianaSettings.dianaColorTreasure
        }
        WpType.GUESS, WpType.ARROW ->
            if (w === closest) DianaSettings.dianaColorClosestGuess else DianaSettings.dianaColorOtherGuess
        WpType.SUB -> DianaSettings.dianaColorSubGuess
        WpType.RARE -> DianaSettings.dianaColorRareMob
        WpType.WORLD -> DianaSettings.dianaColorOther
    }

    private fun opacity(dist: Double): Float {
        if (!DianaSettings.dianaDynamicOpacity) return (DianaSettings.dianaOpacity / 100f).coerceIn(0.05f, 1f)
        return when {
            dist <= 4.5 -> 0.2f
            dist >= 100 -> 1f
            else -> (0.2 + 0.8 * (dist - 4.5) / 95.5).toFloat()
        }
    }

    private fun withAlpha(rgb: Int, a: Float) = ((a.coerceIn(0f, 1f) * 255).toInt() shl 24) or (rgb and 0xFFFFFF)

    private fun eye(): Vec3? = Minecraft.getInstance().gameRenderer.mainCamera.position()

    private fun lineStart(): Vec3 {
        val cam = Minecraft.getInstance().gameRenderer.mainCamera
        return cam.position().add(Vec3.directionFromRotation(cam.xRot(), cam.yRot()))
    }

    private fun label(w: Waypoint, dist: Double): String {
        val sb = StringBuilder(w.label)
        w.warpHint?.let { sb.append(" §7(warp §e").append(it).append("§7)") }
        val cutoff = DianaSettings.dianaDistanceCutoff
        if (cutoff == 0 || dist > cutoff) sb.append(" §b[").append(dist.toInt()).append("m]")
        val diggable = w.type == WpType.BURROW && w.burrowType != BurrowType.START ||
            w.type == WpType.ARROW || w.type == WpType.GUESS
        if (DianaSettings.dianaShowTimesDug && diggable && w.timesDug > 0 && (cutoff == 0 || dist <= cutoff)) {
            val c = if (w.timesDug >= 1) "§6" else "§e"
            sb.append(" §7[").append(c).append(w.timesDug).append("§7/§a2§7]")
        }
        return sb.toString()
    }

    private fun render() {
        if (list.isEmpty() || !visible()) return
        val eye = eye() ?: return
        val closest = list.filter { it.type == WpType.GUESS || it.type == WpType.ARROW }.minByOrNull { it.distTo(eye) }
        val width = DianaSettings.dianaLineWidth.toFloat()

        for (w in list) {
            if (w.hidden()) continue
            if (!DianaSettings.dianaGuessing && w.type != WpType.RARE && w.type != WpType.WORLD) continue
            if (!DianaSettings.dianaRareMobs && (w.type == WpType.RARE || w.type == WpType.WORLD)) continue
            if (w.type == WpType.SUB && !DianaSettings.dianaSubGuesses) continue
            val d = w.distTo(eye)
            val a = opacity(d)
            val rgb = baseColor(w, closest)
            val box = AABB(w.pos.x.toDouble(), w.pos.y.toDouble(), w.pos.z.toDouble(), w.pos.x + 1.0, w.pos.y + 1.0, w.pos.z + 1.0)
            RenderUtils.gizmoBox(box, withAlpha(rgb, a), 0, true)
            if (DianaSettings.dianaBeaconBeam && w.type != WpType.SUB && d > DianaSettings.dianaBeaconDistance) {
                val beam = AABB(w.pos.x + 0.3, w.pos.y + 1.0, w.pos.z + 0.3, w.pos.x + 0.7, w.pos.y + 200.0, w.pos.z + 0.7)
                RenderUtils.gizmoBox(beam, withAlpha(rgb, a * 0.45f), 0, true)
            }
            val text = if (w.type == WpType.SUB) (if (DianaSettings.dianaSubGuessText) "Possible" else "") else label(w, d)
            if (text.isNotEmpty()) {
                // Grows with distance so labels stay roughly the same size on screen
                val scale = (DianaSettings.dianaTextScale * maxOf(1.2, d * 0.12)).toFloat()
                val textColor = withAlpha(0xFFFFFF, DianaSettings.dianaTextOpacity / 100f)
                val pos = Vec3(w.pos.x + 0.5, w.pos.y + 1.5 + d / 25.0, w.pos.z + 0.5)
                val col = colorCode(w, closest)
                if (DianaSettings.dianaTextShadow) {
                    val off = shadowOffset(pos, eye, scale)
                    val shadowA = (DianaSettings.dianaTextOpacity / 100f) * 0.8f
                    RenderUtils.gizmoText(Component.literal(text.replace(Regex("§."), "")), pos.add(off), scale, withAlpha(0x202020, shadowA), true)
                }
                RenderUtils.gizmoText(Component.literal(col + text), pos, scale, textColor, true)
            }
        }

        val rare = if (DianaSettings.dianaRareMobs) newestRareMob() else null
        // Rare-mob line takes over; otherwise always fall back to the guess line so it never blinks out
        if (DianaSettings.dianaRareMobLine && rare != null && rare.distTo(eye) >= 8) {
            RenderUtils.gizmoLine(lineStart(), rare.center, withAlpha(DianaSettings.dianaColorRareMob, 1f), width, true)
        } else if (DianaSettings.dianaGuessLine) {
            closestTarget(eye)?.let { RenderUtils.gizmoLine(lineStart(), it.center, withAlpha(baseColor(it, closest), 1f), width, true) }
        }

        if (DianaSettings.dianaGuessing && DianaSettings.dianaOrderLines) renderOrder(eye, width)

        if (DianaSettings.dianaGuessing && DianaSettings.dianaSubGuesses) ArrowGuess.renderChains { a, b ->
            RenderUtils.gizmoLine(a, b, withAlpha(DianaSettings.dianaColorSubGuess, 0.6f), (width / 1.6f).coerceAtLeast(1f), true)
        }
    }

    // Down-right in screen space and slightly behind, so the dark copy reads as a drop shadow
    private fun shadowOffset(pos: Vec3, eye: Vec3, scale: Float): Vec3 {
        val fwd = pos.subtract(eye).normalize()
        val right = fwd.cross(Vec3(0.0, 1.0, 0.0)).normalize()
        val up = right.cross(fwd).normalize()
        val px = scale * 0.025
        return right.scale(px).subtract(up.scale(px)).add(fwd.scale(0.05))
    }

    private fun colorCode(w: Waypoint, closest: Waypoint?): String = when (w.type) {
        WpType.BURROW -> when (w.burrowType) { BurrowType.START -> "§a"; BurrowType.MOB -> "§c"; else -> "§6" }
        WpType.GUESS, WpType.ARROW -> if (w === closest) "§d" else "§b"
        WpType.RARE -> "§e"
        WpType.WORLD -> "§9"
        WpType.SUB -> "§7"
    }

    // Greedy nearest-neighbour path from the player; first leg always, legs 2-3 only if within 50
    private fun renderOrder(eye: Vec3, width: Float) {
        val left = targets().toMutableList()
        var from = eye
        val color = withAlpha(DianaSettings.dianaColorOrderLine, 0.45f)
        var i = 0
        while (left.isNotEmpty() && i < 3) {
            val next = left.minByOrNull { it.center.distanceTo(from) }!!
            if (i > 0 && next.distTo(eye) > 50) break
            val to = next.center
            RenderUtils.gizmoLine(if (i == 0) lineStart() else from, to, color, (width / 1.6f).coerceIn(1f, 20f), true)
            left.remove(next); from = to; i++
        }
    }

    fun dist(a: BlockPos, b: BlockPos): Double {
        val dx = (a.x - b.x).toDouble(); val dy = (a.y - b.y).toDouble(); val dz = (a.z - b.z).toDouble()
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}
