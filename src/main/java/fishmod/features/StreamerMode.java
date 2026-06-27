package fishmod.features;

import fishmod.cosmetic.NameRewriter;
import fishmod.cosmetic.NickState;
import fishmod.utils.config.values.FishSettings;
import net.minecraft.text.Text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Streamer Mode — keeps stream-snipers and beggars from reading your screen. When on it swaps your
 * real IGN for a neutral alias everywhere FishMod already rewrites names (chat, tab, scoreboard,
 * tooltips), and masks money totals on the scoreboard sidebar (Purse / Bits / Piggy / Motes).
 *
 * Render-only: it changes what's drawn, never what's sent — your real name still works in commands.
 */
public final class StreamerMode {

    private StreamerMode() {}

    // "Purse: 12,345", "Bits: 1.2k", "Piggy: 999", "Motes: 4,200" → mask the value.
    private static final Pattern MONEY_PAT = Pattern.compile(
            "(?i)(Purse|Bits|Piggy|Motes|Copper)\\s*[:：]\\s*(\\d[\\d,\\.]*[kKmMbB]?)");

    public static boolean on() { return FishSettings.streamerMode; }

    private static String alias() {
        String a = FishSettings.streamerAlias;
        return (a == null || a.isBlank()) ? "Player" : a.trim();
    }

    /** Replace the local player's IGN with the alias (for chat / GUI name text). */
    public static Text censorName(Text text) {
        if (!FishSettings.streamerMode || text == null) return text;
        String real = NickState.realName();
        if (real.isEmpty() || !text.getString().contains(real)) return text;
        return NameRewriter.replaceName(text, real, Text.literal(alias()));
    }

    /** Mask money totals on a sidebar/HUD line (Purse/Bits/…); returns the same instance if untouched. */
    public static Text censorMoney(Text text) {
        if (!FishSettings.streamerMode || text == null) return text;
        String s = text.getString();
        Matcher m = MONEY_PAT.matcher(s);
        Text out = text;
        java.util.Set<String> done = new java.util.HashSet<>();
        while (m.find()) {
            String num = m.group(2);
            if (num == null || num.isEmpty() || num.equals("***") || !done.add(num)) continue;
            // Replace just the value substring so the line keeps its original colors.
            out = NameRewriter.replaceName(out, num, Text.literal("***"));
        }
        return out;
    }

    /** Full censor pass for on-screen GUI text (name + money). */
    public static Text censor(Text text) {
        return censorMoney(censorName(text));
    }
}
