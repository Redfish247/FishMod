package fishmod.features.diana

import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
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

    // Per-frame draw targets, only valid inside render()
    private lateinit var rCtx: LevelRenderContext
    private lateinit var rPs: PoseStack
    private lateinit var rVc: VertexConsumer

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { tick() }
        Events.ON_WORLD_CHANGE.register { clearAll(); false }
        // Boxes, lines and labels all go through our own see-through pass, no vanilla gizmos
        RenderingEvents.NO_DEPTH_FILLED.register { ctx, ps, vc ->
            rCtx = ctx; rPs = ps; rVc = vc
            render()
        }
    }

    private fun line(a: Vec3, b: Vec3, argb: Int, px: Float) = RenderUtils.screenLine(rPs, rVc, a, b, argb, px)

    fun inHubBounds(p: BlockPos) =
        p.x > MIN_X && p.x <= MAX_X && p.y > MIN_Y && p.y <= MAX_Y && p.z > MIN_Z && p.z <= MAX_Z

    // Burrows sit on grass with air above; an unloaded chunk is optimistically valid
    // Once seen invalid in a loaded chunk it stays invalid, so guesses don't flip as chunks load/unload (SBO does the same)
    private val invalid = HashSet<BlockPos>()

    fun isValidBlock(p: BlockPos): Boolean {
        if (!inHubBounds(p) || p in invalid) return false
        val level = Minecraft.getInstance().level ?: return true
        if (!level.hasChunk(p.x shr 4, p.z shr 4)) return true
        val st = level.getBlockState(p)
        val ok = st.`is`(Blocks.GRASS_BLOCK) || (st.isAir && Diana.clickedRecently(p))
        val valid = ok && level.getBlockState(p.above()).isAir
        if (!valid && !st.isAir) invalid.add(p.immutable())
        return valid
    }

    // Grass-with-air-above nearest p in height, searching the whole hub Y range of p's column, then rings out to 2;
    // null if the chunk isn't loaded or there's no grass nearby
    fun snapToGround(p: BlockPos): BlockPos? {
        val level = Minecraft.getInstance().level ?: return null
        if (!level.hasChunk(p.x shr 4, p.z shr 4)) return null
        for (r in 0..2) {
            var best: BlockPos? = null
            for (dx in -r..r) for (dz in -r..r) {
                if (maxOf(abs(dx), abs(dz)) != r) continue
                for (y in MAX_Y downTo MIN_Y + 1) {
                    val q = BlockPos(p.x + dx, y, p.z + dz)
                    if (!inHubBounds(q) || !level.getBlockState(q).`is`(Blocks.GRASS_BLOCK) || !level.getBlockState(q.above()).isAir) continue
                    if (best == null || abs(q.y - p.y) < abs(best.y - p.y)) best = q
                }
            }
            if (best != null) return best
        }
        return null
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
        list.clear(); removedAt.clear(); invalid.clear()
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
            if (isValidBlock(g.pos)) continue
            val snapped = snapToGround(g.pos) ?: continue
            if (snapped == g.pos || at(snapped, WpType.GUESS) != null) continue
            list.remove(g)
            add(Waypoint(snapped, WpType.GUESS, g.label).also { it.carryFrom(g) })
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
            // Past render distance, draw a shrunken copy closer along the same ray so it isn't far-clipped
            val (c, k) = RenderUtils.pullIn(w.center, eye)
            RenderUtils.fillBox(rPs, rVc, AABB.ofSize(c, k, k, k), withAlpha(rgb, a))
            if (DianaSettings.dianaBeaconBeam && w.type != WpType.SUB && d > DianaSettings.dianaBeaconDistance) {
                val bb = c.add(0.0, k * 100.5, 0.0)
                RenderUtils.fillBox(rPs, rVc, AABB.ofSize(bb, k * 0.4, k * 200, k * 0.4), withAlpha(rgb, a * 0.45f))
            }
            val text = if (w.type == WpType.SUB) (if (DianaSettings.dianaSubGuessText) "Possible" else "") else label(w, d)
            if (text.isNotEmpty()) {
                // World size per font pixel grows with distance so labels stay the same size on screen;
                // past render distance the label is pulled in along the same ray (SBO does the same)
                val (pos, pk) = RenderUtils.pullIn(Vec3(w.pos.x + 0.5, w.pos.y + 1.5 + d / 25.0, w.pos.z + 0.5), eye)
                val px = (DianaSettings.dianaTextScale * maxOf(0.035, d * 0.0035) * pk).toFloat()
                val textColor = withAlpha(0xFFFFFF, DianaSettings.dianaTextOpacity / 100f)
                RenderUtils.renderSeeThroughText(rCtx, rPs, Component.literal(colorCode(w, closest) + text), pos, px, textColor, DianaSettings.dianaTextShadow)
            }
        }

        val rare = if (DianaSettings.dianaRareMobs) newestRareMob() else null
        // Rare-mob line takes over; otherwise always fall back to the guess line so it never blinks out
        if (DianaSettings.dianaRareMobLine && rare != null && rare.distTo(eye) >= 8) {
            line(lineStart(), rare.center, withAlpha(DianaSettings.dianaColorRareMob, 1f), width)
        } else if (DianaSettings.dianaGuessLine) {
            closestTarget(eye)?.let { line(lineStart(), it.center, withAlpha(baseColor(it, closest), 1f), width) }
        }

        if (DianaSettings.dianaGuessing && DianaSettings.dianaOrderLines) renderOrder(eye, width)

        if (DianaSettings.dianaGuessing && DianaSettings.dianaSubGuesses) ArrowGuess.renderChains { a, b ->
            line(a, b, withAlpha(DianaSettings.dianaColorSubGuess, 0.6f), (width / 1.6f).coerceAtLeast(1f))
        }
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
            line(if (i == 0) lineStart() else from, to, color, (width / 1.6f).coerceIn(1f, 20f))
            left.remove(next); from = to; i++
        }
    }

    fun dist(a: BlockPos, b: BlockPos): Double {
        val dx = (a.x - b.x).toDouble(); val dy = (a.y - b.y).toDouble(); val dz = (a.z - b.z).toDouble()
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}
