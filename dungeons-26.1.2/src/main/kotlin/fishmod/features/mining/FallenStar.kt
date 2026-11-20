package fishmod.features.mining

import fishmod.utils.events.FishEvents
import fishmod.features.mining.MiningSettings as S

// Title when a Fallen Star crashes in the Dwarven Mines
object FallenStar {
    private val CRASH = Regex("^✯ A Fallen Star has crashed at (.+?)! Nearby ore and Powder drops are amplified!$")

    fun init() {
        FishEvents.chat.register { text ->
            if (S.miningFallenStar) CRASH.find(Mining.strip(text.chatRaw).trim())?.let { Mining.title("§e§l✯ Fallen Star", "§f" + it.groupValues[1], 3000, true) }
            false
        }
    }
}
