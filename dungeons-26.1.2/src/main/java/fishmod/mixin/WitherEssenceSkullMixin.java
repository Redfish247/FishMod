package fishmod.mixin;

import com.mojang.authlib.GameProfile;
import fishmod.utils.config.values.VisualSettings;
import fishmod.utils.debug.FishDiag;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

// Hypixel's wither essence skulls share one owner id; swap their skin for our own texture.
@Mixin(SkullBlockRenderer.class)
public abstract class WitherEssenceSkullMixin {

    @Unique private static final UUID fishmod$essenceOwner = UUID.fromString("e0f3e929-869e-3dca-9504-54c666ee6f23");
    @Unique private static final Identifier fishmod$essenceSkin = Identifier.fromNamespaceAndPath("fishmod", "textures/entity/wither_essence_skull.png");

    @Shadow
    public static RenderType getSkullRenderType(SkullBlock.Type type, @Nullable Identifier texture) {
        return null;
    }

    @Inject(method = "resolveSkullRenderType", at = @At("HEAD"), cancellable = true)
    private void fishmod$essenceSkull(SkullBlock.Type type, SkullBlockEntity skull, CallbackInfoReturnable<RenderType> cir) {
        if (type != SkullBlock.Types.PLAYER || !VisualSettings.witherEssenceSkin) return;
        try {
            ResolvableProfile owner = skull.getOwnerProfile();
            GameProfile profile = owner == null ? null : owner.partialProfile();
            if (profile != null && fishmod$essenceOwner.equals(profile.id())) {
                cir.setReturnValue(getSkullRenderType(type, fishmod$essenceSkin));
            }
        } catch (Throwable t) {
            FishDiag.fail("WitherEssenceSkullMixin.1", "wither essence skull resolve failed", t);
        }
    }
}
