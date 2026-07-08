package fishmod.mixin.accessors;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the protected GUI-geometry fields of {@link AbstractContainerScreen} so overlays can anchor
 * themselves beside the inventory background. Coordinates are in GUI-scaled space — the same
 * space as {@code mouseX/mouseY} and {@code DrawContext.fill}.
 */
@Mixin(AbstractContainerScreen.class)
public interface HandledScreenAccessor {
    @Accessor("leftPos") int getBgX();
    @Accessor("topPos") int getBgY();
    @Accessor("imageWidth")  int getBgWidth();
    @Accessor("imageHeight") int getBgHeight();
}
