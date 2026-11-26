package fishmod.cosmetic.badge

data class BadgeDef(
    val id: String,
    val displayName: String,
    val symbol: String,
    val colorRgb: Int,
    val order: Int,
) {
    private fun drawable(): String {
        if (symbol.isEmpty()) return "✦"
        if (symbol.codePointAt(0) == 0x1F6E0) return "⚒"
        return if (symbol.codePoints().anyMatch { it > 0xFFFF }) "✦" else symbol
    }

    fun component(suffix: String = ""): net.minecraft.network.chat.MutableComponent =
        net.minecraft.network.chat.Component.literal(drawable() + suffix).setStyle(net.minecraft.network.chat.Style.EMPTY.withColor(net.minecraft.network.chat.TextColor.fromRgb(colorRgb)))
}
