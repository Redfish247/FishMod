package fishmod.features.dungeon

import fishmod.utils.SkyblockLocation
import fishmod.utils.TextStrip
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.FishEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import java.util.regex.Pattern

object ArchitectDraft {

    private val PUZZLE_FAIL = Pattern.compile("PUZZLE FAIL! (\\w{1,16}) .+")
    private val ORUO_WRONG = Pattern.compile("\\[STATUE] Oruo the Omniscient: (\\w{1,16}) chose the wrong answer!.*")

    private var pending = -1

    @JvmStatic
    fun init() {
        FishEvents.chat.register { text ->
            if (!FishSettings.architectDraftRefill || !SkyblockLocation.inDungeon()) return@register false
            val s = TextStrip.plain(text.string).trim()
            val self = Minecraft.getInstance().player?.gameProfile?.name ?: return@register false
            val m1 = PUZZLE_FAIL.matcher(s)
            val m2 = ORUO_WRONG.matcher(s)
            val ok1 = m1.matches()
            val ok2 = m2.matches()
            if (!ok1 && s.startsWith("PUZZLE FAIL! ")) FishDiag.fail("ArchitectDraft.1", "puzzle fail line did not parse: '$s'")
            if (!ok2 && s.startsWith("[STATUE] Oruo the Omniscient: ") && s.contains("wrong answer")) FishDiag.fail("ArchitectDraft.2", "oruo wrong-answer line did not parse: '$s'")
            if ((ok1 && m1.group(1) == self) || (ok2 && m2.group(1) == self)) pending = 30
            false
        }

        FishEvents.worldChange.register { pending = -1; false }

        ClientTickEvents.END_CLIENT_TICK.register {
            if (pending < 0) return@register
            if (pending == 0) FishDiag.guard("ArchitectDraft.3", "architect draft gfs command failed") { Minecraft.getInstance().connection?.sendCommand("gfs ARCHITECT_FIRST_DRAFT 1") }
            pending--
        }
    }
}
