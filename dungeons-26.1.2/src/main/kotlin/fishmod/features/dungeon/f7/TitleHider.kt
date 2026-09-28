package fishmod.features.dungeon.f7

import fishmod.utils.Location
import fishmod.utils.config.values.Floor7
import fishmod.utils.dungeon.Phase
import fishmod.utils.events.Events
import net.minecraft.network.chat.Component

object TitleHider {

    // Death/revive titles stay visible so you know you're a ghost
    private val KEEP = listOf("You became a ghost!", "Hopefully your teammates will be able to revive you!", "BEING REVIVED", "You will be revived in")

    // Own P3 latch so a missed split message upstream can't leave titles unhidden
    private var inP3 = false

    @JvmStatic
    fun init() {
        Events.ON_GAME_MESSAGE.register { msg ->
            when (msg.string.replace(fishmod.utils.Constants.STRIP_COLOR_REGEX, "").trim()) {
                "[BOSS] Goldor: Who dares trespass into my domain?" -> inP3 = true
                "[BOSS] Necron: You went further than any human before, congratulations." -> inP3 = false
            }
            false
        }
        Events.ON_LOCATION_CHANGE.register { _ ->
            inP3 = false
            false
        }
    }

    @JvmStatic
    fun shouldHideTitle(title: Component): Boolean {
        if (!Floor7.hideTerminalTitles || !Location.inDungeon() || !(inP3 || Phase.inP3())) return false
        val s = title.string.replace(fishmod.utils.Constants.STRIP_COLOR_REGEX, "")
        return KEEP.none { s.contains(it) }
    }
}
