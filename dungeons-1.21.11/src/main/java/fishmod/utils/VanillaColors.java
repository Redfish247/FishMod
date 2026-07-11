package fishmod.utils;

/**
 * The 16 vanilla Minecraft chat colors (§0–§f) with their ARGB ints.
 * Used in place of the cosmetic full-RGB color picker for HUD colors.
 */
public final class VanillaColors {

    public static final String[] NAMES = {
            "Black", "Dark Blue", "Dark Green", "Dark Aqua",
            "Dark Red", "Dark Purple", "Gold", "Gray",
            "Dark Gray", "Blue", "Green", "Aqua",
            "Red", "Light Purple", "Yellow", "White"
    };

    public static final int[] ARGB = {
            0xFF000000, 0xFF0000AA, 0xFF00AA00, 0xFF00AAAA,
            0xFFAA0000, 0xFFAA00AA, 0xFFFFAA00, 0xFFAAAAAA,
            0xFF555555, 0xFF5555FF, 0xFF55FF55, 0xFF55FFFF,
            0xFFFF5555, 0xFFFF55FF, 0xFFFFFF55, 0xFFFFFFFF
    };

    public static int colorFor(String name) {
        for (int i = 0; i < NAMES.length; i++) if (NAMES[i].equalsIgnoreCase(name)) return ARGB[i];
        return 0xFFFFFFFF;
    }

    public static String nameFor(int argb) {
        for (int i = 0; i < ARGB.length; i++) if (ARGB[i] == argb) return NAMES[i];
        return "White";
    }

    private VanillaColors() {}
}
