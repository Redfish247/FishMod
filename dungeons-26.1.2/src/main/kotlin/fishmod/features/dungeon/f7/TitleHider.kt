package fishmod.features.dungeon.f7

import fishmod.utils.Location
import fishmod.utils.config.values.Floor7
import fishmod.utils.data.EntityUtil
import fishmod.utils.dungeon.Phase
import net.minecraft.network.chat.Component
import java.util.regex.Pattern

/**
 * Hides other players' "activated/completed a terminal/device/lever!" titles during P3 so only
 * your own progress pops a title. Pure function, stateless — no init()/HUD needed. Ported from
 * blade-addons.
 */
object TitleHider {

    private val TERMINALS_DONE_PATTERN: Pattern =
        Pattern.compile("^(\\w+) (activated|completed) a (terminal|device|lever)! \\((\\d)/(\\d)\\)$")

    @JvmStatic
    fun shouldHideTitle(title: Component): Boolean {
        if (!(Floor7.hideTerminalTitles && Phase.inP3() && Location.inDungeon())) return false
        val titleString = title.string.replace(Regex("§."), "")

        val matcher = TERMINALS_DONE_PATTERN.matcher(titleString)
        return if (matcher.find()) {
            val name = matcher.group(1)
            !EntityUtil.isClientPlayer(name)
        } else {
            false
        }
    }
}
