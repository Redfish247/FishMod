package fishmod.mixin;

import fishmod.utils.Misc;
import fishmod.utils.config.values.ExtraOptions;
import fishmod.utils.debug.Debug;
import fishmod.utils.events.Events;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.PacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPlayNetworkHandlerMixin {

    @Shadow
    private ClientLevel level;

    @org.spongepowered.asm.mixin.Unique
    private ClientboundSystemChatPacket fishmod$lastBundledSystemChat;

    @Inject(method = "applyPlayerInfoUpdate", at = @At(value = "TAIL"))
    private void onPlayerList(ClientboundPlayerInfoUpdatePacket.Action action, ClientboundPlayerInfoUpdatePacket.Entry receivedEntry, PlayerInfo currentEntry, CallbackInfo ci) {
        try {
            Events.ON_PLAYER_ENTRY.invoke(playerListEvent -> playerListEvent.onNewPlayerEntry(receivedEntry));
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("ClientPlayNetworkHandlerMixin.1", "player list entry event failed action=" + action, t);
        }
    }

    @Inject(method = "handleAddEntity", at = @At("HEAD"), cancellable = true)
    private void fishmod$renderOptimizerHideEntities(net.minecraft.network.protocol.game.ClientboundAddEntityPacket packet, CallbackInfo ci) {
        if (!fishmod.utils.config.values.Visual.renderOptimizer) return;
        net.minecraft.world.entity.EntityType<?> t = packet.getType();
        if ((fishmod.utils.config.values.Visual.roHideFallingBlocks && t == net.minecraft.world.entity.EntityType.FALLING_BLOCK)
                || (fishmod.utils.config.values.Visual.roHideLightning && t == net.minecraft.world.entity.EntityType.LIGHTNING_BOLT)
                || (fishmod.utils.config.values.Visual.roHideExperienceOrbs && t == net.minecraft.world.entity.EntityType.EXPERIENCE_ORB)) {
            ci.cancel();
        }
    }

    @Inject(method = "handleAddEntity", at = @At("TAIL"))
    private void fishmod$onEntitySpawned(net.minecraft.network.protocol.game.ClientboundAddEntityPacket packet, CallbackInfo ci, @Local Entity entity) {
        if (entity == null) return;
        try {
            Events.ON_ENTITY_SPAWNED.invoke(e -> e.onEntity(entity, this.level));
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("ClientPlayNetworkHandlerMixin.2", "entity spawned event failed type=" + entity.getType(), t);
        }
    }

    @Inject(method = "handleSoundEvent", at = @At(value = "HEAD"), cancellable = true)
    private void onSound(ClientboundSoundPacket packet, CallbackInfo ci) {
        if (!Minecraft.getInstance().isSameThread()) return;
        try {
            float volume = packet.getVolume();
            float pitch = packet.getPitch();
            SoundEvent event = packet.getSound().value();

            if (ExtraOptions.disableAbilityCooldownSound && pitch == 0.0 && volume == 8.0 && event == SoundEvents.ENDERMAN_TELEPORT) {
                ci.cancel();
            }

            if (Debug.sendSound) {
                Misc.addChatMessage(Component.literal("Sound: " + event.location() + " Volume: " + volume + " Pitch: " + pitch));
            }

            if (fishmod.features.diana.DianaSoundMute.shouldMute(event, packet.getX(), packet.getY(), packet.getZ())) {
                ci.cancel();
                return;
            }

            if (Events.ON_SOUND.invoke(soundEvent -> soundEvent.onSound(event, volume, pitch))) {
                ci.cancel();
            }
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("ClientPlayNetworkHandlerMixin.3", "sound packet handling failed", t);
        }
    }

    @Inject(method = "handleSoundEntityEvent", at = @At(value = "HEAD"), cancellable = true)
    private void onLocationSound(ClientboundSoundEntityPacket packet, CallbackInfo ci) {
        if (!Minecraft.getInstance().isSameThread()) return;
        try {
            float volume = packet.getVolume();
            float pitch = packet.getPitch();
            SoundEvent event = packet.getSound().value();

            if (Debug.sendSound) {
                Misc.addChatMessage(Component.literal("Sound: " + event.location() + " Volume: " + volume + " Pitch: " + pitch + "entitySeed: " + packet.getId()));
            }

            if (Events.ON_SOUND.invoke(soundEvent -> soundEvent.onSound(event, volume, pitch))) {
                ci.cancel();
            }
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("ClientPlayNetworkHandlerMixin.4", "entity sound packet handling failed", t);
        }
    }

    @Inject(method = "handleParticleEvent", at = @At("HEAD"), cancellable = true)
    private void onParticle(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        if (!Minecraft.getInstance().isSameThread()) return;
        try {
            if (Events.ON_PARTICLE.invoke(particleEvent -> particleEvent.onParticle(packet))) {
                ci.cancel();
            }
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("ClientPlayNetworkHandlerMixin.5", "particle event failed", t);
        }
    }

    @WrapOperation(method = "handleBundlePacket", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V"))
    private void apply(Packet<?> packet, PacketListener listener, Operation<Void> original) {
        try {
            if (packet instanceof ClientboundSystemChatPacket sysChat) {
                fishmod$lastBundledSystemChat = sysChat;
                if (!sysChat.overlay()) {
                    if (Events.ON_GAME_MESSAGE.invoke(gameMessageEvent -> gameMessageEvent.onGameMessage(sysChat.content()))) {
                        return;
                    }
                }
            }

            Events.ON_PACKET.invoke(packetEvent -> packetEvent.onPacket(packet));
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("ClientPlayNetworkHandlerMixin.6", "bundled packet event failed packet=" + packet.getClass().getSimpleName(), t);
        }
        original.call(packet, listener);
    }

    @Inject(method = "setTitleText", at = @At("HEAD"), cancellable = true)
    private void onTitle(ClientboundSetTitleTextPacket packet, CallbackInfo ci) {
        if (!Minecraft.getInstance().isSameThread()) return;
        try {
            Component text = packet.text();
            if (text != null) {
                fishmod.features.dungeon.SimonSaysTracker.onTitle(text.getString());

                if (fishmod.features.dungeon.f7.TitleHider.shouldHideTitle(text) || fishmod.features.dungeon.f7.DeviceNotifier.disableTitles(text)
                        || fishmod.features.dungeon.f7.StormOverAlert.shouldHideServerCountdown(text)) {
                    ci.cancel();
                }
            }
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("ClientPlayNetworkHandlerMixin.7", "title packet handling failed", t);
        }
    }

    @Inject(method = "setSubtitleText", at = @At("HEAD"), cancellable = true)
    private void fishmod$onSubtitle(net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket packet, CallbackInfo ci) {
        if (!Minecraft.getInstance().isSameThread()) return;
        try {
            Component text = packet.text();
            if (text != null && (fishmod.features.dungeon.f7.StormOverAlert.shouldHideServerCountdown(text)
                    || fishmod.features.dungeon.f7.TitleHider.shouldHideTitle(text))) ci.cancel();
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("ClientPlayNetworkHandlerMixin.8", "subtitle packet handling failed", t);
        }
    }

    @Inject(method = "handleSystemChat", at = @At("HEAD"), cancellable = true)
    private void onGameMessage(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        if (!Minecraft.getInstance().isSameThread()) return;
        if (packet == fishmod$lastBundledSystemChat) {
            fishmod$lastBundledSystemChat = null;
            return;
        }
        try {
            if (!packet.overlay()) {
                if (Events.ON_GAME_MESSAGE.invoke(gameMessageEvent -> gameMessageEvent.onGameMessage(packet.content()))) {
                    ci.cancel();
                }
            }
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("ClientPlayNetworkHandlerMixin.9", "system chat event failed", t);
        }
    }
}