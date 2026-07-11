package fishmod.features;

import fishmod.utils.config.values.FishSettings;
import fishmod.utils.dungeon.DungeonClass;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

/**
 * Recolors the local player's worn boots (leather dye) to a color that matches their detected dungeon
 * class. Client-side only — re-applied every tick to the equipped feet stack so server slot updates
 * don't wipe it (same approach as {@link ItemCustomizer}). Class is detected by {@link DungeonClass}
 * (chiefly the "Your <class> stats are doubled…" message + the dungeon tab list).
 *
 * Registered AFTER ItemCustomizer.init() so, while enabled, the class color wins over any per-item dye
 * the player set on those boots. Only shows on leather/dyeable boots (the DYED_COLOR tint is ignored
 * by non-dyeable models).
 */
public final class ClassColoredBoots {
    private ClassColoredBoots() {}

    /** Boot dye color per dungeon class (RGB), per the requested palette. */
    private static int colorFor(DungeonClass c) {
        if (c == null) return -1;
        return switch (c) {
            case HEALER  -> 0xFF99FF;
            case BERSERK -> 0xE89149;
            case ARCHER  -> 0xFF5555;
            case MAGE    -> 0x99FFFF;
            case TANK    -> 0x99FF99;
        };
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (!FishSettings.classColoredBootsEnabled || mc.player == null) return;
            int rgb = colorFor(DungeonClass.currentClass);
            if (rgb < 0) return;
            try {
                ItemStack boots = mc.player.getEquippedStack(EquipmentSlot.FEET);
                if (boots == null || boots.isEmpty()) return;
                boots.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(rgb & 0xFFFFFF));
            } catch (Exception ignored) {}
        });
    }
}
