package fishmod.features.fishing

import fishmod.compat.speed
import fishmod.features.fishing.FishingWorldSettings as W
import fishmod.utils.FishMsg
import fishmod.utils.data.ItemTools
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.FishEvents
import fishmod.utils.rendering.FishDraw
import fishmod.utils.rendering.RenderHooks
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

// Hotspot finder: waypoint + distance for every hotspot armor stand that is loaded (not only within 10 blocks),
// remembered until it despawns, plus a guess from the Hotspot Radar's flame trail.
// Behaviour studied from SkyHanni (FishingHotspotRadar, LGPL: not copied) and Feesh. ALL UNVERIFIED in game:
// the radar item id HOTSPOT_RADAR, FLAME particles with count 1 / speed 0 forming the trail, the trail ending at the hotspot,
// and the HOTSPOT stand name format (FishingAlertText.isHotspotName). Turn on "Debug Raw Signals" to see what is really sent.
object HotspotFinder {
    private const val SCAN_TICKS = 20
    private const val NEAR_FLAT_DROP = 48.0
    private const val MAX_AGE_MS = 10 * 60_000L
    private const val NO_WAYPOINT_NEAR = 12
    private const val GUESS_LIFE_MS = 90_000L
    private const val GUESS_COVERED_RADIUS = 20.0
    private const val GUESS_REACHED = 10.0
    private const val DEBUG_MAX_PER_TRAIL = 40

    private val registry = HotspotMatch.Registry()
    private val trail = HotspotMatch.Trail()
    private var hasGuess = false
    private var gx = 0.0
    private var gy = 0.0
    private var gz = 0.0
    // Finished trails (one per radar use), used to triangulate; the live trail is projected on its own
    private val shots = ArrayList<HotspotMatch.Line>()
    private var committed = true
    private var dirty = false
    private var trailEx = 0.0
    private var trailEy = 0.0
    private var trailEz = 0.0
    private var bandNX = 0.0
    private var bandNZ = 0.0
    private var bandFX = 0.0
    private var bandFZ = 0.0
    private var lastStandY = Double.NaN
    // Last projected trail, kept to calibrate the range ratio when the real stand shows up (debug log only)
    private var lastLen = 0.0
    private var lastEndX = 0.0
    private var lastEndZ = 0.0
    private var lastTrailAt = 0L
    private val probePos = net.minecraft.core.BlockPos.MutableBlockPos()
    private var guessAt = 0L
    private var guessLabel: Component? = null
    private var guessShown = -1
    private var debugCount = 0
    private var tick = 0

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { onTick() }
        FishEvents.worldChange.register { reset(); false }
        FishEvents.particle.register { p ->
            if (W.hotspotFinder && W.hotspotRadar && Fishing.enabled() && p.particle.type === ParticleTypes.FLAME) {
                onFlame(p.x, p.y, p.z, p.count, p.speed)
            }
            false
        }
        RenderHooks.GIZMO.register { _ -> render() }
    }

    private fun reset() {
        registry.clear(); trail.clear(); shots.clear(); committed = true; dirty = false; hasGuess = false; guessLabel = null; guessShown = -1; debugCount = 0
    }

    private fun on() = W.hotspotFinder && Fishing.enabled()

    private fun onFlame(x: Double, y: Double, z: Double, count: Int, speed: Float) {
        val now = System.currentTimeMillis()
        val fresh = trail.size == 0 || now - trail.lastMs > HotspotMatch.NEW_TRAIL_GAP_MS
        if (fresh) {
            commitTrail(); debugCount = 0; if (W.hotspotDebug) debugRadarUse()
        }
        if (W.hotspotDebug && debugCount++ < DEBUG_MAX_PER_TRAIL) {
            FishMsg.send("§7[Hotspot] flame #${debugCount} count=$count speed=$speed at ${"%.1f".format(x)} ${"%.1f".format(y)} ${"%.1f".format(z)}")
        }
        // SkyHanni filters the trail to count 1 / speed 0; same assumption here (UNVERIFIED)
        if (count != 1 || speed != 0f) return
        if (!trail.add(x, y, z, now)) return
        committed = false; dirty = true; guessAt = now
    }

    // Keep a finished trail's heading as a shot for triangulation (called when the next trail starts or this one settles)
    private fun commitTrail() {
        if (committed) return
        committed = true
        if (!trail.hasGuess()) return
        val l = trail.fit() ?: return
        shots.add(l)
        while (shots.size > HotspotMatch.MAX_SHOTS) shots.removeAt(0)
    }

    private val lines = ArrayList<HotspotMatch.Line>()

    // Project the live trail; with earlier shots from other spots, triangulate. Runs at most once per tick, only after new points.
    private fun recompute(level: net.minecraft.world.level.Level, player: net.minecraft.world.entity.player.Player, now: Long) {
        dirty = false
        val cur = if (trail.hasGuess()) trail.fit() else null
        lines.clear()
        for (i in shots.indices) {
            val sh = shots[i]
            // A shot from (almost) the same spot as the live trail adds nothing and would give a bogus crossing
            if (cur != null && !committed && sq(sh.ox - cur.ox) + sq(sh.oz - cur.oz) < sq(HotspotMatch.MIN_SHOT_SEP)) continue
            lines.add(sh)
        }
        if (cur != null && !committed) lines.add(cur)
        if (lines.isEmpty()) return
        val last = cur ?: lines[lines.size - 1]
        // Hotspot is right here (or trail too short to trust): nothing to project
        if (sq(last.ox - player.x) + sq(last.oz - player.z) < sq(HotspotMatch.NEAR_END_BLOCKS)) { hasGuess = false; return }
        val probe = HotspotMatch.FluidProbe { x, y, z ->
            probePos.set(Math.floor(x).toInt(), Math.floor(y).toInt(), Math.floor(z).toInt())
            level.hasChunkAt(probePos) && !level.getFluidState(probePos).isEmpty
        }
        // Fallback Y when no fluid is under the projected point: your hook's height, else the last hotspot stand, else your feet
        val hook = player.fishing
        val fbY = if (hook != null) hook.y else if (!lastStandY.isNaN()) lastStandY else player.y - 1.0
        val single = HotspotMatch.project(last, probe, fbY)
        var est = single
        val tri = HotspotMatch.triangulate(lines)
        // Never let a triangulation far from the player replace the single-trail guess
        if (tri != null && sq(tri.x - player.x) + sq(tri.z - player.z) <= sq(HotspotMatch.MAX_CROSS_RANGE)) {
            val ty = HotspotMatch.surfaceY(probe, tri.x, last.oy + 3.0, tri.z) ?: fbY
            est = HotspotMatch.Estimate(tri.x, ty, tri.z, HotspotMatch.Kind.TRIANGULATED)
        }
        gx = est.x; gy = est.y; gz = est.z
        bandNX = est.nearX; bandNZ = est.nearZ; bandFX = est.farX; bandFZ = est.farZ
        trailEx = last.ox; trailEy = last.oy; trailEz = last.oz
        lastLen = last.len; lastEndX = last.ox; lastEndZ = last.oz; lastTrailAt = now
        hasGuess = true; guessShown = -1
        if (W.hotspotDebug) {
            FishMsg.send("§7[Hotspot] trail ${trail.size} pts len=${"%.1f".format(last.len)} end ${last.ox.toInt()} ${last.oy.toInt()} ${last.oz.toInt()} dir=${"%.2f".format(last.dx)},${"%.2f".format(last.dz)} slope=${"%.2f".format(last.slope)} " +
                "shots=${shots.size} single=${single.kind} ${single.x.toInt()} ${single.y.toInt()} ${single.z.toInt()}" +
                (if (tri != null) " tri=${tri.x.toInt()} ${tri.z.toInt()}" else "") + " -> ${gx.toInt()} ${gy.toInt()} ${gz.toInt()} (${HotspotMatch.distance(player.x, player.y, player.z, gx, gy, gz)}m)")
        }
    }

    private fun sq(v: Double) = v * v

    private fun onTick() {
        if (!on()) { if (registry.list.isNotEmpty() || hasGuess) reset(); return }
        val now = System.currentTimeMillis()
        if (hasGuess && now - guessAt > GUESS_LIFE_MS) { hasGuess = false; shots.clear() }
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val player = mc.player ?: return
        if (!committed && trail.settled(now)) commitTrail()
        if (dirty) {
            try { recompute(level, player, now) } catch (t: Throwable) { dirty = false; FishDiag.fail("HotspotFinder.3", "radar projection failed", t) }
        }
        if (++tick % SCAN_TICKS != 0) return
        try {
            var stands: ArrayList<ArmorStand>? = null
            for (e in level.entitiesForRendering()) {
                if (e !is ArmorStand || !e.hasCustomName()) continue
                val name = e.customName?.string ?: continue
                if (!FishingAlertText.isHotspotName(name)) continue
                val known = registry.find(e.id)
                if (known == null) {
                    if (stands == null) {
                        stands = ArrayList()
                        for (s in level.entitiesForRendering()) if (s is ArmorStand && s.hasCustomName()) stands.add(s)
                    }
                    val perk = FishingAlertsWorld.perkBelow(e, stands)
                    registry.see(e.id, e.x, e.y, e.z, perk, now)
                    lastStandY = e.y
                    if (W.hotspotDebug && lastLen > 0.0 && now - lastTrailAt < 60_000L) {
                        val past = Math.sqrt(sq(e.x - lastEndX) + sq(e.z - lastEndZ))
                        FishMsg.send("§7[Hotspot] calibration: trail len=${"%.1f".format(lastLen)} stand ${"%.1f".format(past)} past its end, ratio=${"%.2f".format(past / lastLen)}")
                        lastLen = 0.0
                    }
                    if (W.hotspotDebug) FishMsg.send("§7[Hotspot] stand #${e.id} '$name' perk '$perk' at ${e.x.toInt()} ${e.y.toInt()} ${e.z.toInt()} yaw=${"%.0f".format(e.yRot)}")
                } else {
                    registry.see(e.id, e.x, e.y, e.z, "", now)
                }
            }
            registry.prune({ id -> val en = level.getEntity(id); en is ArmorStand && en.customName?.string?.let { FishingAlertText.isHotspotName(it) } == true },
                player.x, player.z, NEAR_FLAT_DROP, now, MAX_AGE_MS)
            if (hasGuess) {
                val l = registry.list
                for (i in l.indices) if (HotspotMatch.covered(gx, gz, l[i].x, l[i].z, GUESS_COVERED_RADIUS)) { hasGuess = false; shots.clear(); break }
                val dx = gx - player.x; val dz = gz - player.z
                if (hasGuess && trail.settled(now) && dx * dx + dz * dz < GUESS_REACHED * GUESS_REACHED) { hasGuess = false; shots.clear() }
            }
        } catch (t: Throwable) {
            FishDiag.fail("HotspotFinder.1", "hotspot scan failed", t)
        }
    }

    private fun render() {
        if (!on()) return
        val l = registry.list
        if (l.isEmpty() && !hasGuess) return
        val p = Minecraft.getInstance().player ?: return
        FishDiag.guard("HotspotFinder.2", "hotspot waypoint render failed") {
            val color = W.hotspotColor or 0xFF000000.toInt()
            val fill = (W.hotspotColor and 0x00FFFFFF) or 0x55000000
            for (i in l.indices) {
                val k = l[i]
                val d = HotspotMatch.distance(p.x, p.y, p.z, k.x, k.y, k.z)
                if (d < NO_WAYPOINT_NEAR) continue
                if (k.shownDist != d || k.label == null) {
                    k.shownDist = d
                    k.label = Component.literal("§6§lHotspot §f${k.perk} §7${d}m")
                }
                FishDraw.gizmoBox(AABB(k.x - 0.5, k.y - 1.0, k.z - 0.5, k.x + 0.5, k.y + 1.0, k.z + 0.5), fill, color, true)
                FishDraw.gizmoText(k.label as Component, Vec3(k.x, k.y + 1.6, k.z), 1.5f, -0x1, true)
            }
            if (hasGuess) {
                val d = HotspotMatch.distance(p.x, p.y, p.z, gx, gy, gz)
                if (guessShown != d || guessLabel == null) {
                    guessShown = d
                    guessLabel = Component.literal("§d§l~Hotspot §7~${d}m §8(${bandRange(p)})")
                }
                FishDraw.gizmoBox(AABB(gx - 0.5, gy - 0.5, gz - 0.5, gx + 0.5, gy + 1.5, gz + 0.5), (W.hotspotColor and 0x00FFFFFF) or 0x33000000, color, true)
                FishDraw.gizmoLine(Vec3(trailEx, trailEy, trailEz), Vec3(bandNX, gy, bandNZ), color, 2f, true)
                if (bandFX != bandNX || bandFZ != bandNZ) FishDraw.gizmoLine(Vec3(bandNX, gy, bandNZ), Vec3(bandFX, gy, bandFZ), color, 8f, true)
                FishDraw.gizmoText(guessLabel!!, Vec3(gx, gy + 2.2, gz), 1.5f, -0x1, true)
            }
        }
    }

    private fun bandRange(p: net.minecraft.world.entity.player.Player): String {
        val n = Math.sqrt(sq(bandNX - p.x) + sq(bandNZ - p.z)).toInt()
        val f = Math.sqrt(sq(bandFX - p.x) + sq(bandFZ - p.z)).toInt()
        return if (n == f) "" else "${minOf(n, f)}-${maxOf(n, f)}m"
    }
}
