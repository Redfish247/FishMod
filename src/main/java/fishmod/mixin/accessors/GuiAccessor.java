package fishmod.mixin.accessors;

import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Gui.class)
public interface GuiAccessor {
    @Accessor("titleFadeInTime")  void fishmod$setTitleFadeInTime(int v);
    @Accessor("titleStayTime")    void fishmod$setTitleStayTime(int v);
    @Accessor("titleFadeOutTime") void fishmod$setTitleFadeOutTime(int v);
}
