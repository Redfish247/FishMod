package fishmod.mixin;

import fishmod.features.chat.PartyCommandHint;
import fishmod.utils.config.values.FishSettings;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public abstract class ChatCommandHintMixin {
    @Shadow protected EditBox input;

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void fishmod$drawCommandHint(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        if (!FishSettings.partyCommandsEnabled || !FishSettings.pcArgHints || this.input == null) return;
        try {
            PartyCommandHint.render(context, this.input);
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("ChatCommandHintMixin.1", "party command hint render failed", t);
        }
    }
}
