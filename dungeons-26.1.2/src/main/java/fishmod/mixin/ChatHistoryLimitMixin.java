package fishmod.mixin;

import fishmod.utils.config.values.FishSettings;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

// Skipped by FishModMixinPlugin when SkySoft is loaded: it modifies the same constants and crashes if we take them first.
@Mixin(ChatComponent.class)
public class ChatHistoryLimitMixin {

    private static int fishmod$limit(int vanilla) {
        return (FishSettings.chatFeatureEnabled && FishSettings.infiniteChatHistory)
                ? Math.max(vanilla, FishSettings.infiniteChatHistoryLimit) : vanilla;
    }

    @ModifyConstant(method = "addMessageToQueue", constant = @Constant(intValue = 100))
    private int fishmod$expandScrollback(int cap) {
        return fishmod$limit(cap);
    }

    @ModifyConstant(method = "addMessageToDisplayQueue", constant = @Constant(intValue = 100))
    private int fishmod$expandTrimmed(int cap) {
        return fishmod$limit(cap);
    }
}
