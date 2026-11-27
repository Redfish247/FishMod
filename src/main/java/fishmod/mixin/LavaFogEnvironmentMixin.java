package fishmod.mixin;

import fishmod.utils.config.values.FishSettings;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.LavaFogEnvironment;
import net.minecraft.client.renderer.fog.environment.WaterFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LavaFogEnvironment.class)
public abstract class LavaFogEnvironmentMixin {

    @Unique private static final WaterFogEnvironment fishmod$WATER_FOG = new WaterFogEnvironment();

    @Inject(method = "setupFog", at = @At("HEAD"), cancellable = true)
    private void fishmod$setupFog(FogData fog, Camera camera, ClientLevel level, float renderDistance, DeltaTracker dt, CallbackInfo ci) {
        if (!FishSettings.lavaToWaterEnabled || !FishSettings.lavaToWaterHideFog) return;
        fog.color.set(fog.color.x, fog.color.y, fog.color.z, 0f);
        fog.environmentalStart = renderDistance;
        fog.environmentalEnd = renderDistance;
        ci.cancel();
    }

    @Inject(method = "getBaseColor", at = @At("HEAD"), cancellable = true)
    private void fishmod$baseColor(ClientLevel level, Camera camera, int renderDistance, float partialTicks, CallbackInfoReturnable<Integer> cir) {
        if (!FishSettings.lavaToWaterEnabled) return;
        int color = FishSettings.lavaToWaterTint
                ? (FishSettings.lavaToWaterColor & 0xFFFFFF)
                : fishmod$WATER_FOG.getBaseColor(level, camera, renderDistance, partialTicks);
        cir.setReturnValue(color);
    }
}
