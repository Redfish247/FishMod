package fishmod.mixin;

import fishmod.features.chat.ChatTabs;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Chat channel tab buttons above the input (fishmod.dev #599)
@Mixin(ChatScreen.class)
public abstract class ChatTabsMixin extends Screen {

    protected ChatTabsMixin(Component title) {
        super(title);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void fishmod$drawChatTabs(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        try {
            ChatTabs.render(context, this.height, mouseX, mouseY);
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("ChatTabsMixin.1", "chat tabs render failed", t);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void fishmod$clickChatTabs(MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (ChatTabs.click(this.height, click.x(), click.y(), click.button())) cir.setReturnValue(true);
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("ChatTabsMixin.2", "chat tabs click failed", t);
        }
    }
}
