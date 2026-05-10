package blade.addon.mixin;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(ChatHud.class)
public interface ChatHudInvoker {

    @Invoker("getChatScale")
    double invokeChatScale();

    @Invoker("getLineHeight")
    int invokeLineHeight();

    @Invoker("getWidth")
    int invokeWidth();

    @Accessor("scrolledLines")
    int getScrolledLines();

    @Accessor("visibleMessages")
    List<ChatHudLine.Visible> getVisibleMessages();
}
