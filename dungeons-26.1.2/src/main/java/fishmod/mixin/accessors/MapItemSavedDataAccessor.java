package fishmod.mixin.accessors;

import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * Exposes the private {@code decorations} map on {@link MapItemSavedData} (Mojang mappings leave it
 * private, with only a read-only {@code Iterable<MapDecoration>} getter available publicly). The
 * dungeon map feature needs keyed lookup/mutation of decorations by their string id.
 *
 * Note: {@code colors} (the map's pixel byte array) is a public field on this mapping and needs no accessor.
 */
@Mixin(MapItemSavedData.class)
public interface MapItemSavedDataAccessor {
    @Accessor("decorations")
    Map<String, MapDecoration> getDecorations();
}
