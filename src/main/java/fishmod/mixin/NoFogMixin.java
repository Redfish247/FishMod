package fishmod.mixin;

import fishmod.utils.config.values.FishSettings;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.effect.MobEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public class NoFogMixin {

    @Inject(method = "setupFog", at = @At("RETURN"))
    private void fishmod$noFog(Camera camera, int renderDistance, DeltaTracker dt, float darken, ClientLevel level, CallbackInfoReturnable<FogData> cir) {
        if (!FishSettings.noFogEnabled) return;
        var p = Minecraft.getInstance().player;
        if (p != null && (p.hasEffect(MobEffects.BLINDNESS) || p.hasEffect(MobEffects.DARKNESS))) return;
        FogData fog = cir.getReturnValue();
        if (fog == null) return;
        fog.environmentalStart = 1.0e6f;
        fog.environmentalEnd = 1.0e6f;
        fog.renderDistanceStart = 1.0e6f;
        fog.renderDistanceEnd = 1.0e6f;
    }
}
