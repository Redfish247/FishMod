package fishmod.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import fishmod.cosmetic.prestige.PrestigeLevelColors;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GuiMessage.Line.class)
public abstract class PrestigeChatLineMixin {

    @ModifyReturnValue(method = "content", at = @At("RETURN"), require = 0)
    private FormattedCharSequence fishmod$animatePrestige(FormattedCharSequence original) {
        FormattedCharSequence out = original;
        try {
            out = PrestigeLevelColors.animateLine(out);
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("PrestigeChatLineMixin.1", "chat level animate failed", t);
        }
        try {
            out = fishmod.cosmetic.NickState.animateChatLine(out);
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("PrestigeChatLineMixin.2", "chat nick animate failed", t);
        }
        return out;
    }
}
