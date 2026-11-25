package fishmod.features.slayer

import fishmod.features.slayer.EndAlertSettings as S
import fishmod.features.fishing.FishingEvents
import fishmod.utils.FishUtil
import fishmod.utils.TextStrip
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.AABB
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.FishEvents
import fishmod.utils.events.chatPlain
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.Vec3
import fishmod.utils.rendering.FishDraw
import fishmod.utils.rendering.RenderHooks

object SlayerExtras {

    // from SkyHanni (SlayerApi: cocoons burst after 5s)
    private const val COCOON_MS = 5_000L
    // from SkyHanni (SummoningMobManager)
    private val RECALL = Regex("^The Seraph recalled your (\\d+) summoned allies!$")

    private class Cocoon(val label: String, val start: Long, var id: Int, var pos: Vec3, val lift: Double)
    private val cocoons = ArrayList<Cocoon>()
    private var slayerCocoon: Cocoon? = null
    private val pendingCatch = ArrayList<Pair<String, Long>>()

    fun init() {
        FishEvents.chat.register { text ->
            if (S.summonsRecallAlert) {
                RECALL.find(text.chatPlain.trim())?.let { m -> title("§c§lSUMMONS RECALLED", "§7Seraph took §f${m.groupValues[1]}§7 allies", 2000) }
            }
            false
        }
        FishingEvents.seaCreature.register { e ->
            if (S.cocoonTimer && e.cocoon) pendingCatch.add(e.creature.name to System.currentTimeMillis())
            false
        }
        FishEvents.worldChange.register { cocoons.clear(); pendingCatch.clear(); slayerCocoon = null; false }
        ClientTickEvents.END_CLIENT_TICK.register { FishDiag.guard("SlayerExtras.3", "cocoon scan threw") { onTick() } }
        RenderHooks.GIZMO.register { _ ->
            try { renderCocoon() } catch (e: Exception) { FishDiag.fail("SlayerExtras.2", "cocoon timer render failed", e) }
        }
    }

    private fun onTick() {
        val now = System.currentTimeMillis()
        if (!S.cocoonTimer) { cocoons.clear(); pendingCatch.clear(); slayerCocoon = null; return }
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val pl = mc.player ?: return
        cocoons.removeIf { now - it.start > COCOON_MS }

        if (SlayerManager.state == SlayerManager.State.COCOONED) {
            if (slayerCocoon == null) {
                val name = SlayerManager.type?.displayName ?: "Slayer Boss"
                val boss = SlayerManager.bossEntity ?: findStand(level, pl.position(), name)
                if (boss != null) {
                    val c = if (boss is LivingEntity) Cocoon(name, now, boss.id, boss.position(), boss.bbHeight + 0.6)
                        else Cocoon(name, now, boss.id, boss.position(), 0.9)
                    slayerCocoon = c
                    cocoons.add(c)
                }
            }
        } else slayerCocoon = null

        pendingCatch.removeIf { now - it.second > 3000 }
        if (pendingCatch.isNotEmpty()) {
            val it = pendingCatch.iterator()
            while (it.hasNext()) {
                val (name, _) = it.next()
                val stand = findStand(level, pl.position(), name, true) ?: continue
                if (cocoons.none { c -> c.id == stand.id }) cocoons.add(Cocoon(name, now, stand.id, stand.position(), 0.9))
                it.remove()
            }
        }
        for (c in cocoons) level.getEntity(c.id)?.let { c.pos = it.position() }
    }

    private fun findStand(level: net.minecraft.client.multiplayer.ClientLevel, from: Vec3, name: String, needHealth: Boolean = false): Entity? {
        var best: Entity? = null
        var bestD = Double.MAX_VALUE
        for (s in level.getEntitiesOfClass(ArmorStand::class.java, AABB.ofSize(from, 80.0, 80.0, 80.0))) {
            if (!s.isCustomNameVisible) continue
            val plain = s.customName?.let { TextStrip.plain(it.string) } ?: continue
            if (!plain.contains(name)) continue
            if (needHealth && plain.indexOf('❤') < 0) continue
            if (cocoons.any { c -> c.id == s.id }) continue
            val d = s.position().distanceToSqr(from)
            if (d < bestD) { bestD = d; best = s }
        }
        return best
    }

    private fun renderCocoon() {
        if (!S.cocoonTimer || cocoons.isEmpty()) return
        val now = System.currentTimeMillis()
        for (c in cocoons) {
            val left = (COCOON_MS - (now - c.start)) / 1000.0
            if (left < 0) continue
            FishDraw.gizmoText(Component.literal("§dCocoon §f" + c.label + " §e" + String.format("%.1f", left) + "s"),
                Vec3(c.pos.x, c.pos.y + c.lift, c.pos.z), 1.4f, -0x1, true)
        }
    }

    private fun title(main: String, sub: String, ms: Int) {
        try {
            FishUtil.forceTitle(Component.literal(main), Component.literal(sub), ms)
        } catch (e: Exception) {
            FishDiag.fail("SlayerExtras.1", "slayer extras title failed", e)
        }
    }
}
