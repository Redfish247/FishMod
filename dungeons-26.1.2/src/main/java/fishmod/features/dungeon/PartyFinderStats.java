package fishmod.features.dungeon;

import fishmod.utils.FishMsg;
import fishmod.utils.HypixelApi;
import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Map;

/**
 * Party Finder join-request helper: while FishSettings.pfStatsEnabled is on, any whisper you
 * receive (typically someone asking to join your party) triggers a local-only lookup of their
 * MP/PB/Cata/Gear, printed to your own chat so you can vet them before inviting. Nothing is ever
 * sent back to the sender.
 */
public final class PartyFinderStats {
    private PartyFinderStats() {}

    private static final Map<String, Long> lastLookupAt = new HashMap<>();
    private static final long COOLDOWN_MS = 15_000;

    public static void onWhisper(String sender) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || sender == null) return;
        if (sender.equalsIgnoreCase(mc.player.getName().getString())) return;

        long now = System.currentTimeMillis();
        Long last = lastLookupAt.get(sender.toLowerCase());
        if (last != null && now - last < COOLDOWN_MS) return;
        lastLookupAt.put(sender.toLowerCase(), now);

        HypixelApi.getByNameSilent(sender, data -> {
            String mp = data.magicalPower >= 0 ? String.valueOf(data.magicalPower) : "N/A";
            String pb = data.masterPbs != null && data.masterPbs.length > 7 && data.masterPbs[7] != null
                    ? data.masterPbs[7] : "N/A";
            String cata = HypixelApi.formatLevel(data.cataXp);
            String gear = data.armorStars != null
                    ? String.format("H%d C%d L%d B%d", data.armorStars[0], data.armorStars[1],
                            data.armorStars[2], data.armorStars[3])
                    : "N/A";
            FishMsg.send(sender + " wants to join — MP: " + mp + " | M7 PB: " + pb
                    + " | Cata: " + cata + " | Gear: " + gear);
        });
    }
}
