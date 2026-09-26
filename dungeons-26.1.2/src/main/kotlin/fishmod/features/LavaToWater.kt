package fishmod.features

import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.color.block.BlockTintSources
import net.minecraft.client.renderer.block.FluidModel
import net.minecraft.client.renderer.block.FluidStateModelSet
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

object LavaToWater {

    private var lastEnabled = false
    private var appliedTint = 0L
    private var settleTicks = 0

    private class Tinted(val water: FluidModel, val rgb: Int, val model: FluidModel)

    @Volatile
    private var tinted: Tinted? = null

    private fun tintKey(): Long =
        (if (FishSettings.lavaToWaterTint) 1L shl 32 else 0L) or (FishSettings.lavaToWaterColor.toLong() and 0xFFFFFFL)

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register {
            if (FishSettings.lavaToWaterEnabled != lastEnabled) {
                lastEnabled = FishSettings.lavaToWaterEnabled
                appliedTint = tintKey()
                refresh()
                return@register
            }
            if (!lastEnabled) return@register
            if (tintKey() == appliedTint) { settleTicks = 0; return@register }
            if (++settleTicks < 10) return@register
            settleTicks = 0
            appliedTint = tintKey()
            refresh()
        }
    }

    @JvmStatic
    fun refresh() {
        Minecraft.getInstance().levelRenderer?.allChanged()
    }

    @JvmStatic
    fun modelHook(self: FluidStateModelSet, state: FluidState, cir: CallbackInfoReturnable<FluidModel>) {
        if (!FishSettings.lavaToWaterEnabled) return
        val t = state.type
        if (t !== Fluids.LAVA && t !== Fluids.FLOWING_LAVA) return
        val water = self.get(Fluids.WATER.defaultFluidState())
        if (!FishSettings.lavaToWaterTint) {
            cir.setReturnValue(water)
            return
        }
        val rgb = FishSettings.lavaToWaterColor and 0xFFFFFF
        val cached = tinted
        if (cached != null && cached.water === water && cached.rgb == rgb) {
            cir.setReturnValue(cached.model)
            return
        }
        val model = FluidModel(
            water.layer(), water.stillMaterial(), water.flowingMaterial(), water.overlayMaterial(),
            BlockTintSources.constant(rgb, rgb)
        )
        tinted = Tinted(water, rgb, model)
        cir.setReturnValue(model)
    }
}
