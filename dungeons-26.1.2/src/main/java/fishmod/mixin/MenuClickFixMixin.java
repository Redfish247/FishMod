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

// SkyBlock menus: double-click collect and middle click become one plain left click.
@Mixin(AbstractContainerScreen.class)
public abstract class MenuClickFixMixin {
    @Shadow protected abstract void slotClicked(Slot slot, int slotId, int button, ContainerInput input);

    @Unique private boolean fishmod$reissue;

    @Inject(method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V", at = @At("HEAD"), cancellable = true)
    private void fishmod$fixClick(Slot slot, int slotId, int button, ContainerInput input, CallbackInfo ci) {
        if (fishmod$reissue || slot == null || slot.container instanceof Inventory) return;
        try {
            boolean dbl = ExtrasSettings.fixDoubleClicks && input == ContainerInput.PICKUP_ALL;
            boolean mid = ExtrasSettings.middleClickFix && input == ContainerInput.CLONE && slot.hasItem();
            if (!dbl && !mid) return;
            if (!fishmod.utils.SkyblockLocation.inSkyblock()) return;
            ci.cancel();
            fishmod$reissue = true;
            try { slotClicked(slot, slotId, 0, ContainerInput.PICKUP); } finally { fishmod$reissue = false; }
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("MenuClickFixMixin.1", "menu click fix failed slotId=" + slotId, t);
        }
    }
}
