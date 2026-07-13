package fishmod.utils;

import java.util.ArrayList;
import java.util.List;

/** Case-insensitive, comma-separated player-name list (used for party-action whitelists/blacklists). */
public final class NameList {
    private NameList() {}

    public static boolean contains(String csv, String name) {
        if (csv == null || csv.isBlank() || name == null) return false;
        for (String s : csv.split(",")) {
            if (s.trim().equalsIgnoreCase(name.trim())) return true;
        }
        return false;
    }

    public static String add(String csv, String name) {
        if (name == null || name.isBlank() || contains(csv, name)) return csv;
        List<String> names = toList(csv);
        names.add(name.trim());
        return String.join(",", names);
    }

    public static String remove(String csv, String name) {
        if (name == null) return csv;
        List<String> names = toList(csv);
        names.removeIf(s -> s.equalsIgnoreCase(name.trim()));
        return String.join(",", names);
    }

    public static List<String> toList(String csv) {
        List<String> out = new ArrayList<>();
        if (csv == null || csv.isBlank()) return out;
        for (String s : csv.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }
}
