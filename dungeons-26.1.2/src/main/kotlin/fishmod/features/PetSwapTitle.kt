package fishmod.features

import fishmod.utils.Misc
import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import java.util.Optional

object PetSwapTitle {

    private val COLOR = fishmod.utils.Constants.STRIP_COLOR_REGEX
    private val AUTOPET = Regex("""Autopet equipped your \[Lvl\s*\d+]\s*(.+?)!""")
    private val SUMMON = Regex("""You summoned your\s+(.+?)!""")

    @JvmStatic
    fun init() {
        ClientReceiveMessageEvents.GAME.register { msg, overlay ->
            if (overlay || !FishSettings.petSwapTitleEnabled) return@register
            val s = COLOR.replace(msg.string, "").trim()
            val name = (AUTOPET.find(s) ?: SUMMON.find(s))?.groupValues?.get(1)?.replace("✦", "")?.trim() ?: return@register
            show(name, rarityColor(msg, name))
        }
    }

    private fun rarityColor(msg: Component, name: String): Int? {
        val first = name.split(' ').first()
        var color: Int? = null
        msg.visit({ style: Style, text: String ->
            if (text.contains(first)) { color = style.color?.value; Optional.of(Unit) } else Optional.empty()
        }, Style.EMPTY)
        if (color != null) return color
        val raw = msg.string
        val idx = raw.indexOf(first)
        if (idx < 2) return null
        for (i in idx - 1 downTo 1) {
            if (raw[i - 1] != '§') continue
            net.minecraft.ChatFormatting.getByCode(raw[i])?.color?.let { return it }
        }
        return null
    }

    private fun show(name: String, rarity: Int?) {
        val text = FishSettings.petSwapTitleFormat.ifBlank { "{pet}" }.replace("{pet}", name)
        val color = if (FishSettings.petSwapTitleRarityColor && rarity != null) rarity else FishSettings.petSwapTitleColor
        Misc.forceTitle(Component.literal(text).withColor(color and 0xFFFFFF), Component.empty(), FishSettings.petSwapTitleMs)
    }
}
