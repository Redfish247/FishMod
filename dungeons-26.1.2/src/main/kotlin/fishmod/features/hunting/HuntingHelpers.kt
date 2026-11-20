package fishmod.features.hunting

import fishmod.compat.speed
import fishmod.features.AlertTitles
import fishmod.utils.FishUtil
import fishmod.utils.SkyblockLocation
import fishmod.utils.TextStrip
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.FishEvents
import fishmod.utils.rendering.FishDraw
import fishmod.utils.rendering.RenderHooks
import fishmod.utils.sound.SoundManager
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
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

// Hunting: Cinderbat/Invisibug boxes, lasso REEL alert, honeycomb pot timers (logic from NoFrills)
object HuntingHelpers {
    private var bats: List<Entity> = emptyList()
    private val bugParticles = ConcurrentLinkedQueue<Vec3>()
    private class Bug(var pos: Vec3, var hits: Int, var ttl: Int)
    private val bugs = ArrayList<Bug>()
    @Volatile private var shownBugs: List<Vec3> = emptyList()
    private var lastReel = 0L
    private class Pot(val pos: BlockPos, var readyAt: Long, var told: Boolean)
    private val pots = HashMap<BlockPos, Pot>()
    private var tick = 0

    fun init() {
        // from NoFrills: invisibug trail = single CRIT particle, no spread, forced
        FishEvents.particle.register { p ->
            if (W.invisibugHighlight && p.particle.type === ParticleTypes.CRIT && p.count == 1 && p.speed == 0f &&
                p.xDist == 0f && p.yDist == 0f && p.zDist == 0f && p.isOverrideLimiter) bugParticles.add(Vec3(p.x, p.y, p.z))
            false
        }
        FishEvents.worldChange.register { bugs.clear(); shownBugs = emptyList(); bats = emptyList(); false }
        ClientTickEvents.END_CLIENT_TICK.register { FishDiag.guard("HuntingHelpers.1", "hunting scan threw") { onTick() } }
        RenderHooks.GIZMO.register { _ ->
            FishDiag.guard("HuntingHelpers.2", "hunting box render threw") {
                if (W.cinderbatHighlight) for (b in bats) if (b.isAlive) FishDraw.gizmoBox(b.boundingBox, 0, W.cinderbatColor, true)
                if (W.invisibugHighlight) for (v in shownBugs) FishDraw.gizmoBox(AABB.ofSize(v, 1.0, 1.0, 1.0), W.invisibugColor, W.invisibugColor or 0xFF000000.toInt(), true)
            }
        }
        RenderHooks.NO_DEPTH_LINE.register { ctx, ps, _ ->
            if (!W.honeycombTimer || pots.isEmpty() || !SkyblockLocation.`in`(SkyblockLocation.GALATEA)) return@register
            FishDiag.guard("HuntingHelpers.3", "honeycomb text render threw") {
                val now = System.currentTimeMillis()
                for (p in pots.values.toList()) {
                    val left = p.readyAt - now
                    val t = if (left <= 0) "§aHoneycomb ready" else "§6Honeycomb §e" + fmt(left)
                    FishDraw.renderText(ctx, ps, Component.literal(t), Vec3.atCenterOf(p.pos).add(0.0, 1.5, 0.0), 1.5f)
                }
            }
        }
    }

    private fun onTick() {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val player = mc.player ?: return
        if (W.invisibugHighlight) bugTick(level) else bugParticles.clear()
        if (tick++ % 5 != 0) return
        val crimson = W.cinderbatHighlight && SkyblockLocation.`in`(SkyblockLocation.CRIMSON_ISLE)
        val galatea = SkyblockLocation.`in`(SkyblockLocation.GALATEA)
        val b = ArrayList<Entity>()
        val leashed = if (W.lassoAlert) Leashable.leashableLeashedTo(player).filterIsInstance<LivingEntity>() else emptyList()
        for (e in level.entitiesForRendering()) {
            // from NoFrills: Cinderbats are bats with max HP 6 but HP above 4.8M
            if (crimson && e is Bat && e.maxHealth == 6f && e.health > 4_800_000f) b += e
            if (e !is ArmorStand) continue
            val name = e.customName?.let { TextStrip.plain(it.string) } ?: continue
            // from NoFrills: lasso stand reads REEL when you can pull
            if (name == "REEL" && leashed.any { l -> val dx = l.x - e.x; val dz = l.z - e.z; dx * dx + dz * dz <= 4.0 }) reel()
            // from NoFrills: "<critter> Critter in: 4m 30s" over a pot of honeycomb
            if (W.honeycombTimer && galatea && name.contains("Critter in: ")) potSeen(e, name.substringAfter("Critter in: ").trim())
        }
        bats = b
        if (W.honeycombTimer) {
            val now = System.currentTimeMillis()
            for (p in pots.values) if (!p.told && p.readyAt <= now) {
                p.told = true
                FishUtil.addChatMessage(Component.literal("§aHoneycomb at " + p.pos.x + ", " + p.pos.y + ", " + p.pos.z + " is ready."))
            }
            pots.values.removeIf { it.told && Vec3.atCenterOf(it.pos).distanceTo(player.position()) < 10.0 }
        }
    }

    private fun reel() {
        val now = System.currentTimeMillis()
        if (now - lastReel < 1500) return
        lastReel = now
        AlertTitles.show(Component.literal("§eREEL"), Component.empty(), 1000)
        SoundManager.play(SoundManager.preset(W.lassoSound), 1f)
    }

    private fun potSeen(e: ArmorStand, time: String) {
        if (time == "1s") return
        val secs = parseTime(time) ?: return
        val pos = BlockPos.containing(e.position())
        val key = pots.keys.firstOrNull { it.closerThan(pos, 3.0) } ?: pos
        val p = pots.getOrPut(key) { Pot(key, 0L, false) }
        p.readyAt = System.currentTimeMillis() + secs * 1000L
        p.told = false
    }

    private fun parseTime(s: String): Long? {
        var total = 0L
        for (part in s.split(' ')) {
            if (part.length < 2) return null
            val n = part.dropLast(1).toLongOrNull() ?: return null
            total += when (part.last()) { 'h' -> n * 3600; 'm' -> n * 60; 's' -> n; 'd' -> n * 86400; else -> return null }
        }
        return total
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

    private fun fmt(ms: Long): String { val s = (ms + 999) / 1000; return if (s >= 3600) "${s / 3600}h ${s % 3600 / 60}m" else if (s >= 60) "${s / 60}m ${s % 60}s" else "${s}s" }
}
