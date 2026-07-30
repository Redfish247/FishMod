package fishmod.utils

/**
 * The 16 vanilla Minecraft chat colors (§0-§f) with their ARGB ints.
 * Used in place of the cosmetic full-RGB color picker for HUD colors.
 */
object VanillaColors {

    @JvmField
    val NAMES: Array<String> = arrayOf(
        "Black", "Dark Blue", "Dark Green", "Dark Aqua",
        "Dark Red", "Dark Purple", "Gold", "Gray",
        "Dark Gray", "Blue", "Green", "Aqua",
        "Red", "Light Purple", "Yellow", "White"
    )

    @JvmField
    val ARGB: IntArray = intArrayOf(
        0xFF000000.toInt(), 0xFF0000AA.toInt(), 0xFF00AA00.toInt(), 0xFF00AAAA.toInt(),
        0xFFAA0000.toInt(), 0xFFAA00AA.toInt(), 0xFFFFAA00.toInt(), 0xFFAAAAAA.toInt(),
        0xFF555555.toInt(), 0xFF5555FF.toInt(), 0xFF55FF55.toInt(), 0xFF55FFFF.toInt(),
        0xFFFF5555.toInt(), 0xFFFF55FF.toInt(), 0xFFFFFF55.toInt(), 0xFFFFFFFF.toInt()
    )

    @JvmStatic
    fun colorFor(name: String): Int {
        for (i in NAMES.indices) if (NAMES[i].equals(name, ignoreCase = true)) return ARGB[i]
        return 0xFFFFFFFF.toInt()
    }

    @JvmStatic
    fun nameFor(argb: Int): String {
        for (i in ARGB.indices) if (ARGB[i] == argb) return NAMES[i]
        return "White"
    }
}
