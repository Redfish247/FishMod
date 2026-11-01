package fishmod.utils.rendering

import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.resources.Identifier

// Registers mod HUD layers and flags them so the global text-shadow override only hits mod text
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
        if (!inModHud) return orig
        return when (FishSettings.hudTextShadowMode) { "Always" -> true; "Never" -> false; else -> orig }
    }
}
