package fishmod.cosmetic;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/** Holds the client-side cosmetic display name and turns the raw "&"-coded string into a styled Text. */
public final class NickState {
    private static volatile String nick = null;

    private NickState() {}

    public static void set(String name) {
        // Censor banned words before anything is stored, displayed or uploaded. This is the single
        // chokepoint for every nick path (gradient, solid, custom text) — the filter understands
        // color codes, so it works even on a fully gradient-coded string.
        if (name != null && !name.isEmpty()) name = ProfanityFilter.censor(name);
        nick = (name != null && !name.isEmpty()) ? name : null;
        NickData.save(nick);
        RemoteNicks.uploadOwn(); // publish so other mod users see the change
    }

    /** Recolors the player's real username with a gradient over the given RGB stops. */
    public static void setGradient(int[][] stops) {
        String raw = GradientNick.build(realName(), stops);
        set(raw);
    }

    /**
     * Applies the configured nick: takes the custom name (if set) or the real IGN as the base text,
     * strips any existing color codes, then re-colors it in the chosen mode (Solid or Gradient).
     */
    public static void applyFromSettings() {
        String custom = fishmod.utils.config.values.FishSettings.nickCustomName;
        String base = (custom != null && !custom.isEmpty()) ? custom : realName();
        // Strip ONLY color codes (hex + 0-9/a-f/x) so the chosen palette wins. Keep format codes
        // (&l/&o/&m/&n/&k/&r) intact — GradientNick re-emits them after every per-letter color so
        // bold/italic etc. survive the gradient.
        String stripped = base.replaceAll("&#[0-9a-fA-F]{6}", "").replaceAll("[&§][0-9a-fxA-FX]", "");
        // Empty-visible check (strip format codes too, just for this test).
        String visibleOnly = stripped.replaceAll("[&§][klmnorKLMNOR]", "");
        if (visibleOnly.isEmpty()) { reset(); return; }
        int[][] stops;
        if ("SOLID".equalsIgnoreCase(fishmod.utils.config.values.FishSettings.nickColorMode)) {
            stops = new int[][] { GradientNick.rgb(fishmod.utils.config.values.FishSettings.nickColorStart) };
        } else {
            stops = new int[][] {
                GradientNick.rgb(fishmod.utils.config.values.FishSettings.nickColorStart),
                GradientNick.rgb(fishmod.utils.config.values.FishSettings.nickColorEnd)
            };
        }
        set(GradientNick.build(stripped, stops));
    }

    public static void reset() {
        nick = null;
        NickData.save(null);
        RemoteNicks.uploadOwn();
    }

    public static void applyFromDisk(String raw) {
        if (raw != null && !raw.isEmpty()) raw = ProfanityFilter.censor(raw);
        nick = (raw != null && !raw.isEmpty()) ? raw : null;
    }

    public static boolean isActive() {
        return nick != null;
    }

    public static String getRaw() {
        return nick;
    }

    /** The player's real in-game username, used as the search target when swapping. */
    public static String realName() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getUser() != null) {
            String n = mc.getUser().getName();
            if (n != null && !n.isEmpty()) return n;
        }
        return "";
    }

    public static Component asComponent() {
        return parse(nick);
    }

    /** Parses a string with &-codes (and &#rrggbb hex codes) into a styled Text. */
    public static Component parse(String input) {
        MutableComponent root = Component.empty();
        if (input == null || input.isEmpty()) return root;

        Style style = Style.EMPTY;
        StringBuilder buf = new StringBuilder();
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < input.length()) {
                char next = input.charAt(i + 1);
                // "&*" inserts a SkyBlock star (✪) in the CURRENT color — pick the color with the code
                // right before it (e.g. "&6&*" = gold star, "&d&*" = pink). Replaces the old star counter.
                if (next == '*') {
                    buf.append('✪');
                    i += 2;
                    continue;
                }
                if (next == '#' && i + 7 < input.length()) {
                    String hex = input.substring(i + 2, i + 8);
                    if (hex.matches("[0-9a-fA-F]{6}")) {
                        if (buf.length() > 0) {
                            root.append(Component.literal(buf.toString()).setStyle(style));
                            buf.setLength(0);
                        }
                        style = Style.EMPTY.withColor(TextColor.parseColor("#" + hex).getOrThrow());
                        i += 8;
                        continue;
                    }
                }

                ChatFormatting fmt = ChatFormatting.getByCode(Character.toLowerCase(next));
                if (fmt != null) {
                    if (buf.length() > 0) {
                        root.append(Component.literal(buf.toString()).setStyle(style));
                        buf.setLength(0);
                    }
                    if (fmt == ChatFormatting.RESET) {
                        style = Style.EMPTY;
                    } else if (fmt.isColor()) {
                        style = Style.EMPTY.withColor(fmt);
                    } else {
                        style = style.applyFormat(fmt);
                    }
                    i += 2;
                    continue;
                }
            }

            buf.append(c);
            i++;
        }

        if (buf.length() > 0) {
            root.append(Component.literal(buf.toString()).setStyle(style));
        }
        return root;
    }
}
