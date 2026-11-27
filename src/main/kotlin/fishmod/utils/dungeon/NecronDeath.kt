package fishmod.utils.dungeon
import fishmod.utils.events.chatPlain
import fishmod.compat.hud

import fishmod.utils.FishConstants
import fishmod.utils.debug.FishDebug
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.FishEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft

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
        FishEvents.chat.register { text ->
            if (text.chatPlain.startsWith(ARGH)) started = true
            false
        }
        FishEvents.worldChange.register { started = false; dead = false; lastTag = null; false }
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (!started || dead || !BossPhase.isInFloor7()) return@register
            FishDiag.guard("NecronDeath.1", "necron death check threw") { check(mc) }
        }
    }

    private fun check(mc: Minecraft) {
        val pct = NecronSnapshot.bar(mc)
        if (pct != null && pct <= 0f) return fire("boss bar 0")
        val tag = NecronSnapshot.nametag(mc) ?: return
        if (tag != lastTag) {
            lastTag = tag
            FishDebug.LOGGER.info("[NecronDeath] tag '$tag' cps=${tag.codePoints().toArray().joinToString(",") { Integer.toHexString(it) }}")
        }
        if (tag.contains('?') || tag.contains('�') || tag.contains('□')) return fire("nametag ?")
    }

    private fun fire(reason: String) {
        dead = true
        FishDebug.LOGGER.info("[NecronDeath] dead: $reason")
        for (l in listeners) FishDiag.guard("NecronDeath.2", "necron death listener threw") { l() }
    }
}
