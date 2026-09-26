package fishmod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import fishmod.cosmetic.NametagStatsHolder;
import fishmod.utils.Location;
import fishmod.utils.config.values.Dungeons;
import fishmod.utils.config.values.FishSettings;
import fishmod.utils.config.values.Visual;
import fishmod.utils.data.EntityUtil;
import fishmod.utils.dungeon.DungeonClass;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {

    @org.spongepowered.asm.mixin.Unique
    private static final double NAMETAG_STATS_RANGE_SQ = 10.0 * 10.0;

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    public void fishmod$adjustNameTag(T entity, S state, float tickProgress, CallbackInfo ci) {

        if (Dungeons.hideBlazeNameTag && state.nameTag != null && Location.inDungeon()
                && !(entity instanceof net.minecraft.world.entity.player.Player)) {
            String nameTagText = state.nameTag.getString();
            if (nameTagText.contains("Blaze")) {
                state.nameTag = null;
            }
        }
        if (entity instanceof RemotePlayer player && Dungeons.renderClassName && Location.inDungeon()) {
            if (DungeonClass.isTeammate(player)) {
                state.nameTag = null;
            }
        }

        if (entity instanceof Player sized) {
            float[] sc = fishmod.cosmetic.PlayerSize.scaleFor(sized);
            ((fishmod.cosmetic.ScaleHolder) state).fishmod$setScale(sc[0], sc[1], sc[2]);
            if (sc[1] != 1.0f && state.nameTagAttachment != null) {
                state.nameTagAttachment = state.nameTagAttachment.add(0, sized.getBbHeight() * (sc[1] - 1.0), 0);
            }
        }

        if (entity instanceof RemotePlayer teammate) {
            int outline = fishmod.features.dungeon.PlayerHighlight.outlineColor(teammate);
            if (outline != EntityRenderState.NO_OUTLINE) state.outlineColor = outline;
        }

        if (Visual.hideEntityFire) {
            state.displayFireAnimation = false;
        } else if (entity instanceof Player player && Visual.hideFireInf5) {
            if (EntityUtil.isClientPlayer(player)) {
                state.displayFireAnimation = false;
            }
        }

        if (entity instanceof Player p && EntityUtil.isClientPlayer(p)
                && FishSettings.nickPreviewEnabled && FishSettings.nickPreviewYOffset != 0.0
                && state.nameTagAttachment != null) {
            state.nameTagAttachment = state.nameTagAttachment.add(0, FishSettings.nickPreviewYOffset, 0);
        }

        List<Component> statLines = null;
        if (FishSettings.nametagStatsEnabled && state.nameTag != null && entity instanceof Player pl) {
            boolean self = EntityUtil.isClientPlayer(pl);
            Minecraft mc = Minecraft.getInstance();
            boolean inRange = self || mc.player == null || pl.distanceToSqr(mc.player) <= NAMETAG_STATS_RANGE_SQ;
            if ((!self || FishSettings.nametagStatsShowSelf) && inRange) {
                String playerName = pl.getName().getString();
                statLines = fishmod.features.NametagStats.linesFor(playerName);
            }
        }
        if (statLines != null && !statLines.isEmpty() && !FishSettings.nametagStatsAbove && state.nameTagAttachment != null) {
            state.nameTagAttachment = state.nameTagAttachment.add(0, statLines.size() * 10 * 0.025, 0);
        }
        ((NametagStatsHolder) state).fishmod$setNametagStats(statLines);
    }

    @Inject(
        method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V",
        at = @At("TAIL")
    )
    private void fishmod$extraNametagLines(S state, PoseStack poseStack, SubmitNodeCollector collector,
                                          CameraRenderState cameraRenderState, int baseOffset, CallbackInfo ci) {
        List<Component> lines = ((NametagStatsHolder) state).fishmod$getNametagStats();
        if (lines == null || lines.isEmpty()) return;
        boolean above = FishSettings.nametagStatsAbove;
        int y = above ? baseOffset - 10 : baseOffset + 10;
        for (Component line : lines) {
            collector.submitNameTag(poseStack, state.nameTagAttachment, y, line,
                !state.isDiscrete, state.lightCoords, state.distanceToCameraSq, cameraRenderState);
            y += above ? -10 : 10;
        }
    }

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void fishmod$cullEntities(T entity, Frustum frustum, double camX, double camY, double camZ,
                                     CallbackInfoReturnable<Boolean> cir) {
        if (fishmod.features.dungeon.puzzles.odin.BlazeSolver.INSTANCE.shouldHideMob(entity)) {
            cir.setReturnValue(false);
            return;
        }
        if (!Visual.renderOptimizer) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || entity == mc.player) return;

        if (Visual.hidePlayersInRange && entity instanceof Player) {
            double r = Visual.hidePlayerRange;
            if (r > 0 && entity.distanceToSqr(mc.player) <= r * r) {
                cir.setReturnValue(false);
                return;
            }
        }

        if (Visual.hideDeadEntities && entity instanceof LivingEntity le
                && (le.isDeadOrDying() || le.getHealth() <= 0f)) {
            cir.setReturnValue(false);
            return;
        }

        if (fishmod.features.RenderOptimizer.hideDeathAnimation()) {
            if (entity instanceof net.minecraft.world.entity.decoration.ArmorStand) {
                if (fishmod.features.RenderOptimizer.hideDyingArmorStands()
                        && entity.getVehicle() instanceof LivingEntity mount && mount.deathTime > 0) {
                    cir.setReturnValue(false);
                }
            } else if (entity instanceof LivingEntity dying && dying.deathTime > 0) {
                cir.setReturnValue(false);
            }
        }
    }
}
