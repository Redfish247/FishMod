package fishmod.cosmetic;

import net.minecraft.network.chat.Component;

import java.util.List;

public interface NametagStatsHolder {
    List<Component> fishmod$getNametagStats();
    void fishmod$setNametagStats(List<Component> lines);
}
