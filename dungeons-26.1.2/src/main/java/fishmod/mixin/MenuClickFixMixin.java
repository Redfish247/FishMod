package fishmod.mixin;

import fishmod.features.extras.ExtrasSettings;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class MenuClickFixMixin {
    @Shadow protected abstract void slotClicked(Slot slot, int slotId, int button, ContainerInput input);

    @Unique private boolean fishmod$reissue;

    @Inject(method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V", at = @At("HEAD"), cancellable = true)
    private void fishmod$fixClick(Slot slot, int slotId, int button, ContainerInput input, CallbackInfo ci) {
        if (fishmod$reissue || slot == null) return;
        if (slot.container instanceof Inventory && !(((Object) this) instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen)) return;
        try {
            boolean dbl = ExtrasSettings.fixDoubleClicks && input == ContainerInput.PICKUP_ALL;
            boolean mid = ExtrasSettings.middleClickFix && (input == ContainerInput.PICKUP || input == ContainerInput.QUICK_MOVE || input == ContainerInput.PICKUP_ALL) && slot.hasItem();
            if (!dbl && !mid) return;
            if (!fishmod.utils.SkyblockLocation.inSkyblock()) return;
            String title = ((AbstractContainerScreen<?>) (Object) this).getTitle().getString().toLowerCase();
            if (mid && (title.contains("loadout") || title.equals("chest") || title.equals("large chest"))) {
                if (!dbl) return;
                mid = false;
            }
            ci.cancel();
            fishmod$reissue = true;
            try {
                if (mid) slotClicked(slot, slotId, 2, ContainerInput.CLONE);
                else slotClicked(slot, slotId, 0, ContainerInput.PICKUP);
            } finally { fishmod$reissue = false; }
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("MenuClickFixMixin.1", "menu click fix failed slotId=" + slotId, t);
        }
    }
}
