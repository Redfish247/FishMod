package fishmod.cosmetic.badge

data class BadgeDef(
    val id: String,
    val displayName: String,
    val symbol: String,
    val colorRgb: Int,
    val order: Int,
) {
    fun component(suffix: String = ""): net.minecraft.network.chat.MutableComponent =
        net.minecraft.network.chat.Component.literal(symbol + suffix).setStyle(net.minecraft.network.chat.Style.EMPTY.withColor(net.minecraft.network.chat.TextColor.fromRgb(colorRgb)))
}
