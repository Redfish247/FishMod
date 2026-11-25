package fishmod.features.dungeon.floor7

import fishmod.utils.events.chatPlain
import fishmod.utils.SkyblockLocation
import fishmod.utils.config.values.Floor7Settings
import fishmod.utils.dungeon.BossPhase
import fishmod.utils.events.FishEvents
import net.minecraft.network.chat.Component

object P3TitleFilter {

    private val KEEP = listOf("You became a ghost!", "Hopefully your teammates will be able to revive you!", "BEING REVIVED", "You will be revived in")

    private var inP3 = false

    @JvmStatic
    fun init() {
        FishEvents.chat.register { msg ->
            when (msg.chatPlain.trim()) {
                "[BOSS] Goldor: Who dares trespass into my domain?" -> if (SkyblockLocation.inDungeon()) inP3 = true
                "[BOSS] Necron: You went further than any human before, congratulations." -> inP3 = false
            }
            false
        }
        // world change only: a mid-run location resend used to clear P3 and let later titles through
        FishEvents.worldChange.register {
            inP3 = false
            false
        }
    }

    @JvmStatic
    fun shouldHideTitle(title: Component): Boolean {
        if (!Floor7Settings.muteTerminalTitles || !(inP3 || BossPhase.inP3())) return false
        val s = title.string.replace(fishmod.utils.FishConstants.STRIP_COLOR_REGEX, "")
        return KEEP.none { s.contains(it) }
    }
}
