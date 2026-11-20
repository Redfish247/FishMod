package fishmod.features.mining

import fishmod.utils.SkyblockLocation
import fishmod.utils.events.FishEvents
import fishmod.features.mining.MiningSettings as S

// HUD with time spent in the current Glacite Mineshaft
object MineshaftTimer {
    private var enteredMs = 0L

    fun init() {
        FishEvents.worldChange.register { enteredMs = 0L; false }
        FishEvents.locationChange.register { loc -> if (loc == SkyblockLocation.MINESHAFT) enteredMs = System.currentTimeMillis(); false }
        MiningHuds.reg("Mineshaft Timer", "mining_shaft_timer", 90, 10,
            { S.miningShaftTimerX }, { S.miningShaftTimerX = it }, { S.miningShaftTimerY }, { S.miningShaftTimerY = it },
            { S.miningShaftTimerScale }, { S.miningShaftTimerScale = it },
            { S.miningShaftTimer && (Mining.inShaft() || fishmod.features.FishHudEditor.isOpen()) }, { S.miningShaftTimer },
            example = { listOf("§bMineshaft: §f2m 05s") }, lines = ::lines)
    }

    private fun lines(): List<String> {
        if (enteredMs == 0L) enteredMs = System.currentTimeMillis()
        val s = (System.currentTimeMillis() - enteredMs) / 1000
        return listOf("§bMineshaft: §f%dm %02ds".format(s / 60, s % 60))
    }
}
