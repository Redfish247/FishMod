package fishmod.utils.dungeon
import fishmod.compat.hud

import fishmod.mixin.accessors.BossBarHudAccessor
import fishmod.utils.Constants
import fishmod.utils.debug.Debug
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.Events
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.decoration.ArmorStand

// Necron death like NoammAddons: boss bar hits 0 after "ARGH!"; backup = "?" in his nametag
object NecronDeath {
    private const val ARGH = "[BOSS] Necron: ARGH!"
    private var started = false
    private var dead = false
    private var lastTag: String? = null
    private val listeners = ArrayList<() -> Unit>()

    @JvmStatic fun onDeath(l: () -> Unit) { listeners.add(l) }

    @JvmStatic
    fun init() {
        Events.ON_GAME_MESSAGE.register { text ->
            if (Constants.STRIP_COLOR_REGEX.replace(text.string, "").startsWith(ARGH)) started = true
            false
        }
        Events.ON_WORLD_CHANGE.register { started = false; dead = false; lastTag = null; false }
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (!started || dead || !Phase.isInFloor7()) return@register
            FishDiag.guard("NecronDeath.1", "necron death check threw") { check(mc) }
        }
    }

    private fun check(mc: Minecraft) {
        val bars = (mc.hud.bossOverlay as BossBarHudAccessor).bossBars.values
        if (bars.any { it.name.string.contains("Necron") && it.progress <= 0f }) return fire("boss bar 0")

        val level = mc.level ?: return
        for (e in level.entitiesForRendering()) {
            if (e !is ArmorStand || !e.hasCustomName()) continue
            val tag = Constants.STRIP_COLOR_REGEX.replace(e.customName?.string ?: continue, "")
            if (!tag.contains("Necron")) continue
            if (tag != lastTag) {
                lastTag = tag
                Debug.LOGGER.info("[NecronDeath] tag '$tag' cps=${tag.codePoints().toArray().joinToString(",") { Integer.toHexString(it) }}")
            }
            if (tag.contains('?') || tag.contains('�') || tag.contains('□')) return fire("nametag ?")
        }
    }

    private fun fire(reason: String) {
        dead = true
        Debug.LOGGER.info("[NecronDeath] dead: $reason")
        for (l in listeners) FishDiag.guard("NecronDeath.2", "necron death listener threw") { l() }
    }
}
