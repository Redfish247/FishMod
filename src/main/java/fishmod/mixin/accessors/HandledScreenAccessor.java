package fishmod.mixin.accessors;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the protected GUI-geometry fields of {@link HandledScreen} so overlays can anchor
 * themselves beside the inventory background. Coordinates are in GUI-scaled space — the same
 * space as {@code mouseX/mouseY} and {@code DrawContext.fill}.
 */
@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {
    @Accessor("x") int getBgX();
    @Accessor("y") int getBgY();
    @Accessor("backgroundWidth")  int getBgWidth();
    @Accessor("backgroundHeight") int getBgHeight();
}
