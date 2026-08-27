package fishmod.mixin;

import fishmod.features.CameraTweaks;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Full Bright — forces the lightmap render state to a flat white, exactly like NoammAddons'
 * MixinLightmap. Vanilla clamps {@code options.gamma()} to 1.0, so setting the option can never
 * give a true fullbright; mutating the {@link LightmapRenderState} here does.
 */
@Mixin(Lightmap.class)
public abstract class LightmapMixin {

    @Unique private static final Vector3fc fishmod$WHITE = new Vector3f(1f, 1f, 1f);

    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true)
    private LightmapRenderState fishmod$fullBright(LightmapRenderState state) {
        boolean active = CameraTweaks.fullBrightActive();
        // When the state changes, force one recompute (so it clears cleanly on disable too).
        if (CameraTweaks.flashFullBright || active) state.needsUpdate = true;
        CameraTweaks.flashFullBright = false;
        if (!active) return state;

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
        return state;
    }
}
