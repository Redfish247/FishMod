package fishmod.features.dungeon.f7

import fishmod.utils.Location
import fishmod.utils.config.values.Floor7
import fishmod.utils.dungeon.Phase
import net.minecraft.network.chat.Component

object TitleHider {

    // Death/revive titles stay visible so you know you're a ghost
    private val KEEP = listOf("You became a ghost!", "Hopefully your teammates will be able to revive you!", "BEING REVIVED", "You will be revived in")

    @JvmStatic
    fun shouldHideTitle(title: Component): Boolean {
        if (!(Floor7.hideTerminalTitles && Phase.inP3() && Location.inDungeon())) return false
        val s = title.string.replace(fishmod.utils.Constants.STRIP_COLOR_REGEX, "")
        return KEEP.none { s.contains(it) }
    }
}
