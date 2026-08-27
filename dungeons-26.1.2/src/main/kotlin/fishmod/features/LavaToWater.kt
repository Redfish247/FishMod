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

/**
 * Lava To Water (ported from NoammAddons' LavaToWater + MixinFluidStateModelSet /
 * MixinLavaFogEnvironment). Swaps the lava fluid model for water at bake-lookup time, optionally
 * with a custom tint, and neutralises the lava fog.
 */
object LavaToWater {

    private var lastEnabled = false

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register {
            if (FishSettings.lavaToWaterEnabled != lastEnabled) {
                lastEnabled = FishSettings.lavaToWaterEnabled
                refresh()
            }
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
        cir.setReturnValue(
            FluidModel(
                water.layer(), water.stillMaterial(), water.flowingMaterial(), water.overlayMaterial(),
                BlockTintSources.constant(rgb, rgb)
            )
        )
    }
}
