package fishmod.mixin;

import fishmod.features.dungeon.StarredMobGlow;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Forces a glowing outline on starred dungeon mobs flagged by {@link StarredMobGlow}. */
@Mixin(MinecraftClient.class)
public class StarredMobGlowMixin {

    @Inject(method = "hasOutline", at = @At("HEAD"), cancellable = true)
    private void fishmod$starredMobGlow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (StarredMobGlow.shouldGlow(entity)) cir.setReturnValue(true);
    }
}
