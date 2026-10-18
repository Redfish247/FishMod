package fishmod.features.extras

import fishmod.utils.Misc
import fishmod.utils.events.Events
import net.minecraft.network.chat.Component
import fishmod.features.extras.ExtrasSettings as S

// Sound (and optional title) when someone joins your party
object PartyJoinSound {
    private val JOIN = Regex("""^(?:\[[^]]+]\s+)?(\w{1,16}) joined the party\.$""")
    private var last = ""
    private var lastMs = 0L

    fun init() {
        Events.ON_GAME_MESSAGE.register { text ->
            if (S.partyJoinSound) {
                val s = text.string.replace(fishmod.utils.Constants.STRIP_COLOR_REGEX, "").trim()
                JOIN.find(s)?.let { onJoin(it.groupValues[1]) }
            }
            false
        }
    }

    private fun onJoin(name: String) {
        val now = System.currentTimeMillis()
        if (name == last && now - lastMs < 1500) return
        last = name; lastMs = now
        ExtrasSounds.play(S.partyJoinSoundName, S.partyJoinVolume, "fm_party_join")
        if (S.partyJoinTitle) Misc.forceTitle(Component.empty(), Component.literal("§a$name §7joined the party"), 1500)
    }
}
