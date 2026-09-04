package fishmod.cosmetic;

import net.minecraft.network.chat.Component;

import java.util.List;

/** Carries the resolved networth / cata lines to draw under a player's nametag (see NametagStats). */
public interface NametagStatsHolder {
    List<Component> fishmod$getNametagStats();
    void fishmod$setNametagStats(List<Component> lines);
}
