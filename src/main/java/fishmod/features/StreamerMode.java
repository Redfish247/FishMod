package fishmod.features;

import fishmod.cosmetic.NameRewriter;
import fishmod.cosmetic.NickState;
import fishmod.utils.config.values.FishSettings;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;

/**
 * Streamer Mode — anti-snipe name hiding. The real leak for streamers is the Party Finder menu (and
 * lobby tab), where viewers can read teammates' IGNs off-stream and follow/snipe. So this scrambles
 * actual player names with Minecraft's own {@code §k} obfuscated font — same width, unreadable:
 *
 *   • Party Finder / Group menus — always (while Streamer Mode is on): every online player's IGN in
 *     the menu is scrambled.
 *   • Your own IGN in chat — scrambled, so your messages don't expose your name.
 *   • Lobby tab list — optional ("Hide Tab Names"), for when you're sitting in a hub/lobby; leave it
 *     off in dungeons so you can still read your teammates.
 *
 * Only names that are actually online (in the tab list) are touched, so class/floor/other menu text
 * is never garbled. Render-only — nothing about what you send changes.
 */
public final class StreamerMode {

    private StreamerMode() {}

    private static Set<String> namesCache = Set.of();
    private static long namesAt = 0;

    /** Online player IGNs (tab list + you), cached ~1s so we don't rebuild it on every text draw. */
    private static Set<String> onlineNames() {
        long now = System.currentTimeMillis();
        if (now - namesAt < 1000) return namesCache;
        namesAt = now;
        Set<String> s = new HashSet<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            for (var e : mc.getConnection().getOnlinePlayers()) {
                var gp = e.getProfile();
                if (gp != null && gp.name() != null && gp.name().length() >= 3) s.add(gp.name());
            }
        }
        String self = NickState.realName();
        if (!self.isEmpty()) s.add(self);
        namesCache = s;
        return s;
    }

    private static Component obfuscate(String name) {
        return Component.literal(name).withStyle(st -> st.withObfuscated(true));
    }

    private static Component scramble(Component text, Set<String> names) {
        if (text == null || names.isEmpty()) return text;
        String str = text.getString();
        Component out = text;
        for (String n : names) {
            if (str.contains(n)) out = NameRewriter.replaceName(out, n, obfuscate(n));
        }
        return out;
    }

    /** Chat: scramble just your own IGN. */
    public static Component censorChat(Component text) {
        if (!FishSettings.streamerMode || text == null) return text;
        String self = NickState.realName();
        if (self.isEmpty() || !text.getString().contains(self)) return text;
        return NameRewriter.replaceName(text, self, obfuscate(self));
    }

    /** On-screen GUI text: scramble names in Party Finder menus, and in the tab list when enabled. */
    public static Component forGui(Component text, boolean inMenu) {
        if (!FishSettings.streamerMode || text == null) return text;
        if (inMenu) return inPartyFinder() ? scramble(text, onlineNames()) : text;
        return FishSettings.streamerHideTab ? scramble(text, onlineNames()) : text;
    }

    private static boolean inPartyFinder() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen)) return false;
        Component title = mc.screen.getTitle();
        String t = title == null ? "" : title.getString().toLowerCase();
        return t.contains("party finder") || t.contains("group builder")
                || t.contains("parties") || t.contains("your party") || t.contains("party settings");
    }
}
