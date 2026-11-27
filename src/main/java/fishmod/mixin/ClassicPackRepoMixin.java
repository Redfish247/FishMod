package fishmod.mixin;

import fishmod.features.visuals.ClassicPack;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.Arrays;

@Mixin(Minecraft.class)
public abstract class ClassicPackRepoMixin {
    @ModifyArg(
        method = "<init>",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/repository/PackRepository;<init>([Lnet/minecraft/server/packs/repository/RepositorySource;)V"),
        index = 0,
        require = 0
    )
    private RepositorySource[] fishmod$addSkyblockPackSource(RepositorySource[] sources) {
        RepositorySource[] out = Arrays.copyOf(sources, sources.length + 1);
        out[sources.length] = new ClassicPack.Source();
        return out;
    }
}
