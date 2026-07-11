package fishmod.cosmetic;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side bad-word filter for cosmetic text (custom nicks + item names). It censors banned
 * words in the VISIBLE text of a Minecraft-formatted string (codes like {@code &a}, {@code §l} and
 * {@code &#rrggbb} are preserved) and is robust to the usual evasions: leetspeak ({@code n1gg3r}),
 * separators between letters ({@code f-u-c-k}) and padded letters ({@code shiiit}).
 *
 * Used in both directions: our own nick/item names are censored before they're shown or uploaded,
 * and other players' incoming nicks/item names are censored before they're displayed locally.
 */
public final class ProfanityFilter {
    private ProfanityFilter() {}

    /**
     * Banned words as canonical de-leeted, run-collapsed base forms (see {@link #collapse}). The
     * list targets unambiguous slurs/profanity; short words that collide with normal text (e.g.
     * "ass") are intentionally left out to avoid false positives in legitimate names.
     */
    private static final String[] RAW_WORDS = {
        "hitler", "nazi",
        "fuck", "fuk", "fuq", "motherfucker", "fucker",
        "shit",
        "nigger", "nigga", "niglet", "nignog",
        "faggot", "fagot", "faggit",
        "retard", "retarted",
        "cunt",
        "kike", "spic", "chink", "gook", "wetback", "beaner", "coon", "wop",
        "tranny", "dyke",
        "whore", "slut",
        "rape", "rapist", "pedo", "pedophile",
        "bitch",
        "asshole",
        "pussy",
    };

    /** Collapsed/normalized banned words (built once). */
    private static final String[] WORDS;
    static {
        List<String> ws = new ArrayList<>(RAW_WORDS.length);
        for (String w : RAW_WORDS) {
            StringBuilder n = new StringBuilder();
            for (int i = 0; i < w.length(); i++) {
                char c = normalize(w.charAt(i));
                if (c != 0) n.append(c);
            }
            String collapsed = collapse(n.toString());
            if (collapsed.length() >= 3) ws.add(collapsed);
        }
        WORDS = ws.toArray(new String[0]);
    }

    /** True if the visible text of {@code raw} contains any banned word. */
    public static boolean isProfane(String raw) {
        if (raw == null || raw.isEmpty()) return false;
        Visible v = visible(raw);
        Compact c = compact(v);
        for (String w : WORDS) if (c.text.contains(w)) return true;
        return false;
    }

    /**
     * Returns {@code raw} with every banned word's visible characters replaced by {@code *},
     * leaving all color/format codes intact. Idempotent — re-running on the result is a no-op.
     */
    public static String censor(String raw) {
        if (raw == null || raw.isEmpty()) return raw;
        Visible v = visible(raw);
        Compact c = compact(v);
        boolean[] censorVis = new boolean[v.chars.size()];
        boolean any = false;
        for (String w : WORDS) {
            int from = 0, idx;
            while ((idx = c.text.indexOf(w, from)) >= 0) {
                int startVis = c.runStartVis[idx];
                int endVis   = c.runEndVis[idx + w.length() - 1];
                for (int i = startVis; i <= endVis; i++) censorVis[i] = true;
                any = true;
                from = idx + 1;
            }
        }
        if (!any) return raw;

        StringBuilder out = new StringBuilder(raw.length());
        int vi = 0;
        for (Unit u : v.units) {
            if (u.code != null) {
                out.append(u.code);
            } else {
                out.append(censorVis[vi] ? '*' : u.ch);
                vi++;
            }
        }
        return out.toString();
    }

    // ── internals ────────────────────────────────────────────────────────────

    private record Unit(String code, char ch) {}

    private static final class Visible {
        final List<Unit> units = new ArrayList<>();
        final List<Character> chars = new ArrayList<>(); // visible chars only, in order
    }

    private static final class Compact {
        String text;
        int[] runStartVis; // compact index → first visible-char index of its run
        int[] runEndVis;   // compact index → last visible-char index of its run
    }

    /** Splits a formatted string into code tokens + visible characters. */
    private static Visible visible(String raw) {
        Visible v = new Visible();
        int i = 0, n = raw.length();
        while (i < n) {
            char c = raw.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < n) {
                char next = raw.charAt(i + 1);
                if (next == '#' && i + 7 < n && isHex(raw, i + 2, 6)) {
                    v.units.add(new Unit(raw.substring(i, i + 8), (char) 0));
                    i += 8;
                    continue;
                }
                if (isFormatCode(next)) {
                    v.units.add(new Unit(raw.substring(i, i + 2), (char) 0));
                    i += 2;
                    continue;
                }
            }
            v.units.add(new Unit(null, c));
            v.chars.add(c);
            i++;
        }
        return v;
    }

    /** Builds the normalized, run-collapsed compact string with run→visible index maps. */
    private static Compact compact(Visible v) {
        StringBuilder sb = new StringBuilder(v.chars.size());
        int[] start = new int[v.chars.size()];
        int[] end = new int[v.chars.size()];
        int len = 0;
        for (int i = 0; i < v.chars.size(); i++) {
            char nc = normalize(v.chars.get(i));
            if (nc == 0) continue; // separator — ignored, but spans still bridge over it
            if (len > 0 && sb.charAt(len - 1) == nc) {
                end[len - 1] = i; // extend current run to include this padded duplicate
            } else {
                sb.append(nc);
                start[len] = i;
                end[len] = i;
                len++;
            }
        }
        Compact c = new Compact();
        c.text = sb.toString();
        c.runStartVis = new int[len];
        c.runEndVis = new int[len];
        System.arraycopy(start, 0, c.runStartVis, 0, len);
        System.arraycopy(end, 0, c.runEndVis, 0, len);
        return c;
    }

    /** Lowercases + maps leetspeak to a base letter; returns 0 for non-alphanumeric separators. */
    private static char normalize(char c) {
        c = Character.toLowerCase(c);
        switch (c) {
            case '0': return 'o';
            case '1': case '!': case '|': return 'i';
            case '3': case '£': case '€': return 'e';
            case '4': case '@': return 'a';
            case '5': case '$': return 's';
            case '7': case '+': return 't';
            case '8': return 'b';
            case '9': return 'g';
            default:
                if (c >= 'a' && c <= 'z') return c;
                if (c >= '0' && c <= '9') return c; // unmapped digit — kept, harmless
                return 0;
        }
    }

    /** Removes consecutive duplicate characters ("shiiit" → "shit"). */
    private static String collapse(String s) {
        if (s.isEmpty()) return s;
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (b.length() == 0 || b.charAt(b.length() - 1) != c) b.append(c);
        }
        return b.toString();
    }

    private static boolean isFormatCode(char c) {
        c = Character.toLowerCase(c);
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')
                || c == 'k' || c == 'l' || c == 'm' || c == 'n' || c == 'o' || c == 'r' || c == 'x';
    }

    private static boolean isHex(String s, int off, int count) {
        for (int i = 0; i < count; i++) {
            char c = Character.toLowerCase(s.charAt(off + i));
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) return false;
        }
        return true;
    }
}
