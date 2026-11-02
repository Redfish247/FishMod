package fishmod.mixin;

import fishmod.features.dungeon.map.DungeonPlayers;
import fishmod.utils.SkyblockLocation;
import fishmod.utils.config.values.DungeonSettings;
import fishmod.utils.config.values.VisualSettings;
import fishmod.utils.dungeon.DungeonRole;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class FishEntityMixin {

    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
    public void isGlowing(CallbackInfoReturnable<Boolean> cir) {
        if (VisualSettings.noEntityGlow) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    public void fishmod$classGlowColor(CallbackInfoReturnable<Integer> cir) {
        if (!DungeonSettings.classColoredGlow || !((Object) this instanceof Player player)) return;
        if (!SkyblockLocation.inDungeon()) return;
        try {
            DungeonRole cls = DungeonRole.getClass(player);
            if (cls == null) cls = DungeonPlayers.classOf(player.getName().getString());
            if (cls != null) cir.setReturnValue(DungeonRole.getColor(cls) & 0xFFFFFF);
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("FishEntityMixin.1", "class glow color lookup failed", t);
        }
    }
}
