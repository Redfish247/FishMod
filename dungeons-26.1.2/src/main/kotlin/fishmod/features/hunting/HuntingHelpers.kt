package fishmod.features.hunting

import fishmod.compat.limiterOverridden
import fishmod.compat.speed
import fishmod.features.AlertTitles
import fishmod.utils.SkyblockLocation
import fishmod.utils.TextStrip
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.FishEvents
import fishmod.utils.rendering.FishDraw
import fishmod.utils.rendering.RenderHooks
import fishmod.utils.sound.SoundManager
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Leashable
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ambient.Bat
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.concurrent.ConcurrentLinkedQueue
import fishmod.features.fishing.FishingWorldSettings as W

// Hunting: Cinderbat/Invisibug boxes, lasso REEL alert (logic from NoFrills)
object HuntingHelpers {
    private var bats: List<Entity> = emptyList()
    private val bugParticles = ConcurrentLinkedQueue<Vec3>()
    private class Bug(var pos: Vec3, var hits: Int, var ttl: Int)
    private val bugs = ArrayList<Bug>()
    @Volatile private var shownBugs: List<Vec3> = emptyList()
    private var lastReel = 0L
    private var tick = 0

    fun init() {
        // from NoFrills: invisibug trail = single CRIT particle, no spread, forced
        FishEvents.particle.register { p ->
            if (W.invisibugHighlight && p.particle.type === ParticleTypes.CRIT && p.count == 1 && p.speed == 0f &&
                p.xDist == 0f && p.yDist == 0f && p.zDist == 0f && p.limiterOverridden) bugParticles.add(Vec3(p.x, p.y, p.z))
            false
        }
        FishEvents.worldChange.register { bugs.clear(); shownBugs = emptyList(); bats = emptyList(); false }
        ClientTickEvents.END_CLIENT_TICK.register { FishDiag.guard("HuntingHelpers.1", "hunting scan threw") { onTick() } }
        RenderHooks.GIZMO.register { _ ->
            FishDiag.guard("HuntingHelpers.2", "hunting box render threw") {
                if (W.cinderbatHighlight) for (b in bats) if (b.isAlive) FishDraw.gizmoBox(b.boundingBox, 0, W.cinderbatColor or 0xFF000000.toInt(), true)
                if (W.invisibugHighlight) for (v in shownBugs) FishDraw.gizmoBox(AABB.ofSize(v, 1.0, 1.0, 1.0), 0, W.invisibugColor or 0xFF000000.toInt(), true)
            }
        }
        // no-depth fill so invisibugs show through walls
        RenderHooks.NO_DEPTH_FILLED.register { _, ps, vc ->
            if (!W.invisibugHighlight || shownBugs.isEmpty()) return@register
            FishDiag.guard("HuntingHelpers.3", "invisibug fill render threw") {
                val a = maxOf(0x60, W.invisibugColor ushr 24)
                for (v in shownBugs) FishDraw.fillBox(ps, vc, AABB.ofSize(v, 1.0, 1.0, 1.0), (a shl 24) or (W.invisibugColor and 0xFFFFFF))
            }
        }
    }

    private fun onTick() {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val player = mc.player ?: return
        if (W.invisibugHighlight) bugTick(level) else bugParticles.clear()
        if (tick++ % 5 != 0) return
        // no entity scan while both Cinderbat and lasso are off
        if (!W.cinderbatHighlight && !W.lassoAlert) { bats = emptyList(); return }
        val crimson = W.cinderbatHighlight && SkyblockLocation.`in`(SkyblockLocation.CRIMSON_ISLE)
        val b = ArrayList<Entity>()
        val leashed = if (W.lassoAlert) Leashable.leashableLeashedTo(player).filterIsInstance<LivingEntity>() else emptyList()
        for (e in level.entitiesForRendering()) {
            // from NoFrills (HP above 4.8M) and SkyHanni MobFilter (5M max HP): Cinderbat
            if (crimson && e is Bat && (e.health > 4_800_000f || e.maxHealth >= 4_800_000f)) b += e
            if (e !is ArmorStand) continue
            val name = e.customName?.let { TextStrip.plain(it.string) } ?: continue
            // from NoFrills: lasso stand reads REEL when you can pull
            if (name == "REEL" && leashed.any { l -> val dx = l.x - e.x; val dz = l.z - e.z; dx * dx + dz * dz <= 4.0 }) reel()
        }
        bats = b
    }

    private fun reel() {
        val now = System.currentTimeMillis()
        if (now - lastReel < 1500) return
        lastReel = now
        AlertTitles.show(Component.literal("§eREEL"), Component.empty(), 1000)
        SoundManager.play(SoundManager.preset(W.lassoSound), 1f)
    }

    private fun bugTick(level: net.minecraft.client.multiplayer.ClientLevel) {
        val marsh = SkyblockLocation.`in`(SkyblockLocation.MOONGLADE_MARSH)
        while (true) {
            val v = bugParticles.poll() ?: break
            if (!marsh) continue
            // from NoFrills: a lone unnamed marker stand sits at each invisibug
            val near = level.getEntitiesOfClass(ArmorStand::class.java, AABB.ofSize(v, 1.0, 2.0, 1.0))
            if (near.size != 1 || !near[0].isMarker || near[0].customName != null) continue
            val bug = bugs.firstOrNull { it.pos.distanceTo(v) <= 0.3 }
            if (bug != null) { bug.pos = v; bug.hits++; bug.ttl = 20 } else bugs.add(Bug(v, 1, 20))
        }
        bugs.removeIf { --it.ttl <= 0 }
        shownBugs = bugs.filter { it.hits >= 4 }.map { it.pos }
    }
}
