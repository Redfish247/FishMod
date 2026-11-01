package fishmod.utils.rendering

import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.resources.Identifier

// Registers mod HUD layers; shadow() backs the global "shadow all text" option
object FishHudLayer {
    @JvmField var inModHud = false

    @JvmStatic fun addLast(id: Identifier, el: HudElement) = HudElementRegistry.addLast(id, wrap(el))
    @JvmStatic fun addFirst(id: Identifier, el: HudElement) = HudElementRegistry.addFirst(id, wrap(el))

    private fun wrap(el: HudElement) = HudElement { ctx, tick ->
        val prev = inModHud
        inModHud = true
        try { el.extractRenderState(ctx, tick) } finally { inModHud = prev }
    }

    @JvmStatic fun shadow(orig: Boolean): Boolean {
        return orig || FishSettings.hudTextShadowMode == "All Text"
    }
}
