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
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.client.Minecraft
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

// Hotspot finder: waypoint + distance for every hotspot armor stand that is loaded (not only within 10 blocks),
// remembered until it despawns, plus a Hotspot Radar guess. The radar draws a Bezier trail of flame particles toward the hotspot;
// TrailBezier (shared with Diana's spade) fits it and solves the endpoint. Behaviour studied from SkyHanni (LGPL, not copied) and Feesh.
// Calibrated against Eli's 1.7.46 logs (HotspotMatchTest). Still UNVERIFIED: item id HOTSPOT_RADAR, the HOTSPOT stand name format.
object HotspotFinder {
    private const val SCAN_TICKS = 20
    private const val NEAR_FLAT_DROP = 48.0
    private const val MAX_AGE_MS = 10 * 60_000L
    private const val NO_WAYPOINT_NEAR = 12
    private const val FAR_DRAW = 64.0
    private const val GUESS_LIFE_MS = 90_000L
    private const val GUESS_COVERED_RADIUS = 20.0
    private const val GUESS_REACHED = 10.0
    private const val DEBUG_MAX_PER_TRAIL = 40
    private const val RADAR_ID = "HOTSPOT_RADAR"

    private val registry = HotspotMatch.Registry()
    private val trail = HotspotMatch.Trail()
    private var hasGuess = false
    private var gx = 0.0
    private var gy = 0.0
    private var gz = 0.0
    private var guessAt = 0L
    private var guessLabel: Component? = null
    private var guessShown = -1
    private var dirty = false
    private var debugCount = 0
    private var tick = 0
    // The guess only exists while a radar is in the inventory and was right-clicked within the last minute
    private const val RADAR_USE_WINDOW_MS = 60_000L
    private var lastRadarUseMs = 0L
    // Last solved trail, kept to log the error when the real stand shows up (debug only)
    private var calibGuess = false
    private var calibX = 0.0
    private var calibY = 0.0
    private var calibZ = 0.0
    private var calibAt = 0L
    private val probePos = net.minecraft.core.BlockPos.MutableBlockPos()

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { onTick() }
        FishEvents.worldChange.register { reset(); false }
        FishEvents.particle.register { p ->
            if (W.hotspotFinder && W.hotspotRadar && Fishing.enabled() && p.particle.type === ParticleTypes.FLAME) {
                onFlame(p.x, p.y, p.z, p.count, p.speed)
            }
            false
        }
        UseItemCallback.EVENT.register(UseItemCallback { player, level, hand ->
            if (!level.isClientSide || !W.hotspotFinder || !W.hotspotRadar || !Fishing.enabled()) return@UseItemCallback InteractionResult.PASS
            if (ItemTools.getId(player.getItemInHand(hand)) != RADAR_ID) return@UseItemCallback InteractionResult.PASS
            lastRadarUseMs = System.currentTimeMillis()
            // Using the radar again while its trail is still being drawn restarts it; SkyHanni cancels that click, so do we
            if (trail.size > 0 && System.currentTimeMillis() - trail.lastMs < HotspotMatch.REUSE_GUARD_MS) return@UseItemCallback InteractionResult.FAIL
            trail.clear(); hasGuess = false; dirty = false
            InteractionResult.PASS
        })
        RenderHooks.GIZMO.register { _ -> render() }
    }

    private fun reset() {
        registry.clear(); trail.clear(); hasGuess = false; dirty = false; guessLabel = null; guessShown = -1; debugCount = 0; calibGuess = false
    }

    private fun on() = W.hotspotFinder && Fishing.enabled()

    private fun radarRecentlyUsed(now: Long) = now - lastRadarUseMs <= RADAR_USE_WINDOW_MS

    private fun hasRadarInInventory(p: net.minecraft.world.entity.player.Player): Boolean {
        for (i in 0 until p.inventory.containerSize) if (ItemTools.getId(p.inventory.getItem(i)) == RADAR_ID) return true
        return false
    }

    private fun onFlame(x: Double, y: Double, z: Double, count: Int, speed: Float) {
        val now = System.currentTimeMillis()
        // Flames from other players' radars or other sources must not make a guess
        if (!radarRecentlyUsed(now)) return
        val fresh = trail.size == 0 || now - trail.lastMs > HotspotMatch.NEW_TRAIL_GAP_MS
        if (fresh) { debugCount = 0; if (W.hotspotDebug) debugRadarUse() }
        if (W.hotspotDebug && debugCount++ < DEBUG_MAX_PER_TRAIL) {
            FishMsg.send("§7[Hotspot] flame #${debugCount} count=$count speed=$speed at ${"%.1f".format(x)} ${"%.1f".format(y)} ${"%.1f".format(z)}")
        }
        // Trail points are count 1 / speed 0; other flames (count 6 ...) are something else (seen in a real log)
        if (count != 1 || speed != 0f) return
        if (trail.add(x, y, z, now)) { dirty = true; guessAt = now }
    }

    private fun debugRadarUse() {
        val p = Minecraft.getInstance().player ?: return
        val held = p.mainHandItem
        FishMsg.send("§7[Hotspot] new flame trail, main hand '${held.hoverName.string}' id=${ItemTools.getId(held)}")
    }

    // Solve the trail whenever new points arrived (at most once per tick)
    private fun solve(level: net.minecraft.world.level.Level, now: Long) {
        dirty = false
        val r = trail.solve() ?: return
        val ex = trail.endX(); val ez = trail.endZ()
        if (!HotspotMatch.sane(r[0], r[1], r[2], ex, ez, level.minY.toDouble(), (level.minY + level.height).toDouble())) {
            if (W.hotspotDebug) FishMsg.send("§7[Hotspot] ${trail.size} pts: guess ${r[0].toInt()} ${r[1].toInt()} ${r[2].toInt()} rejected (out of range)")
            hasGuess = false
            return
        }
        // Snap Y to the water/lava surface under the solved point when one is found just below
        val probe = HotspotMatch.FluidProbe { x, y, z ->
            probePos.set(Math.floor(x).toInt(), Math.floor(y).toInt(), Math.floor(z).toInt())
            level.hasChunkAt(probePos) && !level.getFluidState(probePos).isEmpty
        }
        val sy = HotspotMatch.surfaceY(probe, r[0], r[1] + 2.0, r[2])
        gx = r[0]; gy = sy ?: r[1]; gz = r[2]
        hasGuess = true; guessShown = -1
        calibGuess = true; calibX = gx; calibY = gy; calibZ = gz; calibAt = now
        if (W.hotspotDebug) {
            val p = Minecraft.getInstance().player
            val d = if (p != null) HotspotMatch.distance(p.x, p.y, p.z, gx, gy, gz) else -1
            FishMsg.send("§7[Hotspot] ${trail.size} pts, end ${ex.toInt()} ${trail.endY().toInt()} ${ez.toInt()} -> solved ${gx.toInt()} ${gy.toInt()} ${gz.toInt()} (${if (sy != null) "surface" else "raw y"}, ${d}m)")
        }
    }

    private fun onTick() {
        if (!on()) { if (registry.list.isNotEmpty() || hasGuess) reset(); return }
        val now = System.currentTimeMillis()
        if (hasGuess && now - guessAt > GUESS_LIFE_MS) hasGuess = false
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val player = mc.player ?: return
        if (hasGuess && (!radarRecentlyUsed(now) || !hasRadarInInventory(player))) { hasGuess = false; dirty = false }
        if (dirty) {
            try { solve(level, now) } catch (t: Throwable) { dirty = false; FishDiag.fail("HotspotFinder.3", "radar solve failed", t) }
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
                    if (W.hotspotDebug) {
                        FishMsg.send("§7[Hotspot] stand #${e.id} '$name' perk '$perk' at ${e.x.toInt()} ${e.y.toInt()} ${e.z.toInt()} yaw=${"%.0f".format(e.yRot)}")
                        if (calibGuess && now - calibAt < 60_000L) {
                            val flat = Math.sqrt(sq(e.x - calibX) + sq(e.z - calibZ))
                            FishMsg.send("§7[Hotspot] calibration: solved guess was ${"%.1f".format(flat)} flat / ${"%.1f".format(Math.abs(e.y - calibY))} vertical off the real stand")
                        }
                    }
                    calibGuess = false
                } else {
                    registry.see(e.id, e.x, e.y, e.z, "", now)
                }
            }
            registry.prune({ id -> val en = level.getEntity(id); en is ArmorStand && en.customName?.string?.let { FishingAlertText.isHotspotName(it) } == true },
                player.x, player.z, NEAR_FLAT_DROP, now, MAX_AGE_MS)
            if (hasGuess) {
                val l = registry.list
                for (i in l.indices) if (HotspotMatch.covered(gx, gz, l[i].x, l[i].z, GUESS_COVERED_RADIUS)) { hasGuess = false; break }
                val dx = gx - player.x; val dz = gz - player.z
                if (hasGuess && trail.settled(now) && dx * dx + dz * dz < GUESS_REACHED * GUESS_REACHED) hasGuess = false
            }
        } catch (t: Throwable) {
            FishDiag.fail("HotspotFinder.1", "hotspot scan failed", t)
        }
    }

    private fun sq(v: Double) = v * v

    // Far markers are drawn at most FAR_DRAW blocks out along the same direction (distance in the label stays real).
    private fun capped(p: net.minecraft.world.entity.player.Player, x: Double, y: Double, z: Double, d: Int): DoubleArray {
        if (d <= FAR_DRAW) return doubleArrayOf(x, y, z)
        val ex = p.x; val ey = p.eyeY; val ez = p.z
        val f = FAR_DRAW / Math.sqrt(sq(x - ex) + sq(y - ey) + sq(z - ez))
        return doubleArrayOf(ex + (x - ex) * f, ey + (y - ey) * f, ez + (z - ez) * f)
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
                val c = capped(p, k.x, k.y, k.z, d)
                FishDraw.gizmoBox(AABB(c[0] - 0.5, c[1] - 1.0, c[2] - 0.5, c[0] + 0.5, c[1] + 1.0, c[2] + 0.5), fill, color, true)
                FishDraw.gizmoText(k.label as Component, Vec3(c[0], c[1] + 1.6, c[2]), 1.5f, -0x1, true)
            }
            if (hasGuess) {
                val d = HotspotMatch.distance(p.x, p.y, p.z, gx, gy, gz)
                if (guessShown != d || guessLabel == null) {
                    guessShown = d
                    guessLabel = Component.literal("§d§lRadar guess §7${d}m")
                }
                val c = capped(p, gx, gy, gz, d)
                FishDraw.gizmoBox(AABB(c[0] - 0.5, c[1] - 0.5, c[2] - 0.5, c[0] + 0.5, c[1] + 1.5, c[2] + 0.5), (W.hotspotColor and 0x00FFFFFF) or 0x33000000, color, true)
                FishDraw.gizmoText(guessLabel!!, Vec3(c[0], c[1] + 2.2, c[2]), 1.5f, -0x1, true)
            }
        }
    }
}
