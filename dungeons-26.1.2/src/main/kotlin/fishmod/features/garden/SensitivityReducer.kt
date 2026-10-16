package fishmod.features.garden

import fishmod.features.mining.Mining
import fishmod.utils.FishMsg
import fishmod.utils.events.Events
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import fishmod.features.garden.GardenSettings as S

// Lowers the client mouse-sensitivity option while standing on the ground inside a plot; restores it otherwise. Option change only, no input is sent.
object SensitivityReducer {

    private var reduced = false

    private fun inPlot() = Mining.sidebar.any { it.contains("Plot", true) }

    private fun restore(mc: Minecraft, msg: Boolean) {
        if (S.gardenSensOriginal < 0) { reduced = false; return }
        mc.options.sensitivity().set(S.gardenSensOriginal)
        S.gardenSensOriginal = -1.0
        reduced = false
        if (msg && S.gardenSensMsg) FishMsg.send(Garden.legacy(S.gardenSensColor) + "Mouse sensitivity restored.")
    }

    fun init() {
        // A crash while reduced leaves the saved original behind; put it back on the next launch
        ClientLifecycleEvents.CLIENT_STARTED.register { mc -> if (S.gardenSensOriginal >= 0) restore(mc, false) }
        ClientLifecycleEvents.CLIENT_STOPPING.register { mc -> if (reduced) restore(mc, false) }
        Events.ON_WORLD_CHANGE.register { Minecraft.getInstance().let { mc -> if (reduced) restore(mc, false) }; false }
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            val p = mc.player
            if (p == null) return@register
            val want = S.gardenSens && Garden.inGarden() && p.onGround() && inPlot()
            val opt = mc.options.sensitivity()
            if (want && !reduced) {
                S.gardenSensOriginal = opt.get()
                opt.set((S.gardenSensOriginal * S.gardenSensPercent.coerceIn(5, 100) / 100.0).coerceIn(0.0, 1.0))
                reduced = true
                if (S.gardenSensMsg) FishMsg.send(Garden.legacy(S.gardenSensColor) + "Mouse sensitivity lowered to ${S.gardenSensPercent}%.")
            } else if (!want && reduced) restore(mc, true)
        }
        Garden.hud("Sensitivity", "garden_sens", 110, 12,
            { S.gardenSensX }, { S.gardenSensX = it }, { S.gardenSensY }, { S.gardenSensY = it },
            { S.gardenSensScale }, { S.gardenSensScale = it },
            { S.gardenSens && S.gardenSensHud && reduced }) { listOf(Garden.legacy(S.gardenSensColor) + "Sensitivity §f${S.gardenSensPercent}%") }
    }
}
