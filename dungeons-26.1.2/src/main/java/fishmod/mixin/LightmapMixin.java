package fishmod.mixin;

import fishmod.features.CameraTweaks;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Full Bright — forces the lightmap render state to a flat white. Vanilla clamps
 * {@code options.gamma()} to 1.0, so setting the option can never give a true fullbright;
 * mutating the {@link LightmapRenderState} here does.
 */
@Mixin(Lightmap.class)
public abstract class LightmapMixin {

    @Unique private static final Vector3fc fishmod$WHITE = new Vector3f(1f, 1f, 1f);
    @Unique private static boolean fishmod$wasActive = false;

    @Inject(method = "render", at = @At("HEAD"))
    private void fishmod$fullBright(LightmapRenderState state, CallbackInfo ci) {
        boolean active = CameraTweaks.fullBrightActive();
        // Force one rebake only on the enable/disable edge (or an explicit flash), not every frame.
        if (CameraTweaks.flashFullBright || active != fishmod$wasActive) state.needsUpdate = true;
        fishmod$wasActive = active;
        CameraTweaks.flashFullBright = false;
        if (!active) return;

        state.skyFactor = 1f;
        state.blockFactor = 1f;
        state.nightVisionEffectIntensity = 0f;
        state.darknessEffectScale = 0f;
        state.bossOverlayWorldDarkening = 0f;
        state.brightness = 1f;
        state.blockLightTint = fishmod$WHITE;
        state.skyLightColor = fishmod$WHITE;
        state.ambientColor = fishmod$WHITE;
        state.nightVisionColor = fishmod$WHITE;
    }
}
