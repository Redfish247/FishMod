package fishmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;

@Mixin(ChatComponent.class)
public interface ChatHudInvoker {

    @Invoker("getScale")
    double invokeChatScale();

    @Invoker("getLineHeight")
    int invokeLineHeight();

    @Invoker("getWidth")
    int invokeWidth();

    @Accessor("chatScrollbarPos")
    int getScrolledLines();

    @Accessor("trimmedMessages")
    List<GuiMessage.Line> getVisibleMessages();

    @Accessor("allMessages")
    List<GuiMessage> getMessages();

    /** Rebuilds visibleMessages from messages (re-wraps lines); preserves scroll position. */
    @Invoker("refreshTrimmedMessages")
    void invokeRefresh();
}
