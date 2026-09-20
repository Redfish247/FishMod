package fishmod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fishmod.features.ActionBarCleaner;
import fishmod.features.DarkMode;
import fishmod.utils.Keybinds;
import fishmod.utils.Location;
import fishmod.utils.config.values.FishSettings;
import fishmod.utils.config.values.Visual;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiMixin {

    private static boolean fishmod$ab(boolean toggle) {
        return FishSettings.actionBarEnabled && toggle && Location.inSkyblock();
    }

    @ModifyVariable(method = "setOverlayMessage(Lnet/minecraft/network/chat/Component;Z)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Component fishmod$cleanActionBar(Component message) {
        return ActionBarCleaner.filter(message);
    }

    @Inject(method = "extractArmor", at = @At("HEAD"), cancellable = true)
    private static void fishmod$hideArmorRow(GuiGraphicsExtractor extractor, Player player,
                                             int a, int b, int c, int d, CallbackInfo ci) {
        if (fishmod$ab(FishSettings.abHideArmorRow)) ci.cancel();
    }

    @WrapOperation(method = "extractPlayerHealth",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getAbsorptionAmount()F"))
    private float fishmod$hideAbsorption(Player instance, Operation<Float> original) {
        return fishmod$ab(FishSettings.abHideAbsorption) ? 0f : original.call(instance);
    }

    @WrapOperation(method = "extractChat",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/ChatComponent;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V"))
    private void fishmod$chatPeek(ChatComponent chat, GuiGraphicsExtractor extractor, Font font, int tickCount,
                                   int mouseX, int mouseY, ChatComponent.DisplayMode mode, boolean focused,
                                   Operation<Void> original) {
        if (Keybinds.chatPeekActive()) {
            original.call(chat, extractor, font, tickCount, mouseX, mouseY, ChatComponent.DisplayMode.FOREGROUND, true);
        } else {
            original.call(chat, extractor, font, tickCount, mouseX, mouseY, mode, focused);
        }
    }

    @WrapOperation(method = "extractHotbarAndDecorations",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/contextualbar/ContextualBarRenderer;extractExperienceLevel(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;I)V"))
    private void fishmod$hideXpLevel(GuiGraphicsExtractor extractor, Font font, int level, Operation<Void> original) {
        if (!fishmod$ab(FishSettings.abHideXpBar)) original.call(extractor, font, level);
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void fishmod$darkModePre(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!Visual.darkModeTintHud) DarkMode.drawOverlay(extractor);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void fishmod$darkModePost(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (Visual.darkModeTintHud) DarkMode.drawOverlay(extractor);
    }
}
