package fishmod.features.dungeon.f7

import fishmod.utils.SkyblockLocation
import fishmod.utils.config.values.Floor7Settings
import fishmod.utils.dungeon.BossPhase
import fishmod.utils.events.FishEvents
import net.minecraft.network.chat.Component

object P3TitleFilter {

    // Death/revive titles stay visible so you know you're a ghost
    private val KEEP = listOf("You became a ghost!", "Hopefully your teammates will be able to revive you!", "BEING REVIVED", "You will be revived in")

    // Own P3 latch so a missed split message upstream can't leave titles unhidden
    private var inP3 = false

    @JvmStatic
    fun init() {
        FishEvents.ON_GAME_MESSAGE.register { msg ->
            when (msg.string.replace(fishmod.utils.FishConstants.STRIP_COLOR_REGEX, "").trim()) {
                "[BOSS] Goldor: Who dares trespass into my domain?" -> inP3 = true
                "[BOSS] Necron: You went further than any human before, congratulations." -> inP3 = false
            }
            false
        }
        FishEvents.ON_LOCATION_CHANGE.register { _ ->
            inP3 = false
            false
        }
    }

    @JvmStatic
    fun shouldHideTitle(title: Component): Boolean {
        if (!Floor7Settings.hideTerminalTitles || !SkyblockLocation.inDungeon() || !(inP3 || BossPhase.inP3())) return false
        val s = title.string.replace(fishmod.utils.FishConstants.STRIP_COLOR_REGEX, "")
        return KEEP.none { s.contains(it) }
    }
}
