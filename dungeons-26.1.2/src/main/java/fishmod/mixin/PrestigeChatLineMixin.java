package fishmod.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import fishmod.cosmetic.prestige.PrestigeLevelColors;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GuiMessage.Line.class)
public abstract class PrestigeChatLineMixin {

    // Animates level digits when the line is read for drawing; chat is never rebuilt
    @ModifyReturnValue(method = "content", at = @At("RETURN"), require = 0)
    private FormattedCharSequence fishmod$animatePrestige(FormattedCharSequence original) {
        try {
            return PrestigeLevelColors.animateLine(original);
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("PrestigeChatLineMixin.1", "chat level animate failed", t);
            return original;
        }
    }
}
