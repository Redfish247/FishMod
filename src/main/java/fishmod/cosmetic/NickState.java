package fishmod.cosmetic;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

/** Holds the client-side cosmetic display name and turns the raw "&"-coded string into a styled Text. */
public final class NickState {
    private static volatile String nick = null;

    private NickState() {}

    public static void set(String name) {
        nick = (name != null && !name.isEmpty()) ? name : null;
        NickData.save(nick);
        RemoteNicks.uploadOwn(); // publish so other mod users see the change
    }

    public static void reset() {
        nick = null;
        NickData.save(null);
        RemoteNicks.uploadOwn();
    }

    public static void applyFromDisk(String raw) {
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
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getSession() != null) {
            String n = mc.getSession().getUsername();
            if (n != null && !n.isEmpty()) return n;
        }
        return "";
    }

    public static Text asComponent() {
        return parse(nick);
    }

    /** Parses a string with &-codes (and &#rrggbb hex codes) into a styled Text. */
    public static Text parse(String input) {
        MutableText root = Text.empty();
        if (input == null || input.isEmpty()) return root;

        Style style = Style.EMPTY;
        StringBuilder buf = new StringBuilder();
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < input.length()) {
                char next = input.charAt(i + 1);
                if (next == '#' && i + 7 < input.length()) {
                    String hex = input.substring(i + 2, i + 8);
                    if (hex.matches("[0-9a-fA-F]{6}")) {
                        if (buf.length() > 0) {
                            root.append(Text.literal(buf.toString()).setStyle(style));
                            buf.setLength(0);
                        }
                        style = Style.EMPTY.withColor(TextColor.parse("#" + hex).getOrThrow());
                        i += 8;
                        continue;
                    }
                }

                Formatting fmt = Formatting.byCode(Character.toLowerCase(next));
                if (fmt != null) {
                    if (buf.length() > 0) {
                        root.append(Text.literal(buf.toString()).setStyle(style));
                        buf.setLength(0);
                    }
                    if (fmt == Formatting.RESET) {
                        style = Style.EMPTY;
                    } else if (fmt.isColor()) {
                        style = Style.EMPTY.withColor(fmt);
                    } else {
                        style = style.withFormatting(fmt);
                    }
                    i += 2;
                    continue;
                }
            }

            buf.append(c);
            i++;
        }

        if (buf.length() > 0) {
            root.append(Text.literal(buf.toString()).setStyle(style));
        }
        return root;
    }
}
