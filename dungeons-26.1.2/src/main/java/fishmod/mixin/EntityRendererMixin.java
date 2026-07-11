package fishmod.mixin;

import fishmod.utils.Location;
import fishmod.utils.config.values.Dungeons;
import fishmod.utils.config.values.FishSettings;
import fishmod.utils.config.values.Visual;
import fishmod.utils.data.EntityUtil;
import fishmod.utils.dungeon.DungeonClass;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    public void hideFire(T entity, S state, float tickProgress, CallbackInfo ci) {

        if (Dungeons.hideBlazeNameTag && state.nameTag != null) {
            if (state.nameTag.getString().contains("Blaze")) {
                state.nameTag = null;
            }

        }
        if (entity instanceof RemotePlayer player && Dungeons.renderClassName && Location.inDungeon()) {
            if (DungeonClass.isTeammate(player)) {
                state.nameTag = null;
            }
        }

        // Stash this player's custom render size on the state so PlayerEntityRendererScaleMixin can
        // apply it inside scale(). Own size shows locally; others' only when sharing is on.
        if (entity instanceof Player sized) {
            float[] sc = fishmod.cosmetic.PlayerSize.scaleFor(sized);
            ((fishmod.cosmetic.ScaleHolder) state).fishmod$setScale(sc[0], sc[1], sc[2]);
            // Keep the nametag above the head when the model is taller. The model scale pivots at the
            // feet, so a Y-scaled model grows upward and would clip through its fixed-height label.
            // Raise the label by the extra model height (height × (scaleY − 1)). Only Y matters — the
            // overall tag size is unchanged; X/Z (width/depth) never move it.
            if (sc[1] != 1.0f && state.nameTagAttachment != null) {
                state.nameTagAttachment = state.nameTagAttachment.add(0, sized.getBbHeight() * (sc[1] - 1.0), 0);
            }
        }

        if (Visual.hideEntityFire) {
            state.displayFireAnimation = false;
        } else if (entity instanceof Player player && Visual.hideFireInf5) {
            if (EntityUtil.isClientPlayer(player)) {
                state.displayFireAnimation = false;
            }
        }

        // Lower the local player's own nametag. The position lives in the render state, which the
        // label renderer reads — so this works even though the text SIZE is fixed by ImmediatelyFast.
        if (entity instanceof Player p && EntityUtil.isClientPlayer(p)
                && FishSettings.nickPreviewEnabled && FishSettings.nickPreviewYOffset != 0.0
                && state.nameTagAttachment != null) {
            state.nameTagAttachment = state.nameTagAttachment.add(0, FishSettings.nickPreviewYOffset, 0);
        }
    }
}
