package fishmod.mixin.accessors;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
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
    @Accessor("hoveredSlot") Slot fishmod$getHoveredSlot();

    // Setters: storage overlay zeroes container geometry so vanilla slot render/hover can't leak through
    @Accessor("leftPos") void fishmod$setLeftPos(int v);
    @Accessor("topPos") void fishmod$setTopPos(int v);
    @Accessor("imageWidth") void fishmod$setImageWidth(int v);
    @Accessor("imageHeight") void fishmod$setImageHeight(int v);
}
