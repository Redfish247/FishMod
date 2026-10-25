package fishmod.features.rift

import fishmod.utils.Misc
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import fishmod.features.rift.RiftSettings as S

// Leech Supreme laser phase (Mortiferous Lazer, last 160 of 800 HP) cheese spot; no SkyHanni/Skyblocker equivalent exists, spot + phases from the Hypixel SkyBlock wiki
object RiftLeech {

    private val CHEESE = Vec3(-146.5, 31.0, 71.5)
    private val HP = Regex("""([\d][\d,.]*)\s*([kKmM]?)\s*/\s*([\d][\d,.]*)\s*([kKmM]?)""")
    private const val MAX_HP = 800.0
    private const val LASER_HP = 160.0
    @Volatile private var laser = false
    private var lastSeen = 0L
    private var lastTitle = 0L
    private var tick = 0

    private fun num(n: String, suf: String): Double {
        val v = n.replace(",", "").toDoubleOrNull() ?: return -1.0
        return v * when (suf.lowercase()) { "k" -> 1e3; "m" -> 1e6; else -> 1.0 }
    }

    // Boss nametag "Leech Supreme ... cur/max": laser phase once cur is within the last fifth of max
    private fun poll() {
        if (!S.riftLeech || !Rift.inRift()) { laser = false; return }
        val now = System.currentTimeMillis()
        val stand = Rift.namedStands(40.0).firstOrNull { it.second.contains("Leech Supreme", true) }
        if (stand == null) { if (now - lastSeen > 3000L) laser = false; return }
        lastSeen = now
        val m = HP.find(stand.second)
        val cur = m?.let { num(it.groupValues[1], it.groupValues[2]) } ?: -1.0
        val max = m?.let { num(it.groupValues[3], it.groupValues[4]) }?.takeIf { it > 0 } ?: MAX_HP
        laser = cur in 0.0..(max * LASER_HP / MAX_HP)
    }

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { if (tick++ % 5 == 0) poll() }
        fishmod.utils.events.Events.ON_WORLD_CHANGE.register { laser = false; false }
        RenderingEvents.GIZMO.register { _ -> render() }
    }

    private fun render() {
        if (!laser || !S.riftLeech) return
        val c = S.riftLeechColor
        val box = AABB(CHEESE.x - 0.5, CHEESE.y, CHEESE.z - 0.5, CHEESE.x + 0.5, CHEESE.y + 1.0, CHEESE.z + 0.5)
        RenderUtils.gizmoBox(box, (c and 0xFFFFFF) or 0x60000000, c, true)
        RenderUtils.gizmoLine(RenderUtils.cameraLineStart(1.0), box.center, c, 2f, true)
        if (S.riftLeechTitle) {
            val now = System.currentTimeMillis()
            if (now - lastTitle > 1000L) {
                lastTitle = now
                Misc.forceTitle(Component.literal("§c§lLASER!"), Component.literal("§7Go to the safe spot"), 1500)
            }
        }
    }
}
