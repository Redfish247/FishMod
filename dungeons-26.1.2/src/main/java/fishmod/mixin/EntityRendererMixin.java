package fishmod.mixin;

import fishmod.utils.Location;
import fishmod.utils.config.values.Dungeons;
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

        if (Visual.hideEntityFire) {
            state.displayFireAnimation = false;
        } else if (entity instanceof Player player && Visual.hideFireInf5) {
            if (EntityUtil.isClientPlayer(player)) {
                state.displayFireAnimation = false;
            }
        }
    }
}
