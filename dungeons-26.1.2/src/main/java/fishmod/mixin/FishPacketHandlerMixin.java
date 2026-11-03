package fishmod.mixin;

import fishmod.utils.FishUtil;
import fishmod.utils.config.values.MiscSettings;
import fishmod.utils.debug.FishDebug;
import fishmod.utils.debug.MixinGuard;
import fishmod.utils.events.FishEvents;
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
public class FishPacketHandlerMixin {

    @Shadow
    private ClientLevel level;

    @org.spongepowered.asm.mixin.Unique
    private ClientboundSystemChatPacket fishmod$lastBundledSystemChat;

    @Inject(method = "applyPlayerInfoUpdate", at = @At(value = "TAIL"))
    private void fishmod$tabUpdated(ClientboundPlayerInfoUpdatePacket.Action action, ClientboundPlayerInfoUpdatePacket.Entry receivedEntry, PlayerInfo currentEntry, CallbackInfo ci) {
        MixinGuard.run("FishPacketHandlerMixin.1", "player list entry event failed",
                () -> FishEvents.tabEntry.invoke(playerListEvent -> playerListEvent.onNewPlayerEntry(receivedEntry)));
    }

    @Inject(method = "handleAddEntity", at = @At("HEAD"), cancellable = true)
    private void fishmod$renderOptimizerHideEntities(net.minecraft.network.protocol.game.ClientboundAddEntityPacket packet, CallbackInfo ci) {
        if (!fishmod.utils.config.values.VisualSettings.renderOptimizer) return;
        net.minecraft.world.entity.EntityType<?> t = packet.getType();
        if ((fishmod.utils.config.values.VisualSettings.roHideFallingBlocks && t == fishmod.compat.ET.FALLING_BLOCK)
                || (fishmod.utils.config.values.VisualSettings.roHideLightning && t == fishmod.compat.ET.LIGHTNING_BOLT)
                || (fishmod.utils.config.values.VisualSettings.roHideExperienceOrbs && t == fishmod.compat.ET.EXPERIENCE_ORB)) {
            ci.cancel();
        }
    }

    @Inject(method = "handleAddEntity", at = @At("TAIL"))
    private void fishmod$onEntitySpawned(net.minecraft.network.protocol.game.ClientboundAddEntityPacket packet, CallbackInfo ci, @Local Entity entity) {
        if (entity == null) return;
        MixinGuard.run("FishPacketHandlerMixin.2", "entity spawned event failed",
                () -> FishEvents.entitySpawn.invoke(e -> e.onEntity(entity, this.level)));
    }

    @Inject(method = "handleSoundEvent", at = @At(value = "HEAD"), cancellable = true)
    private void fishmod$soundPacket(ClientboundSoundPacket packet, CallbackInfo ci) {
        if (!Minecraft.getInstance().isSameThread()) return;
        try {
            float volume = packet.getVolume();
            float pitch = packet.getPitch();
            SoundEvent event = packet.getSound().value();

            if (MiscSettings.muteAbilityCooldown && pitch == 0.0 && volume == 8.0 && event == SoundEvents.ENDERMAN_TELEPORT) {
                ci.cancel();
            }

            if (FishDebug.sendSound) {
                FishUtil.addChatMessage(Component.literal("Sound: " + event.location() + " Volume: " + volume + " Pitch: " + pitch));
            }

            if (fishmod.features.diana.DianaSoundMute.shouldMute(event, pitch, packet.getX(), packet.getY(), packet.getZ())) {
                ci.cancel();
                return;
            }

            if (FishEvents.sound.invoke(soundEvent -> soundEvent.onSound(event, volume, pitch))) {
                ci.cancel();
            }
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("FishPacketHandlerMixin.3", "sound packet handling failed", t);
        }
    }

    @Inject(method = "handleSoundEntityEvent", at = @At(value = "HEAD"), cancellable = true)
    private void fishmod$entitySoundPacket(ClientboundSoundEntityPacket packet, CallbackInfo ci) {
        if (!Minecraft.getInstance().isSameThread()) return;
        try {
            float volume = packet.getVolume();
            float pitch = packet.getPitch();
            SoundEvent event = packet.getSound().value();

            if (FishDebug.sendSound) {
                FishUtil.addChatMessage(Component.literal("Sound: " + event.location() + " Volume: " + volume + " Pitch: " + pitch + "entitySeed: " + packet.getId()));
            }

            if (FishEvents.sound.invoke(soundEvent -> soundEvent.onSound(event, volume, pitch))) {
                ci.cancel();
            }
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("FishPacketHandlerMixin.4", "entity sound packet handling failed", t);
        }
    }

    @Inject(method = "handleParticleEvent", at = @At("HEAD"), cancellable = true)
    private void fishmod$particlePacket(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        if (!Minecraft.getInstance().isSameThread()) return;
        MixinGuard.run("FishPacketHandlerMixin.5", "particle event failed", () -> {
            if (FishEvents.particle.invoke(particleEvent -> particleEvent.onParticle(packet))) ci.cancel();
        });
    }

    @WrapOperation(method = "handleBundlePacket", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V"))
    private void fishmod$wrapPacketHandle(Packet<?> packet, PacketListener listener, Operation<Void> original) {
        try {
            if (packet instanceof ClientboundSystemChatPacket sysChat) {
                fishmod$lastBundledSystemChat = sysChat;
                if (!sysChat.overlay()) {
                    if (FishEvents.chat.invoke(gameMessageEvent -> gameMessageEvent.onGameMessage(sysChat.content()))) {
                        return;
                    }
                }
            }

            if (fishmod.utils.events.PacketFilter.wanted(packet)) FishEvents.packet.invoke(packetEvent -> packetEvent.onPacket(packet));
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("FishPacketHandlerMixin.6", "bundled packet event failed packet=" + packet.getClass().getSimpleName(), t);
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

                if (fishmod.features.dungeon.f7.P3TitleFilter.shouldHideTitle(text) || fishmod.features.dungeon.f7.DeviceDoneAlert.disableTitles(text)
                        || fishmod.features.dungeon.f7.StormOverAlert.shouldHideServerCountdown(text)) {
                    ci.cancel();
                }
            }
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("FishPacketHandlerMixin.7", "title packet handling failed", t);
        }
    }

    @Inject(method = "setSubtitleText", at = @At("HEAD"), cancellable = true)
    private void fishmod$onSubtitle(net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket packet, CallbackInfo ci) {
        if (!Minecraft.getInstance().isSameThread()) return;
        MixinGuard.run("FishPacketHandlerMixin.8", "subtitle packet handling failed", () -> {
            Component text = packet.text();
            if (text != null && (fishmod.features.dungeon.f7.StormOverAlert.shouldHideServerCountdown(text)
                    || fishmod.features.dungeon.f7.P3TitleFilter.shouldHideTitle(text))) ci.cancel();
        });
    }

    @Inject(method = "handleSystemChat", at = @At("HEAD"), cancellable = true)
    private void onGameMessage(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        if (!Minecraft.getInstance().isSameThread()) return;
        if (packet == fishmod$lastBundledSystemChat) {
            fishmod$lastBundledSystemChat = null;
            return;
        }
        if (packet.overlay()) return;
        MixinGuard.run("FishPacketHandlerMixin.9", "system chat event failed", () -> {
            if (FishEvents.chat.invoke(gameMessageEvent -> gameMessageEvent.onGameMessage(packet.content()))) ci.cancel();
        });
    }
}