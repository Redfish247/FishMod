package fishmod.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import fishmod.features.chat.ChatSearch;
import fishmod.mixin.accessors.KeyBindingAccessor;
import fishmod.utils.Keybinds;
import fishmod.utils.config.values.FishSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatSearchMixin extends Screen {

    @Shadow protected EditBox input;
    @Shadow public abstract void moveInHistory(int direction);

    @Unique private EditBox fishmod$searchBox;
    @Unique private static boolean fishmod$searchShown = true;

    protected ChatSearchMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void fishmod$addSearchBox(CallbackInfo ci) {
        if (FishSettings.chatFeatureEnabled && FishSettings.chatSearch && fishmod$searchShown) fishmod$buildSearchBox();
    }

    @Unique
    private void fishmod$buildSearchBox() {
        int h = 12;
        int y = this.height - 38;

        ChatComponent chat = Minecraft.getInstance().gui.getChat();
        ChatHudInvoker acc = (ChatHudInvoker) chat;
        int x = 4;
        int w = Math.max(40, (int) (acc.invokeWidth() * acc.invokeChatScale()));

        fishmod$searchBox = new EditBox(this.font, x, y, w, h,
                Component.translatable("fishmod.chatSearch"));
        fishmod$searchBox.setMaxLength(128);
        fishmod$searchBox.setBordered(false);
        fishmod$searchBox.setHint(Component.literal("Search chat…").withStyle(ChatFormatting.DARK_GRAY));
        fishmod$searchBox.setValue(ChatSearch.getQuery());
        fishmod$searchBox.setResponder(ChatSearch::onQueryChanged);
        this.addRenderableWidget(fishmod$searchBox);

        if (this.input != null) this.input.setCanLoseFocus(true);
    }

    @Unique
    private void fishmod$focusSearchBox() {
        if (fishmod$searchBox == null) return;
        this.setFocused(fishmod$searchBox);
        fishmod$searchBox.setFocused(true);
    }

    @Unique
    private void fishmod$removeSearchBox() {
        if (fishmod$searchBox != null) {
            this.removeWidget(fishmod$searchBox);
            fishmod$searchBox = null;
        }
        ChatSearch.clear();
        if (this.input != null) {
            this.setFocused(this.input);
            this.input.setFocused(true);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void fishmod$chatSearchKey(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (!FishSettings.chatFeatureEnabled || !FishSettings.chatSearch) return;
        int key = event.key();

        InputConstants.Key bound = ((KeyBindingAccessor) Keybinds.chatSearchToggle).getBoundKey();
        if (bound.getType() == InputConstants.Type.KEYSYM
                && bound.getValue() != InputConstants.UNKNOWN.getValue()
                && key == bound.getValue()) {
            fishmod$searchShown = !fishmod$searchShown;
            if (fishmod$searchShown) { fishmod$buildSearchBox(); fishmod$focusSearchBox(); }
            else fishmod$removeSearchBox();
            cir.setReturnValue(true);
            return;
        }

        if ((key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) && fishmod$searchBox != null && this.getFocused() == fishmod$searchBox) {
            fishmod$searchBox.setFocused(false);
            if (this.input != null) { this.setFocused(this.input); this.input.setFocused(true); }
            this.moveInHistory(key == GLFW.GLFW_KEY_UP ? -1 : 1);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void fishmod$clearSearch(CallbackInfo ci) {
        ChatSearch.clear();
    }
}
