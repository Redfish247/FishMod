package fishmod.features;

import fishmod.utils.Location;
import fishmod.utils.config.values.FishSettings;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * Reskins your summoned (floating) pet entity to a chosen head texture. SkyBlock pets render as a
 * small armor stand wearing a textured player_head with a "[Lvl …]" nametag; each tick we find the
 * nearest such armor stand to the player and overwrite its head PROFILE with the configured skin —
 * no mixin needed (same trick as the item customizer). Texture accepts a hash / URL / base64 value,
 * resolved by {@link ItemCustomizer#buildSkinProfile}.
 *
 * <p>v1 reskins the nearest pet armor stand (almost always yours, since pets hug their owner); telling
 * yours apart from a neighbour's reliably needs in-game tuning.
 */
public final class PetEntitySkin {
    private PetEntitySkin() {}

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (!FishSettings.petEntitySkinEnabled || FishSettings.petEntitySkin == null
                    || FishSettings.petEntitySkin.isEmpty()) return;
            if (mc.world == null || mc.player == null || !Location.inSkyblock()) return;

            ProfileComponent pc;
            try { pc = ItemCustomizer.buildSkinProfile(FishSettings.petEntitySkin); }
            catch (Exception e) { return; }
            if (pc == null) return;

            ArmorStandEntity best = null;
            double bestDist = 16.0; // within 4 blocks
            for (var e : mc.world.getEntities()) {
                if (!(e instanceof ArmorStandEntity as) || !as.hasCustomName()) continue;
                var name = as.getCustomName();
                if (name == null || !name.getString().contains("[Lvl")) continue;
                ItemStack head = as.getEquippedStack(EquipmentSlot.HEAD);
                if (head == null || !head.isOf(Items.PLAYER_HEAD)) continue;
                double d = as.squaredDistanceTo(mc.player);
                if (d < bestDist) { bestDist = d; best = as; }
            }
            if (best != null) best.getEquippedStack(EquipmentSlot.HEAD).set(DataComponentTypes.PROFILE, pc);
        });
    }
}
