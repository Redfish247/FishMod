package fishmod.cosmetic.badge

data class BadgeDef(
    val id: String,
    val displayName: String,
    val symbol: String,
    val colorRgb: Int,
    val order: Int,
) {
    // Crown uses the pixel font; others are plain text
    fun component(suffix: String = ""): net.minecraft.network.chat.MutableComponent =
        if (id == "gamble_crown") fishmod.features.gamble.CrownBadge.icon(colorRgb).append(net.minecraft.network.chat.Component.literal(suffix))
        else net.minecraft.network.chat.Component.literal(symbol + suffix).setStyle(net.minecraft.network.chat.Style.EMPTY.withColor(net.minecraft.network.chat.TextColor.fromRgb(colorRgb)))
}
