package fishmod.features.fishing

import fishmod.features.mining.MiningHuds
import fishmod.utils.debug.FishDiag
import fishmod.utils.rendering.FishDraw
import fishmod.utils.rendering.RenderHooks
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin
import fishmod.features.fishing.FishingWorldSettings as W

// Totem of Corruption: HUD with time left/owner and its 16-block range ring (stand layout as in FishingAlertsWorld, range from SkyHanni)
object TotemOverlay {
    private class Totem(val pos: Vec3, val timer: String, val owner: String) {
        val ring: List<Vec3> = (0..48).map { i ->
            val a = i * Math.PI * 2 / 48
            Vec3(pos.x + 16 * cos(a), pos.y, pos.z + 16 * sin(a))
        }
    }
    @Volatile private var totems: List<Totem> = emptyList()
    private var tick = 0

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { if (++tick % 10 == 0) FishDiag.guard("TotemOverlay.1", "totem scan threw") { scan() } }
        RenderHooks.GIZMO.register { _ ->
            if (!W.totemOverlay || !W.totemRing) return@register
            FishDiag.guard("TotemOverlay.2", "totem ring threw") {
                for (t in totems) {
                    val r = t.ring
                    for (i in 1 until r.size) FishDraw.gizmoLine(r[i - 1], r[i], W.totemColor)
                }
            }
        }
        MiningHuds.reg("Totem of Corruption", "fishing_totem", 140, 20,
            { W.totemX }, { W.totemX = it }, { W.totemY }, { W.totemY = it }, { W.totemScale }, { W.totemScale = it },
            { W.totemOverlay && totems.isNotEmpty() }, { W.totemOverlay },
            example = { listOf("§5Totem of Corruption", "§7Remaining\t§e2m 41s", "§7Owner\t§fYou") }, lines = ::lines)
    }

    private fun scan() {
        val mc = Minecraft.getInstance()
        val level = mc.level; val pl = mc.player
        if (!W.totemOverlay || level == null || pl == null) { totems = emptyList(); return }
        val out = ArrayList<Totem>()
        for (s in level.getEntitiesOfClass(ArmorStand::class.java, pl.boundingBox.inflate(W.totemDistance.toDouble()))) {
            if (s.customName?.string != "Totem of Corruption") continue
            val timer = level.getEntity(s.id + 1)?.customName?.string ?: continue
            if (!timer.contains("Remaining: ")) continue
            val owner = level.getEntity(s.id + 2)?.customName?.string?.substringAfter("Owner: ", "?") ?: "?"
            out += Totem(Vec3(s.x, s.y - 1.0, s.z), timer.substringAfter("Remaining: "), owner)
        }
        totems = out.sortedBy { it.pos.distanceTo(pl.position()) }
    }

    private fun lines(): List<String> {
        val t = totems.firstOrNull() ?: return emptyList()
        return listOf("§5Totem of Corruption", "§7Remaining\t§e" + t.timer, "§7Owner\t§f" + t.owner)
    }
}
