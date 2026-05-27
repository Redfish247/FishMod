package fishmod.mixin;

import fishmod.utils.Location;
import fishmod.utils.config.values.Dungeons;
import fishmod.utils.config.values.FishSettings;
import fishmod.utils.config.values.Visual;
import fishmod.utils.data.EntityUtil;
import fishmod.utils.dungeon.DungeonClass;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {

    @Inject(method = "updateRenderState", at = @At("TAIL"))
    public void hideFire(T entity, S state, float tickProgress, CallbackInfo ci) {

        if (Dungeons.hideBlazeNameTag && state.displayName != null) {
            if (state.displayName.getString().contains("Blaze")) {
                state.displayName = null;
            }

        }
        if (entity instanceof OtherClientPlayerEntity player && Dungeons.renderClassName && Location.inDungeon()) {
            if (DungeonClass.isTeammate(player)) {
                state.displayName = null;
            }
        }

        if (Visual.hideEntityFire) {
            state.onFire = false;
        } else if (entity instanceof PlayerEntity player && Visual.hideFireInf5) {
            if (EntityUtil.isClientPlayer(player)) {
                state.onFire = false;
            }
        }

        // Lower the local player's own nametag. The position lives in the render state, which the
        // label renderer reads — so this works even though the text SIZE is fixed by ImmediatelyFast.
        if (entity instanceof PlayerEntity p && EntityUtil.isClientPlayer(p)
                && FishSettings.nickPreviewEnabled && FishSettings.nickPreviewYOffset != 0.0
                && state.nameLabelPos != null) {
            state.nameLabelPos = state.nameLabelPos.add(0, FishSettings.nickPreviewYOffset, 0);
        }
    }
}
