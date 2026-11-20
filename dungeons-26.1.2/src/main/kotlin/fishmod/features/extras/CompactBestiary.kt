package fishmod.features.extras

import fishmod.features.extras.ExtrasSettings
import fishmod.utils.FishUtil
import fishmod.utils.events.FishEvents
import fishmod.utils.events.chatPlain
import net.minecraft.network.chat.Component

// Folds Hypixel's multi-line BESTIARY level-up block into one line (lines from Eli's logs)
object CompactBestiary {
    private val LEVEL = Regex("^(.+?) (\\w+) ➡ (\\w+)$")
    private val MILESTONE = Regex("^(\\d+) ?➡ ?(\\d+)$")
    private var active = false
    private var startedAt = 0L
    private var name: String? = null
    private var milestone: String? = null
    private var expectMilestone = false

    fun init() {
        FishEvents.chat.register { text ->
            if (!ExtrasSettings.compactBestiary) { active = false; return@register false }
            val s = text.chatPlain.trim()
            if (s == "BESTIARY") { active = true; startedAt = System.currentTimeMillis(); name = null; milestone = null; expectMilestone = false; return@register true }
            if (!active) return@register false
            if (System.currentTimeMillis() - startedAt > 3000) { active = false; return@register false }
            if (s.startsWith("▬▬▬")) { finish(); return@register true }
            if (name == null) LEVEL.find(s)?.let { name = "§b${it.groupValues[1]} §8${it.groupValues[2]}➡§b${it.groupValues[3]}" }
            if (s == "BESTIARY MILESTONE") expectMilestone = true
            else if (expectMilestone) MILESTONE.find(s)?.let { milestone = "§8${it.groupValues[1]}➡§e${it.groupValues[2]}"; expectMilestone = false }
            true
        }
    }

    private fun finish() {
        active = false
        val n = name ?: return
        val m = milestone?.let { " §6Milestone $it" } ?: ""
        FishUtil.addChatMessage(Component.literal("§6§lBESTIARY §r$n$m"))
    }
}
