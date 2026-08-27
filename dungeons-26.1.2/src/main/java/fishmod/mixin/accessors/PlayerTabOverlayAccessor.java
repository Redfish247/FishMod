package fishmod.mixin.accessors;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only access to the tab-list footer text — Hypixel puts the dungeon's active Blessings there. */
@Mixin(PlayerTabOverlay.class)
public interface PlayerTabOverlayAccessor {

    @Accessor("footer")
    Component fishmod$getFooter();
}
