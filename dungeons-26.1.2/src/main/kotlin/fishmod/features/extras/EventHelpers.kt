package fishmod.features.extras

import fishmod.utils.FishMsg
import fishmod.utils.FishUtil
import fishmod.utils.events.FishEvents
import fishmod.utils.events.chatPlain
import net.minecraft.network.chat.Component
import fishmod.features.extras.ExtrasSettings as S

// Hoppity's Hunt session summary and Trick or Treat Chest alert (chat lines from Eli's logs)
object EventHelpers {
    private var eggs = 0
    private var newRabbits = 0
    private var dupes = 0

    fun init() {
        FishEvents.chat.register { text ->
            val s = text.chatPlain.trim()
            if (S.hoppitySummary) hoppity(s)
            if (S.trickTreatAlert && s == "SPOOKY! A Trick or Treat Chest has appeared!")
                FishUtil.forceTitle(Component.literal(S.trickTreatTitle.replace('&', '§')), Component.literal(""), 2000)
            false
        }
    }

    private fun hoppity(s: String) {
        when {
            s.startsWith("HOPPITY'S HUNT You found a ") && s.contains(" Egg") -> { eggs++; return }
            s.startsWith("NEW RABBIT!") -> newRabbits++
            s.startsWith("DUPLICATE RABBIT!") -> dupes++
            else -> return
        }
        FishUtil.addChatMessage(Component.literal(FishMsg.prefix() + "§dHoppity session: §e$eggs §7eggs, §a$newRabbits §7new, §7$dupes duplicate"))
    }
}
