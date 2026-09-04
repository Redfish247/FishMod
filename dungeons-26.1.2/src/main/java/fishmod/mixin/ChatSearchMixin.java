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

/**
 * Chat Search: an opt-in search field on the open chat screen that live-filters the visible
 * scrollback via {@link ChatSearch}. The field is hidden until the user presses the rebindable
 * "FishMod: Toggle Chat Search" key (unbound by default) — it used to be added unconditionally,
 * which let a stray Up land in it instead of driving vanilla's sent-message history. Up / Down
 * always fall through to {@link ChatScreen#moveInHistory(int)} now, even while the field is focused.
 */
@Mixin(ChatScreen.class)
public abstract class ChatSearchMixin extends Screen {

    @Shadow protected EditBox input;
    @Shadow public abstract void moveInHistory(int direction);

    @Unique private EditBox fishmod$searchBox;
    // Default true: shown whenever the Chat Search setting is on
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
        int y = this.height - 38; // one line above the vanilla input + the "Chat: All" strip

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

        // Allow focus to move between the two fields, but leave it on the chat input — the search
        // box shouldn't grab it on chat open (that swallowed typing and the Up-arrow history key).
        if (this.input != null) this.input.setCanLoseFocus(true);
    }

    /** Focus the search field (called when the user clicks it or presses the toggle key). */
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

        // While the search box exists it's a second focusable widget, so vanilla would grab Up/Down
        // for focus-cycling before ChatScreen runs its history handler. Do the history move here and
        // consume the key so focus never leaves the chat input.
        if ((key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) && fishmod$searchBox != null) {
            if (this.getFocused() == fishmod$searchBox) fishmod$searchBox.setFocused(false);
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
