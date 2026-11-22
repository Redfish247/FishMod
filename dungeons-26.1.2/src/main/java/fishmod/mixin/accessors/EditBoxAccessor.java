package fishmod.mixin.accessors;

import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EditBox.class)
public interface EditBoxAccessor {
    @Accessor("displayPos") int fishmodDisplayPos();
    @Accessor("textX") int fishmodTextX();
    @Accessor("textY") int fishmodTextY();
    @Accessor("suggestion") String fishmodSuggestion();
}
