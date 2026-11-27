package fishmod.mixin;

import fishmod.features.visuals.ClassicPack;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.FallbackResourceManager;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** OG text colours: use the vanilla text shaders instead of the ones from Hypixel's SkyBlock pack. */
@Mixin(FallbackResourceManager.class)
public abstract class ClassicColorMixin {
    @Shadow public abstract List<Resource> getResourceStack(Identifier location);

    @Inject(method = "listResources", at = @At("RETURN"), cancellable = true, require = 0)
    private void fishmod$ogTextColors(String path, java.util.function.Predicate<Identifier> filter, CallbackInfoReturnable<Map<Identifier, Resource>> cir) {
        try {
            if (!ClassicPack.ogColors()) return;
            Map<Identifier, Resource> result = new HashMap<>(cir.getReturnValue());
            boolean changed = false;
            for (Identifier loc : new ArrayList<>(result.keySet())) {
                if (!ClassicPack.isTextShader(loc)) continue;
                Resource picked = null;
                for (Resource r : getResourceStack(loc)) {
                    if (!ClassicPack.isSkyblockPackSource(r.sourcePackId())) picked = r;
                }
                if (picked != null) { result.put(loc, picked); changed = true; }
            }
            if (changed) cir.setReturnValue(result);
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("ClassicColorMixin.1", "OG text colours failed", t);
        }
    }
}
