package fishmod.compat

// Legacy colour code table; ChatFormatting no longer exposes colour/char in 26.2.
object Fmt {
    private val codes = "0123456789abcdef"
    private val names = listOf("black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
        "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white")
    private val rgb = intArrayOf(0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
        0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF)

    fun colorOf(code: Char): Int? = codes.indexOf(code.lowercaseChar()).takeIf { it >= 0 }?.let { rgb[it] }
    fun codeOfName(name: String): Char? = names.indexOf(name.lowercase()).takeIf { it >= 0 }?.let { codes[it] }
    fun codeOfRgb(value: Int): Char? = rgb.indexOf(value and 0xFFFFFF).takeIf { it >= 0 }?.let { codes[it] }
}
