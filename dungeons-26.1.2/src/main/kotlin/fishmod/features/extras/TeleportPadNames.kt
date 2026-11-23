package fishmod.features.extras

import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.decoration.ArmorStand
import fishmod.features.extras.ExtrasSettings as S

object TeleportPadNames {
    // from SkyHanni (TeleportPadCompactName)
    private val WARP = Regex("""^✦ Warp To (.+)$""")
    private const val NONE = "✦ No Destination"
    private var tick = 0

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (!S.teleportPadNames || ++tick % 20 != 0) return@register
            val level = mc.level ?: return@register
            try {
                for (e in level.entitiesForRendering()) {
                    if (e !is ArmorStand) continue
                    val name = e.customName ?: continue
                    val plain = name.string
                    if (plain == NONE) { e.isCustomNameVisible = false; continue }
                    WARP.find(plain)?.let { m ->
                        val color = name.siblings.lastOrNull()?.style ?: name.style
                        e.customName = Component.literal(m.groupValues[1]).withStyle(color)
                    }
                }
            } catch (t: Throwable) {
                FishDiag.fail("TeleportPadNames.1", "teleport pad rename failed", t)
            }
        }
    }
}
