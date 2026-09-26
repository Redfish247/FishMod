package fishmod.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import fishmod.utils.config.values.FishSettings;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// ModifyExpressionValue leaves the 100 constant in place so other mods' @ModifyConstant on it still find their target.
@Mixin(ChatComponent.class)
public class ChatHistoryLimitMixin {

    private static int fishmod$historyLimit(int vanilla) {
        return (FishSettings.chatFeatureEnabled && FishSettings.infiniteChatHistory)
                ? Math.max(vanilla, FishSettings.infiniteChatHistoryLimit) : vanilla;
    }

    @ModifyExpressionValue(method = "addMessageToQueue", at = @At(value = "CONSTANT", args = "intValue=100"))
    private int fishmod$expandScrollback(int cap) {
        return fishmod$historyLimit(cap);
    }

    @ModifyExpressionValue(method = "addMessageToDisplayQueue", at = @At(value = "CONSTANT", args = "intValue=100"))
    private int fishmod$expandTrimmed(int cap) {
        return fishmod$historyLimit(cap);
    }

    @ModifyExpressionValue(method = "addRecentChat", at = @At(value = "CONSTANT", args = "intValue=100"))
    private int fishmod$expandRecentChat(int cap) {
        return fishmod$historyLimit(cap);
    }
}
