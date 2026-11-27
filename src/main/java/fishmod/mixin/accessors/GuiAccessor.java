package fishmod.mixin.accessors;

//? if <26.2 {
import net.minecraft.client.gui.Gui;
//?} else {
/*import net.minecraft.client.gui.Hud;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

//? if <26.2 {
@Mixin(Gui.class)
//?} else {
/*@Mixin(Hud.class)
*///?}
public interface GuiAccessor {
    @Accessor("titleFadeInTime")  void fishmod$setTitleFadeInTime(int v);
    @Accessor("titleStayTime")    void fishmod$setTitleStayTime(int v);
    @Accessor("titleFadeOutTime") void fishmod$setTitleFadeOutTime(int v);
}
