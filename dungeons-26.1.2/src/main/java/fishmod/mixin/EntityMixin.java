package fishmod.mixin;

import fishmod.features.dungeon.map.DungeonPlayers;
import fishmod.utils.Location;
import fishmod.utils.config.values.Dungeons;
import fishmod.utils.config.values.Visual;
import fishmod.utils.dungeon.DungeonClass;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityMixin {

    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
    public void isGlowing(CallbackInfoReturnable<Boolean> cir) {
        if (Visual.disableGlowing) {
            cir.setReturnValue(false);
        }
    }

    // Glow outline colour = getTeamColor(); swap Hypixel's teammate colour for class colour.
    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    public void fishmod$classGlowColor(CallbackInfoReturnable<Integer> cir) {
        if (!Dungeons.classColoredGlow || !((Object) this instanceof Player player)) return;
        if (!Location.inDungeon()) return;
        DungeonClass cls = DungeonClass.getClass(player);
        if (cls == null) cls = DungeonPlayers.classOf(player.getName().getString());
        if (cls != null) cir.setReturnValue(DungeonClass.getColor(cls) & 0xFFFFFF);
    }
}
