package fishmod.cosmetic;

import net.minecraft.ChatFormatting;

/** Builds a per-character &#rrggbb gradient string over a fixed name (no custom text allowed). */
public final class GradientNick {
    private GradientNick() {}

    /**
     * Interpolates the given RGB stops across the visible characters of {@code name}, preserving any
     * inline format codes ({@code &l}/{@code &o}/{@code &m}/{@code &n}/{@code &k}/{@code &r}). Color
     * codes inside the input are ignored — the gradient overrides them. Format codes are accumulated
     * as the input is scanned and re-emitted after every per-letter color code (because in Minecraft
     * a new color code resets formatting, so bold/italic would otherwise be wiped out).
     */
    public static String build(String name, int[][] stops) {
        if (name == null || name.isEmpty() || stops == null || stops.length == 0) return name;

        // First pass: count visible characters (those that aren't part of a color/format code).
        int letterCount = 0;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < name.length()) {
                char next = name.charAt(i + 1);
                if (next == '#' && i + 7 < name.length() && name.substring(i + 2, i + 8).matches("[0-9a-fA-F]{6}")) {
                    i += 7; continue;
                }
                char low = Character.toLowerCase(next);
                if ("0123456789abcdefxklmnor".indexOf(low) >= 0) { i++; continue; }
            }
            letterCount++;
        }
        if (letterCount == 0) return name;

        // Second pass: emit each letter prefixed by the per-position color AND any active formats.
        StringBuilder out = new StringBuilder();
        StringBuilder activeFormats = new StringBuilder();
        int letterIdx = 0;
        boolean solid = stops.length == 1;
        String solidHex = solid ? hex(stops[0]) : null;

        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < name.length()) {
                char next = name.charAt(i + 1);
                if (next == '#' && i + 7 < name.length() && name.substring(i + 2, i + 8).matches("[0-9a-fA-F]{6}")) {
                    i += 7; continue; // hex color — overridden by gradient
                }
                char low = Character.toLowerCase(next);
                if ("0123456789abcdefx".indexOf(low) >= 0) {
                    activeFormats.setLength(0); // color codes reset formatting in MC
                    i++; continue;
                }
                if ("klmno".indexOf(low) >= 0) {
                    activeFormats.append('&').append(low);
                    i++; continue;
                }
                if (low == 'r') { activeFormats.setLength(0); i++; continue; }
            }
            // Visible character → emit color, formats, then the char.
            String colorPrefix;
            if (solid) colorPrefix = solidHex;
            else {
                double t = letterCount == 1 ? 0.0 : (double) letterIdx / (letterCount - 1);
                double scaled = t * (stops.length - 1);
                int seg = (int) Math.floor(scaled);
                if (seg >= stops.length - 1) seg = stops.length - 2;
                double lt = scaled - seg;
                int[] a = stops[seg], b = stops[seg + 1];
                colorPrefix = hex(new int[] { lerp(a[0], b[0], lt), lerp(a[1], b[1], lt), lerp(a[2], b[2], lt) });
            }
            out.append(colorPrefix).append(activeFormats).append(c);
            letterIdx++;
        }
        return out.toString();
    }

    private static int lerp(int a, int b, double t) { return (int) Math.round(a + (b - a) * t); }

    private static String hex(int[] rgb) {
        return String.format("&#%02x%02x%02x", rgb[0] & 0xFF, rgb[1] & 0xFF, rgb[2] & 0xFF);
    }

    /** {r,g,b} from a packed ARGB/RGB int. */
    public static int[] rgb(int packed) {
        return new int[] { (packed >> 16) & 0xFF, (packed >> 8) & 0xFF, packed & 0xFF };
    }

    /** Parses a hex (#rrggbb / rrggbb), color code (&a / a), or color name (red, dark_blue...). Null if invalid. */
    public static int[] parseColor(String token) {
        if (token == null) return null;
        String t = token.trim();
        if (t.startsWith("&")) t = t.substring(1);
        if (t.startsWith("#")) t = t.substring(1);
        if (t.matches("[0-9a-fA-F]{6}")) {
            return new int[] {
                Integer.parseInt(t.substring(0, 2), 16),
                Integer.parseInt(t.substring(2, 4), 16),
                Integer.parseInt(t.substring(4, 6), 16)
            };
        }
        ChatFormatting f = null;
        if (t.length() == 1) f = ChatFormatting.getByCode(t.charAt(0));
        if (f == null) f = ChatFormatting.getByName(t.toUpperCase());
        if (f != null && f.isColor() && f.getColor() != null) return rgb(f.getColor());
        return null;
    }

    /** red→orange→yellow→green→blue→purple, for /nick rainbow. */
    public static int[][] rainbow() {
        return new int[][] {
            {255, 85, 85}, {255, 170, 0}, {255, 255, 85},
            {85, 255, 85}, {85, 85, 255}, {170, 0, 170}
        };
    }
}
