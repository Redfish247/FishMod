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
        registry.clear(); trail.clear(); hasGuess = false; guessLabel = null; guessShown = -1; debugCount = 0
    }

    private fun on() = W.hotspotFinder && Fishing.enabled()

    private fun onFlame(x: Double, y: Double, z: Double, count: Int, speed: Float) {
        val now = System.currentTimeMillis()
        val fresh = trail.size == 0 || now - trail.lastMs > HotspotMatch.NEW_TRAIL_GAP_MS
        if (fresh) { debugCount = 0; if (W.hotspotDebug) debugRadarUse() }
        if (W.hotspotDebug && debugCount++ < DEBUG_MAX_PER_TRAIL) {
            FishMsg.send("§7[Hotspot] flame #${debugCount} count=$count speed=$speed at ${"%.1f".format(x)} ${"%.1f".format(y)} ${"%.1f".format(z)}")
        }
        // SkyHanni filters the trail to count 1 / speed 0; same assumption here (UNVERIFIED)
        if (count != 1 || speed != 0f) return
        if (!trail.add(x, y, z, now) || !trail.hasGuess()) return
        gx = trail.endX(); gy = trail.endY(); gz = trail.endZ()
        hasGuess = true; guessAt = now; guessShown = -1
    }

    private fun debugRadarUse() {
        val p = Minecraft.getInstance().player ?: return
        val held = p.mainHandItem
        FishMsg.send("§7[Hotspot] new flame trail, main hand '${held.hoverName.string}' id=${ItemTools.getId(held)}")
    }

    private fun onTick() {
        if (!on()) { if (registry.list.isNotEmpty() || hasGuess) reset(); return }
        val now = System.currentTimeMillis()
        if (hasGuess && now - guessAt > GUESS_LIFE_MS) hasGuess = false
        if (++tick % SCAN_TICKS != 0) return
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val player = mc.player ?: return
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
                    if (W.hotspotDebug) FishMsg.send("§7[Hotspot] stand #${e.id} '$name' perk '$perk' at ${e.x.toInt()} ${e.y.toInt()} ${e.z.toInt()} yaw=${"%.0f".format(e.yRot)}")
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
                    guessLabel = Component.literal("§d§lRadar guess §7~${d}m")
                }
                FishDraw.gizmoBox(AABB(gx - 0.5, gy - 0.5, gz - 0.5, gx + 0.5, gy + 1.5, gz + 0.5), (W.hotspotColor and 0x00FFFFFF) or 0x33000000, color, true)
                FishDraw.gizmoText(guessLabel!!, Vec3(gx, gy + 2.2, gz), 1.5f, -0x1, true)
            }
        }
    }
}
