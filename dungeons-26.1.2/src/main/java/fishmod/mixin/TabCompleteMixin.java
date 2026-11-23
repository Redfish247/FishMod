package fishmod.mixin;

import com.mojang.brigadier.suggestion.Suggestions;
import fishmod.features.chat.TabComplete;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;

@Mixin(CommandSuggestions.class)
public abstract class TabCompleteMixin {
    @Shadow @Final private EditBox input;
    @Shadow private CompletableFuture<Suggestions> pendingSuggestions;

    @Inject(method = "updateCommandInfo", at = @At("TAIL"))
    private void fishmod$hypixelTabComplete(CallbackInfo ci) {
        try {
            String text = this.input.getValue();
            if (this.input.getCursorPosition() != text.length()) return;
            Suggestions party = TabComplete.partySuggest(text);
            if (party != null) { this.pendingSuggestions = CompletableFuture.completedFuture(party); return; }
            CompletableFuture<Suggestions> p = this.pendingSuggestions;
            if (p != null && p.isDone() && !p.isCompletedExceptionally() && !p.join().isEmpty()) return;
            Suggestions ours = TabComplete.suggest(text);
            if (ours != null) this.pendingSuggestions = CompletableFuture.completedFuture(ours);
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("TabCompleteMixin.1", "tab complete failed", t);
        }
    }
}
